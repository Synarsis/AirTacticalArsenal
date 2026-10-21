package com.synarsis.airtacticalarsenal.mixins;

import com.synarsis.airtacticalarsenal.client.OrlanCameraHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {

    @ModifyVariable(method = "updateViewCenter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ata$viewCenterX(int x) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.chunkPosition().x : x;
    }

    @ModifyVariable(method = "updateViewCenter", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ata$viewCenterZ(int z) {
        Entity cam = ata$fpvCamera();
        return cam != null ? cam.chunkPosition().z : z;
    }

    @Unique
    private static Entity ata$fpvCamera() {
        if (!OrlanCameraHandler.isCameraActive()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        return (cam != null && cam != mc.player) ? cam : null;
    }
}
