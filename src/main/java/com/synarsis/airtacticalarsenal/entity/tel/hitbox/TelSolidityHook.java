package com.synarsis.airtacticalarsenal.entity.tel.hitbox;

import com.synarsis.airtacticalarsenal.vehicle.hitbox.HitboxVehicle;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.VehicleRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TelSolidityHook {

    private TelSolidityHook() {
    }

    public static void apply(Entity e) {
        if (!(e instanceof LivingEntity) || e.isSpectator() || e.isPassenger()) {
            return;
        }
        if (!VehicleRegistry.any(e.level())) {
            return;
        }
        for (HitboxVehicle tel : VehicleRegistry.in(e.level())) {
            if (tel.entity().isRemoved() || e.getRootVehicle() == tel.entity()) {
                continue;
            }
            AABB body = e.getBoundingBox();
            if (!tel.getBoundingBoxForCulling().intersects(body)) {
                continue;
            }
            TelSolidity.Push push = TelSolidity.resolve(tel.getSolidBoxes(), body);
            if (push.isNone()) {
                continue;
            }

            Vec3 off = push.offset();
            e.setPos(e.getX() + off.x, e.getY() + off.y, e.getZ() + off.z);
            Vec3 dm = e.getDeltaMovement();
            if (push.standing()) {
                e.setOnGround(true);
                e.fallDistance = 0.0f;
                e.setDeltaMovement(dm.x, Math.max(dm.y, 0.0), dm.z);
                carry(e, tel);
            } else {
                Vec3 n = off.normalize();
                double into = dm.dot(n);
                if (into < 0.0) {
                    e.setDeltaMovement(dm.subtract(n.scale(into)));
                }
            }
        }
    }

    private static void carry(Entity e, HitboxVehicle vehicle) {
        Entity tel = vehicle.entity();
        Vec3 d = vehicle.getCarryDelta();
        double turn = Math.toRadians(vehicle.getCarryYaw());
        if (d.lengthSqr() < 1.0E-12 && Math.abs(turn) < 1.0E-9) {
            return;
        }
        double ox = tel.getX() - d.x;
        double oz = tel.getZ() - d.z;
        double rx = e.getX() - ox;
        double rz = e.getZ() - oz;
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double nx = rx * cos - rz * sin;
        double nz = rx * sin + rz * cos;
        e.setPos(ox + d.x + nx, e.getY() + d.y, oz + d.z + nz);
    }
}
