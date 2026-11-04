package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Litematica Upgraded's message layout, byte for byte as its MessageCapabilities / MessageShulkerTake write it. */
class LuProtocolTest {
    @Test void capabilitiesSayPrinterAllowedAndShulkerRestockOnly() {
        ByteBuffer m = ByteBuffer.wrap(LuProtocol.capabilities(true, false));
        assertEquals(0, m.get(), "message number");
        assertEquals(1, m.get(), "printer allowed");
        assertEquals(0, m.get(), "accurate placement");
        assertEquals(0, m.get(), "air placement");
        assertEquals(1, m.get(), "shulker restock");
        assertEquals(0f, m.getFloat(), "range: vanilla");
        assertEquals(0, m.get(), "saving");
        assertEquals(0, m.get(), "edits");
        assertEquals(0, m.getInt(), "upload KiB");
        assertEquals(0, m.get(), "accurate states");
        assertFalse(m.hasRemaining());
    }

    @Test void capabilitiesOffDifferOnlyInTheShulkerFlag() {
        byte[] on = LuProtocol.capabilities(true, false), off = LuProtocol.capabilities(false, false);
        assertEquals(on.length, off.length);
        for (int i = 0; i < on.length; i++) assertEquals(i == 4 ? 0 : on[i], off[i], "byte " + i);
    }

    @Test void accurateTurnsOnBothItsFlagsAndNothingElse() {
        byte[] on = LuProtocol.capabilities(false, true), off = LuProtocol.capabilities(false, false);
        assertEquals(on.length, off.length);
        for (int i = 0; i < on.length; i++) assertEquals(i == 2 || i == on.length - 1 ? 1 : off[i], on[i], "byte " + i);
    }

    private static ByteBuffer intentMessage(String state, int claimedLength) {
        byte[] text = state.getBytes(StandardCharsets.UTF_8);
        ByteBuffer m = ByteBuffer.allocate(15 + text.length);
        m.put((byte) 1).putInt(-30).putInt(255).putInt(70000).putShort((short) claimedLength).put(text).flip();
        return m;
    }

    @Test void aPlacementRequestIsRead() {
        String state = "minecraft:oak_stairs[facing=east,half=top,shape=straight]";
        LuProtocol.Intent i = LuProtocol.intent(intentMessage(state, state.length()));
        assertNotNull(i);
        assertEquals(-30, i.x);
        assertEquals(255, i.y);
        assertEquals(70000, i.z);
        assertEquals("minecraft:oak_stairs", i.block);
        assertEquals("east", i.properties.get("facing"));
        assertEquals("top", i.properties.get("half"));
        assertEquals(3, i.properties.size());
    }

    @Test void aStateWithoutPropertiesHasNone() {
        assertTrue(new LuProtocol.Intent(0, 0, 0, "minecraft:dirt").properties.isEmpty());
        assertEquals("minecraft:dirt", new LuProtocol.Intent(0, 0, 0, "minecraft:dirt[broken").block);
    }

    @Test void badPlacementRequestsAreIgnored() {
        assertNull(LuProtocol.intent(intentMessage("minecraft:dirt", 3)), "length does not fill the message");
        assertNull(LuProtocol.intent(ByteBuffer.wrap(new byte[] {1, 0, 0})), "cut short");
        assertNull(LuProtocol.intent(ByteBuffer.wrap(new byte[] {5, 0, 0, 0})), "another message");
        char[] huge = new char[1025];
        Arrays.fill(huge, 'a');
        assertNull(LuProtocol.intent(intentMessage(new String(huge), huge.length)), "oversized");
    }

    @Test void onlyWhatAPlayerCouldChooseIsSettable() {
        for (String p : new String[] {"facing", "half", "shape", "open", "delay", "mode", "hinge", "rotation", "axis"}) {
            assertTrue(LuProtocol.settable(p, false, false), p);
        }
        for (String p : new String[] {"powered", "power", "variant", "color", "age", "snowy", "layers"}) {
            assertFalse(LuProtocol.settable(p, false, false), p);
        }
        assertTrue(LuProtocol.settable("powered", true, false), "a lever's switch");
        assertFalse(LuProtocol.settable("open", false, true), "iron opens by redstone only");
    }

    @Test void aShulkerRequestIsRead() {
        ByteBuffer m = ByteBuffer.allocate(10);
        m.put((byte) 5).put((byte) 200).put((byte) 26).putInt(1).putShort((short) 3).put((byte) 64).flip();
        LuProtocol.Take t = LuProtocol.take(m);
        assertNotNull(t);
        assertEquals(200, t.boxSlot, "unsigned");
        assertEquals(26, t.innerSlot);
        assertEquals(1, t.itemId);
        assertEquals(3, t.meta);
        assertEquals(64, t.count);
    }

    @Test void otherAndCutShortMessagesAreIgnored() {
        assertNull(LuProtocol.take(ByteBuffer.wrap(new byte[] {1, 0, 0, 0})), "LU's other messages are not ours");
        assertNull(LuProtocol.take(ByteBuffer.wrap(new byte[] {5, 0, 0, 0})), "cut short");
        assertNull(LuProtocol.take(ByteBuffer.wrap(new byte[0])), "empty");
    }
}
