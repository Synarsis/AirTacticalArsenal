package com.synarsis.airtacticalarsenal.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class LauncherZoneValidator {

    public static final float MAX_DEVIATION_ANGLE = 80.0f;

    public static boolean isTargetInValidZone(Vec3 launcherPos, float launcherYaw, Vec3 targetPos) {
        double angle = calculateDeviationAngle(launcherPos, launcherYaw, targetPos);
        return angle <= MAX_DEVIATION_ANGLE;
    }

    public static boolean isTargetInValidZone(BlockPos launcherPos, float launcherYaw, BlockPos targetPos) {
        return isTargetInValidZone(
            Vec3.atCenterOf(launcherPos),
            launcherYaw,
            Vec3.atCenterOf(targetPos)
        );
    }

    public static boolean isTargetInValidZone(BlockPos launcherPos, float launcherYaw, int targetX, int targetZ) {
        return isTargetInValidZone(
            Vec3.atCenterOf(launcherPos),
            launcherYaw,
            new Vec3(targetX + 0.5, launcherPos.getY(), targetZ + 0.5)
        );
    }

    public static double calculateDeviationAngle(Vec3 launcherPos, float launcherYaw, Vec3 targetPos) {

        Vec3 toTarget = new Vec3(
            targetPos.x - launcherPos.x,
            0,
            targetPos.z - launcherPos.z
        );

        if (toTarget.lengthSqr() < 0.01) {
            return 0;
        }

        toTarget = toTarget.normalize();

        Vec3 launcherDirection = getDirectionFromYaw(launcherYaw);

        double dotProduct = toTarget.dot(launcherDirection);

        dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));

        return Math.toDegrees(Math.acos(dotProduct));
    }

    public static Vec3 getDirectionFromYaw(float yaw) {
        float radYaw = (float) Math.toRadians(yaw);
        return new Vec3(
            -Math.sin(radYaw),  
            0,                   
            Math.cos(radYaw)    
        ).normalize();
    }

    public static String getTargetDirectionDescription(double angle) {
        if (angle > 150) {
            return "прямо ПОЗАДИ ПУ";
        } else if (angle > 120) {
            return "сзади-сбоку";
        } else if (angle > 90) {
            return "сбоку/сзади";
        } else if (angle > MAX_DEVIATION_ANGLE) {
            return "слишком сбоку";
        } else {
            return "в допустимой зоне";
        }
    }

    public static String getShortErrorMessage(double angle) {
        return String.format("Угол: %.0f° (макс: %.0f°) - %s", 
            angle, MAX_DEVIATION_ANGLE, getTargetDirectionDescription(angle));
    }
}
