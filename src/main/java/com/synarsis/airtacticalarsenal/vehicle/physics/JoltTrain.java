package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SwingTwistConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.ESwingType;

public final class JoltTrain implements AutoCloseable {

    private final PhysicsWorld world;
    private final TrainSpec spec;
    private final JoltVehicle tractor;
    private final JoltVehicle trailer;
    private TwoBodyConstraint link;
    private boolean closed;

    public JoltTrain(PhysicsWorld world, TrainSpec spec, double x, double y, double z, double yawDeg, double pitchDeg,
            double rollDeg, double articulationYaw, double articulationPitch, double articulationRoll,
            boolean wheeled) {
        this.world = world;
        this.spec = spec;
        this.tractor = new JoltVehicle(world, spec.tractor, x, y, z, yawDeg, pitchDeg, rollDeg, wheeled);
        try {
            this.trailer = new JoltVehicle(world, spec.trailer, this.tractor.slot(), x, y, z, yawDeg, pitchDeg,
                    rollDeg, wheeled);
        } catch (RuntimeException error) {
            this.tractor.close();
            throw error;
        }
        this.trailer.followBrakesOf(this.tractor);
        this.placeTrailer(articulationYaw, articulationPitch, articulationRoll, null, null);
        if (wheeled) {
            this.link();
        }
    }

    public JoltVehicle tractor() {
        return this.tractor;
    }

    public JoltVehicle trailer() {
        return this.trailer;
    }

    public TrainSpec spec() {
        return this.spec;
    }

    public int slot() {
        return this.tractor.slot();
    }

    public boolean linked() {
        return this.link != null;
    }

    private double[][] trailerRotation(double[][] tractorRotation, double yaw, double pitch, double roll) {
        return PoseMath.multiply(tractorRotation, PoseMath.articulation(yaw, pitch, roll));
    }

    private double[] trailerOrigin(double[] tractorOrigin, double[][] tractorRotation, double[][] m) {
        double[] k = this.spec.coupling.kingpin;
        double[] mk = PoseMath.apply(m, k);
        double[] shift = PoseMath.apply(tractorRotation, new double[] {k[0] - mk[0], k[1] - mk[1], k[2] - mk[2]});
        return new double[] {tractorOrigin[0] + shift[0], tractorOrigin[1] + shift[1], tractorOrigin[2] + shift[2]};
    }

    public void placeTrailer(double yaw, double pitch, double roll, Vec3 linear, Vec3 angular) {
        double[][] rt = this.tractor.rotation();
        double[][] m = PoseMath.articulation(yaw, pitch, roll);
        this.trailer.teleportBody(this.trailerOrigin(this.tractor.origin(), rt, m), PoseMath.multiply(rt, m), linear,
                angular);
    }

    public double[] articulation() {
        double[][] m = PoseMath.multiply(PoseMath.transpose(this.tractor.rotation()), this.trailer.rotation());
        return PoseMath.articulationAngles(m);
    }

    public void teleport(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg,
            double articulationYaw, double articulationPitch, double articulationRoll, Vec3 linear, Vec3 angular) {
        boolean wasLinked = this.link != null;
        this.unlink();
        this.tractor.teleport(x, y, z, yawDeg, pitchDeg, rollDeg, linear, angular);
        this.placeTrailer(articulationYaw, articulationPitch, articulationRoll, linear, angular);
        if (wasLinked) {
            this.link();
        }
    }

    public void moveKinematic(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg,
            double articulationYaw, double articulationPitch, double articulationRoll, double dt) {
        this.tractor.moveKinematic(x, y, z, yawDeg, pitchDeg, rollDeg, dt);
        double[] origin = PoseMath.bodyOrigin(x, y, z, yawDeg, pitchDeg, rollDeg);
        double[][] rt = PoseMath.rotation(yawDeg, pitchDeg, rollDeg);
        double[][] m = PoseMath.articulation(articulationYaw, articulationPitch, articulationRoll);
        this.trailer.moveKinematicBody(this.trailerOrigin(origin, rt, m), PoseMath.multiply(rt, m), dt);
    }

    public void setVelocity(double vx, double vy, double vz, double wx, double wy, double wz) {
        this.tractor.setVelocity(vx, vy, vz, wx, wy, wz);
        this.trailer.setVelocity(vx, vy, vz, wx, wy, wz);
    }

