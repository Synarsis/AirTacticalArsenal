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

public record OpenShahedTabletPacket(
        List<BlockPos> launcherPositions,
        List<Boolean> hasDroneList,
        List<BlockPos> targetList,
        List<Float> launcherYawList,
        Map<Integer, int[]> savedCoords,
        List<List<BlockPos>> savedRoutes,
        List<int[]> flyingShahedsData
) implements CustomPacketPayload {

    public static final Type<OpenShahedTabletPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "open_shahed_tablet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShahedTabletPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int size = p.launcherPositions.size();
                buf.writeInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeBlockPos(p.launcherPositions.get(i));
                    buf.writeBoolean(p.hasDroneList.get(i));
                    buf.writeFloat(i < p.launcherYawList.size() ? p.launcherYawList.get(i) : 0.0f);

                    BlockPos target = i < p.targetList.size() ? p.targetList.get(i) : null;
                    buf.writeBoolean(target != null);
                    if (target != null) {
                        buf.writeBlockPos(target);
                    }

                    int[] coords = p.savedCoords.get(i);
                    buf.writeBoolean(coords != null);
                    if (coords != null) {
                        buf.writeInt(coords[0]);
                        buf.writeInt(coords[1]);
                    }

                    List<BlockPos> route = i < p.savedRoutes.size() ? p.savedRoutes.get(i) : new ArrayList<>();
                    buf.writeVarInt(route.size());
                    for (BlockPos wp : route) {
                        buf.writeBlockPos(wp);
                    }
                }

                buf.writeVarInt(p.flyingShahedsData.size());
                for (int[] data : p.flyingShahedsData) {
                    buf.writeVarInt(data[0]);
                    buf.writeInt(data[1]);
                    buf.writeInt(data[2]);
                    buf.writeVarInt(data[3]);
                    buf.writeVarInt(data[4]);
                }
            },
            (buf) -> {
                int size = buf.readInt();
                List<BlockPos> launcherPositions = new ArrayList<>();
                List<Boolean> hasDroneList = new ArrayList<>();
                List<BlockPos> targetList = new ArrayList<>();
                List<Float> launcherYawList = new ArrayList<>();
                Map<Integer, int[]> savedCoords = new HashMap<>();
                List<List<BlockPos>> savedRoutes = new ArrayList<>();
                List<int[]> flyingShahedsData = new ArrayList<>();

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

                return new OpenShahedTabletPacket(launcherPositions, hasDroneList, targetList, launcherYawList,
                        savedCoords, savedRoutes, flyingShahedsData);
            }
    );

    public OpenShahedTabletPacket(List<BlockPos> launcherPositions,
                                  List<Boolean> hasDroneList,
                                  List<BlockPos> targetList,
                                  List<Float> launcherYawList,
                                  Map<Integer, int[]> savedCoords,
                                  List<List<BlockPos>> savedRoutes) {
        this(launcherPositions, hasDroneList, targetList,
                launcherYawList != null ? launcherYawList : new ArrayList<>(),
                savedCoords != null ? savedCoords : new HashMap<>(),
                savedRoutes != null ? savedRoutes : new ArrayList<>(),
                new ArrayList<>());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenShahedTabletPacket payload, IPayloadContext context) {
        // Client-only handler. Payload handlers are registered on both sides, and a dedicated
        // server that loads a class referencing client types aborts mod loading entirely -- the
        // server does not start at all. Guard before touching anything client-side.
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.ClientScreens.openShahedTablet(
                payload.launcherPositions(), payload.hasDroneList(), payload.targetList(),
                payload.launcherYawList(), payload.savedCoords(), payload.savedRoutes(),
                payload.flyingShahedsData()));
    }
}
