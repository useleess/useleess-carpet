package com.useleess.carpet.server;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Litematica Upgraded's message layout, byte for byte as its MessageCapabilities / MessageShulkerTake write it. */
class LuProtocolTest {
    @Test void capabilitiesSayPrinterAllowedAndShulkerRestockOnly() {
        ByteBuffer m = ByteBuffer.wrap(LuProtocol.capabilities(true));
        assertEquals(0, m.get(), "message number");
        assertEquals(1, m.get(), "printer allowed");
        assertEquals(0, m.get(), "accurate placement");
        assertEquals(0, m.get(), "air placement");
        assertEquals(1, m.get(), "shulker restock");
        assertEquals(0f, m.getFloat(), "range: vanilla");
        assertEquals(0, m.get(), "saving");
        assertEquals(0, m.get(), "edits");
        assertEquals(0, m.getInt(), "upload KiB");
        assertFalse(m.hasRemaining());
    }

    @Test void capabilitiesOffDifferOnlyInTheShulkerFlag() {
        byte[] on = LuProtocol.capabilities(true), off = LuProtocol.capabilities(false);
        assertEquals(on.length, off.length);
        for (int i = 0; i < on.length; i++) assertEquals(i == 4 ? 0 : on[i], off[i], "byte " + i);
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
