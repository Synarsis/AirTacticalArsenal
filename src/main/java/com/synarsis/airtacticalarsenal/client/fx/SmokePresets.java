package com.synarsis.airtacticalarsenal.client.fx;

import java.util.List;

public final class SmokePresets {

    public static final float SOFT_LIMIT = 7.0f;
    public static final float PIXEL_LIMIT = 3.0f;

    public record Limits(double minRise, double maxRise, double minSide, double maxSide, double maxLift) {
        static final double ANY = Double.POSITIVE_INFINITY;
    }

    public record Preset(String name, List<SmokeParams> variants, double[] lateral, double[] lift, Limits limits) {
    }

    private SmokePresets() {
    }

    public static SmokeParams limited(SmokeParams p) {
        float limit = p.pixel() ? PIXEL_LIMIT : SOFT_LIMIT;
        float s1 = Math.min(p.size1(), limit);
        float s0 = Math.min(p.size0(), s1);
        return p.withSize(s0, s1);
    }

    public static SmokeParams steam(boolean late) {
        float grey = late ? 0.72f : 0.97f;
        return limited(SmokeParams.soft(grey, grey, grey).withSize(0.5f, 1.5f).withLife(40).withGrowth(0, 40)
                .withDrift(0.88f, 3.2f, 1.0f).withAlpha(late ? 0.7f : 0.55f).withCollide(false));
    }

    public static SmokeParams launchCloud(float[] colour, float shade, boolean fine, int life) {
        float r = colour[0] * shade;
        float g = colour[1] * shade;
        float b = colour[2] * shade;
        return limited(SmokeParams.soft(r, g, b)
                .withColours(r, g, b, Math.min(1.0f, r + 0.1f), Math.min(1.0f, g + 0.1f), Math.min(1.0f, b + 0.1f))
                .withSize(fine ? 1.0f : 2.0f, fine ? 3.0f : 6.0f).withLife(life).withGrowth(0, 60)
                .withDrift(0.95f, 1.0f, 1.0f).withRiseDelay(80).withAlpha(0.6f).withPixel(fine));
    }

    public static SmokeParams trailColumn(float grey, int life, float size1) {
        return limited(SmokeParams.soft(grey, grey, grey).withColours(grey, grey, grey, grey * 0.8f, grey * 0.8f, grey * 0.82f)
                .withSize(1.5f, size1).withLife(life).withGrowth(0, Math.max(1, life / 2)).withDrift(0.985f, 0.0f, 1.0f)
                .withAlpha(0.85f).withCollide(false));
    }

    public static SmokeParams trailGround(float grey) {
        return limited(SmokeParams.soft(grey, grey, grey).withSize(1.5f, 6.0f).withLife(400).withGrowth(0, 200)
                .withDrift(0.97f, 0.0f, 1.0f).withAlpha(0.8f).withCollide(true));
    }

    public static SmokeParams fireSmoke(float size1, int life) {
        return limited(SmokeParams.soft(0.36f, 0.34f, 0.32f).withSize(size1 * 0.4f, size1).withLife(life)
                .withGrowth(0, 60).withDrift(0.96f, 7.0f, 1.0f).withAlpha(0.55f).withCollide(false));
    }

    public static SmokeParams dustRing(float[] colour, float shade, boolean fine, int life) {
        float r = colour[0] * shade;
        float g = colour[1] * shade;
        float b = colour[2] * shade;
        return limited(SmokeParams.soft(r, g, b).withSize(fine ? 1.0f : 2.0f, fine ? 3.0f : 6.0f).withLife(life)
                .withGrowth(0, 60).withDrift(0.9f, 0.5f, 1.0f).withAlpha(0.9f).withPixel(fine));
    }

    public static SmokeParams pillar(boolean cap) {
        return limited(SmokeParams.soft(0.16f, 0.15f, 0.14f).withColours(0.16f, 0.15f, 0.14f, 0.46f, 0.45f, 0.44f)
                .withSize(4.0f, 7.0f).withLife(1200).withGrowth(0, 300).withDrift(0.988f, 0.0f, 1.0f).withAlpha(0.92f)
                .withCollide(false).withCap(cap ? 0.06f : 0.0f));
    }

    public static SmokeParams emberSmoke() {
        return limited(SmokeParams.soft(0.45f, 0.45f, 0.46f).withSize(0.6f, 2.4f).withLife(120).withGrowth(0, 90)
                .withDrift(0.97f, 3.5f, 1.0f).withAlpha(0.45f).withCollide(false));
    }

