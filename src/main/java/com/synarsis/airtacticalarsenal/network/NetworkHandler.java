package com.synarsis.airtacticalarsenal.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToClient(IskanderExplosionPacket.TYPE, IskanderExplosionPacket.STREAM_CODEC, IskanderExplosionPacket::handle);
        registrar.playToClient(IskanderRemovePacket.TYPE, IskanderRemovePacket.STREAM_CODEC, IskanderRemovePacket::handle);
        registrar.playToClient(IskanderSpawnPacket.TYPE, IskanderSpawnPacket.STREAM_CODEC, IskanderSpawnPacket::handle);
        registrar.playToClient(OrlanRemovePacket.TYPE, OrlanRemovePacket.STREAM_CODEC, OrlanRemovePacket::handle);
        registrar.playToClient(OrlanSpawnPacket.TYPE, OrlanSpawnPacket.STREAM_CODEC, OrlanSpawnPacket::handle);
        registrar.playToServer(OrlanCameraViewPacket.TYPE, OrlanCameraViewPacket.STREAM_CODEC, OrlanCameraViewPacket::handle);
        registrar.playToClient(OrlanCameraForceExitPacket.TYPE, OrlanCameraForceExitPacket.STREAM_CODEC, OrlanCameraForceExitPacket::handle);
        registrar.playToClient(ShahedSpawnPacket.TYPE, ShahedSpawnPacket.STREAM_CODEC, ShahedSpawnPacket::handle);
        registrar.playToClient(ShahedRemovePacket.TYPE, ShahedRemovePacket.STREAM_CODEC, ShahedRemovePacket::handle);
        registrar.playToClient(ShahedExplosionPacket.TYPE, ShahedExplosionPacket.STREAM_CODEC, ShahedExplosionPacket::handle);
        registrar.playToClient(HitboxDebugPacket.TYPE, HitboxDebugPacket.STREAM_CODEC, HitboxDebugPacket::handle);
        registrar.playToClient(FxDebugPacket.TYPE, FxDebugPacket.STREAM_CODEC, FxDebugPacket::handle);
        registrar.playToClient(TelTracePacket.TYPE, TelTracePacket.STREAM_CODEC, TelTracePacket::handle);
        registrar.playToServer(TelImpactPacket.TYPE, TelImpactPacket.STREAM_CODEC, TelImpactPacket::handle);
        registrar.playToServer(TelRollPacket.TYPE, TelRollPacket.STREAM_CODEC, TelRollPacket::handle);
        registrar.playToClient(VehicleSpecPacket.TYPE, VehicleSpecPacket.STREAM_CODEC, VehicleSpecPacket::handle);
        registrar.playToServer(PatriotCommandPacket.TYPE, PatriotCommandPacket.STREAM_CODEC, PatriotCommandPacket::handle);
        registrar.playToServer(PatriotPosePacket.TYPE, PatriotPosePacket.STREAM_CODEC, PatriotPosePacket::handle);
        registrar.playToServer(PatriotImpactPacket.TYPE, PatriotImpactPacket.STREAM_CODEC, PatriotImpactPacket::handle);
        registrar.playToServer(M983CommandPacket.TYPE, M983CommandPacket.STREAM_CODEC, M983CommandPacket::handle);
        registrar.playToServer(TelCommandPacket.TYPE, TelCommandPacket.STREAM_CODEC, TelCommandPacket::handle);
        registrar.playToServer(TelSetTargetPacket.TYPE, TelSetTargetPacket.STREAM_CODEC, TelSetTargetPacket::handle);
        registrar.playToClient(DebugOverridesPacket.TYPE, DebugOverridesPacket.STREAM_CODEC, DebugOverridesPacket::handle);
        registrar.playToClient(ShahedHitPacket.TYPE, ShahedHitPacket.STREAM_CODEC, ShahedHitPacket::handle);
        registrar.playToClient(RadarUpdatePacket.TYPE, RadarUpdatePacket.STREAM_CODEC, RadarUpdatePacket::handle);
        registrar.playToClient(WorldMapPacket.TYPE, WorldMapPacket.STREAM_CODEC, WorldMapPacket::handle);
        registrar.playToClient(SirenSoundPacket.TYPE, SirenSoundPacket.STREAM_CODEC, SirenSoundPacket::handle);
        registrar.playToClient(OpenTerminalScreenPacket.TYPE, OpenTerminalScreenPacket.STREAM_CODEC, OpenTerminalScreenPacket::handle);
        registrar.playToClient(OpenRadarScreenPacket.TYPE, OpenRadarScreenPacket.STREAM_CODEC, OpenRadarScreenPacket::handle);
        registrar.playToClient(OpenShahedTabletPacket.TYPE, OpenShahedTabletPacket.STREAM_CODEC, OpenShahedTabletPacket::handle);
        registrar.playToClient(OpenOrlanTabletPacket.TYPE, OpenOrlanTabletPacket.STREAM_CODEC, OpenOrlanTabletPacket::handle);
        registrar.playToClient(RadarMapDataPacket.TYPE, RadarMapDataPacket.STREAM_CODEC, RadarMapDataPacket::handle);
        registrar.playToClient(RouteMapDataPacket.TYPE, RouteMapDataPacket.STREAM_CODEC, RouteMapDataPacket::handle);
        registrar.playToClient(SurfaceHeightPacket.TYPE, SurfaceHeightPacket.STREAM_CODEC, SurfaceHeightPacket::handle);
        registrar.playToClient(SegmentMaxHeightPacket.TYPE, SegmentMaxHeightPacket.STREAM_CODEC, SegmentMaxHeightPacket::handle);

        registrar.playToServer(RequestRadarUpdatePacket.TYPE, RequestRadarUpdatePacket.STREAM_CODEC, RequestRadarUpdatePacket::handle);
        registrar.playToServer(LaunchShahedFromTerminalPacket.TYPE, LaunchShahedFromTerminalPacket.STREAM_CODEC, LaunchShahedFromTerminalPacket::handle);
        registrar.playToServer(ChangeShahedAltitudePacket.TYPE, ChangeShahedAltitudePacket.STREAM_CODEC, ChangeShahedAltitudePacket::handle);
        registrar.playToServer(OrlanAltitudePacket.TYPE, OrlanAltitudePacket.STREAM_CODEC, OrlanAltitudePacket::handle);
        registrar.playToServer(RequestWorldMapPacket.TYPE, RequestWorldMapPacket.STREAM_CODEC, RequestWorldMapPacket::handle);

        registrar.playToServer(SaveShahedRoutePacket.TYPE, SaveShahedRoutePacket.STREAM_CODEC, SaveShahedRoutePacket::handle);
        registrar.playToServer(SaveOrlanRoutePacket.TYPE, SaveOrlanRoutePacket.STREAM_CODEC, SaveOrlanRoutePacket::handle);
        registrar.playToServer(LaunchShahedByRoutePacket.TYPE, LaunchShahedByRoutePacket.STREAM_CODEC, LaunchShahedByRoutePacket::handle);
        registrar.playToServer(LaunchOrlanFromTabletPacket.TYPE, LaunchOrlanFromTabletPacket.STREAM_CODEC, LaunchOrlanFromTabletPacket::handle);
        registrar.playToServer(RemoveLauncherFromTabletPacket.TYPE, RemoveLauncherFromTabletPacket.STREAM_CODEC, RemoveLauncherFromTabletPacket::handle);
        registrar.playToServer(SaveTabletCoordsPacket.TYPE, SaveTabletCoordsPacket.STREAM_CODEC, SaveTabletCoordsPacket::handle);
        registrar.playToServer(OrlanReturnPacket.TYPE, OrlanReturnPacket.STREAM_CODEC, OrlanReturnPacket::handle);
        registrar.playToServer(UpdateOrlanRoutePacket.TYPE, UpdateOrlanRoutePacket.STREAM_CODEC, UpdateOrlanRoutePacket::handle);
        registrar.playToServer(RequestRouteMapPacket.TYPE, RequestRouteMapPacket.STREAM_CODEC, RequestRouteMapPacket::handle);
        registrar.playToServer(RequestRadarMapPacket.TYPE, RequestRadarMapPacket.STREAM_CODEC, RequestRadarMapPacket::handle);
        registrar.playToServer(RequestSurfaceHeightPacket.TYPE, RequestSurfaceHeightPacket.STREAM_CODEC, RequestSurfaceHeightPacket::handle);
        registrar.playToServer(RequestSegmentMaxHeightPacket.TYPE, RequestSegmentMaxHeightPacket.STREAM_CODEC, RequestSegmentMaxHeightPacket::handle);
    }
}
