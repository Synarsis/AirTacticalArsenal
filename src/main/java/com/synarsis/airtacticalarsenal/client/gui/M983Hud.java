package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class M983Hud {

    private static final int MARGIN = 6;
    private static final int WIDTH = 150;

    private M983Hud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !AtaClientConfig.isTelHudEnabled()) {
            return;
        }
        if (!(minecraft.player.getVehicle() instanceof M983TractorEntity tractor)
                || tractor.getControllingPassenger() != minecraft.player) {
            return;
        }
        int x = (g.guiWidth() - WIDTH) / 2;
        int bottom = g.guiHeight() - 22 - MARGIN;
        int top = bottom - 68;
        Rect panel = new Rect(x, top, WIDTH, bottom - top);
        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), TelUi.PANEL);
        TelUi.frame(g, panel, TelUi.BORDER);

        int speed = Math.round(Math.abs(tractor.getSpeedKmh()));
        String gear = tractor.getSpeedKmh() < -0.5f ? "R" : tractor.getGear() == 0 ? "N" : Integer.toString(tractor.getGear());
        Rect title = new Rect(x + MARGIN, panel.y() + 5, WIDTH - 2 * MARGIN, 10);
        TelUi.text(g, minecraft.font, Component.translatable("hud.ata.m983.title"), title, TelUi.SECTION);

        Rect speedBox = new Rect(x + MARGIN, panel.y() + 18, 54, 19);
        TelUi.text(g, minecraft.font, Component.literal(speed + " ").append(Component.translatable("hud.ata.m983.kmh")),
                speedBox, TelUi.TEXT);

        Rect gearBox = new Rect(x + 66, panel.y() + 18, 24, 19);
        TelUi.text(g, minecraft.font, Component.literal(gear), gearBox,
                "R".equals(gear) ? TelUi.YELLOW : TelUi.GREEN);

        Rect rpmBox = new Rect(x + 96, panel.y() + 18, WIDTH - MARGIN - 96, 19);
        TelUi.textRight(g, minecraft.font,
                Component.literal(String.valueOf(Math.round(tractor.getEngineRpm()))), rpmBox, TelUi.DIM);

        TelUi.text(g, minecraft.font, Component.translatable("gui.ata.tel.hud.lights",
                Component.translatable(com.synarsis.airtacticalarsenal.entity.tel.TelHeadlights.modeKey(tractor.headlightMode()))),
                new Rect(x + MARGIN, panel.y() + 37, WIDTH - 2 * MARGIN, 10), TelUi.DIM);
        int rowY = panel.y() + 47;
        for (M983HudLines.Line line : M983HudLines.lines(tractor.couplingState(), tractor.couplingHint(),
                tractor.slowForCoupling(), tractor.trailerLoaded())) {
            Rect row = new Rect(x + MARGIN, rowY, WIDTH - 2 * MARGIN, 10);
            TelUi.text(g, minecraft.font, Component.translatable(line.key(), line.args()), row, colour(line.tone()));
            rowY += 10;
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
