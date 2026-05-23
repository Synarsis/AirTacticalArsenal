package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class OpenShahedTabletPacket {

    private final List<BlockPos> launcherPositions;
    private final List<Boolean> hasDroneList;
    private final List<BlockPos> targetList;
    private final List<Float> launcherYawList;
    private final Map<Integer, int[]> savedCoords;
    private final List<List<BlockPos>> savedRoutes;

    private final List<int[]> flyingShahedsData;

    public OpenShahedTabletPacket(List<BlockPos> launcherPositions, 
                                   List<Boolean> hasDroneList,
                                   List<BlockPos> targetList,
                                   List<Float> launcherYawList,
                                   Map<Integer, int[]> savedCoords,
                                   List<List<BlockPos>> savedRoutes,
                                   List<int[]> flyingShahedsData) {
        this.launcherPositions = launcherPositions;
        this.hasDroneList = hasDroneList;
        this.targetList = targetList;
        this.launcherYawList = launcherYawList != null ? launcherYawList : new ArrayList<>();
        this.savedCoords = savedCoords != null ? savedCoords : new HashMap<>();
        this.savedRoutes = savedRoutes != null ? savedRoutes : new ArrayList<>();
        this.flyingShahedsData = flyingShahedsData != null ? flyingShahedsData : new ArrayList<>();
    }

    public OpenShahedTabletPacket(List<BlockPos> launcherPositions, 
                                   List<Boolean> hasDroneList,
                                   List<BlockPos> targetList,
                                   List<Float> launcherYawList,
                                   Map<Integer, int[]> savedCoords,
                                   List<List<BlockPos>> savedRoutes) {
        this(launcherPositions, hasDroneList, targetList, launcherYawList, savedCoords, savedRoutes, new ArrayList<>());
    }

    public OpenShahedTabletPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.launcherPositions = new ArrayList<>();
        this.hasDroneList = new ArrayList<>();
        this.targetList = new ArrayList<>();
        this.launcherYawList = new ArrayList<>();
        this.savedCoords = new HashMap<>();
        this.savedRoutes = new ArrayList<>();
        this.flyingShahedsData = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            launcherPositions.add(buf.readBlockPos());
            hasDroneList.add(buf.readBoolean());
            launcherYawList.add(buf.readFloat());
            boolean hasTarget = buf.readBoolean();
            if (hasTarget) {
                targetList.add(buf.readBlockPos());
            } else {
                targetList.add(null);
            }
            boolean hasCoords = buf.readBoolean();
            if (hasCoords) {
                savedCoords.put(i, new int[]{buf.readInt(), buf.readInt()});
            }

            int routeSize = buf.readVarInt();
            List<BlockPos> route = new ArrayList<>();
            for (int j = 0; j < routeSize; j++) {
                route.add(buf.readBlockPos());
            }
            savedRoutes.add(route);
        }

        int flyingCount = buf.readVarInt();
        for (int i = 0; i < flyingCount; i++) {
            flyingShahedsData.add(new int[]{
                buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt()
            });
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(launcherPositions.size());

        for (int i = 0; i < launcherPositions.size(); i++) {
            buf.writeBlockPos(launcherPositions.get(i));
            buf.writeBoolean(hasDroneList.get(i));
            buf.writeFloat(i < launcherYawList.size() ? launcherYawList.get(i) : 0.0f);

            BlockPos target = i < targetList.size() ? targetList.get(i) : null;
            buf.writeBoolean(target != null);
            if (target != null) {
                buf.writeBlockPos(target);
            }
            int[] coords = savedCoords.get(i);
            buf.writeBoolean(coords != null);
            if (coords != null) {
                buf.writeInt(coords[0]);
                buf.writeInt(coords[1]);
            }

            List<BlockPos> route = i < savedRoutes.size() ? savedRoutes.get(i) : new ArrayList<>();
            buf.writeVarInt(route.size());
            for (BlockPos wp : route) {
                buf.writeBlockPos(wp);
            }
        }

        buf.writeVarInt(flyingShahedsData.size());
        for (int[] data : flyingShahedsData) {
            buf.writeVarInt(data[0]); 
            buf.writeInt(data[1]);    
            buf.writeInt(data[2]);    
            buf.writeVarInt(data[3]); 
            buf.writeVarInt(data[4]); 
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(launcherPositions, hasDroneList, targetList, launcherYawList, savedCoords, savedRoutes, flyingShahedsData));
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(List<BlockPos> positions, List<Boolean> drones, 
                                      List<BlockPos> targets, List<Float> yawList, Map<Integer, int[]> coords,
                                      List<List<BlockPos>> routes, List<int[]> flyingShaheds) {
        com.synarsis.airtacticalarsenal.client.ClientPacketHandler.openShahedTabletScreen(positions, drones, targets, yawList, coords, routes, flyingShaheds);
    }
}
