package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitScan;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TelHitboxQuery {

    private TelHitboxQuery() {
    }

    public static List<Obb> toObbs(List<WorldBox> boxes) {
        List<Obb> out = new ArrayList<>(boxes.size());
        for (WorldBox b : boxes) {
            Matrix3f m = new Matrix3f(
                    (float) b.ax().x, (float) b.ax().y, (float) b.ax().z,
                    (float) b.ay().x, (float) b.ay().y, (float) b.ay().z,
                    (float) b.az().x, (float) b.az().y, (float) b.az().z);
            Quaternionf q = new Quaternionf().setFromNormalized(m).normalize();
            Obb obb = new Obb(b.zone());
            obb.set(b.centre().x, b.centre().y, b.centre().z, (float) b.hx(), (float) b.hy(), (float) b.hz(), q);
            out.add(obb);
        }
        return out;
    }

    public static Optional<Vec3> clip(List<Obb> boxes, Vec3 from, Vec3 to) {
        HitScan.BoxHit hit = HitScan.nearestBox(boxes, from, to);
        return hit == null ? Optional.empty() : Optional.of(hit.point());
    }

    public static boolean contains(List<Obb> boxes, Vec3 point) {
        for (Obb box : boxes) {
            if (box.contains(point)) {
                return true;
            }
        }
        return false;
    }
}
