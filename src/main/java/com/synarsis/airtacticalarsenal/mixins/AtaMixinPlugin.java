package com.synarsis.airtacticalarsenal.mixins;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Decides which of this mod's mixins may be applied.
 *
 * <p>Exists for one case: {@link LevelRendererMixin} redirects three calls inside
 * {@code LevelRenderer.setupRender}, and the rendering-optimisation mods rewrite that method
 * wholesale. Mixin refuses to inject into a method another mixin has merged at the same priority,
 * and it refuses at the <em>prepare</em> stage with an {@code InvalidInjectionException} -- which
 * {@code require = 0} does not suppress, because that setting only governs "the target was not
 * found", not "injecting here is not allowed". The result was a hard crash during mod construction:
 * the game never reached the main menu.
 *
 * <p>So the mixin is simply not offered when such a mod is present. The drone camera still works --
 * {@code setCameraEntity} and {@code ClientChunkCacheMixin} are untouched -- it only loses the
 * render-grid recentring, which can leave distant sections around the drone unbuilt. A cosmetic
 * limitation is the right trade against refusing to start.
 */
public class AtaMixinPlugin implements IMixinConfigPlugin {

    /**
     * Mods that replace {@code LevelRenderer.setupRender}.
     *
     * <p>Checked by mod id through {@link LoadingModList}, which is populated before mixins are
     * applied. OptiFine is absent from the list because it is not a NeoForge mod and would not
     * appear here; if it is installed, the injection simply fails to find its target, which
     * {@code require = 0} does handle.
     */
    private static final Set<String> RENDERER_REPLACING_MODS =
            Set.of("embeddium", "sodium", "rubidium", "nvidium", "xenon");

    private static final String LEVEL_RENDERER_MIXIN =
            "com.synarsis.airtacticalarsenal.mixins.LevelRendererMixin";

    private Boolean rendererReplaced;

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!LEVEL_RENDERER_MIXIN.equals(mixinClassName)) {
            return true;
        }
        return !isRendererReplaced();
    }

    private boolean isRendererReplaced() {
        if (rendererReplaced == null) {
            LoadingModList mods = LoadingModList.get();
            rendererReplaced = mods != null && RENDERER_REPLACING_MODS.stream()
                    .anyMatch(id -> mods.getModFileById(id) != null);
        }
        return rendererReplaced;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
