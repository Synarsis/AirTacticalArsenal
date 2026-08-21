package com.synarsis.airtacticalarsenal.combat.sweep;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.compat.TacZCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Catches TacZ rounds, which never reach {@code hurt()} -- they are resolved by TacZ's own
 * raytrace and the entity turns them away, so a drone can only be shot down if something looks for
 * them.
 */
public final class BulletSweep {

    /** How far out to look for rounds, in blocks. */
    private static final double SEARCH_MARGIN = 24.0;

    private static final double LOOKAHEAD = 0.5;

    private BulletSweep() {
    }

    public interface HitSink {
        void onHit(HitPart part, Vec3 point, float damage, Entity shooter, String weaponId);
    }

    public static void run(Level level, Entity target, AABB bounds, List<Obb> boxes, HitSink sink) {
        if (boxes.isEmpty() || !TacZCompat.isTacZLoaded()) {
            return;
        }

        List<Entity> rounds = level.getEntities(target, bounds.inflate(SEARCH_MARGIN),
                TacZCompat::isTacZBullet);
        if (rounds.isEmpty()) {
            return;
        }

        for (Entity round : rounds) {
            Vec3 now = round.position();
            Vec3 step = round.getDeltaMovement();

            // The travelled segment plus a short projection ahead: a round can straddle the airframe
            // between two ticks without either endpoint being inside it.
            Vec3 from = now.subtract(step);
            Vec3 to = now.add(step.scale(LOOKAHEAD));

            HitScan.Hit hit = HitScan.nearest(boxes, from, to);
            if (hit == null) {
                continue;
            }

            float damage = TacZCompat.getBulletDamage(round, hit.point());
            String weaponId = TacZCompat.getGunIdString(round);
            if (weaponId != null) {
                damage *= TacZCompat.classifyWeapon(weaponId).getDamageMultiplier();
            }

            sink.onHit(hit.part(), hit.point(), damage, TacZCompat.getShooter(round), weaponId);
            round.discard();
        }
    }
}
