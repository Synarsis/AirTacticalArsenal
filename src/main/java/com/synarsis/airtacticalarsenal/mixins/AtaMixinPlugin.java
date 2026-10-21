package com.synarsis.airtacticalarsenal.mixins;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AtaMixinPlugin implements IMixinConfigPlugin {

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
