package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.ModKeys;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.network.M983CommandPacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class M983ControlScreen extends Screen {

    private static final int WIDTH = 280;
    private static final int PAD = TelPanelLayout.PAD;
    private static final int LINE_H = TelPanelLayout.LINE_H;
    private static final int GAP = TelPanelLayout.GAP;

    private final M983TractorEntity tractor;
    private Rect panel;
    private Rect health;
    private Rect coupling;
    private TelButton unlock;
    private TelButton trailerPanel;

    public M983ControlScreen(M983TractorEntity tractor) {
        super(Component.translatable("gui.ata.m983.title"));
        this.tractor = tractor;
    }

    @Override
    protected void init() {
        int width = Math.min(WIDTH, this.width - 16);
        int height = PAD + 16 + GAP + LINE_H + 5 + GAP + LINE_H + GAP + 20 + GAP + 20 + PAD;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;
        this.panel = new Rect(x, y, width, height);
        int innerX = x + PAD;
        int innerW = width - 2 * PAD;
        int ty = y + PAD + 16 + GAP;
        this.health = new Rect(innerX, ty, innerW, 5);
        ty += 5 + GAP;
        this.coupling = new Rect(innerX, ty, innerW, LINE_H);
        ty += LINE_H + GAP;
        this.unlock = new TelButton(new Rect(innerX, ty, innerW, 20),
                () -> Component.translatable("gui.ata.m983.unlock"), true, () -> NetworkHandler.sendToServer(
                        new M983CommandPacket(this.tractor.getId(), M983TractorEntity.CMD_UNLOCK)));
        this.addRenderableWidget(this.unlock);
        ty += 20 + GAP;
        this.trailerPanel = new TelButton(new Rect(innerX, ty, innerW, 20),
                () -> Component.translatable("gui.ata.m983.trailer_panel"), false, this::openTrailerPanel);
        this.addRenderableWidget(this.trailerPanel);
        this.refresh();
    }

    private void openTrailerPanel() {
        PatriotVehicleEntity trailer = this.tractor.coupledTrailer();
        if (trailer != null && this.minecraft != null) {
            this.minecraft.setScreen(new PatriotControlScreen(trailer));
        }
    }

    private void refresh() {
        String why = this.tractor.uncoupleReasonKey();
        this.unlock.setState(why == null, why == null ? null : Component.translatable(why));
        boolean coupled = this.tractor.couplingState() == M983TractorEntity.CouplingState.COUPLED
                && this.tractor.coupledTrailer() != null;
        this.trailerPanel.setState(coupled, Component.translatable("gui.ata.m983.trailer_panel.why"));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null || this.tractor.isRemoved()
                || this.minecraft.player.distanceToSqr(this.tractor) > M983TractorEntity.PANEL_RANGE
                        * M983TractorEntity.PANEL_RANGE) {
            this.onClose();
            return;
        }
        this.refresh();
    }

    private Component couplingText() {
        return switch (this.tractor.couplingState()) {
            case LOCKING -> Component.translatable("gui.ata.m983.coupling.locking");
            case UNLOCKING -> Component.translatable("gui.ata.m983.coupling.unlocking");
            case COUPLED -> {
                PatriotVehicleEntity trailer = this.tractor.coupledTrailer();
                yield trailer == null ? Component.translatable("gui.ata.m983.coupling.locking")
                        : Component.translatable("gui.ata.m983.coupling.coupled", trailer.getType().getDescription());
            }
            default -> Component.translatable("gui.ata.m983.coupling.free");
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xA0080808, 0xB0080808);
        g.fill(this.panel.x(), this.panel.y(), this.panel.right(), this.panel.bottom(), TelUi.PANEL);
        TelUi.frame(g, this.panel, TelUi.BORDER);
        Rect header = new Rect(this.panel.x() + PAD, this.panel.y() + PAD, this.panel.w() - 2 * PAD, 16);
        TelUi.text(g, this.font, this.title, header, TelUi.BORDER);

        float fraction = Math.max(0.0f, Math.min(1.0f,
                this.tractor.getHealth() / Math.max(1.0f, this.tractor.getMaxHealth())));
        int hull = fraction > 0.5f ? TelUi.GREEN : fraction > 0.2f ? TelUi.YELLOW : TelUi.RED;
        g.fill(this.health.x(), this.health.y(), this.health.right(), this.health.bottom(), 0xFF26282B);
        g.fill(this.health.x(), this.health.y(),
                this.health.x() + Math.round(this.health.w() * fraction), this.health.bottom(), hull);
        Rect healthText = new Rect(this.health.x(), this.health.y() + 6, this.health.w(), 10);
        TelUi.textRight(g, this.font, Component.translatable("gui.ata.m983.health",
                Math.round(this.tractor.getHealth()), Math.round(this.tractor.getMaxHealth())), healthText, TelUi.DIM);

        TelUi.text(g, this.font, this.couplingText(), this.coupling,
                this.tractor.couplingState() == M983TractorEntity.CouplingState.COUPLED ? TelUi.GREEN : TelUi.BLUE);
        super.render(g, mouseX, mouseY, partialTick);
        for (TelButton b : new TelButton[] {this.unlock, this.trailerPanel}) {
            if (b.contains(mouseX, mouseY) && !b.active && b.why() != null) {
                java.util.List<Component> lines = new java.util.ArrayList<>();
                lines.add(b.currentLabel());
                lines.addAll(TelUi.wrap(this.font, b.why().copy().withStyle(s -> s.withColor(TelUi.YELLOW)), 220));
                g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.TEL_FIRE_CONTROL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
