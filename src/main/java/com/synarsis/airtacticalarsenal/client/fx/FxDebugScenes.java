package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.client.fx.light.LightDebug;
import com.synarsis.airtacticalarsenal.network.ClientPacketHandler;
import com.synarsis.airtacticalarsenal.network.FxDebugKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FxDebugScenes {

    private FxDebugScenes() {
    }

    public static void play(FxDebugKind kind, Vec3 at, double power) {
        ClientLevel level = FxSpawn.level();
        if (level == null) {
            return;
        }
        switch (kind) {
            case LAUNCH -> LaunchFx.ignition(level, at);
            case IMPACT -> {
                if (!com.synarsis.airtacticalarsenal.compat.ExplosionOverhaulCompat.leadsVisuals()) {
                    ImpactFx.blast(at, power, true);
                }
            }
            case SHAHED -> ClientPacketHandler.handleExplosion(at);
            case WRECK -> ImpactFx.wreck(at.add(0.0, 1.0, 0.0));
            case FLASH -> ImpactFx.flash(at, power);
            case LIGHTS_ON -> LightDebug.setEnabled(true);
            case LIGHTS_OFF -> LightDebug.setEnabled(false);
            case LIGHTS_BUFFER -> LightDebug.toggleBufferView();
            case LIGHTS_VOXELS -> LightDebug.toggleVoxels();
            case PHYSICS_ON -> com.synarsis.airtacticalarsenal.client.debug.PhysicsDebugRenderer.setEnabled(true);
            case PHYSICS_OFF -> com.synarsis.airtacticalarsenal.client.debug.PhysicsDebugRenderer.setEnabled(false);
        }
    }
}
