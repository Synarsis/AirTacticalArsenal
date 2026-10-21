package com.synarsis.airtacticalarsenal.config;

public enum FxLighting {
    OFF(0, false, false, false, false, 0, 0),
    LOW(8, true, false, false, false, 2, 1),
    MEDIUM(16, true, true, false, false, 4, 1),
    HIGH(32, false, true, true, true, 8, 2);

    private final int maxLights;
    private final boolean halfResolution;
    private final boolean normals;
    private final boolean shadows;
    private final boolean haze;
    private final int blockShadowLights;
    private final int shadowRays;

    FxLighting(int maxLights, boolean halfResolution, boolean normals, boolean shadows, boolean haze,
            int blockShadowLights, int shadowRays) {
        this.maxLights = maxLights;
        this.halfResolution = halfResolution;
        this.normals = normals;
        this.shadows = shadows;
        this.haze = haze;
        this.blockShadowLights = blockShadowLights;
        this.shadowRays = shadowRays;
    }

    public int maxLights() {
        return this.maxLights;
    }

    public boolean halfResolution() {
        return this.halfResolution;
    }

    public boolean normals() {
        return this.normals;
    }

    public boolean shadows() {
        return this.shadows;
    }

    public boolean haze() {
        return this.haze;
    }

    public int blockShadowLights() {
        return this.blockShadowLights;
    }

    public int shadowRays() {
        return this.shadowRays;
    }
}
