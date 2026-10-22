package com.synarsis.airtacticalarsenal.client.fx.light;

public final class LightMath {

    public static final double CORE = 0.25;
    public static final double KNEE = 0.8;
    public static final double SHOULDER_WIDTH = 0.2;
    public static final double ALBEDO_GAIN = 0.75;
    public static final double ALBEDO_FLOOR = 0.4;
    public static final double GAMMA = 2.2;
    public static final double DAY_STRENGTH = 0.5;
    public static final double FLAT_SHADE = 0.6;

    private LightMath() {
    }

    public static double saturate(double x) {
        return x < 0.0 ? 0.0 : Math.min(x, 1.0);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = saturate((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double attenuation(double d, double radius) {
        if (radius <= 0.0) {
            return 0.0;
        }
        double x = d / radius;
        double window = saturate(1.0 - x * x * x * x);
        double core = CORE * radius;
        double r = d / core;
        return window * window / (1.0 + r * r);
    }

    public static double cone(double cosAngle, double cosOuter, double cosInner) {
        return smoothstep(cosOuter, cosInner, cosAngle);
    }

    public static final double FILL = 0.07;
    public static final double FILL_REACH = 0.5;
    public static final double SPILL = 0.04;
    public static final double SPILL_DEGREES = 70.0;
    public static final double HEADLIGHT_INNER_DEGREES = 18.0;
    public static final double HEADLIGHT_OUTER_DEGREES = 38.0;

    public static double coneSoft(double cosAngle, double cosOuter, double cosInner) {
        double s = smoothstep(cosOuter, cosInner, cosAngle);
        return s * s;
    }

    public static double spill(double cosAngle, double cosOuter) {
        return SPILL * smoothstep(Math.cos(Math.toRadians(SPILL_DEGREES)), cosOuter, cosAngle);
    }

    public static double spot(double cosAngle, double cosOuter, double cosInner) {
        double inside = coneSoft(cosAngle, cosOuter, cosInner);
        return inside + spill(cosAngle, cosOuter) * (1.0 - inside);
    }

    public static double fill(double d, double radius, double axisDot, boolean spot) {
        double front = spot ? smoothstep(0.0, 0.2, axisDot) : 1.0;
        return FILL * attenuation(d, FILL_REACH * radius) * front;
    }

    public static double interleavedGradientNoise(double x, double y) {
        double inner = x * 0.06711056 + y * 0.00583715;
        double a = 52.9829189 * (inner - Math.floor(inner));
        return a - Math.floor(a);
    }

    public static double[] discPoint(int index, int count, double radius, double noise) {
        double angle = 2.0 * Math.PI * (noise + (double) index / count);
        double r = radius * Math.sqrt((index + 0.5) / count);
        return new double[] {Math.cos(angle) * r, Math.sin(angle) * r};
    }

    public static double shade(double wrap, double normalDotLight) {
        return wrap + (1.0 - wrap) * Math.max(normalDotLight, 0.0);
    }

    public static double fogKeep(double distance, double fogStart, double fogEnd) {
        if (fogEnd <= fogStart) {
            return 1.0;
        }
        return 1.0 - saturate((distance - fogStart) / (fogEnd - fogStart));
    }

    public static double[] normal(double[] c, double[] xp, double[] xn, double[] yp, double[] yn) {
        double[] dx = Math.abs(xp[2] - c[2]) < Math.abs(c[2] - xn[2]) ? sub(xp, c) : sub(c, xn);
        double[] dy = Math.abs(yp[2] - c[2]) < Math.abs(c[2] - yn[2]) ? sub(yp, c) : sub(c, yn);
        double[] n = cross(dx, dy);
        double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len < 1.0E-12) {
            return new double[] {0.0, 0.0, 1.0};
        }
        n[0] /= len;
        n[1] /= len;
        n[2] /= len;
        if (n[0] * -c[0] + n[1] * -c[1] + n[2] * -c[2] < 0.0) {
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
        }
        return n;
    }

    public static double[] viewPosition(double u, double v, double depth, double tanHalfX, double tanHalfY, double near,
            double far) {
        double ndcZ = depth * 2.0 - 1.0;
        double z = -2.0 * far * near / ((far + near) - ndcZ * (far - near));
        double x = (u * 2.0 - 1.0) * tanHalfX * -z;
        double y = (v * 2.0 - 1.0) * tanHalfY * -z;
        return new double[] {x, y, z};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double toLinear(double c) {
        return Math.pow(Math.max(c, 0.0), GAMMA);
    }

    public static double toSrgb(double c) {
        return Math.pow(Math.max(c, 0.0), 1.0 / GAMMA);
    }

    public static double luma(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static double shoulder(double x) {
        if (x <= KNEE) {
            return x;
        }
        return KNEE + SHOULDER_WIDTH * (1.0 - Math.exp(-(x - KNEE) / SHOULDER_WIDTH));
    }

    public static double daylight(double sky, double skyLight) {
        return saturate(sky) * saturate(skyLight / 15.0);
    }

    public static double skyBrightness(double timeOfDay, double rain, boolean hasSky) {
        if (!hasSky) {
            return 0.0;
        }
        double sun = saturate(Math.cos(timeOfDay * Math.PI * 2.0) * 2.0 + 0.2);
        return sun * (1.0 - saturate(rain) * 5.0 / 16.0);
    }

    public static double dayFactor(double daylight) {
        return 1.0 + (DAY_STRENGTH - 1.0) * saturate(daylight);
    }

    public static double[] composite(double[] scene, double[] light, double scale) {
        double r = toLinear(scene[0]);
        double g = toLinear(scene[1]);
        double b = toLinear(scene[2]);
        double lum = luma(r, g, b);
        double hue = 1.0 / Math.max(lum, 0.02);
        double gain = Math.max(ALBEDO_FLOOR, Math.min(1.0, lum * 8.0)) * ALBEDO_GAIN;
        double[] out = new double[3];
        double[] c = {r, g, b};
        for (int i = 0; i < 3; i++) {
            double alb = c[i] * hue * gain;
            double add = alb * light[i] * scale;
            out[i] = toSrgb(c[i] + Math.max(shoulder(c[i] + add) - shoulder(c[i]), 0.0));
        }
        return out;
    }
}
