package com.synarsis.airtacticalarsenal.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class ShahedConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SPAWN_X;
    public static final ForgeConfigSpec.IntValue SPAWN_Y;
    public static final ForgeConfigSpec.IntValue SPAWN_Z;
    public static final ForgeConfigSpec.IntValue TARGET_Y_LEVEL;
    public static final ForgeConfigSpec.IntValue LAUNCH_COST_COINS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FORBIDDEN_ZONES;
    public static final ForgeConfigSpec.BooleanValue BLACKLIST_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST_ZONES;
    public static final ForgeConfigSpec.BooleanValue WHITELIST_ENABLED;
    public static final ForgeConfigSpec.BooleanValue EXPLOSION_PARTICLES_ENABLED;
    public static final ForgeConfigSpec.IntValue EXPLOSION_PARTICLE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue EXPLOSION_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_EXPLOSION_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue LOGGING_ENABLED;

    public static final ForgeConfigSpec.DoubleValue SHAHED_CRUISE_SPEED;
    public static final ForgeConfigSpec.DoubleValue SHAHED_DIVE_SPEED;
    public static final ForgeConfigSpec.IntValue SHAHED_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue SHAHED_MAX_RANGE;

    public static final ForgeConfigSpec.IntValue ISKANDER_LAUNCH_COST;

    public static final ForgeConfigSpec.BooleanValue SHAHED_SHOOTDOWN_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SHAHED_MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue SHAHED_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHAHED_ENGINE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHAHED_WARHEAD_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue SHAHED_DISTANCE_FALLOFF;
    public static final ForgeConfigSpec.DoubleValue SHAHED_MIN_CALIBER_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue SHAHED_SMOKE_ON_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue SHAHED_CRITICAL_DETONATION;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_APEX_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_LAUNCH_SPEED;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_CRUISE_SPEED;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_TERMINAL_SPEED;
    public static final ForgeConfigSpec.IntValue ISKANDER_PREPARE_TICKS;
    public static final ForgeConfigSpec.IntValue ISKANDER_LAUNCH_TICKS;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_KVO_DEVIATION;
    public static final ForgeConfigSpec.IntValue ISKANDER_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue ISKANDER_MAX_RANGE;
    public static final ForgeConfigSpec.IntValue ISKANDER_MAX_FLIGHT_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_DIVE_ANGLE;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_APEX_PROGRESS;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_HORIZONTAL_TRANSITION_SPEED;

    public static final ForgeConfigSpec.DoubleValue ISKANDER_PLAYER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_ENTITY_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ISKANDER_VEHICLE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHAHED_EXPLOSION_PLAYER_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHAHED_EXPLOSION_ENTITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SHAHED_EXPLOSION_VEHICLE_MULTIPLIER;

    public static boolean isInForbiddenZone(int x, int z) {
        boolean inWhitelist = false;
        boolean inBlacklist = false;

        if (WHITELIST_ENABLED.get()) {
            for (String zoneStr : WHITELIST_ZONES.get()) {
                ForbiddenZone zone = ForbiddenZone.parse(zoneStr);
                if (zone != null && zone.contains(x, z)) {
                    inWhitelist = true;
                    break;
                }
            }
            if (!inWhitelist) {
                return true;
            }
        }

        if (BLACKLIST_ENABLED.get()) {
            for (String zoneStr : FORBIDDEN_ZONES.get()) {
                ForbiddenZone zone = ForbiddenZone.parse(zoneStr);
                if (zone != null && zone.contains(x, z)) {
                    inBlacklist = true;
                    break;
                }
            }
            if (inBlacklist) {
                return true;
            }
        }

        return false;
    }

    public static boolean areExplosionParticlesEnabled() {
        return EXPLOSION_PARTICLES_ENABLED.get();
    }

    public static void setExplosionParticlesEnabled(boolean enabled) {
        EXPLOSION_PARTICLES_ENABLED.set(enabled);
    }

    public static int getParticleMultiplier() {
        return EXPLOSION_PARTICLE_MULTIPLIER.get();
    }

    public static void setParticleMultiplier(int multiplier) {
        EXPLOSION_PARTICLE_MULTIPLIER.set(Math.max(0, Math.min(500, multiplier)));
    }

    public static boolean doExplosionsBreakBlocks() {
        return EXPLOSION_BREAKS_BLOCKS.get();
    }

    public static void setExplosionsBreakBlocks(boolean breaks) {
        EXPLOSION_BREAKS_BLOCKS.set(breaks);
    }

    public static boolean areCustomExplosionSoundsEnabled() {
        return CUSTOM_EXPLOSION_SOUNDS.get();
    }

    public static void setCustomExplosionSoundsEnabled(boolean enabled) {
        CUSTOM_EXPLOSION_SOUNDS.set(enabled);
    }

    public static boolean isLoggingEnabled() {
        return LOGGING_ENABLED.get();
    }

    public static void setLoggingEnabled(boolean enabled) {
        LOGGING_ENABLED.set(enabled);
    }

    public static double getShahedCruiseSpeed() { return SHAHED_CRUISE_SPEED.get(); }
    public static double getShahedDiveSpeed() { return SHAHED_DIVE_SPEED.get(); }
    public static int getShahedMinDistance() { return SHAHED_MIN_DISTANCE.get(); }
    public static int getShahedMaxRange() { return SHAHED_MAX_RANGE.get(); }

    public static boolean isShahedShootdownEnabled() { return SHAHED_SHOOTDOWN_ENABLED.get(); }
    public static double getShahedMaxHealth() { return SHAHED_MAX_HEALTH.get(); }
    public static double getShahedDamageMultiplier() { return SHAHED_DAMAGE_MULTIPLIER.get(); }
    public static double getShahedEngineDamageMultiplier() { return SHAHED_ENGINE_DAMAGE_MULTIPLIER.get(); }
    public static double getShahedWarheadDamageMultiplier() { return SHAHED_WARHEAD_DAMAGE_MULTIPLIER.get(); }
    public static boolean isShahedDistanceFalloffEnabled() { return SHAHED_DISTANCE_FALLOFF.get(); }
    public static double getShahedMinCaliberDamage() { return SHAHED_MIN_CALIBER_DAMAGE.get(); }
    public static boolean isShahedSmokeOnDamageEnabled() { return SHAHED_SMOKE_ON_DAMAGE.get(); }
    public static boolean isShahedCriticalDetonationEnabled() { return SHAHED_CRITICAL_DETONATION.get(); }

    public static int getIskanderLaunchCost() { return ISKANDER_LAUNCH_COST.get(); }
    public static double getIskanderApexHeight() { return ISKANDER_APEX_HEIGHT.get(); }
    public static double getIskanderLaunchSpeed() { return ISKANDER_LAUNCH_SPEED.get(); }
    public static double getIskanderCruiseSpeed() { return ISKANDER_CRUISE_SPEED.get(); }
    public static double getIskanderTerminalSpeed() { return ISKANDER_TERMINAL_SPEED.get(); }
    public static int getIskanderPrepareTicks() { return ISKANDER_PREPARE_TICKS.get(); }
    public static int getIskanderLaunchTicks() { return ISKANDER_LAUNCH_TICKS.get(); }
    public static double getIskanderKvoDeviation() { return ISKANDER_KVO_DEVIATION.get(); }
    public static int getIskanderMinDistance() { return ISKANDER_MIN_DISTANCE.get(); }
    public static int getIskanderMaxRange() { return ISKANDER_MAX_RANGE.get(); }
    public static int getIskanderMaxFlightHeight() { return ISKANDER_MAX_FLIGHT_HEIGHT.get(); }
    public static double getIskanderDiveAngle() { return ISKANDER_DIVE_ANGLE.get(); }
    public static double getIskanderApexProgress() { return ISKANDER_APEX_PROGRESS.get(); }
    public static double getIskanderHorizontalTransitionSpeed() { return ISKANDER_HORIZONTAL_TRANSITION_SPEED.get(); }

    public static double getIskanderPlayerDamageMultiplier()  { return ISKANDER_PLAYER_DAMAGE_MULTIPLIER.get(); }
    public static double getIskanderEntityDamageMultiplier()  { return ISKANDER_ENTITY_DAMAGE_MULTIPLIER.get(); }
    public static double getIskanderVehicleDamageMultiplier() { return ISKANDER_VEHICLE_DAMAGE_MULTIPLIER.get(); }
    public static double getShahedExplosionPlayerMultiplier() { return SHAHED_EXPLOSION_PLAYER_MULTIPLIER.get(); }
    public static double getShahedExplosionEntityMultiplier() { return SHAHED_EXPLOSION_ENTITY_MULTIPLIER.get(); }
    public static double getShahedExplosionVehicleMultiplier(){ return SHAHED_EXPLOSION_VEHICLE_MULTIPLIER.get(); }

    public static boolean isBlacklistEnabled() {
        return BLACKLIST_ENABLED.get();
    }

    public static void setBlacklistEnabled(boolean enabled) {
        BLACKLIST_ENABLED.set(enabled);
    }

    public static boolean isWhitelistEnabled() {
        return WHITELIST_ENABLED.get();
    }

    public static void setWhitelistEnabled(boolean enabled) {
        WHITELIST_ENABLED.set(enabled);
    }

    public static void reload() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("ata-common.toml");
        CommentedFileConfig fileConfig = CommentedFileConfig.builder(configPath).sync().build();
        fileConfig.load();
        SPEC.setConfig(fileConfig);
    }

    static {
        BUILDER.push("Spawn Settings");
        SPAWN_X = BUILDER.comment("Shahed spawn X coordinate").defineInRange("spawnX", 0, -30000000, 30000000);
        SPAWN_Y = BUILDER.comment("Shahed spawn Y coordinate (height)").defineInRange("spawnY", 200, 100, 320);
        SPAWN_Z = BUILDER.comment("Shahed spawn Z coordinate").defineInRange("spawnZ", 0, -30000000, 30000000);
        TARGET_Y_LEVEL = BUILDER.comment("Target Y coordinate (attack height)").defineInRange("targetYLevel", 64, -64, 320);
        BUILDER.pop();
        BUILDER.push("Cost Settings");
        LAUNCH_COST_COINS = BUILDER.comment("Launch cost in coins").defineInRange("launchCostCoins", 10, 0, 64);
        BUILDER.pop();
        BUILDER.push("Blacklist Zones");
        BLACKLIST_ENABLED = BUILDER.comment("Enable blacklist (forbidden zones)").define("blacklistEnabled", false);
        BUILDER.comment("Forbidden zones for attacks", "Format: \"x1,z1,x2,z2\" where x1,z1 - first corner, x2,z2 - second corner", "Example: \"0,0,100,100\" forbids area from 0,0 to 100,100");
        FORBIDDEN_ZONES = BUILDER.defineList("forbiddenZones", Arrays.asList("160,771,-622,1011", "170,-1020,-420,-785", "-750,-600,-1145,-1015"), obj -> obj instanceof String);
        BUILDER.pop();
        BUILDER.push("Whitelist Zones");
        WHITELIST_ENABLED = BUILDER.comment("Enable whitelist (allowed zones)", "If enabled, attacks are ONLY allowed inside whitelist zones").define("whitelistEnabled", false);
        BUILDER.comment("Allowed zones for attacks", "Format: \"x1,z1,x2,z2\" where x1,z1 - first corner, x2,z2 - second corner", "Attacks are ONLY allowed inside these zones (if whitelist is enabled)");
        WHITELIST_ZONES = BUILDER.defineList("whitelistZones", Arrays.asList(), obj -> obj instanceof String);
        BUILDER.pop();
        BUILDER.push("Explosion Effects");
        EXPLOSION_PARTICLES_ENABLED = BUILDER.comment("Enable explosion particles").define("explosionParticlesEnabled", true);
        EXPLOSION_PARTICLE_MULTIPLIER = BUILDER.comment("Particle amount multiplier (0-500, 100 = default amount)").defineInRange("explosionParticleMultiplier", 100, 0, 500);
        EXPLOSION_BREAKS_BLOCKS = BUILDER.comment("Explosion breaks blocks").define("explosionBreaksBlocks", true);
        CUSTOM_EXPLOSION_SOUNDS = BUILDER.comment("Use custom explosion sounds").define("customExplosionSounds", true);
        BUILDER.pop();
        BUILDER.push("Debug");
        LOGGING_ENABLED = BUILDER.comment("Enable launch logging to console").define("loggingEnabled", false);
        BUILDER.pop();

        BUILDER.push("Shahed Settings");
        SHAHED_CRUISE_SPEED = BUILDER.comment("Cruise speed in blocks/tick (30 blocks/sec = 1.5)").defineInRange("shahedCruiseSpeed", 1.5, 0.5, 10.0);
        SHAHED_DIVE_SPEED = BUILDER.comment("Dive speed in blocks/tick (72 blocks/sec = 3.6)").defineInRange("shahedDiveSpeed", 3.6, 1.0, 15.0);
        SHAHED_MIN_DISTANCE = BUILDER.comment("Minimum launch distance in blocks").defineInRange("shahedMinDistance", 600, 50, 2000);
        SHAHED_MAX_RANGE = BUILDER.comment("Maximum launch distance in blocks").defineInRange("shahedMaxRange", 12000, 1000, 30000);
        BUILDER.pop();

        BUILDER.push("Anti-Air Defense (TacZ Integration)");
        BUILDER.comment("Settings for shooting down Shahed drones with TacZ weapons");
        SHAHED_SHOOTDOWN_ENABLED = BUILDER.comment("Enable shooting down Shaheds with TacZ weapons").define("shahedShootdownEnabled", true);
        SHAHED_MAX_HEALTH = BUILDER.comment("Maximum health of Shahed drone (100 = default)").defineInRange("shahedMaxHealth", 100.0, 10.0, 1000.0);
        SHAHED_DAMAGE_MULTIPLIER = BUILDER.comment("Global damage multiplier for all weapons (1.0 = normal)").defineInRange("shahedDamageMultiplier", 1.0, 0.1, 10.0);
        SHAHED_ENGINE_DAMAGE_MULTIPLIER = BUILDER.comment("Damage multiplier for hits to engine (rear part)").defineInRange("shahedEngineDamageMultiplier", 1.5, 1.0, 5.0);
        SHAHED_WARHEAD_DAMAGE_MULTIPLIER = BUILDER.comment("Damage multiplier for hits to warhead (front part) - may cause detonation!").defineInRange("shahedWarheadDamageMultiplier", 2.0, 1.0, 10.0);
        SHAHED_DISTANCE_FALLOFF = BUILDER.comment("Enable damage falloff based on distance (realistic)").define("shahedDistanceFalloff", true);
        SHAHED_MIN_CALIBER_DAMAGE = BUILDER.comment("Minimum damage threshold - shots below this are ignored (filters weak weapons)").defineInRange("shahedMinCaliberDamage", 2.0, 0.0, 20.0);
        SHAHED_SMOKE_ON_DAMAGE = BUILDER.comment("Show smoke effects when Shahed is damaged").define("shahedSmokeOnDamage", true);
        SHAHED_CRITICAL_DETONATION = BUILDER.comment("Warhead hits at low HP can cause premature detonation").define("shahedCriticalDetonation", true);
        BUILDER.pop();

        BUILDER.push("Iskander Settings");
        ISKANDER_LAUNCH_COST = BUILDER.comment("Launch cost in coins for Iskander").defineInRange("iskanderLaunchCost", 50, 0, 640);
        ISKANDER_APEX_HEIGHT = BUILDER.comment("Maximum flight height in blocks").defineInRange("iskanderApexHeight", 800.0, 100.0, 1500.0);
        ISKANDER_LAUNCH_SPEED = BUILDER.comment("Vertical speed during launch phase (blocks/tick)").defineInRange("iskanderLaunchSpeed", 2.5, 0.3, 8.0);
        ISKANDER_CRUISE_SPEED = BUILDER.comment("Horizontal speed during cruise/ballistic phase (blocks/tick, 4.0 = 80 blocks/sec)").defineInRange("iskanderCruiseSpeed", 4.0, 0.5, 30.0);
        ISKANDER_TERMINAL_SPEED = BUILDER.comment("Speed during terminal dive phase (blocks/tick)").defineInRange("iskanderTerminalSpeed", 5.0, 1.0, 40.0);
        ISKANDER_PREPARE_TICKS = BUILDER.comment("Preparation time before launch (ticks, 20 = 1 second). Default 140 = 7 seconds").defineInRange("iskanderPrepareTicks", 140, 0, 300);
        ISKANDER_LAUNCH_TICKS = BUILDER.comment("Duration of vertical launch phase (ticks, 20 = 1 sec). Default 60 = 3 seconds").defineInRange("iskanderLaunchTicks", 60, 20, 300);
        ISKANDER_KVO_DEVIATION = BUILDER.comment("Circular Error Probable - target deviation in blocks").defineInRange("iskanderKvoDeviation", 3.0, 0.0, 50.0);
        ISKANDER_MIN_DISTANCE = BUILDER.comment("Minimum launch distance for Iskander in blocks").defineInRange("iskanderMinDistance", 1000, 100, 2000);
        ISKANDER_MAX_RANGE = BUILDER.comment("Maximum launch distance for Iskander in blocks").defineInRange("iskanderMaxRange", 5000, 500, 10000);
        ISKANDER_MAX_FLIGHT_HEIGHT = BUILDER.comment("Maximum flight height in blocks absolute Y coordinate (e.g. 600 means missile apex will not exceed Y=600)").defineInRange("iskanderMaxFlightHeight", 600, 100, 1500);
        ISKANDER_DIVE_ANGLE = BUILDER.comment("Terminal dive angle in degrees (70-85 recommended)").defineInRange("iskanderDiveAngle", 75.0, 45.0, 89.0);
        ISKANDER_APEX_PROGRESS = BUILDER.comment("Position of trajectory apex (0.3-0.6, where 0.5 = middle of flight)").defineInRange("iskanderApexProgress", 0.4, 0.2, 0.7);
        ISKANDER_HORIZONTAL_TRANSITION_SPEED = BUILDER.comment("Speed of horizontal transition smoothing (0.05-0.3)").defineInRange("iskanderHorizontalTransitionSpeed", 0.1, 0.02, 0.5);
        BUILDER.pop();

        BUILDER.push("Explosion Damage Multipliers");
        BUILDER.comment(
            "Multipliers applied to explosion damage per target category.",
            "1.0 = default damage. 2.0 = double damage. 0.5 = half damage.",
            "Vehicle category applies to SuperbWarfare/VVP vehicle entities (tanks, helicopters, planes).",
            "Entity category applies to all other non-player entities (mobs, animals, etc)."
        );
        ISKANDER_PLAYER_DAMAGE_MULTIPLIER  = BUILDER.comment("Iskander: damage multiplier vs players").defineInRange("iskanderPlayerDamageMultiplier",  1.0, 0.0, 20.0);
        ISKANDER_ENTITY_DAMAGE_MULTIPLIER  = BUILDER.comment("Iskander: damage multiplier vs mobs/entities (non-vehicle)").defineInRange("iskanderEntityDamageMultiplier",  1.0, 0.0, 20.0);
        ISKANDER_VEHICLE_DAMAGE_MULTIPLIER = BUILDER.comment("Iskander: damage multiplier vs SuperbWarfare/VVP vehicles").defineInRange("iskanderVehicleDamageMultiplier", 3.5, 0.0, 20.0);
        SHAHED_EXPLOSION_PLAYER_MULTIPLIER  = BUILDER.comment("Shahed: explosion damage multiplier vs players").defineInRange("shahedExplosionPlayerMultiplier",  1.0, 0.0, 20.0);
        SHAHED_EXPLOSION_ENTITY_MULTIPLIER  = BUILDER.comment("Shahed: explosion damage multiplier vs mobs/entities (non-vehicle)").defineInRange("shahedExplosionEntityMultiplier",  1.0, 0.0, 20.0);
        SHAHED_EXPLOSION_VEHICLE_MULTIPLIER = BUILDER.comment("Shahed: explosion damage multiplier vs SuperbWarfare/VVP vehicles").defineInRange("shahedExplosionVehicleMultiplier", 3.5, 0.0, 20.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static class ForbiddenZone {
        public final int x1;
        public final int z1;
        public final int x2;
        public final int z2;

        public ForbiddenZone(int x1, int z1, int x2, int z2) {
            this.x1 = Math.min(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.z2 = Math.max(z1, z2);
        }

        public boolean contains(int x, int z) {
            return x >= this.x1 && x <= this.x2 && z >= this.z1 && z <= this.z2;
        }

        public static ForbiddenZone parse(String str) {
            try {
                String[] parts = str.split(",");
                if (parts.length == 4) {
                    return new ForbiddenZone(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()),
                            Integer.parseInt(parts[3].trim())
                    );
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
    }
}
