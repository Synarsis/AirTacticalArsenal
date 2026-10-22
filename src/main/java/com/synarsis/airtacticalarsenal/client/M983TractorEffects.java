package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.fx.light.HeadlightFx;
import com.synarsis.airtacticalarsenal.client.sound.M983EngineSoundInstance;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class M983TractorEffects {

    private static final Map<Integer, M983EngineSoundInstance> ENGINES = new HashMap<>();

    private M983TractorEffects() {
    }

    public static void tick(M983TractorEntity tractor) {
        HeadlightFx.tick(tractor);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        M983EngineSoundInstance existing = ENGINES.get(tractor.getId());
        if (tractor.isRemoved() || tractor.getHealth() <= 0.0f) {
            if (existing != null) {
                minecraft.getSoundManager().stop(existing);
                ENGINES.remove(tractor.getId());
            }
            return;
        }
        if (existing == null) {
            M983EngineSoundInstance sound = new M983EngineSoundInstance(tractor);
            ENGINES.put(tractor.getId(), sound);
            minecraft.getSoundManager().play(sound);
        } else if (existing.isStopped()) {
            ENGINES.remove(tractor.getId());
        }
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Iterator<M983EngineSoundInstance> it = ENGINES.values().iterator(); it.hasNext();) {
            minecraft.getSoundManager().stop(it.next());
            it.remove();
        }
    }
}