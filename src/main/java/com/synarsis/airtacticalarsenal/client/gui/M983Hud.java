package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.client.hud.TelHudLayout;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelHeadlights;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class M983Hud {

    private static final int PAD = TelHudLayout.PAD;

    private M983Hud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !AtaClientConfig.isTelHudEnabled()) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof M983TractorEntity tractor)
                || tractor.getControllingPassenger() != mc.player) {
            return;
        }
        Font font = mc.font;
        List<M983HudLines.Line> lines = M983HudLines.lines(tractor.couplingState(), tractor.couplingHint(),
                tractor.slowForCoupling(), tractor.trailerLoaded());
        int height = PAD + TelHudLayout.SPEED_HEIGHT + TelHudLayout.BAR_GAP + TelHudLayout.HULL_GAP
                + TelHudLayout.ROW + 10 * lines.size() + PAD - 2;
        Rect place = TelHudLayout.panel(g.guiWidth(), g.guiHeight(), height);
        int x = place.x();
        int y = place.y();
        g.fill(x, y, place.right(), place.bottom(), TelUi.PANEL);
        TelUi.frame(g, place, TelUi.BORDER);

        int tx = x + PAD;
        int inner = TelHudLayout.inner(place.w());
        int ty = y + PAD;

        String speed = Integer.toString(Math.round(tractor.getSpeedKmh()));
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, speed, 0, 0, TelUi.TEXT, false);
        g.pose().popPose();
        g.drawString(font, Component.translatable("hud.ata.m983.kmh"), tx + font.width(speed) * 2 + 4, ty + 9,
                TelUi.DIM, false);
        ty += TelHudLayout.SPEED_HEIGHT;
        String gear = tractor.getSpeedKmh() < -0.5f ? "R" : tractor.getGear() == 0 ? "N" : Integer.toString(tractor.getGear());
        String drive = gear + "  " + Math.round(tractor.getEngineRpm() / 10.0) * 10 + " rpm";
        g.drawString(font, drive, tx + inner - font.width(drive), ty + 9, TelUi.DIM, false);

        float load = Mth.clamp(Math.abs((float) tractor.getEngineLoad()), 0.0f, 1.0f);
        g.fill(tx, ty, tx + inner, ty + 3, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * load), ty + 3, load > 0.85f ? TelUi.YELLOW : TelUi.GREEN);
        ty += TelHudLayout.BAR_GAP;

        float fraction = Mth.clamp(tractor.getHealth() / tractor.getMaxHealth(), 0.0f, 1.0f);
        int hull = fraction > 0.5f ? TelUi.GREEN : (fraction > 0.2f ? TelUi.YELLOW : TelUi.RED);
        g.fill(tx, ty, tx + inner, ty + 5, 0xFF26282B);
        g.fill(tx, ty, tx + Math.round(inner * fraction), ty + 5, hull);
        ty += TelHudLayout.HULL_GAP;

        TelUi.text(g, font, Component.translatable("gui.ata.tel.hud.lights",
                Component.translatable(TelHeadlights.modeKey(tractor.headlightMode()))),
                new Rect(tx, ty, inner, 10), tractor.areHeadlightsOn() ? TelUi.YELLOW : TelUi.DIM);
        ty += TelHudLayout.ROW;
        for (M983HudLines.Line line : lines) {
            TelUi.text(g, font, Component.translatable(line.key(), line.args()), new Rect(tx, ty, inner, 10),
                    colour(line.tone()));
            ty += 10;
        }
    }

    private static int colour(M983HudLines.Tone tone) {
        return switch (tone) {
            case BLUE -> TelUi.BLUE;
            case GREEN -> TelUi.GREEN;
            case YELLOW -> TelUi.YELLOW;
            default -> TelUi.TEXT;
        };
    }
}
