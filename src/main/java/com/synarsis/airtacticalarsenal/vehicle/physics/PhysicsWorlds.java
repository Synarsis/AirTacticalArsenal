package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.config.VehiclePhysicsMode;
import net.minecraft.world.level.Level;
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

        default void blockChanged(int x, int y, int z) {
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

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DEFAULT_GRAVITY_SCALE = 1.6;
    private static final Map<Level, Entry> ENTRIES = new IdentityHashMap<>();

    private PhysicsWorlds() {
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

    public static synchronized PhysicsWorld obtain(Level level) {
        Entry entry = ENTRIES.get(level);
        if (entry == null) {
            double scale = gravityScale();
            entry = new Entry(new PhysicsWorld(level.isClientSide, scale), scale);
            ENTRIES.put(level, entry);
        }
        return entry.world;
    }

    public static synchronized PhysicsWorld existing(Level level) {
        Entry entry = ENTRIES.get(level);
        return entry == null ? null : entry.world;
    }

    public static synchronized void join(Level level, Participant participant) {
        obtain(level);
        ENTRIES.get(level).participants.add(participant);
    }

    public static synchronized void leave(Level level, Participant participant) {
        Entry entry = ENTRIES.get(level);
        if (entry != null) {
            entry.participants.remove(participant);
        }
    }

    public static synchronized void tick(Level level) {
        Entry entry = ENTRIES.get(level);
        if (entry == null) {
            return;
        }
        if (entry.world.idleExpired(!entry.participants.isEmpty())) {
            close(entry);
            ENTRIES.remove(level);
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

    public static synchronized void blockChanged(Level level, int x, int y, int z) {
        Entry entry = ENTRIES.get(level);
        if (entry == null || entry.participants.isEmpty()) {
            return;
        }
        for (Participant participant : entry.participants) {
            participant.blockChanged(x, y, z);
        }
    }

    public static synchronized void unload(Level level) {
        Entry entry = ENTRIES.remove(level);
        if (entry != null) {
            close(entry);
        }
    }

    public static synchronized void unloadAll(boolean clientSide) {
        List<Level> levels = new ArrayList<>();
        for (Level level : ENTRIES.keySet()) {
            if (level.isClientSide == clientSide) {
                levels.add(level);
            }
        }
        for (Level level : levels) {
            unload(level);
        }
    }

    public static synchronized int worldCount() {
        return ENTRIES.size();
    }

    public static synchronized int bodyCount(Level level) {
        Entry entry = ENTRIES.get(level);
        return entry == null ? 0 : entry.world.bodyCount();
    }

    private static void close(Entry entry) {
        for (Participant participant : new ArrayList<>(entry.participants)) {
            try {
                participant.shutdown();
            } catch (RuntimeException error) {
                LOGGER.warn("A vehicle did not shut down cleanly: {}", error.toString());
            }
        }
        entry.participants.clear();
        entry.world.close();
    }
}
