package com.synarsis.airtacticalarsenal.vehicle.physics;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.network.VehicleSpecPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID)
public final class VehicleEvents {

    private VehicleEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        VehicleSpecs.loadFromDisk(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new VehicleSpecPacket(VehicleSpecs.currentJson()));
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = event.getExplosion().center();
        float power = event.getExplosion().radius();
        double reach = power * 12.0 + 12.0;
        AABB area = new AABB(centre, centre).inflate(reach);
        for (Entity entity : level.getEntitiesOfClass(IskanderTelEntity.class, area)) {
            ((IskanderTelEntity) entity).pushByExplosion(centre, power);
        }
    }
}
