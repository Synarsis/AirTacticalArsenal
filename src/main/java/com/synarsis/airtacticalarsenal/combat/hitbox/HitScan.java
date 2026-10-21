package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HitScan {

    private HitScan() {
    }

    public record Hit(HitPart part, Vec3 point, double t) {
    }

    public record BoxHit(Obb box, Vec3 point, double t) {
    }

    public static BoxHit nearestBox(List<Obb> boxes, Vec3 from, Vec3 to) {
        Obb best = null;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            Obb box = boxes.get(i);
            double t = box.clipSegment(from, to);
            if (t != Obb.NO_HIT && t < bestT) {
                bestT = t;
                best = box;
            }
        }
        return best == null ? null : new BoxHit(best, from.add(to.subtract(from).scale(bestT)), bestT);
    }

    public static Hit nearest(List<Obb> boxes, Vec3 from, Vec3 to) {
        Obb best = null;
        double bestT = Double.MAX_VALUE;

        for (int i = 0; i < boxes.size(); i++) {
            Obb box = boxes.get(i);
            double t = box.clipSegment(from, to);
            if (t != Obb.NO_HIT && t < bestT) {
                bestT = t;
                best = box;
            }
        }

        if (best == null) {
            return null;
        }
        return new Hit(best.part(), from.add(to.subtract(from).scale(bestT)), bestT);
    }

    public static HitPart at(List<Obb> boxes, Vec3 point) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(point)) {
                return boxes.get(i).part();
            }
        }
        return null;
    }
}
