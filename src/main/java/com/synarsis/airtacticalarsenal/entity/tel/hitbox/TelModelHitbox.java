package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TelModelHitbox {

    private static final Logger LOGGER = LoggerFactory.getLogger("ATA-TelHitbox");

    public static final String GEO = "/assets/ata/geo/iskander_tel_loaded.geo.json";
    public static final String ANIMATIONS = "/assets/ata/animations/iskander_tel_loaded.animation.json";

    public static final int TARGET_BOXES = 250;
    private static final double MIN_SIDE_PX = 0.5;
    private static final double MIN_VOLUME_PX3 = 4.0;
    private static final double SOLID_SIDE = 0.25;

    private static volatile TelModelHitbox instance;

    private record Bone(String name, int parent, double[] pivot, double[] baseRot, int group) {
    }

    private record Box(int bone, Zone zone, double[] centre, double[] half, double[] pivot, Matrix3d rot,
            boolean solid) {
    }

    private final Bone[] bones;
    private final Box[] boxes;
    private final TelAnimSampler sampler;
    private final int cubeCount;
    private final double mergeRatio;

    private TelModelHitbox(Bone[] bones, Box[] boxes, TelAnimSampler sampler, int cubeCount, double mergeRatio) {
        this.bones = bones;
        this.boxes = boxes;
        this.sampler = sampler;
        this.cubeCount = cubeCount;
        this.mergeRatio = mergeRatio;
    }

    public static TelModelHitbox get() {
        TelModelHitbox local = instance;
        if (local == null) {
            synchronized (TelModelHitbox.class) {
                local = instance;
                if (local == null) {
                    local = loadFromClasspath();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static TelModelHitbox loadFromClasspath() {
        try (InputStream geo = TelModelHitbox.class.getResourceAsStream(GEO);
                InputStream anim = TelModelHitbox.class.getResourceAsStream(ANIMATIONS)) {
            if (geo == null || anim == null) {
                throw new IOException("launcher model files are missing from the jar");
            }
            return load(geo, anim);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not build the launcher hit boxes; it will have none", e);
            return new TelModelHitbox(new Bone[0], new Box[0], new TelAnimSampler(), 0, 0.0);
        }
    }

    public static TelModelHitbox load(InputStream geo, InputStream animations) throws IOException {
        TelAnimSampler sampler = TelAnimSampler.load(animations);
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(geo, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonArray boneArray = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < boneArray.size(); i++) {
            index.put(boneArray.get(i).getAsJsonObject().get("name").getAsString(), i);
        }

        Bone[] bones = new Bone[boneArray.size()];
        List<List<Item>> cubes = new ArrayList<>();
        int cubeCount = 0;
        for (int i = 0; i < bones.length; i++) {
            JsonObject b = boneArray.get(i).getAsJsonObject();
            String name = b.get("name").getAsString();
            int parent = b.has("parent") ? index.getOrDefault(b.get("parent").getAsString(), -1) : -1;
            if (parent >= i) {
                throw new IOException("bone " + name + " comes before its parent");
            }
            double[] pivot = b.has("pivot") ? triple(b.getAsJsonArray("pivot")) : new double[3];
            double[] rot = b.has("rotation") ? triple(b.getAsJsonArray("rotation")) : new double[3];
            bones[i] = new Bone(name, parent, new double[] {-pivot[0] / 16.0, pivot[1] / 16.0, pivot[2] / 16.0},
                    new double[] {-rot[0], -rot[1], rot[2]}, TelBoneGroups.of(name));

            List<Item> items = new ArrayList<>();
            if (b.has("cubes")) {
                for (JsonElement c : b.getAsJsonArray("cubes")) {
                    items.add(Item.of(c.getAsJsonObject()));
                    cubeCount++;
                }
            }
            cubes.add(items);
        }

        double ratio = 1.2;
        Box[] boxes;
        while (true) {
            boxes = build(bones, cubes, ratio);
            if (boxes.length <= TARGET_BOXES || ratio > 2.5) {
                break;
            }
            ratio += 0.05;
        }
        LOGGER.info("Launcher hit boxes: {} cubes in the model, {} boxes kept (merge ratio {})",
                cubeCount, boxes.length, String.format("%.2f", ratio));
        return new TelModelHitbox(bones, boxes, sampler, cubeCount, ratio);
    }

    public int boxCount() {
        return this.boxes.length;
    }

    public int cubeCount() {
        return this.cubeCount;
    }

    public double mergeRatio() {
        return this.mergeRatio;
    }

    public TelAnimSampler sampler() {
        return this.sampler;
    }

    public int countIn(Zone zone) {
        int n = 0;
        for (Box box : this.boxes) {
            if (box.zone() == zone) {
                n++;
            }
        }
        return n;
    }

    public List<WorldBox> compute(TelHitboxPose pose, double x, double y, double z,
            float yawDeg, float pitchDeg, float rollDeg) {
        List<WorldBox> out = new ArrayList<>(this.boxes.length);
        if (this.boxes.length == 0) {
            return out;
        }
        Matrix4d[] chain = this.chain(pose);

        for (Box box : this.boxes) {
            if (box.zone() == Zone.MISSILE_1 && !pose.loaded0() || box.zone() == Zone.MISSILE_2 && !pose.loaded1()) {
                continue;
            }
            Matrix4d m = chain[box.bone()];

            Vector3d p = new Vector3d(box.centre()[0], box.centre()[1], box.centre()[2]);
            Matrix3d axes = new Matrix3d(m);
            if (box.rot() != null) {
                p.sub(box.pivot()[0], box.pivot()[1], box.pivot()[2]);
                box.rot().transform(p);
                p.add(box.pivot()[0], box.pivot()[1], box.pivot()[2]);
                axes.mul(box.rot());
            }
            m.transformPosition(p);

            Vec3 centre = TelGeometry.localToWorld(x, y, z, yawDeg, pitchDeg, rollDeg, new Vec3(p.x, p.y, p.z));
            out.add(new WorldBox(box.zone(), centre,
                    TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(axes.m00, axes.m01, axes.m02)),
                    TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(axes.m10, axes.m11, axes.m12)),
                    TelGeometry.rotateToWorld(yawDeg, pitchDeg, rollDeg, new Vec3(axes.m20, axes.m21, axes.m22)),
                    box.half()[0], box.half()[1], box.half()[2], box.solid()));
        }
        return out;
    }

    public Vec3 modelPointOf(TelHitboxPose pose, String bone, double gx, double gy, double gz) {
        for (int i = 0; i < this.bones.length; i++) {
            if (this.bones[i].name().equals(bone)) {
                Vector3d p = new Vector3d(-gx / 16.0, gy / 16.0, gz / 16.0);
                this.chain(pose)[i].transformPosition(p);
                return new Vec3(p.x, p.y, p.z);
            }
        }
        return null;
    }

    private Matrix4d[] chain(TelHitboxPose pose) {
        Matrix4d[] chain = new Matrix4d[this.bones.length];
        for (int i = 0; i < this.bones.length; i++) {
            Bone b = this.bones[i];
            double rx = b.baseRot()[0];
            double ry = b.baseRot()[1];
            double rz = b.baseRot()[2];
            double dx = 0.0;
            double dy = 0.0;
            double dz = 0.0;
            double sx = 1.0;
            double sy = 1.0;
            double sz = 1.0;
            TelAnimSampler.BoneValue av = null;
            if (b.group() >= 0) {
                TelHitboxPose.Part part = pose.part(b.group());
                if (part.clip() != null) {
                    av = this.sampler.bone(part.clip(), b.name(), part.seconds());
                }
            }
            if (av != null) {
                if (av.rotation() != null) {
                    rx -= av.rotation()[0];
                    ry -= av.rotation()[1];
                    rz += av.rotation()[2];
                }
                if (av.position() != null) {
                    dx = -av.position()[0] / 16.0;
                    dy = av.position()[1] / 16.0;
                    dz = av.position()[2] / 16.0;
                }
                double[] scale = av.scaleOrOne();
                sx = scale[0];
                sy = scale[1];
                sz = scale[2];
            }
            if (b.name().startsWith("jack_")) {
                dy -= 9.0 / 16.0 * pose.jackProgress();
            }
            double[] p = b.pivot();
            Matrix4d local = new Matrix4d()
                    .translation(dx + p[0], dy + p[1], dz + p[2])
                    .rotateZ(Math.toRadians(rz))
                    .rotateY(Math.toRadians(ry))
                    .rotateX(Math.toRadians(rx))
                    .scale(sx, sy, sz)
                    .translate(-p[0], -p[1], -p[2]);
            chain[i] = b.parent() < 0 ? local : new Matrix4d(chain[b.parent()]).mul(local);
        }
        return chain;
    }

    private static final class Item {
        double[] lo = new double[3];
        double[] hi = new double[3];
        double[] rot;
        double[] pivot;

        static Item of(JsonObject cube) {
            double[] o = triple(cube.getAsJsonArray("origin"));
            double[] s = triple(cube.getAsJsonArray("size"));
            Item it = new Item();
            it.lo = new double[] {-(o[0] + s[0]), o[1], o[2]};
            it.hi = new double[] {-o[0], o[1] + s[1], o[2] + s[2]};
            if (cube.has("rotation")) {
                double[] r = triple(cube.getAsJsonArray("rotation"));
                if (Math.abs(r[0]) > 1.0E-9 || Math.abs(r[1]) > 1.0E-9 || Math.abs(r[2]) > 1.0E-9) {
                    double[] p = cube.has("pivot") ? triple(cube.getAsJsonArray("pivot")) : new double[3];
                    it.rot = new double[] {-r[0], -r[1], r[2]};
                    it.pivot = new double[] {-p[0], p[1], p[2]};
                }
            }
            return it;
        }

        double volume() {
            return (this.hi[0] - this.lo[0]) * (this.hi[1] - this.lo[1]) * (this.hi[2] - this.lo[2]);
        }

        double smallestSide() {
            return Math.min(this.hi[0] - this.lo[0], Math.min(this.hi[1] - this.lo[1], this.hi[2] - this.lo[2]));
        }

        Matrix3d matrix() {
            return new Matrix3d().rotateZ(Math.toRadians(this.rot[2])).rotateY(Math.toRadians(this.rot[1]))
                    .rotateX(Math.toRadians(this.rot[0]));
        }

        double[][] bounds() {
            if (this.rot == null) {
                return new double[][] {this.lo.clone(), this.hi.clone()};
            }
            Matrix3d r = this.matrix();
            double[] mn = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] mx = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int cx = 0; cx < 2; cx++) {
                for (int cy = 0; cy < 2; cy++) {
                    for (int cz = 0; cz < 2; cz++) {
                        Vector3d v = new Vector3d(
                                (cx == 0 ? this.lo[0] : this.hi[0]) - this.pivot[0],
                                (cy == 0 ? this.lo[1] : this.hi[1]) - this.pivot[1],
                                (cz == 0 ? this.lo[2] : this.hi[2]) - this.pivot[2]);
                        r.transform(v);
                        double[] w = {v.x + this.pivot[0], v.y + this.pivot[1], v.z + this.pivot[2]};
                        for (int i = 0; i < 3; i++) {
                            mn[i] = Math.min(mn[i], w[i]);
                            mx[i] = Math.max(mx[i], w[i]);
                        }
                    }
                }
            }
            return new double[][] {mn, mx};
        }

        boolean sameFrameAs(Item o) {
            if (this.rot == null || o.rot == null) {
                return this.rot == null && o.rot == null;
            }
            for (int i = 0; i < 3; i++) {
                if (Math.abs(this.rot[i] - o.rot[i]) > 1.0E-6 || Math.abs(this.pivot[i] - o.pivot[i]) > 1.0E-6) {
                    return false;
                }
            }
            return true;
        }
    }

    private static Item tryMerge(Item a, Item b, double ratio) {
        if (a.sameFrameAs(b)) {
            Item m = new Item();
            for (int i = 0; i < 3; i++) {
                m.lo[i] = Math.min(a.lo[i], b.lo[i]);
                m.hi[i] = Math.max(a.hi[i], b.hi[i]);
            }
            m.rot = a.rot;
            m.pivot = a.pivot;
            return m.volume() <= ratio * (a.volume() + b.volume()) ? m : null;
        }
        double[][] ba = a.bounds();
        double[][] bb = b.bounds();
        Item m = new Item();
        for (int i = 0; i < 3; i++) {
            m.lo[i] = Math.min(ba[0][i], bb[0][i]);
            m.hi[i] = Math.max(ba[1][i], bb[1][i]);
        }
        return m.volume() <= ratio * (a.volume() + b.volume()) ? m : null;
    }

    private static List<Item> simplify(List<Item> items, double ratio) {
        List<Item> list = new ArrayList<>(items);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Item merged = tryMerge(list.get(i), list.get(j), ratio);
                    if (merged != null) {
                        list.set(i, merged);
                        list.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return list;
    }

    private static Box[] build(Bone[] bones, List<List<Item>> cubes, double ratio) {
        List<Box> out = new ArrayList<>();
        for (int b = 0; b < bones.length; b++) {
            List<Item> all = cubes.get(b);
            if (all.isEmpty()) {
                continue;
            }
            Zone zone = zoneOf(bones, b);

            List<Item> kept = new ArrayList<>();
            for (Item it : all) {
                if (it.smallestSide() >= MIN_SIDE_PX && it.volume() >= MIN_VOLUME_PX3) {
                    kept.add(it);
                }
            }
            if (kept.isEmpty()) {
                double[] mn = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
                double[] mx = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
                for (Item it : all) {
                    double[][] bounds = it.bounds();
                    for (int i = 0; i < 3; i++) {
                        mn[i] = Math.min(mn[i], bounds[0][i]);
                        mx[i] = Math.max(mx[i], bounds[1][i]);
                    }
                }
                Item envelope = new Item();
                envelope.lo = mn;
                envelope.hi = mx;
                kept.add(envelope);
            }

            for (Item it : simplify(kept, ratio)) {
                double[] centre = new double[3];
                double[] half = new double[3];
                for (int i = 0; i < 3; i++) {
                    centre[i] = (it.lo[i] + it.hi[i]) * 0.5 / 16.0;
                    half[i] = (it.hi[i] - it.lo[i]) * 0.5 / 16.0;
                }
                boolean solid = Math.min(half[0], Math.min(half[1], half[2])) * 2.0 >= SOLID_SIDE;
                double[] pivot = it.rot == null ? null
                        : new double[] {it.pivot[0] / 16.0, it.pivot[1] / 16.0, it.pivot[2] / 16.0};
                out.add(new Box(b, zone, centre, half, pivot, it.rot == null ? null : it.matrix(), solid));
            }
        }
        return out.toArray(new Box[0]);
    }

    private static Zone zoneOf(Bone[] bones, int index) {
        int i = index;
        while (i >= 0) {
            Zone z = Zone.ofBone(bones[i].name());
            if (z != null) {
                return z;
            }
            i = bones[i].parent();
        }
        return Zone.CHASSIS;
    }

    private static double[] triple(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }
}
