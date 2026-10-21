package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TelSolidity {

    private static final double MAX_PUSH = 1.2;
    private static final int PASSES = 3;

    public record Push(Vec3 offset, boolean standing) {
        public static final Push NONE = new Push(Vec3.ZERO, false);

        public boolean isNone() {
            return this.offset.lengthSqr() < 1.0E-12;
        }
    }

    private TelSolidity() {
    }

    public static Push resolve(List<WorldBox> boxes, AABB entity) {
        AABB at = entity;
        Vec3 total = Vec3.ZERO;
        boolean standing = false;
        for (int pass = 0; pass < PASSES; pass++) {
            boolean moved = false;
            for (WorldBox box : boxes) {
                if (!box.solid()) {
                    continue;
                }
                Vec3 mtv = ObbAabbSat.mtv(box.toObb(), at);
                if (mtv == null) {
                    continue;
                }
                double len = mtv.length();
                if (len < 1.0E-4 || len > MAX_PUSH) {
                    continue;
                }
                Vec3 push = mtv.scale(-1.0);
                at = at.move(push);
                total = total.add(push);
                if (push.y / len > 0.7) {
                    standing = true;
                }
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        return total.lengthSqr() < 1.0E-12 ? Push.NONE : new Push(total, standing);
    }
}
