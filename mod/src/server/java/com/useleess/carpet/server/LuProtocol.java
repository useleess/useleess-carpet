package com.useleess.carpet.server;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Litematica Upgraded messages a Carpet server answers (game-free). LU's network channel puts the message's number
 * first, then its fields, Netty style (big-endian): number 0 = server to client "what the server supports", number 1 =
 * client to server "the state my next placement at this spot should end with", number 5 = client to server "move items
 * out of a shulker box in my inventory". LU's printer and Easy Place use both. Every other LU message is left alone, so
 * LU keeps those features to itself.
 */
final class LuProtocol {
    static final String CHANNEL = "litematica_upgraded";
    static final int CAPABILITIES = 0, PLACEMENT_INTENT = 1, SHULKER_TAKE = 5;
    private static final int MAX_STATE_BYTES = 1024;
    /**
     * Properties kept as the placement made them: the world's (power, growth, neighbours), the block's kind (variant, colour).
     * LU's list plus layers: on Forge LU's drop check refuses those, a vanilla block has no drop count per state.
     */
    private static final Set<String> WORLD_MADE = new HashSet<>(Arrays.asList(
            "powered", "power", "triggered", "extended", "enabled", "locked", "attached", "disarmed", "occupied", "part", "short",
            "check_decay", "decayable", "snowy", "age", "conditional", "has_bottle_0", "has_bottle_1", "has_bottle_2", "contents",
            "eye", "explode", "nodrop", "has_record", "variant", "type", "color", "wet", "damage", "seamless", "legacy_data", "layers"));

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

    /** A placement request: the block (name) and properties the next placement at x, y, z should end with. */
    static final class Intent {
        final int x, y, z;
        final String block;
        final Map<String, String> properties = new LinkedHashMap<>();

        /** A state written name[key=value,...]. */
        Intent(int x, int y, int z, String state) {
            this.x = x;
            this.y = y;
            this.z = z;
            int bracket = state.indexOf('[');
            block = bracket < 0 ? state : state.substring(0, bracket);
            if (bracket < 0 || !state.endsWith("]")) return;
            for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) properties.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
    }

    /** Whether a client may choose this property: a lever's switch is the player's, an iron door or trapdoor opens by redstone only. */
    static boolean settable(String property, boolean lever, boolean iron) {
        if (lever && property.equals("powered")) return true;
        if (iron && property.equals("open")) return false;
        return !WORLD_MADE.contains(property);
    }

    /**
     * Capabilities: printer allowed (it is LU's own client feature), accurate placement with full states and shulker
     * restock as given, no air placement, vanilla range, no server-side saving or edits.
     */
    static byte[] capabilities(boolean shulkerRestock, boolean accurate) {
        byte accurateByte = (byte) (accurate ? 1 : 0);
        ByteBuffer out = ByteBuffer.allocate(1 + 4 + 4 + 2 + 4 + 1);
        out.put((byte) CAPABILITIES);
        out.put((byte) 1).put(accurateByte).put((byte) 0).put((byte) (shulkerRestock ? 1 : 0));
        out.putFloat(0);
        out.put((byte) 0).put((byte) 0);
        out.putInt(0);
        out.put(accurateByte);
        return out.array();
    }

    /** The placement request in a message, or null for any other, cut-short or oversized message. */
    static Intent intent(ByteBuffer message) {
        try {
            if (message.get() != PLACEMENT_INTENT) return null;
            int x = message.getInt(), y = message.getInt(), z = message.getInt();
            int length = message.getShort() & 0xFFFF;
            if (length > MAX_STATE_BYTES || length != message.remaining()) return null;
            byte[] state = new byte[length];
            message.get(state);
            return new Intent(x, y, z, new String(state, StandardCharsets.UTF_8));
        } catch (BufferUnderflowException e) {
            return null;
        }
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
