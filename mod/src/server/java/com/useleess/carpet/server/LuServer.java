package com.useleess.carpet.server;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * This server's side of Litematica Upgraded's channel. Players with LU are told what the server answers: at join while a
 * rule is on, and everyone on each change (a feature that switched itself off counts as a change).
 */
final class LuServer {
    private static final LuFeature SHULKER = feature(ShulkerTake.RULE, "log.shulker_failed", "shulker.failed");
    private static final LuFeature ACCURATE = feature(AccuratePlacement.RULE, "log.accurate_failed", "accurate.failed");
    private static byte[] told = LuProtocol.capabilities(false, false);

    private LuServer() {}

    private static LuFeature feature(String rule, String logKey, String noticeKey) {
        return new LuFeature(Rule.named(rule), e -> {
            Guard.LOG.error(ServerText.get(logKey), e);
            Guard.notice(ServerText.get(noticeKey, String.valueOf(e)));
        });
    }

    static byte[] capabilities() {
        return LuProtocol.capabilities(SHULKER.on(), ACCURATE.on());
    }

    static void tick(MinecraftServer server) {
        ACCURATE.whenOn(AccuratePlacement::tick);
        byte[] now = capabilities();
        if (Arrays.equals(now, told)) return;
        told = now;
        tell(server.getPlayerList().getPlayers().toArray(new EntityPlayerMP[0]));
    }

    /** Off is LU's own default, so a joining player only needs telling while a rule is on. */
    static void joined(EntityPlayerMP player) {
        if (SHULKER.on() || ACCURATE.on()) tell(player);
    }

    static void left(EntityPlayerMP player) {
        AccuratePlacement.forget(player);
    }

    /** NetHandlerPlayServer.processCustomPayload, at its start (it runs once on the network thread, then on the server's). */
    static void received(NetHandlerPlayServer handler, CPacketCustomPayload packet) {
        EntityPlayerMP player = handler.player;
        if (!LuProtocol.CHANNEL.equals(packet.getChannelName()) || !player.mcServer.isCallingFromMinecraftThread()) return;
        SHULKER.whenOn(() -> {
            LuProtocol.Take take = LuProtocol.take(message(packet));
            if (take != null) ShulkerTake.take(player, take);
        });
        ACCURATE.whenOn(() -> {
            LuProtocol.Intent intent = LuProtocol.intent(message(packet));
            if (intent != null) AccuratePlacement.intent(player, intent);
        });
    }

    private static ByteBuffer message(CPacketCustomPayload packet) {
        return packet.getBufferData().duplicate().nioBuffer();
    }

    /** A step around NetHandlerPlayServer.processTryUseItemOnBlock (like above, only its run on the server thread counts). */
    static void click(NetHandlerPlayServer handler, Consumer<EntityPlayerMP> step) {
        EntityPlayerMP player = handler.player;
        if (player.mcServer.isCallingFromMinecraftThread()) ACCURATE.whenOn(() -> step.accept(player));
    }

    private static void tell(EntityPlayerMP... players) {
        try {
            byte[] capabilities = capabilities();
            for (EntityPlayerMP p : players) {
                p.connection.sendPacket(new SPacketCustomPayload(LuProtocol.CHANNEL, new PacketBuffer(Unpooled.wrappedBuffer(capabilities))));
            }
        } catch (RuntimeException | LinkageError e) {
            SHULKER.fail(e);
            ACCURATE.fail(e);
        }
    }
}
