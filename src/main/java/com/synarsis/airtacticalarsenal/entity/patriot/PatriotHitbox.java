package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelAnimSampler;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class PatriotHitbox {

    public static final int TARGET_BOXES = 250;

    private static final Map<PatriotKind, PatriotHitbox> INSTANCES = new EnumMap<>(PatriotKind.class);

    private final PatriotKind kind;
    private final ModelHitbox model;

    private PatriotHitbox(PatriotKind kind, ModelHitbox model) {
        this.kind = kind;
        this.model = model;
    }

    public static PatriotHitbox of(PatriotKind kind) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(kind, k -> new PatriotHitbox(k, ModelHitbox.fromClasspath(k.geoPath(),
                    k.animationPath(), rulesOf(k), TARGET_BOXES, k.id())));
        }
    }

    public static PatriotHitbox exact(PatriotKind kind) {
        return new PatriotHitbox(kind, ModelHitbox.fromClasspath(kind.geoPath(), kind.animationPath(), rulesOf(kind),
                100000, 1.0001, kind.id() + " exact"));
    }

    private static ModelHitbox.Rules rulesOf(PatriotKind kind) {
        return new ModelHitbox.Rules() {
            @Override
            public int group(String bone) {
                return PatriotBones.groupOf(kind, bone);
            }

            @Override
            public int channels(String bone) {
                return PatriotBones.channelsOf(bone);
            }

            @Override
            public HitTag zone(String bone) {
                return PatriotZone.ofBone(bone);
            }

            @Override
            public HitTag fallbackZone() {
                return PatriotZone.TRAILER;
            }
        };
    }

    public PatriotKind kind() {
        return this.kind;
    }

    public int boxCount() {
        return this.model.boxCount();
    }

    public int cubeCount() {
        return this.model.cubeCount();
    }

    public double mergeRatio() {
        return this.model.mergeRatio();
    }

    public TelAnimSampler sampler() {
        return this.model.sampler();
    }

    public int countIn(PatriotZone zone) {
        return this.model.countIn(zone);
    }

    public boolean hasBone(String bone) {
        return this.model.hasBone(bone);
    }

    public List<ModelHitbox.LocalBox> localBoxes(PatriotPose pose) {
        return this.model.localBoxes(pose.bonePose(this.kind, this.model.sampler()), null);
    }

    public List<ModelHitbox.LocalBox> localBoxes(PatriotPose pose, Predicate<HitTag> skip) {
        return this.model.localBoxes(pose.bonePose(this.kind, this.model.sampler()), skip);
    }

    public List<WorldBox> compute(PatriotPose pose, double x, double y, double z, float yawDeg, float pitchDeg,
            float rollDeg) {
        return this.model.compute(pose.bonePose(this.kind, this.model.sampler()), null, x, y, z, yawDeg, pitchDeg,
                rollDeg);
    }

    public Vec3 modelPointOf(PatriotPose pose, String bone, double gx, double gy, double gz) {
        return this.model.modelPointOf(pose.bonePose(this.kind, this.model.sampler()), bone, gx, gy, gz);
    }

    public int boneCount() {
        return this.model.boneCount();
    }

    public List<String> controlledBones() {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < this.model.boneCount(); i++) {
            String name = this.model.boneName(i);
            if (PatriotBones.groupOf(this.kind, name) >= 0 || PatriotBones.isDirect(this.kind, name)) {
                out.add(name);
            }
        }
        return out;
    }

    public String boneName(int index) {
        return this.model.boneName(index);
    }
}
