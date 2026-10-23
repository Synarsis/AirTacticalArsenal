package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.CrewAccess;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelHeadlights;
import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.PatriotItem;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.PatriotImpactPacket;
import com.synarsis.airtacticalarsenal.network.PatriotPosePacket;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import com.synarsis.airtacticalarsenal.vehicle.physics.WheelSpin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class M983TractorEntity extends Entity implements GeoEntity {

    public static final float DEFAULT_HEALTH = 300.0f;
    public static final double PANEL_RANGE = 6.0;
    public static final byte CMD_HEADLIGHTS = 0;
    public static final byte CMD_UNLOCK = 1;
    public static final byte IMPACT_HORIZONTAL = 0;
    public static final byte IMPACT_VERTICAL = 1;

    private static final int NO_OCCUPANT = -1;
    private static final int REPAIR_DELAY_TICKS = 10;
    private static final int IMPACT_COOLDOWN_TICKS = 4;
    private static final int DOOR_TICKS = 36;
    private static final int COUPLING_TICKS = 24;
    private static final int HINT_REFRESH_TICKS = 2;
    private static final float BURNING_FRACTION = 0.1f;
    private static final float BURNING_LOSS_PER_TICK = 0.1f;
    private static final float ARMOUR = 2.0f;
    private static final String CLIP_PREFIX = "animation.patriot_m983_tractor.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(CLIP_PREFIX + "idle");
    private static final RawAnimation WHEELS = RawAnimation.begin().thenLoop(CLIP_PREFIX + "wheel_cycle");
    private static final RawAnimation DOORS_OPEN = RawAnimation.begin().thenPlay(CLIP_PREFIX + "doors_open");
    private static final RawAnimation DOORS_CLOSE = RawAnimation.begin().thenPlay(CLIP_PREFIX + "doors_close");
    private static final RawAnimation COUPLING_LOCK = RawAnimation.begin().thenPlay(CLIP_PREFIX + "coupling_lock");
    private static final RawAnimation COUPLING_UNLOCK = RawAnimation.begin().thenPlay(CLIP_PREFIX + "coupling_unlock");

    public enum CouplingState {
        FREE,
        LOCKING,
        COUPLED,
        UNLOCKING;

        public static CouplingState byOrdinal(int ordinal) {
            CouplingState[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FREE;
        }
    }

    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_HEADLIGHTS = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_SEAT_0 = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_1 = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUPLING_STATE = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUPLING_TICKS = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TRAILER_ID = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MISSING_TRAILER_HOLD = SynchedEntityData
            .defineId(M983TractorEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final TractorPart[] parts;
    private final Map<UUID, Integer> savedSeats = new HashMap<>();

    public boolean isLegitSpawn;
    @Nullable
    private UUID ownerUuid;
    public float roll;
    public float rollO;
    public final float[] spinNow = new float[4];
    public final float[] spinO = new float[4];
    public final float[] wheelLift = new float[4];
    public float steerNow;
    public float speedKmh;
    private double testThrottle;
    private double testSteer;
    private double engineRpm;
    private int gear;
    private double engineLoad;
    private int repairCooldown;
    private int impactCooldown;
    private int lastImpactTick = -100;
    private boolean destroyed;
    private boolean joltDisabled;
    private int doorTicks;
    private boolean doorsOpening;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    @Nullable
    private Vec3 lastTickPos;
    private Vec3 serverVelocity = Vec3.ZERO;
    private Vec3 observedVelocity = Vec3.ZERO;
    private double observedYawRate;
    @Nullable
    private DamageSource lastExplosionSource;
    private int lastExplosionTick = -100;
    private float poseSentYaw = Float.NaN;
    private float poseSentPitch = Float.NaN;
    private float poseSentRoll = Float.NaN;
    private float poseSentArtYaw = Float.NaN;
    private int overturnedTicks;
    private int overturnHintCooldown;
    @Nullable
    private TractorJolt jolt;
    private int couplingTicks;
    private int captureTicks;
    private int couplingScanCooldown;
    private double lockStartX;
    private double lockStartY;
    private double lockStartZ;
    private float lockYaw;
    private float lockPitch;
    private float lockRoll;
    private double lockTargetX;
    private double lockTargetY;
    private double lockTargetZ;
    @Nullable
    private UUID coupledTrailerUuid;
    private float storedArticulationYaw;
    private boolean partnerKnown;
    private double partnerX;
    private double partnerY;
    private double partnerZ;
    private int absentTicks;
    @Nullable
    private MissingTrailerPose missingTrailerPose;

    private record MissingTrailerPose(Vec3 position, float yaw, float pitch, float roll, boolean grounded) {
    }
    @Nullable
    private UUID uncoupledFrom;
    private double uncoupleX;
    private double uncoupleZ;
    @Nullable
    private M983Coupling.Hint hintCache;
    private int hintTick = -1;

    public M983TractorEntity(EntityType<? extends M983TractorEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.parts = new TractorPart[TractorGeometry.PART_COUNT];
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i] = new TractorPart(this, i);
        }
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, DEFAULT_HEALTH);
        builder.define(DATA_ROLL, 0.0f);
        builder.define(DATA_HEADLIGHTS, (byte) 0);
        builder.define(DATA_SEAT_0, NO_OCCUPANT);
        builder.define(DATA_SEAT_1, NO_OCCUPANT);
        builder.define(DATA_COUPLING_STATE, CouplingState.FREE.ordinal());
        builder.define(DATA_COUPLING_TICKS, 0);
        builder.define(DATA_TRAILER_ID, -1);
        builder.define(DATA_MISSING_TRAILER_HOLD, false);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (this.parts != null) {
            for (int i = 0; i < this.parts.length; i++) {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    public void updateParts() {
        for (int i = 0; i < this.parts.length; i++) {
            Vec3 p = TractorGeometry.partPosition(i, this.getX(), this.getY(), this.getZ(), this.getYRot(),
                    this.getXRot(), this.roll);
            this.parts[i].setPos(p.x, p.y, p.z);
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }
        this.enforceMissingTrailerHold();
        this.rollO = this.roll;
        for (int i = 0; i < this.spinNow.length; i++) {
            this.spinO[i] = this.spinNow[i];
        }
        if (this.impactCooldown > 0) {
            this.impactCooldown--;
        }
        if (this.doorTicks > 0) {
            this.doorTicks--;
            if (this.doorTicks == 0 && this.doorsOpening) {
                this.doorsOpening = false;
                this.doorTicks = DOOR_TICKS;
            }
        }
        this.roll = this.entityData.get(DATA_ROLL);
        super.tick();
        this.tickLerp();
        boolean simulating = this.isControlledByLocalInstance();
        if (this.useJolt()) {
            this.tickJolt(simulating);
        } else {
            this.dropJolt();
            if (this.level().isClientSide) {
                this.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (this.level().isClientSide) {
            this.tickClient(simulating);
            TractorInput.clientTick.accept(this);
        } else {
            this.tickServer(simulating);
        }
        this.updateParts();
    }

    private void tickJolt(boolean simulating) {
        if (this.jolt == null) {
            this.jolt = new TractorJolt(this);
        }
        boolean client = this.level().isClientSide;
        boolean driven = this.getControllingPassenger() != null;
        int keys = client && driven && simulating ? TractorInput.provider.getAsInt() : 0;
        Vec3 velocity = client ? this.observedVelocity : this.serverVelocity;
        double yawRate = client ? this.observedYawRate
                : -Math.toRadians(Mth.wrapDegrees(this.getYRot() - this.yRotO)) * Units.TICKS_PER_SECOND;
        CouplingState state = this.couplingState();
        this.jolt.setTrainTrailer(state == CouplingState.COUPLED || state == CouplingState.UNLOCKING
                ? this.coupledTrailer() : null);
        this.jolt.update(simulating, driven, this.couplingHoldsTrain(), keys, this.testThrottle, this.testSteer,
                velocity, yawRate);
        if (this.jolt.failed()) {
            this.joltDisabled = true;
            this.dropJolt();
            return;
        }
        this.jolt.applyStagedPose();
        if (client && !simulating) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        if (client && simulating && this.jolt.role() == TractorJolt.Role.SIMULATE) {
            this.sendPoseIfChanged();
        }
    }

    public CouplingState couplingState() {
        return CouplingState.byOrdinal(this.entityData.get(DATA_COUPLING_STATE));
    }

    public int couplingTicks() {
        return this.entityData.get(DATA_COUPLING_TICKS);
    }

    @Nullable
    public PatriotVehicleEntity coupledTrailer() {
        int id = this.entityData.get(DATA_TRAILER_ID);
        if (id >= 0) {
            Entity entity = this.level().getEntity(id);
            if (entity instanceof PatriotVehicleEntity trailer && !trailer.isRemoved()
                    && (this.coupledTrailerUuid == null || this.coupledTrailerUuid.equals(trailer.getUUID()))) {
                return trailer;
            }
        }
        if (this.coupledTrailerUuid != null && this.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(this.coupledTrailerUuid);
            if (entity instanceof PatriotVehicleEntity trailer && !trailer.isRemoved()) {
                this.entityData.set(DATA_TRAILER_ID, trailer.getId());
                return trailer;
            }
        }
        return null;
    }

    @Nullable
    public M983Coupling.Hint couplingHint() {
        if (this.couplingState() != CouplingState.FREE) {
            return null;
        }
        if (this.hintTick < 0 || this.tickCount - this.hintTick >= HINT_REFRESH_TICKS
                || this.tickCount < this.hintTick) {
            this.hintCache = M983Coupling.hint(this);
            this.hintTick = this.tickCount;
        }
        return this.hintCache;
    }

    public boolean slowForCoupling() {
        return M983Coupling.slowEnough(this.speedKmh / 3.6);
    }

    public boolean canUncoupleNow() {
        return this.uncoupleVerdict() == M983Coupling.Uncouple.OK;
    }

    public M983Coupling.Uncouple uncoupleVerdict() {
        PatriotVehicleEntity trailer = this.couplingState() == CouplingState.COUPLED ? this.coupledTrailer() : null;
        double speed = this.level().isClientSide ? this.speedKmh / 3.6
                : Math.hypot(this.serverVelocity.x, this.serverVelocity.z) * Units.TICKS_PER_SECOND;
        return M983Coupling.uncouple(trailer != null, trailer != null && trailer.supportsOutNow(),
                trailer != null && trailer.operationBusy(), speed);
    }

    @Nullable
    public String uncoupleReasonKey() {
        return this.uncoupleVerdict().panelKey();
    }

    private boolean couplingHoldsTrain() {
        if (this.entityData.get(DATA_MISSING_TRAILER_HOLD)) {
            return true;
        }
        CouplingState state = this.couplingState();
        if (state == CouplingState.LOCKING || state == CouplingState.UNLOCKING) {
            return true;
        }
        if (state != CouplingState.COUPLED) {
            return false;
        }
        PatriotVehicleEntity trailer = this.coupledTrailer();
        return trailer == null || !trailer.canBeTowedNow();
    }

    private void tickCouplingServer() {
        if (this.destroyed) {
            return;
        }
        CouplingState state = this.couplingState();
        if (state == CouplingState.FREE) {
            this.tickCapture();
        } else if (state == CouplingState.LOCKING) {
            this.tickLocking();
        } else if (state == CouplingState.UNLOCKING) {
            this.tickUnlocking();
        } else {
            this.tickCoupled();
        }
    }

    private void tickCapture() {
        if (this.uncoupledFrom != null && Math.hypot(this.getX() - this.uncoupleX, this.getZ() - this.uncoupleZ)
                > M983Coupling.RECOUPLE_DISTANCE) {
            this.uncoupledFrom = null;
        }
        double speed = Math.hypot(this.serverVelocity.x, this.serverVelocity.z) * Units.TICKS_PER_SECOND;
        this.captureTicks = M983Coupling.slowEnough(speed) ? this.captureTicks + 1 : 0;
        if (!M983Coupling.heldStill(this.captureTicks)) {
            return;
        }
        if (this.couplingScanCooldown > 0) {
            this.couplingScanCooldown--;
            return;
        }
        this.couplingScanCooldown = 2;
        PatriotVehicleEntity trailer = null;
        double best = M983Coupling.HINT_RANGE;
        for (PatriotVehicleEntity candidate : this.level().getEntitiesOfClass(PatriotVehicleEntity.class,
                this.getBoundingBox().inflate(M983Coupling.HINT_RANGE))) {
            if (!candidate.canBeCoupledNow() || candidate.getUUID().equals(this.uncoupledFrom)) {
                continue;
            }
            Vec3 saddle = M983Coupling.saddleWorld(this);
            Vec3 kingpin = M983Coupling.kingpinWorld(candidate);
            double distance = Math.hypot(kingpin.x - saddle.x, kingpin.z - saddle.z);
            if (distance < best) {
                best = distance;
                trailer = candidate;
            }
        }
        if (trailer == null || !M983Coupling.canCapture(this, trailer)) {
            return;
        }
        this.startLocking(trailer);
    }

    private void startLocking(PatriotVehicleEntity trailer) {
        Vec3 kingpin = M983Coupling.kingpinWorld(trailer);
        float yaw = this.getYRot();
        float pitch = this.getXRot();
        float roll = this.roll;
        double[][] r = PoseMath.rotation(yaw, pitch, roll);
        double[] saddle = PoseMath.apply(r, M983Coupling.saddleAnchor());
        double[] origin = new double[] {kingpin.x - saddle[0], kingpin.y - saddle[1], kingpin.z - saddle[2]};
        Vec3 target = M983Coupling.entityPosForBodyOrigin(origin, yaw, pitch, roll);
        this.lockStartX = this.getX();
        this.lockStartY = this.getY();
        this.lockStartZ = this.getZ();
        this.lockYaw = yaw;
        this.lockPitch = pitch;
        this.lockRoll = roll;
        this.lockTargetX = target.x;
        this.lockTargetY = target.y;
        this.lockTargetZ = target.z;
        this.coupledTrailerUuid = trailer.getUUID();
        this.entityData.set(DATA_TRAILER_ID, trailer.getId());
        this.rememberTrailer(trailer);
        trailer.setHitched(true, this.getId(), this.getUUID());
        trailer.dropJoltBody();
        this.couplingTicks = 0;
        this.captureTicks = 0;
        this.setCoupling(CouplingState.LOCKING, 0);
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), ModSounds.TEL_COVERS.get(),
                SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    private void tickLocking() {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer == null) {
            this.evaluatePartner(false);
            return;
        }
        this.rememberTrailer(trailer);
        this.couplingTicks++;
        double t = Mth.clamp((double) this.couplingTicks / COUPLING_TICKS, 0.0, 1.0);
        double smooth = t * t * (3.0 - 2.0 * t);
        this.setPos(Mth.lerp(smooth, this.lockStartX, this.lockTargetX),
                Mth.lerp(smooth, this.lockStartY, this.lockTargetY),
                Mth.lerp(smooth, this.lockStartZ, this.lockTargetZ));
        this.setYRot(this.lockYaw);
        this.setXRot(this.lockPitch);
        this.setRoll(this.lockRoll);
        this.setDeltaMovement(Vec3.ZERO);
        this.setCoupling(CouplingState.LOCKING, this.couplingTicks);
        if (this.couplingTicks >= COUPLING_TICKS) {
            this.finishLocking();
        }
    }

    private void finishLocking() {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer == null) {
            this.evaluatePartner(false);
            return;
        }
        this.setPos(this.lockTargetX, this.lockTargetY, this.lockTargetZ);
        this.storedArticulationYaw = (float) M983Coupling.bend(this.getYRot(), trailer.getYRot());
        M983Coupling.applyTrailerPose(trailer, this, this.storedArticulationYaw);
        this.couplingTicks = 0;
        this.setCoupling(CouplingState.COUPLED, 0);
        this.messageCrew("message.ata.m983.coupled");
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), ModSounds.TEL_COVERS.get(),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    private void tickCoupled() {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer == null) {
            this.evaluatePartner(false);
            return;
        }
        this.rememberTrailer(trailer);
        trailer.setHitched(true, this.getId(), this.getUUID());
        if (this.jolt != null && this.jolt.role() == TractorJolt.Role.SIMULATE && this.jolt.hitchLinked()) {
            this.storedArticulationYaw = (float) this.jolt.articulation()[0];
        } else if (!this.joltDrivesPose()) {
            M983Coupling.applyTrailerPose(trailer, this, this.storedArticulationYaw);
        }
    }

    private void evaluatePartner(boolean destroyed) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean chunkTicks = this.partnerKnown
                && serverLevel.isPositionEntityTicking(BlockPos.containing(this.partnerX, this.partnerY, this.partnerZ));
        this.absentTicks = M983Coupling.nextAbsent(this.absentTicks, false, this.partnerKnown, chunkTicks);
        if (M983Coupling.partner(false, this.partnerKnown, chunkTicks, this.absentTicks, destroyed)
                == M983Coupling.Partner.GONE) {
            this.releaseCoupling(false);
        }
    }

    public boolean waitingForTrailer() {
        return this.entityData.get(DATA_MISSING_TRAILER_HOLD);
    }

    public boolean enforceMissingTrailerHold() {
        if (this.level().isClientSide) {
            return this.waitingForTrailer();
        }
        boolean missing = this.couplingState() != CouplingState.FREE && this.coupledTrailer() == null;
        if (!missing) {
            this.missingTrailerPose = null;
            this.entityData.set(DATA_MISSING_TRAILER_HOLD, false);
            return false;
        }
        if (this.missingTrailerPose == null) {
            this.missingTrailerPose = new MissingTrailerPose(this.position(), this.getYRot(), this.getXRot(),
                    this.roll, this.onGround());
        }
        this.entityData.set(DATA_MISSING_TRAILER_HOLD, true);
        MissingTrailerPose pose = this.missingTrailerPose;
        this.setPos(pose.position().x, pose.position().y, pose.position().z);
        this.setYRot(pose.yaw());
        this.setXRot(pose.pitch());
        this.setRoll(pose.roll());
        this.setOnGround(pose.grounded());
        this.setDeltaMovement(Vec3.ZERO);
        this.serverVelocity = Vec3.ZERO;
        this.observedVelocity = Vec3.ZERO;
        this.observedYawRate = 0.0;
        this.lastTickPos = pose.position();
        this.speedKmh = 0.0f;
        this.updateParts();
        return true;
    }

    public void trailerDestroyed() {
        if (this.level().isClientSide || this.couplingState() == CouplingState.FREE) {
            return;
        }
        this.evaluatePartner(true);
    }

    private void rememberTrailer(PatriotVehicleEntity trailer) {
        this.absentTicks = 0;
        this.partnerKnown = true;
        this.partnerX = trailer.getX();
        this.partnerY = trailer.getY();
        this.partnerZ = trailer.getZ();
    }

    public boolean trailerLoaded() {
        return this.coupledTrailer() != null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy() && this.couplingState() != CouplingState.FREE) {
            PatriotVehicleEntity trailer = this.coupledTrailer();
            if (trailer != null) {
                trailer.tractorDestroyed();
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean isControlledByLocalInstance() {
        CouplingState state = this.couplingState();
        if (state == CouplingState.LOCKING || state == CouplingState.UNLOCKING) {
            return !this.level().isClientSide;
        }
        return super.isControlledByLocalInstance();
    }

    public void adoptTrailer(PatriotVehicleEntity trailer, float bend) {
        this.coupledTrailerUuid = trailer.getUUID();
        this.entityData.set(DATA_TRAILER_ID, trailer.getId());
        this.storedArticulationYaw = bend;
        trailer.setHitched(true, this.getId(), this.getUUID());
        M983Coupling.applyTrailerPose(trailer, this, bend);
        this.rememberTrailer(trailer);
        this.setCoupling(CouplingState.COUPLED, 0);
    }

    public boolean isCoupledTo(PatriotVehicleEntity trailer) {
        return this.couplingState() != CouplingState.FREE && trailer.getUUID().equals(this.coupledTrailerUuid);
    }

    public void requestUncouple(Player player) {
        if (this.level().isClientSide || this.destroyed || this.couplingState() != CouplingState.COUPLED) {
            return;
        }
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer == null) {
            return;
        }
        M983Coupling.Uncouple verdict = this.uncoupleVerdict();
        if (verdict != M983Coupling.Uncouple.OK) {
            if (verdict.messageKey() != null) {
                player.displayClientMessage(Component.translatable(verdict.messageKey()), true);
            }
            return;
        }
        this.couplingTicks = 0;
        this.setCoupling(CouplingState.UNLOCKING, 0);
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), ModSounds.TEL_COVERS.get(),
                SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    private void tickUnlocking() {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer == null) {
            this.evaluatePartner(false);
            return;
        }
        this.rememberTrailer(trailer);
        this.couplingTicks++;
        this.setCoupling(CouplingState.UNLOCKING, this.couplingTicks);
        if (this.couplingTicks >= COUPLING_TICKS) {
            this.releaseCoupling(true);
        }
    }

    private void releaseCoupling(boolean park) {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        if (trailer != null) {
            this.uncoupledFrom = trailer.getUUID();
            this.uncoupleX = this.getX();
            this.uncoupleZ = this.getZ();
            if (park) {
                trailer.placeOnSupports();
            }
            trailer.setHitched(false, -1, null);
            trailer.dropJoltBody();
        }
        this.coupledTrailerUuid = null;
        this.partnerKnown = false;
        this.absentTicks = 0;
        this.storedArticulationYaw = 0.0f;
        this.couplingTicks = 0;
        this.captureTicks = 0;
        this.entityData.set(DATA_TRAILER_ID, -1);
        this.setCoupling(CouplingState.FREE, 0);
        this.messageCrew("message.ata.m983.uncoupled");
    }

    private void setCoupling(CouplingState state, int ticks) {
        if (state == CouplingState.FREE) {
            this.missingTrailerPose = null;
            this.entityData.set(DATA_MISSING_TRAILER_HOLD, false);
        }
        this.entityData.set(DATA_COUPLING_STATE, state.ordinal());
        this.entityData.set(DATA_COUPLING_TICKS, ticks);
    }

    private void messageCrew(String key) {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                player.displayClientMessage(Component.translatable(key), true);
            }
        }
    }

    private boolean useJolt() {
        return !this.joltDisabled && PhysicsWorlds.joltWanted();
    }

    private void dropJolt() {
        if (this.jolt != null) {
            this.jolt.close();
            this.jolt = null;
        }
    }

    private void tickClient(boolean simulating) {
        this.observedVelocity = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
        float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        this.observedYawRate = -Math.toRadians(yawDelta) * Units.TICKS_PER_SECOND;
        if (!simulating) {
            this.roll = Mth.lerp(0.35f, this.roll, this.entityData.get(DATA_ROLL));
        }
        if (this.joltDrivesPose()) {
            double speed = this.jolt.speedMs();
            this.speedKmh += ((float) Units.msToKmh(speed) - this.speedKmh) * 0.3f;
            this.engineRpm = this.jolt.rpm();
            this.gear = this.jolt.gear();
            this.engineLoad = this.jolt.load();
            this.steerNow = (float) this.jolt.steerDegrees();
            for (int i = 0; i < this.spinNow.length; i++) {
                this.spinNow[i] = (float) WheelSpin.boneRotation(this.jolt.wheelRot(i % 2 == 0));
                this.wheelLift[i] += (float) ((this.jolt.wheelOffset(i) - this.wheelLift[i]) * 0.6);
            }
        } else {
            double speed = Math.hypot(this.observedVelocity.x, this.observedVelocity.z) * Units.TICKS_PER_SECOND;
            this.speedKmh += ((float) Units.msToKmh(speed) - this.speedKmh) * 0.3f;
            double yawRad = Math.toRadians(this.getYRot());
            double forward = this.observedVelocity.z * Math.cos(yawRad) - this.observedVelocity.x * Math.sin(yawRad);
            for (int i = 0; i < this.spinNow.length; i++) {
                this.spinNow[i] += (float) WheelSpin.travelRotation(forward, TractorGeometry.WHEEL_RADIUS);
            }
        }
    }

    private void tickServer(boolean simulating) {
        if (this.repairCooldown > 0) {
            this.repairCooldown--;
        }
        float health = this.getHealth();
        if (health > 0.0f && health <= this.getMaxHealth() * BURNING_FRACTION) {
            this.setHealth(health - BURNING_LOSS_PER_TICK);
            if (this.getHealth() <= 0.0f) {
                this.destroy(this.damageSources().generic());
                return;
            }
        }
        Vec3 here = this.position();
        if (this.lastTickPos == null) {
            this.lastTickPos = here;
        }
        this.serverVelocity = here.subtract(this.lastTickPos);
        this.lastTickPos = here;
        this.tickCouplingServer();
        this.tickOverturnHint();
    }

    private void tickOverturnHint() {
        if (this.overturnHintCooldown > 0) {
            this.overturnHintCooldown--;
        }
        boolean lying = this.isOverturned() && this.serverVelocity.lengthSqr() < 1.0E-3;
        this.overturnedTicks = lying ? this.overturnedTicks + 1 : 0;
        if (this.overturnedTicks >= 60 && this.overturnHintCooldown == 0) {
            this.overturnHintCooldown = 400;
            for (Player near : this.level().players()) {
                if (near.distanceToSqr(this) < 20.0 * 20.0) {
                    near.displayClientMessage(Component.translatable("message.ata.patriot.overturned"), true);
                }
            }
        }
    }

    private void sendPoseIfChanged() {
        PatriotVehicleEntity trailer = this.coupledTrailer();
        float artYaw = trailer == null ? 0.0f : trailer.getArticulationYaw();
        if (Float.isNaN(this.poseSentYaw) || Math.abs(this.getYRot() - this.poseSentYaw) > 0.05f
                || Math.abs(this.getXRot() - this.poseSentPitch) > 0.05f
                || Math.abs(this.roll - this.poseSentRoll) > 0.05f
                || Math.abs(artYaw - this.poseSentArtYaw) > 0.05f) {
            this.poseSentYaw = this.getYRot();
            this.poseSentPitch = this.getXRot();
            this.poseSentRoll = this.roll;
            this.poseSentArtYaw = artYaw;
            NetworkHandler.sendToServer(new PatriotPosePacket(this.getId(), artYaw, this.getYRot(), this.getXRot(),
                    this.roll));
        }
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot,
                    this.lerpXRot);
            this.lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 10;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot();
    }

    @Override
    protected AABB makeBoundingBox() {
        Vec3 p = this.position();
        return TractorGeometry.rootBox(p.x, p.y, p.z);
    }

    @Override
    public float maxUpStep() {
        return 0.0f;
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
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.PLAYER || type == MoverType.SELF) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
            return;
        }
        super.move(type, movement);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean client = this.level().isClientSide;
        if (this.destroyed) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && held.is(Items.IRON_BLOCK) && this.isOverturned()) {
            if (!client) {
                this.rightWithIron(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (this.isRepairItem(held) && this.getHealth() < this.getMaxHealth()) {
            if (!client && this.repairCooldown <= 0) {
                this.repair(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (player.isShiftKeyDown() && held.isEmpty() && this.getPassengers().isEmpty()
                && (this.ownerUuid == null || this.ownerUuid.equals(player.getUUID())
                        || player.getAbilities().instabuild)) {
            if (!client) {
                this.pickUp(player);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!client && !player.startRiding(this)) {
            player.displayClientMessage(Component.translatable("message.ata.patriot.full"), true);
        }
        return InteractionResult.sidedSuccess(client);
    }

    private boolean isRepairItem(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_BLOCK);
    }

    private void rightWithIron(Player player, ItemStack held) {
        for (Entity passenger : new ArrayList<>(this.getPassengers())) {
            passenger.stopRiding();
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (this.jolt != null) {
            this.jolt.startRighting();
        }
        player.displayClientMessage(Component.translatable("message.ata.patriot.righting"), true);
    }

    private void repair(Player player, ItemStack held) {
        float share = held.is(Items.IRON_BLOCK) ? 0.45f : 0.05f;
        this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + this.getMaxHealth() * share));
        this.repairCooldown = REPAIR_DELAY_TICKS;
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.ata.patriot.repaired",
                Math.round(this.getHealth()), Math.round(this.getMaxHealth())), true);
    }

    private void pickUp(Player player) {
        ItemStack stack = new ItemStack(ModItems.M983_TRACTOR.get());
        PatriotItem.writeHealth(stack, this.getHealth());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        this.releaseCoupling(true);
        this.discard();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.M983_TRACTOR.get());
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return !this.destroyed && this.getPassengers().size() < TractorGeometry.SEAT_COUNT;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide) {
            this.entityData.set(seatData(this.chooseSeat(passenger)), passenger.getId());
        }
        this.doorsOpening = true;
        this.doorTicks = DOOR_TICKS;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = this.seatOf(passenger);
        super.removePassenger(passenger);
        if (!this.level().isClientSide) {
            for (int i = 0; i < TractorGeometry.SEAT_COUNT; i++) {
                if (this.entityData.get(seatData(i)) == passenger.getId()) {
                    this.entityData.set(seatData(i), NO_OCCUPANT);
                }
            }
            if (seat == 0) {
                this.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (this.getPassengers().isEmpty()) {
            this.doorsOpening = false;
            this.doorTicks = DOOR_TICKS;
        }
    }

    private static EntityDataAccessor<Integer> seatData(int seat) {
        return seat == 0 ? DATA_SEAT_0 : DATA_SEAT_1;
    }

    private int chooseSeat(Entity arriving) {
        if (this.savedSeats.containsKey(arriving.getUUID())) {
            return Mth.clamp(this.savedSeats.remove(arriving.getUUID()), 0, TractorGeometry.SEAT_COUNT - 1);
        }
        for (int i = 0; i < TractorGeometry.SEAT_COUNT; i++) {
            if (this.entityData.get(seatData(i)) == NO_OCCUPANT) {
                return i;
            }
        }
        return TractorGeometry.SEAT_COUNT - 1;
    }

    public int seatOf(Entity passenger) {
        int id = passenger.getId();
        for (int i = 0; i < TractorGeometry.SEAT_COUNT; i++) {
            if (this.entityData.get(seatData(i)) == id) {
                return i;
            }
        }
        return Mth.clamp(this.getPassengers().indexOf(passenger), 0, TractorGeometry.SEAT_COUNT - 1);
    }

    public boolean isCrewSeat(Entity player) {
        return CrewAccess.mayOperate(this.hasPassenger(player), this.seatOf(player), TractorGeometry.SEAT_COUNT);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity p : this.getPassengers()) {
            if (this.seatOf(p) == 0) {
                return p instanceof Player player ? player : null;
            }
        }
        return null;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(),
                this.roll, TractorGeometry.SEATS[this.seatOf(passenger)]);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        passenger.setYBodyRot(this.getYRot());
        float yaw = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
        float clamped = Mth.clamp(yaw, -95.0f, 95.0f);
        passenger.yRotO += clamped - yaw;
        passenger.setYRot(passenger.getYRot() + clamped - yaw);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.destroyed) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.discard();
            return true;
        }
        float damage;
        if (source.is(TelWorld.STRIKE)) {
            damage = amount;
        } else {
            Entity attacker = source.getEntity();
            if (attacker != null && this.hasPassenger(attacker)) {
                return false;
            }
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                if (source == this.lastExplosionSource && this.tickCount == this.lastExplosionTick) {
                    return false;
                }
                this.lastExplosionSource = source;
                this.lastExplosionTick = this.tickCount;
            }
            damage = amount - ARMOUR;
            if (damage <= 0.0f) {
                return false;
            }
            damage *= this.damageFactor(source);
        }
        this.setHealth(this.getHealth() - damage);
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.IRON_GOLEM_DAMAGE,
                SoundSource.NEUTRAL, 1.0f, 0.7f);
        if (this.getHealth() <= 0.0f) {
            this.destroy(source);
        }
        return true;
    }

    private float damageFactor(DamageSource source) {
        float factor = 1.0f;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            factor = 2.0f;
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            factor = 0.2f;
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            factor = 2.0f;
        } else if (source.getDirectEntity() instanceof LivingEntity) {
            factor = 0.3f;
        }
        Vec3 origin = source.getSourcePosition();
        if (origin != null) {
            Vec3 toAttacker = origin.subtract(this.position());
            if (toAttacker.lengthSqr() > 1.0E-6) {
                double facing = toAttacker.normalize().dot(this.getViewVector(1.0f));
                factor *= (float) Math.max(1.0 - 0.25 * facing, 0.5);
            }
        }
        return factor;
    }

    private void destroy(DamageSource cause) {
        if (this.destroyed || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.destroyed = true;
        this.ejectPassengers();
        this.releaseCoupling(true);
        Entity blame = cause.getEntity();
        if (blame == null && this.ownerUuid != null) {
            blame = serverLevel.getPlayerByUUID(this.ownerUuid);
        }
        DamageSource blast = this.damageSources().explosion(this, blame);
        Level.ExplosionInteraction mode = ShahedConfig.doExplosionsBreakBlocks()
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        Vec3 centre = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(), this.getYRot(),
                this.getXRot(), this.roll, new Vec3(0.0, 2.0, 4.2));
        serverLevel.explode(this, blast, null, centre.x, centre.y, centre.z, 5.0f, false, mode);
        this.spawnAtLocation(new ItemStack(Items.IRON_INGOT, 6 + this.random.nextInt(7)));
        this.discard();
    }

    void reportJoltImpact(byte kind, double speed) {
        if (this.impactCooldown > 0) {
            return;
        }
        this.impactCooldown = IMPACT_COOLDOWN_TICKS;
        if (this.level().isClientSide) {
            NetworkHandler.sendToServer(new PatriotImpactPacket(this.getId(), kind, (float) speed));
        } else {
            this.applyImpact(kind, speed);
        }
    }

    public boolean applyImpact(byte kind, double speed) {
        if (this.level().isClientSide || this.destroyed || !Double.isFinite(speed) || speed <= 0.0 || speed > 2.2) {
            return false;
        }
        if (this.tickCount - this.lastImpactTick < IMPACT_COOLDOWN_TICKS) {
            return false;
        }
        double damage;
        if (kind == IMPACT_HORIZONTAL) {
            if (speed < 0.3) {
                return false;
            }
            damage = 18.0 * Math.pow(speed - 0.2, 2.0);
        } else if (kind == IMPACT_VERTICAL) {
            if (speed <= 0.4) {
                return false;
            }
            double along = Math.hypot(this.serverVelocity.x, this.serverVelocity.z);
            double total = Math.hypot(along, speed);
            damage = 24.0 * (speed - 0.4) * Math.pow(Math.max(total - 0.4, 0.3), 2.0);
        } else {
            return false;
        }
        this.lastImpactTick = this.tickCount;
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(),
                ModSounds.TEL_STRIKE.get(), SoundSource.NEUTRAL, 1.0f, 0.8f + this.random.nextFloat() * 0.3f);
        this.hurt(TelWorld.strike(this.level(), null, null), (float) damage);
        return true;
    }

    void rollBackTo(double x, double y, double z) {
        if (this.level().isClientSide) {
            return;
        }
        this.setPos(x, y, z);
        if (this.getControllingPassenger() instanceof ServerPlayer driver) {
            driver.connection.send(new net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket(this));
        }
    }

    public void applyJoltPose(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
        if (!this.level().isClientSide && this.enforceMissingTrailerHold()) {
            return;
        }
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setRoll(roll);
        this.setDeltaMovement(velocity);
        this.setOnGround(grounded);
        this.verticalCollisionBelow = grounded;
        this.updateParts();
    }

    boolean softBlocksGiveWay() {
        try {
            return ShahedConfig.doesTelBreakSoftBlocks();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public void setRoll(float value) {
        if (Float.isFinite(value) && Math.abs(value) < 180.0f) {
            this.roll = value;
            if (!this.level().isClientSide) {
                this.entityData.set(DATA_ROLL, value);
            }
        }
    }

    public void acceptPose(float artYaw, float yaw, float pitch, float roll) {
        if (this.level().isClientSide || this.enforceMissingTrailerHold()) {
            return;
        }
        if (Float.isFinite(yaw) && Float.isFinite(pitch) && Float.isFinite(roll)
                && Math.abs(pitch) < 180.0f && Math.abs(roll) < 180.0f) {
            this.setYRot(yaw);
            this.setXRot(pitch);
            this.setRoll(roll);
            this.setPos(this.getX(), this.getY(), this.getZ());
            PatriotVehicleEntity trailer = this.coupledTrailer();
            if (trailer != null && Float.isFinite(artYaw) && Math.abs(artYaw)
                    <= VehicleSpecs.train(trailer.kind().specName()).coupling.yawLimitDegrees + 5.0) {
                this.storedArticulationYaw = artYaw;
            }
            if (this.couplingState() == CouplingState.COUPLED && trailer != null && !this.joltDrivesPose()) {
                M983Coupling.applyTrailerPose(trailer, this, this.storedArticulationYaw);
            }
        }
    }

    public void handleCommand(ServerPlayer player, byte command) {
        if (this.level().isClientSide || this.destroyed || !this.isCrewSeat(player)) {
            return;
        }
        if (command == CMD_HEADLIGHTS) {
            this.toggleHeadlights();
        } else if (command == CMD_UNLOCK) {
            this.requestUncouple(player);
        }
    }

    public void toggleHeadlights() {
        if (!this.headlightsWork()) {
            this.setHeadlights(false);
            return;
        }
        this.entityData.set(DATA_HEADLIGHTS, TelHeadlights.next(this.headlightMode(), true));
    }

    public byte headlightMode() {
        return TelHeadlights.mode(this.entityData.get(DATA_HEADLIGHTS));
    }

    public boolean areHeadlightsOn() {
        return this.headlightMode() != 0;
    }

    private void setHeadlights(boolean on) {
        this.entityData.set(DATA_HEADLIGHTS, (byte) (on && this.headlightsWork() ? 1 : 0));
    }

    private boolean headlightsWork() {
        return TelHeadlights.works(this.getHealth(), this.getMaxHealth(), this.destroyed, BURNING_FRACTION);
    }

    public void setTestThrottle(double throttle) {
        this.testThrottle = throttle;
    }

    public void setTestSteer(double steer) {
        this.testSteer = steer;
    }

    public boolean usesJolt() {
        return this.jolt != null && this.jolt.active();
    }

    public boolean joltSimulating() {
        return this.joltDrivesPose();
    }

    private boolean joltDrivesPose() {
        return this.jolt != null && this.jolt.role() == TractorJolt.Role.SIMULATE;
    }

    @Nullable
    public TractorJolt joltView() {
        return this.jolt;
    }

    public int rollbacks() {
        return this.jolt == null ? 0 : this.jolt.rollbacks();
    }

    public boolean isOverturned() {
        return TractorDrive.overturned(this.getXRot(), this.roll);
    }

    public void pushByExplosion(Vec3 centre, float power) {
        if (this.level().isClientSide || this.jolt == null || !this.jolt.spinsFreely()) {
            return;
        }
        Vec3 offset = new Vec3(this.getX(), this.getY() + 1.8, this.getZ()).subtract(centre);
        double distance = offset.length();
        if (distance > power * 12.0 + 12.0 || distance < 1.0E-3) {
            return;
        }
        double magnitude = VehicleSpecs.m983Tractor().limits.explosionImpulse * power
                / Math.max(distance * distance, 4.0);
        Vec3 away = offset.normalize();
        this.jolt.push(away.x * magnitude, Math.max(away.y, 0.25) * magnitude, away.z * magnitude);
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public void setHealth(float value) {
        this.entityData.set(DATA_HEALTH, Mth.clamp(value, 0.0f, this.getMaxHealth()));
    }

    public float getMaxHealth() {
        try {
            return (float) ShahedConfig.getM983MaxHealth();
        } catch (IllegalStateException e) {
            return DEFAULT_HEALTH;
        }
    }

    public float getSpeedKmh() {
        return this.speedKmh;
    }

    public double getEngineRpm() {
        if (this.joltDrivesPose()) {
            return this.jolt.rpm();
        }
        return 700.0 + Mth.clamp(this.speedKmh / 65.0, 0.0, 1.2) * 1200.0;
    }

    public int getGear() {
        return this.joltDrivesPose() ? this.jolt.gear() : 0;
    }

    public double getEngineLoad() {
        return this.joltDrivesPose() ? this.jolt.load() : 0.0;
    }

    public float getSteerNow() {
        return this.steerNow;
    }

    public float getSpin(float partialTick, int wheel) {
        return Mth.lerp(partialTick, this.spinO[wheel], this.spinNow[wheel]);
    }

    public float getWheelLift(int wheel) {
        return this.wheelLift[wheel];
    }

    public boolean doorsOpening() {
        return this.doorsOpening;
    }

    public int doorTicks() {
        return this.doorTicks;
    }

    public float getRollLerp(float partialTick) {
        return Mth.lerp(partialTick, this.rollO, this.roll);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(@Nullable UUID owner) {
        this.ownerUuid = owner;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("LegitSpawn", this.isLegitSpawn);
        tag.putFloat("Health", this.getHealth());
        tag.putFloat("Roll", this.roll);
        tag.putByte("Headlights", this.headlightMode());
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        if (this.coupledTrailerUuid != null && this.couplingState() != CouplingState.FREE) {
            tag.putUUID("CoupledTrailer", this.coupledTrailerUuid);
            tag.putFloat("ArticulationYaw", this.storedArticulationYaw);
            if (this.partnerKnown) {
                CompoundTag partner = new CompoundTag();
                partner.putDouble("X", this.partnerX);
                partner.putDouble("Y", this.partnerY);
                partner.putDouble("Z", this.partnerZ);
                tag.put("PartnerPos", partner);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.isLegitSpawn = tag.contains("LegitSpawn") && tag.getBoolean("LegitSpawn");
        if (tag.contains("Health")) {
            this.setHealth(tag.getFloat("Health"));
        }
        this.roll = tag.getFloat("Roll");
        this.rollO = this.roll;
        this.entityData.set(DATA_ROLL, this.roll);
        this.entityData.set(DATA_HEADLIGHTS, TelHeadlights.mode(tag.getByte("Headlights")));
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
        }
        if (tag.hasUUID("CoupledTrailer")) {
            this.coupledTrailerUuid = tag.getUUID("CoupledTrailer");
            this.storedArticulationYaw = tag.getFloat("ArticulationYaw");
            CompoundTag partner = tag.getCompound("PartnerPos");
            this.partnerKnown = partner.contains("X", Tag.TAG_DOUBLE) && partner.contains("Y", Tag.TAG_DOUBLE)
                    && partner.contains("Z", Tag.TAG_DOUBLE);
            if (this.partnerKnown) {
                this.partnerX = partner.getDouble("X");
                this.partnerY = partner.getDouble("Y");
                this.partnerZ = partner.getDouble("Z");
            }
            this.setCoupling(CouplingState.COUPLED, 0);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
                this.getX() - TractorGeometry.CULL_HALF_XZ, this.getY(), this.getZ() - TractorGeometry.CULL_HALF_XZ,
                this.getX() + TractorGeometry.CULL_HALF_XZ, this.getY() + TractorGeometry.CULL_HEIGHT,
                this.getZ() + TractorGeometry.CULL_HALF_XZ);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    public void onRemovedFromLevel() {
        this.dropJolt();
        super.onRemovedFromLevel();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<M983TractorEntity> state) {
        if (this.doorTicks > 0) {
            state.getController().setAnimation(this.doorsOpening ? DOORS_OPEN : DOORS_CLOSE);
            return PlayState.CONTINUE;
        }
        if (this.doorsOpening) {
            state.getController().setAnimation(DOORS_OPEN);
            return PlayState.CONTINUE;
        }
        if (Math.abs(this.speedKmh) > 0.5) {
            state.getController().setAnimation(WHEELS);
            state.getController().setAnimationSpeed(Math.max(0.05, Math.abs(this.speedKmh) / 3.6 / 3.691));
            return PlayState.CONTINUE;
        }
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    public static final class TractorPart extends PartEntity<M983TractorEntity> {
        private final int index;

        public TractorPart(M983TractorEntity parent, int index) {
            super(parent);
            this.index = index;
            this.setId(parent.getId() + index + 1);
            this.refreshDimensions();
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
            return !this.isRemoved() && this.getParent().hurt(source, amount);
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return EntityDimensions.scalable((float) TractorGeometry.partWidth(this.index),
                    (float) TractorGeometry.partHeight(this.index));
        }

        @Override
        public ItemStack getPickResult() {
            return this.getParent().getPickResult();
        }
    }
}
