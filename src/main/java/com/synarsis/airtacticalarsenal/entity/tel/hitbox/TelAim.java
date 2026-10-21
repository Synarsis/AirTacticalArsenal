package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class TelAim {

    private TelAim() {
    }

    @Nullable
    private static IskanderTelEntity launcherOf(Entity e) {
        if (e instanceof TelPart part) {
            return part.getParent();
        }
        return e instanceof IskanderTelEntity tel ? tel : null;
    }

    @Nullable
    public static EntityHitResult pick(Entity shooter, Vec3 start, Vec3 end, AABB area, Predicate<Entity> filter,
            double distance) {
        Level level = shooter.level();
        double best = distance;
        Entity hit = null;
        Vec3 hitAt = null;

        for (Entity candidate : level.getEntities(shooter, area, filter)) {
            Optional<Vec3> clip;
            boolean inside;
            IskanderTelEntity tel = launcherOf(candidate);
            if (tel != null && tel.hasPassenger(shooter)) {
                continue;
            }
            if (tel != null) {
                List<Obb> boxes = tel.getPickObbs();
                clip = TelHitboxQuery.clip(boxes, start, end);
                inside = TelHitboxQuery.contains(boxes, start);
            } else {
                AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
                clip = box.clip(start, end);
                inside = box.contains(start);
            }

            if (inside) {
                if (best >= 0.0) {
                    hit = candidate;
                    hitAt = clip.orElse(start);
                    best = 0.0;
                }
            } else if (clip.isPresent()) {
                Vec3 point = clip.get();
                double d = start.distanceToSqr(point);
                if (d < best || best == 0.0) {
                    if (candidate.getRootVehicle() == shooter.getRootVehicle() && !candidate.canRiderInteract()) {
                        if (best == 0.0) {
                            hit = candidate;
                            hitAt = point;
                        }
                    } else {
                        hit = candidate;
                        hitAt = point;
                        best = d;
                    }
                }
            }
        }
        return hit == null ? null : new EntityHitResult(hit, hitAt);
    }
}
