package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RequestSegmentMaxHeightPacket;
import com.synarsis.airtacticalarsenal.network.RequestSurfaceHeightPacket;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SurfaceHeightCache {

    private static final Map<Long, Integer> heightCache = new HashMap<>();

    private static final Map<Integer, Integer> segmentHeightCache = new HashMap<>();
    private static final Map<Integer, int[]> segmentProfileCache = new HashMap<>();

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static void requestHeight(int x, int z, List<BlockPos> route, int waypointIndex) {
        long key = packXZ(x, z);
        Integer cached = heightCache.get(key);
        if (cached != null) {
            if (waypointIndex >= 0 && waypointIndex < route.size()) {
                BlockPos old = route.get(waypointIndex);
                route.set(waypointIndex, new BlockPos(old.getX(), cached, old.getZ()));
            }
            return;
        }
        NetworkHandler.sendToServer(new RequestSurfaceHeightPacket(x, z));
    }

    public static void updateHeight(int x, int z, int y) {
        heightCache.put(packXZ(x, z), y);

        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof ShahedRouteScreen srs) {
            srs.onHeightReceived(x, z, y);
        } else if (mc.screen instanceof OrlanRouteScreen ors) {
            ors.onHeightReceived(x, z, y);
        }
    }

    public static int getCachedHeight(int x, int z) {
        Integer h = heightCache.get(packXZ(x, z));
        return h != null ? h : -1;
    }

    public static void requestSegmentHeight(int x1, int z1, int x2, int z2, int segmentIndex) {
        NetworkHandler.sendToServer(new RequestSegmentMaxHeightPacket(x1, z1, x2, z2, segmentIndex));
    }

    public static void updateSegmentHeight(int segmentIndex, int maxY, int[] profile) {
        segmentHeightCache.put(segmentIndex, maxY);
        segmentProfileCache.put(segmentIndex, profile);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof ShahedRouteScreen srs) {
            srs.onSegmentHeightReceived(segmentIndex, maxY);
        } else if (mc.screen instanceof OrlanRouteScreen ors) {
            ors.onSegmentHeightReceived(segmentIndex, maxY);
        }
    }

    public static int getSegmentHeight(int segmentIndex) {
        Integer h = segmentHeightCache.get(segmentIndex);
        return h != null ? h : -1;
    }

    public static int[] getSegmentProfile(int segmentIndex) {
        return segmentProfileCache.get(segmentIndex);
    }

    public static void clear() {
        heightCache.clear();
        segmentHeightCache.clear();
        segmentProfileCache.clear();
    }
}
