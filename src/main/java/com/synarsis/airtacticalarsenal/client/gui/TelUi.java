package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.entity.tel.TelLaunchRules;
import com.synarsis.airtacticalarsenal.entity.tel.TelState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class TelUi {

    public static final int PANEL = 0xE60E0F10;
    public static final int BORDER = 0xFFFF7A1A;
    public static final int STRIP = 0xFF1C1D20;
    public static final int CARD = 0xFF16171A;
    public static final int CARD_BORDER = 0xFF2C2E33;
    public static final int SECTION = 0xFFFFA040;
    public static final int TEXT = 0xFFE6E6E6;
    public static final int DIM = 0xFF9A9A9A;
    public static final int GREEN = 0xFF55E07A;
    public static final int YELLOW = 0xFFFFD34D;
    public static final int RED = 0xFFFF5C5C;
    public static final int BLUE = 0xFF5CB8FF;

    private static final String ELLIPSIS = "…";

    private TelUi() {
    }

    public static String fit(Font font, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        int room = width - font.width(ELLIPSIS);
        if (room <= 0) {
            return "";
        }
        return font.plainSubstrByWidth(text, room) + ELLIPSIS;
    }

    public static String fit(Font font, Component text, int width) {
        return fit(font, text.getString(), width);
    }

    public static void text(GuiGraphics g, Font font, Component text, TelPanelLayout.Rect box, int colour) {
        if (box.isEmpty()) {
            return;
        }
        g.drawString(font, fit(font, text, box.w()), box.x(), box.y() + (box.h() - 8) / 2, colour, false);
    }

    public static void textRight(GuiGraphics g, Font font, Component text, TelPanelLayout.Rect box, int colour) {
        if (box.isEmpty()) {
            return;
        }
        String s = fit(font, text, box.w());
        g.drawString(font, s, box.right() - font.width(s), box.y() + (box.h() - 8) / 2, colour, false);
    }

    public static void textCentred(GuiGraphics g, Font font, Component text, TelPanelLayout.Rect box, int colour) {
        if (box.isEmpty()) {
            return;
        }
        String s = fit(font, text, box.w() - 4);
        g.drawString(font, s, box.x() + (box.w() - font.width(s)) / 2, box.y() + (box.h() - 8) / 2, colour, false);
    }

    public static void frame(GuiGraphics g, TelPanelLayout.Rect r, int colour) {
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, colour);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), colour);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), colour);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), colour);
    }

    public static List<Component> wrap(Font font, Component text, int width) {
        List<Component> out = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(text, width, Style.EMPTY)) {
            out.add(Component.literal(line.getString()).withStyle(text.getStyle()));
        }
        if (out.isEmpty()) {
            out.add(text);
        }
        return out;
    }

    public static String number(double value, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (s.startsWith("-") && Double.parseDouble(s) == 0.0) {
            s = s.substring(1);
        }
        return s;
    }

    public static Component reason(TelLaunchRules.Denial denial) {
        return Component.translatable(denial.reason().messageKey(), denial.args());
    }

    public static Component stateName(TelState state) {
        return Component.translatable("gui.ata.tel.state." + state.name().toLowerCase(Locale.ROOT));
    }

    public static int stateColour(TelState state) {
        return switch (state) {
            case STOWED -> GREEN;
            case COUNTDOWN -> RED;
            case RAISED -> BLUE;
            case COVERS_OPEN -> YELLOW;
            default -> YELLOW;
        };
    }

    public static int seconds(int ticks) {
        return (Math.max(ticks, 0) + 19) / 20;
    }
}
