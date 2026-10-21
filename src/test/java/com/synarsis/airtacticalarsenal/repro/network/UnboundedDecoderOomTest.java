package com.synarsis.airtacticalarsenal.repro.network;

import com.synarsis.airtacticalarsenal.network.LaunchShahedByRoutePacket;
import com.synarsis.airtacticalarsenal.network.SaveOrlanRoutePacket;
import com.synarsis.airtacticalarsenal.network.SaveShahedRoutePacket;
import com.synarsis.airtacticalarsenal.network.SaveTabletCoordsPacket;
import com.synarsis.airtacticalarsenal.network.UpdateOrlanRoutePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

@DisplayName("F-5: client->server codecs allocate attacker-declared sizes before reading the body")
class UnboundedDecoderOomTest {

    private static final int HOSTILE_COUNT = Integer.MAX_VALUE;

    private static RegistryFriendlyByteBuf buf(Consumer<RegistryFriendlyByteBuf> fill) {
        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(raw, RegistryAccess.EMPTY);
        fill.accept(b);
        return b;
    }

    private static <T> void assertRejectsHostileCountWithoutOom(
            String packetName,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            RegistryFriendlyByteBuf hostilePayload) {

        int payloadBytes = hostilePayload.readableBytes();
        try {
            codec.decode(hostilePayload);
        } catch (OutOfMemoryError oom) {
            throw new AssertionError(
                    packetName + ": a " + payloadBytes + "-byte packet claiming " + HOSTILE_COUNT
                            + " elements drove the decoder into OutOfMemoryError (" + oom.getMessage()
                            + "). The size came off the wire and was allocated before the body was"
                            + " read, so this crashes the server from inside the network decoder,"
                            + " ahead of any handler or permission check. A bounded codec"
                            + " (ByteBufCodecs.list(max)) would have rejected the count instead.",
                    oom);
        } catch (RuntimeException expected) {
        }
    }

    @Test
    void saveShahedRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "SaveShahedRoutePacket",
                SaveShahedRoutePacket.STREAM_CODEC,
                buf(b -> {
                    b.writeBlockPos(net.minecraft.core.BlockPos.ZERO);
                    b.writeInt(HOSTILE_COUNT);
                }));
    }

    @Test
    void saveOrlanRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "SaveOrlanRoutePacket",
                SaveOrlanRoutePacket.STREAM_CODEC,
                buf(b -> {
                    b.writeBlockPos(net.minecraft.core.BlockPos.ZERO);
                    b.writeInt(HOSTILE_COUNT);
                }));
    }

    @Test
    void launchShahedByRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "LaunchShahedByRoutePacket",
                LaunchShahedByRoutePacket.STREAM_CODEC,
                buf(b -> b.writeInt(HOSTILE_COUNT)));
    }

    @Test
    void updateOrlanRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "UpdateOrlanRoutePacket",
                UpdateOrlanRoutePacket.STREAM_CODEC,
                buf(b -> {
                    b.writeInt(0);
                    b.writeInt(HOSTILE_COUNT);
                }));
    }

    @Test
    void saveTabletCoordsPacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "SaveTabletCoordsPacket",
                SaveTabletCoordsPacket.STREAM_CODEC,
                buf(b -> {
                    b.writeInt(1);
                    b.writeInt(0);
                    b.writeInt(HOSTILE_COUNT);
                }));
    }
}
