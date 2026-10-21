package com.synarsis.airtacticalarsenal.client.fx.light;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LightSelect {

    public static final double MAX_DISTANCE = 192.0;

    public record Candidate(int id, double x, double y, double z, double radius, double intensity) {
    }

    @FunctionalInterface
    public interface SphereTest {
        boolean visible(double x, double y, double z, double radius);
    }

    private LightSelect() {
    }

    public static double contribution(double intensity, double radius, double distanceSquared) {
        return intensity * radius * radius / Math.max(distanceSquared, 1.0);
    }

    public static List<Candidate> pick(List<Candidate> all, double cx, double cy, double cz, SphereTest visible, int n) {
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : all) {
            double dx = c.x() - cx;
            double dy = c.y() - cy;
            double dz = c.z() - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > MAX_DISTANCE * MAX_DISTANCE || c.intensity() <= 0.0 || c.radius() <= 0.0) {
                continue;
            }
            if (visible.visible(c.x(), c.y(), c.z(), c.radius())) {
                kept.add(c);
            }
        }
        kept.sort(Comparator.comparingDouble((Candidate c) -> -contribution(c.intensity(), c.radius(),
                square(c.x() - cx) + square(c.y() - cy) + square(c.z() - cz))).thenComparingInt(Candidate::id));
        return kept.size() <= n ? kept : new ArrayList<>(kept.subList(0, Math.max(0, n)));
    }

    private static double square(double x) {
        return x * x;
    }
}
