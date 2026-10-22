package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.ModKeys;
import com.synarsis.airtacticalarsenal.client.gui.PatriotPanelLayout.Layout;
import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotRefusal;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotStatus;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.PatriotCommandPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PatriotControlScreen extends Screen {

    private static final int TOOLTIP_WIDTH = 220;

    private final PatriotVehicleEntity vehicle;
    private Layout layout;
    private final Map<Command, TelButton> buttons = new EnumMap<>(Command.class);

    public PatriotControlScreen(PatriotVehicleEntity vehicle) {
        super(Component.translatable("gui.ata.patriot.title." + vehicle.kind().id()));
        this.vehicle = vehicle;
    }

    @Override
    protected void init() {
        this.layout = PatriotPanelLayout.compute(this.width, this.height, this.vehicle.kind().isLauncher());
        this.buttons.clear();
        for (Map.Entry<Command, Rect> entry : this.layout.buttons().entrySet()) {
            Command command = entry.getKey();
            boolean danger = command == Command.STOW;
            TelButton button = new TelButton(entry.getValue(), () -> this.labelOf(command), danger, () -> this.send(command));
            this.buttons.put(command, button);
            this.addRenderableWidget(button);
        }
        this.refresh();
    }

    private Component labelOf(Command command) {
        String name = command.name().toLowerCase(Locale.ROOT);
        if (command == Command.LIFT_UP || command == Command.LIFT_DOWN) {
            name += "." + (this.vehicle.kind().isLauncher() ? "launcher" : "radar");
        }
        return Component.translatable("gui.ata.patriot.button." + name);
    }

    private void send(Command command) {
        NetworkHandler.sendToServer(new PatriotCommandPacket(this.vehicle.getId(), (byte) command.ordinal()));
    }

    private void refresh() {
        for (Map.Entry<Command, TelButton> entry : this.buttons.entrySet()) {
            PatriotRefusal.Denial denial = this.vehicle.denial(entry.getKey());
            entry.getValue().setState(denial.ok(), denial.ok() ? null
                    : Component.translatable(denial.reason().messageKey(), denial.args()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null || this.vehicle.isRemoved()
                || !this.vehicle.inPanelRange(this.minecraft.player)) {
            this.onClose();
            return;
        }
        this.refresh();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xA0080808, 0xB0080808);
        Layout l = this.layout;
        g.fill(l.panel().x(), l.panel().y(), l.panel().right(), l.panel().bottom(), TelUi.PANEL);
        TelUi.frame(g, l.panel(), TelUi.BORDER);
        g.fill(l.header().x() - 4, l.header().y() - 4, l.header().right() + 4, l.header().bottom() + 1, TelUi.STRIP);
        TelUi.text(g, this.font, this.title, l.title(), TelUi.BORDER);

        PatriotSequence view = this.vehicle.seqView();
        double fraction = this.vehicle.operationFraction(partialTick);
        PatriotStatus.Line status = PatriotStatus.of(view, fraction, view.traverse());
        int colour = toneColour(status.tone());
        g.fill(l.chip().x(), l.chip().y() + 2, l.chip().right(), l.chip().bottom() - 1, 0xFF0E0F10);
        TelUi.frame(g, new Rect(l.chip().x(), l.chip().y() + 2, l.chip().w(), l.chip().h() - 3), colour);
        Component statusText = this.vehicle.isHitched() && this.vehicle.coupledTractor() == null
                ? Component.translatable("gui.ata.patriot.tractor_unloaded")
                : Component.translatable(status.key(), status.args());
        TelUi.textCentred(g, this.font, statusText, l.chip(), colour);

        g.fill(l.progress().x(), l.progress().y(), l.progress().right(), l.progress().bottom(), 0xFF26282B);
        if (fraction > 0.0) {
            g.fill(l.progress().x(), l.progress().y(), l.progress().x() + (int) Math.round(l.progress().w() * fraction),
                    l.progress().bottom(), colour);
        }

        if (this.vehicle.kind().isLauncher() && !l.traverse().isEmpty()) {
            Rect value = new Rect(l.traverse().x(), l.traverse().y() - 11, l.traverse().w(), 10);
            TelUi.textCentred(g, this.font, Component.translatable("gui.ata.patriot.traverse",
                    TelUi.number(view.traverse(), 0)), value, TelUi.DIM);
        }

        super.render(g, mouseX, mouseY, partialTick);
        this.drawTooltips(g, mouseX, mouseY);
    }

    static int toneColour(PatriotStatus.Tone tone) {
        return switch (tone) {
            case OK -> TelUi.GREEN;
            case INFO -> TelUi.BLUE;
            case WORKING -> TelUi.YELLOW;
            case WARN -> TelUi.RED;
        };
    }

    private void drawTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (TelButton b : this.buttons.values()) {
            if (!b.contains(mouseX, mouseY)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(b.currentLabel());
            if (!b.active && b.why() != null) {
                lines.addAll(TelUi.wrap(this.font, b.why().copy().withStyle(s -> s.withColor(TelUi.YELLOW)),
                        TOOLTIP_WIDTH));
            }
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            return;
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
