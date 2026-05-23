package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.item.ControlTabletItem;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.ShahedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public class ShahedLauncherEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> DATA_HAS_DRONE = SynchedEntityData
            .defineId(ShahedLauncherEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_IS_LAUNCHING = SynchedEntityData
            .defineId(ShahedLauncherEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> DATA_SHAHED_COLOR = SynchedEntityData
            .defineId(ShahedLauncherEntity.class, EntityDataSerializers.STRING);

    private int storedDroneId = -1;
    private UUID storedDroneUuid;

    private BlockPos targetPos = null;
    private String diveMode = "low";
    private String shahedColor = "white"; 
    private List<BlockPos> routeWaypoints = null; 
    private int launchAltitude = 0; 
    private List<BlockPos> savedRoute = null; 

    private static final int LAUNCH_DELAY_TICKS = 100;
    private int launchTimer = 0;
    private Player launchingPlayer = null;

    public ShahedLauncherEntity(EntityType<? extends ShahedLauncherEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public boolean isLegitSpawn = false;

    @Override
    protected void defineSynchedData() {

        this.entityData.define(DATA_HAS_DRONE, false);
        this.entityData.define(DATA_IS_LAUNCHING, false);
        this.entityData.define(DATA_SHAHED_COLOR, "white");
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
        if (tag.contains("TargetX")) {
            targetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
        if (tag.contains("DiveMode")) {
            diveMode = tag.getString("DiveMode");
        }
        if (tag.contains("ShahedColor")) {
            shahedColor = tag.getString("ShahedColor");
            this.entityData.set(DATA_SHAHED_COLOR, shahedColor);
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
        if (targetPos != null) {
            tag.putInt("TargetX", targetPos.getX());
            tag.putInt("TargetY", targetPos.getY());
            tag.putInt("TargetZ", targetPos.getZ());
        }
        tag.putString("DiveMode", diveMode);
        tag.putString("ShahedColor", shahedColor);

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

        resolveStoredDrone();

        if (launchTimer > 0 && !level().isClientSide) {
            launchTimer--;

            if (launchTimer % 20 == 0 && launchTimer > 0) {

                level().playSound(null, blockPosition(),
                        net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE,
                        net.minecraft.sounds.SoundSource.HOSTILE, 2.0f,
                        0.8f + (float) (LAUNCH_DELAY_TICKS - launchTimer) / LAUNCH_DELAY_TICKS * 0.4f);
            }

            if (launchTimer == 0 && level() instanceof ServerLevel serverLevel) {
                actuallyLaunchDrone(serverLevel);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (CreateCompat.isCreateFakePlayer(player)) return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof ControlTabletItem) {
            if (!level().isClientSide) {
                boolean added = ControlTabletItem.addLinkedLauncher(held, this);

                if (added) {
                    int count = ControlTabletItem.getLauncherCount(held);
                    player.displayClientMessage(Component
                            .literal("§aПУ подключена [" + count + "/" + ControlTabletItem.MAX_LAUNCHERS + "]"), true);
                } else {
                    if (ControlTabletItem.getLauncherCount(held) >= ControlTabletItem.MAX_LAUNCHERS) {
                        player.displayClientMessage(
                                Component.literal("§cМакс. количество ПУ: " + ControlTabletItem.MAX_LAUNCHERS), true);
                    } else {
                        player.displayClientMessage(Component.literal("§eПУ уже подключена"), true);
                    }
                }
            }

            return InteractionResult.SUCCESS;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (held.getItem() instanceof ShahedItem) {
            if (hasDrone()) {
                player.displayClientMessage(Component.literal("§cНа ПУ уже установлен дрон!"), true);
                return InteractionResult.FAIL;
            }

            this.shahedColor = "white";

            CompoundTag itemTag = held.getTag();
            if (itemTag != null) {
                if (itemTag.contains("TargetX")) {
                    this.targetPos = new BlockPos(
                            itemTag.getInt("TargetX"),
                            itemTag.getInt("TargetY"),
                            itemTag.getInt("TargetZ"));
                    this.diveMode = itemTag.getString("DiveMode");
                    if (this.diveMode.isEmpty()) {
                        this.diveMode = "low";
                    }
                }

                if (itemTag.contains("ShahedColor")) {
                    this.shahedColor = itemTag.getString("ShahedColor");
                }
            }

            storedDroneUuid = UUID.randomUUID();
            storedDroneId = -1;
            setHasDrone(true); 
            this.entityData.set(DATA_SHAHED_COLOR, this.shahedColor);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (player.isShiftKeyDown() && held.isEmpty() && hasDrone() && targetPos == null) {

            Vec3 lookVec = player.getLookAngle();
            Vec3 playerPos = player.position();
            int targetX = (int) (playerPos.x + lookVec.x * 500);
            int targetZ = (int) (playerPos.z + lookVec.z * 500);
            int targetY = 64;

            this.targetPos = new BlockPos(targetX, targetY, targetZ);
            this.diveMode = "low";

            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (player.isShiftKeyDown() && held.isEmpty() && hasDrone() && targetPos != null) {
            ejectStoredDrone(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return InteractionResult.PASS;
    }

    public void startLaunchSequence(Player player, BlockPos target, String mode) {
        if (launchTimer > 0)
            return; 
        if (!hasDrone())
            return;

        this.targetPos = target;
        this.diveMode = mode;
        this.launchingPlayer = player;
        this.launchTimer = LAUNCH_DELAY_TICKS;
        this.entityData.set(DATA_IS_LAUNCHING, true);

        if (!level().isClientSide) {
            level().playSound(null, blockPosition(),
                    net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.0f, 0.6f);
        }
    }

    public void startLaunchSequenceWithRoute(Player player, BlockPos target, String mode, List<BlockPos> waypoints) {
        this.routeWaypoints = waypoints != null ? new ArrayList<>(waypoints) : null;
        this.launchAltitude = 0;
        startLaunchSequence(player, target, mode);
    }

    public void startLaunchSequenceWithRoute(Player player, BlockPos target, String mode, List<BlockPos> waypoints,
            int altitude) {
        this.routeWaypoints = waypoints != null ? new ArrayList<>(waypoints) : null;
        this.launchAltitude = altitude;
        startLaunchSequence(player, target, mode);
    }

    private void actuallyLaunchDrone(ServerLevel serverLevel) {
        if (targetPos == null)
            return;

        float launcherYaw = this.getYRot();
        Vec3 launchDirection = Vec3.directionFromRotation(0, launcherYaw).normalize();

        Vec3 launchPos = this.position().add(0, 1.5, 0).add(launchDirection.scale(1.0));

        ShahedEntity drone = new ShahedEntity(serverLevel, launchPos, targetPos, diveMode, launcherYaw);
        if (launchingPlayer != null) {
            drone.setOwner(launchingPlayer);
        }

        drone.setShahedColor(this.shahedColor);

        if (this.routeWaypoints != null && !this.routeWaypoints.isEmpty()) {
            drone.setWaypoints(this.routeWaypoints);
        }

        if (this.launchAltitude > 0) {
            drone.setTargetAltitude(this.launchAltitude);
        }
        drone.isLegitSpawn = true;
        serverLevel.addFreshEntity(drone);

        clearStoredDrone();
        targetPos = null;
        launchingPlayer = null;
        this.routeWaypoints = null;
        this.launchAltitude = 0;
        this.entityData.set(DATA_IS_LAUNCHING, false);
    }

    public boolean isLaunching() {
        return this.entityData.get(DATA_IS_LAUNCHING);
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
        dropSelf();
        discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        dropStoredDroneAsItem();
        super.remove(reason);
    }

    public boolean hasDrone() {

        return this.entityData.get(DATA_HAS_DRONE);
    }

    private void setHasDrone(boolean value) {
        this.entityData.set(DATA_HAS_DRONE, value);
    }

    private void resolveStoredDrone() {

    }

    private void clearStoredDrone() {
        storedDroneId = -1;
        storedDroneUuid = null;
        this.shahedColor = "white";
        this.entityData.set(DATA_SHAHED_COLOR, "white");
        setHasDrone(false); 
    }

    public void clearDroneAfterLaunch() {
        clearStoredDrone();
        targetPos = null;
    }

    private void ejectStoredDrone(Player player) {
        if (!hasDrone())
            return;

        ItemStack stack = new ItemStack(ModItems.SHAHED.get());
        CompoundTag tag = stack.getOrCreateTag();
        if (targetPos != null) {
            tag.putInt("TargetX", targetPos.getX());
            tag.putInt("TargetY", targetPos.getY());
            tag.putInt("TargetZ", targetPos.getZ());
            tag.putString("DiveMode", diveMode);
        }
        tag.putString("ShahedColor", this.shahedColor);

        if (!player.addItem(stack)) {
            spawnAtLocation(stack);
        }

        clearStoredDrone();
        targetPos = null;

    }

    private void dropStoredDroneAsItem() {
        if (!hasDrone())
            return;

        ItemStack stack = new ItemStack(ModItems.SHAHED.get());
        CompoundTag tag = stack.getOrCreateTag();
        if (targetPos != null) {
            tag.putInt("TargetX", targetPos.getX());
            tag.putInt("TargetY", targetPos.getY());
            tag.putInt("TargetZ", targetPos.getZ());
            tag.putString("DiveMode", diveMode);
        }
        tag.putString("ShahedColor", this.shahedColor);
        spawnAtLocation(stack);
        clearStoredDrone();
    }

    private void dropSelf() {
        spawnAtLocation(new ItemStack(ModItems.SHAHED_LAUNCHER.get()));
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
        return super.getBoundingBoxForCulling().inflate(2.0, 1.0, 2.0);
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public String getDiveMode() {
        return diveMode;
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

    public String getShahedColor() {
        return this.entityData.get(DATA_SHAHED_COLOR);
    }
}
