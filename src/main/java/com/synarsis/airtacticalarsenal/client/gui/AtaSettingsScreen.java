package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

@OnlyIn(Dist.CLIENT)
public class AtaSettingsScreen extends Screen {

    private static final int ROW = 24;

    private final Screen parent;

    public AtaSettingsScreen(Screen parent) {
        super(Component.translatable("gui.ata.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 155;
        int wide = 310;
        int half = 152;
        int y = 44;

        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isHitMarkerEnabled())
                .create(x, y, wide, 20, Component.translatable("gui.ata.settings.hit_marker"),
                        (button, value) -> set(AtaClientConfig.HIT_MARKER_ENABLED, value)));
        y += ROW;

        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isHitMarkerSoundEnabled())
                .create(x, y, half, 20, Component.translatable("gui.ata.settings.hit_sound"),
                        (button, value) -> set(AtaClientConfig.HIT_MARKER_SOUND, value)));
        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.showPartLabels())
                .create(x + half + 6, y, half, 20, Component.translatable("gui.ata.settings.part_labels"),
                        (button, value) -> set(AtaClientConfig.PART_LABELS, value)));
        y += ROW;

        addRenderableWidget(Button.builder(
                        scaleLabel(), button -> {
                            cycleScale();
                            button.setMessage(scaleLabel());
                        })
                .bounds(x, y, half, 20).build());
        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isHitboxOverlayEnabled())
                .create(x + half + 6, y, half, 20, Component.translatable("gui.ata.settings.hitbox_overlay"),
                        (button, value) -> {
                            AtaClientConfig.setHitboxOverlay(value);
                            com.synarsis.airtacticalarsenal.client.debug.HitboxDebugRenderer.setEnabled(value);
                        }));

        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isFxHeadlightBeamsEnabled())
                .create(x, y, half, 20, Component.translatable("gui.ata.settings.headlight_beams"),
                        (button, value) -> set(AtaClientConfig.FX_HEADLIGHT_BEAMS, value)));
        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isFxBloomEnabled())
                .create(x + half + 6, y, half, 20, Component.translatable("gui.ata.settings.bloom"),
                        (button, value) -> set(AtaClientConfig.FX_BLOOM, value)));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(AtaClientConfig.isFxGlareEnabled())
                .create(x, y, wide, 20, Component.translatable("gui.ata.settings.glare"),
                        (button, value) -> set(AtaClientConfig.FX_GLARE, value)));

        addRenderableWidget(Button.builder(CommonComponentsDone(), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    private static Component CommonComponentsDone() {
        return Component.translatable("gui.done");
    }

    private Component scaleLabel() {
        return Component.translatable("gui.ata.settings.marker_scale",
                String.format("%.1fx", AtaClientConfig.getHitMarkerScale()));
    }

    private void cycleScale() {
        double next = Math.round((AtaClientConfig.getHitMarkerScale() + 0.5) * 10.0) / 10.0;
        if (next > 2.5) {
            next = 0.5;
        }
        AtaClientConfig.HIT_MARKER_SCALE.set(next);
        AtaClientConfig.HIT_MARKER_SCALE.save();
    }

    private static void set(ModConfigSpec.BooleanValue value, boolean state) {
        value.set(state);
        value.save();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);

        int y = this.height - 68;
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.ata.settings.server_section"), this.width / 2, y, 0xFFFF55);
        y += 12;
        graphics.drawCenteredString(this.font, Component.literal(
                        "shootdown " + onOff(ShahedConfig.isShahedShootdownEnabled())
                                + "   hp " + (int) ShahedConfig.getShahedMaxHealth()
                                + "   fallen warhead " + onOff(ShahedConfig.doesFallenWarheadDetonate())),
                this.width / 2, y, 0xAAAAAA);
        y += 11;
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.ata.settings.server_hint"), this.width / 2, y, 0x777777);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
