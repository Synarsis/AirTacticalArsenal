package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.entity.patriot.M983Coupling;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity.CouplingState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class M983HudLines {

    public enum Tone {
        TEXT,
        BLUE,
        GREEN,
        YELLOW
    }

    public record Line(String key, Object[] args, Tone tone) {
    }

    private static final double CENTRED_METRES = 0.05;

    private M983HudLines() {
    }

    public static List<Line> lines(CouplingState state, @Nullable M983Coupling.Hint hint, boolean slow) {
        return lines(state, hint, slow, true);
    }

    public static List<Line> lines(CouplingState state, @Nullable M983Coupling.Hint hint, boolean slow,
            boolean trailerLoaded) {
        List<Line> lines = new ArrayList<>();
        if (state != CouplingState.FREE && !trailerLoaded) {
            lines.add(new Line("hud.ata.m983.trailer_unloaded", new Object[0], Tone.YELLOW));
            return lines;
        }
        switch (state) {
            case LOCKING -> lines.add(new Line("hud.ata.m983.locking", new Object[0], Tone.YELLOW));
            case COUPLED -> lines.add(new Line("hud.ata.m983.coupled", new Object[0], Tone.GREEN));
            case UNLOCKING -> lines.add(new Line("hud.ata.m983.unlocking", new Object[0], Tone.YELLOW));
            default -> {
                if (hint == null) {
                    lines.add(new Line("hud.ata.m983.free", new Object[0], Tone.BLUE));
                    break;
                }
                String distance = String.format(Locale.ROOT, "%.1f", hint.distance());
                String lateral = String.format(Locale.ROOT, "%.1f", Math.abs(hint.lateral()));
                String angle = String.format(Locale.ROOT, "%.0f", hint.angle());
                Tone tone = hint.ready() ? Tone.GREEN
                        : hint.verdict() == M983Coupling.Verdict.ANGLE || hint.verdict() == M983Coupling.Verdict.HEIGHT
                                ? Tone.YELLOW : Tone.TEXT;
                if (Math.abs(hint.lateral()) < CENTRED_METRES) {
                    lines.add(new Line("hud.ata.m983.hint.centre", new Object[] {distance, angle}, tone));
                } else {
                    lines.add(new Line(hint.lateral() > 0.0 ? "hud.ata.m983.hint.left" : "hud.ata.m983.hint.right",
                            new Object[] {distance, lateral, angle}, tone));
                }
                if (hint.ready()) {
                    lines.add(slow ? new Line("hud.ata.m983.ready", new Object[0], Tone.GREEN)
                            : new Line("hud.ata.m983.stop", new Object[0], Tone.YELLOW));
                }
            }
        }
        return lines;
    }
}
