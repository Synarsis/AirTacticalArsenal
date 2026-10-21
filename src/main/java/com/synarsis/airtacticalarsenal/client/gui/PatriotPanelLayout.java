package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.client.gui.TelPanelLayout.Rect;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;

import java.util.EnumMap;
import java.util.Map;

public final class PatriotPanelLayout {

    public static final int MAX_WIDTH = 320;
    public static final int PAD = TelPanelLayout.PAD;
    public static final int GAP = TelPanelLayout.GAP;
    public static final int BUTTON_H = TelPanelLayout.BUTTON_H;
    public static final int BIG_BUTTON_H = 22;
    public static final int LINE_H = TelPanelLayout.LINE_H;

    public record Layout(Rect panel, Rect header, Rect title, Rect chip, Rect progress, Rect status, Rect hint,
            Rect traverse, Map<Command, Rect> buttons) {
    }

    private PatriotPanelLayout() {
    }

    public static Layout compute(int screenW, int screenH, boolean launcher) {
        int pw = Math.min(MAX_WIDTH, screenW - 16);
        int rows = 4;
        int ph = PAD + 16 + 3 + GAP + LINE_H + LINE_H + GAP + rows * (BUTTON_H + GAP) + BIG_BUTTON_H + PAD;
        int px = (screenW - pw) / 2;
        int py = (screenH - ph) / 2;
        Rect panel = new Rect(px, py, pw, ph);
        int x0 = px + PAD;
        int iw = pw - 2 * PAD;
        int y = py + PAD;

        Rect header = new Rect(x0, y, iw, 16);
        int chipW = Math.min(150, iw / 2);
        Rect chip = new Rect(header.right() - chipW, y, chipW, header.h());
        Rect title = new Rect(x0, y, iw - chipW - GAP, header.h());
        y += header.h();
        Rect progress = new Rect(x0, y, iw, 3);
        y += progress.h() + GAP;
        Rect status = new Rect(x0, y, iw, LINE_H);
        y += LINE_H;
        Rect hint = new Rect(x0, y, iw, LINE_H);
        y += LINE_H + GAP;

        Map<Command, Rect> buttons = new EnumMap<>(Command.class);
        Rect traverse = new Rect(0, 0, 0, 0);

        int half = (iw - GAP) / 2;
        buttons.put(Command.SUPPORTS_OUT, new Rect(x0, y, half, BUTTON_H));
        buttons.put(Command.SUPPORTS_IN, new Rect(x0 + iw - half, y, half, BUTTON_H));
        y += BUTTON_H + GAP;

        buttons.put(Command.LIFT_UP, new Rect(x0, y, half, BUTTON_H));
        buttons.put(Command.LIFT_DOWN, new Rect(x0 + iw - half, y, half, BUTTON_H));
        y += BUTTON_H + GAP;

        if (launcher) {
            int third = (iw - 2 * GAP) / 3;
            buttons.put(Command.TRAVERSE_LEFT, new Rect(x0, y, third, BUTTON_H));
            buttons.put(Command.TRAVERSE_CENTER, new Rect(x0 + third + GAP, y, third, BUTTON_H));
            buttons.put(Command.TRAVERSE_RIGHT, new Rect(x0 + iw - third, y, third, BUTTON_H));
            traverse = buttons.get(Command.TRAVERSE_CENTER);
        } else {
            buttons.put(Command.SERVICE_OPEN, new Rect(x0, y, half, BUTTON_H));
            buttons.put(Command.SERVICE_CLOSE, new Rect(x0 + iw - half, y, half, BUTTON_H));
        }
        y += BUTTON_H + GAP;

        buttons.put(Command.DETACH, new Rect(x0, y, half, BUTTON_H));
        buttons.put(Command.ATTACH, new Rect(x0 + iw - half, y, half, BUTTON_H));
        y += BUTTON_H + GAP;

        buttons.put(Command.DEPLOY, new Rect(x0, y, half, BIG_BUTTON_H));
        buttons.put(Command.STOW, new Rect(x0 + iw - half, y, half, BIG_BUTTON_H));

        return new Layout(panel, header, title, chip, progress, status, hint, traverse, buttons);
    }
}
