package com.synarsis.airtacticalarsenal.entity.tel.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelInput;

public final class TelEngine {

    private TelEngine() {
    }

    public record Params(double inc, double dec, double steer, double maxFwd, double maxBack, double wheelRadius,
            double brakePower, double brakeDamp) {
    }

    public static final Params DEFAULTS = new Params(0.018, 0.009, 0.035, 0.95, 0.45, 0.72, 0.08, 0.92);

    public record Context(double vx, double vy, double vz, double fx, double fy, double fz,
            boolean onGround, boolean inFluid, double submersion, double pitchDeg, boolean hasDriver,
            Params params, double traction) {
        public Context(double vx, double vy, double vz, double fx, double fy, double fz,
                boolean onGround, boolean inFluid, double submersion, double pitchDeg, boolean hasDriver,
                Params params) {
            this(vx, vy, vz, fx, fy, fz, onGround, inFluid, submersion, pitchDeg, hasDriver, params, 1.0);
        }
    }

    public record Result(double vx, double vy, double vz, double yawDelta) {
    }

    public static Result step(TelEngineState s, int input, Context c) {
        Params p = c.params();
        boolean fwd = (input & TelInput.FWD) != 0;
        boolean back = (input & TelInput.BACK) != 0;
        boolean left = (input & TelInput.LEFT) != 0;
        boolean right = (input & TelInput.RIGHT) != 0;
        boolean brake = (input & TelInput.BRAKE) != 0;
        boolean boost = (input & TelInput.BOOST) != 0;
        if (!c.hasDriver()) {
            fwd = false;
            back = false;
            left = false;
            right = false;
            brake = false;
            boost = false;
            s.power = 0.0;
        }

        boolean driveFwd = fwd && !back;
        boolean driveBack = back && !fwd;
        boolean steering = left != right;
        boolean brakeTurn = brake && steering;

        double vx = c.vx();
        double vy = c.vy();
        double vz = c.vz();
        double fx = c.fx();
        double fy = c.fy();
        double fz = c.fz();
        double inc = p.inc();
        double dec = p.dec();

        double speed2 = vx * vx + vy * vy + vz * vz;
        double speed = Math.sqrt(speed2);
        double along = vx * fx + vy * fy + vz * fz;
        double cosAbs = speed > 1.0E-9 ? Math.abs(along) / speed : 0.0;
        if (c.inFluid()) {
            inc *= 0.1;
            dec *= 0.1;
            double k = clamp(0.9 + 0.09 * cosAbs - 4.0 * speed2 - c.submersion() * 0.02, 0.0, 0.99);
            vx += fx * 0.04 * along;
            vy += fy * 0.04 * along;
            vz += fz * 0.04 * along;
            vx *= k;
            vy *= 0.85;
            vz *= k;
        } else if (c.onGround()) {
            double k = brakeTurn ? 0.96 : 0.54 + 0.25 * cosAbs;
            double g = brakeTurn ? 0.001 : 0.05;
            vx += fx * g * along;
            vy += fy * g * along;
            vz += fz * g * along;
            vx *= k;
            vy *= 0.99;
            vz *= k;
        } else {
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
        }

        double maxPower = boost ? 1.3 : (s.power > 1.0 ? s.power - 0.002 : 1.0);
        double fHoriz = Math.hypot(fx, fz);
        double rolling = fHoriz > 1.0E-6 ? (vx * fx + vz * fz) / fHoriz : 0.0;
        boolean serviceBrake = (driveBack && rolling > 0.02) || (driveFwd && rolling < -0.02);
        if (serviceBrake) {
            s.power = s.power > 0.0 ? Math.max(s.power - p.brakePower(), 0.0) : Math.min(s.power + p.brakePower(), 0.0);
            vx *= p.brakeDamp();
            vz *= p.brakeDamp();
        } else if (driveFwd) {
            double rate = s.power < 0.0 ? 2.0 * inc : inc;
            s.power = Math.min(s.power + rate * (maxPower - Math.abs(s.power) / 1.02), maxPower);
        } else if (driveBack) {
            double rate = s.power > 0.0 ? 4.0 * dec : dec;
            s.power = Math.max(s.power - rate * (maxPower - Math.abs(s.power) / 1.02), -1.0);
        }

        double targetSpeed = s.power > 0.0
                ? p.maxFwd() * (1.0 + c.pitchDeg() / 60.0)
                : p.maxBack() * (1.0 - c.pitchDeg() / 60.0);
        targetSpeed = Math.max(targetSpeed, 0.0);

        if (!driveFwd && !driveBack) {
            s.power *= 0.97;
        }
        if (brake) {
            s.power *= c.inFluid() ? 0.97 : (brakeTurn ? 0.93 : 0.6);
            if (c.onGround() && !c.inFluid() && !brakeTurn) {
                vx *= 0.85;
                vz *= 0.85;
            }
        }
        if (steering) {
            s.power *= 0.995;
        }

        double steer = p.steer() * (brakeTurn ? 1.5 : 1.0);
        if (right && !left) {
            s.holdTick++;
            s.deltaRot += steer * 0.12 * Math.min(s.holdTick, 10);
        } else if (left && !right) {
            s.holdTick++;
            s.deltaRot -= steer * 0.12 * Math.min(s.holdTick, 10);
        } else {
            s.holdTick = 0;
        }
        double hs = Math.hypot(vx, vz);
        s.deltaRot *= Math.max(0.78 - 0.25 * hs, 0.1);

        double rolled = vx * fx + vy * fy + vz * fz;
        advanceWheels(s, rolled, Math.sqrt(vx * vx + vy * vy + vz * vz), p.wheelRadius());

        s.rudder = clamp(s.rudder - s.deltaRot, -0.8, 0.8) * 0.75;
        double turnRate = (c.inFluid() && !c.onGround()) ? 6.0 : 12.0;
        double yawDelta = -Math.max(turnRate * hs, 0.0) * s.rudder * (s.power > 0.0 ? 1.0 : -1.0);

        if (c.onGround() || c.inFluid()) {
            double push = (brakeTurn ? 0.02 : 0.15) * targetSpeed * s.power * c.traction();
            vx += fx * push;
            vy += fy * push;
            vz += fz * push;
        }
        return new Result(vx, vy, vz, yawDelta);
    }

    public static void advanceWheels(TelEngineState s, double rolled, double speed, double wheelRadius) {
        double diff = clamp(0.5 * s.deltaRot, -5.0, 5.0) * speed;
        s.wheelRotL += (rolled + diff) / wheelRadius;
        s.wheelRotR += (rolled - diff) / wheelRadius;
    }

    public static void observe(TelEngineState s, double vx, double vy, double vz,
            double fx, double fy, double fz, double yawDeltaDeg, Params p) {
        double forward = vx * fx + vy * fy + vz * fz;
        double hs = Math.hypot(vx, vz);
        double powerNow = clamp(forward / (0.88 * p.maxFwd()), -1.3, 1.3);
        s.power += (powerNow - s.power) * 0.2;

        if (hs > 0.03) {
            double sign = forward >= 0.0 ? 1.0 : -1.0;
            double rudderNow = clamp(-yawDeltaDeg / (12.0 * hs * sign), -0.8, 0.8);
            s.rudder += (rudderNow - s.rudder) * 0.3;
        } else {
            s.rudder *= 0.8;
        }
        s.deltaRot = -s.rudder / 3.0;
        advanceWheels(s, forward, Math.sqrt(vx * vx + vy * vy + vz * vz), p.wheelRadius());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
