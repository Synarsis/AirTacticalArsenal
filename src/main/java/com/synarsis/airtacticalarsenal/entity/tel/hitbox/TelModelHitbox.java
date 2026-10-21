package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.ModelHitbox;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TelModelHitbox {

    public static final String GEO = "/assets/ata/geo/iskander_tel_loaded.geo.json";
    public static final String ANIMATIONS = "/assets/ata/animations/iskander_tel_loaded.animation.json";

    public static final int TARGET_BOXES = 250;

    private static final ModelHitbox.Rules RULES = new ModelHitbox.Rules() {
        @Override
        public int group(String bone) {
            return TelBoneGroups.of(bone);
        }

        @Override
        public HitTag zone(String bone) {
            return Zone.ofBone(bone);
        }

        @Override
        public HitTag fallbackZone() {
            return Zone.CHASSIS;
        }
    };

    private static volatile TelModelHitbox instance;

    private final ModelHitbox model;

    private TelModelHitbox(ModelHitbox model) {
        this.model = model;
    }

    public static TelModelHitbox get() {
        TelModelHitbox local = instance;
        if (local == null) {
            synchronized (TelModelHitbox.class) {
                local = instance;
                if (local == null) {
                    local = new TelModelHitbox(ModelHitbox.fromClasspath(GEO, ANIMATIONS, RULES, TARGET_BOXES,
                            "the launcher"));
                    instance = local;
                }
            }
        }
        return local;
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

    public int countIn(Zone zone) {
        return this.model.countIn(zone);
    }

    private ModelHitbox.BonePose adapt(TelHitboxPose pose) {
        TelAnimSampler sampler = this.model.sampler();
        return (bone, group) -> {
            if (bone.startsWith("jack_")) {
                return new TelAnimSampler.BoneValue(null, new double[] {0.0, -9.0 * pose.jackProgress(), 0.0}, null);
            }
            if (group < 0) {
                return null;
            }
            TelHitboxPose.Part part = pose.part(group);
            return part.clip() == null ? null : sampler.bone(part.clip(), bone, part.seconds());
        };
    }

    public List<WorldBox> compute(TelHitboxPose pose, double x, double y, double z,
            float yawDeg, float pitchDeg, float rollDeg) {
        return this.model.compute(this.adapt(pose),
                zone -> zone == Zone.MISSILE_1 && !pose.loaded0() || zone == Zone.MISSILE_2 && !pose.loaded1(),
                x, y, z, yawDeg, pitchDeg, rollDeg);
    }

    public Vec3 modelPointOf(TelHitboxPose pose, String bone, double gx, double gy, double gz) {
        return this.model.modelPointOf(this.adapt(pose), bone, gx, gy, gz);
    }
}
