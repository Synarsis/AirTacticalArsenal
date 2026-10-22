package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;

public final class Hitch implements AutoCloseable {

    private final PhysicsWorld world;
    private final JoltVehicle tractor;
    private final JoltVehicle trailer;
    private final TrainSpec.Coupling coupling;
    private final double[] tractorAnchor;
    private final double[] trailerAnchor;
    private TwoBodyConstraint link;
    private boolean closed;

    private Hitch(PhysicsWorld world, JoltVehicle tractor, JoltVehicle trailer, TrainSpec.Coupling coupling,
            double[] tractorAnchor, double[] trailerAnchor) {
        this.world = world;
        this.tractor = tractor;
        this.trailer = trailer;
        this.coupling = coupling;
        this.tractorAnchor = tractorAnchor.clone();
        this.trailerAnchor = trailerAnchor.clone();
    }

    public static Hitch attach(PhysicsWorld world, JoltVehicle tractor, VehicleSpec trailerSpec,
            TrainSpec.Coupling coupling, double[] tractorAnchor, double[] trailerAnchor, double x, double y, double z,
            double yaw, double pitch, double roll) {
        JoltVehicle trailer = new JoltVehicle(world, trailerSpec, tractor.slot(), x, y, z, yaw, pitch, roll,
                tractor.wheeled());
        trailer.followBrakesOf(tractor);
        return new Hitch(world, tractor, trailer, coupling, tractorAnchor, trailerAnchor);
    }

    public JoltVehicle tractor() {
        return this.tractor;
    }

    public JoltVehicle trailer() {
        return this.trailer;
    }

    public JoltVehicle.Snapshot snapshot() {
        return this.trailer.snapshot();
    }

    public boolean linked() {
        return this.link != null;
    }

    public double[] articulation() {
        double[][] m = PoseMath.multiply(PoseMath.transpose(this.tractor.rotation()), this.trailer.rotation());
        return PoseMath.articulationAngles(m);
    }

    public void link() {
        if (this.link != null || this.closed || !this.trailer.wheeled()) {
            return;
        }
        this.link = TrainLink.create(this.world, this.tractor, this.trailer, this.coupling, this.tractorAnchor,
                this.trailerAnchor);
    }

    public void unlink() {
        if (this.link == null) {
            return;
        }
        TrainLink.remove(this.world, this.link);
        this.link = null;
    }

    public void setMode(JoltVehicle.Mode mode) {
        if (mode != JoltVehicle.Mode.DYNAMIC) {
            this.unlink();
        }
        this.trailer.setMode(mode);
        if (mode == JoltVehicle.Mode.DYNAMIC) {
            this.link();
        }
    }

    public void teleport(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg, Vec3 linear,
            Vec3 angular) {
        boolean wasLinked = this.link != null;
        this.unlink();
        this.trailer.teleport(x, y, z, yawDeg, pitchDeg, rollDeg, linear, angular);
        if (wasLinked) {
            this.link();
        }
    }

    public void moveKinematic(double x, double y, double z, double yawDeg, double pitchDeg, double rollDeg, double dt) {
        this.trailer.moveKinematic(x, y, z, yawDeg, pitchDeg, rollDeg, dt);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.unlink();
        this.closed = true;
        this.trailer.close();
    }
}
