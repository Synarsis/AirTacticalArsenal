package com.synarsis.airtacticalarsenal.compat;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class ExplosionOverhaulCompat {

    private static final String MOD_ID = "explosionoverhaul";

    private static final ResourceLocation LINE_SPARK =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "line_spark");

    private static Boolean loaded;
    private static SimpleParticleType sparkType;
    private static boolean sparkLookedUp;

    private ExplosionOverhaulCompat() {
    }

    public static boolean isPresent() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    public static boolean leadsVisuals() {
        return isPresent();
    }

    public static void blast(Level level, Entity source, DamageSource damage, Vec3 at, float power,
                             boolean breakBlocks) {
        level.explode(source, damage, null, at.x, at.y, at.z, power, false,
                breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    public static void sparks(ServerLevel level, Vec3 at, int count, double spread) {
        SimpleParticleType type = spark();
        if (type == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) <= 192.0 * 192.0) {
                level.sendParticles(player, type, true, at.x, at.y, at.z, count,
                        spread, spread, spread, 0.05);
            }
        }
    }

    private static SimpleParticleType spark() {
        if (!sparkLookedUp) {
            sparkLookedUp = true;
            if (isPresent()) {
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(LINE_SPARK);
                if (type instanceof SimpleParticleType simple) {
                    sparkType = simple;
                }
            }
        }
        return sparkType;
    }
}
