package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import net.minecraft.world.phys.Vec3;

public record WorldBox(Zone zone, Vec3 centre, Vec3 ax, Vec3 ay, Vec3 az, double hx, double hy, double hz,
        boolean solid) {

    public Obb toObb() {
        return Obb.of(this.centre, this.ax, this.ay, this.az, this.hx, this.hy, this.hz);
    }

    public double minY() {
        return this.centre.y - Math.abs(this.ax.y) * this.hx - Math.abs(this.ay.y) * this.hy
                - Math.abs(this.az.y) * this.hz;
    }

    public double maxY() {
        return this.centre.y + Math.abs(this.ax.y) * this.hx + Math.abs(this.ay.y) * this.hy
                + Math.abs(this.az.y) * this.hz;
    }
}