    public static SmokeParams wreck(boolean fine, int life) {
        return limited(SmokeParams.soft(0.14f, 0.13f, 0.12f).withColours(0.14f, 0.13f, 0.12f, 0.4f, 0.4f, 0.4f)
                .withPixel(fine).withSize(fine ? 0.8f : 1.5f, fine ? 3.0f : 5.0f).withLife(life).withGrowth(0, 100)
                .withDrift(0.975f, 10.0f, 1.0f).withAlpha(0.8f).withCollide(false));
    }

    public static SmokeParams debrisTrail(int life) {
        return limited(SmokeParams.soft(0.3f, 0.3f, 0.3f).withPixel(true).withSize(0.35f, 1.1f).withLife(life)
                .withGrowth(0, 40).withDrift(0.94f, 2.5f, 1.0f).withAlpha(0.65f).withCollide(false));
    }

    public static SmokeParams creepingDust(float[] colour) {
        return limited(SmokeParams.soft(colour[0], colour[1], colour[2]).withPixel(true).withSize(0.8f, 3.0f)
                .withLife(70).withGrowth(0, 50).withDrift(0.93f, 0.0f, 1.0f).withAlpha(0.5f));
    }

    private static final float[] SAND = {0.80f, 0.72f, 0.52f};
    private static final float[] STONE = {0.58f, 0.58f, 0.60f};

    public static List<Preset> all() {
        double any = Limits.ANY;
        return List.of(
                new Preset("steam", List.of(steam(false), steam(true)), new double[] {0.0, 0.03}, new double[] {0.0, 0.02},
                        new Limits(3.0, 5.0, 0.0, any, 0.1)),
                new Preset("launch cloud", List.of(launchCloud(SAND, 0.94f, false, 300), launchCloud(SAND, 1.06f, false, 500),
                        launchCloud(STONE, 1.0f, true, 300), launchCloud(STONE, 1.0f, true, 500)),
                        new double[] {0.5, 1.3}, new double[] {0.0, 0.0}, new Limits(0.0, 3.0, 20.0, 30.0, 0.006)),
                new Preset("trail column", List.of(trailColumn(0.93f, 400, 6.0f), trailColumn(0.62f, 400, 7.0f),
                        trailColumn(0.98f, 800, 10.0f)),
                        new double[] {0.0, 0.03}, new double[] {0.0, 0.0}, new Limits(0.0, 0.0, 0.0, 3.0, 0.0)),
                new Preset("trail near the ground", List.of(trailGround(0.9f)),
                        new double[] {0.05, 0.15}, new double[] {0.0, 0.0}, new Limits(0.0, 0.0, 2.0, 5.0, 0.0)),
                new Preset("fire smoke", List.of(fireSmoke(5.0f, 80), fireSmoke(5.0f, 100), fireSmoke(14.0f, 90)),
                        new double[] {0.0, 0.0}, new double[] {0.0, 0.02}, new Limits(6.0, 10.0, 0.0, any, 0.15)),
                new Preset("dust ring", List.of(dustRing(SAND, 1.0f, false, 200), dustRing(SAND, 1.0f, false, 300),
                        dustRing(STONE, 1.0f, true, 200), dustRing(STONE, 1.0f, true, 300)),
                        new double[] {1.0, 1.7}, new double[] {0.0, 0.0}, new Limits(0.0, 1.0, 10.0, 16.0, 0.01)),
                new Preset("pillar", List.of(pillar(false)),
                        new double[] {0.0, 0.05}, new double[] {0.18, 0.36}, new Limits(14.0, 31.0, 0.0, 5.0, any)),
                new Preset("emberSmoke", List.of(emberSmoke()),
                        new double[] {0.0, 0.0}, new double[] {0.0, 0.02}, new Limits(3.0, 5.0, 0.0, any, 0.06)),
                new Preset("wreck", List.of(wreck(false, 180), wreck(false, 260), wreck(true, 180), wreck(true, 260)),
                        new double[] {0.0, 0.03}, new double[] {0.0, 0.06}, new Limits(10.0, 15.0, 0.0, any, 0.08)),
                new Preset("debris trail", List.of(debrisTrail(60), debrisTrail(80)),
                        new double[] {0.0, 0.0}, new double[] {0.0, 0.0}, new Limits(2.0, 4.0, 0.0, any, 0.08)));
    }

    public static SmokeParams cappedPillar() {
        return pillar(true);
    }
}
