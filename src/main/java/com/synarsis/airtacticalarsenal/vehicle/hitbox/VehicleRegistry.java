package com.synarsis.airtacticalarsenal.vehicle.hitbox;

import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class VehicleRegistry {

    private static final Map<Level, List<HitboxVehicle>> BY_LEVEL = new ConcurrentHashMap<>();

    private VehicleRegistry() {
    }

    public static void add(Level level, HitboxVehicle vehicle) {
        List<HitboxVehicle> list = BY_LEVEL.computeIfAbsent(level, l -> new CopyOnWriteArrayList<>());
        if (!list.contains(vehicle)) {
            list.add(vehicle);
        }
    }

    public static void remove(Level level, HitboxVehicle vehicle) {
        List<HitboxVehicle> list = BY_LEVEL.get(level);
        if (list != null) {
            list.remove(vehicle);
            if (list.isEmpty()) {
                BY_LEVEL.remove(level, list);
            }
        }
    }

    public static boolean any(Level level) {
        List<HitboxVehicle> list = BY_LEVEL.get(level);
        return list != null && !list.isEmpty();
    }

    public static List<HitboxVehicle> in(Level level) {
        List<HitboxVehicle> list = BY_LEVEL.get(level);
        return list == null ? List.of() : list;
    }
}