    public void setMode(JoltVehicle.Mode mode) {
        if (mode != JoltVehicle.Mode.DYNAMIC) {
            this.unlink();
        }
        this.tractor.setMode(mode);
        this.trailer.setMode(mode);
        if (mode == JoltVehicle.Mode.DYNAMIC && this.tractor.wheeled()) {
            this.link();
        }
    }

    public void detachTractor() {
        this.unlink();
        this.trailer.setMode(JoltVehicle.Mode.STATIC);
        this.tractor.setMode(JoltVehicle.Mode.KINEMATIC);
    }

    public void reattachTractor() {
        this.tractor.setMode(JoltVehicle.Mode.DYNAMIC);
        this.trailer.setMode(JoltVehicle.Mode.DYNAMIC);
        if (this.tractor.wheeled()) {
            this.link();
        }
    }

    public void link() {
        if (this.link != null || this.closed) {
            return;
        }
        double[][] rt = this.tractor.rotation();
        double[][] rr = this.trailer.rotation();
        double[] ot = this.tractor.origin();
        double[] or = this.trailer.origin();
        double[] k = this.spec.coupling.kingpin;
        double[] p1 = PoseMath.toWorld(ot, rt, k[0], k[1], k[2]);
        double[] p2 = PoseMath.toWorld(or, rr, k[0], k[1], k[2]);
        TrainSpec.Coupling c = this.spec.coupling;
        TwoBodyConstraintSettings settings;
        if (TrainSpec.SWING_TWIST.equals(c.type)) {
            SwingTwistConstraintSettings s = new SwingTwistConstraintSettings();
            s.setSpace(EConstraintSpace.WorldSpace);
            s.setPosition1(p1[0], p1[1], p1[2]);
            s.setPosition2(p2[0], p2[1], p2[2]);
            s.setTwistAxis1(column(rt, 2));
            s.setTwistAxis2(column(rr, 2));
            s.setPlaneAxis1(column(rt, 0));
            s.setPlaneAxis2(column(rr, 0));
            s.setSwingType(ESwingType.Cone);
            s.setTwistMinAngle((float) -Math.toRadians(c.rollLimitDegrees));
            s.setTwistMaxAngle((float) Math.toRadians(c.rollLimitDegrees));
            s.setPlaneHalfConeAngle((float) Math.toRadians(c.yawLimitDegrees));
            s.setNormalHalfConeAngle((float) Math.toRadians(c.pitchLimitDegrees));
            s.setMaxFrictionTorque((float) c.yawFrictionNm);
            settings = s;
        } else {
            SixDofConstraintSettings s = new SixDofConstraintSettings();
            s.setSpace(EConstraintSpace.WorldSpace);
            s.setPosition1(p1[0], p1[1], p1[2]);
            s.setPosition2(p2[0], p2[1], p2[2]);
            s.setAxisX1(column(rt, 0));
            s.setAxisY1(column(rt, 1));
            s.setAxisX2(column(rr, 0));
            s.setAxisY2(column(rr, 1));
            s.setSwingType(ESwingType.Pyramid);
            s.makeFixedAxis(EAxis.TranslationX);
            s.makeFixedAxis(EAxis.TranslationY);
            s.makeFixedAxis(EAxis.TranslationZ);
            s.setLimitedAxis(EAxis.RotationX, (float) -Math.toRadians(c.pitchLimitDegrees),
                    (float) Math.toRadians(c.pitchLimitDegrees));
            s.setLimitedAxis(EAxis.RotationY, (float) -Math.toRadians(c.yawLimitDegrees),
                    (float) Math.toRadians(c.yawLimitDegrees));
            s.setLimitedAxis(EAxis.RotationZ, (float) -Math.toRadians(c.rollLimitDegrees),
                    (float) Math.toRadians(c.rollLimitDegrees));
            if (c.yawFrictionNm > 0.0) {
                s.setMaxFriction(EAxis.RotationY, (float) c.yawFrictionNm);
            }
            settings = s;
        }
        try {
            this.link = settings.create(this.tractor.body(), this.trailer.body());
        } finally {
            settings.close();
        }
        this.world.system().addConstraint(this.link);
    }

    private static Vec3 column(double[][] m, int c) {
        return new Vec3((float) m[0][c], (float) m[1][c], (float) m[2][c]);
    }

    public void unlink() {
        if (this.link == null) {
            return;
        }
        if (!this.world.isClosed()) {
            this.world.system().removeConstraint(this.link);
        }
        this.link.close();
        this.link = null;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.unlink();
        this.closed = true;
        this.trailer.close();
        this.tractor.close();
    }
}
