package com.synarsis.airtacticalarsenal.client.fx;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FxTrail {

    private double carry;

    public List<Vec3> advance(Vec3 from, Vec3 to, double step) {
        List<Vec3> out = new ArrayList<>();
        double length = from.distanceTo(to);
        if (step <= 0.0 || length <= 1.0E-9) {
            return out;
        }
        double at = step - this.carry;
        while (at <= length + 1.0E-9) {
            out.add(from.lerp(to, at / length));
            at += step;
        }
        this.carry = length - (at - step);
        return out;
    }

    public void reset() {
        this.carry = 0.0;
    }
}
