package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.chunk.MissileChunkManager;
import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.compat.TacZCompat;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.ShahedExplosionPacket;
import com.synarsis.airtacticalarsenal.network.ShahedRemovePacket;
import com.synarsis.airtacticalarsenal.network.ShahedSpawnPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleOptions;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;

public class ShahedEntity extends Projectile implements GeoEntity {

    public enum FlightPhase {
        WARMUP, 
        LAUNCHING, 
        CLIMBING, 
        CRUISING, 
        DIVING 
    }

    private static final int WARMUP_DURATION_TICKS = 100; 

    private static final double LAUNCH_INITIAL_SPEED = 0.3; 
    private static final double LAUNCH_ACCELERATION = 0.02; 
    private static final double LAUNCH_MAX_SPEED = 1.2; 
    private static final double LAUNCH_ANGLE_DEG = 20.0; 

    private static final double CLIMB_ACCELERATION = 0.03; 
    private static final double CLIMB_ANGLE_DEG = 25.0; 
    private static final double CRUISE_ALTITUDE_MIN = 160.0; 
    private static final double CRUISE_ALTITUDE_MAX = 170.0; 
    private static final double CLIMB_DISTANCE = 300.0; 
    private static final double ALTITUDE_TOLERANCE = 5.0; 

    private static final double HEIGHT_CORRECTION_FACTOR = 0.15; 

    private static final double DIVE_ACTIVATION_DISTANCE = 20.0; 
    private static final double DIVE_TRANSITION_DISTANCE = 40.0; 
    private static final double DIVE_ANGLE_DEG = 75.0; 
    private static final double DIVE_ACCELERATION = 0.08; 

    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> IS_DIVING = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DIVE_MODE = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> FLIGHT_PHASE_ID = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNC_X_ROT = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_Y_ROT = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DAMAGED = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CRITICALLY_DAMAGED = SynchedEntityData
            .defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> SHAHED_COLOR = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Boolean> DATA_ON_PAINT_STAND = SynchedEntityData
            .defineId(ShahedEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Float> DATA_STAND_YAW = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Float> TARGET_CRUISE_ALT = SynchedEntityData.defineId(ShahedEntity.class,
            EntityDataSerializers.FLOAT);

    private int mountedStandId = -1;
    private java.util.UUID mountedStandUuid;

    public boolean isLegitSpawn = false;

    public static final double CRUISE_SPEED = 1.5;
    public static final double DIVE_SPEED = 3.6;

    public static double getCruiseSpeed() {
        return ShahedConfig.getShahedCruiseSpeed();
    }

    public static double getDiveSpeed() {
        return ShahedConfig.getShahedDiveSpeed();
    }

    private FlightPhase flightPhase = FlightPhase.WARMUP;
    private int phaseTicks = 0; 
    private double currentSpeed = 0; 
    private double launchStartY = 0; 
    private Vec3 launchStartPos = Vec3.ZERO; 
    private Vec3 launchDirection = Vec3.ZERO; 
    private boolean engineStarted = false; 
    private double targetAltitude = 165.0; 

    private static final float EXPLOSION_POWER = 12.0f;
    private static final float EXPLOSION_RADIUS = 25.0f;
    private boolean isDiving = false;
    private float diveProgress = 0.0f;
    private double diveDistance = 300.0;
    private double diveTransitionDistance = 350.0;

    private static final double TURN_RATE_DEG = 1.5;
    private Vec3 lastDirection = new Vec3(0, -1, 0);

    private List<BlockPos> waypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int syncTickCounter = 0;
    private static final int SYNC_INTERVAL = 40;
    private boolean initialPacketSent = false;
    private boolean exploding = false; 

    private float maxHealth = 100.0f;
    private Entity lastAttacker = null;
    private int damageSmokeTicks = 0;
    private int criticalSmokeTicks = 0;

    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    private int lerpSteps = 0;

    private double previousY = 0;
    private boolean wasDescending = false;

    public ShahedEntity(EntityType<? extends ShahedEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public ShahedEntity(Level level, Vec3 startPos, BlockPos targetPos, String diveMode) {
        this(level, startPos, targetPos, diveMode, Float.NaN);
    }

    public ShahedEntity(Level level, Vec3 startPos, BlockPos targetPos, String diveMode, float launcherYaw) {
        this(ModEntities.SHAHED.get(), level);
        this.setPos(startPos);
        this.entityData.set(TARGET_POS, targetPos);
        this.entityData.set(DIVE_MODE, diveMode);
        if (diveMode.equals("high")) {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        } else {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        }

        this.flightPhase = FlightPhase.LAUNCHING;
        this.phaseTicks = 0;
        this.currentSpeed = LAUNCH_INITIAL_SPEED;
        this.launchStartY = startPos.y;
        this.launchStartPos = startPos;
        this.engineStarted = true;

        if (this.targetAltitude < CRUISE_ALTITUDE_MIN || this.targetAltitude > CRUISE_ALTITUDE_MAX) {

        } else {
            this.targetAltitude = CRUISE_ALTITUDE_MIN
                    + level.random.nextDouble() * (CRUISE_ALTITUDE_MAX - CRUISE_ALTITUDE_MIN);
        }
        this.entityData.set(TARGET_CRUISE_ALT, (float) this.targetAltitude);

        if (!Float.isNaN(launcherYaw)) {

            Vec3 dir = Vec3.directionFromRotation(0, launcherYaw).normalize();
            this.launchDirection = new Vec3(dir.x, 0, dir.z).normalize();
            this.lastDirection = this.launchDirection;
            this.setYRot(launcherYaw);
        } else {

            Vec3 targetVec = Vec3.atCenterOf(targetPos);
            Vec3 horizontalDir = new Vec3(targetVec.x - startPos.x, 0, targetVec.z - startPos.z);
            if (horizontalDir.lengthSqr() > 1.0E-4) {
                this.launchDirection = horizontalDir.normalize();
                this.lastDirection = horizontalDir.normalize();
            } else {
                this.launchDirection = new Vec3(0, 0, 1);
                this.lastDirection = new Vec3(0, 0, 1);
            }
        }

        this.setXRot((float) -LAUNCH_ANGLE_DEG);

        this.entityData.set(FLIGHT_PHASE_ID, this.flightPhase.ordinal());
        this.entityData.set(SYNC_X_ROT, (float) -LAUNCH_ANGLE_DEG);
        this.entityData.set(SYNC_Y_ROT, this.getYRot());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 490000.0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps + 1;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_POS, BlockPos.ZERO);
        this.entityData.define(IS_DIVING, false);
        this.entityData.define(DIVE_MODE, "low");
        this.entityData.define(FLIGHT_PHASE_ID, 0);
        this.entityData.define(SYNC_X_ROT, 0.0f);
        this.entityData.define(SYNC_Y_ROT, 0.0f);

        this.entityData.define(HEALTH, (float) ShahedConfig.getShahedMaxHealth());
        this.entityData.define(IS_DAMAGED, false);
        this.entityData.define(IS_CRITICALLY_DAMAGED, false);

        this.entityData.define(SHAHED_COLOR, "white");

        this.entityData.define(DATA_ON_PAINT_STAND, false);
        this.entityData.define(DATA_STAND_YAW, 0.0f);

        this.entityData.define(TARGET_CRUISE_ALT, 165.0f);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }

        super.tick();

        if (isOnPaintStand()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide) {
                handlePaintStandAttachment();
            }
            return;
        }

        if (this.level().isClientSide && this.lerpSteps > 0) {
            double dx = (this.lerpX - this.getX()) / this.lerpSteps;
            double dy = (this.lerpY - this.getY()) / this.lerpSteps;
            double dz = (this.lerpZ - this.getZ()) / this.lerpSteps;
            this.setPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
            this.lerpSteps--;
        }

