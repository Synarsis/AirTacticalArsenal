package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import com.synarsis.airtacticalarsenal.client.renderer.ShahedRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.ShahedLauncherRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.IskanderRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.IskanderRocketBlockRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.PaintStandRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.OrlanRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.OrlanLauncherRenderer;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.particle.DebrisParticle;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "ata", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHAHED.get(), ShahedRenderer::new);
        event.registerEntityRenderer(ModEntities.ISKANDER.get(), IskanderRenderer::new);
        event.registerEntityRenderer(ModEntities.SHAHED_LAUNCHER.get(), ShahedLauncherRenderer::new);
        event.registerEntityRenderer(ModEntities.PAINT_STAND.get(), PaintStandRenderer::new);
        event.registerEntityRenderer(ModEntities.ORLAN.get(), OrlanRenderer::new);
        event.registerEntityRenderer(ModEntities.ORLAN_LAUNCHER.get(), OrlanLauncherRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ISKANDER_ROCKET.get(), IskanderRocketBlockRenderer::new);
        System.out.println("[ATA] Renderers registered for ShahedEntity, IskanderEntity, ShahedLauncher, PaintStand, Orlan and IskanderRocketBlock!");
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        System.out.println("[ATA] Debris particle registered!");
    }
}
