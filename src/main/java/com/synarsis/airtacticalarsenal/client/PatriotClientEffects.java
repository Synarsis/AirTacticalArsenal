package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.sound.PatriotEngineSoundInstance;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class PatriotClientEffects {

    private static final Map<Integer, PatriotEngineSoundInstance> ENGINES = new HashMap<>();

    private PatriotClientEffects() {
    }

    public static void tick(PatriotVehicleEntity vehicle) {
        if (!vehicle.isEngineRunning()) {
            return;
        }
        PatriotEngineSoundInstance sound = ENGINES.get(vehicle.getId());
        if (sound == null || !Minecraft.getInstance().getSoundManager().isActive(sound)) {
            if (ENGINES.size() > 16) {
                ENGINES.values().removeIf(s -> s.isStopped());
            }
            sound = new PatriotEngineSoundInstance(vehicle);
            ENGINES.put(vehicle.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    public static void clear() {
        ENGINES.clear();
    }
}
