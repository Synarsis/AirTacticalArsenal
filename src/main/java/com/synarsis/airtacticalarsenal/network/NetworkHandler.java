package com.synarsis.airtacticalarsenal.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "6";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(com.synarsis.airtacticalarsenal.ShahedMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.messageBuilder(ShahedSpawnPacket.class, id++)
                .encoder(ShahedSpawnPacket::toBytes)
                .decoder(ShahedSpawnPacket::new)
                .consumerMainThread(ShahedSpawnPacket::handle)
                .add();
        INSTANCE.messageBuilder(ShahedRemovePacket.class, id++)
                .encoder(ShahedRemovePacket::toBytes)
                .decoder(ShahedRemovePacket::new)
                .consumerMainThread(ShahedRemovePacket::handle)
                .add();
        INSTANCE.messageBuilder(ShahedExplosionPacket.class, id++)
                .encoder(ShahedExplosionPacket::toBytes)
                .decoder(ShahedExplosionPacket::new)
                .consumerMainThread(ShahedExplosionPacket::handle)
                .add();
        INSTANCE.messageBuilder(OpenRadarScreenPacket.class, id++)
                .encoder(OpenRadarScreenPacket::toBytes)
                .decoder(OpenRadarScreenPacket::new)
                .consumerMainThread(OpenRadarScreenPacket::handle)
                .add();
        INSTANCE.messageBuilder(SirenSoundPacket.class, id++)
                .encoder(SirenSoundPacket::toBytes)
                .decoder(SirenSoundPacket::new)
                .consumerMainThread(SirenSoundPacket::handle)
                .add();
        INSTANCE.messageBuilder(RadarUpdatePacket.class, id++)
                .encoder(RadarUpdatePacket::toBytes)
                .decoder(RadarUpdatePacket::new)
                .consumerMainThread(RadarUpdatePacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestRadarUpdatePacket.class, id++)
                .encoder(RequestRadarUpdatePacket::toBytes)
                .decoder(RequestRadarUpdatePacket::new)
                .consumerMainThread(RequestRadarUpdatePacket::handle)
                .add();
        INSTANCE.messageBuilder(WorldMapPacket.class, id++)
                .encoder(WorldMapPacket::toBytes)
                .decoder(WorldMapPacket::new)
                .consumerMainThread(WorldMapPacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestWorldMapPacket.class, id++)
                .encoder(RequestWorldMapPacket::toBytes)
                .decoder(RequestWorldMapPacket::new)
                .consumerMainThread(RequestWorldMapPacket::handle)
                .add();
        INSTANCE.messageBuilder(IskanderSpawnPacket.class, id++)
                .encoder(IskanderSpawnPacket::toBytes)
                .decoder(IskanderSpawnPacket::new)
                .consumerMainThread(IskanderSpawnPacket::handle)
                .add();
        INSTANCE.messageBuilder(IskanderExplosionPacket.class, id++)
                .encoder(IskanderExplosionPacket::toBytes)
                .decoder(IskanderExplosionPacket::new)
                .consumerMainThread(IskanderExplosionPacket::handle)
                .add();
        INSTANCE.messageBuilder(IskanderRemovePacket.class, id++)
                .encoder(IskanderRemovePacket::toBytes)
                .decoder(IskanderRemovePacket::new)
                .consumerMainThread(IskanderRemovePacket::handle)
                .add();
        INSTANCE.messageBuilder(OpenUnifiedTerminalPacket.class, id++)
                .encoder(OpenUnifiedTerminalPacket::encode)
                .decoder(OpenUnifiedTerminalPacket::new)
                .consumerMainThread(OpenUnifiedTerminalPacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchFromUnifiedTerminalPacket.class, id++)
                .encoder(LaunchFromUnifiedTerminalPacket::encode)
                .decoder(LaunchFromUnifiedTerminalPacket::new)
                .consumerMainThread(LaunchFromUnifiedTerminalPacket::handle)
                .add();
        INSTANCE.messageBuilder(RocketLaunchStatePacket.class, id++)
                .encoder(RocketLaunchStatePacket::encode)
                .decoder(RocketLaunchStatePacket::new)
                .consumerMainThread(RocketLaunchStatePacket::handle)
                .add();
        INSTANCE.messageBuilder(InstallRocketPacket.class, id++)
                .encoder(InstallRocketPacket::encode)
                .decoder(InstallRocketPacket::new)
                .consumerMainThread(InstallRocketPacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchRocketPacket.class, id++)
                .encoder(LaunchRocketPacket::encode)
                .decoder(LaunchRocketPacket::new)
                .consumerMainThread(LaunchRocketPacket::handle)
                .add();
        INSTANCE.messageBuilder(RemoveRocketPacket.class, id++)
                .encoder(RemoveRocketPacket::encode)
                .decoder(RemoveRocketPacket::new)
                .consumerMainThread(RemoveRocketPacket::handle)
                .add();
        INSTANCE.messageBuilder(LauncherStateUpdatePacket.class, id++)
                .encoder(LauncherStateUpdatePacket::encode)
                .decoder(LauncherStateUpdatePacket::new)
                .consumerMainThread(LauncherStateUpdatePacket::handle)
                .add();
        INSTANCE.messageBuilder(SetLauncherTargetPacket.class, id++)
                .encoder(SetLauncherTargetPacket::encode)
                .decoder(SetLauncherTargetPacket::new)
                .consumerMainThread(SetLauncherTargetPacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchShahedFromTabletPacket.class, id++)
                .encoder(LaunchShahedFromTabletPacket::encode)
                .decoder(LaunchShahedFromTabletPacket::new)
                .consumerMainThread(LaunchShahedFromTabletPacket::handle)
                .add();
        INSTANCE.messageBuilder(OpenShahedTabletPacket.class, id++)
                .encoder(OpenShahedTabletPacket::encode)
                .decoder(OpenShahedTabletPacket::new)
                .consumerMainThread(OpenShahedTabletPacket::handle)
                .add();
        INSTANCE.messageBuilder(RemoveLauncherFromTabletPacket.class, id++)
                .encoder(RemoveLauncherFromTabletPacket::encode)
                .decoder(RemoveLauncherFromTabletPacket::new)
                .consumerMainThread(RemoveLauncherFromTabletPacket::handle)
                .add();
        INSTANCE.messageBuilder(SaveTabletCoordsPacket.class, id++)
                .encoder(SaveTabletCoordsPacket::encode)
                .decoder(SaveTabletCoordsPacket::new)
                .consumerMainThread(SaveTabletCoordsPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanSpawnPacket.class, id++)
                .encoder(OrlanSpawnPacket::toBytes)
                .decoder(OrlanSpawnPacket::new)
                .consumerMainThread(OrlanSpawnPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanRemovePacket.class, id++)
                .encoder(OrlanRemovePacket::toBytes)
                .decoder(OrlanRemovePacket::new)
                .consumerMainThread(OrlanRemovePacket::handle)
                .add();
        INSTANCE.messageBuilder(OpenOrlanTabletPacket.class, id++)
                .encoder(OpenOrlanTabletPacket::encode)
                .decoder(OpenOrlanTabletPacket::new)
                .consumerMainThread(OpenOrlanTabletPacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchOrlanFromTabletPacket.class, id++)
                .encoder(LaunchOrlanFromTabletPacket::encode)
                .decoder(LaunchOrlanFromTabletPacket::new)
                .consumerMainThread(LaunchOrlanFromTabletPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanCameraViewPacket.class, id++)
                .encoder(OrlanCameraViewPacket::encode)
                .decoder(OrlanCameraViewPacket::new)
                .consumerMainThread(OrlanCameraViewPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanCameraForceExitPacket.class, id++)
                .encoder(OrlanCameraForceExitPacket::encode)
                .decoder(OrlanCameraForceExitPacket::new)
                .consumerMainThread(OrlanCameraForceExitPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanReturnPacket.class, id++)
                .encoder(OrlanReturnPacket::encode)
                .decoder(OrlanReturnPacket::new)
                .consumerMainThread(OrlanReturnPacket::handle)
                .add();
        INSTANCE.messageBuilder(OrlanAltitudePacket.class, id++)
                .encoder(OrlanAltitudePacket::encode)
                .decoder(OrlanAltitudePacket::new)
                .consumerMainThread(OrlanAltitudePacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchShahedRoutePacket.class, id++)
                .encoder(LaunchShahedRoutePacket::encode)
                .decoder(LaunchShahedRoutePacket::new)
                .consumerMainThread(LaunchShahedRoutePacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestRadarMapPacket.class, id++)
                .encoder(RequestRadarMapPacket::encode)
                .decoder(RequestRadarMapPacket::new)
                .consumerMainThread(RequestRadarMapPacket::handle)
                .add();
        INSTANCE.messageBuilder(RadarMapDataPacket.class, id++)
                .encoder(RadarMapDataPacket::encode)
                .decoder(RadarMapDataPacket::new)
                .consumerMainThread(RadarMapDataPacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestRouteMapPacket.class, id++)
                .encoder(RequestRouteMapPacket::encode)
                .decoder(RequestRouteMapPacket::new)
                .consumerMainThread(RequestRouteMapPacket::handle)
                .add();
        INSTANCE.messageBuilder(RouteMapDataPacket.class, id++)
                .encoder(RouteMapDataPacket::encode)
                .decoder(RouteMapDataPacket::new)
                .consumerMainThread(RouteMapDataPacket::handle)
                .add();
        INSTANCE.messageBuilder(SaveShahedRoutePacket.class, id++)
                .encoder(SaveShahedRoutePacket::encode)
                .decoder(SaveShahedRoutePacket::new)
                .consumerMainThread(SaveShahedRoutePacket::handle)
                .add();
        INSTANCE.messageBuilder(LaunchShahedByRoutePacket.class, id++)
                .encoder(LaunchShahedByRoutePacket::encode)
                .decoder(LaunchShahedByRoutePacket::new)
                .consumerMainThread(LaunchShahedByRoutePacket::handle)
                .add();
        INSTANCE.messageBuilder(SaveOrlanRoutePacket.class, id++)
                .encoder(SaveOrlanRoutePacket::encode)
                .decoder(SaveOrlanRoutePacket::new)
                .consumerMainThread(SaveOrlanRoutePacket::handle)
                .add();
        INSTANCE.messageBuilder(UpdateOrlanRoutePacket.class, id++)
                .encoder(UpdateOrlanRoutePacket::encode)
                .decoder(UpdateOrlanRoutePacket::new)
                .consumerMainThread(UpdateOrlanRoutePacket::handle)
                .add();
        INSTANCE.messageBuilder(ChangeShahedAltitudePacket.class, id++)
                .encoder(ChangeShahedAltitudePacket::encode)
                .decoder(ChangeShahedAltitudePacket::new)
                .consumerMainThread(ChangeShahedAltitudePacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestSurfaceHeightPacket.class, id++)
                .encoder(RequestSurfaceHeightPacket::encode)
                .decoder(RequestSurfaceHeightPacket::new)
                .consumerMainThread(RequestSurfaceHeightPacket::handle)
                .add();
        INSTANCE.messageBuilder(SurfaceHeightPacket.class, id++)
                .encoder(SurfaceHeightPacket::encode)
                .decoder(SurfaceHeightPacket::new)
                .consumerMainThread(SurfaceHeightPacket::handle)
                .add();
        INSTANCE.messageBuilder(RequestSegmentMaxHeightPacket.class, id++)
                .encoder(RequestSegmentMaxHeightPacket::encode)
                .decoder(RequestSegmentMaxHeightPacket::new)
                .consumerMainThread(RequestSegmentMaxHeightPacket::handle)
                .add();
        INSTANCE.messageBuilder(SegmentMaxHeightPacket.class, id++)
                .encoder(SegmentMaxHeightPacket::encode)
                .decoder(SegmentMaxHeightPacket::new)
                .consumerMainThread(SegmentMaxHeightPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(ServerPlayer player, MSG message) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToAllPlayers(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}
