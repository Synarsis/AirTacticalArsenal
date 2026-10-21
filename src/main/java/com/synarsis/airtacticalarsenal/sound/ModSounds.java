package com.synarsis.airtacticalarsenal.sound;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AirTacticalArsenal.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED_ENGINE = registerSoundEvent("shahed_engine");

    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_1 = registerSoundEvent("explosion_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_2 = registerSoundEvent("explosion_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_3 = registerSoundEvent("explosion_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_4 = registerSoundEvent("explosion_4");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_5 = registerSoundEvent("explosion_5");

    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = registerSoundEvent("siren");

    public static final DeferredHolder<SoundEvent, SoundEvent> ORLAN_ENGINE = registerSoundEvent("orlan_engine");

    public static final DeferredHolder<SoundEvent, SoundEvent> ISKANDER_LAUNCH = registerSoundEvent("iskander_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> ISKANDER_FLIGHT = registerSoundEvent("iskander_flight");
    public static final DeferredHolder<SoundEvent, SoundEvent> ISKANDER_EXPLOSION = registerSoundEvent("iskander_explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> ISKANDER_PREPARING = registerSoundEvent("iskander_preparing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ISKANDER_TERMINAL = registerSoundEvent("iskander_terminal");

    public static final DeferredHolder<SoundEvent, SoundEvent> TEL_ENGINE = registerSoundEvent("tel_engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEL_STRIKE = registerSoundEvent("tel_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEL_HYDRAULICS = registerSoundEvent("tel_hydraulics");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEL_COVERS = registerSoundEvent("tel_covers");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEL_JACKS = registerSoundEvent("tel_jacks");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
