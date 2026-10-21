package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves;
import net.minecraft.world.phys.Vec3;

public final class FxGlow {

    public static final double FULL_TINT = 4.0;
    public static final double TINT_GAIN = 1.5;

    private FxGlow() {
    }

    public static AtaLights.Light add(Vec3 pos, double radius, float red, float green, float blue, double peak, int life) {
        return AtaLights.point(pos).colour(red, green, blue).strength(peak * FULL_TINT, radius)
                .life(0, 0, life, LightCurves.Fall.LINEAR);
    }

    public static double tint(double x, double y, double z, float[] rgb) {
        double lit = 0.0;
        for (AtaLights.Light l : AtaLights.all()) {
            if (!l.tintsParticles() || !l.alive()) {
                continue;
            }
            double strength = Math.min(1.0, l.strength(0.0f) / FULL_TINT);
            if (strength <= 0.0) {
                continue;
            }
            double radius = l.radius(0.0f);
            Vec3 p = l.position(0.0f);
            double dx = x - p.x;
            double dy = y - p.y;
            double dz = z - p.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 >= radius * radius) {
                continue;
            }
            double k = 1.0 - Math.sqrt(d2) / radius;
            double w = Math.min(1.0, strength * k * k * TINT_GAIN);
            rgb[0] += (float) ((l.red() - rgb[0]) * w);
            rgb[1] += (float) ((l.green() - rgb[1]) * w);
            rgb[2] += (float) ((l.blue() - rgb[2]) * w);
            lit = Math.max(lit, w);
        }
        return lit;
    }
}
