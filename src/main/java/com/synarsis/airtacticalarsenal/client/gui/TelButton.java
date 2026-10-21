package com.synarsis.airtacticalarsenal.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class TelButton extends AbstractWidget {

    private final Supplier<Component> label;
    private final Runnable action;
    private final boolean danger;
    @Nullable
    private Component why;
    private int accent = -1;

    public TelButton(TelPanelLayout.Rect r, Supplier<Component> label, boolean danger, Runnable action) {
        super(r.x(), r.y(), r.w(), r.h(), Component.empty());
        this.label = label;
        this.action = action;
        this.danger = danger;
    }

    public void setState(boolean usable, @Nullable Component why) {
        this.active = usable;
        this.why = usable ? null : why;
    }

    public void setAccent(int colour) {
        this.accent = colour;
    }

    @Nullable
    public Component why() {
        return this.why;
    }

    public Component currentLabel() {
        return this.label.get();
    }

    public boolean contains(int mx, int my) {
        return this.visible && mx >= this.getX() && mx < this.getX() + this.width
                && my >= this.getY() && my < this.getY() + this.height;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = this.active && this.contains(mouseX, mouseY);
        int fill;
        int edge;
        int text;
        if (!this.active) {
            fill = this.danger ? 0xFF2A1A1A : 0xFF202124;
            edge = 0xFF2C2E33;
            text = 0xFF6A6C70;
        } else if (this.danger) {
            fill = hot ? 0xFF8A2A2A : 0xFF6A2222;
            edge = 0xFFFF5C5C;
            text = 0xFFFFFFFF;
        } else {
            fill = hot ? 0xFF3A3D42 : 0xFF2B2D31;
            edge = hot ? TelUi.BORDER : 0xFF45484E;
            text = TelUi.TEXT;
        }
        if (this.active && this.accent != -1) {
            text = this.accent;
        }
        TelPanelLayout.Rect r = new TelPanelLayout.Rect(this.getX(), this.getY(), this.width, this.height);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        TelUi.frame(g, r, edge);
        TelUi.textCentred(g, Minecraft.getInstance().font, this.label.get(), r, text);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active) {
            this.action.run();
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        handler.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
