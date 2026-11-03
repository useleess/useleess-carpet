package com.useleess.carpet.server;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * The two Litematica Upgraded messages a Carpet server answers (game-free). LU's network channel puts the message's
 * number first, then its fields, Netty style (big-endian): number 0 = server to client "what the server supports",
 * number 5 = client to server "move items out of a shulker box in my inventory". LU's printer and Easy Place both ask
 * with number 5. Every other LU message is left alone, so LU keeps those features to itself.
 */
final class LuProtocol {
    static final String CHANNEL = "litematica_upgraded";
    static final int CAPABILITIES = 0, SHULKER_TAKE = 5;

    private LuProtocol() {}

    /** A shulker request: up to count of item:meta from slot innerSlot of the box at inventory slot boxSlot. */
    static final class Take {
        final int boxSlot, innerSlot, itemId, meta, count;

        Take(int boxSlot, int innerSlot, int itemId, int meta, int count) {
            this.boxSlot = boxSlot;
            this.innerSlot = innerSlot;
            this.itemId = itemId;
            this.meta = meta;
            this.count = count;
        }
    }

    /**
     * Capabilities: printer allowed (it is LU's own client feature), no accurate or air placement, shulker restock as
     * given, vanilla range, no server-side saving or edits.
     */
    static byte[] capabilities(boolean shulkerRestock) {
        ByteBuffer out = ByteBuffer.allocate(1 + 4 + 4 + 2 + 4);
        out.put((byte) CAPABILITIES);
        out.put((byte) 1).put((byte) 0).put((byte) 0).put((byte) (shulkerRestock ? 1 : 0));
        out.putFloat(0);
        out.put((byte) 0).put((byte) 0);
        out.putInt(0);
        return out.array();
    }

    /** The shulker request in a message, or null for any other or cut-short message. */
    static Take take(ByteBuffer message) {
        try {
            if (message.get() != SHULKER_TAKE) return null;
            return new Take(message.get() & 0xFF, message.get() & 0xFF, message.getInt(), message.getShort(), message.get() & 0xFF);
        } catch (BufferUnderflowException e) {
            return null;
        }
    }
}
