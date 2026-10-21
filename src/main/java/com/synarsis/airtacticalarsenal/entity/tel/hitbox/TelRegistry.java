package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TelRegistry {

    private static final Map<Level, List<IskanderTelEntity>> BY_LEVEL = new ConcurrentHashMap<>();

    private TelRegistry() {
    }

    public static void add(Level level, IskanderTelEntity tel) {
        List<IskanderTelEntity> list = BY_LEVEL.computeIfAbsent(level, l -> new CopyOnWriteArrayList<>());
        if (!list.contains(tel)) {
            list.add(tel);
        }
    }

    public static void remove(Level level, IskanderTelEntity tel) {
        List<IskanderTelEntity> list = BY_LEVEL.get(level);
        if (list != null) {
            list.remove(tel);
            if (list.isEmpty()) {
                BY_LEVEL.remove(level, list);
            }
        }
    }

    public static boolean any(Level level) {
        List<IskanderTelEntity> list = BY_LEVEL.get(level);
        return list != null && !list.isEmpty();
    }

    public static List<IskanderTelEntity> in(Level level) {
        List<IskanderTelEntity> list = BY_LEVEL.get(level);
        return list == null ? List.of() : list;
    }
}
