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

public class OpenOrlanTabletPacket {
    private final List<BlockPos> launcherPositions;
    private final List<Boolean> hasDroneList;
    private final List<Float> launcherYawList;
    private final Map<Integer, int[]> savedCoords;

    private final List<int[]> activeOrlans; 

    private final List<List<BlockPos>> savedRoutes;

    public OpenOrlanTabletPacket(List<BlockPos> launcherPositions,
                                  List<Boolean> hasDroneList,
                                  List<Float> launcherYawList,
                                  Map<Integer, int[]> savedCoords,
                                  List<int[]> activeOrlans,
                                  List<List<BlockPos>> savedRoutes) {
        this.launcherPositions = launcherPositions;
        this.hasDroneList = hasDroneList;
        this.launcherYawList = launcherYawList;
        this.savedCoords = savedCoords != null ? savedCoords : new HashMap<>();
        this.activeOrlans = activeOrlans != null ? activeOrlans : new ArrayList<>();
        this.savedRoutes = savedRoutes != null ? savedRoutes : new ArrayList<>();
    }

    public OpenOrlanTabletPacket(FriendlyByteBuf buf) {
        int launcherCount = buf.readInt();
        this.launcherPositions = new ArrayList<>();
        this.hasDroneList = new ArrayList<>();
        this.launcherYawList = new ArrayList<>();
        for (int i = 0; i < launcherCount; i++) {
            this.launcherPositions.add(buf.readBlockPos());
            this.hasDroneList.add(buf.readBoolean());
            this.launcherYawList.add(buf.readFloat());
        }

        int coordsCount = buf.readInt();
        this.savedCoords = new HashMap<>();
        for (int i = 0; i < coordsCount; i++) {
            int index = buf.readInt();
            int x = buf.readInt();
            int z = buf.readInt();
            this.savedCoords.put(index, new int[]{x, z});
        }

        int orlanCount = buf.readInt();
        this.activeOrlans = new ArrayList<>();
        for (int i = 0; i < orlanCount; i++) {
            this.activeOrlans.add(new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()});
        }

        int routeCount = buf.readInt();
        this.savedRoutes = new ArrayList<>();
        for (int i = 0; i < routeCount; i++) {
            int wpCount = buf.readInt();
            List<BlockPos> route = new ArrayList<>();
            for (int j = 0; j < wpCount; j++) {
                route.add(buf.readBlockPos());
            }
            this.savedRoutes.add(route);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(launcherPositions.size());
        for (int i = 0; i < launcherPositions.size(); i++) {
            buf.writeBlockPos(launcherPositions.get(i));
            buf.writeBoolean(i < hasDroneList.size() && hasDroneList.get(i));
            buf.writeFloat(i < launcherYawList.size() ? launcherYawList.get(i) : 0.0f);
        }

        buf.writeInt(savedCoords.size());
        for (Map.Entry<Integer, int[]> entry : savedCoords.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeInt(entry.getValue()[0]);
            buf.writeInt(entry.getValue()[1]);
        }

        buf.writeInt(activeOrlans.size());
        for (int[] orlan : activeOrlans) {
            buf.writeInt(orlan[0]);
            buf.writeInt(orlan[1]);
            buf.writeInt(orlan[2]);
            buf.writeInt(orlan.length > 3 ? orlan[3] : 0);
        }

        buf.writeInt(savedRoutes.size());
        for (List<BlockPos> route : savedRoutes) {
            buf.writeInt(route.size());
            for (BlockPos wp : route) {
                buf.writeBlockPos(wp);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(launcherPositions, hasDroneList, launcherYawList, savedCoords, activeOrlans, savedRoutes));
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(List<BlockPos> positions, List<Boolean> drones,
                                      List<Float> yawList, Map<Integer, int[]> coords,
                                      List<int[]> activeOrlans, List<List<BlockPos>> savedRoutes) {
        com.synarsis.airtacticalarsenal.client.ClientPacketHandler.openOrlanTabletScreen(positions, drones, yawList, coords, activeOrlans, savedRoutes);
    }
}
