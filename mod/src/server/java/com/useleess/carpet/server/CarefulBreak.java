package com.useleess.carpet.server;

import carpet.logging.LoggerOptions;
import carpet.logging.LoggerRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Careful break per player: Carpet's logger {@code carefulBreak} has the options {@code sneak} (default) and
 * {@code always} (patched in by the build), and {@link #wants} replaces the Sneak check where the drop goes into the
 * miner's inventory. The choice is saved per world and put back when the player joins. Nothing here may take the
 * game down: a failure behaves like Kisde Carpet (Sneak needed) and is logged and told once.
 */
public final class CarefulBreak {
    static final String LOGGER = "carefulBreak";
    static final String ALWAYS = "always";
    static final String SNEAK = "sneak";

    private static ChoiceStore store;
    private static boolean saveBroken;
    private static boolean wantsBroken;
    private static final Map<UUID, String> KNOWN = new HashMap<>();
    private static final Set<UUID> TOLD = new HashSet<>();

    private CarefulBreak() {}

    /** Opens the saved choices; a damaged file is told to the ops and nothing is restored. */
    static void open(File file) {
        try {
            store = ChoiceStore.open(file);
        } catch (IOException | RuntimeException e) {
            store = new ChoiceStore(file);
            Guard.LOG.error(ServerText.get("log.careful_load_failed", file), e);
            Guard.notice(ServerText.get("careful.load_failed"));
        }
    }

    /** Called in place of the Sneak check of the careful-break drop code (patched in by the build). */
    public static boolean wants(EntityPlayerMP player) {
        try {
            if (ALWAYS.equals(current(player.getName()))) return true;
        } catch (RuntimeException | LinkageError e) {
            if (!wantsBroken) {
                wantsBroken = true;
                Guard.LOG.error(ServerText.get("log.careful_failed"), e);
            }
            tell(player);
        }
        return player.isSneaking();
    }

    /** The player's subscription: null = none, else "always" or "sneak". */
    static String current(String name) {
        Map<String, LoggerOptions> subscriptions = LoggerRegistry.getPlayerSubscriptions(name);
        LoggerOptions mine = subscriptions == null ? null : subscriptions.get(LOGGER);
        if (mine == null) return null;
        return ALWAYS.equals(mine.option) ? ALWAYS : SNEAK;
    }

    static void joined(EntityPlayerMP player) {
        if (store == null || saveBroken) return;
        try {
            String saved = store.get(player.getUniqueID());
            if (saved != null && current(player.getName()) == null) {
                LoggerRegistry.subscribePlayer(player.mcServer, player.getName(), LOGGER, saved, null);
            }
            sync(player, true);
        } catch (IOException | RuntimeException | LinkageError e) {
            saveFailed(player, e);
        }
    }

    static void tick(MinecraftServer server) {
        if (store == null || saveBroken) return;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            try {
                sync(player, false);
            } catch (IOException | RuntimeException | LinkageError e) {
                saveFailed(player, e);
                return;
            }
        }
    }

    static void left(EntityPlayerMP player) {
        if (store == null || saveBroken) return;
        try {
            sync(player, false);
        } catch (IOException | RuntimeException | LinkageError e) {
            saveFailed(player, e);
        }
        KNOWN.remove(player.getUniqueID());
    }

    /** Writes the file when the player's choice differs from the last one seen (on joining: from the saved one). */
    private static void sync(EntityPlayerMP player, boolean joining) throws IOException {
        UUID id = player.getUniqueID();
        String now = current(player.getName());
        if (joining) {
            KNOWN.put(id, now);
            if (now == null || now.equals(store.get(id))) return;
        } else if (!KNOWN.containsKey(id) || Objects.equals(KNOWN.get(id), now)) {
            KNOWN.putIfAbsent(id, now);
            return;
        }
        KNOWN.put(id, now);
        store.set(id, now);
    }

    private static void saveFailed(EntityPlayerMP player, Throwable e) {
        if (!saveBroken) Guard.LOG.error(ServerText.get("log.careful_save_failed"), e);
        saveBroken = true;
        tell(player);
    }

    private static void tell(EntityPlayerMP player) {
        if (TOLD.add(player.getUniqueID())) player.sendMessage(new TextComponentString(ServerText.get("careful.failed")));
    }
}
