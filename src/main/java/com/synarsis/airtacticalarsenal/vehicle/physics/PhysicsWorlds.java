package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PhysicsWorlds {

    public interface Participant {
        void beforeStep();

        void afterStep();

        void shutdown();

        default void blockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        }
    }

    private static final class Entry {
        final PhysicsWorld world;
        final List<Participant> participants = new ArrayList<>();
        double gravityScale;

        Entry(PhysicsWorld world, double gravityScale) {
            this.world = world;
            this.gravityScale = gravityScale;
        }
    }

    private static final class Side {
        final Object lock = new Object();
        final Map<Level, Entry> entries = new IdentityHashMap<>();
        volatile int watchers;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DEFAULT_GRAVITY_SCALE = 1.6;
    private static final Side CLIENT = new Side();
    private static final Side SERVER = new Side();

    private PhysicsWorlds() {
    }

    private static Side sideOf(Level level) {
        return level.isClientSide ? CLIENT : SERVER;
    }

    public static boolean joltWanted() {
        try {
            return ShahedConfig.getVehiclePhysics() == VehiclePhysicsMode.JOLT && JoltRuntime.available();
        } catch (IllegalStateException notLoaded) {
            return JoltRuntime.available();
        }
    }

    public static double gravityScale() {
        try {
            return ShahedConfig.getVehicleGravityScale();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_GRAVITY_SCALE;
        }
    }

    private static Entry entryFor(Side side, Level level) {
        Entry entry = side.entries.get(level);
        if (entry == null) {
            double scale = gravityScale();
            entry = new Entry(new PhysicsWorld(level.isClientSide, scale), scale);
            side.entries.put(level, entry);
        }
        return entry;
    }

    public static PhysicsWorld obtain(Level level) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            return entryFor(side, level).world;
        }
    }

    public static PhysicsWorld existing(Level level) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            Entry entry = side.entries.get(level);
            return entry == null ? null : entry.world;
        }
    }

    public static void join(Level level, Participant participant) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            entryFor(side, level).participants.add(participant);
            side.watchers++;
        }
    }

    public static void leave(Level level, Participant participant) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            Entry entry = side.entries.get(level);
            if (entry != null && entry.participants.remove(participant)) {
                side.watchers--;
            }
        }
    }

    public static void tick(Level level) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            Entry entry = side.entries.get(level);
            if (entry == null) {
                return;
            }
            if (entry.world.idleExpired(!entry.participants.isEmpty())) {
                close(side, entry);
                side.entries.remove(level);
                return;
            }
            if (entry.participants.isEmpty()) {
                return;
            }
            double scale = gravityScale();
            if (scale != entry.gravityScale) {
                entry.gravityScale = scale;
                entry.world.setGravityScale(scale);
            }
            List<Participant> crowd = new ArrayList<>(entry.participants);
            for (Participant participant : crowd) {
                participant.beforeStep();
            }
            entry.world.step();
            for (Participant participant : crowd) {
                participant.afterStep();
            }
        }
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        Side side = sideOf(level);
        if (side.watchers == 0) {
            return;
        }
        synchronized (side.lock) {
            Entry entry = side.entries.get(level);
            if (entry == null) {
                return;
            }
            for (Participant participant : entry.participants) {
                participant.blockChanged(pos, oldState, newState);
            }
        }
    }

    public static void unload(Level level) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            Entry entry = side.entries.remove(level);
            if (entry != null) {
                close(side, entry);
            }
        }
    }

    public static void unloadAll(boolean clientSide) {
        Side side = clientSide ? CLIENT : SERVER;
        synchronized (side.lock) {
            for (Entry entry : new ArrayList<>(side.entries.values())) {
                close(side, entry);
            }
            side.entries.clear();
        }
    }

    public static int worldCount() {
        int count = 0;
        for (Side side : new Side[] {CLIENT, SERVER}) {
            synchronized (side.lock) {
                count += side.entries.size();
            }
        }
        return count;
    }

    public static int bodyCount(Level level) {
        Side side = sideOf(level);
        synchronized (side.lock) {
            Entry entry = side.entries.get(level);
            return entry == null ? 0 : entry.world.bodyCount();
        }
    }

    public static boolean watched(boolean clientSide) {
        return (clientSide ? CLIENT : SERVER).watchers > 0;
    }

    private static void close(Side side, Entry entry) {
        for (Participant participant : new ArrayList<>(entry.participants)) {
            try {
                participant.shutdown();
            } catch (RuntimeException error) {
                LOGGER.warn("A vehicle did not shut down cleanly: {}", error.toString());
            }
        }
        side.watchers -= entry.participants.size();
        entry.participants.clear();
        entry.world.close();
    }
}
