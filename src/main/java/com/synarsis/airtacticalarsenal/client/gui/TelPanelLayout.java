package com.synarsis.airtacticalarsenal.client.gui;

public final class TelPanelLayout {

    public static final int MAX_WIDTH = 460;
    public static final int MAX_HEIGHT = 300;
    public static final int PAD = 8;
    public static final int GAP = 4;
    public static final int BUTTON_H = 18;
    public static final int FIELD_H = 16;
    public static final int LINE_H = 11;
    public static final int CHECK_COUNT = 9;
    public static final int COMPACT_WIDTH = 420;
    public static final int COMPACT_HEIGHT = 280;

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return this.x + this.w;
        }

        public int bottom() {
            return this.y + this.h;
        }

        public boolean isEmpty() {
            return this.w <= 0 || this.h <= 0;
        }

        public boolean overlaps(Rect o) {
            return !this.isEmpty() && !o.isEmpty()
                    && this.x < o.right() && o.x < this.right() && this.y < o.bottom() && o.y < this.bottom();
        }

        public boolean contains(Rect o) {
            return o.x >= this.x && o.y >= this.y && o.right() <= this.right() && o.bottom() <= this.bottom();
        }

        public boolean contains(double px, double py) {
            return px >= this.x && px < this.right() && py >= this.y && py < this.bottom();
        }
    }

    public record Card(Rect box, Rect title, Rect status, Rect xField, Rect zField, Rect ok, Rect info,
            Rect error, Rect load, Rect unload, Rect prepare) {
    }

    public record Layout(boolean compact, Rect panel, Rect header, Rect title, Rect chip, Rect progress,
            Card[] cards, Rect copy, Rect tablet, Rect salvoBox, Rect salvoLabel, Rect minus, Rect value, Rect plus,
            Rect both, Rect checksView, int checkColumns, int checkRowHeight, Rect[] bottom) {

        public int checkRows() {
            return (CHECK_COUNT + this.checkColumns - 1) / this.checkColumns;
        }

        public int checksContentHeight() {
            return this.checkRows() * this.checkRowHeight;
        }

        public int maxScroll() {
            return Math.max(0, this.checksContentHeight() - this.checksView.h());
        }

        public Rect checkCell(int index, int scroll) {
            int col = index % this.checkColumns;
            int row = index / this.checkColumns;
            int colW = (this.checksView.w() - GAP * (this.checkColumns - 1)) / this.checkColumns;
            return new Rect(this.checksView.x() + col * (colW + GAP), this.checksView.y() + row * this.checkRowHeight - scroll,
                    colW, this.checkRowHeight);
        }

        public boolean checkVisible(int index, int scroll) {
            return this.checksView.contains(this.checkCell(index, scroll));
        }
    }

    private TelPanelLayout() {
    }

    public static Layout compute(int screenW, int screenH) {
        int pw = Math.min(MAX_WIDTH, screenW - 16);
        int ph = Math.min(MAX_HEIGHT, screenH - 16);
        boolean compact = pw < COMPACT_WIDTH || ph < COMPACT_HEIGHT;
        int px = (screenW - pw) / 2;
        int py = (screenH - ph) / 2;
        Rect panel = new Rect(px, py, pw, ph);

        int x0 = px + PAD;
        int iw = pw - 2 * PAD;
        int y = py + PAD;

        Rect header = new Rect(x0, y, iw, 16);
        int chipW = Math.min(compact ? 128 : 150, iw / 2);
        Rect chip = new Rect(header.right() - chipW, y, chipW, header.h());
        Rect title = new Rect(x0, y, iw - chipW - GAP, header.h());
        y += header.h();
        Rect progress = new Rect(x0, y, iw, 3);
        y += progress.h() + GAP;

        Card[] cards = new Card[2];
        Rect copy;
        if (compact) {
            int h = 4 + BUTTON_H + 2 + BUTTON_H + 4;
            cards[0] = compactCard(x0, y, iw, h);
            cards[1] = compactCard(x0, y + h + GAP, iw, h);
            y += 2 * h + GAP + GAP;
            copy = null;
        } else {
            int gutter = 22;
            int cw = (iw - gutter) / 2;
            int h = 4 + 12 + 2 + FIELD_H + 2 + 10 + 10 + 2 + BUTTON_H + 4;
            cards[0] = fullCard(x0, y, cw, h);
            cards[1] = fullCard(x0 + iw - cw, y, cw, h);
            copy = new Rect(x0 + cw + (gutter - BUTTON_H) / 2, y + h / 2 - BUTTON_H / 2, BUTTON_H, BUTTON_H);
            y += h + GAP;
        }

        Rect salvoBox = new Rect(x0, y, iw, BUTTON_H);
        int tabletW = compact ? 86 : 100;
        int bothW = compact ? 110 : 140;
        Rect tablet = new Rect(salvoBox.right() - tabletW, y, tabletW, BUTTON_H);
        Rect both = new Rect(tablet.x() - GAP - bothW, y, bothW, BUTTON_H);
        int labelW = compact ? 38 : 70;
        Rect salvoLabel = new Rect(x0, y, labelW, BUTTON_H);
        Rect minus = new Rect(salvoLabel.right() + GAP, y, BUTTON_H, BUTTON_H);
        Rect value = new Rect(minus.right() + GAP, y, compact ? 32 : 40, BUTTON_H);
        Rect plus = new Rect(value.right() + GAP, y, BUTTON_H, BUTTON_H);
        if (compact) {
            int copyW = Math.min(40, both.x() - GAP - (plus.right() + GAP));
            copy = new Rect(plus.right() + GAP, y, Math.max(copyW, 0), BUTTON_H);
        }
        y += BUTTON_H + GAP;

        int bh = compact ? BUTTON_H : 20;
        int by = py + ph - PAD - bh;
        Rect[] bottom = bottomRow(x0, by, iw, bh);

        int columns = compact ? 3 : 2;
        int rowH = compact ? LINE_H : 13;
        int viewTop = compact ? y : y + LINE_H;
        int viewH = Math.max(0, by - GAP - viewTop);
        Rect checksView = new Rect(x0, viewTop, iw, viewH);

        return new Layout(compact, panel, header, title, chip, progress, cards, copy, tablet, salvoBox, salvoLabel,
                minus, value, plus, both, checksView, columns, rowH, bottom);
    }

    public static Rect checksLabel(Layout layout) {
        return layout.compact() ? new Rect(0, 0, 0, 0)
                : new Rect(layout.checksView().x(), layout.checksView().y() - LINE_H, layout.checksView().w(), LINE_H);
    }

    private static Card fullCard(int x, int y, int w, int h) {
        Rect box = new Rect(x, y, w, h);
        int ix = x + 4;
        int iw = w - 8;
        int cy = y + 4;
        int statusW = 90;
        Rect title = new Rect(ix, cy, iw - statusW - GAP, 12);
        Rect status = new Rect(ix + iw - statusW, cy, statusW, 12);
        cy += 12 + 2;
        int okW = 18;
        int fw = (iw - okW - 2 * GAP) / 2;
        Rect xField = new Rect(ix, cy, fw, FIELD_H);
        Rect zField = new Rect(xField.right() + GAP, cy, fw, FIELD_H);
        Rect ok = new Rect(zField.right() + GAP, cy, okW, FIELD_H);
        cy += FIELD_H + 2;
        Rect info = new Rect(ix, cy, iw, 10);
        cy += 10;
        Rect error = new Rect(ix, cy, iw, 10);
        cy += 10 + 2;
        int bw = (iw - 2 * GAP) / 3;
        Rect load = new Rect(ix, cy, bw, BUTTON_H);
        Rect unload = new Rect(load.right() + GAP, cy, bw, BUTTON_H);
        Rect prepare = new Rect(unload.right() + GAP, cy, iw - 2 * bw - 2 * GAP, BUTTON_H);
        return new Card(box, title, status, xField, zField, ok, info, error, load, unload, prepare);
    }

    private static Card compactCard(int x, int y, int w, int h) {
        Rect box = new Rect(x, y, w, h);
        int ix = x + 4;
        int iw = w - 8;
        int ay = y + 4;
        int okW = 18;
        int titleW = 62;
        int statusW = 96;
        int fw = (iw - titleW - statusW - okW - 4 * GAP) / 2;
        Rect title = new Rect(ix, ay, titleW, BUTTON_H);
        Rect status = new Rect(title.right() + GAP, ay, statusW, BUTTON_H);
        Rect xField = new Rect(status.right() + GAP, ay + 1, fw, FIELD_H);
        Rect zField = new Rect(xField.right() + GAP, ay + 1, fw, FIELD_H);
        Rect ok = new Rect(zField.right() + GAP, ay + 1, iw - (zField.right() + GAP - ix), FIELD_H);
        int by = ay + BUTTON_H + 2;
        int infoW = 120;
        int bw = (iw - infoW - 3 * GAP) / 3;
        Rect info = new Rect(ix, by, infoW, BUTTON_H);
        Rect load = new Rect(info.right() + GAP, by, bw, BUTTON_H);
        Rect unload = new Rect(load.right() + GAP, by, bw, BUTTON_H);
        Rect prepare = new Rect(unload.right() + GAP, by, ix + iw - (unload.right() + GAP), BUTTON_H);
        return new Card(box, title, status, xField, zField, ok, info, new Rect(info.x(), info.y(), 0, 0), load, unload,
                prepare);
    }

    private static Rect[] bottomRow(int x, int y, int w, int h) {
        double[] weights = {1.0, 1.0, 1.0, 1.0, 1.5};
        double total = 0;
        for (double v : weights) {
            total += v;
        }
        int usable = w - GAP * (weights.length - 1);
        Rect[] out = new Rect[weights.length];
        int cx = x;
        for (int i = 0; i < weights.length; i++) {
            int bw = i == weights.length - 1 ? x + w - cx : (int) Math.floor(usable * weights[i] / total);
            out[i] = new Rect(cx, y, bw, h);
            cx += bw + GAP;
        }
        return out;
    }
}
