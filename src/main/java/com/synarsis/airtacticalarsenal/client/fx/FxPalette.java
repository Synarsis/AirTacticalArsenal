package com.synarsis.airtacticalarsenal.client.fx;

import java.util.Locale;

public final class FxPalette {

    public enum Ground {
        SAND,
        DIRT,
        STONE,
        SNOW
    }

    private FxPalette() {
    }

    public static Ground kindOf(String blockPath) {
        String name = blockPath.toLowerCase(Locale.ROOT);
        if (name.contains("snow") || name.contains("ice") || name.contains("powder")) {
            return Ground.SNOW;
        }
        if (name.contains("sand") || name.contains("terracotta")) {
            return Ground.SAND;
        }
        if (name.contains("stone") || name.contains("deepslate") || name.contains("cobble") || name.contains("andesite")
                || name.contains("granite") || name.contains("diorite") || name.contains("gravel")
                || name.contains("tuff") || name.contains("basalt") || name.contains("concrete")
                || name.contains("brick") || name.contains("ore") || name.contains("obsidian")) {
            return Ground.STONE;
        }
        return Ground.DIRT;
    }

    public static float[] cloudColour(Ground ground) {
        return switch (ground) {
            case SAND -> new float[] {0.80f, 0.72f, 0.52f};
            case DIRT -> new float[] {0.50f, 0.44f, 0.36f};
            case STONE -> new float[] {0.58f, 0.58f, 0.60f};
            case SNOW -> new float[] {0.93f, 0.95f, 0.98f};
        };
    }
}
