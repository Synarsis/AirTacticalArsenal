package com.synarsis.airtacticalarsenal.vehicle.physics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class VehicleRig implements AutoCloseable {

    interface Surface {
        double top(int x, int z);
    }

    interface Smooth {
        double height(double x, double z);
    }

    static final Surface FLAT = (x, z) -> 64.0;

    final PhysicsWorld world;
    final JoltVehicle vehicle;
    private final Surface surface;
    private Smooth smooth;
    private GroundBody ground;
    private AutoCloseable customGround;
    private int ticks;
    private double minGroundGap = Double.MAX_VALUE;

    VehicleRig(Surface surface, double x, double z, double yawDeg) {
        this(surface, VehicleSpec.defaults(), x, z, yawDeg);
    }

    VehicleRig(Surface surface, VehicleSpec spec, double x, double z, double yawDeg) {
        if (!JoltRuntime.load(Path.of("build", "test-natives"))) {
            throw new IllegalStateException("Jolt native library did not load");
        }
        this.surface = surface;
        this.world = new PhysicsWorld(false, 1.6);
        double startY = surface.top((int) Math.floor(x), (int) Math.floor(z));
        this.rebuildGround(x, startY, z);
        this.vehicle = new JoltVehicle(this.world, spec, x, startY + 0.05, z, yawDeg, 0.0, 0.0);
        this.vehicle.setDriven(true);
    }

    VehicleRig(Smooth smooth, VehicleSpec spec, double x, double z, double yawDeg) {
        if (!JoltRuntime.load(Path.of("build", "test-natives"))) {
            throw new IllegalStateException("Jolt native library did not load");
        }
        this.surface = null;
        this.smooth = smooth;
        this.world = new PhysicsWorld(false, 1.6);
        double startY = smooth.height(x, z);
        this.rebuildGround(x, startY, z);
        this.vehicle = new JoltVehicle(this.world, spec, x, startY + 0.05, z, yawDeg, 0.0, 0.0);
        this.vehicle.setDriven(true);
    }

    VehicleRig(java.util.function.Function<PhysicsWorld, AutoCloseable> groundFactory, VehicleSpec spec, double x,
            double y, double z, double yawDeg) {
        if (!JoltRuntime.load(Path.of("build", "test-natives"))) {
            throw new IllegalStateException("Jolt native library did not load");
        }
        this.surface = null;
        this.world = new PhysicsWorld(false, 1.6);
        this.customGround = groundFactory.apply(this.world);
        this.vehicle = new JoltVehicle(this.world, spec, x, y, z, yawDeg, 0.0, 0.0);
        this.vehicle.setDriven(true);
    }

    private void rebuildGround(double x, double y, double z) {
        if (this.smooth != null) {
            int n = GroundPatchBuilder.NODES;
            double ox = Math.floor((x - 0.5 * n * GroundPatchBuilder.STEP) / GroundPatchBuilder.STEP) * GroundPatchBuilder.STEP + 0.25;
            double oz = Math.floor((z - 0.5 * n * GroundPatchBuilder.STEP) / GroundPatchBuilder.STEP) * GroundPatchBuilder.STEP + 0.25;
            float[] heights = new float[n * n];
            for (int iz = 0; iz < n; iz++) {
                for (int ix = 0; ix < n; ix++) {
                    heights[iz * n + ix] = (float) this.smooth.height(ox + ix * GroundPatchBuilder.STEP, oz + iz * GroundPatchBuilder.STEP);
                }
            }
            GroundBody swapped = new GroundBody(this.world, this.vehicleSlot(), new GroundPatch(ox, oz, y, n, GroundPatchBuilder.STEP, heights));
            GroundBody previous = this.ground;
            this.ground = swapped;
            if (previous != null) {
                previous.close();
            }
            return;
        }
        BlockColumns columns = (px, pz, low, high) -> {
            double top = this.surface.top((int) Math.floor(px), (int) Math.floor(pz));
            return top >= low && top <= high ? top : Double.NaN;
        };
        GroundPatch patch = GroundPatchBuilder.build(columns, x, y, z);
        GroundBody fresh = new GroundBody(this.world, this.vehicleSlot(), patch);
        GroundBody old = this.ground;
        this.ground = fresh;
        if (old != null) {
            old.close();
        }
    }

    private int vehicleSlot() {
        return this.vehicle == null ? 0 : this.vehicle.slot();
    }

    void tick() {
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        if (this.ground != null && GroundPatchBuilder.needsRebuild(this.ground.patch(), s.x(), s.y(), s.z(), false)) {
            this.rebuildGround(s.x(), s.y(), s.z());
        }
        this.world.step();
        this.ticks++;
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            this.tick();
        }
    }

    int ticks() {
        return this.ticks;
    }

    double seconds() {
        return this.ticks / 20.0;
    }

    double forwardKmh() {
        return Units.msToKmh(this.vehicle.forwardSpeed());
    }

    void drive(double throttle, double steer, double brake, double handbrake) {
        this.vehicle.setControls(new JoltVehicle.Controls(throttle, steer, brake, handbrake));
    }

    String telemetry() {
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        return String.format(java.util.Locale.ROOT,
                "t=%5.2fs pos=(%.2f %.2f %.2f) yaw=%.1f pitch=%.2f roll=%.2f v=%.1fkm/h rpm=%.0f gear=%d wheels=%d",
                this.seconds(), s.x(), s.y(), s.z(), s.yawDeg(), s.pitchDeg(), s.rollDeg(),
                this.forwardKmh(), this.vehicle.rpm(), this.vehicle.gear(), this.vehicle.wheelsOnGround());
    }

    static void report(String line) {
        try {
            Path file = Path.of("build", "jolt-report.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        this.vehicle.close();
        if (this.ground != null) {
            this.ground.close();
        }
        if (this.customGround != null) {
            try {
                this.customGround.close();
            } catch (Exception ignored) {
            }
        }
        this.world.close();
    }
}
