package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pose of the vehicle and the pose of its Jolt body")
class PoseMathTest {

    @Test
    @DisplayName("a point of the vehicle lands where the model transform puts it")
    void matchesModelTransform() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            double x = random.nextDouble() * 400 - 200;
            double y = random.nextDouble() * 100;
            double z = random.nextDouble() * 400 - 200;
            float yaw = random.nextFloat() * 360 - 180;
            float pitch = random.nextFloat() * 60 - 30;
            float roll = random.nextFloat() * 60 - 30;
            Vec3 local = new Vec3(random.nextDouble() * 4 - 2, random.nextDouble() * 4, random.nextDouble() * 14 - 7);
            Vec3 expected = TelGeometry.localToWorld(x, y, z, yaw, pitch, roll, local);

            double[] origin = PoseMath.bodyOrigin(x, y, z, yaw, pitch, roll);
            double[][] r = PoseMath.rotation(yaw, pitch, roll);
            double[] actual = PoseMath.toWorld(origin, r, local.x, local.y, local.z);
            assertEquals(expected.x, actual[0], 1.0e-5);
            assertEquals(expected.y, actual[1], 1.0e-5);
            assertEquals(expected.z, actual[2], 1.0e-5);
        }
    }

    @Test
    @DisplayName("pose to body to pose gives the same numbers")
    void roundTrip() {
        Random random = new Random(11);
        for (int i = 0; i < 200; i++) {
            double x = random.nextDouble() * 400 - 200;
            double y = random.nextDouble() * 100;
            double z = random.nextDouble() * 400 - 200;
            double yaw = random.nextDouble() * 360 - 180;
            double pitch = random.nextDouble() * 70 - 35;
            double roll = random.nextDouble() * 70 - 35;
            double[] origin = PoseMath.bodyOrigin(x, y, z, yaw, pitch, roll);
            double[] q = PoseMath.quaternion(yaw, pitch, roll);
            PoseMath.Pose back = PoseMath.entityPose(origin[0], origin[1], origin[2], q[0], q[1], q[2], q[3]);
            assertEquals(x, back.x(), 1.0e-6);
            assertEquals(y, back.y(), 1.0e-6);
            assertEquals(z, back.z(), 1.0e-6);
            assertEquals(0.0, PoseMath.wrapDegrees(yaw - back.yawDeg()), 1.0e-6);
            assertEquals(pitch, back.pitchDeg(), 1.0e-6);
            assertEquals(roll, back.rollDeg(), 1.0e-6);
        }
    }

    @Test
    @DisplayName("with no tilt the body origin is the vehicle position")
    void levelPose() {
        double[] origin = PoseMath.bodyOrigin(10.0, 64.0, -3.0, 90.0, 0.0, 0.0);
        assertEquals(10.0, origin[0], 1.0e-9);
        assertEquals(64.0, origin[1], 1.0e-9);
        assertEquals(-3.0, origin[2], 1.0e-9);
    }

    @Test
    @DisplayName("the front of the vehicle looks the way the entity looks")
    void facing() {
        double[][] r = PoseMath.rotation(90.0, 0.0, 0.0);
        assertEquals(-1.0, r[0][2], 1.0e-9);
        assertEquals(0.0, r[2][2], 1.0e-9);
    }

    @Test
    @DisplayName("units")
    void units() {
        assertEquals(16.6666667, Units.kmhToMs(60.0), 1.0e-6);
        assertEquals(60.0, Units.msToKmh(Units.kmhToMs(60.0)), 1.0e-9);
        assertEquals(0.5, Units.msToBlocksPerTick(10.0), 1.0e-9);
        assertEquals(10.0, Units.blocksPerTickToMs(0.5), 1.0e-9);
        assertEquals(2 * Math.PI, Units.rpmToRadPerSecond(60.0), 1.0e-9);
        assertEquals(600.0, Units.radPerSecondToRpm(Units.rpmToRadPerSecond(600.0)), 1.0e-9);
    }

    @Test
    @DisplayName("an articulation turns into a matrix and back")
    void articulationRoundTrip() {
        double[][] cases = {{0, 0, 0}, {30, 0, 0}, {-45, 5, 2}, {70, -10, 3}, {-20, 15, -4}};
        for (double[] c : cases) {
            double[] back = PoseMath.articulationAngles(PoseMath.articulation(c[0], c[1], c[2]));
            assertEquals(c[0], back[0], 1.0e-6);
            assertEquals(c[1], back[1], 1.0e-6);
            assertEquals(c[2], back[2], 1.0e-6);
        }
    }

    @Test
    @DisplayName("a trailer yawed in the entity's positive direction has a positive articulation, and it looks to the right")
    void articulationSign() {
        double[][] tractor = PoseMath.rotation(0.0, 0.0, 0.0);
        double[][] trailer = PoseMath.rotation(20.0, 0.0, 0.0);
        double[] a = PoseMath.articulationAngles(PoseMath.multiply(PoseMath.transpose(tractor), trailer));
        assertEquals(20.0, a[0], 1.0e-6);
        double[][] m = PoseMath.articulation(20.0, 0.0, 0.0);
        assertTrue(m[0][2] < 0.0, "a positive articulation turns the trailer's front toward minus X");
        assertEquals(trailer[0][2], m[0][2], 1.0e-9);
    }

    @Test
    @DisplayName("a matrix turns into a quaternion and back")
    void matrixQuaternion() {
        double[][] r = PoseMath.rotation(33.0, -8.0, 4.0);
        double[] q = PoseMath.quaternion(r);
        double[][] back = PoseMath.rotation(q[0], q[1], q[2], q[3]);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(r[i][j], back[i][j], 1.0e-9);
            }
        }
    }
}
