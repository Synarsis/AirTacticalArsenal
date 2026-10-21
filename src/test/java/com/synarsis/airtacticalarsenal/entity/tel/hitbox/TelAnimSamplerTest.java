package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.animation.keyframe.KeyframeStack;
import software.bernie.geckolib.loading.json.typeadapter.BakedAnimationsAdapter;
import software.bernie.geckolib.loading.math.MathValue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Animation sampler")
class TelAnimSamplerTest {

    private static final String PREFIX = "animation.iskander_tel_loaded.";
    private static final TelModelHitbox MODEL = TelModelHitbox.get();
    private static final double HELD = 1000.0;
    private static final String[] CLIPS = {"cover_open", "cover_close", "raise_left", "lower_left", "raise_right",
            "lower_right", "deploy_left", "stow_right", "idle"};

    private static BoneAnimation[] geckoLibBones(JsonObject clip) throws ReflectiveOperationException {
        Method bake = BakedAnimationsAdapter.class.getDeclaredMethod("bakeBoneAnimations", JsonObject.class);
        bake.setAccessible(true);
        return (BoneAnimation[]) bake.invoke(new BakedAnimationsAdapter(), clip.getAsJsonObject("bones"));
    }

    private static JsonObject animationFile() throws IOException {
        try (InputStream in = TelAnimSamplerTest.class.getResourceAsStream(TelModelHitbox.ANIMATIONS);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
        }
    }

    private static double axis(List<Keyframe<MathValue>> frames, double seconds, double fallback) {
        if (frames.isEmpty()) {
            return fallback;
        }
        double ticks = seconds * 20.0;
        double elapsed = 0.0;
        for (Keyframe<MathValue> frame : frames) {
            double length = frame.length();
            if (ticks <= elapsed + length || frame == frames.get(frames.size() - 1)) {
                double progress = length > 1.0E-9 ? Math.max(0.0, Math.min(1.0, (ticks - elapsed) / length)) : 1.0;
                double eased = frame.easingType().buildTransformer(null).get(progress);
                double from = frame.startValue().get();
                double to = frame.endValue().get();
                return from + (to - from) * eased;
            }
            elapsed += length;
        }
        return fallback;
    }

    private static double[] vec(KeyframeStack<Keyframe<MathValue>> stack, double seconds, double fallback) {
        return new double[] {axis(stack.xKeyframes(), seconds, fallback), axis(stack.yKeyframes(), seconds, fallback),
                axis(stack.zKeyframes(), seconds, fallback)};
    }

    private static void assertVec(double[] expected, double[] actual, double tolerance, String what) {
        for (int i = 0; i < 3; i++) {
            assertEquals(expected[i], actual[i], tolerance, what + " axis " + i);
        }
    }

    @Test
    @DisplayName("the sampler gives what GeckoLib itself reads from the file: rotation, position and scale, with their signs")
    void matchesGeckoLib() throws IOException, ReflectiveOperationException {
        JsonObject file = animationFile();
        TelAnimSampler sampler = MODEL.sampler();
        int compared = 0;
        for (String name : CLIPS) {
            JsonObject json = file.getAsJsonObject(PREFIX + name);
            assertNotNull(json, name);
            double length = sampler.length(PREFIX + name);
            assertEquals(json.get("animation_length").getAsDouble(), length, 1.0E-3, "length of " + name);
            for (BoneAnimation bone : geckoLibBones(json)) {
                for (int k = 0; k <= 12; k++) {
                    double t = length * k / 12.0;
                    TelAnimSampler.BoneValue mine = sampler.bone(PREFIX + name, bone.boneName(), t);
                    assertNotNull(mine, name + " " + bone.boneName());
                    String at = name + " " + bone.boneName() + " at " + t;
                    assertVec(vec(bone.rotationKeyFrames(), t, 0.0), mine.geckoRotation(), 1.0E-4, "rotation of " + at);
                    assertVec(vec(bone.positionKeyFrames(), t, 0.0), mine.geckoPosition(), 1.0E-4, "position of " + at);
                    assertVec(vec(bone.scaleKeyFrames(), t, 1.0), mine.scaleOrOne(), 1.0E-4, "scale of " + at);
                    compared++;
                }
            }
        }
        assertTrue(compared > 300, "compared " + compared);
    }

