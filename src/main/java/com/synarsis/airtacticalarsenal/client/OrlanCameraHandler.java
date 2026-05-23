package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.shader.NvgShaderHandler;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OrlanAltitudePacket;
import com.synarsis.airtacticalarsenal.network.OrlanCameraViewPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OrlanCameraHandler {

    private static boolean cameraActive = false;
    private static int linkedOrlanId = -1;
    private static float zoomLevel = 1.0f;
    private static float zoomLevelLerp = 1.0f;
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 12.0f;
    private static final float ZOOM_STEP = 0.5f;

    private static float cameraPitch = 30.0f; 
    private static float cameraYaw = 0.0f;

    private static final float MIN_CAMERA_PITCH = 20.0f;   
    private static final float MAX_CAMERA_PITCH = 90.0f;   

    private static boolean nvgActive = false;

    private static boolean isLocking = false;
    private static boolean isLocked = false;
    private static float lockProgress = 0.0f;
    private static Vec3 lockTargetPos = null;
    private static final float LOCK_DURATION_TICKS = 30.0f; 
    private static final float TRACK_SMOOTH_SPEED = 0.25f;  

    private static CameraType savedCameraType = CameraType.FIRST_PERSON;

    private static boolean pendingStop = false;

    private static int nullEntityTicks = 0;
    private static final int NULL_ENTITY_GRACE_TICKS = 100; 

    private static double smoothX, smoothY, smoothZ;
    private static boolean smoothInitialized = false;

    public static void startCameraView(int orlanEntityId) {
        cameraActive = true;
        linkedOrlanId = orlanEntityId;
        zoomLevel = 1.0f;
        zoomLevelLerp = 1.0f;
        cameraPitch = 30.0f;
        cameraYaw = 0.0f;
        pendingStop = false;
        nullEntityTicks = 0;
        smoothInitialized = false;
        resetLockOn();
        disableNvg();

        Minecraft mc = Minecraft.getInstance();
        savedCameraType = mc.options.getCameraType();

        if (mc.level != null) {
            Entity orlan = mc.level.getEntity(orlanEntityId);
            if (orlan != null) {
                mc.setCameraEntity(orlan);
            }

        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    public static void stopCameraView() {
        if (!cameraActive) return;

        int wasLinkedId = linkedOrlanId;
        cameraActive = false;
        linkedOrlanId = -1;
        zoomLevel = 1.0f;
        zoomLevelLerp = 1.0f;
        pendingStop = false;
        nullEntityTicks = 0;
        smoothInitialized = false;
        resetLockOn();
        disableNvg();

        if (wasLinkedId != -1) {
            try {
                NetworkHandler.sendToServer(new OrlanCameraViewPacket(wasLinkedId, false));
            } catch (Exception ignored) {}
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(savedCameraType);
        } catch (Exception ignored) {}
    }

    public static void forceStopCameraLocal() {
        if (!cameraActive) return;

        cameraActive = false;
        linkedOrlanId = -1;
        zoomLevel = 1.0f;
        zoomLevelLerp = 1.0f;
        pendingStop = false;
        nullEntityTicks = 0;
        smoothInitialized = false;
        resetLockOn();
        disableNvg();

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            mc.options.setCameraType(savedCameraType);
        } catch (Exception ignored) {}
    }

    public static boolean isCameraActive() {
        return cameraActive;
    }

    public static int getLinkedOrlanId() {
        return linkedOrlanId;
    }

    public static float getZoomLevel() {
        return zoomLevelLerp;
    }

    public static float getTargetZoomLevel() {
        return zoomLevel;
    }

    public static OrlanEntity getLinkedOrlan() {
        if (!cameraActive || linkedOrlanId == -1) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(linkedOrlanId);
        if (entity instanceof OrlanEntity orlan) {
            return orlan;
        }

        return null;
    }

    public static boolean handleMouseScroll(double delta) {
        if (!cameraActive) return false;

        if (delta > 0) {
            zoomLevel = Math.min(zoomLevel + ZOOM_STEP, MAX_ZOOM);
        } else if (delta < 0) {
            zoomLevel = Math.max(zoomLevel - ZOOM_STEP, MIN_ZOOM);
        }
        return true;
    }

    public static void tick() {

        if (pendingStop) {
            stopCameraView();
            return;
        }

        if (!cameraActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(linkedOrlanId);

        if (entity instanceof OrlanEntity orlan) {

            nullEntityTicks = 0;

            if (mc.getCameraEntity() != orlan) {
                mc.setCameraEntity(orlan);
            }
        } else {

            nullEntityTicks++;
            if (nullEntityTicks > NULL_ENTITY_GRACE_TICKS) {

                pendingStop = true;
            }
        }
    }

    public static boolean handleExitKey() {
        if (cameraActive) {
            pendingStop = true; 
            return true;
        }
        return false;
    }

    public static void updateSmoothedPosition(double rawX, double rawY, double rawZ) {
        if (!smoothInitialized) {
            smoothX = rawX;
            smoothY = rawY;
            smoothZ = rawZ;
            smoothInitialized = true;
            return;
        }
        float zoom = getZoomLevel();
        double smoothFactor = 0.3 + (zoom - 1.0) * 0.09;
        smoothFactor = Math.min(smoothFactor, 0.95);
        double tracking = 1.0 - smoothFactor;

        smoothX += (rawX - smoothX) * tracking;
        smoothY += (rawY - smoothY) * tracking;
        smoothZ += (rawZ - smoothZ) * tracking;
    }

    public static void updateZoomSmooth(float partialTick) {
        float speed = 6.0f;
        float dt = partialTick / 20.0f; 
        float factor = 1.0f - (float) Math.exp(-speed * Math.max(dt, 0.01f));
        zoomLevelLerp += (zoomLevel - zoomLevelLerp) * factor;
    }

    public static double getSmoothX() { return smoothX; }
    public static double getSmoothY() { return smoothY; }
    public static double getSmoothZ() { return smoothZ; }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static void setCameraPitch(float pitch) {
        cameraPitch = Math.max(MIN_CAMERA_PITCH, Math.min(MAX_CAMERA_PITCH, pitch));
    }

    public static void setCameraYaw(float yaw) {
        cameraYaw = yaw;
    }

    private static void resetLockOn() {
        isLocking = false;
        isLocked = false;
        lockProgress = 0.0f;
        lockTargetPos = null;
    }

    public static boolean handleLockKey(int action) {
        if (!cameraActive) return false;
        if (action == 1) { 
            if (isLocked) {
                resetLockOn();
                return true;
            }
            isLocking = true;
            lockProgress = 0.0f;
            return true;
        } else if (action == 0) { 
            if (isLocking && !isLocked) {
                isLocking = false;
                lockProgress = 0.0f;
            }
            return true;
        }
        return false;
    }

    public static void tickLockOn() {
        if (!cameraActive) { resetLockOn(); return; }
        if (!isLocking || isLocked) return;

        lockProgress += 1.0f / LOCK_DURATION_TICKS;
        if (lockProgress >= 1.0f) {
            lockProgress = 1.0f;
            Vec3 target = performRaycast();
            if (target != null) {
                lockTargetPos = target;
                isLocked = true;
            }
            isLocking = false;
        }
    }

    public static boolean updateTrackingAngles(float partialTick) {
        if (!isLocked || lockTargetPos == null) return false;
        OrlanEntity orlan = getLinkedOrlan();
        if (orlan == null) return false;

        Vec3 eyePos = orlan.getEyePosition(partialTick);
        double dx = lockTargetPos.x - eyePos.x;
        double dy = lockTargetPos.y - eyePos.y;
        double dz = lockTargetPos.z - eyePos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f);
        float targetPitch = Mth.wrapDegrees((float)(-(Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI))));

        float yawDiff = Mth.wrapDegrees(targetYaw - cameraYaw);
        cameraYaw += yawDiff * TRACK_SMOOTH_SPEED;
        cameraPitch += (targetPitch - cameraPitch) * TRACK_SMOOTH_SPEED;

        cameraPitch = Math.max(MIN_CAMERA_PITCH, Math.min(MAX_CAMERA_PITCH, cameraPitch));
        return true;
    }

    private static Vec3 performRaycast() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        OrlanEntity orlan = getLinkedOrlan();
        if (orlan == null) return null;
        Vec3 eyePos = orlan.getEyePosition(1.0f);
        Vec3 lookDir = Vec3.directionFromRotation(cameraPitch, cameraYaw);
        Vec3 endPos = eyePos.add(lookDir.scale(500.0));
        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, orlan));
        if (result.getType() == HitResult.Type.BLOCK) {
            return Vec3.atCenterOf(result.getBlockPos());
        }
        return null;
    }

    public static boolean isLocking() { return isLocking; }
    public static boolean isLocked() { return isLocked; }
    public static float getLockProgress() { return lockProgress; }
    public static Vec3 getLockTargetPos() { return lockTargetPos; }

    public static boolean isNvgActive() { return nvgActive; }

    public static void toggleNvg() {
        if (!cameraActive) return;
        if (nvgActive) {
            disableNvg();
        } else {
            enableNvg();
        }
    }

    private static void enableNvg() {
        NvgShaderHandler.enable();
        nvgActive = true;
    }

    private static void disableNvg() {
        NvgShaderHandler.disable();
        nvgActive = false;
    }

    private static int altitudeTickCounter = 0;
    private static final int ALTITUDE_TICK_INTERVAL = 3; 

    public static void tickAltitude() {
        if (!cameraActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; 
        long window = mc.getWindow().getWindow();
        boolean up = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!up && !down) {
            altitudeTickCounter = 0;
            return;
        }
        altitudeTickCounter++;
        if (altitudeTickCounter == 1 || altitudeTickCounter % ALTITUDE_TICK_INTERVAL == 0) {
            int direction = up ? 1 : -1;
            try {
                NetworkHandler.sendToServer(new OrlanAltitudePacket(linkedOrlanId, direction));
            } catch (Exception ignored) {}
        }
    }
}
