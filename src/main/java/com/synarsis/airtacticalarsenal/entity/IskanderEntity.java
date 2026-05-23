package com.synarsis.airtacticalarsenal.entity;

import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.chunk.MissileChunkManager;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.IskanderSpawnPacket;
import com.synarsis.airtacticalarsenal.network.IskanderExplosionPacket;
import com.synarsis.airtacticalarsenal.network.IskanderRemovePacket;
import com.synarsis.airtacticalarsenal.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class IskanderEntity extends Projectile implements GeoEntity {

    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(IskanderEntity.class,
            EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FLIGHT_PHASE = SynchedEntityData.defineId(IskanderEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT_TICKS = SynchedEntityData.defineId(IskanderEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNC_X_ROT = SynchedEntityData.defineId(IskanderEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNC_Y_ROT = SynchedEntityData.defineId(IskanderEntity.class,
            EntityDataSerializers.FLOAT);

    private static final float EXPLOSION_POWER = 16.0f;
    private static final float EXPLOSION_RADIUS = 40.0f;
    public static final int PHASE_PREPARING = 0;
    public static final int PHASE_LAUNCH = 1;
    public static final int PHASE_ACTIVE = 2;
    public static final int PHASE_BALLISTIC = 3;
    public static final int PHASE_TERMINAL = 4;
    public static final int PHASE_IMPACT = 5;

    private static final double MODEL_Y_OFFSET = 4.0;

    private Vec3 startPos;
    private Vec3 launchEndPos;
    private double totalFlightTime;
    private double apexHeight;
    private double horizontalDistance;
    private Vec3 finalTargetPos;
    private boolean kvoApplied = false;
    private boolean launchEndPosSet = false;

    private static final double MAX_FLIGHT_HEIGHT = 800.0;

    private Vec3 lastLaunchVelocity = Vec3.ZERO;
    private int transitionTicks = 0;
    private static final int TRANSITION_DURATION = 60; 

    private float prevXRot = -90;
    private float prevYRot = 0;
    private float targetXRot = -90;
    private float targetYRot = 0;
    private float savedYawToTarget = 0;
    private static final float ROTATION_SMOOTHING = 0.08f;

    private double maxParabolicHeight = 0; 
    private double ballisticStartY = 0; 
    private double ballisticTargetY = 0; 
    private double ballisticHorizDistance = 0; 
    private int ballisticStartTick = 0; 
    private double totalBallisticTicks = 0; 
    private Vec3 ballisticStartPos = null; 
    private Vec3 ballisticDirection = null; 
    private boolean trajectoryCalculated = false;
    private double speedVariationPhase = 0; 

    private double diveStartProgress = 0.5; 
    private double diveStartY = 0;          
    private double arcHorizDist = 0;        
    private double horizDive = 0;           

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int syncTickCounter = 0;

    private static final int SYNC_INTERVAL = 1;
    private boolean initialPacketSent = false;

    private double lerpX, lerpY, lerpZ;
    private float lerpYRot, lerpXRot;
    private int lerpSteps = 0;

    private BlockPos launcherPos = null;
    private boolean launcherReleased = false;

    private boolean clientFirstSync = true;
    private int clientSyncTicks = 0;
    private int clientFlightTicks = -1;

    public IskanderEntity(EntityType<? extends IskanderEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;

        this.isLegitSpawn = false;

        this.setXRot(-90.0f);
        this.xRotO = -90.0f;
        this.prevXRot = -90.0f;
        this.targetXRot = -90.0f;
    }

    public boolean isLegitSpawn = false;

    public IskanderEntity(Level level, Vec3 startPos, BlockPos targetPos) {
        this(ModEntities.ISKANDER.get(), level);
        this.setPos(startPos);
        this.startPos = startPos;
        this.entityData.set(TARGET_POS, targetPos);
        this.entityData.set(FLIGHT_PHASE, PHASE_PREPARING);
        this.entityData.set(FLIGHT_TICKS, 0);

        calculateTrajectoryParameters(startPos, targetPos);
    }

    public IskanderEntity(Level level, Vec3 startPos, BlockPos targetPos, boolean skipPreparing) {
        this(ModEntities.ISKANDER.get(), level);
        this.setPos(startPos);
        this.startPos = startPos;
        this.entityData.set(TARGET_POS, targetPos);

        if (skipPreparing) {

            this.entityData.set(FLIGHT_PHASE, PHASE_LAUNCH);
            this.entityData.set(FLIGHT_TICKS, ShahedConfig.getIskanderPrepareTicks());

            this.setXRot(-90.0f);
            this.xRotO = -90.0f;
            this.prevXRot = -90.0f;
            this.targetXRot = -90.0f;

            double dx = targetPos.getX() + 0.5 - startPos.x;
            double dz = targetPos.getZ() + 0.5 - startPos.z;
            float yawToTarget = (float) Math.toDegrees(Math.atan2(dx, dz));
            this.setYRot(yawToTarget);
            this.yRotO = yawToTarget;
            this.prevYRot = yawToTarget;
            this.targetYRot = yawToTarget;
            this.savedYawToTarget = yawToTarget;

            this.entityData.set(SYNC_X_ROT, -90.0f);
            this.entityData.set(SYNC_Y_ROT, yawToTarget);
        } else {
            this.entityData.set(FLIGHT_PHASE, PHASE_PREPARING);
            this.entityData.set(FLIGHT_TICKS, 0);
        }

        calculateTrajectoryParameters(startPos, targetPos);
    }

    public void setLauncherPos(BlockPos pos) {
        this.launcherPos = pos;
    }

    private void releaseLauncher() {
        if (!launcherReleased && launcherPos != null && !this.level().isClientSide) {
            BlockState state = this.level().getBlockState(launcherPos);
            if (state.getBlock() instanceof LauncherBlock launcher) {
                launcher.setLoaded(this.level(), launcherPos, false);
                launcher.setLaunching(this.level(), launcherPos, false);
            }

            net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(launcherPos);
            if (be instanceof com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity launcherEntity) {
                launcherEntity.setInFlight(false);
            }
            launcherReleased = true;
        }
    }

    private void calculateTrajectoryParameters(Vec3 start, BlockPos target) {
        double dx = target.getX() - start.x;
        double dz = target.getZ() - start.z;
        this.horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        int groundY = findGroundLevel(target);
        this.finalTargetPos = new Vec3(target.getX() + 0.5, groundY, target.getZ() + 0.5);

        applyKVO();

        double cruiseSpeed = ShahedConfig.getIskanderCruiseSpeed();
        int prepareTicks = ShahedConfig.getIskanderPrepareTicks();
        int launchTicks = ShahedConfig.getIskanderLaunchTicks();

        double ballisticTicks = horizontalDistance / cruiseSpeed;
        this.totalFlightTime = prepareTicks + launchTicks + ballisticTicks;
    }

    public void syncTrajectoryData(Vec3 startPos, Vec3 launchEndPos, Vec3 serverFinalTargetPos) {
        boolean recalculate = false;
        if (this.startPos == null && startPos != null) {
            this.startPos = startPos;
            recalculate = true;
        }
        if (this.launchEndPos == null && launchEndPos != null) {
            this.launchEndPos = launchEndPos;
            this.launchEndPosSet = true;
            recalculate = true;
        }
        if (recalculate && this.startPos != null && this.entityData.get(TARGET_POS) != BlockPos.ZERO) {
            calculateTrajectoryParameters(this.startPos, this.entityData.get(TARGET_POS));

            if (serverFinalTargetPos != null) {
                this.finalTargetPos = serverFinalTargetPos;
                this.kvoApplied = true;
            }
            if (this.launchEndPosSet) {

                if (this.startPos != null && this.finalTargetPos != null) {
                    double ddx = finalTargetPos.x - startPos.x;
                    double ddz = finalTargetPos.z - startPos.z;
                    double dd = Math.sqrt(ddx * ddx + ddz * ddz);
                    Vec3 hDir = dd > 0.01 ? new Vec3(ddx / dd, 0, ddz / dd) : new Vec3(1, 0, 0);
                    double ls = ShahedConfig.getIskanderLaunchSpeed();
                    double cs = ShahedConfig.getIskanderCruiseSpeed();
                    int lt = ShahedConfig.getIskanderLaunchTicks();

                    double tLast = lt > 0 ? (double)(lt - 1) / lt : 1.0;
                    double accelT = Math.min(1.0, tLast * 3.0);
                    double vVert = ls * (0.6 + 0.4 * easeOutQuad(accelT));
                    double vHoriz = 0;
                    if (tLast > 0.60) {
                        double turnT = easeInCubic((tLast - 0.60) / 0.40);
                        vHoriz = turnT * cs * 0.35;
                        vVert *= (1.0 - turnT * 0.25);
                    }
                    this.lastLaunchVelocity = new Vec3(hDir.x * vHoriz, vVert, hDir.z * vHoriz);
                }
                int correctStartTick = ShahedConfig.getIskanderPrepareTicks() + ShahedConfig.getIskanderLaunchTicks();
                calculateBallisticTrajectory(correctStartTick);
            }
        }
    }

    private void calculateBallisticTrajectory(int currentFlightTicks) {
        if (trajectoryCalculated || launchEndPos == null)
            return;

        this.ballisticStartPos = launchEndPos;
        this.ballisticStartY = launchEndPos.y;
        this.ballisticTargetY = finalTargetPos.y;

        double dx = finalTargetPos.x - launchEndPos.x;
        double dz = finalTargetPos.z - launchEndPos.z;
        this.ballisticHorizDistance = Math.sqrt(dx * dx + dz * dz);

        if (this.ballisticHorizDistance > 0.01) {
            this.ballisticDirection = new Vec3(dx / ballisticHorizDistance, 0, dz / ballisticHorizDistance);
        } else {
            this.ballisticDirection = new Vec3(1, 0, 0);
        }

        this.ballisticStartTick = currentFlightTicks;
        double cruiseSpeed = ShahedConfig.getIskanderCruiseSpeed();
        double terminalSpeed = ShahedConfig.getIskanderTerminalSpeed();
        double diveAngleRad = Math.toRadians(ShahedConfig.getIskanderDiveAngle());
        double maxAbsoluteHeight = ShahedConfig.getIskanderMaxFlightHeight();

        double diveHeightGain = maxAbsoluteHeight - this.ballisticTargetY;
        diveHeightGain = Math.max(50.0, diveHeightGain);

        this.horizDive = diveHeightGain / Math.tan(diveAngleRad);

        this.horizDive = Math.min(this.horizDive, this.ballisticHorizDistance * 0.40);

        double actualDiveHeight = this.horizDive * Math.tan(diveAngleRad);
        this.diveStartY = this.ballisticTargetY + actualDiveHeight;
        this.diveStartY = Math.min(this.diveStartY, maxAbsoluteHeight);

        double diveDist = Math.sqrt(this.horizDive * this.horizDive + actualDiveHeight * actualDiveHeight);

        this.arcHorizDist = Math.max(0.0, this.ballisticHorizDistance - this.horizDive);

        double arcTicks = this.arcHorizDist / cruiseSpeed;
        double diveTicks = diveDist / terminalSpeed;
        this.totalBallisticTicks = arcTicks + diveTicks;

        this.diveStartProgress = (this.totalBallisticTicks > 0 && arcTicks > 0)
                ? arcTicks / this.totalBallisticTicks : 0.0;

        double midArcY = (this.ballisticStartY + this.diveStartY) / 2.0;
        this.maxParabolicHeight = Math.max(10.0,
                Math.min(maxAbsoluteHeight - midArcY,
                        (this.diveStartY - this.ballisticStartY) * 0.55));

        this.speedVariationPhase = this.random.nextDouble() * Math.PI * 2;
        this.apexHeight = midArcY + this.maxParabolicHeight;
        this.trajectoryCalculated = true;

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel
                && this.finalTargetPos != null) {
            BlockPos targetBP = new BlockPos(
                    (int) this.finalTargetPos.x, (int) this.finalTargetPos.y, (int) this.finalTargetPos.z);
            MissileChunkManager.preloadTargetArea(this, serverLevel, targetBP);
        }
    }

    private double getTrajectoryHeight(double progress) {
        progress = Math.max(0, Math.min(1, progress));

        if (diveStartProgress > 0.001 && progress <= diveStartProgress) {

            double q = progress / diveStartProgress;
            double parabolicOffset = 4.0 * maxParabolicHeight * q * (1.0 - q);
            double linearHeight = ballisticStartY + (diveStartY - ballisticStartY) * q;
            return linearHeight + parabolicOffset;
        } else {

            double denom = 1.0 - diveStartProgress;
            if (denom < 0.001) return ballisticTargetY;
            double q2 = Math.max(0, Math.min(1, (progress - diveStartProgress) / denom));

            double arcSlopeEnd = (-4.0 * maxParabolicHeight + (diveStartY - ballisticStartY)) / diveStartProgress;
            double m0 = arcSlopeEnd * denom; 

            double diveAngleRad = Math.toRadians(ShahedConfig.getIskanderDiveAngle());
            double m1 = -Math.tan(diveAngleRad) * horizDive;

            double q2_2 = q2 * q2;
            double q2_3 = q2_2 * q2;
            return (2 * q2_3 - 3 * q2_2 + 1) * diveStartY
                    + (q2_3 - 2 * q2_2 + q2) * m0
                    + (-2 * q2_3 + 3 * q2_2) * ballisticTargetY
                    + (q2_3 - q2_2) * m1;
        }
    }

    private double getHorizontalTraveled(double progress) {
        progress = Math.max(0, Math.min(1, progress));
        if (diveStartProgress > 0.001 && progress <= diveStartProgress) {
            return (progress / diveStartProgress) * arcHorizDist;
        } else {
            double denom = 1.0 - diveStartProgress;
            if (denom < 0.001) return ballisticHorizDistance;
            double q2 = Math.max(0, Math.min(1, (progress - diveStartProgress) / denom));
            return arcHorizDist + q2 * horizDive;
        }
    }

    private double getTrajectorySlope(double progress) {
        progress = Math.max(0, Math.min(1, progress));
        if (diveStartProgress > 0.001 && progress <= diveStartProgress) {
            double q = progress / diveStartProgress;
            double parabolicSlope = 4.0 * maxParabolicHeight * (1.0 - 2.0 * q);
            return (parabolicSlope + (diveStartY - ballisticStartY)) / diveStartProgress;
        } else {

            double denom = 1.0 - diveStartProgress;
            if (denom < 0.001) return 0;
            double q2 = Math.max(0, Math.min(1, (progress - diveStartProgress) / denom));

            double arcSlopeEnd = (-4.0 * maxParabolicHeight + (diveStartY - ballisticStartY)) / diveStartProgress;
            double m0 = arcSlopeEnd * denom;
            double diveAngleRad = Math.toRadians(ShahedConfig.getIskanderDiveAngle());
            double m1 = -Math.tan(diveAngleRad) * horizDive;

            double dydq2 = (6 * q2 * q2 - 6 * q2) * diveStartY
                    + (3 * q2 * q2 - 4 * q2 + 1) * m0
                    + (-6 * q2 * q2 + 6 * q2) * ballisticTargetY
                    + (3 * q2 * q2 - 2 * q2) * m1;
            return dydq2 / denom; 
        }
    }

    private int findGroundLevel(BlockPos target) {
        if (this.level() == null)
            return target.getY();

        for (int y = target.getY() + 50; y > target.getY() - 100; y--) {
            BlockPos checkPos = new BlockPos(target.getX(), y, target.getZ());
            if (!this.level().getBlockState(checkPos).isAir()) {
                return y + 1;
            }
        }
        return target.getY();
    }

    private void applyKVO() {
        if (!kvoApplied) {
            double kvoDeviation = ShahedConfig.getIskanderKvoDeviation();
            double offsetX = (this.random.nextDouble() - 0.5) * 2 * kvoDeviation;
            double offsetZ = (this.random.nextDouble() - 0.5) * 2 * kvoDeviation;
            this.finalTargetPos = this.finalTargetPos.add(offsetX, 0, offsetZ);
            kvoApplied = true;
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 640000.0;
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
    protected AABB makeBoundingBox() {
        int phase = this.entityData.get(FLIGHT_PHASE);
        Vec3 pos = this.position();

        if (phase == PHASE_PREPARING) {
            return new AABB(pos.x - 0.4, pos.y, pos.z - 0.4, pos.x + 0.4, pos.y + 4.0, pos.z + 0.4);
        }

        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 0.001) {
            return new AABB(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        }

        Vec3 dir = motion.normalize();
        double length = 2.0;
        double width = 0.4;

        double endX = pos.x + dir.x * length;
        double endY = pos.y + dir.y * length;
        double endZ = pos.z + dir.z * length;

        double minX = Math.min(pos.x, endX) - width;
        double minY = Math.min(pos.y, endY) - width;
        double minZ = Math.min(pos.z, endZ) - width;
        double maxX = Math.max(pos.x, endX) + width;
        double maxY = Math.max(pos.y, endY) + width;
        double maxZ = Math.max(pos.z, endZ) + width;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        this.setBoundingBox(this.makeBoundingBox());
    }

    public void setServerPosition(Vec3 pos) {
        if (this.level().isClientSide) {
            this.lerpX = pos.x;
            this.lerpY = pos.y;
            this.lerpZ = pos.z;
            this.lerpSteps = SYNC_INTERVAL;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_POS, BlockPos.ZERO);
        this.entityData.define(FLIGHT_PHASE, PHASE_PREPARING);
        this.entityData.define(FLIGHT_TICKS, 0);
        this.entityData.define(SYNC_X_ROT, -90.0f);
        this.entityData.define(SYNC_Y_ROT, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);

        if (level() != null && level().isClientSide && clientFirstSync) {
            if (pKey == SYNC_X_ROT) {
                float val = this.entityData.get(SYNC_X_ROT);
                this.setXRot(val);
                this.xRotO = val;
            } else if (pKey == SYNC_Y_ROT) {
                float val = this.entityData.get(SYNC_Y_ROT);
                this.setYRot(val);
                this.yRotO = val;
            }
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }

        super.tick();

        int flightTicks = this.entityData.get(FLIGHT_TICKS);
        int phase = this.entityData.get(FLIGHT_PHASE);
        BlockPos target = this.entityData.get(TARGET_POS);

        if (this.level().isClientSide) {

            if (this.clientFlightTicks < 0) {
                this.clientFlightTicks = flightTicks;
            } else {

                if (Math.abs(this.clientFlightTicks - flightTicks) > 10) {
                    this.clientFlightTicks = flightTicks;
                } else {
                    this.clientFlightTicks++;
                }
            }

            tickClientSide(this.clientFlightTicks, phase, target);
            return;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            MissileChunkManager.updateChunksForMissile(this, serverLevel);
        }

        flightTicks++;
        this.entityData.set(FLIGHT_TICKS, flightTicks);

        updateFlightPhase(flightTicks);
        phase = this.entityData.get(FLIGHT_PHASE);

        Vec3 currentPos = this.position();

        boolean useParabola = (phase >= PHASE_ACTIVE && trajectoryCalculated && ballisticStartPos != null
                && ballisticDirection != null && totalBallisticTicks > 0);

        Vec3 realMotion;
        Vec3 exactPos;
        if (useParabola) {
            int bTicks = flightTicks - ballisticStartTick;
            double prog = Math.min(1.0, (double) bTicks / totalBallisticTicks);
            double traveledH = getHorizontalTraveled(prog);
            exactPos = new Vec3(
                    ballisticStartPos.x + ballisticDirection.x * traveledH,
                    getTrajectoryHeight(prog),
                    ballisticStartPos.z + ballisticDirection.z * traveledH);

            if (bTicks > 0 && bTicks < TRANSITION_DURATION && lastLaunchVelocity.lengthSqr() > 0.001) {
                double tp = easeInOutCubic((double) bTicks / TRANSITION_DURATION);

                Vec3 launchExtrap = ballisticStartPos.add(lastLaunchVelocity.scale(bTicks));
                exactPos = new Vec3(
                        launchExtrap.x + (exactPos.x - launchExtrap.x) * tp,
                        launchExtrap.y + (exactPos.y - launchExtrap.y) * tp,
                        launchExtrap.z + (exactPos.z - launchExtrap.z) * tp);
            }

            realMotion = exactPos.subtract(currentPos);
        } else {
            realMotion = calculateMotion(flightTicks, phase);
            exactPos = currentPos.add(realMotion);
        }

        this.setDeltaMovement(realMotion);

        updateRotation(realMotion, phase);

        if (phase >= PHASE_LAUNCH) {
            if (phase == PHASE_LAUNCH) {

                if (checkCollisionOnPath(currentPos, realMotion)) {
                    this.explode();
                    return;
                }
            } else {

                HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                if (hitResult.getType() != HitResult.Type.MISS) {
                    this.onHit(hitResult);
                    return;
                }
            }
        }

        if (finalTargetPos != null && currentPos.y <= finalTargetPos.y
                && realMotion.y < 0 && phase >= PHASE_TERMINAL) {
            this.explode();
            return;
        }

        if (trajectoryCalculated && totalBallisticTicks > 0 && phase >= PHASE_ACTIVE) {
            int ballisticTicks = flightTicks - ballisticStartTick;
            double progress = (double) ballisticTicks / totalBallisticTicks;
            if (progress >= 1.0) {
                this.explode();
                return;
            }
        }

        this.setPos(exactPos.x, exactPos.y, exactPos.z);

        if (phase >= PHASE_LAUNCH) {
            spawnFlightParticles(phase);
        }

        if (!this.initialPacketSent) {
            this.initialPacketSent = true;
            NetworkHandler.sendToAllPlayers(
                    new IskanderSpawnPacket(this.getId(), this.position(), target, phase, flightTicks, launcherPos,
                            this.startPos, this.launchEndPos, this.finalTargetPos));
        }

        this.syncTickCounter++;
        if (this.syncTickCounter >= SYNC_INTERVAL) {
            this.syncTickCounter = 0;
            NetworkHandler.sendToAllPlayers(
                    new IskanderSpawnPacket(this.getId(), this.position(), target, phase, flightTicks, launcherPos,
                            this.startPos, this.launchEndPos, this.finalTargetPos));

            if (launcherPos != null && phase >= PHASE_LAUNCH) {
                NetworkHandler.sendToAllPlayers(new com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket(
                        launcherPos,
                        com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket.LauncherState.IN_FLIGHT,
                        0, 0, flightTicks));
            }
        }
    }

    private void tickClientSide(int flightTicks, int phase, BlockPos target) {

        Vec3 visualMotion = calculateMotion(flightTicks, phase);

        float syncedXRot = this.entityData.get(SYNC_X_ROT);
        float syncedYRot = this.entityData.get(SYNC_Y_ROT);

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        if (clientFirstSync || clientSyncTicks < 10) {
            this.setXRot(syncedXRot);
            this.setYRot(syncedYRot);
            clientFirstSync = false;
            clientSyncTicks++;
        } else {
            float TURN_RATE_DEG = 5.0f;
            float newYRot = net.minecraft.util.Mth.approachDegrees(this.yRotO, syncedYRot, TURN_RATE_DEG);
            float newXRot = net.minecraft.util.Mth.approachDegrees(this.xRotO, syncedXRot, TURN_RATE_DEG);
            this.setYRot(newYRot);
            this.setXRot(newXRot);
        }

        boolean useParabola = (phase >= PHASE_ACTIVE && trajectoryCalculated && ballisticStartPos != null
                && ballisticDirection != null && totalBallisticTicks > 0);

        Vec3 motionForParticles;
        if (useParabola) {
            int bTicks = flightTicks - ballisticStartTick;
            double prog = Math.min(1.0, (double) bTicks / totalBallisticTicks);
            double traveledH = getHorizontalTraveled(prog);
            Vec3 parabolaPos = new Vec3(
                    ballisticStartPos.x + ballisticDirection.x * traveledH,
                    getTrajectoryHeight(prog),
                    ballisticStartPos.z + ballisticDirection.z * traveledH);

            Vec3 blendedPos;
            if (bTicks > 0 && bTicks < TRANSITION_DURATION && lastLaunchVelocity.lengthSqr() > 0.001) {
                double tp = easeInOutCubic((double) bTicks / TRANSITION_DURATION);
                Vec3 launchExtrap = ballisticStartPos.add(lastLaunchVelocity.scale(bTicks));
                blendedPos = new Vec3(
                        launchExtrap.x + (parabolaPos.x - launchExtrap.x) * tp,
                        launchExtrap.y + (parabolaPos.y - launchExtrap.y) * tp,
                        launchExtrap.z + (parabolaPos.z - launchExtrap.z) * tp);
            } else {
                blendedPos = parabolaPos;
            }

            motionForParticles = new Vec3(blendedPos.x - this.getX(), blendedPos.y - this.getY(), blendedPos.z - this.getZ());
            this.setDeltaMovement(motionForParticles);
            this.setPos(blendedPos.x, blendedPos.y, blendedPos.z);
        } else if (phase < PHASE_ACTIVE) {

            Vec3 motion = visualMotion;
            double maxSpeed = 8.0;
            if (motion.length() > maxSpeed) {
                motion = motion.normalize().scale(maxSpeed);
            }
            motionForParticles = motion;
            this.setDeltaMovement(motion);
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        } else {

            motionForParticles = Vec3.ZERO;
            this.setDeltaMovement(Vec3.ZERO);
        }

        spawnClientParticles(phase, motionForParticles);
    }

    private void spawnClientParticles(int phase, Vec3 motion) {
        if (phase == PHASE_PREPARING)
            return;

        Vec3 visualPos = new Vec3(this.xo, this.yo, this.zo);

        Vec3 enginePos = getEngineOffset(visualPos, motion);
        Vec3 nosePos = getNoseOffset(visualPos, motion);

        if (phase == PHASE_LAUNCH || phase == PHASE_ACTIVE) {
            for (int i = 0; i < 5; i++) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 0.3,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 0.3,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 0.3,
                        -motion.x * 0.05, -motion.y * 0.05, -motion.z * 0.05);
            }
            for (int i = 0; i < 3; i++) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 0.4,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 0.4,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 0.4,
                        -motion.x * 0.02, -motion.y * 0.02, -motion.z * 0.02);
            }
            if (this.tickCount % 2 == 0) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.LAVA, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 0.2,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 0.2,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 0.2,
                        0, 0, 0);
            }
        } else if (phase == PHASE_BALLISTIC) {
            this.level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                    enginePos.x, enginePos.y, enginePos.z, 0, 0, 0);
            if (this.tickCount % 2 == 0) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 0.3,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 0.3,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 0.3,
                        0, 0, 0);
            }
        } else if (phase == PHASE_TERMINAL) {

            for (int i = 0; i < 30; i++) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 0.8,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 0.8,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 0.8,
                        -motion.x * 0.005, -motion.y * 0.005, -motion.z * 0.005);
            }
            for (int i = 0; i < 20; i++) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true,
                        enginePos.x + (this.random.nextDouble() - 0.5) * 1.5,
                        enginePos.y + (this.random.nextDouble() - 0.5) * 1.5,
                        enginePos.z + (this.random.nextDouble() - 0.5) * 1.5,
                        0, 0, 0);
            }
        }
    }

    private void spawnFlightParticles(int phase) {

    }

    private Vec3 getEngineOffset(Vec3 pos, Vec3 motion) {

        Vec3 visualCenter = new Vec3(pos.x, pos.y - MODEL_Y_OFFSET, pos.z);
        if (motion.lengthSqr() > 0.001) {
            return visualCenter.subtract(motion.normalize().scale(1.5));
        }
        return visualCenter.subtract(new Vec3(0, 1.5, 0));
    }

    private Vec3 getNoseOffset(Vec3 pos, Vec3 motion) {
        Vec3 visualCenter = new Vec3(pos.x, pos.y - MODEL_Y_OFFSET, pos.z);
        if (motion.lengthSqr() > 0.001) {
            return visualCenter.add(motion.normalize().scale(1.5));
        }
        return visualCenter.add(new Vec3(0, 1.5, 0));
    }

    private void updateFlightPhase(int flightTicks) {
        int currentPhase = this.entityData.get(FLIGHT_PHASE);
        int newPhase = currentPhase;
        int prepareTicks = ShahedConfig.getIskanderPrepareTicks();
        int launchTicks = ShahedConfig.getIskanderLaunchTicks();

        if (flightTicks < prepareTicks) {
            newPhase = PHASE_PREPARING;
        } else if (flightTicks < prepareTicks + launchTicks) {
            newPhase = PHASE_LAUNCH;
        } else {
            if (!launchEndPosSet) {
                launchEndPos = this.position();
                launchEndPosSet = true;
                transitionTicks = 0;
            }

            double progress;
            if (trajectoryCalculated && totalBallisticTicks > 0) {
                int ballisticTicks = flightTicks - ballisticStartTick;
                progress = Math.min(1.0, (double) ballisticTicks / totalBallisticTicks);
            } else {

                Vec3 currentPos = this.position();
                double distToTarget = Math.sqrt(
                        Math.pow(finalTargetPos.x - currentPos.x, 2) +
                                Math.pow(finalTargetPos.z - currentPos.z, 2));
                double totalHorizDist = Math.sqrt(
                        Math.pow(finalTargetPos.x - launchEndPos.x, 2) +
                                Math.pow(finalTargetPos.z - launchEndPos.z, 2));
                progress = 1.0 - (distToTarget / Math.max(1, totalHorizDist));
            }

            if (progress < 0.25) {
                newPhase = PHASE_ACTIVE;
            } else if (progress < 0.70) {
                newPhase = PHASE_BALLISTIC;
            } else {
                newPhase = PHASE_TERMINAL;
                applyKVO();
            }
        }

        if (newPhase != currentPhase) {
            this.entityData.set(FLIGHT_PHASE, newPhase);
        }
    }

    private Vec3 calculateMotion(int flightTicks, int phase) {
        if (phase == PHASE_PREPARING) {
            return Vec3.ZERO;
        }

        if (this.startPos == null) {
            this.startPos = this.position();
            BlockPos target = this.entityData.get(TARGET_POS);
            calculateTrajectoryParameters(this.startPos, target);
        }

        int prepareTicks = ShahedConfig.getIskanderPrepareTicks();
        int launchTicks = ShahedConfig.getIskanderLaunchTicks();
        double launchSpeed = ShahedConfig.getIskanderLaunchSpeed();
        double cruiseSpeed = ShahedConfig.getIskanderCruiseSpeed();
        double terminalSpeed = ShahedConfig.getIskanderTerminalSpeed();

        Vec3 currentPos = this.position();

        double dx = finalTargetPos.x - startPos.x;
        double dz = finalTargetPos.z - startPos.z;
        double totalDistance = Math.sqrt(dx * dx + dz * dz);
        Vec3 horizontalDir = totalDistance > 0.01 ? new Vec3(dx / totalDistance, 0, dz / totalDistance)
                : new Vec3(1, 0, 0);

        if (phase == PHASE_LAUNCH) {
            int launchTick = flightTicks - prepareTicks;
            double t = (double) launchTick / launchTicks;
            t = Math.max(0, Math.min(1, t));

            double accelT = Math.min(1.0, t * 3.0); 
            double verticalSpeed = launchSpeed * (0.6 + 0.4 * easeOutQuad(accelT));

            double horizontalSpeed = 0;
            if (t > 0.60) {
                double turnT = (t - 0.60) / 0.40; 
                turnT = easeInCubic(turnT); 
                horizontalSpeed = turnT * cruiseSpeed * 0.35;

                verticalSpeed *= (1.0 - turnT * 0.25);
            }

            Vec3 launchVelocity = new Vec3(
                    horizontalDir.x * horizontalSpeed,
                    verticalSpeed,
                    horizontalDir.z * horizontalSpeed);

            this.lastLaunchVelocity = launchVelocity;
            return launchVelocity;
        }

        if (launchEndPos == null) {
            launchEndPos = currentPos;
            launchEndPosSet = true;
            transitionTicks = 0;
            calculateBallisticTrajectory(flightTicks);
        }

        if (!trajectoryCalculated) {
            calculateBallisticTrajectory(flightTicks);
        }

        transitionTicks++;

        int ballisticTicks = flightTicks - ballisticStartTick;
        double flightProgress = Math.min(1.0, (double) ballisticTicks / Math.max(1, totalBallisticTicks));

        double traveledHorizontal = getHorizontalTraveled(flightProgress);
        double targetX = ballisticStartPos.x + ballisticDirection.x * traveledHorizontal;
        double targetZ = ballisticStartPos.z + ballisticDirection.z * traveledHorizontal;

        double targetY = getTrajectoryHeight(flightProgress);

        Vec3 velocity = new Vec3(
                targetX - currentPos.x,
                targetY - currentPos.y,
                targetZ - currentPos.z);

        double speedFactor = 1.0 + 0.125 * Math.sin(ballisticTicks * 0.05 + speedVariationPhase);
        velocity = velocity.scale(speedFactor);

        if (flightProgress > 0.8 && velocity.y < 0) {
            double finalPhase = (flightProgress - 0.8) / 0.2;

            velocity = new Vec3(
                    velocity.x * (1.0 - finalPhase * 0.4),
                    velocity.y * (1.0 + finalPhase * 2.5),
                    velocity.z * (1.0 - finalPhase * 0.4));
        }

        if (transitionTicks < TRANSITION_DURATION && lastLaunchVelocity.lengthSqr() > 0.001) {
            double transitionProgress = (double) transitionTicks / TRANSITION_DURATION;
            double smoothT = easeInOutQuad(transitionProgress);

            velocity = new Vec3(
                    lastLaunchVelocity.x + (velocity.x - lastLaunchVelocity.x) * smoothT,
                    lastLaunchVelocity.y + (velocity.y - lastLaunchVelocity.y) * smoothT,
                    lastLaunchVelocity.z + (velocity.z - lastLaunchVelocity.z) * smoothT);
        }

        return velocity;
    }

    private double easeInOutQuad(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    private double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private double easeOutQuad(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    private double easeInQuad(double t) {
        return t * t;
    }

    private double easeInCubic(double t) {
        return t * t * t;
    }

    private double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private boolean checkCollisionOnPath(Vec3 currentPos, Vec3 motion) {
        if (motion.lengthSqr() < 0.001)
            return false;

        double stepSize = 0.5;
        double motionLength = motion.length();
        int steps = (int) Math.ceil(motionLength / stepSize);
        steps = Math.max(1, Math.min(steps, 20));

        Vec3 stepMotion = motion.scale(1.0 / steps);

        for (int i = 1; i <= steps; i++) {
            double checkX = currentPos.x + stepMotion.x * i;
            double checkY = currentPos.y + stepMotion.y * i;
            double checkZ = currentPos.z + stepMotion.z * i;

            BlockPos checkPos = new BlockPos((int) Math.floor(checkX), (int) Math.floor(checkY),
                    (int) Math.floor(checkZ));

            if (launcherPos != null) {
                int dx = Math.abs(checkPos.getX() - launcherPos.getX());
                int dy = checkPos.getY() - launcherPos.getY();
                int dz = Math.abs(checkPos.getZ() - launcherPos.getZ());
                if (dx <= 1 && dz <= 1 && dy >= 0 && dy <= 2) {
                    continue;
                }
            }

            if (!this.level().getBlockState(checkPos).isAir()) {
                return true;
            }

            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    BlockPos nearPos = checkPos.offset(offsetX, 0, offsetZ);
                    if (launcherPos != null) {
                        int dx = Math.abs(nearPos.getX() - launcherPos.getX());
                        int dy = nearPos.getY() - launcherPos.getY();
                        int dz = Math.abs(nearPos.getZ() - launcherPos.getZ());
                        if (dx <= 1 && dz <= 1 && dy >= 0 && dy <= 2) {
                            continue;
                        }
                    }
                    if (!this.level().getBlockState(nearPos).isAir()) {
                        double blockCenterX = nearPos.getX() + 0.5;
                        double blockCenterZ = nearPos.getZ() + 0.5;
                        double distSq = (checkX - blockCenterX) * (checkX - blockCenterX) +
                                (checkZ - blockCenterZ) * (checkZ - blockCenterZ);
                        if (distSq < 0.64) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void updateRotation(Vec3 motion, int phase) {
        if (phase == PHASE_PREPARING) {
            if (finalTargetPos != null && startPos != null) {
                double dx = finalTargetPos.x - startPos.x;
                double dz = finalTargetPos.z - startPos.z;
                this.savedYawToTarget = (float) Math.toDegrees(Math.atan2(dx, dz));
                this.targetYRot = this.savedYawToTarget;
            }
            this.targetXRot = -90;
        } else if (phase == PHASE_LAUNCH) {

            if (finalTargetPos != null && startPos != null) {
                double dx = finalTargetPos.x - startPos.x;
                double dz = finalTargetPos.z - startPos.z;
                this.savedYawToTarget = (float) Math.toDegrees(Math.atan2(dx, dz));
                this.targetYRot = this.savedYawToTarget;
            }
            double horizontalLength = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontalLength > 0.1) {
                float pitchFromMotion = (float) -Math.toDegrees(Math.atan2(motion.y, horizontalLength));
                pitchFromMotion = Math.max(-90, Math.min(90, pitchFromMotion));
                this.targetXRot = pitchFromMotion;
            } else {
                this.targetXRot = -90;
            }
        } else if (motion.lengthSqr() > 1.0E-6) {
            double horizontalLength = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            if (horizontalLength > 0.05) {
                this.savedYawToTarget = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
            }
            this.targetYRot = this.savedYawToTarget;

            double motionLength = motion.length();
            if (motionLength > 0.01) {
                float pitchFromMotion = (float) -Math.toDegrees(Math.atan2(motion.y, horizontalLength));

                pitchFromMotion = Math.max(-90.0f, Math.min(90.0f, pitchFromMotion));
                this.targetXRot = pitchFromMotion;
            }
        }

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        float turnRate = 4.0f;
        if (phase == PHASE_LAUNCH) {
            turnRate = 8.0f; 
        } else if (phase >= PHASE_BALLISTIC) {
            turnRate = 10.0f; 
        }

        float newYRot = net.minecraft.util.Mth.approachDegrees(this.yRotO, this.targetYRot, turnRate);
        float newXRot = net.minecraft.util.Mth.approachDegrees(this.xRotO, this.targetXRot, turnRate);

        this.setYRot(newYRot);
        this.setXRot(newXRot);

        if (!this.level().isClientSide) {
            this.entityData.set(SYNC_X_ROT, newXRot);
            this.entityData.set(SYNC_Y_ROT, newYRot);
        }
    }

    private float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff > 180)
            diff -= 360;
        while (diff < -180)
            diff += 360;
        return start + diff * t;
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
        if (!this.level().isClientSide) {

            releaseLauncher();

            if (launcherPos != null) {
                NetworkHandler.sendToAllPlayers(new com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket(
                        launcherPos,
                        com.synarsis.airtacticalarsenal.network.LauncherStateUpdatePacket.LauncherState.EMPTY,
                        0, 0, 0));
            }

            Vec3 pos = this.position();
            NetworkHandler.sendToAllPlayers(new IskanderExplosionPacket(pos));

            if (this.level() instanceof ServerLevel serverLevel && ShahedConfig.areExplosionParticlesEnabled()) {
                float mult = ShahedConfig.getParticleMultiplier() / 100.0f;

                sendParticlesForced(serverLevel, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 2, 0, 0, 0, 0);

                int debrisCount = (int) (500 * mult);
                for (int i = 0; i < debrisCount; i++) {
                    double angle1 = this.random.nextDouble() * Math.PI * 2;
                    double angle2 = (this.random.nextDouble() - 0.5) * Math.PI;
                    double speed = 0.6 + this.random.nextDouble() * 2.0;
                    double vx = Math.cos(angle2) * Math.cos(angle1) * speed;
                    double vy = Math.sin(angle2) * speed + 0.4;
                    double vz = Math.cos(angle2) * Math.sin(angle1) * speed;
                    sendParticlesForced(serverLevel, ModParticles.DEBRIS.get(), pos.x, pos.y + 1, pos.z, 1, vx, vy, vz,
                            1.0);
                }

                int flameCount = (int) (300 * mult);
                for (int i = 0; i < flameCount; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2;
                    double speed = 0.4 + this.random.nextDouble() * 1.0;
                    sendParticlesForced(serverLevel, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1,
                            Math.cos(angle) * speed, 0.3 + this.random.nextDouble() * 0.7, Math.sin(angle) * speed,
                            0.15);
                }

                sendParticlesForced(serverLevel, ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 3, pos.z, (int) (120 * mult),
                        15, 10, 15, 0.04);
                sendParticlesForced(serverLevel, ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 5, pos.z,
                        (int) (80 * mult), 8, 5, 8, 0.03);
            }

            Entity owner = this.getOwner();

            AABB explosionBox = new AABB(
                    pos.x - EXPLOSION_RADIUS, pos.y - EXPLOSION_RADIUS, pos.z - EXPLOSION_RADIUS,
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
                    if (distance <= 8.0) {
                        damage = 1000.0f; 
                    } else if (distance <= 16.0) {
                        damage = 250.0f; 
                    } else if (distance <= 26.0) {
                        damage = 80.0f;  
                    } else if (distance <= 36.0) {
                        damage = 30.0f;  
                    }
                    if (damage > 0.0f) {
                        damage *= (float) ShahedConfig.getIskanderPlayerDamageMultiplier();
                        targetPlayer.hurt(damageSource, damage);
                        targetPlayer.hurtMarked = true;
                    }
                } else if (com.synarsis.airtacticalarsenal.compat.SuperbWarfareCompat.isVehicle(entity)) {

                    float damage;
                    if (distance <= 4.0) {
                        damage = 1500.0f; 
                    } else if (distance <= 12.0) {
                        damage = 900.0f;  
                    } else if (distance <= 22.0) {
                        damage = 450.0f;  
                    } else {
                        damage = 200.0f;  
                    }
                    damage *= (float) ShahedConfig.getIskanderVehicleDamageMultiplier();
                    entity.hurt(damageSource, damage);
                    entity.hurtMarked = true;
                } else {

                    float damage;
                    if (distance <= 4.0) {
                        damage = 1500.0f;
                    } else if (distance <= 12.0) {
                        damage = 900.0f;
                    } else if (distance <= 22.0) {
                        damage = 450.0f;
                    } else {
                        damage = 200.0f;
                    }
                    damage *= (float) ShahedConfig.getIskanderEntityDamageMultiplier();
                    entity.hurt(damageSource, damage);
                    entity.hurtMarked = true;
                }
            }

            LivingEntity explosionOwner = (owner instanceof LivingEntity) ? (LivingEntity) owner : null;
            if (ShahedConfig.doExplosionsBreakBlocks()) {

                this.level().explode(this, this.damageSources().explosion(this, explosionOwner), null,
                        pos.x, pos.y, pos.z, EXPLOSION_POWER, false, Level.ExplosionInteraction.TNT);

                float smoothPower = EXPLOSION_POWER * 0.4f;
                double off = 3.0;
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

            MissileChunkManager.unregisterMissile(this);
            NetworkHandler.sendToAllPlayers(new IskanderRemovePacket(this.getId()));
        }
        super.remove(reason);
    }

    public int getFlightPhase() {
        return this.entityData.get(FLIGHT_PHASE);
    }

    public int getFlightTicks() {
        return this.entityData.get(FLIGHT_TICKS);
    }

    public BlockPos getTargetPos() {
        return this.entityData.get(TARGET_POS);
    }

    public Vec3 getStartPos() { return startPos; }
    public Vec3 getLaunchEndPos() { return launchEndPos; }
    public Vec3 getFinalTargetPos() { return finalTargetPos; }

    public String getRadarSignature() {
        int phase = getFlightPhase();
        if (phase == PHASE_LAUNCH || phase == PHASE_ACTIVE) {
            return "СВЕЧА";
        } else if (phase == PHASE_TERMINAL) {
            return "ПИКИРОВАНИЕ";
        }
        return "БАЛЛИСТИЧЕСКАЯ";
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos target = this.entityData.get(TARGET_POS);
        tag.putInt("TargetX", target.getX());
        tag.putInt("TargetY", target.getY());
        tag.putInt("TargetZ", target.getZ());
        tag.putInt("FlightPhase", this.entityData.get(FLIGHT_PHASE));
        tag.putInt("FlightTicks", this.entityData.get(FLIGHT_TICKS));
        tag.putBoolean("KvoApplied", this.kvoApplied);
        if (this.startPos != null) {
            tag.putDouble("StartX", this.startPos.x);
            tag.putDouble("StartY", this.startPos.y);
            tag.putDouble("StartZ", this.startPos.z);
        }
        if (this.finalTargetPos != null) {
            tag.putDouble("FinalTargetX", this.finalTargetPos.x);
            tag.putDouble("FinalTargetY", this.finalTargetPos.y);
            tag.putDouble("FinalTargetZ", this.finalTargetPos.z);
        }
        tag.putDouble("TotalFlightTime", this.totalFlightTime);
        tag.putDouble("ApexHeight", this.apexHeight);
        tag.putDouble("HorizontalDistance", this.horizontalDistance);
        tag.putBoolean("LaunchEndPosSet", this.launchEndPosSet);
        if (this.launchEndPos != null) {
            tag.putDouble("LaunchEndX", this.launchEndPos.x);
            tag.putDouble("LaunchEndY", this.launchEndPos.y);
            tag.putDouble("LaunchEndZ", this.launchEndPos.z);
        }
        tag.putInt("TransitionTicks", this.transitionTicks);
        if (this.lastLaunchVelocity != null) {
            tag.putDouble("LastLaunchVelX", this.lastLaunchVelocity.x);
            tag.putDouble("LastLaunchVelY", this.lastLaunchVelocity.y);
            tag.putDouble("LastLaunchVelZ", this.lastLaunchVelocity.z);
        }

        tag.putDouble("MaxParabolicHeight", this.maxParabolicHeight);
        tag.putDouble("BallisticStartY", this.ballisticStartY);
        tag.putDouble("BallisticTargetY", this.ballisticTargetY);
        tag.putDouble("BallisticHorizDistance", this.ballisticHorizDistance);
        tag.putInt("BallisticStartTick", this.ballisticStartTick);
        tag.putDouble("TotalBallisticTicks", this.totalBallisticTicks);
        if (this.ballisticStartPos != null) {
            tag.putDouble("BallisticStartPosX", this.ballisticStartPos.x);
            tag.putDouble("BallisticStartPosY", this.ballisticStartPos.y);
            tag.putDouble("BallisticStartPosZ", this.ballisticStartPos.z);
        }
        if (this.ballisticDirection != null) {
            tag.putDouble("BallisticDirX", this.ballisticDirection.x);
            tag.putDouble("BallisticDirZ", this.ballisticDirection.z);
        }
        tag.putBoolean("TrajectoryCalculated", this.trajectoryCalculated);

        tag.putDouble("DiveStartProgress", this.diveStartProgress);
        tag.putDouble("DiveStartY", this.diveStartY);
        tag.putDouble("ArcHorizDist", this.arcHorizDist);
        tag.putDouble("HorizDive", this.horizDive);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        BlockPos target = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        this.entityData.set(TARGET_POS, target);
        this.entityData.set(FLIGHT_PHASE, tag.getInt("FlightPhase"));
        this.entityData.set(FLIGHT_TICKS, tag.getInt("FlightTicks"));
        this.kvoApplied = tag.getBoolean("KvoApplied");
        if (tag.contains("StartX")) {
            this.startPos = new Vec3(tag.getDouble("StartX"), tag.getDouble("StartY"), tag.getDouble("StartZ"));
        }
        if (tag.contains("FinalTargetX")) {
            this.finalTargetPos = new Vec3(tag.getDouble("FinalTargetX"), tag.getDouble("FinalTargetY"),
                    tag.getDouble("FinalTargetZ"));
        }
        this.totalFlightTime = tag.getDouble("TotalFlightTime");
        this.apexHeight = tag.getDouble("ApexHeight");
        this.horizontalDistance = tag.getDouble("HorizontalDistance");
        this.launchEndPosSet = tag.getBoolean("LaunchEndPosSet");
        if (tag.contains("LaunchEndX")) {
            this.launchEndPos = new Vec3(tag.getDouble("LaunchEndX"), tag.getDouble("LaunchEndY"),
                    tag.getDouble("LaunchEndZ"));
        }
        this.transitionTicks = tag.getInt("TransitionTicks");
        if (tag.contains("LastLaunchVelX")) {
            this.lastLaunchVelocity = new Vec3(tag.getDouble("LastLaunchVelX"), tag.getDouble("LastLaunchVelY"),
                    tag.getDouble("LastLaunchVelZ"));
        }

        if (tag.contains("MaxParabolicHeight")) {
            this.maxParabolicHeight = tag.getDouble("MaxParabolicHeight");
            this.ballisticStartY = tag.getDouble("BallisticStartY");
            this.ballisticTargetY = tag.getDouble("BallisticTargetY");
            this.ballisticHorizDistance = tag.getDouble("BallisticHorizDistance");
            this.ballisticStartTick = tag.getInt("BallisticStartTick");
            this.totalBallisticTicks = tag.getDouble("TotalBallisticTicks");
            if (tag.contains("BallisticStartPosX")) {
                this.ballisticStartPos = new Vec3(
                        tag.getDouble("BallisticStartPosX"),
                        tag.getDouble("BallisticStartPosY"),
                        tag.getDouble("BallisticStartPosZ"));
            }
            if (tag.contains("BallisticDirX")) {
                this.ballisticDirection = new Vec3(
                        tag.getDouble("BallisticDirX"),
                        0,
                        tag.getDouble("BallisticDirZ"));
            }
            this.trajectoryCalculated = tag.getBoolean("TrajectoryCalculated");

            if (tag.contains("DiveStartProgress")) {
                this.diveStartProgress = tag.getDouble("DiveStartProgress");
                this.diveStartY = tag.getDouble("DiveStartY");
                this.arcHorizDist = tag.getDouble("ArcHorizDist");
                this.horizDive = tag.getDouble("HorizDive");
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<IskanderEntity> animationState) {
        int phase = this.entityData.get(FLIGHT_PHASE);
        if (phase == PHASE_PREPARING) {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.iskander.idle"));
        } else if (phase == PHASE_LAUNCH || phase == PHASE_ACTIVE) {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.iskander.launch"));
        } else {
            animationState.getController().setAnimation(RawAnimation.begin().thenLoop("animation.iskander.flight"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(4.0, 8.0, 4.0);
    }

    private <T extends ParticleOptions> void sendParticlesForced(ServerLevel level, T particle, double x, double y,
            double z, int count, double xOffset, double yOffset, double zOffset, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }

        com.synarsis.airtacticalarsenal.event.OrlanCameraExplosionHelper.sendParticleToFarCameraPlayers(
                level, particle, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }

    private void spawnPreparingParticles(ServerLevel level, Vec3 pos) {
        if (this.tickCount % 3 == 0) {
            for (ServerPlayer player : level.players()) {
                level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                        pos.x, pos.y - 0.5 - MODEL_Y_OFFSET, pos.z,
                        2, 0.3, 0.1, 0.3, 0.01);
                level.sendParticles(player, ParticleTypes.SMOKE, true,
                        pos.x, pos.y - 0.3 - MODEL_Y_OFFSET, pos.z,
                        3, 0.4, 0.2, 0.4, 0.02);
            }
        }
    }

    private void spawnIskanderLaunchParticles(ServerLevel level, Vec3 pos, Vec3 motion) {
        Vec3 enginePos = getEngineOffset(pos, motion);

        sendParticlesForced(level, ParticleTypes.FLAME,
                enginePos.x, enginePos.y, enginePos.z,
                6, 0.25, 0.25, 0.25, 0.1);

        sendParticlesForced(level, ParticleTypes.LAVA,
                enginePos.x, enginePos.y, enginePos.z,
                3, 0.2, 0.2, 0.2, 0.08);

        Vec3 smokePos = getEngineOffset(pos, motion);
        if (motion.lengthSqr() > 0.001) {
            smokePos = smokePos.subtract(motion.normalize().scale(0.5));
        }
        sendParticlesForced(level, ParticleTypes.LARGE_SMOKE,
                smokePos.x, smokePos.y, smokePos.z,
                5, 0.4, 0.4, 0.4, 0.03);

        if (this.tickCount % 2 == 0) {
            sendParticlesForced(level, ParticleTypes.FIREWORK,
                    enginePos.x, enginePos.y, enginePos.z,
                    3, 0.15, 0.15, 0.15, 0.2);
        }
    }

    private void spawnIskanderContrailParticles(ServerLevel level, Vec3 pos, Vec3 motion, double currentY) {
        Vec3 trailPos = getEngineOffset(pos, motion);
        double heightFactor = Math.min(1.0, Math.max(0, (currentY - 100) / 100.0));

        if (currentY >= 200) {
            sendParticlesForced(level, ParticleTypes.CLOUD,
                    trailPos.x, trailPos.y, trailPos.z,
                    4, 0.2, 0.2, 0.2, 0.001);

            if (this.tickCount % 2 == 0) {
                sendParticlesForced(level, ParticleTypes.SNOWFLAKE,
                        trailPos.x, trailPos.y, trailPos.z,
                        2, 0.25, 0.25, 0.25, 0.001);
            }
        } else if (currentY >= 100) {
            int cloudCount = (int) (2 + 3 * heightFactor);
            sendParticlesForced(level, ParticleTypes.CLOUD,
                    trailPos.x, trailPos.y, trailPos.z,
                    cloudCount, 0.15, 0.15, 0.15, 0.005);
        } else {
            if (this.tickCount % 3 == 0) {
                sendParticlesForced(level, ParticleTypes.SMOKE,
                        trailPos.x, trailPos.y, trailPos.z,
                        2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private void spawnIskanderTerminalParticles(ServerLevel level, Vec3 pos, Vec3 motion) {
        Vec3 nosePos = getNoseOffset(pos, motion);
        Vec3 enginePos = getEngineOffset(pos, motion);

        sendParticlesForced(level, ParticleTypes.FLAME,
                nosePos.x, nosePos.y, nosePos.z,
                3, 0.15, 0.15, 0.15, 0.05);

        sendParticlesForced(level, ParticleTypes.FIREWORK,
                pos.x, pos.y - MODEL_Y_OFFSET, pos.z,
                4, 0.3, 0.3, 0.3, 0.15);

        sendParticlesForced(level, ParticleTypes.SMOKE,
                enginePos.x, enginePos.y, enginePos.z,
                3, 0.2, 0.2, 0.2, 0.02);

        if (this.tickCount % 3 == 0) {
            sendParticlesForced(level, ParticleTypes.LAVA,
                    nosePos.x, nosePos.y, nosePos.z,
                    2, 0.1, 0.1, 0.1, 0.1);
        }
    }
}
