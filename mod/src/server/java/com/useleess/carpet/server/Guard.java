package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Pillar 2: nothing of ours may take the server down. Every entry point from Minecraft or Carpet runs through
 * {@link #run}: a failure is logged with its details, the feature switches itself off (open editors give the
 * inventories back first) and the ops are told in plain words. Notices (skipped recipe files, a failed restore) are
 * kept until an op is online to read them.
 */
final class Guard {
    static final Logger LOG = LogManager.getLogger("UseleessCarpet");
    private static final List<String> NOTICES = new ArrayList<>();
    private static MinecraftServer server;
    private static boolean off;

    private Guard() {}

    static void attach(MinecraftServer s) {
        server = s;
    }

    static boolean off() {
        return off;
    }

    /** Runs a hook; the feature is already off = nothing happens. */
    static void run(String what, Runnable action) {
        if (off) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            fail(what, e);
        }
    }

    static void fail(String what, Throwable e) {
        boolean first = !off;
        off = true;
        LOG.error(ServerText.get("log.recipes_failed", what), e);
        if (!first) return;
        Editor.endAll();
        notice(ServerText.get("recipes.off_after_error", what, String.valueOf(e)));
    }

    /** Logged now, told to every op online and to each op who joins later (until read once by someone). */
    static void notice(String text) {
        LOG.warn(text);
        NOTICES.add(text);
        if (server != null) for (EntityPlayerMP p : server.getPlayerList().getPlayers()) tellIfOp(p);
    }

    /** An op who joins gets the notices. */
    static void tellIfOp(EntityPlayerMP player) {
        if (server == null || NOTICES.isEmpty() || !server.getPlayerList().canSendCommands(player.getGameProfile())) return;
        for (String n : NOTICES) player.sendMessage(new TextComponentString(n));
    }

    static List<String> notices() {
        return NOTICES;
    }
}
