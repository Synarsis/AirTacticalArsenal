package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;

final class HitchRig implements AutoCloseable {

    static final double GROUND = 64.0;
    static final double[] TRACTOR_ANCHOR = {0.0, 1.3125, 0.0};
    static final double[] TRAILER_ANCHOR = {0.0, 1.3125, 5.875};

    final PhysicsWorld world;
    final VehicleGround ground;
    final JoltVehicle tractor;
    final TrainSpec train;
    JoltVehicle freeTrailer;
    Hitch hitch;
    private int ticks;
    private double worstImpact;

    HitchRig(TrainSpec train, double tractorX, double tractorZ, double tractorYaw) {
        this(train, tractorX, tractorZ, tractorYaw, VehicleRig.FLAT);
    }

    HitchRig(TrainSpec train, double tractorX, double tractorZ, double tractorYaw, VehicleRig.Surface surface) {
        if (!JoltRuntime.load(Path.of("build", "test-natives"))) {
            throw new IllegalStateException("Jolt native library did not load");
        }
        this.train = train;
        this.world = new PhysicsWorld(false, 1.6);
        this.tractor = new JoltVehicle(this.world, VehicleSpecs.m983Tractor(), tractorX, GROUND + 0.05, tractorZ,
                tractorYaw, 0.0, 0.0);
        this.tractor.setDriven(true);
        this.ground = new VehicleGround(this.world, this.tractor.slot(), (px, pz, low, high) -> {
            double top = surface.top((int) Math.floor(px), (int) Math.floor(pz));
            return top >= low && top <= high ? top : Double.NaN;
        });
        this.ground.update(tractorX, GROUND, tractorZ);
    }

    void placeFreeTrailer(double x, double z, double yaw) {
        this.freeTrailer = new JoltVehicle(this.world, this.train.trailer, x, GROUND, z, yaw, 0.0, 0.0, false);
        this.freeTrailer.setMode(JoltVehicle.Mode.STATIC);
    }

    void tick() {
        JoltVehicle.Snapshot s = this.tractor.snapshot();
        this.ground.update(s.x(), s.y(), s.z());
        this.world.step();
        this.ticks++;
        double hit = this.tractor.takeImpactSpeed();
        if (hit > 0.0 && Math.abs(this.tractor.lastImpactNormalY()) < 0.7) {
            this.worstImpact = Math.max(this.worstImpact, hit);
        }
    }

    void run(int count) {
        for (int i = 0; i < count; i++) {
            this.tick();
        }
    }

    double worstImpact() {
        return this.worstImpact / Units.TICKS_PER_SECOND;
    }

    int ticks() {
        return this.ticks;
    }

    void drive(double throttle, double steer, double brake, double handbrake) {
        this.tractor.setControls(new JoltVehicle.Controls(throttle, steer, brake, handbrake));
    }

    double forwardKmh() {
        return Units.msToKmh(this.tractor.forwardSpeed());
    }

    Vec3 saddle() {
        JoltVehicle.Snapshot s = this.tractor.snapshot();
        return M983Coupling.worldOf(s.x(), s.y(), s.z(), (float) s.yawDeg(), (float) s.pitchDeg(), (float) s.rollDeg(),
                TRACTOR_ANCHOR);
    }

    Vec3 kingpin() {
        JoltVehicle.Snapshot s = this.freeTrailer.snapshot();
        return M983Coupling.worldOf(s.x(), s.y(), s.z(), (float) s.yawDeg(), (float) s.pitchDeg(), (float) s.rollDeg(),
                TRAILER_ANCHOR);
    }

    M983Coupling.Verdict verdict() {
        Vec3 saddle = this.saddle();
        Vec3 kingpin = this.kingpin();
        double bend = M983Coupling.bend((float) this.tractor.snapshot().yawDeg(),
                (float) this.freeTrailer.snapshot().yawDeg());
        return M983Coupling.verdict(kingpin.x - saddle.x, kingpin.y - saddle.y, kingpin.z - saddle.z, bend);
    }

    void couple(boolean linked) {
        JoltVehicle.Snapshot t = this.tractor.snapshot();
        JoltVehicle.Snapshot f = this.freeTrailer.snapshot();
        double bend = M983Coupling.bend((float) t.yawDeg(), (float) f.yawDeg());
        double[] pose = M983Coupling.trailerPoseFor(t.x(), t.y(), t.z(), (float) t.yawDeg(), (float) t.pitchDeg(),
                (float) t.rollDeg(), bend);
        this.freeTrailer.close();
        this.freeTrailer = null;
        this.hitch = Hitch.attach(this.world, this.tractor, this.train.trailer, this.train.coupling, TRACTOR_ANCHOR,
                TRAILER_ANCHOR, pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]);
        this.hitch.trailer().setVelocity(0, 0, 0, 0, 0, 0);
        if (linked) {
            this.hitch.setMode(JoltVehicle.Mode.DYNAMIC);
        }
    }

    void uncouple() {
        JoltVehicle.Snapshot f = this.hitch.snapshot();
        this.hitch.close();
        this.hitch = null;
        this.freeTrailer = new JoltVehicle(this.world, this.train.trailer, f.x(), f.y(), f.z(), f.yawDeg(),
                f.pitchDeg(), f.rollDeg(), false);
        this.freeTrailer.setMode(JoltVehicle.Mode.STATIC);
    }

    String telemetry() {
        JoltVehicle.Snapshot s = this.tractor.snapshot();
        String free = "";
        if (this.freeTrailer != null) {
            Vec3 saddle = this.saddle();
            Vec3 kingpin = this.kingpin();
            free = String.format(java.util.Locale.ROOT, " pin dx=%.2f dy=%.2f dz=%.2f", kingpin.x - saddle.x,
                    kingpin.y - saddle.y, kingpin.z - saddle.z);
        }
        return String.format(java.util.Locale.ROOT, "t=%5.2fs pos=(%.2f %.2f %.2f) yaw=%.1f v=%.2fkm/h%s",
                this.ticks / 20.0, s.x(), s.y(), s.z(), s.yawDeg(), this.forwardKmh(), free);
    }

    @Override
    public void close() {
        if (this.hitch != null) {
            this.hitch.close();
        }
        if (this.freeTrailer != null) {
            this.freeTrailer.close();
        }
        this.tractor.close();
        this.ground.close();
        this.world.close();
    }
}
