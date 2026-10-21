package com.synarsis.airtacticalarsenal.combat.sweep;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.compat.TacZCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BulletSweep {

    private static final double SEARCH_MARGIN = 24.0;

    private static final double LOOKAHEAD = 0.5;

    private BulletSweep() {
    }

    public interface HitSink {
        void onHit(HitPart part, Vec3 point, float damage, Entity shooter, String weaponId);
    }

    public interface TagSink {
        void onHit(HitTag tag, Vec3 point, float damage, Entity shooter, String weaponId);
    }

    public static void run(Level level, Entity target, AABB bounds, List<Obb> boxes, HitSink sink) {
        runTagged(level, target, bounds, boxes,
                (tag, point, damage, shooter, weaponId) -> sink.onHit((HitPart) tag, point, damage, shooter, weaponId));
    }

    public static void runTagged(Level level, Entity target, AABB bounds, List<Obb> boxes, TagSink sink) {
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

            Vec3 from = now.subtract(step);
            Vec3 to = now.add(step.scale(LOOKAHEAD));

            HitScan.BoxHit hit = HitScan.nearestBox(boxes, from, to);
            if (hit == null) {
                continue;
            }

            float damage = TacZCompat.getBulletDamage(round, hit.point());
            String weaponId = TacZCompat.getGunIdString(round);
            if (weaponId != null) {
                damage *= TacZCompat.classifyWeapon(weaponId).getDamageMultiplier();
            }

            sink.onHit(hit.box().tag(), hit.point(), damage, TacZCompat.getShooter(round), weaponId);
            round.discard();
        }
    }
}