        BlockPos target = this.entityData.get(TARGET_POS);
        Vec3 targetVec = Vec3.atCenterOf(target);
        String mode = this.entityData.get(DIVE_MODE);

        if (mode.equals("high")) {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        } else {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        }

        Vec3 currentPos = this.position();
        double horizontalDist = Math
                .sqrt(Math.pow(targetVec.x - currentPos.x, 2.0) + Math.pow(targetVec.z - currentPos.z, 2.0));

        int syncedPhaseId = this.entityData.get(FLIGHT_PHASE_ID);
        if (this.level().isClientSide && syncedPhaseId >= 0 && syncedPhaseId < FlightPhase.values().length) {
            this.flightPhase = FlightPhase.values()[syncedPhaseId];
        }

        Vec3 motion;

        switch (this.flightPhase) {
            case WARMUP:

                transitionToPhase(FlightPhase.LAUNCHING);
                motion = tickLaunchingPhase(targetVec, currentPos);
                break;
            case LAUNCHING:
                motion = tickLaunchingPhase(targetVec, currentPos);
                break;
            case CLIMBING:
                motion = tickClimbingPhase(targetVec, currentPos);
                break;
            case CRUISING:
                motion = tickCruisingPhase(targetVec, currentPos, horizontalDist);
                break;
            case DIVING:

                Vec3 diveTarget = new Vec3(targetVec.x, findSurfaceY(target), targetVec.z);
                motion = tickDivingPhase(diveTarget, currentPos);
                break;
            default:
                motion = this.lastDirection.scale(getCruiseSpeed());
        }

        this.phaseTicks++;
        this.setDeltaMovement(motion);

        if (motion.lengthSqr() > 1.0E-4) {
            this.lastDirection = motion.normalize();
        }

        if (this.level().isClientSide) {
            float syncedXRot = this.entityData.get(SYNC_X_ROT);
            float syncedYRot = this.entityData.get(SYNC_Y_ROT);

            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();

            this.setYRot(syncedYRot);
            this.setXRot(syncedXRot);
            this.setYBodyRot(syncedYRot);
            this.setYHeadRot(syncedYRot);

            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            return;
        }

