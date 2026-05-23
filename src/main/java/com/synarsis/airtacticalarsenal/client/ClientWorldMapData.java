package com.synarsis.airtacticalarsenal.client;

import java.util.HashSet;
import java.util.Set;

public class ClientWorldMapData {
    private static final ClientWorldMapData INSTANCE = new ClientWorldMapData();

    private Set<Long> coastlinePoints = new HashSet<>();
    private int centerX;
    private int centerZ;
    private int radius;
    private boolean scanComplete;
    private int scanProgress;
    private int totalChunks;
    private long lastUpdateTime;

    private ClientWorldMapData() {}

    public static ClientWorldMapData getInstance() {
        return INSTANCE;
    }

    public void update(int centerX, int centerZ, int radius, Set<Long> points, 
                       boolean scanComplete, int scanProgress, int totalChunks) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.coastlinePoints = new HashSet<>(points);
        this.scanComplete = scanComplete;
        this.scanProgress = scanProgress;
        this.totalChunks = totalChunks;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public Set<Long> getCoastlinePoints() {
        return coastlinePoints;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public int getScanProgress() {
        return scanProgress;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public float getScanPercent() {
        if (totalChunks == 0) return 0;
        return (float) scanProgress / totalChunks * 100f;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean hasData() {
        return !coastlinePoints.isEmpty();
    }

    public void clear() {
        coastlinePoints.clear();
        lastUpdateTime = 0;
    }

    public static int unpackX(long packed) {
        return (int) packed;
    }

    public static int unpackZ(long packed) {
        return (int) (packed >> 32);
    }
}
