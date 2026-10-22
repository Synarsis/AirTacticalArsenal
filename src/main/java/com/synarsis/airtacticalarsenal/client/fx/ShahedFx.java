package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.AtaLights;
import com.synarsis.airtacticalarsenal.client.fx.light.HazeSources;
import com.synarsis.airtacticalarsenal.client.fx.light.LightCurves.Fall;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ShahedFx {

    private ShahedFx() {
    }

    public static void explosionLight(Vec3 pos) {
        AtaLights.point(pos.add(0.0, 1.5, 0.0)).colour(1.0f, 0.90f, 0.75f).strength(10.0, 48.0)
                .life(1, 0, 6, Fall.EXPONENTIAL).shadows(true).sourceRadius(AtaLights.BLAST_SOURCE);
        AtaLights.point(pos.add(0.0, 1.5, 0.0)).colour(1.0f, 0.55f, 0.20f).strength(5.0, 28.0).radiusEnd(0.6)
                .life(1, 0, 59, Fall.LINEAR).flicker(0.20, 9.0).wrap(0.5).drift(new Vec3(0.0, 0.05, 0.0))
                .seed(pos.hashCode()).shadows(true).sourceRadius(AtaLights.BLAST_SOURCE);
        HazeSources.at(pos.add(0.0, 1.5, 0.0), 5.0, 0.8, 40);
    }
}
