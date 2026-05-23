package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.OrlanItem;
import com.synarsis.airtacticalarsenal.item.OrlanTabletItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrlanLauncherEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> DATA_HAS_DRONE = SynchedEntityData
            .defineId(OrlanLauncherEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID storedDroneUuid;
    private List<BlockPos> savedRoute = null;

    public boolean isLegitSpawn = false;

    public OrlanLauncherEntity(EntityType<? extends OrlanLauncherEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_HAS_DRONE, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("LegitSpawn")) {
            this.isLegitSpawn = tag.getBoolean("LegitSpawn");
        }
        if (tag.hasUUID("StoredDrone")) {
            storedDroneUuid = tag.getUUID("StoredDrone");
            this.entityData.set(DATA_HAS_DRONE, true);
        }
        if (tag.contains("SavedRoute")) {
            net.minecraft.nbt.ListTag routeTag = tag.getList("SavedRoute", 10);
            savedRoute = new ArrayList<>();
            for (int i = 0; i < routeTag.size(); i++) {
                CompoundTag wp = routeTag.getCompound(i);
                savedRoute.add(new BlockPos(wp.getInt("x"), wp.getInt("y"), wp.getInt("z")));
            }
            if (savedRoute.isEmpty())
                savedRoute = null;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("LegitSpawn", this.isLegitSpawn);
        if (storedDroneUuid != null) {
            tag.putUUID("StoredDrone", storedDroneUuid);
        }
        if (savedRoute != null && !savedRoute.isEmpty()) {
            net.minecraft.nbt.ListTag routeTag = new net.minecraft.nbt.ListTag();
            for (BlockPos wp : savedRoute) {
                CompoundTag wpTag = new CompoundTag();
                wpTag.putInt("x", wp.getX());
                wpTag.putInt("y", wp.getY());
                wpTag.putInt("z", wp.getZ());
                routeTag.add(wpTag);
            }
            tag.put("SavedRoute", routeTag);
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
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (CreateCompat.isCreateFakePlayer(player)) return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof OrlanTabletItem) {
            if (!level().isClientSide) {
                boolean added = OrlanTabletItem.addLinkedLauncher(held, this);
                if (added) {
                    int count = OrlanTabletItem.getLinkedLaunchers(held).size();
                    player.displayClientMessage(
                            Component.literal(
                                    "§bПУ Орлана подключена [" + count + "/" + OrlanTabletItem.MAX_LAUNCHERS + "]"),
                            true);
                } else {
                    if (OrlanTabletItem.getLinkedLaunchers(held).size() >= OrlanTabletItem.MAX_LAUNCHERS) {
                        player.displayClientMessage(
                                Component.literal("§cМакс. количество ПУ: " + OrlanTabletItem.MAX_LAUNCHERS), true);
                    } else {
                        player.displayClientMessage(Component.literal("§eПУ уже подключена"), true);
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!(level() instanceof net.minecraft.server.level.ServerLevel)) {
            return InteractionResult.PASS;
        }

        if (held.getItem() instanceof OrlanItem) {
            if (hasDrone()) {
                player.displayClientMessage(Component.literal("§cНа ПУ уже установлен дрон!"), true);
                return InteractionResult.FAIL;
            }

            storedDroneUuid = UUID.randomUUID();
            setHasDrone(true);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            player.displayClientMessage(Component.literal("§bОрлан-10 установлен на ПУ"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (player.isShiftKeyDown() && held.isEmpty() && hasDrone()) {
            ejectDrone(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    public boolean hasDrone() {
        return this.entityData.get(DATA_HAS_DRONE);
    }

    private void setHasDrone(boolean value) {
        this.entityData.set(DATA_HAS_DRONE, value);
    }

    public void removeDrone() {
        storedDroneUuid = null;
        setHasDrone(false);
    }

    private void ejectDrone(Player player) {
        if (!hasDrone())
            return;

        ItemStack stack = new ItemStack(ModItems.ORLAN.get());
        if (!player.addItem(stack)) {
            spawnAtLocation(stack);
        }
        removeDrone();
        player.displayClientMessage(Component.literal("§eОрлан-10 снят с ПУ"), true);
    }

    private void dropStoredDroneAsItem() {
        if (!hasDrone())
            return;
        spawnAtLocation(new ItemStack(ModItems.ORLAN.get()));
        removeDrone();
    }

    private void dropSelf() {
        spawnAtLocation(new ItemStack(ModItems.ORLAN_LAUNCHER.get()));
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
        if (level().isClientSide || !isAlive())
            return false;
        if (CreateCompat.isCreateDamageSource(source))
            return false;
        dropStoredDroneAsItem();
        dropSelf();
        discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        dropStoredDroneAsItem();
        super.remove(reason);
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

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.5, 0.5, 1.5);
    }

    public List<BlockPos> getSavedRoute() {
        return savedRoute;
    }

    public void setSavedRoute(List<BlockPos> route) {
        this.savedRoute = route != null && !route.isEmpty() ? new ArrayList<>(route) : null;
    }

    public boolean hasSavedRoute() {
        return savedRoute != null && !savedRoute.isEmpty();
    }
}
