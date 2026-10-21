package com.synarsis.airtacticalarsenal.vehicle.physics;

import java.nio.file.Path;

final class TrainRig implements AutoCloseable {

    static final VehicleRig.Surface FLAT = VehicleRig.FLAT;

    final PhysicsWorld world;
    final JoltTrain train;
    private final VehicleRig.Surface surface;
    private final VehicleGround ground;
    private int ticks;

    TrainRig(VehicleRig.Surface surface, TrainSpec spec, double x, double z, double yawDeg) {
        if (!JoltRuntime.load(Path.of("build", "test-natives"))) {
            throw new IllegalStateException("Jolt native library did not load");
        }
        this.surface = surface;
        this.world = new PhysicsWorld(false, 1.6);
        double startY = surface.top((int) Math.floor(x), (int) Math.floor(z));
        this.ground = new VehicleGround(this.world, 0, (px, pz, low, high) -> {
            double top = this.surface.top((int) Math.floor(px), (int) Math.floor(pz));
            return top >= low && top <= high ? top : Double.NaN;
        });
        this.ground.update(x, startY, z);
        this.train = new JoltTrain(this.world, spec, x, startY + 0.05, z, yawDeg, 0.0, 0.0, 0.0, 0.0, 0.0, true);
        this.train.tractor().setDriven(true);
    }

    void tick() {
        JoltVehicle.Snapshot s = this.train.tractor().snapshot();
        this.ground.update(s.x(), s.y(), s.z());
        this.world.step();
        this.ticks++;
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            this.tick();
        }
    }

    double seconds() {
        return this.ticks / 20.0;
    }

    double forwardKmh() {
        return Units.msToKmh(this.train.tractor().forwardSpeed());
    }

    void drive(double throttle, double steer, double brake, double handbrake) {
        this.train.tractor().setControls(new JoltVehicle.Controls(throttle, steer, brake, handbrake));
    }

    String telemetry() {
        JoltVehicle.Snapshot s = this.train.tractor().snapshot();
        double[] a = this.train.articulation();
        return String.format(java.util.Locale.ROOT,
                "t=%5.2fs pos=(%.2f %.2f %.2f) yaw=%.1f pitch=%.2f roll=%.2f v=%.1fkm/h art=(%.2f %.2f %.2f) wheels=%d+%d",
                this.seconds(), s.x(), s.y(), s.z(), s.yawDeg(), s.pitchDeg(), s.rollDeg(), this.forwardKmh(), a[0],
                a[1], a[2], this.train.tractor().wheelsOnGround(), this.train.trailer().wheelsOnGround());
    }

    @Override
    public void close() {
        this.train.close();
        this.ground.close();
        this.world.close();
    }
}
