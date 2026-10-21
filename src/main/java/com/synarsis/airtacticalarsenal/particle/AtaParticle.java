package com.synarsis.airtacticalarsenal.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.synarsis.airtacticalarsenal.client.fx.FxBudget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AtaParticle extends TextureSheetParticle {

    public static final ParticleRenderType TRANSLUCENT_NO_DEPTH = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "ATA_TRANSLUCENT_NO_DEPTH";
        }
    };

    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "ATA_ADDITIVE";
        }
    };

    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    protected AtaParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0f;
        FxBudget.born();
    }

    @Override
    public void remove() {
        if (!this.removed) {
            FxBudget.died();
        }
        super.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        double reach = Math.max(2.0, this.getQuadSize(partialTick) * 2.0);
        return new AABB(this.x - reach, this.y - reach, this.z - reach, this.x + reach, this.y + reach, this.z + reach);
    }

    protected boolean solidAt(double px, double py, double pz) {
        this.probe.set((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
        if (this.level.isOutsideBuildHeight(this.probe.getY()) || !this.level.hasChunkAt(this.probe)) {
            return false;
        }
        return !this.level.getBlockState(this.probe).getCollisionShape(this.level, this.probe).isEmpty();
    }
}
