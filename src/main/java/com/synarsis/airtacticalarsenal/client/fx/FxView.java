package com.synarsis.airtacticalarsenal.client.fx;

import com.synarsis.airtacticalarsenal.config.AtaClientConfig;
import com.synarsis.airtacticalarsenal.config.FxQuality;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FxView {

    private static final java.util.Random ROLLS = new java.util.Random();
    private static Level windLevel;
    private static long windSeed;

    private FxView() {
    }

    public static Vec3 camera() {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
        if (camera != null && camera.isInitialized()) {
            return camera.getPosition();
        }
        return mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
    }

    public static double distanceTo(Vec3 point) {
        return camera().distanceTo(point);
    }

    public static FxQuality quality() {
        try {
            return AtaClientConfig.getFxQuality();
        } catch (IllegalStateException e) {
            return FxQuality.HIGH;
        }
    }

    public static double serverShare() {
        try {
            return ShahedConfig.areExplosionParticlesEnabled() ? ShahedConfig.getParticleMultiplier() / 100.0 : 0.0;
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static double vanillaShare() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1.0 : FxBudget.vanillaShare(mc.options.particles().get().getId());
    }

    public static double share() {
        return serverShare() * vanillaShare();
    }

    public static boolean particlesWanted() {
        return serverShare() > 0.0 && quality().multiplier() > 0.0;
    }

    public static int allow(int requested, Vec3 at, boolean essential) {
        return FxBudget.allow(requested, distanceTo(at), essential, quality(), share(), FxBudget.live(),
                ROLLS.nextDouble());
    }

    public static double[] wind(Level level, double y) {
        if (level != windLevel) {
            windLevel = level;
            windSeed = FxWind.seedOf(level.dimension().location().toString());
        }
        return FxWind.compute(level.getGameTime(), windSeed, y);
    }

    public static void reset() {
        windLevel = null;
    }

    public static double random() {
        return ROLLS.nextDouble();
    }
}
