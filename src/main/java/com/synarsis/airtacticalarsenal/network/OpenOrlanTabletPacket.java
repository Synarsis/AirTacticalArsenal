package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record OpenOrlanTabletPacket(
        List<BlockPos> launcherPositions,
        List<Boolean> hasDroneList,
        List<Float> launcherYawList,
        Map<Integer, int[]> savedCoords,
        List<int[]> activeOrlans,
        List<List<BlockPos>> savedRoutes
) implements CustomPacketPayload {

    public static final Type<OpenOrlanTabletPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "open_orlan_tablet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOrlanTabletPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.launcherPositions.size());
                for (int i = 0; i < p.launcherPositions.size(); i++) {
                    buf.writeBlockPos(p.launcherPositions.get(i));
                    buf.writeBoolean(i < p.hasDroneList.size() && p.hasDroneList.get(i));
                    buf.writeFloat(i < p.launcherYawList.size() ? p.launcherYawList.get(i) : 0.0f);
                }

                buf.writeInt(p.savedCoords.size());
                for (Map.Entry<Integer, int[]> entry : p.savedCoords.entrySet()) {
                    buf.writeInt(entry.getKey());
                    buf.writeInt(entry.getValue()[0]);
                    buf.writeInt(entry.getValue()[1]);
                }

                buf.writeInt(p.activeOrlans.size());
                for (int[] orlan : p.activeOrlans) {
                    buf.writeInt(orlan[0]);
                    buf.writeInt(orlan[1]);
                    buf.writeInt(orlan[2]);
                    buf.writeInt(orlan.length > 3 ? orlan[3] : 0);
                }

                buf.writeInt(p.savedRoutes.size());
                for (List<BlockPos> route : p.savedRoutes) {
                    buf.writeInt(route.size());
                    for (BlockPos wp : route) {
                        buf.writeBlockPos(wp);
                    }
                }
            },
            (buf) -> {
                int launcherCount = buf.readInt();
                List<BlockPos> launcherPositions = new ArrayList<>();
                List<Boolean> hasDroneList = new ArrayList<>();
                List<Float> launcherYawList = new ArrayList<>();
                for (int i = 0; i < launcherCount; i++) {
                    launcherPositions.add(buf.readBlockPos());
                    hasDroneList.add(buf.readBoolean());
                    launcherYawList.add(buf.readFloat());
                }

                int coordsCount = buf.readInt();
                Map<Integer, int[]> savedCoords = new HashMap<>();
                for (int i = 0; i < coordsCount; i++) {
                    int index = buf.readInt();
                    int x = buf.readInt();
                    int z = buf.readInt();
                    savedCoords.put(index, new int[]{x, z});
                }

                int orlanCount = buf.readInt();
                List<int[]> activeOrlans = new ArrayList<>();
                for (int i = 0; i < orlanCount; i++) {
                    activeOrlans.add(new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()});
                }

                int routeCount = buf.readInt();
                List<List<BlockPos>> savedRoutes = new ArrayList<>();
                for (int i = 0; i < routeCount; i++) {
                    int wpCount = buf.readInt();
                    List<BlockPos> route = new ArrayList<>();
                    for (int j = 0; j < wpCount; j++) {
                        route.add(buf.readBlockPos());
                    }
                    savedRoutes.add(route);
                }

                return new OpenOrlanTabletPacket(launcherPositions, hasDroneList, launcherYawList,
                        savedCoords, activeOrlans, savedRoutes);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenOrlanTabletPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.ClientScreens.openOrlanTablet(
                payload.launcherPositions(), payload.hasDroneList(), payload.launcherYawList(),
                payload.savedCoords(), payload.activeOrlans(), payload.savedRoutes()));
    }
}
