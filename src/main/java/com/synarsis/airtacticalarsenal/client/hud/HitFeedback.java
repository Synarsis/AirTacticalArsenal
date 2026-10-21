package com.synarsis.airtacticalarsenal.client.hud;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitPart;
import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.network.ShahedHitPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HitFeedback {

    private static final int NEUTRAL = 0xFFFFFFFF;
    private static final int HURT = 0xFFFFC93B;
    private static final int CRITICAL = 0xFFFF4C3B;
    private static final int RICOCHET = 0xFF9AA3AC;

    private static long shownAtTick;
    private static int colour = NEUTRAL;
    private static boolean knockedOut;
    private static String label = "";

    private HitFeedback() {
    }

    public static void onHit(ShahedHitPacket hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        shownAtTick = mc.level.getGameTime();
        HitFeedback.knockedOut = hit.knockedOut();

        if (!hit.penetrated()) {
            colour = RICOCHET;
            label = "ricochet";
        } else {
            colour = hit.healthPct() > 0.5f ? NEUTRAL : (hit.healthPct() > 0.2f ? HURT : CRITICAL);
            HitPart part = partOf(hit.partOrdinal());
            label = part != null ? part.id() : "";
        }

        if (AtaClientConfig.isHitMarkerSoundEnabled() && mc.player != null) {
            mc.player.playSound(hit.penetrated() ? SoundEvents.ARROW_HIT_PLAYER : SoundEvents.ANVIL_LAND,
                    hit.penetrated() ? 0.5f : 0.22f,
                    hit.penetrated() ? (hit.knockedOut() ? 1.6f : 1.2f) : 1.9f);
        }
    }

    private static HitPart partOf(int ordinal) {
        HitPart[] all = HitPart.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    public static void render(GuiGraphics graphics) {
        if (!AtaClientConfig.isHitMarkerEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long age = mc.level.getGameTime() - shownAtTick;
        if (age < 0 || age > AtaClientConfig.getHitMarkerTicks()) {
            return;
        }

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int reach = Math.round(6.0f * AtaClientConfig.getHitMarkerScale());
        int gap = Math.round(2.0f * AtaClientConfig.getHitMarkerScale());

        int outer = knockedOut ? reach + 3 : reach;
        stroke(graphics, cx - outer, cy - outer, cx - gap, cy - gap);
        stroke(graphics, cx + gap, cy - outer, cx + outer, cy - gap);
        stroke(graphics, cx - outer, cy + gap, cx - gap, cy + outer);
        stroke(graphics, cx + gap, cy + gap, cx + outer, cy + outer);

        if (AtaClientConfig.showPartLabels() && !label.isEmpty()) {
            Component text = Component.literal(label);
            int width = mc.font.width(text);
            graphics.drawString(mc.font, text, cx - width / 2, cy + outer + 4, colour, true);
        }
    }

    private static void stroke(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
