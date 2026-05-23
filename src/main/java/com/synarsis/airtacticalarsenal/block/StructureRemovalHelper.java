package com.synarsis.airtacticalarsenal.block;

import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Set;

public class StructureRemovalHelper {

    private static final ThreadLocal<Set<BlockPos>> REMOVING_POSITIONS = 
        ThreadLocal.withInitial(HashSet::new);

    public static boolean isRemoving(BlockPos pos) {
        return REMOVING_POSITIONS.get().contains(pos);
    }

    public static void startRemoving(BlockPos pos) {
        REMOVING_POSITIONS.get().add(pos);
    }

    public static void finishRemoving(BlockPos pos) {
        REMOVING_POSITIONS.get().remove(pos);
    }

    public static void clearAll() {
        REMOVING_POSITIONS.get().clear();
    }

    public static boolean isRemovingAny() {
        return !REMOVING_POSITIONS.get().isEmpty();
    }
}
