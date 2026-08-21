package com.synarsis.airtacticalarsenal.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Upper bounds for collections decoded from the wire.
 *
 * <p>A stream codec that reads an element count and sizes storage from it is trusting the sender
 * with an allocation. Because the count is read before the elements, the allocation follows what
 * the sender claimed rather than how many bytes actually arrived, so a payload of a few bytes can
 * demand an array of billions of entries and take the server down inside the network decoder --
 * before any handler, ownership check or {@code enqueueWork} runs.
 *
 * <p>{@link #readCount} closes that gap: the count is validated against a declared ceiling and
 * rejected as a decode error, which is the failure mode the network layer already knows how to
 * handle (the offending connection is dropped, the server keeps running).
 *
 * <p>The ceilings below sit well above what the mod's own UI can produce -- route screens cap
 * waypoints at 20 and tablets at 10 launchers -- so legitimate traffic never approaches them.
 */
public final class PacketLimits {

    /** Waypoints in a single saved route. The route screens allow 20. */
    public static final int MAX_WAYPOINTS = 64;

    /** Launcher positions in one packet. Tablets and terminals allow 10. */
    public static final int MAX_LAUNCHERS = 64;

    /** Saved coordinate slots on a tablet. */
    public static final int MAX_SAVED_COORD_SLOTS = 64;

    /** Coordinates per saved slot; the format is a fixed (x, z) pair. */
    public static final int MAX_SAVED_COORD_VALUES = 2;

    private PacketLimits() {}

    /**
     * Reads an element count and refuses anything outside {@code [0, max]}.
     *
     * @param what short name of the collection, used in the decode error
     * @throws DecoderException if the count is negative or exceeds {@code max}
     */
    public static int readCount(FriendlyByteBuf buf, int max, String what) {
        int count = buf.readInt();
        if (count < 0 || count > max) {
            throw new DecoderException(
                    "ata: refusing " + what + " count " + count + " (allowed 0.." + max + ")");
        }
        return count;
    }
}
