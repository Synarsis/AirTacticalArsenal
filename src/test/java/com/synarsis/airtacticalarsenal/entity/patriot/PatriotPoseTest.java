package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot poses, signs and coordinates")
class PatriotPoseTest {

    private static final PatriotHitbox LAUNCHER = PatriotHitbox.of(PatriotKind.LAUNCHER);
    private static final PatriotHitbox RADAR = PatriotHitbox.of(PatriotKind.RADAR);
    private static final double HELD = TelPose.HELD_SECONDS;

    private static String clip(PatriotKind kind, String name) {
        return kind.clipPrefix() + name;
    }

    private static Vec3 pivotInL(PatriotHitbox model, String bone, double gx, double gy, double gz) {
        Vec3 p = model.modelPointOf(PatriotPose.REST, bone, gx, gy, gz);
        assertNotNull(p, bone);
        return p;
    }

    private static double[] extents(List<ModelHitbox.LocalBox> boxes) {
        double[] e = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (ModelHitbox.LocalBox b : boxes) {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        Vec3 p = b.centre().add(b.ax().scale(sx * b.hx())).add(b.ay().scale(sy * b.hy()))
                                .add(b.az().scale(sz * b.hz()));
                        e[0] = Math.min(e[0], p.x);
                        e[1] = Math.min(e[1], p.y);
                        e[2] = Math.min(e[2], p.z);
                        e[3] = Math.max(e[3], p.x);
                        e[4] = Math.max(e[4], p.y);
                        e[5] = Math.max(e[5], p.z);
                    }
                }
            }
        }
        return e;
    }

    private static double wheelTrack(PatriotHitbox model, double axleZ) {
        double inner = Double.MAX_VALUE;
        double outer = 0.0;
        for (ModelHitbox.LocalBox box : model.localBoxes(PatriotPose.REST)) {
            if (box.zone() != PatriotZone.WHEEL || Math.abs(box.centre().z - axleZ) > 0.2) {
                continue;
            }
            double[] e = extents(List.of(box));
            if (box.centre().x < 0.0) {
                inner = Math.min(inner, Math.abs(e[3]));
                outer = Math.max(outer, Math.abs(e[0]));
            }
        }
        return (inner + outer) * 0.5;
    }

    private static boolean contains(ModelHitbox.LocalBox b, Vec3 p) {
        Vec3 d = p.subtract(b.centre());
        return Math.abs(d.dot(b.ax())) <= b.hx() + 1.0E-6 && Math.abs(d.dot(b.ay())) <= b.hy() + 1.0E-6
                && Math.abs(d.dot(b.az())) <= b.hz() + 1.0E-6;
    }

    @Test
    @DisplayName("the points of the model land where the geometry table says, with the side the renderer draws them on")
    void selfCheck() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            Vec3 axle0 = pivotInL(model, "truck_wheel_left_0", 26, 10.2, 94);
            assertEquals(PatriotGeometry.TRACTOR_AXLE_Z[0], axle0.z, 0.01);
            assertEquals(PatriotGeometry.TRACTOR_WHEEL_CENTRE_Y, axle0.y, 0.01);
            assertEquals(PatriotGeometry.TRACTOR_TRACK, wheelTrack(model, PatriotGeometry.TRACTOR_AXLE_Z[0]), 0.02);
            assertEquals(PatriotGeometry.TRACTOR_AXLE_Z[3], pivotInL(model, "truck_wheel_left_3", 26, 10.2, 234).z, 0.01);
            assertEquals(PatriotGeometry.TRACTOR_AXLE_Z[2], pivotInL(model, "truck_wheel_left_2", 26, 10.2, 210).z, 0.01);
            assertEquals(PatriotGeometry.TRACTOR_AXLE_Z[1], pivotInL(model, "truck_wheel_left_1", 26, 10.2, 118).z, 0.01);

            Vec3 trailer0 = pivotInL(model, "wheel_left_0", 24, 9.3, -72);
            assertEquals(PatriotGeometry.TRAILER_AXLE_Z[0], trailer0.z, 0.01);
            assertEquals(PatriotGeometry.TRAILER_TRACK, wheelTrack(model, PatriotGeometry.TRAILER_AXLE_Z[0]), 0.02);
            assertEquals(PatriotGeometry.TRAILER_AXLE_Z[1], pivotInL(model, "wheel_left_1", 24, 9.3, -49).z, 0.01);

            Vec3 pivot = pivotInL(model, "root", 0, 21, 94);
            assertEquals(PatriotGeometry.ARTICULATION_PIVOT.x, pivot.x, 0.01);
            assertEquals(PatriotGeometry.ARTICULATION_PIVOT.y, pivot.y, 0.01);
            assertEquals(PatriotGeometry.ARTICULATION_PIVOT.z, pivot.z, 0.01);
        }
    }

    @Test
    @DisplayName("the kingpin cubes are at the hitch point in L")
    void kingpin() {
        Vec3 kingpin = new Vec3(0.0, 1.34, 5.875);
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            boolean found = false;
            for (ModelHitbox.LocalBox box : model.localBoxes(PatriotPose.REST)) {
                found |= contains(box, kingpin);
            }
            assertTrue(found, model.kind() + " has no box around the kingpin");
        }
    }

    @Test
    @DisplayName("the driver's seat, the steering wheel and the driver's door are on one side, and the seat table agrees")
    void driverSide() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            double door = pivotInL(model, "truck_door_left", 27.9, 42, 252.8).x;
            double doorOther = pivotInL(model, "truck_door_right", -27.9, 42, 252.8).x;
            assertTrue(door * doorOther < 0.0);
            assertEquals(Math.signum(door), Math.signum(PatriotGeometry.SEATS[0].x));
            assertEquals(Math.signum(door), Math.signum(PatriotGeometry.DISMOUNTS[0].x));
            assertEquals(Math.signum(doorOther), Math.signum(PatriotGeometry.SEATS[1].x));
            assertEquals(Math.signum(doorOther), Math.signum(PatriotGeometry.DISMOUNTS[1].x));
            assertEquals(Math.abs(PatriotGeometry.SEATS[0].x), 0.7625, 1.0E-9);
            double wheel = pivotInL(model, "truck_wheel_left_0", 26, 10.2, 94).x;
            assertEquals(Math.signum(door), Math.signum(wheel), "bones called left stand on one side");
        }
    }

    @Test
    @DisplayName("the parked vehicle is as big as the table says, and the wheels touch the ground")
    void parkedSize() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            double[] e = extents(model.localBoxes(PatriotPose.REST));
            assertEquals(0.05, e[1], 0.02, model.kind() + " lowest point");
            double height = model.kind().isLauncher() ? PatriotGeometry.HEIGHT : 4.58;
            assertEquals(height, e[4], 0.1, model.kind() + " height");
            assertTrue(e[2] > PatriotGeometry.REAR_Z - 0.05 && e[2] < PatriotGeometry.REAR_Z + 0.25,
                    model.kind() + " rear " + e[2]);
            assertEquals(PatriotGeometry.FRONT_Z, e[5], 0.1, model.kind() + " front");
            assertTrue(e[0] >= -PatriotGeometry.HALF_WIDTH_WITH_FEET - 0.05, model.kind() + " " + e[0]);
            assertTrue(e[3] <= PatriotGeometry.HALF_WIDTH_WITH_FEET + 0.05, model.kind() + " " + e[3]);
        }
    }

    @Test
    @DisplayName("the clips give the signs the code relies on: articulate_left, steer_left, wheel_cycle")
    void clipSigns() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotKind kind = model.kind();
            TelAnimSampler s = model.sampler();
            double articulate = s.bone(clip(kind, "articulate_left"), "root", HELD).rotation()[1];
            assertEquals(-12.0, articulate, 0.01);
            double steer = s.bone(clip(kind, "steer_left"), "truck_steer_left_3", HELD).rotation()[1];
            assertEquals(18.0, steer, 0.01);
            assertEquals(steer, s.bone(clip(kind, "steer_left"), "truck_steer_left_2", HELD).rotation()[1], 1.0E-9);
            assertEquals(s.bone(clip(kind, "steer_left"), "truck_steer_right_3", HELD).rotation()[1], steer, 1.0E-9);
            assertEquals(-180.0, s.bone(clip(kind, "wheel_cycle"), "wheel_left_0", 0.5).rotation()[0], 0.01);
            assertEquals(-360.0, s.bone(clip(kind, "wheel_cycle"), "wheel_right_1", 0.999999).rotation()[0], 0.1);
        }
    }

    @Test
    @DisplayName("the code-driven values produce the same bone transform as the clips they were taken from")
    void codeDrivenValuesMatchTheClips() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotKind kind = model.kind();
            TelAnimSampler s = model.sampler();

            double articulate = s.bone(clip(kind, "articulate_left"), "root", HELD).rotation()[1];
            PatriotPose articulated = PatriotPose.REST.withArticulation(articulate, 0.0, 0.0);
            assertEquals(articulate, articulated.value(kind, s, "root", -1).rotation()[1], 1.0E-9);

            double steer = s.bone(clip(kind, "steer_left"), "truck_steer_left_3", HELD).rotation()[1];
            PatriotPose steered = PatriotPose.REST.withSteer(steer, steer * 0.7);
            assertEquals(steer, steered.value(kind, s, "truck_steer_right_3", -1).rotation()[1], 1.0E-9);
            assertEquals(steer * 0.7, steered.value(kind, s, "truck_steer_left_2", -1).rotation()[1], 1.0E-9);

            double clipSpin = s.bone(clip(kind, "wheel_cycle"), "wheel_left_0", 0.25).rotation()[0];
            PatriotPose spinning = PatriotPose.REST.withWheels(new double[] {0, 0, -clipSpin, -clipSpin}, null);
            assertEquals(clipSpin, spinning.value(kind, s, "wheel_left_0", -1).rotation()[0], 1.0E-9);
            assertEquals(clipSpin, spinning.value(kind, s, "wheel_right_1", -1).rotation()[0], 1.0E-9);
            assertEquals(0.0, spinning.value(kind, s, "truck_wheel_left_0", -1).rotation()[0], 1.0E-9);
        }
    }

    @Test
    @DisplayName("a negative yaw points the trailer at the positive X side and swings its tail to the other, a positive one the reverse")
    void articulationDirection() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            Vec3 rest = model.modelPointOf(PatriotPose.REST, "wheel_left_0", 24, 9.3, -72);
            Vec3 swungLeft = model.modelPointOf(PatriotPose.REST.withArticulation(-12.0, 0.0, 0.0), "wheel_left_0",
                    24, 9.3, -72);
            Vec3 swungRight = model.modelPointOf(PatriotPose.REST.withArticulation(12.0, 0.0, 0.0), "wheel_left_0",
                    24, 9.3, -72);
            assertTrue(swungLeft.x < rest.x - 0.5, "negative yaw points the trailer at +X, so its tail swings to -X");
            assertTrue(swungRight.x > rest.x + 0.5, "positive yaw points the trailer at -X, so its tail swings to +X");
            assertEquals(rest.y, swungLeft.y, 1.0E-9);
            Vec3 pivot = model.modelPointOf(PatriotPose.REST.withArticulation(30.0, 0.0, 0.0), "root", 0, 21, 94);
            assertEquals(PatriotGeometry.ARTICULATION_PIVOT.z, pivot.z, 0.01, "the pivot itself stays");

            Vec3 noseDown = model.modelPointOf(PatriotPose.REST.withArticulation(0.0, 5.0, 0.0), "wheel_left_0", 24,
                    9.3, -72);
            assertTrue(noseDown.y > rest.y + 0.2, "a positive pitch lowers the front of the trailer, so its tail rises");
            Vec3 rolled = model.modelPointOf(PatriotPose.REST.withArticulation(0.0, 0.0, 3.0), "wheel_left_0", 24,
                    9.3, -72);
            assertTrue(Math.abs(rolled.y - rest.y) > 0.05);
        }
    }

    @Test
    @DisplayName("the tractor moves forward along L by the offset, and spins its wheels as in the detach clip")
    void tractorOffset() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotPose out = PatriotPose.REST.withTractor(PatriotGeometry.TRACTOR_OFFSET_PX, PatriotTiming.LATCH_OPEN_DEGREES);
            Vec3 before = model.modelPointOf(PatriotPose.REST, "truck_cab", 0, 44, 227);
            Vec3 after = model.modelPointOf(out, "truck_cab", 0, 44, 227);
            assertEquals(PatriotGeometry.TRACTOR_OFFSET_BLOCKS, after.z - before.z, 1.0E-6);
            assertEquals(before.x, after.x, 1.0E-9);
            Vec3 trailerBefore = model.modelPointOf(PatriotPose.REST, "wheel_left_0", 24, 9.3, -72);
            Vec3 trailerAfter = model.modelPointOf(out, "wheel_left_0", 24, 9.3, -72);
            assertEquals(0.0, trailerAfter.subtract(trailerBefore).length(), 1.0E-9);
            assertEquals(-PatriotGeometry.TRACTOR_OFFSET_WHEEL_DEGREES,
                    out.value(model.kind(), model.sampler(), "truck_wheel_left_0", -1).rotation()[0], 1.0E-6);
            TelAnimSampler s = model.sampler();
            double detachSpin = s.bone(clip(model.kind(), "detach"), "truck_wheel_left_0", HELD).rotation()[0];
            assertEquals(PatriotGeometry.TRACTOR_OFFSET_WHEEL_DEGREES, Math.abs(detachSpin), 0.5);
            double forwardSpin = s.bone(clip(model.kind(), "wheel_cycle"), "wheel_left_0", 0.5).rotation()[0];
            assertTrue(forwardSpin * out.value(model.kind(), s, "truck_wheel_left_0", -1).rotation()[0] > 0.0,
                    "driving out rolls the wheels the way wheel_cycle does for forward travel");
            double detachShift = s.bone(clip(model.kind(), "detach"), "tractor_carrier", HELD).position()[2];
            assertEquals(PatriotGeometry.TRACTOR_OFFSET_PX, detachShift, 0.01);
            double unlock = s.bone(clip(model.kind(), "coupling_unlock"), "truck_coupling_latch", HELD).rotation()[1];
            assertEquals(PatriotTiming.LATCH_OPEN_DEGREES, unlock, 0.01);
        }
    }

    @Test
    @DisplayName("every group starts a clip where the opposite clip ended: no jump at the seam")
    void seamless() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotKind kind = model.kind();
            TelAnimSampler s = model.sampler();
            for (int group = 0; group < PatriotBones.COUNT; group++) {
                if (!PatriotBones.hasGroup(kind, group)) {
                    continue;
                }
                String out = PatriotBones.clipName(kind, group, TelPose.Clip.OPENING);
                String in = PatriotBones.clipName(kind, group, TelPose.Clip.CLOSING);
                for (String bone : PatriotBones.bones(kind, group)) {
                    int channels = PatriotBones.channelsOf(bone);
                    TelAnimSampler.BoneValue end = s.bone(out, bone, HELD);
                    TelAnimSampler.BoneValue start = s.bone(in, bone, 0.0);
                    TelAnimSampler.BoneValue rest = s.bone(out, bone, 0.0);
                    String at = kind + " " + out + " " + bone;
                    if ((channels & ModelHitbox.ROTATION) != 0) {
                        assertVec(end.rotation(), start.rotation(), at + " rotation");
                        assertVec(rest.rotation(), s.bone(in, bone, HELD).rotation(), at + " rotation home");
                        assertVec(new double[3], rest.rotation(), at + " rotation at rest");
                    }
                    if ((channels & ModelHitbox.POSITION) != 0) {
                        assertVec(end.position(), start.position(), at + " position");
                        assertVec(new double[3], rest.position(), at + " position at rest");
                    }
                    if ((channels & ModelHitbox.SCALE) != 0) {
                        assertVec(end.scaleOrOne(), start.scaleOrOne(), at + " scale");
                        assertVec(new double[] {1, 1, 1}, rest.scaleOrOne(), at + " scale at rest");
                    }
                }
            }
        }
    }

    private static void assertVec(double[] expected, double[] actual, String what) {
        if (expected == null) {
            expected = new double[3];
        }
        if (actual == null) {
            actual = new double[3];
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], 1.0E-3, what + " axis " + i);
        }
    }

    @Test
    @DisplayName("a group reads only its own bones: the constants other bones carry inside a clip are ignored")
    void ownBonesOnly() {
        TelAnimSampler s = LAUNCHER.sampler();
        String raise = PatriotKind.LAUNCHER.clipPrefix() + "raise";
        PatriotPose raised = PatriotPose.REST.withParts(TelHitboxPose.Part.REST,
                new TelHitboxPose.Part(raise, HELD), TelHitboxPose.Part.REST, TelHitboxPose.Part.REST);
        assertEquals(-1, PatriotBones.groupOf(PatriotKind.LAUNCHER, "traverse"));
        assertEquals(-1, PatriotBones.groupOf(PatriotKind.LAUNCHER, "root"));
        assertEquals(-1, PatriotBones.groupOf(PatriotKind.LAUNCHER, "tractor_carrier"));
        assertEquals(0.0, raised.value(PatriotKind.LAUNCHER, s, "traverse", -1).rotation()[1], 1.0E-9);
        assertEquals(0.0, raised.value(PatriotKind.LAUNCHER, s, "root", -1).rotation()[1], 1.0E-9);
        assertEquals(-58.0, raised.value(PatriotKind.LAUNCHER, s, "elevation", PatriotBones.LIFT).rotation()[0], 0.01);
        assertEquals(2.643, raised.value(PatriotKind.LAUNCHER, s, "lift_rod_left", PatriotBones.LIFT).scaleOrOne()[1],
                0.01);
        assertEquals(ModelHitbox.POSITION, PatriotBones.channelsOf("chassis"));
    }

    @Test
    @DisplayName("raising the launcher lifts the pack, and the raised pose is taller than the parked one")
    void erectorMovesBoxes() {
        PatriotPose raised = PatriotPose.REST.withParts(TelHitboxPose.Part.REST,
                new TelHitboxPose.Part(PatriotKind.LAUNCHER.clipPrefix() + "raise", HELD), TelHitboxPose.Part.REST,
                TelHitboxPose.Part.REST);
        double parkedTop = extents(LAUNCHER.localBoxes(PatriotPose.REST))[4];
        double raisedTop = extents(LAUNCHER.localBoxes(raised))[4];
        assertTrue(raisedTop > parkedTop + 3.0, "parked " + parkedTop + " raised " + raisedTop);
        PatriotPose up = PatriotPose.REST.withParts(TelHitboxPose.Part.REST,
                new TelHitboxPose.Part(PatriotKind.RADAR.clipPrefix() + "antenna_up", HELD), TelHitboxPose.Part.REST,
                TelHitboxPose.Part.REST);
        assertTrue(extents(RADAR.localBoxes(up))[4] != extents(RADAR.localBoxes(PatriotPose.REST))[4]);
    }

    @Test
    @DisplayName("the hit boxes fit the budget, every zone is populated, and articulation and offset move boxes")
    void hitboxes() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            assertTrue(model.boxCount() > 40 && model.boxCount() <= PatriotHitbox.TARGET_BOXES,
                    model.kind() + " boxes " + model.boxCount() + " at ratio " + model.mergeRatio());
            assertTrue(model.countIn(PatriotZone.TRACTOR) > 0);
            assertTrue(model.countIn(PatriotZone.TRAILER) > 0);
            assertTrue(model.countIn(PatriotZone.WHEEL) > 0);
            assertTrue(model.countIn(PatriotZone.SUPPORT) > 0);
            assertTrue(model.countIn(model.kind().isLauncher() ? PatriotZone.PACK : PatriotZone.ANTENNA) > 0);

            List<WorldBox> parked = model.compute(PatriotPose.REST, 0, 0, 0, 0f, 0f, 0f);
            assertEquals(model.boxCount(), parked.size());
            List<WorldBox> bent = model.compute(PatriotPose.REST.withArticulation(25.0, 0.0, 0.0), 0, 0, 0, 0f, 0f,
                    0f);
            List<WorldBox> out = model.compute(PatriotPose.REST.withTractor(95.0, -60.0), 0, 0, 0, 0f, 0f, 0f);
            int movedByBend = 0;
            int movedByOffset = 0;
            int tractorMoved = 0;
            for (int i = 0; i < parked.size(); i++) {
                if (parked.get(i).centre().distanceTo(bent.get(i).centre()) > 1.0E-6) {
                    movedByBend++;
                }
                if (parked.get(i).centre().distanceTo(out.get(i).centre()) > 1.0E-6) {
                    movedByOffset++;
                    if (parked.get(i).zone() == PatriotZone.TRACTOR) {
                        tractorMoved++;
                    }
                }
            }
            assertTrue(movedByBend > 20, "bend moves the trailer: " + movedByBend);
            assertTrue(movedByOffset > 20 && tractorMoved > 10, "offset moves the tractor: " + movedByOffset);
        }
    }
}
