package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.vehicle.hitbox.HitboxPart;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.HitboxVehicle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

public class PatriotPart extends PartEntity<PatriotVehicleEntity> implements HitboxPart {

    public final int index;

    public PatriotPart(PatriotVehicleEntity parent, int index) {
        super(parent);
        this.index = index;
        this.refreshDimensions();
    }

    @Override
    public HitboxVehicle owner() {
        return this.getParent();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(PatriotGeometry.PART_SIZE, PatriotGeometry.PART_SIZE);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.getParent().interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source) && this.getParent().hurt(source, amount);
    }

    @Override
    public ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }
}
