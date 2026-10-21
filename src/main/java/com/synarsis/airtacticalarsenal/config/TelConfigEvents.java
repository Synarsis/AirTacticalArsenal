package com.synarsis.airtacticalarsenal.config;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TelConfigEvents {

    private TelConfigEvents() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        migrate(event.getConfig());
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        migrate(event.getConfig());
    }

    private static void migrate(ModConfig config) {
        if (config.getSpec() != ShahedConfig.SPEC) {
            return;
        }
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        try {
            int stored = ShahedConfig.TEL_CONFIG_VERSION.get();
            List<String> keys = TelConfigMigration.keysToReset(stored);
            if (keys.isEmpty()) {
                return;
            }
            for (String key : keys) {
                ModConfigSpec.DoubleValue value = ShahedConfig.TEL_PHYSICS_KEYS.get(key);
                if (value != null) {
                    value.set(value.getDefault());
                }
            }
            ShahedConfig.TEL_CONFIG_VERSION.set(TelConfigMigration.CURRENT_VERSION);
            ShahedConfig.SPEC.save();
            AirTacticalArsenal.LOGGER.info("TEL config migrated from v{} to v{}: reset {}",
                    stored, TelConfigMigration.CURRENT_VERSION, keys);
        } catch (RuntimeException e) {
            AirTacticalArsenal.LOGGER.warn("Could not migrate the launcher config values", e);
        }
    }
}
