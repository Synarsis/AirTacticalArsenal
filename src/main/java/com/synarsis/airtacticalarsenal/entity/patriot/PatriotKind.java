package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.resources.ResourceLocation;

public enum PatriotKind {

    LAUNCHER("patriot_launcher", "patriot_pac2_transport", "patriot_transport", "patriot_launcher.json", 500.0f),
    RADAR("patriot_radar", "patriot_anmpq65_transport", "patriot_anmpq65_transport", "patriot_radar.json", 450.0f);

    private final String id;
    private final String model;
    private final String texture;
    private final String specFile;
    private final float defaultHealth;

    PatriotKind(String id, String model, String texture, String specFile, float defaultHealth) {
        this.id = id;
        this.model = model;
        this.texture = texture;
        this.specFile = specFile;
        this.defaultHealth = defaultHealth;
    }

    public String id() {
        return this.id;
    }

    public String modelName() {
        return this.model;
    }

    public String specName() {
        return this.id;
    }

    public String specFile() {
        return this.specFile;
    }

    public float defaultHealth() {
        return this.defaultHealth;
    }

    public String geoPath() {
        return "/assets/ata/geo/" + this.model + ".geo.json";
    }

    public String animationPath() {
        return "/assets/ata/animations/" + this.model + ".animation.json";
    }

    public String clipPrefix() {
        return "animation." + this.model + ".";
    }

    public ResourceLocation geoLocation() {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "geo/" + this.model + ".geo.json");
    }

    public ResourceLocation animationLocation() {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
                "animations/" + this.model + ".animation.json");
    }

    public ResourceLocation textureLocation() {
        return ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID,
                "textures/entity/" + this.texture + ".png");
    }

    public boolean isLauncher() {
        return this == LAUNCHER;
    }
}
