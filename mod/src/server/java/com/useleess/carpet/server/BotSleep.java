package com.useleess.carpet.server;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;

/**
 * Bots that sleep by themselves (#11, {@code /autosleep}): a Carpet bot with it on goes to bed each night in a free bed
 * within reach (Minecraft's own reach: 3 blocks across, 2 up or down), so it never holds up the night skip. Minecraft
 * wakes it in the morning as usual. The choice is saved per world by the bot's UUID. A failure switches it off for
 * every bot, logged and told to the ops; the game keeps running.
 */
final class BotSleep {
    static final String ON = "on";
    private static final int ACROSS = 3, UP_DOWN = 2;

    private static ChoiceStore store;
    private static boolean broken;

    private BotSleep() {}

    static void open(File file) {
        try {
            store = ChoiceStore.open(file);
        } catch (IOException | RuntimeException e) {
            store = new ChoiceStore(file);
            Guard.LOG.error(ServerText.get("log.botsleep_load_failed", file), e);
            Guard.notice(ServerText.get("botsleep.load_failed"));
        }
    }

    static boolean working() {
        return store != null && !broken;
    }

    static boolean isBot(Object player) {
        return player instanceof EntityPlayerMPFake;
    }

    static boolean on(EntityPlayerMP bot) {
        return working() && ON.equals(store.get(bot.getUniqueID()));
    }

    /** Switches it on or off for the bot (saved at once); off wakes a sleeping bot. */
    static void set(EntityPlayerMP bot, boolean on) throws IOException {
        store.set(bot.getUniqueID(), on ? ON : null);
        if (!on && bot.isPlayerSleeping()) bot.wakeUpPlayer(true, true, false);
    }

    /** Once a second: every bot with it on that is awake at night goes to bed. */
    static void tick(MinecraftServer server) {
        if (!working() || server.getTickCounter() % 20 != 0) return;
        try {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                if (isBot(player) && on(player)) sleep(player);
            }
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            Guard.LOG.error(ServerText.get("log.botsleep_failed"), e);
            Guard.notice(ServerText.get("botsleep.failed", String.valueOf(e)));
        }
    }

    private static void sleep(EntityPlayerMP bot) {
        World world = bot.world;
        if (bot.isPlayerSleeping() || world.isDaytime() || !world.provider.isSurfaceWorld()) return;
        BlockPos bed = freeBed(bot);
        if (bed != null && bot.trySleep(bed) == EntityPlayer.SleepResult.OK) {
            world.setBlockState(bed, world.getBlockState(bed).withProperty(BlockBed.OCCUPIED, true), 4);
        }
    }

    /** The head of the nearest bed within reach that nobody lies in, or null. */
    static BlockPos freeBed(EntityPlayerMP bot) {
        BlockPos at = new BlockPos(bot), best = null;
        for (BlockPos pos : BlockPos.getAllInBox(at.add(-ACROSS, -UP_DOWN, -ACROSS), at.add(ACROSS, UP_DOWN, ACROSS))) {
            BlockPos head = head(bot.world, pos);
            if (head != null && (best == null || at.distanceSq(head) < at.distanceSq(best))) best = head.toImmutable();
        }
        return best;
    }

    private static BlockPos head(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBed)) return null;
        if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
            pos = pos.offset(state.getValue(BlockHorizontal.FACING));
            state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockBed)) return null;
        }
        return state.getValue(BlockBed.OCCUPIED) ? null : pos;
    }
}
