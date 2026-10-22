package com.synarsis.airtacticalarsenal.entity.patriot;

import java.util.List;
import java.util.Locale;

public final class PatriotStatus {

    public enum Tone {
        OK,
        INFO,
        WORKING,
        WARN
    }

    public record Line(String key, Object[] args, Tone tone) {
    }

    public static final List<String> KEYS = List.of("stowed", "supports", "raised", "antenna", "service",
            "supports_out", "supports_in", "raising", "lowering", "antenna_up", "antenna_down", "service_opening",
            "service_closing", "centering");

    private PatriotStatus() {
    }

    public static String keyOf(String name) {
        return "gui.ata.patriot.status." + name.toLowerCase(Locale.ROOT);
    }

    private static Line line(String name, Tone tone, Object... args) {
        return new Line(keyOf(name), args, tone);
    }

    public static Line of(PatriotSequence view, double fraction, double traverseDegrees) {
        PatriotSequence.Op op = view.current();
        int percent = (int) Math.round(fraction * 100.0);
        if (op != null) {
            String name = switch (op) {
                case SUPPORTS_OUT -> "supports_out";
                case SUPPORTS_IN -> "supports_in";
                case RAISE -> "raising";
                case LOWER -> "lowering";
                case ANTENNA_UP -> "antenna_up";
                case ANTENNA_DOWN -> "antenna_down";
                case SERVICE_OPEN -> "service_opening";
                case SERVICE_CLOSE -> "service_closing";
                case CENTER -> "centering";
            };
            return line(name, Tone.WORKING, percent);
        }
        if (view.lift()) {
            if (view.kind().isLauncher()) {
                return line("raised", Tone.INFO, String.format(Locale.ROOT, "%+.0f", traverseDegrees));
            }
            return line("antenna", Tone.INFO);
        }
        if (view.service()) {
            return line("service", Tone.INFO);
        }
        if (view.supports()) {
            return line("supports", Tone.INFO);
        }
        return line("stowed", Tone.OK);
    }
}
