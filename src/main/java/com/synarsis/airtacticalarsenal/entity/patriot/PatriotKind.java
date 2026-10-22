package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.resources.ResourceLocation;

public enum PatriotKind {

    LAUNCHER("patriot_launcher", "patriot_pac2_launcher", "patriot_pac2", VehicleSpecs.PATRIOT_LAUNCHER_TRAILER,
            400.0f),
    RADAR("patriot_radar", "patriot_anmpq65_radar", "patriot_anmpq65", VehicleSpecs.PATRIOT_RADAR_TRAILER, 350.0f);

    private final String id;
    private final String model;
    private final String texture;
    private final String specName;
    private final float defaultHealth;

    PatriotKind(String id, String model, String texture, String specName, float defaultHealth) {
        this.id = id;
        this.model = model;
        this.texture = texture;
        this.specName = specName;
        this.defaultHealth = defaultHealth;
    }

    public String id() {
        return this.id;
    }

    public String modelName() {
        return this.model;
    }

    public String specName() {
        return this.specName;
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
