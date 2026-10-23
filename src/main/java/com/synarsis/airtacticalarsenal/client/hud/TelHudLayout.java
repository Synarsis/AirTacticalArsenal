package com.synarsis.airtacticalarsenal.client.hud;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;

public final class TelHudLayout {

    public static final int MARGIN = 6;
    public static final int PAD = 5;
    public static final int WIDTH = 132;
    public static final int MIN_WIDTH = 100;
    public static final int HOTBAR_HALF = 91;
    public static final int HOTBAR_HEIGHT = 24;
    public static final int SPEED_HEIGHT = 18 + 2;
    public static final int BAR_GAP = 3 + 4;
    public static final int HULL_GAP = 5 + 4;
    public static final int ROW = 11;

    private TelHudLayout() {
    }

    public static int width(int guiWidth) {
        return Math.min(WIDTH, Math.max(MIN_WIDTH, guiWidth / 3));
    }

    public static Rect panel(int guiWidth, int guiHeight, int height) {
        int width = width(guiWidth);
        boolean clear = MARGIN + width + MARGIN <= guiWidth / 2 - HOTBAR_HALF;
        int y = guiHeight - height - MARGIN - (clear ? 0 : HOTBAR_HEIGHT);
        return new Rect(MARGIN, y, width, height);
    }

    public static int inner(int width) {
        return width - 2 * PAD;
    }
}
