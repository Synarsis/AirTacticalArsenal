package com.synarsis.airtacticalarsenal.client.fx.light;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = AirTacticalArsenal.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LightShaders {

    private static volatile ShaderInstance volume;
    private static volatile ShaderInstance composite;
    private static volatile ShaderInstance haze;

    private LightShaders() {
    }

    public static ShaderInstance volume() {
        return volume;
    }

    public static ShaderInstance composite() {
        return composite;
    }

    public static ShaderInstance haze() {
        return haze;
    }

    public static boolean ready() {
        return volume != null && composite != null;
    }

    @SubscribeEvent
    public static void onRegister(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_volume"),
                    DefaultVertexFormat.POSITION), shader -> volume = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_composite"),
                    DefaultVertexFormat.POSITION), shader -> composite = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_heat_haze"),
                    DefaultVertexFormat.POSITION), shader -> haze = shader);
        } catch (IOException | RuntimeException e) {
            AirTacticalArsenal.LOGGER.error("The lighting shaders did not load; there will be no lighting pass", e);
        }
    }
}
