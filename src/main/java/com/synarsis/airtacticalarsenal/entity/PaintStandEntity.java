package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.ShahedItem;
import com.synarsis.airtacticalarsenal.item.SprayCanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class PaintStandEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> DATA_HAS_SHAHED = SynchedEntityData
            .defineId(PaintStandEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_PAINT_PROGRESS = SynchedEntityData
            .defineId(PaintStandEntity.class, EntityDataSerializers.FLOAT);

    private int storedDroneId = -1;
    private UUID storedDroneUuid;

    private ItemStack storedShahedItem = ItemStack.EMPTY;

    public boolean isLegitSpawn = false;

    private static final float PAINT_PROGRESS_PER_TICK = 0.007f;
    private static final float SPRAY_COST_PER_TICK = 0.7f;

    public PaintStandEntity(EntityType<? extends PaintStandEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_HAS_SHAHED, false);
        this.entityData.define(DATA_PAINT_PROGRESS, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("LegitSpawn")) {
            this.isLegitSpawn = tag.getBoolean("LegitSpawn");
        }
        if (tag.hasUUID("StoredDrone")) {
            this.storedDroneUuid = tag.getUUID("StoredDrone");
            this.entityData.set(DATA_HAS_SHAHED, true);
        }
        if (tag.contains("PaintProgress")) {
            this.entityData.set(DATA_PAINT_PROGRESS, tag.getFloat("PaintProgress"));
        }
        if (tag.contains("StoredShahedItem")) {
            this.storedShahedItem = ItemStack.of(tag.getCompound("StoredShahedItem"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("LegitSpawn", this.isLegitSpawn);
        if (storedDroneUuid != null) {
            tag.putUUID("StoredDrone", storedDroneUuid);
        }
        tag.putFloat("PaintProgress", this.entityData.get(DATA_PAINT_PROGRESS));
        if (!storedShahedItem.isEmpty()) {
            tag.put("StoredShahedItem", storedShahedItem.save(new CompoundTag()));
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }

        if (CreateCompat.isAnyCreateLoaded()) {
            setDeltaMovement(Vec3.ZERO);
        }

        super.tick();

        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (onGround()) {
            setDeltaMovement(Vec3.ZERO);
        }

        if (!level().isClientSide) {
            resolveStoredDrone();
        }
    }

    private void resolveStoredDrone() {
        if (storedDroneId > 0)
            return;
        if (storedDroneUuid == null || !(level() instanceof ServerLevel serverLevel))
            return;

        Entity entity = serverLevel.getEntity(storedDroneUuid);
        if (entity instanceof ShahedEntity drone && drone.isOnPaintStand()) {
            storedDroneId = drone.getId();
        } else if (entity == null) {

        } else {

            storedDroneUuid = null;
            this.entityData.set(DATA_HAS_SHAHED, false);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (CreateCompat.isCreateFakePlayer(player)) return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof SprayCanItem) {
            return InteractionResult.PASS;
        }

        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (hasShahed()) {
                ejectShahed(player);
            }
            spawnAtLocation(new ItemStack(ModItems.PAINT_STAND.get()));
            level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
            discard();
            return InteractionResult.SUCCESS;
        }

        if (held.getItem() instanceof ShahedItem && !hasShahed()) {
            installShahed(held, player);
            return InteractionResult.SUCCESS;
        }

        if (held.isEmpty() && hasShahed()) {
            ejectShahed(player);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void installShahed(ItemStack shahedStack, Player player) {
        if (!(level() instanceof ServerLevel serverLevel))
            return;

        this.storedShahedItem = shahedStack.copy();
        this.storedShahedItem.setCount(1);

        String color = "white";
        float progress = 0.0f;
        if (shahedStack.hasTag()) {
            CompoundTag tag = shahedStack.getTag();
            if (tag.contains("ShahedColor")) {
                color = tag.getString("ShahedColor");
            }
            if (tag.contains("PaintProgress")) {
                progress = tag.getFloat("PaintProgress");
            }
        }

        ShahedEntity drone = ModEntities.SHAHED.get().create(serverLevel);
        if (drone == null)
            return;

        drone.setShahedColor(color);
        drone.mountPaintStand(this);
        drone.isLegitSpawn = true;
        serverLevel.addFreshEntity(drone);

        this.storedDroneId = drone.getId();
        this.storedDroneUuid = drone.getUUID();
        this.entityData.set(DATA_HAS_SHAHED, true);
        this.entityData.set(DATA_PAINT_PROGRESS, progress);

        if (!player.getAbilities().instabuild) {
            shahedStack.shrink(1);
        }

        level().playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private void ejectShahed(Player player) {
        if (!hasShahed())
            return;

        ShahedEntity drone = getStoredDrone();

        ItemStack stack = storedShahedItem.isEmpty()
                ? new ItemStack(ModItems.SHAHED.get())
                : storedShahedItem.copy();

        CompoundTag tag = stack.getOrCreateTag();
        float progress = this.entityData.get(DATA_PAINT_PROGRESS);
        if (drone != null) {
            tag.putString("ShahedColor", drone.getShahedColor());
        } else if (progress >= 1.0f) {

            tag.putString("ShahedColor", "black");
        }
        tag.putFloat("PaintProgress", progress);

        if (!player.addItem(stack)) {
            spawnAtLocation(stack);
        }

        if (drone != null) {
            drone.discard();
        }

        clearStoredDrone();

        level().playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    public boolean applyPaint(Player player, ItemStack sprayStack) {
        if (!hasShahed())
            return false;

        float currentProgress = this.entityData.get(DATA_PAINT_PROGRESS);

        if (currentProgress >= 1.0f) {
            return false;
        }

        int charge = SprayCanItem.getCharge(sprayStack);
        if (charge <= 0) {
            return false;
        }

        SprayCanItem.consumeCharge(sprayStack, SPRAY_COST_PER_TICK);

        float newProgress = Math.min(1.0f, currentProgress + PAINT_PROGRESS_PER_TICK);
        this.entityData.set(DATA_PAINT_PROGRESS, newProgress);

        if (newProgress >= 1.0f) {
            completePainting(player);
        }

        return true;
    }

    private void completePainting(Player player) {
        ShahedEntity drone = getStoredDrone();
        if (drone != null) {
            drone.setShahedColor("black");
        }

        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.5f);
    }

    private ShahedEntity getStoredDrone() {
        if (storedDroneId > 0) {
            Entity entity = level().getEntity(storedDroneId);
            if (entity instanceof ShahedEntity drone && drone.isOnPaintStand()) {
                return drone;
            }
            storedDroneId = -1;
        }
        if (storedDroneUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(storedDroneUuid);
            if (entity instanceof ShahedEntity drone && drone.isOnPaintStand()) {
                storedDroneId = drone.getId();
                return drone;
            }
            storedDroneUuid = null;
        }
        return null;
    }

    private void clearStoredDrone() {
        this.storedDroneId = -1;
        this.storedDroneUuid = null;
        this.storedShahedItem = ItemStack.EMPTY;
        this.entityData.set(DATA_HAS_SHAHED, false);
        this.entityData.set(DATA_PAINT_PROGRESS, 0.0f);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAlive()) {
            return false;
        }
        if (CreateCompat.isCreateDamageSource(source)) {
            return false;
        }
        dropStoredDroneAsItem();
        spawnAtLocation(new ItemStack(ModItems.PAINT_STAND.get()));
        discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason != RemovalReason.KILLED) {
            dropStoredDroneAsItem();
        }
        super.remove(reason);
    }

    private void dropStoredDroneAsItem() {
        if (!hasShahed() || level().isClientSide)
            return;

        ShahedEntity drone = getStoredDrone();

        ItemStack stack = storedShahedItem.isEmpty()
                ? new ItemStack(ModItems.SHAHED.get())
                : storedShahedItem.copy();

        CompoundTag tag = stack.getOrCreateTag();
        float progress = this.entityData.get(DATA_PAINT_PROGRESS);
        if (drone != null) {
            tag.putString("ShahedColor", drone.getShahedColor());
            drone.discard();
        } else if (progress >= 1.0f) {
            tag.putString("ShahedColor", "black");
        }
        tag.putFloat("PaintProgress", progress);

        spawnAtLocation(stack);
        clearStoredDrone();
    }

    public boolean hasShahed() {
        return this.entityData.get(DATA_HAS_SHAHED);
    }

    public float getPaintProgress() {
        return this.entityData.get(DATA_PAINT_PROGRESS);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
