package com.synarsis.airtacticalarsenal.combat.outcome;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Flies a crippled airframe. */
public final class FallController {

    private FallController() {
    }

    public record State(Vec3 velocity, float yaw, float pitch, float roll) {
    }

    /**
     * @param glideRatio blocks forward per block down with the engine out and wings intact
     * @param deadStickRatio the same once the airframe is damaged as well
     * @param glideSpeed airspeed a glide settles at, blocks per tick
     * @param poweredSpeed airspeed the modes that still have an engine settle at
     * @param spiralYawRate degrees of heading change per tick in a spiral
     * @param spiralBank how far it rolls into the missing wing, degrees
     * @param tumbleSpinRate degrees per tick, end over end
     * @param veerDriftRate degrees of heading drift per tick with guidance gone
     * @param terminalSpeed fastest it will fall, blocks per tick
     */
    public record Tuning(double glideRatio, double deadStickRatio, double glideSpeed,
                         double poweredSpeed, double spiralYawRate, double spiralBank,
                         double tumbleSpinRate, double veerDriftRate, double terminalSpeed) {

        /**
         * Glide ratios are deliberately poor. Seven to one is a sailplane; from a cruise at 165 it would
         * carry the drone over a kilometre and quite possibly onto the target after all, which defeats the
         * point of shooting the engine out. Three to one brings it down inside 500.
         */
        public static final Tuning DEFAULT =
                new Tuning(3.0, 1.8, 1.6, 1.5, 4.5, 35.0, 11.0, 0.125, 3.2);
    }

    private static final double GRAVITY = 0.045;
    private static final double SPEED_CONVERGENCE = 0.08;

    /**
     * Advances one tick.
     *
     * @param fallTick ticks since the airframe was crippled
     * @param spiralSign +1 or -1: which way it rolls, set by the wing it lost
     */
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

    /**
     * Engine out: trades height for airspeed and holds it, keeping its heading. This is the mode a
     * defender wants -- the airframe stays in one piece and lands well short of wherever it was sent.
     */
    private static State glide(State prev, double ratio, double target) {
        Vec3 v = prev.velocity();
        double speed = converge(horizontal(v), target);
        double sink = -speed / Math.max(ratio, 0.5);
        // Nose down onto the glide path promptly: the descent angle is the arctangent of the ratio.
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
            // Nothing to decay from: give it enough forward motion for the corkscrew to read.
            speed = converge(horizontal(v), 0.9);
        }
        double sink = Math.max(v.y - GRAVITY, -t.terminalSpeed());
        // Rolls in over the first couple of seconds rather than snapping over.
        float bank = (float) (t.spiralBank() * spiralSign * Math.min(1.0, fallTick / 30.0));
        float pitch = approach(prev.pitch(), -45.0f, 1.5f);
        return new State(headingVector(yaw, speed, sink), yaw, pitch, approach(prev.roll(), bank, 4.0f));
    }

    /** Nothing left to fly with: end over end, straight down. */
    private static State tumble(State prev, Tuning t) {
        Vec3 v = prev.velocity();
        double speed = horizontal(v) * 0.93;
        double sink = Math.max(v.y - GRAVITY, -t.terminalSpeed());
        float step = (float) t.tumbleSpinRate();
        return new State(headingVector(prev.yaw(), speed, sink), prev.yaw(),
                wrap(prev.pitch() + step), wrap(prev.roll() + step * 0.6f));
    }

    /** Eases an airspeed towards where it belongs. */
    private static double converge(double current, double target) {
        return current + (target - current) * SPEED_CONVERGENCE;
    }

    private static double horizontal(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    private static Vec3 headingVector(float yawDeg, double speed, double vy) {
        // Matches the mod's own convention: yaw is atan2(motion.x, motion.z).
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
