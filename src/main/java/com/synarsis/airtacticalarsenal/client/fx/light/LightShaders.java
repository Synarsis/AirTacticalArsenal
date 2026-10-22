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
    private static volatile ShaderInstance glare;
    private static volatile ShaderInstance excess;
    private static volatile ShaderInstance filter;
    private static volatile ShaderInstance beam;

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

    public static ShaderInstance glare() {
        return glare;
    }

    public static ShaderInstance excess() {return excess;}

    public static ShaderInstance filter() {return filter;}

    public static ShaderInstance beam() {return beam;}

    public static boolean ready() {
        return volume != null && composite != null && glare != null && excess != null && filter != null && beam != null;
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
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_glare"),
                    DefaultVertexFormat.POSITION), shader -> glare = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_excess"),
                    DefaultVertexFormat.POSITION), shader -> excess = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_filter"),
                    DefaultVertexFormat.POSITION), shader -> filter = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "ata_light_beam"),
                    DefaultVertexFormat.POSITION), shader -> beam = shader);
        } catch (IOException | RuntimeException e) {
            AirTacticalArsenal.LOGGER.error("The lighting shaders did not load; there will be no lighting pass", e);
        }
    }
}
