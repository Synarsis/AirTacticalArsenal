package com.synarsis.airtacticalarsenal.combat.effect;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.combat.outcome.FallMode;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;

/** What a drone coming apart looks like. */
public final class FallEffects {

    private static final double VIEW_RANGE = 192.0;
    private static final double VIEW_RANGE_SQR = VIEW_RANGE * VIEW_RANGE;

    private static final int PIECES_PER_LOSS = 14;

    private FallEffects() {
    }

    private static void puff(ServerLevel level, ParticleOptions type, Vec3 at,
                             int count, double spread, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) <= VIEW_RANGE_SQR) {
                level.sendParticles(player, type, true, at.x, at.y, at.z, count,
                        spread, spread, spread, speed);
            }
        }
    }

    private static void piece(ServerLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.x, at.y, at.z) <= VIEW_RANGE_SQR) {
                level.sendParticles(player, type, true, at.x, at.y, at.z, 0,
                        velocity.x, velocity.y, velocity.z, 1.0);
            }
        }
    }

    /**
     * A section has come off: wreckage away from the airframe, and smoke where it tore loose.
     *
     * @param at centre of the box that was knocked out
     * @param motion the airframe's own velocity, so debris carries on with it rather than dropping
     * straight down out of a drone doing three blocks a tick
     */
    public static void sectionLost(ServerLevel level, HitPart part, Vec3 at, Vec3 motion,
                                   RandomSource random) {
        for (int i = 0; i < PIECES_PER_LOSS; i++) {
            Vec3 throwOff = new Vec3(
                    (random.nextDouble() - 0.5) * 0.55,
                    random.nextDouble() * 0.35,
                    (random.nextDouble() - 0.5) * 0.55);
            piece(level, ModParticles.DEBRIS.get(), at, motion.scale(0.7).add(throwOff));
        }

        puff(level, ParticleTypes.LARGE_SMOKE, at, 12, 0.25, 0.03);
        puff(level, ParticleTypes.SMOKE, at, 10, 0.2, 0.02);

        if (part == HitPart.FUEL || part == HitPart.ENGINE) {
            puff(level, ParticleTypes.FLAME, at, 8, 0.15, 0.04);
        }
    }

    /** The trail a crippled drone leaves on the way down. */
    public static void fallTrail(ServerLevel level, List<Obb> boxes, FallMode mode, Vec3 motion,
                                 int fallTicks, RandomSource random) {
        Vec3 tail = centreOf(boxes, HitPart.ENGINE);
        if (tail == null) {
            return;
        }

        switch (mode) {
            case BURNING -> {
                puff(level, ParticleTypes.FLAME, tail, 6, 0.12, 0.02);
                puff(level, ParticleTypes.LARGE_SMOKE, tail, 5, 0.15, 0.01);
            }
            case SPIRAL, TUMBLE -> {
                puff(level, ParticleTypes.LARGE_SMOKE, tail, 4, 0.2, 0.01);
                // The odd piece shaking loose as it comes apart.
                if (fallTicks % 6 == 0) {
                    piece(level, ModParticles.DEBRIS.get(), tail,
                            motion.scale(0.5).add((random.nextDouble() - 0.5) * 0.2,
                                    random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.2));
                }
            }
            case GLIDE, DEAD_STICK -> puff(level, ParticleTypes.SMOKE, tail, 2, 0.1, 0.01);
            default -> {
            }
        }
    }

    /** A drone that arrived without going off: dirt thrown up, pieces shed, and nothing else. */
    public static void groundScatter(ServerLevel level, Vec3 at, Vec3 motion, RandomSource random) {
        puff(level, ParticleTypes.LARGE_SMOKE, at, 18, 0.6, 0.02);
        puff(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.add(0, 0.4, 0), 10, 0.5, 0.01);

        Vec3 slide = new Vec3(motion.x, 0, motion.z).scale(0.4);
        for (int i = 0; i < PIECES_PER_LOSS; i++) {
            piece(level, ModParticles.DEBRIS.get(), at, slide.add(
                    (random.nextDouble() - 0.5) * 0.4,
                    random.nextDouble() * 0.28,
                    (random.nextDouble() - 0.5) * 0.4));
        }
    }

    private static Vec3 centreOf(List<Obb> boxes, HitPart part) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).part() == part) {
                Vector3d c = boxes.get(i).center();
                return new Vec3(c.x, c.y, c.z);
            }
        }
        return null;
    }
}