        if (motion.lengthSqr() > 1.0E-4) {
            float newYRot = (float) (Math.atan2(motion.x, motion.z) * 57.29577951308232);

            float newXRot;
            if (this.flightPhase == FlightPhase.CRUISING) {
                newXRot = 0; 
            } else {
                newXRot = (float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z))
                        * 57.29577951308232);
            }

            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();

            this.setYRot(newYRot);
            this.setXRot(newXRot);
            this.setYBodyRot(newYRot);
            this.setYHeadRot(newYRot);

            this.entityData.set(SYNC_X_ROT, newXRot);
            this.entityData.set(SYNC_Y_ROT, newYRot);
        }

        spawnDamageEffects();

        checkTacZBulletCollisions();

        if (this.level() instanceof ServerLevel serverLevel) {
            MissileChunkManager.updateChunksForMissile(this, serverLevel);
        }

        if (this.flightPhase == FlightPhase.DIVING && !this.isDiving) {
            this.isDiving = true;
            this.entityData.set(IS_DIVING, true);
        }

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            return;
        }

        if (this.isDiving) {
            double surfaceY = findSurfaceY(target);

            if (this.getY() <= surfaceY + 2.0) {
                this.explode();
                return;
            }

            BlockPos currentBlock = this.blockPosition();
            BlockPos belowBlock = currentBlock.below();
            if (!this.level().getBlockState(currentBlock).isAir() || !this.level().getBlockState(belowBlock).isAir()) {
                this.explode();
                return;
            }
        }

        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (!this.initialPacketSent) {
            this.initialPacketSent = true;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, mode));
        }

        this.syncTickCounter++;
        if (this.syncTickCounter >= SYNC_INTERVAL) {
            this.syncTickCounter = 0;
            NetworkHandler.sendToAllPlayers(new ShahedSpawnPacket(this.getId(), this.position(), target, mode));
        }
    }

    private Vec3 tickLaunchingPhase(Vec3 targetVec, Vec3 currentPos) {

        this.currentSpeed = Math.min(this.currentSpeed + LAUNCH_ACCELERATION, LAUNCH_MAX_SPEED);

        double launchAngleRad = Math.toRadians(LAUNCH_ANGLE_DEG);
        double horizontalSpeed = this.currentSpeed * Math.cos(launchAngleRad);
        double verticalSpeed = this.currentSpeed * Math.sin(launchAngleRad);

        Vec3 motion = new Vec3(
                this.launchDirection.x * horizontalSpeed,
                verticalSpeed,
                this.launchDirection.z * horizontalSpeed);

        if (this.currentSpeed >= LAUNCH_MAX_SPEED * 0.8) {
            transitionToPhase(FlightPhase.CLIMBING);
        }

        return motion;
    }

    private Vec3 tickClimbingPhase(Vec3 targetVec, Vec3 currentPos) {

        this.currentSpeed = Math.min(this.currentSpeed + CLIMB_ACCELERATION, getCruiseSpeed());

        double distanceFromLaunch = Math.sqrt(
                Math.pow(currentPos.x - this.launchStartPos.x, 2) +
                        Math.pow(currentPos.z - this.launchStartPos.z, 2));

        Vec3 horizontalDir = this.launchDirection;

        double remainingHeight = this.targetAltitude - this.getY();
        double climbAngleRad;

        if (remainingHeight > 80) {
            climbAngleRad = Math.toRadians(CLIMB_ANGLE_DEG);
        } else if (remainingHeight > 20) {
            climbAngleRad = Math.toRadians(CLIMB_ANGLE_DEG * (remainingHeight / 80.0));
        } else {
            climbAngleRad = Math.toRadians(3.0);
        }

        double horizontalSpeed = this.currentSpeed * Math.cos(climbAngleRad);
        double verticalSpeed = this.currentSpeed * Math.sin(climbAngleRad);

        Vec3 motion = new Vec3(
                horizontalDir.x * horizontalSpeed,
                verticalSpeed,
                horizontalDir.z * horizontalSpeed);

        boolean reachedAltitude = this.getY() >= this.targetAltitude - ALTITUDE_TOLERANCE;
        boolean reachedDistance = distanceFromLaunch >= CLIMB_DISTANCE;

        if (reachedAltitude && reachedDistance) {
            transitionToPhase(FlightPhase.CRUISING);
        }

        return motion;
    }

    private Vec3 tickCruisingPhase(Vec3 targetVec, Vec3 currentPos, double horizontalDist) {

        this.currentSpeed = getCruiseSpeed();

        Vec3 navTarget;
        boolean onFinalLeg;

        if (!this.waypoints.isEmpty() && this.currentWaypointIndex < this.waypoints.size()) {

            BlockPos wp = this.waypoints.get(this.currentWaypointIndex);
            navTarget = Vec3.atCenterOf(wp);

            double prevAlt;
            Vec3 prevPos;
            if (this.currentWaypointIndex == 0) {
                prevAlt = this.launchStartPos.y > 0 ? this.targetAltitude : this.targetAltitude;
                prevPos = this.launchStartPos;
            } else {
                BlockPos prevWp = this.waypoints.get(this.currentWaypointIndex - 1);
                prevAlt = prevWp.getY();
                prevPos = Vec3.atCenterOf(prevWp);
            }
            double segLen = Math.sqrt(Math.pow(navTarget.x - prevPos.x, 2) + Math.pow(navTarget.z - prevPos.z, 2));
            double distToWp = Math.sqrt(
                    Math.pow(navTarget.x - currentPos.x, 2) + Math.pow(navTarget.z - currentPos.z, 2));
            double distFromPrev = Math.sqrt(
                    Math.pow(currentPos.x - prevPos.x, 2) + Math.pow(currentPos.z - prevPos.z, 2));
            double segProgress = segLen > 1 ? Math.min(1.0, distFromPrev / segLen) : 1.0;
            this.targetAltitude = prevAlt + (wp.getY() - prevAlt) * segProgress;

            if (distToWp < 50.0) {
                this.currentWaypointIndex++;
                if (this.currentWaypointIndex >= this.waypoints.size()) {
                    navTarget = targetVec;
                    onFinalLeg = true;
                    this.targetAltitude = wp.getY();
                } else {
                    wp = this.waypoints.get(this.currentWaypointIndex);
                    navTarget = Vec3.atCenterOf(wp);
                    onFinalLeg = false;
                }
            } else {
                onFinalLeg = false;
            }
        } else {

            navTarget = targetVec;
            onFinalLeg = true;
        }

        Vec3 desiredDir = new Vec3(navTarget.x - currentPos.x, 0, navTarget.z - currentPos.z);
        if (desiredDir.lengthSqr() > 1.0E-4) {
            desiredDir = desiredDir.normalize();
        } else {
            desiredDir = this.lastDirection;
        }

        Vec3 smoothDir = smoothTurn(this.lastDirection, desiredDir, TURN_RATE_DEG);

        double heightDiff = this.targetAltitude - this.getY();
        double verticalCorrection = heightDiff * HEIGHT_CORRECTION_FACTOR;
        verticalCorrection = Math.max(-0.8, Math.min(0.8, verticalCorrection));

        Vec3 motion = new Vec3(
                smoothDir.x * this.currentSpeed,
                verticalCorrection,
                smoothDir.z * this.currentSpeed);

        if (onFinalLeg && horizontalDist < this.diveTransitionDistance) {
            Vec3 toTargetDir = new Vec3(targetVec.x - currentPos.x, 0, targetVec.z - currentPos.z);
            if (toTargetDir.lengthSqr() > 1.0E-4) {
                toTargetDir = toTargetDir.normalize();
                Vec3 currentHoriz = new Vec3(this.lastDirection.x, 0, this.lastDirection.z);
                if (currentHoriz.lengthSqr() > 1.0E-4) {
                    currentHoriz = currentHoriz.normalize();
                    double dot = currentHoriz.x * toTargetDir.x + currentHoriz.z * toTargetDir.z;
                    dot = Math.max(-1.0, Math.min(1.0, dot));
                    double angleDeg = Math.toDegrees(Math.acos(dot));
                    if (angleDeg < 15.0) {
                        transitionToPhase(FlightPhase.DIVING);
                    }
                } else {
                    transitionToPhase(FlightPhase.DIVING);
                }
            } else {
                transitionToPhase(FlightPhase.DIVING);
            }
        }

        return motion;
    }

    private Vec3 tickDivingPhase(Vec3 targetVec, Vec3 currentPos) {

        Vec3 toTarget = targetVec.subtract(currentPos);
        double distanceToTarget = toTarget.length();

        if (distanceToTarget < 5.0) {
            return toTarget; 
        }

        Vec3 directionToTarget = toTarget.normalize();

        this.diveProgress = Math.min(1.0f, this.diveProgress + 0.05f);

        if (distanceToTarget < 30.0) {
            this.currentSpeed = Math.min(this.currentSpeed + DIVE_ACCELERATION, getDiveSpeed());

            return directionToTarget.scale(this.currentSpeed);
        }

        this.currentSpeed = Math.min(this.currentSpeed + DIVE_ACCELERATION, getDiveSpeed());

        Vec3 desiredHoriz = new Vec3(directionToTarget.x, 0, directionToTarget.z);
        if (desiredHoriz.lengthSqr() > 1.0E-4) {
            desiredHoriz = desiredHoriz.normalize();

            Vec3 smoothHoriz = smoothTurn(this.lastDirection, desiredHoriz, 5.0);

            double targetVy = directionToTarget.y;
            double currentVy = this.lastDirection.y;
            double smoothVy = currentVy + (targetVy - currentVy) * 0.4;
            Vec3 combined = new Vec3(smoothHoriz.x, smoothVy, smoothHoriz.z).normalize();
            return combined.scale(this.currentSpeed);
        }

        return directionToTarget.scale(this.currentSpeed);
    }

    private static Vec3 smoothTurn(Vec3 current, Vec3 desired, double maxDegreesPerTick) {
        double currentYaw = Math.atan2(current.x, current.z);
        double desiredYaw = Math.atan2(desired.x, desired.z);
        double diff = desiredYaw - currentYaw;
        while (diff > Math.PI)
            diff -= 2 * Math.PI;
        while (diff < -Math.PI)
            diff += 2 * Math.PI;
        double maxRad = Math.toRadians(maxDegreesPerTick);
        if (Math.abs(diff) > maxRad) {
            diff = Math.signum(diff) * maxRad;
        }
        double newYaw = currentYaw + diff;
        return new Vec3(Math.sin(newYaw), 0, Math.cos(newYaw)).normalize();
    }

    private void transitionToPhase(FlightPhase newPhase) {
        if (this.flightPhase != newPhase) {
            this.flightPhase = newPhase;
            this.phaseTicks = 0;

            if (!this.level().isClientSide) {
                this.entityData.set(FLIGHT_PHASE_ID, newPhase.ordinal());
            }
        }
    }

    private double findSurfaceY(BlockPos target) {
        if (this.level() == null)
            return target.getY();

        for (int y = 320; y >= -64; y--) {
            BlockPos checkPos = new BlockPos(target.getX(), y, target.getZ());
            if (!this.level().getBlockState(checkPos).isAir()) {
                return y + 1; 
            }
        }
        return target.getY(); 
    }

    public FlightPhase getFlightPhase() {
        return this.flightPhase;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.explode();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            this.explode();
        }
    }

    private void explode() {
        if (this.exploding)
            return; 
        this.exploding = true;

        if (!this.level().isClientSide) {
            Vec3 pos = this.position();
            NetworkHandler.sendToAllPlayers(new ShahedExplosionPacket(pos));

            if (this.level() instanceof ServerLevel serverLevel && ShahedConfig.areExplosionParticlesEnabled()) {
                float mult = ShahedConfig.getParticleMultiplier() / 100.0f;

                sendParticlesForced(serverLevel, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);

                int debrisCount = (int) (400 * mult);
                for (int i = 0; i < debrisCount; i++) {
                    double angle1 = this.random.nextDouble() * Math.PI * 2;
                    double angle2 = (this.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.5 + this.random.nextDouble() * 1.5;
                    double vx = Math.cos(angle2) * Math.cos(angle1) * speed;
                    double vy = Math.sin(angle2) * speed + 0.3;
                    double vz = Math.cos(angle2) * Math.sin(angle1) * speed;
                    sendParticlesForced(serverLevel, ModParticles.DEBRIS.get(), pos.x, pos.y + 1, pos.z, 1, vx, vy, vz,
                            1.0);
                }

                int flameCount = (int) (200 * mult);
                for (int i = 0; i < flameCount; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double speed = 0.3 + this.random.nextDouble() * 0.8;
                    sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1,
                            Math.cos(angle) * speed, 0.2 + this.random.nextDouble() * 0.5, Math.sin(angle) * speed,
                            0.1);
                }

                int smokeCount = (int) (160 * mult);
                for (int i = 0; i < smokeCount; i++) {
                    double radius = this.random.nextDouble() * 8.0;
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double ox = Math.cos(angle) * radius;
                    double oz = Math.sin(angle) * radius;
                    sendParticlesForced(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            pos.x + ox, pos.y + this.random.nextDouble() * 12, pos.z + oz, 1, 0, 0.15, 0, 0.02);
                }

                sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, (int) (80 * mult),
                        12, 8, 12, 0.03);

                int sparkCount = (int) (120 * mult);
                for (int i = 0; i < sparkCount; i++) {
                    double angle1 = this.random.nextDouble() * Math.PI * 2;
                    double angle2 = (this.random.nextDouble() - 0.3) * Math.PI;
                    double speed = 0.4 + this.random.nextDouble() * 1.0;
                    sendParticlesForced(serverLevel, ParticleTypes.FIREWORK, pos.x, pos.y + 0.5, pos.z, 1,
                            Math.cos(angle2) * Math.cos(angle1) * speed,
                            Math.sin(angle2) * speed + 0.2,
                            Math.cos(angle2) * Math.sin(angle1) * speed, 0.1);
                }

                sendParticlesForced(serverLevel, ParticleTypes.LAVA, pos.x, pos.y, pos.z, (int) (60 * mult), 2, 1, 2,
                        0.1);
                sendParticlesForced(serverLevel, ParticleTypes.CLOUD, pos.x, pos.y + 1, pos.z, (int) (100 * mult), 4, 2,
                        4, 0.05);

                int bigFireCount = (int) (30 * mult);
                for (int i = 0; i < bigFireCount; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double radius = this.random.nextDouble() * 3.0;
                    double ox = Math.cos(angle) * radius;
                    double oz = Math.sin(angle) * radius;
                    double vy = 1.5 + this.random.nextDouble() * 2.0;
                    double vx = (this.random.nextDouble() - 0.5) * 0.3;
                    double vz = (this.random.nextDouble() - 0.5) * 0.3;
                    sendParticlesForced(serverLevel, ParticleTypes.LAVA, pos.x + ox, pos.y + 2, pos.z + oz, 3, vx, vy,
                            vz, 0.5);
                    sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x + ox, pos.y + 2, pos.z + oz, 5, vx, vy,
                            vz, 0.3);
                    sendParticlesForced(serverLevel, ParticleTypes.SOUL_FIRE_FLAME, pos.x + ox, pos.y + 1, pos.z + oz,
                            2, vx, vy * 0.8, vz, 0.2);
                }

                int fireballCount = (int) (15 * mult);
                for (int i = 0; i < fireballCount; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double dist = 1.0 + this.random.nextDouble() * 4.0;
                    double ox = Math.cos(angle) * dist;
                    double oz = Math.sin(angle) * dist;
                    double vy = 2.0 + this.random.nextDouble() * 1.5;
                    sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x + ox, pos.y, pos.z + oz, 15, 0.3, vy,
                            0.3, 0.15);
                }
            }

            Entity owner = this.getOwner();

            AABB explosionBox = new AABB(pos.x - EXPLOSION_RADIUS, pos.y - EXPLOSION_RADIUS, pos.z - EXPLOSION_RADIUS,
                    pos.x + EXPLOSION_RADIUS, pos.y + EXPLOSION_RADIUS, pos.z + EXPLOSION_RADIUS);
            List<Entity> entities = this.level().getEntities(this, explosionBox);
            DamageSource damageSource;
            if (owner instanceof LivingEntity livingOwner) {
                damageSource = this.damageSources().explosion(this, livingOwner);
            } else {
                damageSource = this.damageSources().explosion(this, null);
            }
            for (Entity entity : entities) {
                if (entity == this)
                    continue;
                double distance = entity.position().distanceTo(pos);
                if (distance > EXPLOSION_RADIUS)
                    continue;

                if (entity instanceof Player targetPlayer) {

                    float damage = 0.0f;
                    if (distance <= 10.0) {
                        damage = 800.0f; 
                    } else if (distance <= 16.0) {
                        damage = 150.0f; 
                    } else if (distance <= 21.0) {
                        damage = 60.0f;  
                    } else if (distance <= 25.0) {
                        damage = 20.0f;  
                    }
                    if (damage > 0.0f) {
                        damage *= (float) ShahedConfig.getShahedExplosionPlayerMultiplier();
                        targetPlayer.hurt(damageSource, damage);
                        targetPlayer.hurtMarked = true;
                    }
                } else if (com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isVehicle(entity)) {

                    float damage;
                    if (distance <= 7.0) {
                        damage = 1100.0f; 
                    } else if (distance <= 15.0) {
                        damage = 650.0f;  
                    } else if (distance <= 20.0) {
                        damage = 380.0f;  
                    } else {
                        damage = 150.0f;  
                    }
                    damage *= (float) ShahedConfig.getShahedExplosionVehicleMultiplier();
                    entity.hurt(damageSource, damage);
                    entity.hurtMarked = true;
                } else {

                    float damage;
                    if (distance <= 7.0) {
                        damage = 1100.0f;
                    } else if (distance <= 15.0) {
                        damage = 650.0f;
                    } else if (distance <= 20.0) {
                        damage = 380.0f;
                    } else {
                        damage = 150.0f;
                    }
                    damage *= (float) ShahedConfig.getShahedExplosionEntityMultiplier();
                    entity.hurt(damageSource, damage);
                    entity.hurtMarked = true;
                }
            }

            LivingEntity explosionOwner = (owner instanceof LivingEntity) ? (LivingEntity) owner : null;
            if (ShahedConfig.doExplosionsBreakBlocks()) {

                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x, pos.y, pos.z, EXPLOSION_POWER, false, Level.ExplosionInteraction.TNT);

                float smoothPower = EXPLOSION_POWER * 0.35f;
                double off = 2.0;
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x + off, pos.y, pos.z + off, smoothPower, false, Level.ExplosionInteraction.TNT);
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x - off, pos.y, pos.z + off, smoothPower, false, Level.ExplosionInteraction.TNT);
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x + off, pos.y, pos.z - off, smoothPower, false, Level.ExplosionInteraction.TNT);
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x - off, pos.y, pos.z - off, smoothPower, false, Level.ExplosionInteraction.TNT);
            } else {
                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x, pos.y, pos.z, EXPLOSION_POWER, false, Level.ExplosionInteraction.NONE);
            }

            if (this.level() instanceof ServerLevel sl) {
                com.synarsis.airtacticalarsenal.event.OrlanCameraExplosionHelper.sendExplosionChunkUpdates(sl, pos);
            }
        }
        this.discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {

            if (!this.exploding
                    && (reason == RemovalReason.DISCARDED || reason == RemovalReason.KILLED)
                    && this.flightPhase != FlightPhase.WARMUP) {
                this.exploding = true;
                spawnShootdownEffects(this.position());
            }

            MissileChunkManager.unregisterMissile(this);

            NetworkHandler.sendToAllPlayers(new ShahedRemovePacket(this.getId()));
        }
        super.remove(reason);
    }

    @Override
    public void kill() {
        if (!this.level().isClientSide && !this.exploding) {
            destroyByProjectile(null);
        } else {
            super.kill();
        }
    }

    public boolean getIsDiving() {
        return this.entityData.get(IS_DIVING);
    }

    public String getDiveMode() {
        return this.entityData.get(DIVE_MODE);
    }

    public double getDiveDistance() {
        return this.diveDistance;
    }

    public double getDiveTransitionDistance() {
        return this.diveTransitionDistance;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos target = this.entityData.get(TARGET_POS);
        tag.putInt("TargetX", target.getX());
        tag.putInt("TargetY", target.getY());
        tag.putInt("TargetZ", target.getZ());
        tag.putBoolean("IsDiving", this.isDiving);
        tag.putFloat("DiveProgress", this.diveProgress);
        tag.putString("DiveMode", this.entityData.get(DIVE_MODE));
        tag.putDouble("LastDirX", this.lastDirection.x);
        tag.putDouble("LastDirY", this.lastDirection.y);
        tag.putDouble("LastDirZ", this.lastDirection.z);

        tag.putInt("FlightPhase", this.flightPhase.ordinal());
        tag.putInt("PhaseTicks", this.phaseTicks);
        tag.putDouble("CurrentSpeed", this.currentSpeed);
        tag.putDouble("LaunchStartY", this.launchStartY);
        tag.putDouble("LaunchStartX", this.launchStartPos.x);
        tag.putDouble("LaunchStartPosY", this.launchStartPos.y);
        tag.putDouble("LaunchStartZ", this.launchStartPos.z);
        tag.putDouble("LaunchDirX", this.launchDirection.x);
        tag.putDouble("LaunchDirY", this.launchDirection.y);
        tag.putDouble("LaunchDirZ", this.launchDirection.z);
        tag.putBoolean("EngineStarted", this.engineStarted);
        tag.putDouble("TargetAltitude", this.targetAltitude);

        tag.putFloat("Health", this.getHealth());
        tag.putFloat("MaxHealth", this.maxHealth);

        if (!this.waypoints.isEmpty()) {
            ListTag waypointList = new ListTag();
            for (BlockPos wp : this.waypoints) {
                CompoundTag wpTag = new CompoundTag();
                wpTag.putInt("X", wp.getX());
                wpTag.putInt("Y", wp.getY());
                wpTag.putInt("Z", wp.getZ());
                waypointList.add(wpTag);
            }
            tag.put("Waypoints", waypointList);
            tag.putInt("CurrentWaypointIndex", this.currentWaypointIndex);
        }

        tag.putString("ShahedColor", getShahedColor());

        tag.putBoolean("OnPaintStand", isOnPaintStand());
        if (mountedStandUuid != null) {
            tag.putUUID("MountedStandUuid", mountedStandUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        BlockPos target = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        this.entityData.set(TARGET_POS, target);
        this.isDiving = tag.getBoolean("IsDiving");
        this.diveProgress = tag.getFloat("DiveProgress");
        String mode = tag.getString("DiveMode");
        this.entityData.set(DIVE_MODE, mode);
        if (mode.equals("high")) {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        } else {
            this.diveDistance = 300.0;
            this.diveTransitionDistance = 350.0;
        }
        this.entityData.set(IS_DIVING, this.isDiving);
        if (tag.contains("LastDirX")) {
            this.lastDirection = new Vec3(tag.getDouble("LastDirX"), tag.getDouble("LastDirY"),
                    tag.getDouble("LastDirZ"));
        }

        if (tag.contains("FlightPhase")) {
            int phaseId = tag.getInt("FlightPhase");
            if (phaseId >= 0 && phaseId < FlightPhase.values().length) {
                this.flightPhase = FlightPhase.values()[phaseId];
                this.entityData.set(FLIGHT_PHASE_ID, phaseId);
            }
        }
        if (tag.contains("PhaseTicks")) {
            this.phaseTicks = tag.getInt("PhaseTicks");
        }
        if (tag.contains("CurrentSpeed")) {
            this.currentSpeed = tag.getDouble("CurrentSpeed");
        }
        if (tag.contains("LaunchStartY")) {
            this.launchStartY = tag.getDouble("LaunchStartY");
        }
        if (tag.contains("LaunchDirX")) {
            this.launchDirection = new Vec3(
                    tag.getDouble("LaunchDirX"),
                    tag.getDouble("LaunchDirY"),
                    tag.getDouble("LaunchDirZ"));
        }
        if (tag.contains("LaunchStartX")) {
            this.launchStartPos = new Vec3(
                    tag.getDouble("LaunchStartX"),
                    tag.getDouble("LaunchStartPosY"),
                    tag.getDouble("LaunchStartZ"));
        }
        if (tag.contains("EngineStarted")) {
            this.engineStarted = tag.getBoolean("EngineStarted");
        }
        if (tag.contains("TargetAltitude")) {
            this.targetAltitude = tag.getDouble("TargetAltitude");
        }

        if (tag.contains("Health")) {
            this.setHealth(tag.getFloat("Health"));
        }
        if (tag.contains("MaxHealth")) {
            this.maxHealth = tag.getFloat("MaxHealth");
        }

        if (tag.contains("Waypoints")) {
            ListTag waypointList = tag.getList("Waypoints", 10);
            this.waypoints.clear();
            for (int i = 0; i < waypointList.size(); i++) {
                CompoundTag wpTag = waypointList.getCompound(i);
                this.waypoints.add(new BlockPos(wpTag.getInt("X"), wpTag.getInt("Y"), wpTag.getInt("Z")));
            }
        }
        if (tag.contains("CurrentWaypointIndex")) {
            this.currentWaypointIndex = tag.getInt("CurrentWaypointIndex");
        }

        if (tag.contains("ShahedColor")) {
            setShahedColor(tag.getString("ShahedColor"));
        }

        if (tag.getBoolean("OnPaintStand")) {
            this.entityData.set(DATA_ON_PAINT_STAND, true);
            this.setNoGravity(true);
            if (tag.hasUUID("MountedStandUuid")) {
                this.mountedStandUuid = tag.getUUID("MountedStandUuid");
            }
        }
    }

    public float getDiveProgress() {
        return this.diveProgress;
    }

    public void setWaypoints(List<BlockPos> waypoints) {
        this.waypoints = new ArrayList<>(waypoints);
        this.currentWaypointIndex = 0;

        if (!this.waypoints.isEmpty()) {
            this.targetAltitude = this.waypoints.get(0).getY();
            this.entityData.set(TARGET_CRUISE_ALT, (float) this.targetAltitude);
        }
    }

    public List<BlockPos> getWaypoints() {
        return this.waypoints;
    }

    public int getCurrentWaypointIndex() {
        return this.currentWaypointIndex;
    }

    public void setTargetAltitude(double altitude) {
        this.targetAltitude = Math.max(80.0, Math.min(220.0, altitude));
        this.entityData.set(TARGET_CRUISE_ALT, (float) this.targetAltitude);
    }

    public double getTargetAltitude() {
        if (this.level().isClientSide) {
            return this.entityData.get(TARGET_CRUISE_ALT);
        }
        return this.targetAltitude;
    }

    public float getHealth() {
        return this.entityData.get(HEALTH);
    }

    public void setHealth(float health) {
        this.maxHealth = (float) ShahedConfig.getShahedMaxHealth();
        float clampedHealth = Math.max(0, Math.min(health, this.maxHealth));
        this.entityData.set(HEALTH, clampedHealth);

        boolean damaged = clampedHealth < this.maxHealth;
        boolean critical = clampedHealth < this.maxHealth * 0.5f;
        this.entityData.set(IS_DAMAGED, damaged);
        this.entityData.set(IS_CRITICALLY_DAMAGED, critical);
    }

    public boolean isDamaged() {
        return this.entityData.get(IS_DAMAGED);
    }

    public boolean isCriticallyDamaged() {
        return this.entityData.get(IS_CRITICALLY_DAMAGED);
    }

    public float getHealthPercent() {
        this.maxHealth = (float) ShahedConfig.getShahedMaxHealth();
        return this.getHealth() / this.maxHealth;
    }

    public HitZone determineHitZone(Vec3 hitPos) {
        Vec3 entityPos = this.position();
        Vec3 localHit = hitPos.subtract(entityPos);

        Vec3 flightDir = this.lastDirection.normalize();

        double projection = localHit.dot(flightDir);

        if (projection > 0.5) {
            return HitZone.WARHEAD; 
        } else if (projection < -0.5) {
            return HitZone.ENGINE; 
        } else {
            return HitZone.BODY; 
        }
    }

    public enum HitZone {
        WARHEAD(2.0f, "warhead"), 
        ENGINE(1.5f, "engine"), 
        BODY(1.0f, "body"); 

        public final float damageMultiplier;
        public final String name;

        HitZone(float multiplier, String name) {
            this.damageMultiplier = multiplier;
            this.name = name;
        }
    }

    public void takeDamage(float damage, Entity attacker, Vec3 hitPos, String source) {
        if (!ShahedConfig.isShahedShootdownEnabled())
            return;
        if (this.level().isClientSide)
            return;

        if (damage < ShahedConfig.getShahedMinCaliberDamage())
            return;

        damage *= ShahedConfig.getShahedDamageMultiplier();

        HitZone hitZone = HitZone.BODY;
        if (hitPos != null) {
            hitZone = determineHitZone(hitPos);
            switch (hitZone) {
                case ENGINE:
                    damage *= ShahedConfig.getShahedEngineDamageMultiplier();
                    break;
                case WARHEAD:
                    damage *= ShahedConfig.getShahedWarheadDamageMultiplier();
                    break;
                default:
                    break;
            }
        }

        float oldHealth = this.getHealth();
        float newHealth = oldHealth - damage;
        this.setHealth(newHealth);
        this.lastAttacker = attacker;

        if (ShahedConfig.isLoggingEnabled()) {
            String attackerName = attacker != null ? attacker.getName().getString() : "Unknown";
            System.out.println("[ATA] Shahed hit by " + attackerName + " (" + source + ") for " +
                    String.format("%.1f", damage) + " damage (zone: " + hitZone.name + "). HP: " +
                    String.format("%.1f", oldHealth) + " -> " + String.format("%.1f", newHealth));
        }

        spawnHitEffects(hitPos != null ? hitPos : this.position(), hitZone);

        if (hitZone == HitZone.WARHEAD && ShahedConfig.isShahedCriticalDetonationEnabled()) {
            if (newHealth < this.maxHealth * 0.3f && this.random.nextFloat() < 0.3f) {

                if (ShahedConfig.isLoggingEnabled()) {
                    System.out.println("[ATA] Shahed warhead detonated prematurely!");
                }
                this.explode();
                return;
            }
        }

        if (newHealth <= 0) {
            destroyByProjectile(attacker);
        }
    }

    public void onTacZProjectileHit(Entity projectile, Vec3 hitPos, DamageSource source, float damage) {
        if (!TacZCompat.isTacZLoaded())
            return;

        var gunId = TacZCompat.getGunId(projectile);
        var shooter = TacZCompat.getShooter(projectile);

        float finalDamage = TacZCompat.calculateDamage(damage, gunId, 0);

        String sourceName = gunId != null ? gunId.toString() : "tacz_bullet";
        takeDamage(finalDamage, shooter, hitPos, sourceName);
    }

    private void spawnHitEffects(Vec3 hitPos, HitZone zone) {
        if (!(this.level() instanceof ServerLevel serverLevel))
            return;

        for (ServerPlayer player : serverLevel.players()) {

            serverLevel.sendParticles(player, ParticleTypes.FIREWORK, true,
                    hitPos.x, hitPos.y, hitPos.z,
                    5, 0.1, 0.1, 0.1, 0.15);

            serverLevel.sendParticles(player, ParticleTypes.SMOKE, true,
                    hitPos.x, hitPos.y, hitPos.z,
                    3, 0.1, 0.1, 0.1, 0.05);
        }

        if (zone == HitZone.ENGINE) {
            this.damageSmokeTicks = 100; 
            for (ServerPlayer player : serverLevel.players()) {
                serverLevel.sendParticles(player, ParticleTypes.FLAME, true,
                        hitPos.x, hitPos.y, hitPos.z,
                        2, 0.05, 0.05, 0.05, 0.02);
            }
        } else if (zone == HitZone.WARHEAD) {
            for (ServerPlayer player : serverLevel.players()) {
                serverLevel.sendParticles(player, ParticleTypes.LAVA, true,
                        hitPos.x, hitPos.y, hitPos.z,
                        3, 0.1, 0.1, 0.1, 0.1);
            }
        }
    }

    private void checkTacZBulletCollisions() {
        if (this.level().isClientSide)
            return;
        if (!ShahedConfig.isShahedShootdownEnabled())
            return;
        if (!TacZCompat.isTacZLoaded())
            return;

        double bulletSpeed = 15.0; 
        AABB searchBox = this.getBoundingBox().inflate(bulletSpeed);

        List<Entity> nearbyBullets = this.level().getEntities(this, searchBox, TacZCompat::isTacZBullet);

        if (nearbyBullets.isEmpty())
            return;

        AABB myBox = this.getBoundingBox().inflate(0.5);
        Vec3 myCenter = this.position();

        for (Entity bullet : nearbyBullets) {
            Vec3 bulletPos = bullet.position();
            Vec3 bulletMotion = bullet.getDeltaMovement();
            double speed = bulletMotion.length();

            Vec3 prevBulletPos = bulletPos.subtract(bulletMotion);

            Vec3 futurePos = bulletPos.add(bulletMotion.scale(0.5));

            boolean hit = myBox.contains(bulletPos) ||
                    rayIntersectsAABB(prevBulletPos, bulletPos, myBox) ||
                    rayIntersectsAABB(bulletPos, futurePos, myBox);

            if (!hit) {
                double distToCenter = bulletPos.distanceTo(myCenter);
                if (distToCenter < 2.5) { 
                    hit = true;
                }
            }

            if (hit) {

                float damage = TacZCompat.getBulletDamage(bullet, bulletPos);

                String gunId = TacZCompat.getGunIdString(bullet);
                if (gunId != null) {
                    TacZCompat.WeaponCategory category = TacZCompat.classifyWeapon(gunId);
                    damage *= category.getDamageMultiplier();
                }

                Entity shooter = TacZCompat.getShooter(bullet);

                takeDamage(damage, shooter, bulletPos, "tacz_bullet");

                bullet.discard();

                if (ShahedConfig.isLoggingEnabled()) {
                    System.out.println("[ATA] TacZ bullet hit Shahed! Damage: " + damage +
                            ", Gun: " + (gunId != null ? gunId : "unknown") +
                            ", Health: " + this.getHealth() + "/" + this.maxHealth +
                            ", Speed: " + String.format("%.1f", speed));
                }

                break;
            }
        }
    }

    private boolean rayIntersectsAABB(Vec3 start, Vec3 end, AABB box) {
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 0.001)
            return box.contains(start);

        double invDirX = Math.abs(dir.x) > 0.0001 ? 1.0 / dir.x : Double.MAX_VALUE;
        double invDirY = Math.abs(dir.y) > 0.0001 ? 1.0 / dir.y : Double.MAX_VALUE;
        double invDirZ = Math.abs(dir.z) > 0.0001 ? 1.0 / dir.z : Double.MAX_VALUE;

        double t1 = (box.minX - start.x) * invDirX;
        double t2 = (box.maxX - start.x) * invDirX;
        double t3 = (box.minY - start.y) * invDirY;
        double t4 = (box.maxY - start.y) * invDirY;
        double t5 = (box.minZ - start.z) * invDirZ;
        double t6 = (box.maxZ - start.z) * invDirZ;

        double tMin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        double tMax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        return tMax >= 0 && tMin <= tMax && tMin <= length;
    }

    private void spawnDamageEffects() {
        if (!(this.level() instanceof ServerLevel serverLevel))
            return;
        if (!ShahedConfig.isShahedSmokeOnDamageEnabled())
            return;

        Vec3 pos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 enginePos = pos.subtract(motion.normalize().scale(1.0));

        if (this.isDamaged() && this.tickCount % 3 == 0) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.position().distanceTo(pos) < 200.0) {
                    serverLevel.sendParticles(player, ParticleTypes.SMOKE, true,
                            enginePos.x, enginePos.y, enginePos.z,
                            1, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }

        if (this.isCriticallyDamaged() && this.tickCount % 2 == 0) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.position().distanceTo(pos) < 200.0) {
                    serverLevel.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                            enginePos.x, enginePos.y, enginePos.z,
                            2, 0.3, 0.3, 0.3, 0.02);

                    if (this.tickCount % 4 == 0) {
                        serverLevel.sendParticles(player, ParticleTypes.FLAME, true,
                                enginePos.x, enginePos.y, enginePos.z,
                                1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }
    }

    private void destroyByProjectile(Entity attacker) {
        if (this.level().isClientSide)
            return;
        if (this.exploding)
            return; 
        this.exploding = true;

        Vec3 pos = this.position();

        String attackerName = attacker != null ? attacker.getName().getString() : "Unknown";
        if (ShahedConfig.isLoggingEnabled()) {
            System.out.println("[ATA] Shahed DESTROYED by " + attackerName + " at " +
                    String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z));
        }

        spawnShootdownEffects(pos);

        this.discard();
    }

    private void spawnShootdownEffects(Vec3 pos) {

        NetworkHandler.sendToAllPlayers(new ShahedExplosionPacket(pos));

        if (this.level() instanceof ServerLevel serverLevel && ShahedConfig.areExplosionParticlesEnabled()) {
            float mult = ShahedConfig.getParticleMultiplier() / 100.0f;

            sendParticlesForced(serverLevel, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);

            int debrisCount = (int) (400 * mult);
            for (int i = 0; i < debrisCount; i++) {
                double angle1 = this.random.nextDouble() * Math.PI * 2;
                double angle2 = (this.random.nextDouble() - 0.5) * Math.PI;
                double speed = 0.5 + this.random.nextDouble() * 1.5;
                double vx = Math.cos(angle2) * Math.cos(angle1) * speed;
                double vy = Math.sin(angle2) * speed + 0.3;
                double vz = Math.cos(angle2) * Math.sin(angle1) * speed;
                sendParticlesForced(serverLevel, ModParticles.DEBRIS.get(), pos.x, pos.y + 1, pos.z, 1, vx, vy, vz,
                        1.0);
            }

            int flameCount = (int) (200 * mult);
            for (int i = 0; i < flameCount; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double speed = 0.3 + this.random.nextDouble() * 0.8;
                sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1,
                        Math.cos(angle) * speed, 0.2 + this.random.nextDouble() * 0.5, Math.sin(angle) * speed, 0.1);
            }

            int smokeCount = (int) (160 * mult);
            for (int i = 0; i < smokeCount; i++) {
                double radius = this.random.nextDouble() * 8.0;
                double angle = this.random.nextDouble() * Math.PI * 2;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;
                sendParticlesForced(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x + ox, pos.y + this.random.nextDouble() * 12, pos.z + oz, 1, 0, 0.15, 0, 0.02);
            }

            sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 2, pos.z, (int) (80 * mult), 12,
                    8, 12, 0.03);

            int sparkCount = (int) (120 * mult);
            for (int i = 0; i < sparkCount; i++) {
                double angle1 = this.random.nextDouble() * Math.PI * 2;
                double angle2 = (this.random.nextDouble() - 0.3) * Math.PI;
                double speed = 0.4 + this.random.nextDouble() * 1.0;
                sendParticlesForced(serverLevel, ParticleTypes.FIREWORK, pos.x, pos.y + 0.5, pos.z, 1,
                        Math.cos(angle2) * Math.cos(angle1) * speed,
                        Math.sin(angle2) * speed + 0.2,
                        Math.cos(angle2) * Math.sin(angle1) * speed, 0.1);
            }

            sendParticlesForced(serverLevel, ParticleTypes.LAVA, pos.x, pos.y, pos.z, (int) (60 * mult), 2, 1, 2, 0.1);
            sendParticlesForced(serverLevel, ParticleTypes.CLOUD, pos.x, pos.y + 1, pos.z, (int) (100 * mult), 4, 2, 4,
                    0.05);

            int bigFireCount = (int) (30 * mult);
            for (int i = 0; i < bigFireCount; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double radius = this.random.nextDouble() * 3.0;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;
                double vy = 1.5 + this.random.nextDouble() * 2.0;
                double vx = (this.random.nextDouble() - 0.5) * 0.3;
                double vz = (this.random.nextDouble() - 0.5) * 0.3;
                sendParticlesForced(serverLevel, ParticleTypes.LAVA, pos.x + ox, pos.y + 2, pos.z + oz, 3, vx, vy, vz,
                        0.5);
                sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x + ox, pos.y + 2, pos.z + oz, 5, vx, vy, vz,
                        0.3);
                sendParticlesForced(serverLevel, ParticleTypes.SOUL_FIRE_FLAME, pos.x + ox, pos.y + 1, pos.z + oz, 2,
                        vx, vy * 0.8, vz, 0.2);
            }

            int fireballCount = (int) (15 * mult);
            for (int i = 0; i < fireballCount; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double dist = 1.0 + this.random.nextDouble() * 4.0;
                double ox = Math.cos(angle) * dist;
                double oz = Math.sin(angle) * dist;
                double vy = 2.0 + this.random.nextDouble() * 1.5;
                sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x + ox, pos.y, pos.z + oz, 15, 0.3, vy, 0.3,
                        0.15);
            }
        }

        playRandomExplosionSound(pos);
    }

    private void playRandomExplosionSound(Vec3 pos) {
        SoundEvent[] explosions = {
                ModSounds.EXPLOSION_1.get(),
                ModSounds.EXPLOSION_2.get(),
                ModSounds.EXPLOSION_3.get(),
                ModSounds.EXPLOSION_4.get(),
                ModSounds.EXPLOSION_5.get()
        };

        SoundEvent sound = explosions[this.random.nextInt(explosions.length)];
        this.level().playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS,
                3.0F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide)
            return false;
        if (CreateCompat.isCreateDamageSource(source))
            return false;
        if (this.exploding)
            return false; 
        if (!ShahedConfig.isShahedShootdownEnabled())
            return false;

        Entity directEntity = source.getDirectEntity();

        if (directEntity != null && TacZCompat.isTacZBullet(directEntity)) {

            return false;
        }

        if (directEntity instanceof net.minecraft.world.entity.projectile.Projectile) {
            takeDamage(amount, source.getEntity(), directEntity.position(), "projectile");
            return true;
        }

        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            takeDamage(amount * 2.0f, source.getEntity(), this.position(), "explosion");
            return true;
        }

        takeDamage(amount, source.getEntity() != null ? source.getEntity() : directEntity, this.position(),
                source.getMsgId());
        return true;
    }

    @Override
    public boolean isPickable() {

        if (isOnPaintStand())
            return false;
        return ShahedConfig.isShahedShootdownEnabled();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<ShahedEntity> animationState) {

        if (isOnPaintStand()) {
            return PlayState.STOP;
        }
        if (this.isDiving) {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.shahed.dive"));
        } else {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.shahed.fly"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2.0, 0.3, 2.0);
    }

    private <T extends ParticleOptions> void sendParticlesForced(ServerLevel level, T particle, double x, double y,
            double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }

        com.synarsis.airtacticalarsenal.event.OrlanCameraExplosionHelper.sendParticleToFarCameraPlayers(
                level, particle, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }

    private void spawnFlightParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel))
            return;

        Vec3 pos = this.position();
        Vec3 motion = this.getDeltaMovement();

        boolean hasNearbyPlayer = false;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.position().distanceTo(pos) < 256.0) {
                hasNearbyPlayer = true;
                break;
            }
        }

        if (!hasNearbyPlayer) {
            this.previousY = this.getY();
            return;
        }

        switch (this.flightPhase) {
            case LAUNCHING:

                spawnLaunchParticles(serverLevel, pos, motion);
                break;
            case CLIMBING:

                spawnClimbParticles(serverLevel, pos, motion, this.getY());
                break;
            case CRUISING:

                spawnContrailParticles(serverLevel, pos, motion);
                break;
            case DIVING:

                spawnDiveParticles(serverLevel, pos, motion);
                break;
        }

        this.previousY = this.getY();
    }

    private void spawnLaunchParticles(ServerLevel level, Vec3 pos, Vec3 motion) {

        Vec3 enginePos = pos.subtract(motion.normalize().scale(1.5));

        for (ServerPlayer player : level.players()) {

            level.sendParticles(player, ParticleTypes.FLAME, true,
                    enginePos.x, enginePos.y, enginePos.z,
                    5, 0.2, 0.2, 0.2, 0.08);

            level.sendParticles(player, ParticleTypes.LAVA, true,
                    enginePos.x, enginePos.y, enginePos.z,
                    2, 0.15, 0.15, 0.15, 0.05);

            level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                    enginePos.x - motion.x * 0.5, enginePos.y - motion.y * 0.5, enginePos.z - motion.z * 0.5,
                    3, 0.3, 0.3, 0.3, 0.02);

            if (this.tickCount % 3 == 0) {
                level.sendParticles(player, ParticleTypes.FIREWORK, true,
                        enginePos.x, enginePos.y, enginePos.z,
                        2, 0.1, 0.1, 0.1, 0.15);
            }
        }
    }

    private void spawnClimbParticles(ServerLevel level, Vec3 pos, Vec3 motion, double currentY) {
        Vec3 enginePos = pos.subtract(motion.normalize().scale(1.2));

        double climbStart = this.launchStartY + 30.0; 
        double transitionProgress = (currentY - climbStart) / (this.targetAltitude - climbStart);
        transitionProgress = Math.max(0, Math.min(1, transitionProgress));

        for (ServerPlayer player : level.players()) {

            if (transitionProgress < 0.7) {
                int flameCount = (int) (3 * (1 - transitionProgress));
                if (flameCount > 0) {
                    level.sendParticles(player, ParticleTypes.FLAME, true,
                            enginePos.x, enginePos.y, enginePos.z,
                            flameCount, 0.15, 0.15, 0.15, 0.05);
                }
            }

            int cloudCount = (int) (3 * transitionProgress) + 1;
            level.sendParticles(player, ParticleTypes.CLOUD, true,
                    enginePos.x, enginePos.y, enginePos.z,
                    cloudCount, 0.2, 0.2, 0.2, 0.01);

            level.sendParticles(player, ParticleTypes.SMOKE, true,
                    enginePos.x - motion.x * 0.3, enginePos.y - motion.y * 0.3, enginePos.z - motion.z * 0.3,
                    2, 0.25, 0.25, 0.25, 0.01);
        }
    }

    private void spawnContrailParticles(ServerLevel level, Vec3 pos, Vec3 motion) {

        Vec3 trailPos = pos.subtract(motion.normalize().scale(1.0));

        for (ServerPlayer player : level.players()) {

            level.sendParticles(player, ParticleTypes.CLOUD, true,
                    trailPos.x, trailPos.y, trailPos.z,
                    3, 0.15, 0.15, 0.15, 0.001); 

            level.sendParticles(player, ParticleTypes.CLOUD, true,
                    trailPos.x + (this.random.nextDouble() - 0.5) * 0.3,
                    trailPos.y + (this.random.nextDouble() - 0.5) * 0.3,
                    trailPos.z + (this.random.nextDouble() - 0.5) * 0.3,
                    2, 0.1, 0.1, 0.1, 0.001);

            if (this.tickCount % 2 == 0) {
                level.sendParticles(player, ParticleTypes.SNOWFLAKE, true,
                        trailPos.x, trailPos.y, trailPos.z,
                        1, 0.2, 0.2, 0.2, 0.001);
            }
        }
    }

    private void spawnDiveParticles(ServerLevel level, Vec3 pos, Vec3 motion) {
        Vec3 enginePos = pos.subtract(motion.normalize().scale(0.8));

        for (ServerPlayer player : level.players()) {

            level.sendParticles(player, ParticleTypes.SMOKE, true,
                    enginePos.x, enginePos.y, enginePos.z,
                    3, 0.2, 0.2, 0.2, 0.03);

            level.sendParticles(player, ParticleTypes.FIREWORK, true,
                    pos.x, pos.y, pos.z,
                    2, 0.3, 0.3, 0.3, 0.1);

            if (this.tickCount % 5 == 0) {
                level.sendParticles(player, ParticleTypes.FLAME, true,
                        enginePos.x, enginePos.y, enginePos.z,
                        1, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }

    public String getShahedColor() {
        return this.entityData.get(SHAHED_COLOR);
    }

    public void setShahedColor(String color) {
        this.entityData.set(SHAHED_COLOR, color);
    }

    public void mountPaintStand(PaintStandEntity stand) {
        this.mountedStandId = stand.getId();
        this.mountedStandUuid = stand.getUUID();
        this.entityData.set(DATA_ON_PAINT_STAND, true);
        this.entityData.set(DATA_STAND_YAW, stand.getYRot());
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        updatePaintStandPose(stand);
    }

    public boolean isOnPaintStand() {
        return this.entityData.get(DATA_ON_PAINT_STAND);
    }

    public float getStandYaw() {
        return this.entityData.get(DATA_STAND_YAW);
    }

    private void handlePaintStandAttachment() {
        setDeltaMovement(Vec3.ZERO);
        PaintStandEntity stand = resolvePaintStand();
        if (stand != null) {
            updatePaintStandPose(stand);
        } else {

            if (!level().isClientSide) {
                ItemStack stack = new ItemStack(com.synarsis.airtacticalarsenal.item.ModItems.SHAHED.get());
                CompoundTag tag = stack.getOrCreateTag();
                tag.putString("ShahedColor", getShahedColor());
                spawnAtLocation(stack);
                discard();
            }
        }
    }

    private PaintStandEntity resolvePaintStand() {
        if (mountedStandId > 0) {
            Entity entity = level().getEntity(mountedStandId);
            if (entity instanceof PaintStandEntity stand) {
                return stand;
            }
        }
        if (mountedStandUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(mountedStandUuid);
            if (entity instanceof PaintStandEntity stand) {
                mountedStandId = stand.getId();
                return stand;
            }
        }
        return null;
    }

    private void updatePaintStandPose(PaintStandEntity stand) {
        Vec3 anchor = stand.position().add(0.0, 1.0, 0.0);
        setPos(anchor.x, anchor.y, anchor.z);
        float yaw = stand.getYRot();
        setYRot(yaw);
        this.yRotO = yaw;
        setXRot(0.0f);
        this.xRotO = 0.0f;
        this.entityData.set(DATA_STAND_YAW, yaw);
    }

    public void releaseFromPaintStand() {
        this.entityData.set(DATA_ON_PAINT_STAND, false);
        this.entityData.set(DATA_STAND_YAW, 0.0f);
        this.mountedStandId = -1;
        this.mountedStandUuid = null;
    }
}