    @Test
    @DisplayName("the cylinder bones carry a scale channel and the sleeve a turn, in every lifting clip")
    void cylinderChannels() {
        TelAnimSampler sampler = MODEL.sampler();
        var rest = sampler.bone(PREFIX + "raise_left", "lift_rod6_left", 0.0);
        var end = sampler.bone(PREFIX + "raise_left", "lift_rod6_left", 4.8);
        assertEquals(1.0, rest.scaleOrOne()[1], 1.0E-6);
        assertEquals(4.18488, end.scaleOrOne()[1], 1.0E-4);
        assertEquals(1.0, end.scaleOrOne()[0], 1.0E-6);
        assertEquals(108.92279, sampler.bone(PREFIX + "raise_left", "lift_sleeve6_left", 4.8).rotation()[0], 1.0E-4);
        assertEquals(3.5, sampler.bone(PREFIX + "raise_left", "carrier_shoe6_left", 4.8).position()[1], 1.0E-4);
        assertEquals(1.0, sampler.bone(PREFIX + "raise_left", "lift_rod6_right", 4.8).scaleOrOne()[1], 1.0E-9);
        assertEquals(4.18488, sampler.bone(PREFIX + "lower_left", "lift_rod6_left", 0.0).scaleOrOne()[1], 1.0E-4);
        assertEquals(1.0, sampler.bone(PREFIX + "lower_left", "lift_rod6_left", 4.8).scaleOrOne()[1], 1.0E-4);
    }

    @Test
    @DisplayName("a bone without a scale channel is not scaled, and a bone the clip leaves alone has no value")
    void absentChannels() {
        TelAnimSampler sampler = MODEL.sampler();
        var cover = sampler.bone(PREFIX + "cover_open", "cover_left", 1.0);
        assertNull(cover.scale());
        assertEquals(1.0, cover.scaleOrOne()[0], 0.0);
        assertEquals(1.0, cover.scaleOrOne()[1], 0.0);
        assertEquals(1.0, cover.scaleOrOne()[2], 0.0);
        assertNull(sampler.bone(PREFIX + "cover_open", "erector_left", 1.0));
        assertNull(sampler.bone(PREFIX + "no_such_clip", "cover_left", 1.0));
    }

    @Test
    @DisplayName("the top of a cylinder rod stays on the eye of its erector, at rest, at the end of the lift and between")
    void rodMeetsTheEye() {
        for (boolean left : new boolean[] {true, false}) {
            String side = left ? "left" : "right";
            double x = left ? 10.5 : -10.5;
            String clip = PREFIX + "raise_" + side;
            String rod = "lift_rod6_" + side;
            String erector = "erector_" + side;

            for (int k = 0; k <= 12; k++) {
                double t = k == 12 ? HELD : 4.8 * k / 11.0;
                TelHitboxPose pose = k == 0 ? TelHitboxPose.PARKED : liftPose(left, clip, t);
                Vec3 rodTop = MODEL.modelPointOf(pose, rod, x, 59.2, -61.0);
                Vec3 eye = MODEL.modelPointOf(pose, erector, x, 26.3, -28.0);
                assertNotNull(rodTop);
                assertNotNull(eye);
                assertTrue(rodTop.distanceTo(eye) < 0.1,
                        side + " at " + t + " s: rod top " + rodTop + " eye " + eye + " apart "
                                + rodTop.distanceTo(eye));
            }
        }
    }

    private static TelHitboxPose liftPose(boolean left, String clip, double seconds) {
        TelHitboxPose.Part up = new TelHitboxPose.Part(clip, seconds);
        return left
                ? new TelHitboxPose(TelHitboxPose.Part.REST, up, TelHitboxPose.Part.REST, 1.0, true, true)
                : new TelHitboxPose(TelHitboxPose.Part.REST, TelHitboxPose.Part.REST, up, 1.0, true, true);
    }

    @Test
    @DisplayName("the rod really does lengthen: its top rises with the erector, a long way from where it started")
    void rodTopMoves() {
        Vec3 rest = MODEL.modelPointOf(TelHitboxPose.PARKED, "lift_rod6_left", 10.5, 59.2, -61.0);
        TelHitboxPose up = liftPose(true, PREFIX + "raise_left", HELD);
        Vec3 raised = MODEL.modelPointOf(up, "lift_rod6_left", 10.5, 59.2, -61.0);
        assertTrue(raised.y - rest.y > 1.0, "rose " + (raised.y - rest.y));
    }

    @Test
    @DisplayName("without the scale, the rod top would miss the eye: the scale is what closes the gap")
    void scaleMatters() {
        TelHitboxPose up = liftPose(true, PREFIX + "raise_left", HELD);
        Vec3 eye = MODEL.modelPointOf(up, "erector_left", 10.5, 26.3, -28.0);
        Vec3 pivot = MODEL.modelPointOf(up, "lift_rod6_left", 10.5, 47.2, -61.0);
        Vec3 top = MODEL.modelPointOf(up, "lift_rod6_left", 10.5, 59.2, -61.0);
        double scaled = pivot.distanceTo(top);
        double unscaled = (59.2 - 47.2) / 16.0;
        assertTrue(scaled > unscaled * 3.0, "scaled " + scaled + " unscaled " + unscaled);
        assertTrue(top.distanceTo(eye) < 0.1);
    }
}
