package com.synarsis.airtacticalarsenal.combat.outcome;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FallController {

    private FallController() {
    }

    public record State(Vec3 velocity, float yaw, float pitch, float roll) {
    }

    public record Tuning(double glideRatio, double deadStickRatio, double glideSpeed,
                         double poweredSpeed, double spiralYawRate, double spiralBank,
                         double tumbleSpinRate, double veerDriftRate, double terminalSpeed) {

        public static final Tuning DEFAULT =
                new Tuning(3.0, 1.8, 1.6, 1.5, 4.5, 35.0, 11.0, 0.125, 3.2);
    }

    private static final double GRAVITY = 0.045;
    private static final double SPEED_CONVERGENCE = 0.08;

    public static State step(FallMode mode, int fallTick, int spiralSign, State prev, Tuning t) {
        return switch (mode) {
            case NONE -> prev;
            case VEER -> veer(prev, t);
            case BURNING -> burning(prev, t);
            case GLIDE -> glide(prev, t.glideRatio(), t.glideSpeed());
            case DEAD_STICK -> glide(prev, t.deadStickRatio(), t.glideSpeed());
            case SPIRAL -> spiral(prev, fallTick, spiralSign, t);
            case TUMBLE -> tumble(prev, t);
        };
    }

    private static State veer(State prev, Tuning t) {
        float yaw = wrap(prev.yaw() + (float) t.veerDriftRate());
        Vec3 v = prev.velocity();
        double speed = converge(horizontal(v), t.poweredSpeed());
        return new State(headingVector(yaw, speed, v.y * 0.98), yaw, prev.pitch(),
                approach(prev.roll(), 0.0f, 1.5f));
    }

    private static State burning(State prev, Tuning t) {
        Vec3 v = prev.velocity();
        double speed = converge(horizontal(v), t.poweredSpeed());
        double sink = Math.max(v.y - 0.004, -0.35);
        return new State(headingVector(prev.yaw(), speed, sink), prev.yaw(), prev.pitch(), prev.roll());
    }

    private static State glide(State prev, double ratio, double target) {
        Vec3 v = prev.velocity();
        double speed = converge(horizontal(v), target);
        double sink = -speed / Math.max(ratio, 0.5);
        float wanted = (float) -Math.toDegrees(Math.atan(1.0 / Math.max(ratio, 0.5)));
        float pitch = approach(prev.pitch(), wanted, 3.0f);
        return new State(headingVector(prev.yaw(), speed, sink), prev.yaw(), pitch,
                approach(prev.roll(), 0.0f, 3.0f));
    }

    private static State spiral(State prev, int fallTick, int spiralSign, Tuning t) {
        float yaw = wrap(prev.yaw() + (float) (t.spiralYawRate() * spiralSign));
        Vec3 v = prev.velocity();
        double speed = Math.max(horizontal(v) * 0.97, 0.6);
        if (horizontal(v) < 0.2) {
            speed = converge(horizontal(v), 0.9);
        }
        double sink = Math.max(v.y - GRAVITY, -t.terminalSpeed());
        float bank = (float) (t.spiralBank() * spiralSign * Math.min(1.0, fallTick / 30.0));
        float pitch = approach(prev.pitch(), -45.0f, 1.5f);
        return new State(headingVector(yaw, speed, sink), yaw, pitch, approach(prev.roll(), bank, 4.0f));
    }

    private static State tumble(State prev, Tuning t) {
        Vec3 v = prev.velocity();
        double speed = horizontal(v) * 0.93;
        double sink = Math.max(v.y - GRAVITY, -t.terminalSpeed());
        float step = (float) t.tumbleSpinRate();
        return new State(headingVector(prev.yaw(), speed, sink), prev.yaw(),
                wrap(prev.pitch() + step), wrap(prev.roll() + step * 0.6f));
    }

    private static double converge(double current, double target) {
        return current + (target - current) * SPEED_CONVERGENCE;
    }

    private static double horizontal(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    private static Vec3 headingVector(float yawDeg, double speed, double vy) {
        double rad = Math.toRadians(yawDeg);
        return new Vec3(Math.sin(rad) * speed, vy, Math.cos(rad) * speed);
    }

    private static float approach(float from, float to, float maxStep) {
        float diff = Mth.degreesDifference(from, to);
        return wrap(from + Mth.clamp(diff, -maxStep, maxStep));
    }

    private static float wrap(float degrees) {
        return Mth.wrapDegrees(degrees);
    }
}
