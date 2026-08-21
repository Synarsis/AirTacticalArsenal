package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Resolves a shot against a set of oriented boxes. */
public final class HitScan {

    private HitScan() {
    }

    /**
     * @param part the section that was struck
     * @param point where on the airframe the segment entered it
     * @param t entry parameter along the segment, in {@code [0, 1]}
     */
    public record Hit(HitPart part, Vec3 point, double t) {
    }

    /** The first box the segment enters, or {@code null} if it misses every one. */
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

    /** The section containing a bare point, or {@code null}. */
    public static HitPart at(List<Obb> boxes, Vec3 point) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(point)) {
                return boxes.get(i).part();
            }
        }
        return null;
    }
}
