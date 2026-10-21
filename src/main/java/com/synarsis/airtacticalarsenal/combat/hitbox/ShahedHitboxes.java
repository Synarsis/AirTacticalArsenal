package com.synarsis.airtacticalarsenal.combat.hitbox;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ShahedHitboxes {

    private ShahedHitboxes() {
    }

    public static final List<PartBox> TEMPLATE = List.of(
            PartBox.rotated(HitPart.WARHEAD, 0.09499f, 0.04749f, 0.29090f, -0.00361f, 0.30107f, 1.34237f, 0.00000f, 0.99851f, 0.05451f, 0.00000f, 0.02877f),
            PartBox.rotated(HitPart.WARHEAD, 0.09499f, 0.04749f, 0.29090f, -0.00361f, 0.07888f, 1.34237f, 0.00000f, 0.99851f, -0.05451f, 0.00000f, 0.02877f),
            PartBox.rotated(HitPart.WARHEAD, 0.09499f, 0.04749f, 0.29090f, 0.10748f, 0.18998f, 1.34237f, 0.70606f, -0.70606f, 0.03855f, -0.03855f, 0.02877f),
            PartBox.rotated(HitPart.WARHEAD, 0.09499f, 0.04437f, 0.29090f, -0.11160f, 0.18998f, 1.34203f, 0.70606f, -0.70606f, -0.03855f, 0.03855f, 0.02688f),
            PartBox.rotated(HitPart.WARHEAD, 0.04749f, 0.04749f, 0.29327f, 0.10799f, 0.07915f, 1.33786f, 0.70711f, 0.70290f, -0.00000f, 0.07698f, 0.01450f),
            PartBox.rotated(HitPart.WARHEAD, 0.04749f, 0.04749f, 0.29327f, 0.10799f, 0.30080f, 1.33786f, 0.70711f, -0.70290f, -0.00000f, -0.07698f, 0.01450f),
            PartBox.rotated(HitPart.WARHEAD, 0.04749f, 0.04437f, 0.29327f, -0.11211f, 0.07915f, 1.33752f, 0.70711f, -0.70290f, 0.00000f, 0.07698f, 0.01355f),
            PartBox.rotated(HitPart.WARHEAD, 0.04749f, 0.04437f, 0.29327f, -0.11211f, 0.30080f, 1.33752f, 0.70711f, 0.70290f, 0.00000f, -0.07698f, 0.01355f),
            PartBox.of(HitPart.WARHEAD, 0.12344f, 0.12656f, 0.03125f, -0.00156f, 0.19000f, 1.60625f, 0.01070f),
            PartBox.of(HitPart.AVIONICS, 0.18750f, 0.19062f, 0.32125f, 0.00000f, 0.19062f, 0.74125f, 0.08000f),
            PartBox.of(HitPart.FUEL, 0.18750f, 0.19062f, 0.36000f, 0.00000f, 0.19062f, 0.06000f, 0.14000f),
            PartBox.of(HitPart.BODY, 0.18750f, 0.19062f, 0.60000f, 0.00000f, 0.19062f, -0.90000f, 0.20000f),
            PartBox.rotated(HitPart.WING_LEFT, 0.05938f, 0.10312f, 1.07187f, 0.75870f, 0.19062f, -0.31769f, 0.00000f, -0.32144f, 0.00000f, 0.94693f, 0.01667f),
            PartBox.rotated(HitPart.WING_LEFT, 0.15625f, 0.05000f, 0.52187f, 0.77188f, 0.18750f, -1.59375f, 0.00000f, -0.70711f, 0.00000f, 0.70711f, 0.01035f),
            PartBox.of(HitPart.WING_LEFT, 0.63438f, 0.09687f, 0.06445f, 0.82188f, 0.19062f, -1.25039f, 0.01006f),
            PartBox.of(HitPart.WING_LEFT, 0.63438f, 0.09687f, 0.06445f, 0.82188f, 0.19062f, -1.37930f, 0.01006f),
            PartBox.of(HitPart.WING_LEFT, 0.57254f, 0.09687f, 0.06445f, 0.76004f, 0.19062f, -1.12148f, 0.00908f),
            PartBox.of(HitPart.WING_LEFT, 0.54162f, 0.09687f, 0.06445f, 0.72912f, 0.19062f, -0.99258f, 0.00859f),
            PartBox.of(HitPart.WING_LEFT, 0.51070f, 0.09687f, 0.06445f, 0.69820f, 0.19062f, -0.86367f, 0.00810f),
            PartBox.of(HitPart.WING_LEFT, 0.44887f, 0.09687f, 0.06445f, 0.63637f, 0.19062f, -0.73477f, 0.00712f),
            PartBox.of(HitPart.WING_LEFT, 0.41795f, 0.09687f, 0.06445f, 0.60545f, 0.19062f, -0.60586f, 0.00663f),
            PartBox.of(HitPart.WING_LEFT, 0.35612f, 0.09687f, 0.06445f, 0.54362f, 0.19062f, -0.47695f, 0.00565f),
            PartBox.of(HitPart.WING_LEFT, 0.29428f, 0.09687f, 0.06445f, 0.48178f, 0.19062f, -0.34805f, 0.00467f),
            PartBox.of(HitPart.WING_LEFT, 0.26336f, 0.09687f, 0.06445f, 0.45086f, 0.19062f, -0.21914f, 0.00418f),
            PartBox.of(HitPart.WING_LEFT, 0.20153f, 0.09687f, 0.06445f, 0.38903f, 0.19062f, -0.09023f, 0.00320f),
            PartBox.of(HitPart.WING_LEFT, 0.17061f, 0.09687f, 0.06445f, 0.35811f, 0.19062f, 0.03867f, 0.00271f),
            PartBox.of(HitPart.WING_LEFT, 0.10878f, 0.09687f, 0.06445f, 0.29628f, 0.19062f, 0.16758f, 0.00172f),
            PartBox.of(HitPart.WING_LEFT, 0.07786f, 0.09687f, 0.06445f, 0.26536f, 0.19062f, 0.29648f, 0.00123f),
            PartBox.rotated(HitPart.WING_RIGHT, 0.05938f, 0.10312f, 1.06875f, -0.75055f, 0.19062f, -0.31521f, 0.00000f, 0.32144f, 0.00000f, 0.94693f, 0.01658f),
            PartBox.rotated(HitPart.WING_RIGHT, 0.15625f, 0.05000f, 0.52187f, -0.77188f, 0.18750f, -1.59375f, 0.00000f, -0.70711f, 0.00000f, 0.70711f, 0.01033f),
            PartBox.of(HitPart.WING_RIGHT, 0.63125f, 0.09687f, 0.06445f, -0.81875f, 0.19062f, -1.25039f, 0.00999f),
            PartBox.of(HitPart.WING_RIGHT, 0.63125f, 0.09687f, 0.06445f, -0.81875f, 0.19062f, -1.37930f, 0.00999f),
            PartBox.of(HitPart.WING_RIGHT, 0.60033f, 0.09687f, 0.06445f, -0.78783f, 0.19062f, -1.12148f, 0.00950f),
            PartBox.of(HitPart.WING_RIGHT, 0.53850f, 0.09687f, 0.06445f, -0.72600f, 0.19062f, -0.99258f, 0.00852f),
            PartBox.of(HitPart.WING_RIGHT, 0.50758f, 0.09687f, 0.06445f, -0.69508f, 0.19062f, -0.86367f, 0.00803f),
            PartBox.of(HitPart.WING_RIGHT, 0.44574f, 0.09687f, 0.06445f, -0.63324f, 0.19062f, -0.73477f, 0.00705f),
            PartBox.of(HitPart.WING_RIGHT, 0.41483f, 0.09687f, 0.06445f, -0.60233f, 0.19062f, -0.60586f, 0.00656f),
            PartBox.of(HitPart.WING_RIGHT, 0.35299f, 0.09687f, 0.06445f, -0.54049f, 0.19062f, -0.47695f, 0.00559f),
            PartBox.of(HitPart.WING_RIGHT, 0.32207f, 0.09687f, 0.06445f, -0.50957f, 0.19062f, -0.34805f, 0.00510f),
            PartBox.of(HitPart.WING_RIGHT, 0.26024f, 0.09687f, 0.06445f, -0.44774f, 0.19062f, -0.21914f, 0.00412f),
            PartBox.of(HitPart.WING_RIGHT, 0.19840f, 0.09687f, 0.06445f, -0.38590f, 0.19062f, -0.09023f, 0.00314f),
            PartBox.of(HitPart.WING_RIGHT, 0.16749f, 0.09687f, 0.06445f, -0.35499f, 0.19062f, 0.03867f, 0.00265f),
            PartBox.of(HitPart.WING_RIGHT, 0.10565f, 0.09687f, 0.06445f, -0.29315f, 0.19062f, 0.16758f, 0.00167f),
            PartBox.of(HitPart.WING_RIGHT, 0.07473f, 0.09687f, 0.06445f, -0.26223f, 0.19062f, 0.29648f, 0.00118f),
            PartBox.rotated(HitPart.TAIL, 0.34375f, 0.03750f, 0.31250f, -1.38125f, 0.18750f, -1.52500f, -0.50000f, -0.50000f, -0.50000f, 0.50000f, 0.01824f),
            PartBox.rotated(HitPart.TAIL, 0.34375f, 0.03750f, 0.31250f, 1.38750f, 0.18750f, -1.52500f, -0.50000f, -0.50000f, -0.50000f, 0.50000f, 0.01824f),
            PartBox.rotated(HitPart.TAIL, 0.04375f, 0.03750f, 0.11875f, 1.38750f, 0.37385f, -1.18135f, 0.57923f, 0.57923f, 0.40558f, -0.40558f, 0.00088f),
            PartBox.rotated(HitPart.TAIL, 0.04375f, 0.03750f, 0.11875f, -1.38125f, 0.37385f, -1.18135f, 0.57923f, 0.57923f, 0.40558f, -0.40558f, 0.00088f),
            PartBox.rotated(HitPart.TAIL, 0.04375f, 0.03750f, 0.11875f, 1.38750f, 0.00042f, -1.18532f, -0.41812f, -0.41812f, -0.57024f, 0.57024f, 0.00088f),
            PartBox.rotated(HitPart.TAIL, 0.04375f, 0.03750f, 0.11875f, -1.38125f, 0.00042f, -1.18532f, -0.41812f, -0.41812f, -0.57024f, 0.57024f, 0.00088f),
            PartBox.rotated(HitPart.ENGINE, 0.19375f, 0.09687f, 0.06250f, 0.00000f, 0.19062f, -1.69375f, 0.00000f, -0.70711f, 0.00000f, 0.70711f, 0.04680f),
            PartBox.of(HitPart.ENGINE, 0.05875f, 0.05625f, 0.05312f, 0.11500f, 0.19375f, -1.58188f, 0.00700f),
            PartBox.of(HitPart.ENGINE, 0.05875f, 0.05625f, 0.05312f, -0.11500f, 0.19375f, -1.58188f, 0.00700f),
            PartBox.rotated(HitPart.ENGINE, 0.05875f, 0.05625f, 0.05312f, -0.11495f, 0.19375f, -1.75078f, 0.00000f, 0.00873f, 0.00000f, 0.99996f, 0.00700f),
            PartBox.of(HitPart.ENGINE, 0.05875f, 0.05625f, 0.05312f, 0.11500f, 0.19375f, -1.75062f, 0.00700f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.15000f, 0.02000f, -0.11500f, 0.21250f, -1.58188f, 0.00299f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.15000f, 0.02000f, -0.11500f, 0.21250f, -1.75062f, 0.00299f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.15000f, 0.02000f, 0.11500f, 0.21250f, -1.58188f, 0.00299f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.15000f, 0.02000f, 0.11500f, 0.21250f, -1.75062f, 0.00299f),
            PartBox.rotated(HitPart.ENGINE, 0.06250f, 0.02813f, 0.03125f, 0.00000f, 0.31562f, -1.75000f, 0.00000f, -0.70711f, 0.00000f, 0.70711f, 0.00219f),
            PartBox.rotated(HitPart.ENGINE, 0.05813f, 0.02000f, 0.02000f, 0.04640f, 0.32406f, -1.69388f, 0.96126f, 0.00000f, 0.27564f, 0.00000f, 0.00093f),
            PartBox.rotated(HitPart.ENGINE, 0.04563f, 0.02000f, 0.02000f, 0.00000f, 0.34906f, -1.53937f, 0.00000f, -0.70711f, 0.00000f, 0.70711f, 0.00073f),
            PartBox.rotated(HitPart.ENGINE, 0.04531f, 0.02000f, 0.02000f, -0.00000f, 0.33248f, -1.62214f, -0.13795f, -0.69352f, 0.13795f, 0.69352f, 0.00072f),
            PartBox.of(HitPart.ENGINE, 0.03125f, 0.02000f, 0.02563f, -0.11500f, 0.06875f, -1.75063f, 0.00064f),
            PartBox.of(HitPart.ENGINE, 0.03125f, 0.02000f, 0.02563f, -0.11500f, 0.06875f, -1.58187f, 0.00064f),
            PartBox.of(HitPart.ENGINE, 0.03125f, 0.02000f, 0.02563f, 0.11625f, 0.06875f, -1.58187f, 0.00064f),
            PartBox.of(HitPart.ENGINE, 0.03125f, 0.02000f, 0.02563f, 0.11625f, 0.06875f, -1.75063f, 0.00064f),
            PartBox.rotated(HitPart.ENGINE, 0.03188f, 0.02000f, 0.02000f, 0.07576f, 0.32406f, -1.75699f, 0.00000f, -0.36650f, 0.00000f, 0.93042f, 0.00051f),
            PartBox.rotated(HitPart.ENGINE, 0.03031f, 0.02000f, 0.02000f, 0.02893f, 0.32406f, -1.78608f, 0.00000f, -0.16505f, 0.00000f, 0.98629f, 0.00048f),
            PartBox.rotated(HitPart.ENGINE, 0.02719f, 0.02000f, 0.02000f, -0.03711f, 0.32406f, -1.71210f, 0.00000f, 0.87882f, 0.00000f, -0.47716f, 0.00043f),
            PartBox.rotated(HitPart.ENGINE, 0.02719f, 0.02000f, 0.02000f, -0.07534f, 0.32406f, -1.75727f, 0.00000f, 0.99939f, 0.00000f, 0.03490f, 0.00043f),
            PartBox.rotated(HitPart.ENGINE, 0.02687f, 0.02000f, 0.02000f, -0.07290f, 0.32406f, -1.67732f, 0.00000f, 0.96126f, 0.00000f, -0.27564f, 0.00043f),
            PartBox.rotated(HitPart.ENGINE, 0.02625f, 0.02000f, 0.02000f, 0.00000f, 0.31732f, -1.68582f, 0.00309f, -0.70710f, -0.00309f, 0.70710f, 0.00042f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.02000f, 0.02000f, -0.11500f, 0.32812f, -1.75062f, 0.00040f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.02000f, 0.02000f, -0.11500f, 0.32812f, -1.58188f, 0.00040f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.02000f, 0.02000f, 0.11500f, 0.33437f, -1.75062f, 0.00040f),
            PartBox.of(HitPart.ENGINE, 0.02500f, 0.02000f, 0.02000f, 0.11500f, 0.33437f, -1.58188f, 0.00040f),
            PartBox.rotated(HitPart.ENGINE, 0.02406f, 0.02000f, 0.02000f, -0.03023f, 0.32406f, -1.74469f, 0.00000f, 0.97030f, 0.00000f, 0.24192f, 0.00038f),
            PartBox.rotated(HitPart.ENGINE, 0.02375f, 0.02000f, 0.02000f, 0.11664f, 0.32406f, -1.61552f, 0.78801f, 0.00000f, 0.61566f, 0.00000f, 0.00038f),
            PartBox.rotated(HitPart.ENGINE, 0.02375f, 0.02000f, 0.02000f, -0.11664f, 0.32406f, -1.61552f, 0.00000f, -0.78801f, 0.00000f, 0.61566f, 0.00038f),
            PartBox.rotated(HitPart.ENGINE, 0.02000f, 0.02000f, 0.02000f, 0.10266f, 0.32406f, -1.65058f, 0.87882f, 0.00000f, 0.47716f, 0.00000f, 0.00032f),
            PartBox.rotated(HitPart.ENGINE, 0.02000f, 0.02000f, 0.02000f, -0.10266f, 0.32406f, -1.65058f, 0.00000f, 0.87882f, 0.00000f, -0.47716f, 0.00032f),
            PartBox.rotated(HitPart.PROP, 0.23906f, 0.02000f, 0.05469f, 0.27797f, 0.19531f, -1.94880f, 0.00000f, -0.50000f, 0.86603f, 0.00000f, 0.01709f),
            PartBox.rotated(HitPart.PROP, 0.23906f, 0.02000f, 0.05156f, -0.27203f, 0.19229f, -1.96211f, 0.00000f, 0.79335f, -0.60876f, 0.00000f, 0.01612f),
            PartBox.rotated(HitPart.PROP, 0.03906f, 0.05156f, 0.05156f, 0.00015f, 0.19375f, -1.93401f, 0.00000f, 1.00000f, 0.00000f, 0.00000f, 0.00679f));

    public static final PartBox HULL =
            PartBox.of(HitPart.BODY, 1.46f, 0.32f, 1.82f, 0.004f, 0.187f, -0.178f, 1.0f);

    public static Quaternionf bodyRotation(float yawDeg, float pitchDeg, float rollDeg, Quaternionf dest) {
        return dest.identity()
                .rotateY((float) Math.toRadians(yawDeg))
                .rotateX((float) Math.toRadians(-pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
    }

    public static List<Obb> newBoxes() {
        List<Obb> boxes = new ArrayList<>(TEMPLATE.size());
        for (PartBox template : TEMPLATE) {
            boxes.add(new Obb(template.part()));
        }
        return boxes;
    }

    public static void place(List<Obb> boxes, double x, double y, double z,
                             float yawDeg, float pitchDeg, float rollDeg) {
        Quaternionf rot = bodyRotation(yawDeg, pitchDeg, rollDeg, new Quaternionf());
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).place(TEMPLATE.get(i), x, y, z, rot);
        }
    }

    public static AABB hullBox(double x, double y, double z, float yawDeg, float pitchDeg, float rollDeg) {
        Matrix3f m = bodyRotation(yawDeg, pitchDeg, rollDeg, new Quaternionf()).get(new Matrix3f());

        Vector3f c = m.transform(HULL.center().x(), HULL.center().y(), HULL.center().z(), new Vector3f());
        float hx = HULL.halfExtents().x();
        float hy = HULL.halfExtents().y();
        float hz = HULL.halfExtents().z();

        double ex = Math.abs(m.m00()) * hx + Math.abs(m.m10()) * hy + Math.abs(m.m20()) * hz;
        double ey = Math.abs(m.m01()) * hx + Math.abs(m.m11()) * hy + Math.abs(m.m21()) * hz;
        double ez = Math.abs(m.m02()) * hx + Math.abs(m.m12()) * hy + Math.abs(m.m22()) * hz;

        return new AABB(x + c.x - ex, y + c.y - ey, z + c.z - ez,
                x + c.x + ex, y + c.y + ey, z + c.z + ez);
    }
}
