package com.synarsis.airtacticalarsenal.combat.damage;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.combat.hitbox.PartBox;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/** Durability of an airframe, tracked per section as well as in total. */
public final class PartHealth {

    private final float[] hp = new float[HitPart.values().length];
    private final float[] max = new float[HitPart.values().length];

    public PartHealth(List<PartBox> template, float totalMax) {
        for (PartBox box : template) {
            int i = box.part().ordinal();
            max[i] += totalMax * box.hpFraction();
        }
        System.arraycopy(max, 0, hp, 0, hp.length);
    }

    public float remaining(HitPart part) {
        return hp[part.ordinal()];
    }

    public float capacity(HitPart part) {
        return max[part.ordinal()];
    }

    public boolean isKnockedOut(HitPart part) {
        return hp[part.ordinal()] <= 0.0f;
    }

    public float total() {
        float sum = 0.0f;
        for (float v : hp) {
            sum += v;
        }
        return sum;
    }

    public float totalCapacity() {
        float sum = 0.0f;
        for (float v : max) {
            sum += v;
        }
        return sum;
    }

    /**
     * Applies aimed damage.
     *
     * @return {@code true} if this is the blow that knocked the section out
     */
    public boolean hurt(HitPart part, float damage) {
        int i = part.ordinal();
        if (hp[i] <= 0.0f) {
            return false;
        }
        hp[i] = Math.max(0.0f, hp[i] - damage);
        return hp[i] <= 0.0f;
    }

    /** Applies damage that arrived without a direction -- a blast, fire, a command. */
    public void hurtEverywhere(float damage) {
        float pool = total();
        if (pool <= 0.0f) {
            return;
        }
        for (int i = 0; i < hp.length; i++) {
            if (hp[i] > 0.0f) {
                hp[i] = Math.max(0.0f, hp[i] - damage * (hp[i] / pool));
            }
        }
    }

    public void setTotal(float value) {
        float pool = total();
        if (pool <= 0.0f) {
            return;
        }
        float scale = Math.max(0.0f, value) / pool;
        for (int i = 0; i < hp.length; i++) {
            hp[i] *= scale;
        }
    }

    public void restore() {
        System.arraycopy(max, 0, hp, 0, hp.length);
    }

    public void restorePart(HitPart part) {
        hp[part.ordinal()] = max[part.ordinal()];
    }

    public void save(CompoundTag tag) {
        // Written by name, not ordinal: reordering HitPart must not silently reassign durability.
        CompoundTag parts = new CompoundTag();
        for (HitPart part : HitPart.values()) {
            parts.putFloat(part.id(), hp[part.ordinal()]);
        }
        tag.put("PartHealth", parts);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("PartHealth")) {
            return;
        }
        CompoundTag parts = tag.getCompound("PartHealth");
        for (HitPart part : HitPart.values()) {
            if (parts.contains(part.id())) {
                hp[part.ordinal()] = Math.min(parts.getFloat(part.id()), max[part.ordinal()]);
            }
        }
    }
}
