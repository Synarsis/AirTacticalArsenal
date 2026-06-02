package com.synarsis.airtacticalarsenal.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around the legacy NBT structure of "SavedCoords" used by ControlTabletItem and OrlanTabletItem.
 * Maps a slot index (int) -> a 2-element int[] containing (x, z) coordinates.
 *
 * Stored on disk as a list of entries (slot, x, z) so we can codec it cleanly.
 */
public record SavedCoords(Map<Integer, int[]> entries) {

    public static final SavedCoords EMPTY = new SavedCoords(new HashMap<>());

    public static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(Entry::slot),
            Codec.INT.fieldOf("x").forGetter(Entry::x),
            Codec.INT.fieldOf("z").forGetter(Entry::z)
    ).apply(instance, Entry::new));

    public static final Codec<SavedCoords> CODEC = ENTRY_CODEC.listOf().xmap(
            list -> {
                Map<Integer, int[]> map = new HashMap<>();
                for (Entry e : list) {
                    map.put(e.slot(), new int[]{e.x(), e.z()});
                }
                return new SavedCoords(map);
            },
            sc -> {
                List<Entry> out = new ArrayList<>();
                for (Map.Entry<Integer, int[]> me : sc.entries().entrySet()) {
                    int[] xz = me.getValue();
                    if (xz != null && xz.length >= 2) {
                        out.add(new Entry(me.getKey(), xz[0], xz[1]));
                    }
                }
                return out;
            }
    );

    public static final StreamCodec<ByteBuf, Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::slot,
            ByteBufCodecs.VAR_INT, Entry::x,
            ByteBufCodecs.VAR_INT, Entry::z,
            Entry::new
    );

    public static final StreamCodec<ByteBuf, SavedCoords> STREAM_CODEC =
            ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                    list -> {
                        Map<Integer, int[]> map = new HashMap<>();
                        for (Entry e : list) {
                            map.put(e.slot(), new int[]{e.x(), e.z()});
                        }
                        return new SavedCoords(map);
                    },
                    sc -> {
                        List<Entry> out = new ArrayList<>();
                        for (Map.Entry<Integer, int[]> me : sc.entries().entrySet()) {
                            int[] xz = me.getValue();
                            if (xz != null && xz.length >= 2) {
                                out.add(new Entry(me.getKey(), xz[0], xz[1]));
                            }
                        }
                        return out;
                    }
            );

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(int slot, int x, int z) {}
}
