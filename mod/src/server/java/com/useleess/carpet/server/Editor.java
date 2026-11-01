package com.useleess.carpet.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The open editors, one per op. */
final class Editor {
    private static final Map<UUID, EditorSession> SESSIONS = new HashMap<>();

    private Editor() {}

    static EditorSession get(EntityPlayerMP player) {
        return SESSIONS.get(player.getUniqueID());
    }

    static void forget(EntityPlayerMP player) {
        SESSIONS.remove(player.getUniqueID());
    }

    /** Opens the editor: the inventory is saved and emptied first; if that fails nothing opens and nothing is lost. */
    static void open(EntityPlayerMP player) {
        EditorSession open = get(player);
        if (open != null) {
            open.showEditor();
            return;
        }
        try {
            if (Vault.has(player.getUniqueID())) Vault.restore(player);
            Vault.store(player);
        } catch (IOException e) {
            Guard.LOG.error(ServerText.get("log.vault_failed", player.getName()), e);
            player.sendMessage(new TextComponentString(ServerText.get("editor.cannot_save_inventory", String.valueOf(e.getMessage()))));
            return;
        }
        EditorSession session = new EditorSession(player);
        SESSIONS.put(player.getUniqueID(), session);
        try {
            session.showEditor();
        } catch (RuntimeException | LinkageError e) {
            session.end(false);
            throw e;
        }
    }

    /** The player's editor ends (death, leaving): items back, window closed when asked. */
    static void end(EntityPlayerMP player, boolean closeWindow) {
        EditorSession session = get(player);
        if (session != null) session.end(closeWindow);
    }

    static void endAll() {
        for (EditorSession s : new ArrayList<>(SESSIONS.values())) s.end(true);
    }
}
