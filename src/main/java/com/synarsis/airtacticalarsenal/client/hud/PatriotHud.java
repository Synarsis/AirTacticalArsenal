package com.synarsis.airtacticalarsenal.client.hud;

import com.synarsis.airtacticalarsenal.client.ModKeys;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.client.gui.TelUi;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotRefusal;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotStatus;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PatriotHud implements LayeredDraw.Layer {

    private static final int MARGIN = 6;
    private static final int PAD = 5;
    private static final int WIDTH = 150;
    private static final int HOTBAR_HALF = 91;
    private static final int HOTBAR_HEIGHT = 24;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !AtaClientConfig.isTelHudEnabled()) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof PatriotVehicleEntity vehicle)) {
            return;
        }
        Font font = mc.font;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int width = Math.min(WIDTH, Math.max(110, g.guiWidth() / 3));
        int height = PAD + 18 + 2 + 3 + 4 + 5 + 4 + 12 + 4 + 11 + 11 + PAD - 2;
        int x = MARGIN;
        boolean clear = x + width + MARGIN <= g.guiWidth() / 2 - HOTBAR_HALF;
        int y = g.guiHeight() - height - MARGIN - (clear ? 0 : HOTBAR_HEIGHT);

        g.fill(x, y, x + width, y + height, TelUi.PANEL);
        TelUi.frame(g, new Rect(x, y, width, height), TelUi.BORDER);

        int tx = x + PAD;
        int inner = width - 2 * PAD;
        int ty = y + PAD;

        String speed = Integer.toString(Math.round(vehicle.getSpeedKmh()));
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, speed, 0, 0, TelUi.TEXT, false);
        g.pose().popPose();
        g.drawString(font, Component.translatable("gui.ata.tel.hud.kmh"), tx + font.width(speed) * 2 + 4, ty + 9,
                TelUi.DIM, false);
        ty += 18 + 2;
        if (vehicle.joltSimulating()) {
            String gear = vehicle.getGear() > 0 ? Integer.toString(vehicle.getGear())
                    : (vehicle.getGear() < 0 ? "R" : "N");
            String drive = gear + "  " + Math.round(vehicle.getEngineRpm() / 10.0) * 10 + " rpm";
            g.drawString(font, drive, tx + inner - font.width(drive), ty - 12, TelUi.DIM, false);
        }
        float load = Mth.clamp((float) vehicle.getEngineLoad(), 0.0f, 1.0f);
        g.fill(tx, ty, tx + inner, ty + 3, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * load), ty + 3, load > 0.85f ? TelUi.YELLOW : TelUi.GREEN);
        ty += 3 + 4;

        float fraction = Mth.clamp(vehicle.getHealth() / vehicle.getMaxHealth(), 0.0f, 1.0f);
        int hull = fraction > 0.5f ? TelUi.GREEN : (fraction > 0.2f ? TelUi.YELLOW : TelUi.RED);
        g.fill(tx, ty, tx + inner, ty + 5, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * fraction), ty + 5, hull);
        ty += 5 + 4;

        PatriotSequence view = vehicle.seqView();
        PatriotStatus.Line status = PatriotStatus.of(view, vehicle.operationFraction(partial), view.traverse());
        int colour = switch (status.tone()) {
            case OK -> TelUi.GREEN;
            case INFO -> TelUi.BLUE;
            case WORKING -> TelUi.YELLOW;
            case WARN -> TelUi.RED;
        };
        Rect chipBox = new Rect(tx, ty, inner, 12);
        g.fill(chipBox.x(), chipBox.y(), chipBox.right(), chipBox.bottom(), 0xFF0E0F10);
        TelUi.frame(g, chipBox, colour);
        TelUi.textCentred(g, font, Component.translatable(status.key(), status.args()), chipBox, colour);
        ty += 12 + 4;

        PatriotRefusal why = vehicle.whyNotDrive();
        Component line = why.ok() ? Component.translatable("gui.ata.patriot.can_drive")
                : Component.translatable(why.messageKey());
        TelUi.text(g, font, line, new Rect(tx, ty, inner, 10), why.ok() ? TelUi.GREEN : TelUi.YELLOW);
        ty += 11;
        TelUi.text(g, font, Component.translatable("gui.ata.tel.hint", ModKeys.TEL_FIRE_CONTROL.getTranslatedKeyMessage()),
                new Rect(tx, ty, inner, 10), TelUi.DIM);
    }
}
