package com.synarsis.airtacticalarsenal.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

public final class PacketLimits {

    public static final int MAX_WAYPOINTS = 64;

    public static final int MAX_LAUNCHERS = 64;

    public static final int MAX_SAVED_COORD_SLOTS = 64;

    public static final int MAX_SAVED_COORD_VALUES = 2;

    private PacketLimits() {}

    public static int readCount(FriendlyByteBuf buf, int max, String what) {
        int count = buf.readInt();
        if (count < 0 || count > max) {
            throw new DecoderException(
                    "ata: refusing " + what + " count " + count + " (allowed 0.." + max + ")");
        }
        return count;
    }
}
