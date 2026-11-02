package com.useleess.carpet.server;

import net.minecraft.command.CommandHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The few places Minecraft and Carpet call into us (patched in by the build, see tools/ServerLayer): server loaded,
 * every tick, player joined / left / dying, server stopping. Each one is guarded: a failure switches the feature off.
 */
public final class Hooks {
    private static final Queue<Runnable> NEXT_TICK = new ArrayDeque<>();

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
    }

    public static void playerLeft(EntityPlayerMP player) {
        Guard.run("leave", () -> Editor.end(player, false));
        CarefulBreak.left(player);
    }

    /** Before the death drops: the real items are back, so the game rules decide what is dropped. */
    public static void playerDying(EntityPlayerMP player) {
        Guard.run("death", () -> Editor.end(player, true));
    }

    public static void serverStopping(MinecraftServer server) {
        Guard.run("stop", Editor::endAll);
    }
}
