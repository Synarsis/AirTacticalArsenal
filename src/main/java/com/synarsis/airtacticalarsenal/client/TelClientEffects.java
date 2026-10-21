package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.sound.IskanderPreparingSoundClient;
import com.synarsis.airtacticalarsenal.client.sound.TelEngineSoundInstance;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class TelClientEffects {

    private static final Map<Integer, TelEngineSoundInstance> ENGINES = new HashMap<>();

    private static final class Cycle {
        TelState state;
        boolean hydraulics;
        final IskanderPreparingSoundClient[] preparing = new IskanderPreparingSoundClient[2];
    }

    private static final Map<Integer, Cycle> CYCLES = new HashMap<>();

    private static final int HYDRAULICS_TICKS = 60;

    private TelClientEffects() {
    }

    public static void tick(IskanderTelEntity tel) {
        cycleSounds(tel);
        com.synarsis.airtacticalarsenal.client.fx.LaunchFx.tick(tel);
        com.synarsis.airtacticalarsenal.client.fx.light.HeadlightFx.tick(tel);
        if (!tel.isEngineRunning()) {
            return;
        }
        TelEngineSoundInstance sound = ENGINES.get(tel.getId());
        if (sound == null || !Minecraft.getInstance().getSoundManager().isActive(sound)) {
            if (ENGINES.size() > 16) {
                ENGINES.values().removeIf(s -> s.isStopped());
            }
            sound = new TelEngineSoundInstance(tel);
            ENGINES.put(tel.getId(), sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    private static boolean longMovement(TelState state) {
        return state == TelState.DEPLOYING || state == TelState.RAISING || state == TelState.LOWERING
                || state == TelState.STOWING;
    }

    private static void cycleSounds(IskanderTelEntity tel) {
        TelState state = tel.getTelState();
        Cycle cycle = CYCLES.get(tel.getId());
        if (cycle == null) {
            if (CYCLES.size() > 32) {
                CYCLES.values().removeIf(c -> c.preparing[0] == null && c.preparing[1] == null);
            }
            cycle = new Cycle();
            cycle.state = state;
            cycle.hydraulics = true;
            CYCLES.put(tel.getId(), cycle);
        }

        if (state != cycle.state) {
            cycle.state = state;
            cycle.hydraulics = false;
            switch (state) {
                case JACKS_DOWN, JACKS_UP -> play(tel, ModSounds.TEL_JACKS.get());
                case COVERS_OPENING, COVERS_CLOSING, DEPLOYING, RAISING, LOWERING,
                        STOWING -> play(tel, ModSounds.TEL_COVERS.get());
                default -> {
                }
            }
        }
        if (!cycle.hydraulics && longMovement(state) && tel.getVisualTicks() >= HYDRAULICS_TICKS) {
            cycle.hydraulics = true;
            play(tel, ModSounds.TEL_HYDRAULICS.get());
        }

        for (int slot = 0; slot < 2; slot++) {
            final int s = slot;
            int left = tel.getCountdownLeft(slot);
            boolean humming = state == TelState.COUNTDOWN && left >= 0 && left <= IskanderTelEntity.PREPARING_TICKS;
            IskanderPreparingSoundClient sound = cycle.preparing[slot];
            if (humming) {
                if (sound == null || sound.isStopped()) {
                    sound = new IskanderPreparingSoundClient(tel.footOf(slot),
                            () -> !tel.isRemoved() && tel.getTelState() == TelState.COUNTDOWN
                                    && tel.getCountdownLeft(s) >= 0);
                    cycle.preparing[slot] = sound;
                    sound.start();
                } else {
                    sound.moveTo(tel.footOf(slot));
                }
            } else if (sound != null) {
                sound.stopSound();
                cycle.preparing[slot] = null;
            }
        }
    }

    private static void play(IskanderTelEntity tel, SoundEvent sound) {
        Minecraft.getInstance().level.playLocalSound(tel.getX(), tel.getY(), tel.getZ(), sound,
                SoundSource.NEUTRAL, 1.0f, 1.0f, false);
    }

    public static void trace(String line) {
        Minecraft mc = Minecraft.getInstance();
        com.synarsis.airtacticalarsenal.AirTacticalArsenal.LOGGER.info("[tel trace] {}", line);
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7[tel] " + line), false);
        }
    }

    public static void clear() {
        ENGINES.clear();
        CYCLES.values().forEach(c -> {
            for (IskanderPreparingSoundClient hum : c.preparing) {
                if (hum != null) {
                    hum.stopSound();
                }
            }
        });
        CYCLES.clear();
    }
}
