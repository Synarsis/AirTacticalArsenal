package com.synarsis.airtacticalarsenal.sound;

import com.synarsis.airtacticalarsenal.ShahedMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ShahedMod.MOD_ID);

    public static final RegistryObject<SoundEvent> SHAHED_ENGINE = registerSoundEvent("shahed_engine");

    public static final RegistryObject<SoundEvent> EXPLOSION_1 = registerSoundEvent("explosion_1");
    public static final RegistryObject<SoundEvent> EXPLOSION_2 = registerSoundEvent("explosion_2");
    public static final RegistryObject<SoundEvent> EXPLOSION_3 = registerSoundEvent("explosion_3");
    public static final RegistryObject<SoundEvent> EXPLOSION_4 = registerSoundEvent("explosion_4");
    public static final RegistryObject<SoundEvent> EXPLOSION_5 = registerSoundEvent("explosion_5");

    public static final RegistryObject<SoundEvent> SIREN = registerSoundEvent("siren");

    public static final RegistryObject<SoundEvent> ORLAN_ENGINE = registerSoundEvent("orlan_engine");

    public static final RegistryObject<SoundEvent> ISKANDER_LAUNCH = registerSoundEvent("iskander_launch");
    public static final RegistryObject<SoundEvent> ISKANDER_FLIGHT = registerSoundEvent("iskander_flight");
    public static final RegistryObject<SoundEvent> ISKANDER_EXPLOSION = registerSoundEvent("iskander_explosion");
    public static final RegistryObject<SoundEvent> ISKANDER_PREPARING = registerSoundEvent("iskander_preparing");
    public static final RegistryObject<SoundEvent> ISKANDER_TERMINAL = registerSoundEvent("iskander_terminal");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = new ResourceLocation(ShahedMod.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
