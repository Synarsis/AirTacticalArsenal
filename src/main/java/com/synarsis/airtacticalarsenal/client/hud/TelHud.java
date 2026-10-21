package com.synarsis.airtacticalarsenal.client.hud;

import com.synarsis.airtacticalarsenal.client.ModKeys;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.client.gui.TelUi;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
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
public class TelHud implements LayeredDraw.Layer {

    private static final int MARGIN = 6;
    private static final int PAD = 5;
    private static final int WIDTH = 132;
    private static final int HOTBAR_HALF = 91;
    private static final int HOTBAR_HEIGHT = 24;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !AtaClientConfig.isTelHudEnabled()) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof IskanderTelEntity tel)) {
            return;
        }
        Font font = mc.font;
        boolean crew = tel.isCrewSeat(mc.player);

        int width = Math.min(WIDTH, Math.max(100, g.guiWidth() / 3));
        int height = PAD + 18 + 2 + 3 + 4 + 5 + 4 + 12 + 4 + 2 * 11 + (crew ? 12 + 11 : 0) + PAD - 2;
        int x = MARGIN;
        boolean clear = x + width + MARGIN <= g.guiWidth() / 2 - HOTBAR_HALF;
        int y = g.guiHeight() - height - MARGIN - (clear ? 0 : HOTBAR_HEIGHT);

        if (TelInput.traceEnabled) {
            this.renderTrace(g, font, tel, x, y - 52, width + 20);
        }

        g.fill(x, y, x + width, y + height, TelUi.PANEL);
        TelUi.frame(g, new Rect(x, y, width, height), TelUi.BORDER);

        int tx = x + PAD;
        int inner = width - 2 * PAD;
        int ty = y + PAD;

        String speed = Integer.toString(Math.round(tel.getSpeedKmh()));
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, speed, 0, 0, TelUi.TEXT, false);
        g.pose().popPose();
        g.drawString(font, Component.translatable("gui.ata.tel.hud.kmh"), tx + font.width(speed) * 2 + 4, ty + 9,
                TelUi.DIM, false);
        ty += 18 + 2;
        float power = Mth.clamp(Math.abs((float) tel.engine.power), 0.0f, 1.0f);
        g.fill(tx, ty, tx + inner, ty + 3, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * power), ty + 3, power > 0.85f ? TelUi.YELLOW : TelUi.GREEN);
        ty += 3 + 4;

        float fraction = Mth.clamp(tel.getHealth() / tel.getMaxHealth(), 0.0f, 1.0f);
        int hull = fraction > 0.5f ? TelUi.GREEN : (fraction > 0.2f ? TelUi.YELLOW : TelUi.RED);
        g.fill(tx, ty, tx + inner, ty + 5, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * fraction), ty + 5, hull);
        ty += 5 + 4;

        TelState state = tel.getTelState();
        int colour = TelUi.stateColour(state);
        Component chip = state == TelState.COUNTDOWN
                ? Component.translatable("gui.ata.tel.chip.countdown", TelUi.seconds(this.nextLaunch(tel)))
                : TelUi.stateName(state);
        Rect chipBox = new Rect(tx, ty, inner, 12);
        g.fill(chipBox.x(), chipBox.y(), chipBox.right(), chipBox.bottom(), 0xFF0E0F10);
        TelUi.frame(g, chipBox, colour);
        TelUi.textCentred(g, font, chip, chipBox, colour);
        ty += 12 + 4;

        for (int slot = 0; slot < 2; slot++) {
            this.renderPlate(g, font, tel, slot, new Rect(tx, ty, inner, 10));
            ty += 11;
        }

        if (crew) {
            ty += 1;
            boolean lit = tel.areHeadlightsOn();
            TelUi.text(g, font, Component.translatable("gui.ata.tel.hud.lights",
                    Component.translatable(lit ? "gui.ata.tel.hud.on" : "gui.ata.tel.hud.off")),
                    new Rect(tx, ty, inner, 10), lit ? TelUi.YELLOW : TelUi.DIM);
            ty += 11;
            TelUi.text(g, font, Component.translatable("gui.ata.tel.hint", ModKeys.TEL_FIRE_CONTROL.getTranslatedKeyMessage()),
                    new Rect(tx, ty, inner, 10), TelUi.DIM);
        }
    }

    private int nextLaunch(IskanderTelEntity tel) {
        int best = Integer.MAX_VALUE;
        for (int slot = 0; slot < 2; slot++) {
            int left = tel.getCountdownLeft(slot);
            if (left >= 0) {
                best = Math.min(best, left);
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private void renderPlate(GuiGraphics g, Font font, IskanderTelEntity tel, int slot, Rect box) {
        TelState state = tel.getTelState();
        boolean loaded = tel.isLoaded(slot);
        int left = tel.getCountdownLeft(slot);
        Component status;
        int colour;
        if (state == TelState.COUNTDOWN && left >= 0) {
            status = Component.translatable("gui.ata.tel.status.countdown", TelUi.seconds(left));
            colour = left < 40 ? TelUi.RED : TelUi.YELLOW;
        } else if (tel.isRaisedNow(slot)) {
            status = Component.translatable(loaded ? "gui.ata.tel.status.raised" : "gui.ata.tel.status.gone");
            colour = loaded ? TelUi.BLUE : TelUi.DIM;
        } else if (loaded) {
            status = Component.translatable("gui.ata.tel.status.loaded");
            colour = TelUi.GREEN;
        } else {
            status = Component.translatable("gui.ata.tel.status.empty");
            colour = TelUi.DIM;
        }

        String number = "№" + (slot + 1);
        g.drawString(font, number, box.x(), box.y(), TelUi.SECTION, false);
        int labelX = box.x() + font.width(number) + 4;

        String distance = "";
        if (loaded && tel.hasTarget(slot)) {
            try {
                TelLaunchRules.Aim aim = tel.aim(slot);
                distance = Component.translatable("gui.ata.tel.hud.metres", Math.round(aim.distance())).getString();
            } catch (IllegalStateException ignored) {
            }
        }
        int distW = font.width(distance);
        if (!distance.isEmpty()) {
            g.drawString(font, distance, box.right() - distW, box.y(), TelUi.TEXT, false);
        }
        TelUi.text(g, font, status, new Rect(labelX, box.y(), box.right() - labelX - distW - (distW > 0 ? 4 : 0), 10),
                colour);
    }

    private void renderTrace(GuiGraphics g, Font font, IskanderTelEntity tel, int x, int y, int width) {
        g.fill(x, y, x + width, y + 48, TelUi.PANEL);
        TelUi.frame(g, new Rect(x, y, width, 48), TelUi.CARD_BORDER);
        int tx = x + 5;
        int ty = y + 4;
        g.drawString(font, String.format(java.util.Locale.ROOT, "power %.2f   %.1f km/h", tel.engine.power,
                tel.getSpeedKmh()), tx, ty, TelUi.YELLOW, false);
        g.drawString(font, String.format(java.util.Locale.ROOT, "pitch %.1f   roll %.1f", tel.getXRot(), tel.getRoll()),
                tx, ty += 10, TelUi.YELLOW, false);
        g.drawString(font, String.format(java.util.Locale.ROOT, "wheels %d/8   skids %d/8", tel.getWheelContacts(),
                tel.getSkidSupports()), tx, ty += 10, TelUi.YELLOW, false);
        g.drawString(font, tel.isAirborne() ? "AIRBORNE" : "grounded", tx, ty + 10,
                tel.isAirborne() ? TelUi.RED : TelUi.GREEN, false);
    }
}
