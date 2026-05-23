package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OrlanCameraOverlay {

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!OrlanCameraHandler.isCameraActive()) return;

        OrlanEntity orlan = OrlanCameraHandler.getLinkedOrlan();
        if (orlan == null) return;

        Minecraft mc = Minecraft.getInstance();
        float zoom = OrlanCameraHandler.getZoomLevel();
        Vec3 dronePos = orlan.position();
        BlockPos targetPos = orlan.getTargetPos();
        Vec3 targetVec = Vec3.atCenterOf(targetPos);

        RenderSystem.enableBlend();

        renderNightVision(guiGraphics, mc, screenWidth, screenHeight);

        int borderSize = 32;
        int borderColor = 0xDD000000;
        guiGraphics.fill(0, 0, screenWidth, borderSize, borderColor);
        guiGraphics.fill(0, screenHeight - borderSize, screenWidth, screenHeight, borderColor);
        guiGraphics.fill(0, borderSize, borderSize, screenHeight - borderSize, borderColor);
        guiGraphics.fill(screenWidth - borderSize, borderSize, screenWidth, screenHeight - borderSize, borderColor);

        int frameColor = 0xCC00CC00;
        int frameColor2 = 0x6600AA00;

        guiGraphics.fill(borderSize, borderSize, screenWidth - borderSize, borderSize + 2, frameColor);
        guiGraphics.fill(borderSize, screenHeight - borderSize - 2, screenWidth - borderSize, screenHeight - borderSize, frameColor);
        guiGraphics.fill(borderSize, borderSize, borderSize + 2, screenHeight - borderSize, frameColor);
        guiGraphics.fill(screenWidth - borderSize - 2, borderSize, screenWidth - borderSize, screenHeight - borderSize, frameColor);

        guiGraphics.fill(borderSize + 2, borderSize + 2, screenWidth - borderSize - 2, borderSize + 3, frameColor2);
        guiGraphics.fill(borderSize + 2, screenHeight - borderSize - 3, screenWidth - borderSize - 2, screenHeight - borderSize - 2, frameColor2);
        guiGraphics.fill(borderSize + 2, borderSize + 2, borderSize + 3, screenHeight - borderSize - 2, frameColor2);
        guiGraphics.fill(screenWidth - borderSize - 3, borderSize + 2, screenWidth - borderSize - 2, screenHeight - borderSize - 2, frameColor2);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        boolean locked = OrlanCameraHandler.isLocked();
        int crossColor = locked ? 0xCCFF2222 : 0xCC00FF00;
        int crossColorDim = locked ? 0x88CC0000 : 0x8800CC00;
        int dotColor = locked ? 0xFFFF0000 : 0xFF00FF00;
        int crossSize = 18;
        int crossGap = 4;

        guiGraphics.fill(cx - crossSize, cy, cx - crossGap, cy + 1, crossColor);
        guiGraphics.fill(cx + crossGap + 1, cy, cx + crossSize, cy + 1, crossColor);

        guiGraphics.fill(cx, cy - crossSize, cx + 1, cy - crossGap, crossColor);
        guiGraphics.fill(cx, cy + crossGap + 1, cx + 1, cy + crossSize, crossColor);

        guiGraphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, dotColor);

        int tickSize = 5;
        int tickOffset = 45;
        guiGraphics.fill(cx - tickSize, cy - tickOffset, cx + tickSize, cy - tickOffset + 1, crossColor);
        guiGraphics.fill(cx - tickSize, cy + tickOffset, cx + tickSize, cy + tickOffset + 1, crossColor);
        guiGraphics.fill(cx - tickOffset, cy - tickSize, cx - tickOffset + 1, cy + tickSize, crossColor);
        guiGraphics.fill(cx + tickOffset, cy - tickSize, cx + tickOffset + 1, cy + tickSize, crossColor);

        int cornerLen = 20;
        int cornerOff = 80;
        int cornerColor = crossColorDim;

        guiGraphics.fill(cx - cornerOff, cy - cornerOff, cx - cornerOff + cornerLen, cy - cornerOff + 1, cornerColor);
        guiGraphics.fill(cx - cornerOff, cy - cornerOff, cx - cornerOff + 1, cy - cornerOff + cornerLen, cornerColor);

        guiGraphics.fill(cx + cornerOff - cornerLen, cy - cornerOff, cx + cornerOff, cy - cornerOff + 1, cornerColor);
        guiGraphics.fill(cx + cornerOff, cy - cornerOff, cx + cornerOff + 1, cy - cornerOff + cornerLen, cornerColor);

        guiGraphics.fill(cx - cornerOff, cy + cornerOff, cx - cornerOff + cornerLen, cy + cornerOff + 1, cornerColor);
        guiGraphics.fill(cx - cornerOff, cy + cornerOff - cornerLen, cx - cornerOff + 1, cy + cornerOff, cornerColor);

        guiGraphics.fill(cx + cornerOff - cornerLen, cy + cornerOff, cx + cornerOff, cy + cornerOff + 1, cornerColor);
        guiGraphics.fill(cx + cornerOff, cy + cornerOff - cornerLen, cx + cornerOff + 1, cy + cornerOff, cornerColor);

        int iL = borderSize + 4; 
        int iR = screenWidth - borderSize - 4; 
        int iT = borderSize + 4; 
        int iB = screenHeight - borderSize - 4; 

        int textColor = 0x00FF00;
        int brightColor = 0x44FF44;
        int dimColor = 0x009900;
        int labelColor = 0x00BB00;
        int warnColor = 0xFF5555;
        int panelBg = 0x88000000;

        String header = "ОРЛАН-10 // РАЗВЕДКА";
        int headerW = mc.font.width(header) + 8;
        guiGraphics.fill(iL, iT, iL + headerW, iT + 12, panelBg);
        guiGraphics.drawString(mc.font, header, iL + 4, iT + 2, textColor, true);

        long dayTime = mc.level != null ? mc.level.getDayTime() % 24000 : 0;
        int hours = (int) ((dayTime / 1000 + 6) % 24);
        int minutes = (int) ((dayTime % 1000) * 60 / 1000);
        String timeStr = String.format("%02d:%02d", hours, minutes);
        int timeW = mc.font.width(timeStr) + 8;
        guiGraphics.fill(iR - timeW, iT, iR, iT + 12, panelBg);
        guiGraphics.drawString(mc.font, timeStr, iR - timeW + 4, iT + 2, dimColor, true);

        if (OrlanCameraHandler.isNvgActive()) {
            String nvgStr = "ПНВ";
            int nvgW = mc.font.width(nvgStr) + 8;
            guiGraphics.fill(iR - timeW - nvgW - 2, iT, iR - timeW - 2, iT + 12, 0x88003300);
            guiGraphics.drawString(mc.font, nvgStr, iR - timeW - nvgW + 2, iT + 2, 0xFF00FF00, true);
        }

        int leftX = iL + 4;
        int leftY = iT + 18;
        int leftPanelW = 118;

        guiGraphics.fill(iL, leftY - 2, iL + leftPanelW, leftY + 46, panelBg);
        guiGraphics.drawString(mc.font, "КООРДИНАТЫ:", leftX, leftY, labelColor, true);
        guiGraphics.drawString(mc.font, String.format("X: %.0f", dronePos.x), leftX, leftY + 12, brightColor, true);
        guiGraphics.drawString(mc.font, String.format("Y: %.0f", dronePos.y), leftX, leftY + 22, brightColor, true);
        guiGraphics.drawString(mc.font, String.format("Z: %.0f", dronePos.z), leftX, leftY + 32, brightColor, true);

        int targetY = leftY + 50;
        OrlanEntity.FlightPhase phase = orlan.getFlightPhase();
        double horizDistToTarget = Math.sqrt(
                Math.pow(targetVec.x - dronePos.x, 2) + Math.pow(targetVec.z - dronePos.z, 2));

        boolean showDistToTarget = (phase != OrlanEntity.FlightPhase.PATROLLING
                && phase != OrlanEntity.FlightPhase.LANDED);

        int targetPanelH = showDistToTarget ? 38 : 26;
        guiGraphics.fill(iL, targetY - 2, iL + leftPanelW, targetY + targetPanelH, panelBg);
        guiGraphics.drawString(mc.font, "ЦЕЛЬ:", leftX, targetY, labelColor, true);
        guiGraphics.drawString(mc.font, String.format("X: %d  Z: %d", targetPos.getX(), targetPos.getZ()),
                leftX, targetY + 12, dimColor, true);

        if (showDistToTarget) {
            String distStr = String.format("ДО ЦЕЛИ: %.0f m", horizDistToTarget);
            int distColor = (horizDistToTarget < 100) ? brightColor : textColor;
            guiGraphics.drawString(mc.font, distStr, leftX, targetY + 24, distColor, true);
        }

        int altY = targetY + targetPanelH + 4;
        guiGraphics.fill(iL, altY - 2, iL + 108, altY + 22, panelBg);
        guiGraphics.drawString(mc.font, "ALT", leftX, altY, labelColor, true);
        guiGraphics.drawString(mc.font,
                String.format("%.0f m  TGT: %.0f", dronePos.y, orlan.getTargetAltitude()),
                leftX, altY + 10, brightColor, true);

        int aimY = altY + 26;
        BlockHitResult aimResult = raycastFromCamera(mc, orlan);
        if (aimResult != null && aimResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = aimResult.getBlockPos();
            double aimDist = dronePos.distanceTo(Vec3.atCenterOf(hitPos));
            int aimPanelW = Math.max(leftPanelW, mc.font.width(String.format("X:%-6d Z:%-6d", hitPos.getX(), hitPos.getZ())) + 12);
            guiGraphics.fill(iL, aimY - 2, iL + aimPanelW, aimY + 36, panelBg);
            guiGraphics.drawString(mc.font, "НАВЕДЕНИЕ:", leftX, aimY, 0xFF5555, true);
            guiGraphics.drawString(mc.font, String.format("X: %d  Z: %d", hitPos.getX(), hitPos.getZ()),
                    leftX, aimY + 12, 0xFFFF44, true);
            guiGraphics.drawString(mc.font, String.format("Y: %d   ДИСТ: %.0f m", hitPos.getY(), aimDist),
                    leftX, aimY + 24, 0xFFFF44, true);
        } else {
            guiGraphics.fill(iL, aimY - 2, iL + leftPanelW, aimY + 14, panelBg);
            guiGraphics.drawString(mc.font, "НАВЕДЕНИЕ: ---", leftX, aimY, dimColor, true);
        }

        int rightPanelW = 88;
        int rightPanelLeft = iR - rightPanelW;
        int rightX = rightPanelLeft + 4;
        int rightY = iT + 18;

        guiGraphics.fill(rightPanelLeft, rightY - 2, iR, rightY + 22, panelBg);
        guiGraphics.drawString(mc.font, "ZOOM", rightX, rightY, labelColor, true);
        guiGraphics.drawString(mc.font, String.format("%.1fx", zoom), rightX, rightY + 10, brightColor, true);

        int zoomBarX = iR - 14;
        int zoomBarTop = rightY + 26;
        int zoomBarHeight = 80;

        guiGraphics.fill(zoomBarX - 2, zoomBarTop - 2, zoomBarX + 10, zoomBarTop + zoomBarHeight + 2, panelBg);
        guiGraphics.fill(zoomBarX, zoomBarTop, zoomBarX + 8, zoomBarTop + zoomBarHeight, 0x44003300);

        float zoomPercent = (zoom - 1.0f) / 11.0f;
        int fillHeight = (int) (zoomBarHeight * zoomPercent);
        if (fillHeight > 0) {
            guiGraphics.fill(zoomBarX, zoomBarTop + zoomBarHeight - fillHeight,
                    zoomBarX + 8, zoomBarTop + zoomBarHeight, 0xAA00CC00);
        }

        guiGraphics.fill(zoomBarX, zoomBarTop, zoomBarX + 8, zoomBarTop + 1, dimColor);
        guiGraphics.fill(zoomBarX, zoomBarTop + zoomBarHeight - 1, zoomBarX + 8, zoomBarTop + zoomBarHeight, dimColor);
        guiGraphics.fill(zoomBarX, zoomBarTop, zoomBarX + 1, zoomBarTop + zoomBarHeight, dimColor);
        guiGraphics.fill(zoomBarX + 7, zoomBarTop, zoomBarX + 8, zoomBarTop + zoomBarHeight, dimColor);

        guiGraphics.drawString(mc.font, "12x", zoomBarX - 18, zoomBarTop - 1, dimColor, true);
        guiGraphics.drawString(mc.font, "1x", zoomBarX - 16, zoomBarTop + zoomBarHeight - 8, dimColor, true);

        int hpY = zoomBarTop + zoomBarHeight + 8;
        float healthPercent = orlan.getHealthPercent();
        int healthColor = healthPercent > 0.5f ? textColor : (healthPercent > 0.25f ? 0xFFAA00 : warnColor);
        guiGraphics.fill(rightPanelLeft, hpY - 2, iR, hpY + 12, panelBg);
        guiGraphics.drawString(mc.font, String.format("HP %.0f%%", healthPercent * 100), rightX, hpY, healthColor, true);

        String phaseStr;
        switch (phase) {
            case LAUNCHING: phaseStr = "ЗАПУСК"; break;
            case CLIMBING: phaseStr = "НАБОР"; break;
            case CRUISING: phaseStr = "ПОЛЁТ"; break;
            case PATROLLING: phaseStr = "ПАТРУЛЬ"; break;
            case RETURNING: phaseStr = "ВОЗВРАТ"; break;
            case LANDING: phaseStr = "ПОСАДКА"; break;
            case LANDED: phaseStr = "НА ЗЕМЛЕ"; break;
            default: phaseStr = "---";
        }
        int phaseY = hpY + 14;
        guiGraphics.fill(rightPanelLeft, phaseY - 2, iR, phaseY + 12, panelBg);
        guiGraphics.drawString(mc.font, phaseStr, rightX, phaseY, brightColor, true);

        int remainingTicks = orlan.getRemainingFlightTicks();
        int totalSeconds = remainingTicks / 20;
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        int timerColor = (mins < 2) ? warnColor : textColor;
        int timerY = phaseY + 14;
        guiGraphics.fill(rightPanelLeft, timerY - 2, iR, timerY + 12, panelBg);
        guiGraphics.drawString(mc.font, String.format("TIME %02d:%02d", mins, secs), rightX, timerY, timerColor, true);

        int bottomY = iB - 10;

        guiGraphics.fill(iL, bottomY - 2, iR, bottomY + 11, panelBg);

        if (mc.player != null) {
            double distToPlayer = mc.player.position().distanceTo(dronePos);
            guiGraphics.drawString(mc.font, String.format("ДИСТ: %.0f m", distToPlayer), iL + 4, bottomY, dimColor, true);
        }

        Vec3 motion = orlan.getDeltaMovement();
        double speed = motion.length() * 20.0;
        guiGraphics.drawString(mc.font, String.format("SPD: %.0f b/s", speed), cx - 30, bottomY, dimColor, true);

        String exitHint = "[LAlt] Выход [R] Lock [N] ПНВ [Space/Ctrl] ALT";
        guiGraphics.drawString(mc.font, exitHint,
                iR - mc.font.width(exitHint) - 4, bottomY, dimColor, true);

        renderCompass(guiGraphics, mc, cx, borderSize + 14, OrlanCameraHandler.getCameraYaw());

        renderLockOnUI(guiGraphics, mc, cx, cy, dronePos, screenWidth);

        RenderSystem.disableBlend();
    }

    private static void renderNightVision(GuiGraphics guiGraphics, Minecraft mc, int w, int h) {
        if (mc.level == null) return;
        long dayTime = mc.level.getDayTime() % 24000;
        boolean isNight = dayTime >= 13000 && dayTime < 23000;
        boolean isDusk = (dayTime >= 11500 && dayTime < 13000) || dayTime >= 23000 || dayTime < 500;

        if (OrlanCameraHandler.isNvgActive()) {

            if (!isNight && !isDusk) {

                guiGraphics.fill(0, 0, w, h, 0x88FFFFFF);
            } else if (isDusk) {

                guiGraphics.fill(0, 0, w, h, 0x22FFFFFF);
            }

        } else {
            if (isNight) {

                guiGraphics.fill(0, 0, w, h, 0xAA000000);
            } else if (isDusk) {

                guiGraphics.fill(0, 0, w, h, 0x33000000);
            }
        }
    }

    private static void renderCompass(GuiGraphics guiGraphics, Minecraft mc, int centerX, int y, float yaw) {
        int compassWidth = 220;
        int halfWidth = compassWidth / 2;

        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;

        guiGraphics.fill(centerX - halfWidth, y - 2, centerX + halfWidth, y + 11, 0x88000000);

        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        float[] angles = {180, 225, 270, 315, 0, 45, 90, 135};

        for (int i = 0; i < directions.length; i++) {
            float angle = angles[i];
            float diff = angle - yaw;
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;

            int xPos = centerX + (int) (diff * halfWidth / 90.0f);

            if (xPos > centerX - halfWidth + 5 && xPos < centerX + halfWidth - 5) {
                boolean isCardinal = i % 2 == 0;
                int color = isCardinal ? 0x44FF44 : 0x009900;
                guiGraphics.drawString(mc.font, directions[i], xPos - mc.font.width(directions[i]) / 2, y, color, true);
            }
        }

        guiGraphics.fill(centerX, y - 4, centerX + 1, y - 1, 0xFF00FF00);
        guiGraphics.fill(centerX - 1, y - 3, centerX + 2, y - 1, 0xFF00FF00);
    }

    private static void renderLockOnUI(GuiGraphics guiGraphics, Minecraft mc, int cx, int cy, Vec3 dronePos, int screenWidth) {
        int crossSize = 18; 

        if (OrlanCameraHandler.isLocking()) {

            float progress = OrlanCameraHandler.getLockProgress();
            int barW = 54;
            int barH = 3;
            int barX = cx - barW / 2;
            int barY = cy + crossSize + 6;
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0x55000000);
            int filled = (int)(barW * progress);
            guiGraphics.fill(barX, barY, barX + filled, barY + barH, 0xDDFF2222);

            String lockingText = ">> ЗАХВАТ <<";
            guiGraphics.drawString(mc.font, lockingText,
                    cx - mc.font.width(lockingText) / 2, barY + barH + 3, 0xDDFF3333, true);
        } else if (OrlanCameraHandler.isLocked()) {

            String lockLabel = "[ TGT LOCK ]";
            guiGraphics.drawString(mc.font, lockLabel,
                    cx - mc.font.width(lockLabel) / 2, cy + crossSize + 6, 0xDDFF2222, true);

            Vec3 target = OrlanCameraHandler.getLockTargetPos();
            if (target != null) {
                double dist = dronePos.distanceTo(target);
                int panelX = cx + 80 + 10; 
                int panelY = cy - 6;

                guiGraphics.fill(panelX - 2, panelY - 1, panelX + 102, panelY + 35, 0x44000000);

                guiGraphics.drawString(mc.font,
                        String.format("X: %d  Z: %d", (int) target.x, (int) target.z),
                        panelX, panelY + 1, 0xCCFF4444, true);
                guiGraphics.drawString(mc.font,
                        String.format("Y: %d", (int) target.y),
                        panelX, panelY + 13, 0xCCFF4444, true);

                guiGraphics.drawString(mc.font,
                        String.format("ДИСТ: %.0f m", dist),
                        panelX, panelY + 25, 0xCCFF6644, true);
            }
        }
    }

    private static BlockHitResult raycastFromCamera(Minecraft mc, OrlanEntity orlan) {
        if (mc.level == null) return null;
        float pitch = OrlanCameraHandler.getCameraPitch();
        float yaw = OrlanCameraHandler.getCameraYaw();
        Vec3 eyePos = orlan.getEyePosition(1.0f);
        Vec3 lookDir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 endPos = eyePos.add(lookDir.scale(500.0));
        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, orlan));
        return result;
    }
}
