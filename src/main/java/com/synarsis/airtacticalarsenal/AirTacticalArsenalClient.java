package com.synarsis.airtacticalarsenal;

import com.synarsis.airtacticalarsenal.client.renderer.IskanderRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.IskanderTelRenderer;
import com.synarsis.airtacticalarsenal.client.renderer.PatriotRenderer;
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
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mc, parent) -> new com.synarsis.airtacticalarsenal.client.gui.AtaSettingsScreen(parent));

        com.synarsis.airtacticalarsenal.entity.tel.TelInput.provider =
                com.synarsis.airtacticalarsenal.client.ClientTelInput::poll;
        com.synarsis.airtacticalarsenal.entity.tel.TelInput.clientTick =
                com.synarsis.airtacticalarsenal.client.TelClientEffects::tick;
        com.synarsis.airtacticalarsenal.entity.tel.TelInput.partialTick =
                () -> net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        com.synarsis.airtacticalarsenal.entity.tel.TelInput.traceSink =
                com.synarsis.airtacticalarsenal.client.TelClientEffects::trace;
        com.synarsis.airtacticalarsenal.entity.tel.TelInput.destroyedFx =
                com.synarsis.airtacticalarsenal.client.fx.ImpactFx::telDestroyed;
        com.synarsis.airtacticalarsenal.entity.patriot.PatriotInput.clientTick =
                com.synarsis.airtacticalarsenal.client.PatriotClientEffects::tick;
        com.synarsis.airtacticalarsenal.entity.patriot.PatriotInput.destroyedFx =
                com.synarsis.airtacticalarsenal.client.fx.ImpactFx::patriotDestroyed;
        com.synarsis.airtacticalarsenal.entity.IskanderEntity.clientFx =
                com.synarsis.airtacticalarsenal.client.fx.TrailFx::tick;
    }

    @EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onRegisterKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
            com.synarsis.airtacticalarsenal.client.ModKeys.register(event);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SHAHED.get(),          ShahedRenderer::new);
            event.registerEntityRenderer(ModEntities.ISKANDER.get(),        IskanderRenderer::new);
            event.registerEntityRenderer(ModEntities.ISKANDER_TEL.get(),    IskanderTelRenderer::new);
            event.registerEntityRenderer(ModEntities.PATRIOT_LAUNCHER.get(), PatriotRenderer::new);
            event.registerEntityRenderer(ModEntities.PATRIOT_RADAR.get(),    PatriotRenderer::new);
            event.registerEntityRenderer(ModEntities.ORLAN.get(),           OrlanRenderer::new);
            event.registerEntityRenderer(ModEntities.SHAHED_LAUNCHER.get(), ShahedLauncherRenderer::new);
            event.registerEntityRenderer(ModEntities.ORLAN_LAUNCHER.get(),  OrlanLauncherRenderer::new);
            event.registerEntityRenderer(ModEntities.PAINT_STAND.get(),     PaintStandRenderer::new);
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
        public static void onRegisterGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
            event.registerAboveAll(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "tel_hud"),
                    new com.synarsis.airtacticalarsenal.client.hud.TelHud());
            event.registerAboveAll(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "patriot_hud"),
                    new com.synarsis.airtacticalarsenal.client.hud.PatriotHud());
            event.registerAboveAll(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "fx_flash"),
                    new com.synarsis.airtacticalarsenal.client.fx.FxScreenFlash());
            event.registerAboveAll(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "light_debug"),
                    new com.synarsis.airtacticalarsenal.client.fx.light.LightDebug());
        }

        @SubscribeEvent
        public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.DEBRIS.get(), DebrisParticle.Provider::new);
            event.registerSpriteSet(ModParticles.SMOKE.get(), com.synarsis.airtacticalarsenal.particle.AtaSmokeParticle.Provider::new);
            event.registerSpriteSet(ModParticles.FLASH.get(), com.synarsis.airtacticalarsenal.particle.FlashParticle.Provider::new);
            event.registerSpriteSet(ModParticles.FIRE.get(), com.synarsis.airtacticalarsenal.particle.FireParticle.Provider::new);
            event.registerSpriteSet(ModParticles.SPARK.get(), com.synarsis.airtacticalarsenal.particle.SparkParticle.Provider::new);
            event.registerSpriteSet(ModParticles.SHOCK_RING.get(), com.synarsis.airtacticalarsenal.particle.ShockRingParticle.Provider::new);
            event.registerSpriteSet(ModParticles.HOT_DEBRIS.get(), com.synarsis.airtacticalarsenal.particle.HotDebrisParticle.Provider::new);
        }
    }
}
