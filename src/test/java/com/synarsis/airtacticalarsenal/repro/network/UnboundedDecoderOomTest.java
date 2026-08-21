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

/**
 * REPRO: network F-5 — unbounded collection sizing in client-to-server stream codecs.
 *
 * <p>Every client-to-server codec in this mod reads an element count straight off the wire
 * with {@code buf.readInt()} and immediately hands it to {@code new ArrayList<>(n)} or
 * {@code new int[n]}. Both allocate storage for {@code n} elements BEFORE a single element is
 * read, so the allocation is driven by the attacker's declared count and not by how many bytes
 * the packet actually carries.
 *
 * <p>That is the whole attack: a payload of roughly a dozen bytes claims
 * {@link Integer#MAX_VALUE} elements and the server tries to allocate an array of that length
 * while still inside the network decoder — before any handler, permission check, or
 * {@code enqueueWork} runs. The resulting {@link OutOfMemoryError} escapes the decode path.
 *
 * <p>Contrast with the codecs that size a plain {@code new ArrayList<>()} and fill it in a loop
 * (e.g. {@code LaunchFromUnifiedTerminalPacket}): those still lack a bound, but the loop hits
 * the end of the buffer and throws a decode error long before memory runs out. Those are NOT
 * reproduced here — this test targets only the codecs that allocate up front, because those are
 * the ones a tiny packet can weaponise.
 *
 * <p>EXPECTED (a bounded codec): decode rejects the absurd count with a decode-level exception,
 * the way {@code ByteBufCodecs.list(maxSize)} does.
 * ACTUAL (current code): {@link OutOfMemoryError}.
 *
 * <p>Note on safety: {@link Integer#MAX_VALUE} exceeds the JVM's maximum array length, so the
 * error is raised by the array-size check rather than by exhausting the heap. The test neither
 * allocates gigabytes nor destabilises the JVM.
 */
@DisplayName("F-5: client->server codecs allocate attacker-declared sizes before reading the body")
class UnboundedDecoderOomTest {

    /** Element count an attacker writes into the packet. Larger than any array the JVM allows. */
    private static final int HOSTILE_COUNT = Integer.MAX_VALUE;

    private static RegistryFriendlyByteBuf buf(Consumer<RegistryFriendlyByteBuf> fill) {
        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf b = new RegistryFriendlyByteBuf(raw, RegistryAccess.EMPTY);
        fill.accept(b);
        return b;
    }

    /**
     * Decodes a hostile payload and reports how the codec reacted.
     *
     * @return nothing; fails the test if an {@link OutOfMemoryError} escapes the decoder.
     */
    private static <T> void assertRejectsHostileCountWithoutOom(
            String packetName,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            RegistryFriendlyByteBuf hostilePayload) {

        int payloadBytes = hostilePayload.readableBytes();
        try {
            codec.decode(hostilePayload);
            // A bounded codec would normally throw; returning is acceptable too, as long as it
            // did not try to size the collection from the wire.
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
            // Ran out of buffer / decode error: the bound-less allocation did not happen.
        }
    }

    @Test
    void saveShahedRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "SaveShahedRoutePacket",
                SaveShahedRoutePacket.STREAM_CODEC,
                buf(b -> {
                    b.writeBlockPos(net.minecraft.core.BlockPos.ZERO);
                    b.writeInt(HOSTILE_COUNT); // waypoint count -> new ArrayList<>(n)
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
                buf(b -> b.writeInt(HOSTILE_COUNT))); // launcher-position count comes first here
    }

    @Test
    void updateOrlanRoutePacketAllocatesFromTheWire() {
        assertRejectsHostileCountWithoutOom(
                "UpdateOrlanRoutePacket",
                UpdateOrlanRoutePacket.STREAM_CODEC,
                buf(b -> {
                    b.writeInt(0);            // orlan entity id
                    b.writeInt(HOSTILE_COUNT);
                }));
    }

    @Test
    void saveTabletCoordsPacketAllocatesFromTheWire() {
        // This one nests: an outer entry count, then a per-entry int[] length. The inner
        // length is the sharper edge -- one entry is enough.
        assertRejectsHostileCountWithoutOom(
                "SaveTabletCoordsPacket",
                SaveTabletCoordsPacket.STREAM_CODEC,
                buf(b -> {
                    b.writeInt(1);            // one entry
                    b.writeInt(0);            // slot key
                    b.writeInt(HOSTILE_COUNT); // int[] length -> new int[n]
                }));
    }
}
