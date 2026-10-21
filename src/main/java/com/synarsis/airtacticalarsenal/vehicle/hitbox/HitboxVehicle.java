package com.synarsis.airtacticalarsenal.vehicle.hitbox;

import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface HitboxVehicle {

    default Entity entity() {
        return (Entity) this;
    }

    List<Obb> getPickObbs();

    List<WorldBox> getSolidBoxes();

    AABB getBoundingBoxForCulling();

    Vec3 getCarryDelta();

    float getCarryYaw();
}
