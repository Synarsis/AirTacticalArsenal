package com.synarsis.airtacticalarsenal;

import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import com.synarsis.airtacticalarsenal.client.renderer.IskanderRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.IskanderRocketBlockRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.OrlanLauncherRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.OrlanRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.PaintStandRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.ShahedLauncherRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.ShahedRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.IskanderItemRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.OrlanItemRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.OrlanLauncherItemRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.PaintStandItemRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.ShahedItemRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.item.SprayCanItemRenderer;
import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.particle.DebrisParticle;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@Mod(value = AirTacticalArsenal.MODID, dist = Dist.CLIENT)
public class AirTacticalArsenalClient {
    public AirTacticalArsenalClient(ModContainer container) {
    }

    @EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SHAHED.get(),          ShahedRenderer::new);
            event.registerEntityRenderer(ModEntities.ISKANDER.get(),        IskanderRenderer::new);
            event.registerEntityRenderer(ModEntities.ORLAN.get(),           OrlanRenderer::new);
            event.registerEntityRenderer(ModEntities.SHAHED_LAUNCHER.get(), ShahedLauncherRenderer::new);
            event.registerEntityRenderer(ModEntities.ORLAN_LAUNCHER.get(),  OrlanLauncherRenderer::new);
            event.registerEntityRenderer(ModEntities.PAINT_STAND.get(),     PaintStandRenderer::new);

            event.registerBlockEntityRenderer(ModBlockEntities.ISKANDER_ROCKET.get(), IskanderRocketBlockRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                private final IskanderItemRenderer r = new IskanderItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.ISKANDER.get());

            event.registerItem(new IClientItemExtensions() {
                private final OrlanItemRenderer r = new OrlanItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.ORLAN.get());

            event.registerItem(new IClientItemExtensions() {
                private final OrlanLauncherItemRenderer r = new OrlanLauncherItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.ORLAN_LAUNCHER.get());

            event.registerItem(new IClientItemExtensions() {
                private final PaintStandItemRenderer r = new PaintStandItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.PAINT_STAND.get());

            event.registerItem(new IClientItemExtensions() {
                private final ShahedItemRenderer r = new ShahedItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.SHAHED.get());

            event.registerItem(new IClientItemExtensions() {
                private final SprayCanItemRenderer r = new SprayCanItemRenderer();
                @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return r; }
            }, ModItems.SPRAY_CAN.get());
        }

        @SubscribeEvent
        public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
        }
    }
}
