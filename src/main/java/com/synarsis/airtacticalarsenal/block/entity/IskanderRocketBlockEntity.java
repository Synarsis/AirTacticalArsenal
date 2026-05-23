package com.synarsis.airtacticalarsenal.block.entity;

import com.synarsis.airtacticalarsenal.block.IskanderRocketBlock;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RocketLaunchStatePacket;
import com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

public class IskanderRocketBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final int LAUNCH_COUNTDOWN_TICKS = 140; 

    private boolean launching = false;
    private int launchTicks = 0;
    private BlockPos targetPos = BlockPos.ZERO;
    private BlockPos launcherCenterPos = null;
    private UUID ownerUUID = null;
    private String ownerName = "";

    private boolean soundStarted = false;

    public IskanderRocketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ISKANDER_ROCKET.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IskanderRocketBlockEntity entity) {
        if (entity.launching) {
            entity.launchTicks++;

            if (!level.isClientSide) {

                entity.serverTick((ServerLevel) level, pos, state);
            } else {

                entity.clientTick(level, pos, state);
            }
        }
    }

    private void serverTick(ServerLevel level, BlockPos pos, BlockState state) {

        spawnSmokeParticles(level, pos);

        if (launchTicks % 10 == 0) {
            NetworkHandler
                    .sendToAllPlayers(new RocketLaunchStatePacket(pos, launchTicks, LAUNCH_COUNTDOWN_TICKS, true));

            if (launcherCenterPos != null) {
                NetworkHandler.sendToAllPlayers(new LauncherStateUpdatePacket(
                        launcherCenterPos,
                        LauncherStateUpdatePacket.LauncherState.COUNTDOWN,
                        launchTicks,
                        LAUNCH_COUNTDOWN_TICKS,
                        0));
            }
        }

        if (launchTicks >= LAUNCH_COUNTDOWN_TICKS) {
            performLaunch(level, pos);
        }
    }

    private void clientTick(Level level, BlockPos pos, BlockState state) {

        spawnClientSmokeEffects(level, pos);
    }

    private void spawnSmokeParticles(ServerLevel level, BlockPos pos) {

        float progress = (float) launchTicks / LAUNCH_COUNTDOWN_TICKS;

        int baseCount = (int) (2 + progress * 15);

        Vec3 rocketBase = Vec3.atCenterOf(pos).add(0, 0.5, 0);

        for (ServerPlayer player : level.players()) {

            level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    rocketBase.x, rocketBase.y, rocketBase.z,
                    baseCount,
                    0.5 + progress * 0.5, 
                    0.1,
                    0.5 + progress * 0.5,
                    0.02 + progress * 0.03);

            if (progress > 0.2) {
                int sideSmoke = (int) (progress * 10);
                for (int i = 0; i < sideSmoke; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2;
                    double speed = 0.3 + progress * 0.5;
                    level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                            rocketBase.x, rocketBase.y - 0.5, rocketBase.z,
                            1,
                            Math.cos(angle) * speed,
                            -0.1,
                            Math.sin(angle) * speed,
                            0.05);
                }
            }

            if (progress > 0.5) {
                int flameCount = (int) ((progress - 0.5) * 10);
                level.sendParticles(player, ParticleTypes.FLAME, true,
                        rocketBase.x, rocketBase.y - 0.3, rocketBase.z,
                        flameCount,
                        0.3, 0.1, 0.3,
                        0.05);
            }

            if (progress > 0.85) {
                level.sendParticles(player, ParticleTypes.LAVA, true,
                        rocketBase.x, rocketBase.y - 0.2, rocketBase.z,
                        2,
                        0.4, 0.2, 0.4,
                        0.1);
            }
        }
    }

    private void spawnClientSmokeEffects(Level level, BlockPos pos) {
        float progress = (float) launchTicks / LAUNCH_COUNTDOWN_TICKS;
        Vec3 rocketBase = Vec3.atCenterOf(pos).add(0, 0.5, 0);

        if (level.random.nextFloat() < progress) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double speed = 0.2 + progress * 0.4;
            level.addParticle(ParticleTypes.SMOKE,
                    rocketBase.x + (level.random.nextDouble() - 0.5) * 0.5,
                    rocketBase.y,
                    rocketBase.z + (level.random.nextDouble() - 0.5) * 0.5,
                    Math.cos(angle) * speed,
                    -0.05 + level.random.nextDouble() * 0.1,
                    Math.sin(angle) * speed);
        }
    }

    private void performLaunch(ServerLevel level, BlockPos pos) {

        if (launcherCenterPos != null) {
            BlockState launcherState = level.getBlockState(launcherCenterPos);
            if (launcherState.getBlock() instanceof LauncherBlock launcher) {
                launcher.setLaunching(level, launcherCenterPos, true);
            }

            BlockEntity launcherBE = level.getBlockEntity(launcherCenterPos);
            if (launcherBE instanceof LauncherBlockEntity launcherEntity) {
                BlockPos hatchPos = launcherEntity.getLoadingHatchPos();
                if (hatchPos != null) {
                    BlockEntity hatchBE = level.getBlockEntity(hatchPos);
                    if (hatchBE instanceof LoadingHatchBlockEntity hatch) {
                        hatch.clearRocket();

                        BlockState hatchState = level.getBlockState(hatchPos);
                        if (hatchState.hasProperty(com.synarsis.airtacticalarsenal.block.LoadingHatchBlock.LOADED)) {
                            level.setBlock(hatchPos, hatchState.setValue(
                                    com.synarsis.airtacticalarsenal.block.LoadingHatchBlock.LOADED, false), 3);
                        }
                    }
                }

                launcherEntity.setRocketLoaded(false);
                launcherEntity.setInFlight(true);
            }

            NetworkHandler.sendToAllPlayers(new LauncherStateUpdatePacket(
                    launcherCenterPos,
                    LauncherStateUpdatePacket.LauncherState.IN_FLIGHT,
                    0,
                    0,
                    0 
            ));
        }

        level.removeBlock(pos, false);

        Vec3 launchPos = Vec3.atCenterOf(pos);
        IskanderEntity iskander = new IskanderEntity(level, launchPos, targetPos, true);

        if (ownerUUID != null) {
            Player owner = level.getPlayerByUUID(ownerUUID);
            if (owner != null) {
                iskander.setOwner(owner);
            }
        }

        if (launcherCenterPos != null) {
            iskander.setLauncherPos(launcherCenterPos);
        }

        iskander.isLegitSpawn = true;
        level.addFreshEntity(iskander);

        NetworkHandler.sendToAllPlayers(new RocketLaunchStatePacket(pos, 0, LAUNCH_COUNTDOWN_TICKS, false));

        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, ParticleTypes.EXPLOSION, true,
                    launchPos.x, launchPos.y, launchPos.z,
                    1, 0, 0, 0, 0);
            level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    launchPos.x, launchPos.y - 0.5, launchPos.z,
                    30, 1.5, 0.5, 1.5, 0.1);
            level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                    launchPos.x, launchPos.y, launchPos.z,
                    50, 2.0, 1.0, 2.0, 0.15);
        }
    }

    public void startLaunch(BlockPos target, BlockPos launcherCenter, @Nullable Player owner) {
        this.launching = true;
        this.launchTicks = 0;
        this.targetPos = target;
        this.launcherCenterPos = launcherCenter;
        if (owner != null) {
            this.ownerUUID = owner.getUUID();
            this.ownerName = owner.getName().getString();
        }
        this.setChanged();

        if (level != null && !level.isClientSide) {

            NetworkHandler
                    .sendToAllPlayers(new RocketLaunchStatePacket(worldPosition, 0, LAUNCH_COUNTDOWN_TICKS, true));

            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void cancelLaunch() {
        if (launching && level != null && !level.isClientSide) {
            this.launching = false;
            this.launchTicks = 0;
            this.setChanged();

            NetworkHandler
                    .sendToAllPlayers(new RocketLaunchStatePacket(worldPosition, 0, LAUNCH_COUNTDOWN_TICKS, false));

            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public boolean isLaunching() {
        return launching;
    }

    public int getLaunchTicks() {
        return launchTicks;
    }

    public float getLaunchProgress() {
        return (float) launchTicks / LAUNCH_COUNTDOWN_TICKS;
    }

    public int getRemainingSeconds() {
        int remainingTicks = LAUNCH_COUNTDOWN_TICKS - launchTicks;
        return (int) Math.ceil(remainingTicks / 20.0);
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public void setLaunchTicks(int ticks) {
        this.launchTicks = ticks;
    }

    public void setLaunching(boolean launching) {
        this.launching = launching;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Launching", launching);
        tag.putInt("LaunchTicks", launchTicks);
        tag.putInt("TargetX", targetPos.getX());
        tag.putInt("TargetY", targetPos.getY());
        tag.putInt("TargetZ", targetPos.getZ());
        if (launcherCenterPos != null) {
            tag.putInt("LauncherX", launcherCenterPos.getX());
            tag.putInt("LauncherY", launcherCenterPos.getY());
            tag.putInt("LauncherZ", launcherCenterPos.getZ());
        }
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
            tag.putString("OwnerName", ownerName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        launching = tag.getBoolean("Launching");
        launchTicks = tag.getInt("LaunchTicks");
        targetPos = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        if (tag.contains("LauncherX")) {
            launcherCenterPos = new BlockPos(tag.getInt("LauncherX"), tag.getInt("LauncherY"), tag.getInt("LauncherZ"));
        }
        if (tag.contains("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
            ownerName = tag.getString("OwnerName");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<IskanderRocketBlockEntity> state) {

        state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.iskander.idle"));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
