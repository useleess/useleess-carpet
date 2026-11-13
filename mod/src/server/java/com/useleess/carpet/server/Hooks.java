package com.useleess.carpet.server;

import net.minecraft.command.CommandHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * The few places Minecraft and Carpet call into us (patched in by the build, see tools/ServerLayer): server loaded,
 * every tick (bots going to bed at night), player joined / left / dying, server stopping, a player's "on the ground", a
 * mod's message from a client, a click on a block, the furnace's and the brewing stand's recipe lookups, a recipe-book message. Each one is guarded: a failure
 * switches the feature off.
 */
public final class Hooks {
    private static final Queue<Runnable> NEXT_TICK = new ArrayDeque<>();
    private static final Set<UUID> NO_CONNECTION_TOLD = new HashSet<>();
    private static final FallCheck FALL = new FallCheck(Rule.named(FallCheck.RULE), e -> {
        Guard.LOG.error(ServerText.get("log.fall_check_failed"), e);
        Guard.notice(ServerText.get("fall.check_failed", String.valueOf(e)));
    });

    private Hooks() {}

    /** Carpet calls this before the worlds are loaded: the world folder is read by {@link #worldsLoaded} later. */
    public static void serverLoaded(MinecraftServer server) {
        Guard.attach(server);
        Guard.run("start", () -> {
            Rule.init();
            ScopeSwitch.init();
            CustomRecipes.register();
            CommandHandler commands = (CommandHandler) server.getCommandManager();
            commands.registerCommand(new CustomRecipeCommand());
            commands.registerCommand(new BotSleepCommand());
            if (SelfTest.enabled()) commands.registerCommand(new SelfTest());
        });
    }

    /** The world folder: our recipes and the saved inventories. Runs once, on the first tick or the first join. */
    private static void worldsLoaded(MinecraftServer server) {
        if (RecipeStore.current != null) return;
        File root = new File(server.getWorld(0).getSaveHandler().getWorldDirectory(), "useleess-carpet");
        Vault.dir = new File(root, "editor");
        RecipeStore.current = RecipeStore.open(new File(root, "recipes"));
        CarefulBreak.open(new File(root, "careful-break.json"));
        BotSleep.open(new File(root, "bot-sleep.json"));
    }

    /**
     * Runs an action at the start of the next tick. (Minecraft's own addScheduledTask runs at once when called on the
     * server thread, e.g. inside a window being closed.)
     */
    static void nextTick(Runnable action) {
        NEXT_TICK.add(action);
    }

    public static void tick(MinecraftServer server) {
        Guard.run("start", () -> worldsLoaded(server));
        ScopeSwitch.sync();
        CarefulBreak.tick(server);
        BotSleep.tick(server);
        LuServer.tick(server);
        for (int n = NEXT_TICK.size(); n > 0; n--) Guard.run("tick", NEXT_TICK.poll());
    }

    public static void playerJoined(EntityPlayerMP player) {
        Guard.run("join", () -> {
            worldsLoaded(player.mcServer);
            if (Vault.has(player.getUniqueID())) {
                try {
                    Vault.restore(player);
                } catch (IOException e) {
                    Guard.LOG.error(ServerText.get("log.restore_failed", player.getName()), e);
                    Guard.notice(ServerText.get("editor.restore_failed", player.getName(), String.valueOf(e)));
                }
            }
            Guard.tellIfOp(player);
        });
        CarefulBreak.joined(player);
        LuServer.joined(player);
    }

    public static void playerLeft(EntityPlayerMP player) {
        Guard.run("leave", () -> Editor.end(player, false));
        CarefulBreak.left(player);
        LuServer.left(player);
    }

    public static void customPayload(NetHandlerPlayServer handler, CPacketCustomPayload packet) {
        LuServer.received(handler, packet);
    }

    /** NetHandlerPlayServer.processTryUseItemOnBlock, at its start. */
    public static void clickStarted(NetHandlerPlayServer handler) {
        LuServer.click(handler, AccuratePlacement::clickStarted);
    }

    /** NetHandlerPlayServer.processTryUseItemOnBlock, before each return. */
    public static void clickDone(NetHandlerPlayServer handler) {
        LuServer.click(handler, AccuratePlacement::clickDone);
    }

    /** EntityPlayerMP.handleFalling, at its start: the client's "on the ground" as the server believes it. */
    public static boolean onGround(EntityPlayerMP player, boolean claimed) {
        return FALL.onGround(claimed, () -> !player.world.getCollisionBoxes(player, underFeet(player)).isEmpty());
    }

    /** A thin slab right under the player's box: blocks and solid entities (boats, shulkers) in it carry the player. */
    private static AxisAlignedBB underFeet(EntityPlayerMP player) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        return new AxisAlignedBB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ);
    }

    /** FurnaceRecipes.getSmeltingResult, at its start: a saved furnace recipe's result, null = Minecraft's own. */
    public static ItemStack smeltResult(ItemStack input) {
        return StationRecipes.smeltResult(input);
    }

    /** PotionHelper.isReagent, at its start: true = a saved brewing recipe's ingredient (else Minecraft's own answer). */
    public static boolean brewIngredient(ItemStack stack) {
        return StationRecipes.ingredient(stack);
    }

    /** PotionHelper.hasConversions, at its start: true = a saved brewing recipe brews this bottle. */
    public static boolean brews(ItemStack bottle, ItemStack ingredient) {
        return StationRecipes.brews(bottle, ingredient);
    }

    /** PotionHelper.doReaction, at its start: a saved brewing recipe's result for the bottle, null = Minecraft's own. */
    public static ItemStack brewResult(ItemStack ingredient, ItemStack bottle) {
        return StationRecipes.brewResult(ingredient, bottle);
    }

    /**
     * RecipeBookServer.sendPacket, at its start (#12): true = the player has no connection (a crash on a player's server:
     * a recipe advancement fired for such a player), so the message is skipped; the recipe stays unlocked and reaches
     * the game with the whole book at the next join. Logged once per player with where it came from.
     */
    public static boolean noConnection(EntityPlayerMP player) {
        if (player.connection != null) return false;
        if (NO_CONNECTION_TOLD.add(player.getUniqueID())) Guard.LOG.warn(ServerText.get("log.recipebook_no_connection", player.getName()), new IllegalStateException());
        return true;
    }

    /** Before the death drops: the real items are back, so the game rules decide what is dropped. */
    public static void playerDying(EntityPlayerMP player) {
        Guard.run("death", () -> Editor.end(player, true));
    }

    public static void serverStopping(MinecraftServer server) {
        Guard.run("stop", Editor::endAll);
    }
}
