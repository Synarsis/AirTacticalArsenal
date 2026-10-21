package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyLockRead;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bounds of the ground body after its heights are changed in place")
class GroundBoundsTest {

    private static final class Flat implements BlockColumns {
        final Map<Long, Double> raised = new HashMap<>();

        void raise(int x, int z, double top) {
            this.raised.put(((long) x << 32) | (z & 0xFFFFFFFFL), top);
        }

        @Override
        public double solidTop(double x, double z, double low, double high) {
            Double top = this.raised.get(((long) Math.floor(x) << 32) | ((long) Math.floor(z) & 0xFFFFFFFFL));
            double value = top == null ? 64.0 : top;
            return value >= low && value <= high ? value : Double.NaN;
        }
    }

    private static double bodyTop(PhysicsWorld world, int bodyId) {
        BodyLockRead lock = new BodyLockRead(world.system().getBodyLockInterface(), bodyId);
        try {
            return lock.getBody().getWorldSpaceBounds().getMax().getY();
        } finally {
            lock.releaseLock();
            lock.close();
        }
    }

    private static int flyBox(PhysicsWorld world, double x, double y, double z, double vx, double vz) {
        BoxShape shape = new BoxShape(new Vec3(1f, 0.5f, 1f));
        BodyCreationSettings settings = new BodyCreationSettings(shape, new RVec3(x, y, z), new Quat(0f, 0f, 0f, 1f),
                EMotionType.Dynamic, PhysicsLayers.vehicle(0));
        settings.setGravityFactor(0.0f);
        settings.setLinearVelocity(new Vec3((float) vx, 0f, (float) vz));
        settings.setFriction(0.0f);
        Body body = world.bodies().createBody(settings);
        world.bodies().addBody(body.getId(), EActivation.Activate);
        settings.close();
        shape.close();
        return body.getId();
    }

    @Test
    @DisplayName("a wall raised in place above everything before is seen by the box of the body and stops a flying box")
    void wallRaisedInPlace() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        Flat blocks = new Flat();
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6); VehicleGround ground = new VehicleGround(world, 0, blocks)) {
            ground.update(100.0, 64.0, 100.0);
            assertEquals(1, ground.fullRebuilds());
            int bodyId = world.system().getBodyLockInterface() == null ? 0 : firstGroundBody(world);
            double before = bodyTop(world, bodyId);

            for (int x = 100; x < 103; x++) {
                blocks.raise(x, 110, 66.0);
                assertTrue(ground.markChanged(x, 65, 110));
            }
            assertTrue(ground.update(100.0, 64.0, 100.0));
            assertEquals(1, ground.fullRebuilds());

            double after = bodyTop(world, bodyId);
            double tree = world.system().getBounds().getMax().getY();
            VehicleRig.report(String.format(Locale.ROOT, "BOUNDS body top before %.3f, after %.3f, broadphase top %.3f", before, after, tree));
            assertTrue(after >= 65.9, "body box top " + after);
            assertTrue(tree >= 65.9, "broadphase top " + tree);

            int box = flyBox(world, 101.5, 65.0, 104.0, 0.0, 10.0);
            double deepest = 0.0;
            for (int i = 0; i < 60; i++) {
                world.step();
                deepest = Math.max(deepest, world.bodies().getPosition(box).zz());
            }
            VehicleRig.report(String.format(Locale.ROOT, "BOUNDS box centre went to z=%.2f against a wall at z=110", deepest));
            assertTrue(deepest < 110.0, "the box passed into the wall, z=" + deepest);
        }
    }

    @Test
    @DisplayName("a wall that appears only in the new strip after the patch moves is solid as well")
    void wallInNewStrip() {
        assertTrue(JoltRuntime.load(Path.of("build", "test-natives")));
        Flat blocks = new Flat();
        try (PhysicsWorld world = new PhysicsWorld(false, 1.6); VehicleGround ground = new VehicleGround(world, 0, blocks)) {
            ground.update(100.0, 64.0, 100.0);
            int bodyId = firstGroundBody(world);
            for (int z = 108; z < 112; z++) {
                blocks.raise(138, z, 67.0);
            }
            assertTrue(ground.update(109.0, 64.0, 100.0));
            assertEquals(1, ground.fullRebuilds());
            double top = bodyTop(world, bodyId);
            VehicleRig.report(String.format(Locale.ROOT, "BOUNDS body top after the move %.3f", top));
            assertTrue(top >= 66.9, "body box top " + top);

            int box = flyBox(world, 133.0, 65.5, 109.5, 10.0, 0.0);
            double deepest = 0.0;
            for (int i = 0; i < 60; i++) {
                world.step();
                deepest = Math.max(deepest, world.bodies().getPosition(box).xx());
            }
            assertTrue(deepest < 138.0, "the box passed into the wall, x=" + deepest);
        }
    }

    private static int firstGroundBody(PhysicsWorld world) {
        com.github.stephengold.joltjni.BodyIdVector ids = new com.github.stephengold.joltjni.BodyIdVector();
        world.system().getBodies(ids);
        int id = ids.get(0);
        ids.close();
        return id;
    }
}
