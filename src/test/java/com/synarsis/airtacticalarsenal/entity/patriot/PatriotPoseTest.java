package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Patriot trailer poses, signs and coordinates")
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
    @DisplayName("the points of the trailer model land where the geometry table says")
    void selfCheck() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            Vec3 trailer0 = pivotInL(model, "wheel_left_0", 24, 9.3, -72);
            assertEquals(PatriotGeometry.TRAILER_AXLE_Z[0], trailer0.z, 0.01);
            assertEquals(PatriotGeometry.TRAILER_WHEEL_CENTRE_Y, trailer0.y, 0.01);
            assertEquals(PatriotGeometry.TRAILER_TRACK, wheelTrack(model, PatriotGeometry.TRAILER_AXLE_Z[0]), 0.02);
            assertEquals(PatriotGeometry.TRAILER_AXLE_Z[1], pivotInL(model, "wheel_left_1", 24, 9.3, -49).z, 0.01);

            Vec3 origin = pivotInL(model, "root", 0, 0, 0);
            assertEquals(0.0, origin.x, 0.01);
            assertEquals(0.0, origin.y, 0.01);
            assertEquals(0.0, origin.z, 0.01);
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
    @DisplayName("the trailer has no tractor bones or tractor hit boxes")
    void trailerOnly() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            assertFalse(model.hasBone("truck_chassis"));
            assertFalse(model.hasBone("tractor_carrier"));
        }
    }

    @Test
    @DisplayName("the parked trailer is as big as the table says, and the wheels touch the ground")
    void parkedSize() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            double[] e = extents(model.localBoxes(PatriotPose.REST));
            assertEquals(0.05, e[1], 0.02, model.kind() + " lowest point");
            double height = model.kind().isLauncher() ? PatriotGeometry.HEIGHT : 4.48;
            assertEquals(height, e[4], 0.1, model.kind() + " height");
            double rear = model.kind().isLauncher() ? PatriotGeometry.REAR_Z : -6.2812;
            assertTrue(e[2] > rear - 0.05 && e[2] < rear + 0.25, model.kind() + " rear " + e[2]);
            assertEquals(6.21875, e[5], 0.1, model.kind() + " front");
            assertTrue(e[0] >= -PatriotGeometry.HALF_WIDTH_WITH_FEET - 0.05, model.kind() + " " + e[0]);
            assertTrue(e[3] <= PatriotGeometry.HALF_WIDTH_WITH_FEET + 0.05, model.kind() + " " + e[3]);
        }
    }

    @Test
    @DisplayName("the wheel cycle gives the sign the code relies on")
    void clipSigns() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotKind kind = model.kind();
            TelAnimSampler s = model.sampler();
            assertEquals(-180.0, s.bone(clip(kind, "wheel_cycle"), "wheel_left_0", 0.5).rotation()[0], 0.01);
            assertEquals(-360.0, s.bone(clip(kind, "wheel_cycle"), "wheel_right_1", 0.999999).rotation()[0], 0.1);
        }
    }

    @Test
    @DisplayName("the code-driven wheel spin matches the clip")
    void codeDrivenValuesMatchTheClips() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            PatriotKind kind = model.kind();
            TelAnimSampler s = model.sampler();
            double clipSpin = s.bone(clip(kind, "wheel_cycle"), "wheel_left_0", 0.25).rotation()[0];
            PatriotPose spinning = PatriotPose.REST.withWheels(-clipSpin, -clipSpin);
            assertEquals(clipSpin, spinning.value(kind, s, "wheel_left_0", -1).rotation()[0], 1.0E-9);
            assertEquals(clipSpin, spinning.value(kind, s, "wheel_right_1", -1).rotation()[0], 1.0E-9);
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
                new TelHitboxPose.Part(raise, HELD), TelHitboxPose.Part.REST);
        assertEquals(-1, PatriotBones.groupOf(PatriotKind.LAUNCHER, "traverse"));
        assertEquals(-1, PatriotBones.groupOf(PatriotKind.LAUNCHER, "root"));
        assertEquals(0.0, raised.value(PatriotKind.LAUNCHER, s, "traverse", -1).rotation()[1], 1.0E-9);
        assertNull(raised.value(PatriotKind.LAUNCHER, s, "root", -1));
        assertEquals(-58.0, raised.value(PatriotKind.LAUNCHER, s, "elevation", PatriotBones.LIFT).rotation()[0], 0.01);
        assertEquals(2.643, raised.value(PatriotKind.LAUNCHER, s, "lift_rod_left", PatriotBones.LIFT).scaleOrOne()[1],
                0.01);
        assertEquals(ModelHitbox.POSITION, PatriotBones.channelsOf("chassis"));
    }

    @Test
    @DisplayName("raising the launcher lifts the pack, and the raised pose is taller than the parked one")
    void erectorMovesBoxes() {
        PatriotPose raised = PatriotPose.REST.withParts(TelHitboxPose.Part.REST,
                new TelHitboxPose.Part(PatriotKind.LAUNCHER.clipPrefix() + "raise", HELD), TelHitboxPose.Part.REST);
        double parkedTop = extents(LAUNCHER.localBoxes(PatriotPose.REST))[4];
        double raisedTop = extents(LAUNCHER.localBoxes(raised))[4];
        assertTrue(raisedTop > parkedTop + 3.0, "parked " + parkedTop + " raised " + raisedTop);
        PatriotPose up = PatriotPose.REST.withParts(TelHitboxPose.Part.REST,
                new TelHitboxPose.Part(PatriotKind.RADAR.clipPrefix() + "antenna_up", HELD), TelHitboxPose.Part.REST);
        assertTrue(extents(RADAR.localBoxes(up))[4] != extents(RADAR.localBoxes(PatriotPose.REST))[4]);
    }

    @Test
    @DisplayName("the hit boxes fit the budget, every trailer zone is populated, and the lift moves boxes")
    void hitboxes() {
        for (PatriotHitbox model : new PatriotHitbox[] {LAUNCHER, RADAR}) {
            assertTrue(model.boxCount() > 40 && model.boxCount() <= PatriotHitbox.TARGET_BOXES,
                    model.kind() + " boxes " + model.boxCount() + " at ratio " + model.mergeRatio());
            assertTrue(model.countIn(PatriotZone.TRAILER) > 0);
            assertTrue(model.countIn(PatriotZone.WHEEL) > 0);
            assertTrue(model.countIn(PatriotZone.SUPPORT) > 0);
            assertTrue(model.countIn(model.kind().isLauncher() ? PatriotZone.PACK : PatriotZone.ANTENNA) > 0);

            List<com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox> parked =
                    model.compute(PatriotPose.REST, 0, 0, 0, 0f, 0f, 0f);
            assertEquals(model.boxCount(), parked.size());
        }
    }
}
