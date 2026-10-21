package com.synarsis.airtacticalarsenal.entity.tel;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;
import com.synarsis.airtacticalarsenal.combat.hitbox.ObbProvider;
import com.synarsis.airtacticalarsenal.combat.sweep.BulletSweep;
import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelBoneGroups;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxQuery;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelModelHitbox;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelRegistry;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.Zone;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbSweep;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Support;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelHandover;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Suspension;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngine;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelEngineState;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelSway;
import com.synarsis.airtacticalarsenal.item.IskanderTelItem;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.network.TelImpactPacket;
import com.synarsis.airtacticalarsenal.network.TelRollPacket;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
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

public class IskanderTelEntity extends Entity implements GeoEntity, ObbProvider {

    public static final float DEFAULT_MAX_HEALTH = 400.0f;
    private static final float BURNING_FRACTION = 0.1f;
    private static final float BURNING_LOSS_PER_TICK = 0.1f;
    private static final float ARMOUR = 2.0f;
    private static final float RIDER_YAW_LIMIT = 95.0f;
    private static final float RIDER_PITCH_LIMIT = 60.0f;
    private static final int REPAIR_DELAY_TICKS = 10;
    private static final double GRAVITY = 0.06;
    private static final int NO_OCCUPANT = -1;
    public static final byte IMPACT_HORIZONTAL = 0;
    public static final byte IMPACT_VERTICAL = 1;
    private static final int IMPACT_COOLDOWN_TICKS = 4;

    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LOADED_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LOADED_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COVER_CLIP = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COVER_START = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFT_1_CLIP = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFT_1_START = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFT_2_CLIP = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFT_2_START = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICKS = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RAISED_MASK = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_JACKS = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HAS_TARGET_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_TARGET_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TARGET_X_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_X_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_Z_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_Z_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUNTDOWN_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COUNTDOWN_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INTERVAL = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HEADLIGHTS = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SEAT_0 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_1 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_2 = SynchedEntityData
            .defineId(IskanderTelEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(TelPose.PREFIX + "idle");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private final TelPart[] parts;

    private final TelSequence sequence = new TelSequence();
    @Nullable
    private UUID commandPlayer;
    @Nullable
    private UUID loadingPlayer;
    private ItemStack pendingRound = ItemStack.EMPTY;
    private int syncState = -1;
    private int syncTicks;
    private int syncAt;
    private float jackPrev;
    private float jackNow;
    @Nullable
    private Clearance clearanceCache;
    private int clearanceTick = -100;
    private final ItemStack[] missiles = {ItemStack.EMPTY, ItemStack.EMPTY};

    public boolean isLegitSpawn = false;
    @Nullable
    private UUID ownerUuid;

    public float roll;
    public float rollO;

    public final TelEngineState engine = new TelEngineState();
    private int collisionCooldown;

    private final Suspension.State suspension = new Suspension.State();
    private boolean wheelsDown;
    private boolean skidsOnly;
    private int wheelContacts;
    private int skidSupports;
    @Nullable
    private List<AABB> wallBoxes;
    private int wallBoxesTick = -1;
    @Nullable
    private TelWorld.GroundScan lastScan;
    @Nullable
    private Support[] prevWheelState;
    @Nullable
    private Support[] prevSkidState;
    private int impactCooldown;

    @Nullable
    private List<WorldBox> modelBoxes;
    @Nullable
    private List<com.synarsis.airtacticalarsenal.combat.hitbox.Obb> modelObbs;
    private long boxKey = Long.MIN_VALUE;
    @Nullable
    private List<com.synarsis.airtacticalarsenal.combat.hitbox.Obb> renderObbs;
    private long renderKey = Long.MIN_VALUE;
    @Nullable
    private Vec3 carryFrom;
    private float carryFromYaw;
    private Vec3 carryDelta = Vec3.ZERO;
    private float carryYaw;
    private int lastCutTrace = -100;
    private int lastImpactTick = -100;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    @Nullable
    private Vec3 lastTickPos;
    private Vec3 serverVelocity = Vec3.ZERO;
    private int quietTicks;
    @Nullable
    private DamageSource lastExplosionSource;
    private int lastExplosionTick = -1;
    private int repairCooldown;
    private boolean destroyed;

    public double wheelRotLO;
    public double wheelRotRO;
    public double steerO;
    private final TelSway sway = new TelSway();
    private double swayPitchO;
    private double swayRollO;
    private Vec3 lastClientVelocity = Vec3.ZERO;
    private final double[] wheelOffset = new double[Suspension.WHEELS];
    private final double[] wheelOffsetO = new double[Suspension.WHEELS];
    private float speedKmh;

    @Nullable
    private TelJolt jolt;
    private boolean joltDisabled;
    private Vec3 observedVelocity = Vec3.ZERO;
    private double observedYawRate;
    private int overturnedTicks;
    private int overturnHintCooldown;
    private float rollSent = Float.NaN;

    private final Map<UUID, Rider> riders = new HashMap<>();
    private final Map<UUID, Integer> savedSeats = new HashMap<>();

    private static final class Rider {
        int seat;
        float vehicleYaw;

        Rider(int seat, float vehicleYaw) {
            this.seat = seat;
            this.vehicleYaw = vehicleYaw;
        }
    }

    public IskanderTelEntity(EntityType<? extends IskanderTelEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;

        TelPart[] made = new TelPart[TelGeometry.PART_Z.length];
        for (int i = 0; i < made.length; i++) {
            made[i] = new TelPart(this, i);
        }
        this.parts = made;
        this.setId(ENTITY_COUNTER.getAndAdd(made.length + 1) + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, DEFAULT_MAX_HEALTH);
        builder.define(DATA_LOADED_0, false);
        builder.define(DATA_LOADED_1, false);
        builder.define(DATA_ROLL, 0.0f);
        builder.define(DATA_STATE, TelState.STOWED.ordinal());
        builder.define(DATA_COVER_CLIP, 0);
        builder.define(DATA_COVER_START, 0);
        builder.define(DATA_LIFT_1_CLIP, 0);
        builder.define(DATA_LIFT_1_START, 0);
        builder.define(DATA_LIFT_2_CLIP, 0);
        builder.define(DATA_LIFT_2_START, 0);
        builder.define(DATA_STATE_TICKS, 0);
        builder.define(DATA_RAISED_MASK, 0);
        builder.define(DATA_JACKS, 0);
        builder.define(DATA_HAS_TARGET_0, false);
        builder.define(DATA_HAS_TARGET_1, false);
        builder.define(DATA_TARGET_X_0, 0);
        builder.define(DATA_TARGET_X_1, 0);
        builder.define(DATA_TARGET_Z_0, 0);
        builder.define(DATA_TARGET_Z_1, 0);
        builder.define(DATA_COUNTDOWN_0, -1);
        builder.define(DATA_COUNTDOWN_1, -1);
        builder.define(DATA_INTERVAL, TelSequence.DEFAULT_INTERVAL_SECONDS);
        builder.define(DATA_HEADLIGHTS, false);
        builder.define(DATA_SEAT_0, NO_OCCUPANT);
        builder.define(DATA_SEAT_1, NO_OCCUPANT);
        builder.define(DATA_SEAT_2, NO_OCCUPANT);
    }

    @Override
    protected AABB makeBoundingBox() {
        Vec3 p = this.position();
        return new AABB(p.x - 0.6, p.y + 1.4, p.z - 0.6, p.x + 0.6, p.y + 2.6, p.z + 0.6);
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
            TelPart part = this.parts[i];
            Vec3 p = TelGeometry.partPosition(i, this.getX(), this.getY(), this.getZ(),
                    this.getYRot(), this.getXRot(), this.roll);
            double ox = part.getX();
            double oy = part.getY();
            double oz = part.getZ();
            part.setPos(p.x, p.y, p.z);
            part.xo = ox;
            part.yo = oy;
            part.zo = oz;
            part.xOld = ox;
            part.yOld = oy;
            part.zOld = oz;
        }
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && !this.isLegitSpawn) {
            this.discard();
            return;
        }

        this.rollO = this.roll;
        this.wheelRotLO = this.engine.wheelRotL;
        this.wheelRotRO = this.engine.wheelRotR;
        this.steerO = this.steerNow();
        this.swayPitchO = this.sway.fakePitch;
        this.swayRollO = this.sway.fakeRoll;
        if (this.collisionCooldown > 0) {
            this.collisionCooldown--;
        }
        if (this.impactCooldown > 0) {
            this.impactCooldown--;
        }

        super.tick();
        this.tickLerp();
        if (this.level().isClientSide) {
            this.tickCycleClient();
        } else {
            this.tickSequence();
        }

        boolean simulating = this.isControlledByLocalInstance();
        if (this.useJolt()) {
            this.tickJolt(simulating);
        } else {
            this.dropJolt();
            if (simulating) {
                this.simulate();
            } else if (this.level().isClientSide) {
                this.setDeltaMovement(Vec3.ZERO);
            }

            if (!simulating && !this.level().isClientSide) {
                this.followClientRoll();
            }
        }

        if (this.level().isClientSide) {
            this.tickClient(simulating);
        } else {
            this.tickServer(simulating);
        }

        this.updateParts();

        if (!this.joltDrivesPose()) {
            this.updateCarry();
        }
        this.boxKey = Long.MIN_VALUE;
        TelRegistry.add(this.level(), this);
        if (!this.level().isClientSide) {
            this.sweepBullets();
        }
    }

    private void updateCarry() {
        Vec3 here = this.position();
        this.carryDelta = this.carryFrom == null ? Vec3.ZERO : here.subtract(this.carryFrom);
        this.carryYaw = this.carryFrom == null ? 0.0f : Mth.wrapDegrees(this.getYRot() - this.carryFromYaw);
        this.carryFrom = here;
        this.carryFromYaw = this.getYRot();
    }

    private boolean useJolt() {
        return !this.joltDisabled && PhysicsWorlds.joltWanted();
    }

    private boolean joltDrivesPose() {
        return this.jolt != null && this.jolt.role() == TelJolt.Role.SIMULATE;
    }

    private void dropJolt() {
        if (this.jolt != null) {
            this.jolt.close();
            this.jolt = null;
        }
    }

    public boolean isJacked() {
        return this.getJackProgress(1.0f) >= 0.95f;
    }

    private void tickJolt(boolean simulating) {
        if (this.jolt == null) {
            this.jolt = new TelJolt(this);
        }
        boolean client = this.level().isClientSide;
        boolean driven = this.getControllingPassenger() != null;
        boolean stowed = this.getTelState() == TelState.STOWED;
        int keys = client && driven && simulating ? TelInput.provider.getAsInt() : 0;
        Vec3 velocity = client ? this.observedVelocity : this.serverVelocity;
        double yawRate = client ? this.observedYawRate
                : -Math.toRadians(Mth.wrapDegrees(this.getYRot() - this.yRotO)) * Units.TICKS_PER_SECOND;
        this.jolt.update(simulating, driven, this.isJacked() || this.groundUnloaded(), stowed, keys, velocity, yawRate);
        if (this.jolt.failed()) {
            this.joltDisabled = true;
            this.dropJolt();
            return;
        }
        if (client && !simulating) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        if (this.jolt.role() == TelJolt.Role.SIMULATE) {
            this.engine.wheelRotL = this.jolt.wheelRot(true);
            this.engine.wheelRotR = this.jolt.wheelRot(false);
            this.engine.power = driven ? this.jolt.load() * Math.signum(this.observedForward()) : 0.0;
        }
        if (client && simulating && this.jolt.role() == TelJolt.Role.SIMULATE
                && (Float.isNaN(this.rollSent) || Math.abs(this.roll - this.rollSent) > 0.05f)) {
            this.rollSent = this.roll;
            PacketDistributor.sendToServer(new TelRollPacket(this.getId(), this.roll));
        }
    }

    private boolean groundUnloaded() {
        double reach = 12.0;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (!this.level().hasChunkAt(BlockPos.containing(this.getX() + dx * reach, this.getY(),
                        this.getZ() + dz * reach))) {
                    return true;
                }
            }
        }
        return false;
    }

    private double observedForward() {
        Vec3 f = this.getViewVector(1.0f);
        return this.observedVelocity.x * f.x + this.observedVelocity.z * f.z;
    }

    private float steerNow() {
        if (this.joltDrivesPose()) {
            return (float) Mth.clamp(this.jolt.steerDegrees(), -28.0, 28.0);
        }
        return (float) this.engine.steerDegrees();
    }

    public void applyJoltPose(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setRoll(roll);
        this.setDeltaMovement(velocity);
        this.setOnGround(grounded);
        this.verticalCollisionBelow = grounded;
        this.updateParts();
        this.updateCarry();
        this.boxKey = Long.MIN_VALUE;
    }

    void reportJoltImpact(byte kind, double speed) {
        this.reportImpact(kind, speed);
    }

    void rollBackTo(double x, double y, double z, String reason) {
        if (this.level().isClientSide) {
            return;
        }
        AirTacticalArsenal.LOGGER.debug("Launcher {}: {}", this.getId(), reason);
        this.setPos(x, y, z);
        if (this.getControllingPassenger() instanceof ServerPlayer driver) {
            driver.connection.send(new net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket(this));
        }
    }

    public void acceptRoll(float value) {
        if (Float.isFinite(value) && Math.abs(value) < 180.0f) {
            this.setRoll(value);
        }
    }

    boolean softBlocksGiveWayPublic() {
        return this.softBlocksGiveWay();
    }

    public boolean isOverturned() {
        return TelJolt.overturned(this.getXRot(), this.roll);
    }

    public boolean usesJolt() {
        return this.jolt != null && this.jolt.active();
    }

    public boolean joltSimulating() {
        return this.joltDrivesPose();
    }

    public double getEngineRpm() {
        if (this.joltDrivesPose()) {
            return this.jolt.rpm();
        }
        double fraction = Mth.clamp(this.speedKmh / 65.0, 0.0, 1.2);
        return 700.0 + fraction * 1200.0;
    }

    public int getGear() {
        return this.joltDrivesPose() ? this.jolt.gear() : 0;
    }

    public double getEngineLoad() {
        if (this.joltDrivesPose()) {
            return this.jolt.load();
        }
        return Mth.clamp(Math.abs(this.engine.power), 0.0, 1.0);
    }

    @Nullable
    public TelJolt joltView() {
        return this.jolt;
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
        VehicleSpec spec = VehicleSpecs.current();
        double magnitude = spec.limits.explosionImpulse * power / Math.max(distance * distance, 4.0);
        Vec3 away = offset.normalize();
        this.jolt.push(away.x * magnitude, Math.max(away.y, 0.25) * magnitude, away.z * magnitude);
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
        player.displayClientMessage(Component.translatable("message.ata.tel.righting"), true);
    }

    private void simulate() {
        boolean client = this.level().isClientSide;
        boolean driven = this.getControllingPassenger() != null;

        Vec3 v = this.getDeltaMovement();
        if (!driven && this.wheelsDown && v.x * v.x + v.z * v.z < 1.0E-5
                && Math.abs(this.suspension.pitchRate) < 0.05 && Math.abs(this.suspension.rollRate) < 0.05
                && this.tickCount % 10 != 0) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        int keys = 0;
        if (client && driven) {
            keys = this.getTelState() == TelState.STOWED ? TelInput.provider.getAsInt() : TelInput.BRAKE;
        }

        Vec3 f = this.getViewVector(1.0f);
        boolean inWater = this.isInWater();
        TelEngine.Context ctx = new TelEngine.Context(v.x, 0.0, v.z, f.x, f.y, f.z,
                this.wheelsDown, inWater, inWater ? this.getFluidHeight(FluidTags.WATER) : 0.0,
                this.getXRot(), driven, this.engineParams(), this.skidsOnly ? 0.5 : 1.0);
        TelEngine.Result r = TelEngine.step(this.engine, keys, ctx);
        Vec3 horizontal = new Vec3(r.vx(), 0.0, r.vz());

        float turn = (float) r.yawDelta();
        float newYaw = this.getYRot() + turn;
        this.stageWallBoxes(newYaw, horizontal);
        if (turn != 0.0f) {
            Obb before = this.wallHull(this.getY(), this.getXRot(), this.roll);
            Obb after = Obb.wallHull(this.getX(), this.getY(), this.getZ(), newYaw, this.getXRot(), this.roll);
            if (ObbSweep.turnAllowed(before, after, this.wallBoxes, ObbSweep.Params.STANDARD, 0.1)) {
                this.setYRot(newYaw);
            } else {
                this.engine.rudder *= 0.5;
                this.trace(String.format("turn refused: %.2f deg would put it %.2f into a wall (was %.2f)",
                        turn, ObbSweep.depth(after, this.wallBoxes, ObbSweep.Params.STANDARD),
                        ObbSweep.depth(before, this.wallBoxes, ObbSweep.Params.STANDARD)));
            }
        }

        this.setDeltaMovement(r.vx(), this.suspension.vy, r.vz());
        this.move(MoverType.SELF, horizontal);
        this.runSuspension();
    }

    private TelEngine.Params engineParams() {
        try {
            return new TelEngine.Params(ShahedConfig.getTelAcceleration(), ShahedConfig.getTelBraking(),
                    ShahedConfig.getTelSteering(), ShahedConfig.getTelMaxForwardSpeed(),
                    ShahedConfig.getTelMaxBackwardSpeed(), TelEngine.DEFAULTS.wheelRadius(),
                    ShahedConfig.getTelBrakePower(), ShahedConfig.getTelBrakeDamp());
        } catch (IllegalStateException e) {
            return TelEngine.DEFAULTS;
        }
    }

    private double climb() {
        try {
            return ShahedConfig.getTelClimb();
        } catch (IllegalStateException e) {
            return TelGeometry.CLIMB;
        }
    }

    boolean softBlocksGiveWay() {
        try {
            return ShahedConfig.doesTelBreakSoftBlocks();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private double inertiaRate() {
        try {
            return ShahedConfig.getTelInertiaRotateRate();
        } catch (IllegalStateException e) {
            return 0.6;
        }
    }

    private void trace(String message) {
        if (TelInput.traceEnabled && this.level().isClientSide && this.isControlledByLocalInstance()) {
            TelInput.traceSink.accept(message);
        }
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        if (this.level().isClientSide && this.isControlledByLocalInstance()) {
            double back = Math.sqrt(this.distanceToSqr(x, y, z));
            if (back > 0.05) {
                this.trace(String.format(java.util.Locale.ROOT, "сервер откатил машину на %.2f блоков", back));
            }
        }
        super.absMoveTo(x, y, z, yRot, xRot);
    }

    @Override
    public float maxUpStep() {
        return 0.0f;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.PLAYER) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
            return;
        }
        if (type != MoverType.SELF) {
            super.move(type, movement);
            return;
        }
        this.moveOnHull(movement);
    }

    private Obb wallHull(double y, float pitch, float roll) {
        return Obb.wallHull(this.getX(), y, this.getZ(), this.getYRot(), pitch, roll);
    }

    private void stageWallBoxes(float yawForArea, Vec3 movement) {
        Obb area = Obb.wallHull(this.getX(), this.getY(), this.getZ(), yawForArea, this.getXRot(), this.roll);
        this.wallBoxes = TelWorld.collisionBoxes(this.level(), this, area, movement.x, movement.z,
                this.softBlocksGiveWay());
        this.wallBoxesTick = this.tickCount;
    }

    private void moveOnHull(Vec3 movement) {
        Obb hull = this.wallHull(this.getY(), this.getXRot(), this.roll);
        if (this.wallBoxes == null || this.wallBoxesTick != this.tickCount) {
            this.stageWallBoxes(this.getYRot(), movement);
        }
        ObbSweep.Result r = ObbSweep.resolve(hull, movement.x, movement.z, this.wallBoxes, ObbSweep.Params.STANDARD);

        this.setPos(this.getX() + r.dx(), this.getY(), this.getZ() + r.dz());

        boolean hitX = r.shortX() > 0.002;
        boolean hitZ = r.shortZ() > 0.002;
        this.horizontalCollision = hitX || hitZ;
        boolean minorX = r.shortX() < 0.1 || r.shortX() < 0.15 * Math.abs(movement.x);
        boolean minorZ = r.shortZ() < 0.1 || r.shortZ() < 0.15 * Math.abs(movement.z);
        this.minorHorizontalCollision = this.horizontalCollision && (!hitX || minorX) && (!hitZ || minorZ);

        Vec3 dm = this.getDeltaMovement();
        double vx = dm.x;
        double vz = dm.z;
        if (hitX) {
            vx = minorX ? vx * 0.3 : 0.0;
        }
        if (hitZ) {
            vz = minorZ ? vz * 0.3 : 0.0;
        }

        double entering = Math.hypot(movement.x, movement.z);
        boolean hard = this.horizontalCollision && !this.minorHorizontalCollision;
        if (hard && entering >= 0.2 && this.collisionCooldown == 0) {
            vx *= 0.8;
            vz *= 0.8;
            this.engine.power *= 0.8;
            this.collisionCooldown = 4;
        }
        this.setDeltaMovement(vx, dm.y, vz);

        if (this.horizontalCollision && this.tickCount - this.lastCutTrace >= 10) {
            this.lastCutTrace = this.tickCount;
            AABB blocker = hitX ? r.blockerX() : r.blockerZ();
            this.trace(String.format("wall cut %s: refused %.2f of %.2f, speed %.2f%s, block %s",
                    hitX && hitZ ? "X+Z" : (hitX ? "X" : "Z"), Math.max(r.shortX(), r.shortZ()),
                    Math.max(Math.abs(movement.x), Math.abs(movement.z)), entering,
                    hard ? "" : " (graze)",
                    blocker == null ? "?" : String.format("%.0f %.0f %.0f", blocker.minX, blocker.minY, blocker.minZ)));
        }
        if (hard && entering >= 0.3) {
            this.reportImpact(IMPACT_HORIZONTAL, entering);
        }
        this.checkInsideBlocks();
    }

    private void reportImpact(byte kind, double speed) {
        if (this.impactCooldown > 0) {
            return;
        }
        this.impactCooldown = IMPACT_COOLDOWN_TICKS;
        if (this.level().isClientSide) {
            this.trace(String.format("impact sent: %s at %.2f", kind == IMPACT_HORIZONTAL ? "wall" : "landing", speed));
            PacketDistributor.sendToServer(new TelImpactPacket(this.getId(), kind, (float) speed));
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

    private void runSuspension() {
        TelWorld.GroundScan scan = TelWorld.scanGround(this.level(), this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll, this.climb(), this.softBlocksGiveWay());
        if (scan == null) {
            return;
        }
        this.lastScan = scan;
        this.traceSupportChanges(scan);

        Suspension.State s = this.suspension;
        s.y = this.getY();
        s.pitch = this.getXRot();
        s.roll = this.roll;
        double oldY = s.y;
        double oldPitch = s.pitch;
        double oldRoll = s.roll;
        boolean wasAirborne = s.airborne;
        double oldVy = s.vy;
        Suspension.Result result = Suspension.step(s, scan.input(), this.isInWater(), GRAVITY);

        if (!Double.isFinite(s.y) || !Double.isFinite(s.pitch) || !Double.isFinite(s.roll)) {
            s.y = oldY;
            s.pitch = oldPitch;
            s.roll = oldRoll;
            s.vy = 0.0;
            s.pitchRate = 0.0;
            s.rollRate = 0.0;
            this.trace("suspension produced a non-finite pose; held the last one");
            return;
        }

        if (s.y > oldY + 1.0E-6 && this.ceilingBlocks(oldY, (float) oldPitch, (float) oldRoll, s)) {
            s.y = oldY;
            s.vy = 0.0;
        }

        this.setPos(this.getX(), s.y, this.getZ());
        this.setXRot((float) s.pitch);
        this.setRoll((float) s.roll);
        this.wheelContacts = result.wheelContacts();
        this.skidSupports = result.skidSupports();
        this.skidsOnly = this.wheelContacts == 0 && this.skidSupports > 0;
        this.wheelsDown = result.contact() && !s.airborne;
        this.setOnGround(result.onGround());
        this.verticalCollisionBelow = result.onGround();
        this.verticalCollision = result.landingSpeed() > 0.0;
        Vec3 dm = this.getDeltaMovement();
        this.setDeltaMovement(dm.x, s.vy, dm.z);

        if (!wasAirborne && s.airborne) {
            this.trace(String.format("left the ground, vy %.2f", oldVy));
        }
        if (result.landingSpeed() > 0.0) {
            this.trace(String.format("landed at %.2f", result.landingSpeed()));
            if (result.landingSpeed() > 0.4) {
                this.reportImpact(IMPACT_VERTICAL, result.landingSpeed());
            }
        }
    }

    private void traceSupportChanges(TelWorld.GroundScan scan) {
        if (this.prevWheelState != null && TelInput.traceEnabled && this.isControlledByLocalInstance()) {
            for (int i = 0; i < Suspension.WHEELS; i++) {
                if (scan.wheelState()[i] != this.prevWheelState[i]) {
                    this.trace(String.format("wheel %d: %s -> %s", i, this.prevWheelState[i], scan.wheelState()[i]));
                }
            }
            for (int i = 0; i < Suspension.SKIDS; i++) {
                if (scan.skidState()[i] != this.prevSkidState[i]) {
                    this.trace(String.format("skid %d: %s -> %s", i, this.prevSkidState[i], scan.skidState()[i]));
                }
            }
        }
        this.prevWheelState = scan.wheelState().clone();
        this.prevSkidState = scan.skidState().clone();
    }

    private boolean ceilingBlocks(double oldY, float oldPitch, float oldRoll, Suspension.State next) {
        if (this.wallBoxes == null || this.wallBoxesTick != this.tickCount) {
            this.stageWallBoxes(this.getYRot(), Vec3.ZERO);
        }
        double tolerance = ObbSweep.Params.STANDARD.tolerance();
        Obb after = Obb.wallHull(this.getX(), next.y, this.getZ(), this.getYRot(), (float) next.pitch, (float) next.roll);
        if (!ObbSweep.intersects(after, this.wallBoxes, tolerance)) {
            return false;
        }
        return !ObbSweep.intersects(this.wallHull(oldY, oldPitch, oldRoll), this.wallBoxes, tolerance);
    }

    private void followClientRoll() {
        boolean moving = this.serverVelocity.lengthSqr() > 1.0E-6;
        if (moving) {
            this.quietTicks = 0;
        } else if (this.quietTicks < 40) {
            this.quietTicks++;
        } else if (this.tickCount % 10 != 0) {
            return;
        }

        TelWorld.GroundScan scan = TelWorld.scanGround(this.level(), this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll, this.climb(), this.softBlocksGiveWay());
        if (scan == null) {
            return;
        }
        this.lastScan = scan;
        Suspension.Target target = Suspension.target(this.getY(), this.getXRot(), this.roll, scan.input());
        boolean grounded = target.contact() && Math.abs(this.getY() - target.y()) <= 0.1;
        this.setOnGround(grounded);
        this.verticalCollisionBelow = grounded;
        if (target.plane() && Double.isFinite(target.rollDeg())) {
            this.setRoll(this.roll + (float) (target.rollDeg() - this.roll) * 0.2f);
        }
    }

    private void tickServer(boolean simulating) {
        if (this.repairCooldown > 0) {
            this.repairCooldown--;
        }

        if (this.areHeadlightsOn() && !TelHeadlights.afterTick(true, this.headlightsWork())) {
            this.entityData.set(DATA_HEADLIGHTS, false);
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
        if (!simulating) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        this.tickOverturnHint();

        Vec3 vel = simulating ? this.getDeltaMovement() : this.serverVelocity;
        if (vel.lengthSqr() >= 0.09) {
            this.crushScan(vel, ShahedConfig.isTelCrushDamageEnabled(), simulating);
        }

        if (this.tickCount % 4 == 0 && this.level() instanceof ServerLevel serverLevel
                && ShahedConfig.doesTelBreakSoftBlocks()) {
            double hs = Math.hypot(vel.x, vel.z);
            if (hs > 0.09 || (simulating && this.engine.power > 0.15)) {
                Obb hull = Obb.fromPose(this.getX(), this.getY(), this.getZ(),
                        this.getYRot(), this.getXRot(), this.roll);
                TelWorld.smashSoftBlocks(serverLevel, hull, vel, this);
            }
        }
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
                    near.displayClientMessage(Component.translatable("message.ata.tel.overturned"), true);
                }
            }
        }
    }

    private boolean isCrushable(Entity e) {
        if (e == this || e instanceof PartEntity<?> || e.isSpectator() || !e.isAlive() || e.getRootVehicle() == this) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof Boat || e instanceof AbstractMinecart
                || e instanceof IskanderTelEntity;
    }

    private void crushScan(Vec3 vel, boolean damage, boolean brake) {
        Obb ahead = Obb.fromPose(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.roll)
                .translated(vel);
        List<Entity> targets = this.level().getEntities(this, ahead.worldAabb(), this::isCrushable);
        if (targets.isEmpty()) {
            return;
        }

        double mySize = TelGeometry.OBB_HALF.x * TelGeometry.OBB_HALF.y * TelGeometry.OBB_HALF.z * 8.0;
        Entity driver = this.getControllingPassenger();
        for (Entity target : targets) {
            if (ObbAabbSat.mtv(ahead, target.getBoundingBox()) == null) {
                continue;
            }
            Vec3 rel = vel.subtract(target.getDeltaMovement());
            double speed = rel.length();
            if (speed <= 0.3) {
                continue;
            }
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(ahead.centre());
            if (rel.dot(toTarget) <= 0.0) {
                continue;
            }

            double theirSize = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
            double f = Mth.clamp(theirSize / mySize, 0.25, 4.0);
            double f1 = Mth.clamp(mySize / theirSize, 0.25, 4.0);
            double over = speed - 0.3;

            if (damage) {
                double amount = (target instanceof LivingEntity ? 80.0 : 60.0) * f1 * over * over;
                target.hurt(TelWorld.strike(this.level(), this, driver), (float) amount);
                Vec3 shove = vel.normalize().scale(Math.min(f1 * 0.8 * speed, 2.0));
                target.setDeltaMovement(target.getDeltaMovement().add(shove));
                target.hurtMarked = true;
                if (amount > 1.0) {
                    this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                            ModSounds.TEL_STRIKE.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
                }
            }
            if (brake && theirSize * 8.0 > mySize) {
                this.setDeltaMovement(this.getDeltaMovement().add(rel.scale(-0.3 * f * 0.8)));
            }
        }
    }

    private void tickClient(boolean simulating) {
        Vec3 vel = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
        this.observedVelocity = vel;
        this.observedYawRate = -Math.toRadians(Mth.wrapDegrees(this.getYRot() - this.yRotO)) * Units.TICKS_PER_SECOND;
        if (!simulating) {
            this.roll = Mth.lerp(0.35f, this.roll, this.entityData.get(DATA_ROLL));
            Vec3 f = this.getViewVector(1.0f);
            TelEngine.observe(this.engine, vel.x, vel.y, vel.z, f.x, f.y, f.z,
                    Mth.wrapDegrees(this.getYRot() - this.yRotO), this.engineParams());
        } else if (this.getDeltaMovement().lengthSqr() > 0.09) {
            this.crushScan(this.getDeltaMovement(), false, true);
        }

        double yawRad = Math.toRadians(this.getYRot());
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        Vec3 accel = vel.subtract(this.lastClientVelocity);
        this.lastClientVelocity = vel;
        this.sway.tick(accel.x * fwdX + accel.z * fwdZ, accel.x * -fwdZ + accel.z * fwdX, this.inertiaRate());

        float kmh = (float) (Math.hypot(vel.x, vel.z) * 20.0 * 3.6);
        this.speedKmh += (kmh - this.speedKmh) * 0.3f;

        this.updateWheelContact(simulating);
        this.spawnDamageParticles();
        this.spawnDust(vel);
        TelInput.clientTick.accept(this);
    }

    private void updateWheelContact(boolean simulating) {
        System.arraycopy(this.wheelOffset, 0, this.wheelOffsetO, 0, Suspension.WHEELS);
        if (this.joltDrivesPose()) {
            for (int i = 0; i < Suspension.WHEELS; i++) {
                this.wheelOffset[i] += (this.jolt.wheelOffset(i) - this.wheelOffset[i]) * 0.6;
            }
            return;
        }
        if (this.level().getNearestPlayer(this, 64.0) == null) {
            return;
        }
        if (!simulating) {
            TelWorld.GroundScan found = TelWorld.scanGround(this.level(), this.getX(), this.getY(), this.getZ(),
                    this.getYRot(), this.getXRot(), this.roll, this.climb(), this.softBlocksGiveWay());
            if (found != null) {
                this.lastScan = found;
            }
        }
        TelWorld.GroundScan g = this.lastScan;
        if (g == null) {
            return;
        }
        for (int i = 0; i < Suspension.WHEELS; i++) {
            double target = switch (g.wheelState()[i]) {
                case CONTACT -> Mth.clamp(g.wheelTop()[i] - Suspension.bottom(this.getY(), this.getXRot(), this.roll, i),
                        -0.3, 0.3);
                case HANGING -> -0.3;
                case WALL -> 0.0;
            };
            this.wheelOffset[i] += (target - this.wheelOffset[i]) * 0.5;
        }
    }

    public double getWheelOffset(int i, float partialTick) {
        return Mth.lerp(partialTick, this.wheelOffsetO[i], this.wheelOffset[i]);
    }

    @Nullable
    public TelWorld.GroundScan getLastScan() {
        return this.lastScan;
    }

    public int getWheelContacts() {
        if (this.joltDrivesPose() && this.jolt.vehicle() != null) {
            return this.jolt.vehicle().wheelsOnGround();
        }
        return this.wheelContacts;
    }

    public int getSkidSupports() {
        return this.skidSupports;
    }

    public boolean isAirborne() {
        if (this.joltDrivesPose()) {
            return this.jolt.vehicle() != null && this.jolt.vehicle().wheelsOnGround() == 0;
        }
        return this.suspension.airborne;
    }

    private void spawnDamageParticles() {
        if (!this.isBurning()) {
            return;
        }
        Vec3 deck = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll, TelGeometry.ENGINE_DECK);
        this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                deck.x + (this.random.nextDouble() - 0.5), deck.y, deck.z + (this.random.nextDouble() - 0.5),
                0.0, 0.08, 0.0);
        if (this.tickCount % 3 == 0) {
            this.level().addParticle(ParticleTypes.FLAME,
                    deck.x + (this.random.nextDouble() - 0.5), deck.y - 0.2, deck.z + (this.random.nextDouble() - 0.5),
                    0.0, 0.03, 0.0);
        }
    }

    private void spawnDust(Vec3 vel) {
        double speed = Math.hypot(vel.x, vel.z);
        if (speed < 0.1 || this.tickCount % 2 != 0 || !this.onGround()) {
            return;
        }
        for (double wheelZ : TelGeometry.WHEEL_Z) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                        this.getYRot(), this.getXRot(), this.roll,
                        new Vec3(side * TelGeometry.WHEEL_X, 0.1, wheelZ));
                BlockState below = this.level().getBlockState(BlockPos.containing(p.x, p.y - 0.3, p.z));
                if (below.is(BlockTags.SAND) || below.is(BlockTags.DIRT) || below.is(BlockTags.SNOW)) {
                    this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below),
                            p.x, p.y, p.z,
                            -vel.x * 0.3 + (this.random.nextDouble() - 0.5) * 0.1,
                            0.05 + this.random.nextDouble() * 0.05,
                            -vel.z * 0.3 + (this.random.nextDouble() - 0.5) * 0.1);
                }
            }
        }
    }

    public boolean isBurning() {
        return this.getHealth() <= this.getMaxHealth() * BURNING_FRACTION;
    }

    public boolean isEngineRunning() {
        return this.getControllingPassenger() != null || this.speedKmh > 1.0f;
    }

    public float getSpeedKmh() {
        return this.speedKmh;
    }

    public double getWheelRot(boolean driversLeft, float partialTick) {
        return driversLeft
                ? Mth.lerp(partialTick, this.wheelRotLO, this.engine.wheelRotL)
                : Mth.lerp(partialTick, this.wheelRotRO, this.engine.wheelRotR);
    }

    public double getSteerDegrees(float partialTick) {
        return Mth.lerp(partialTick, this.steerO, this.steerNow());
    }

    public float getSwayPitch(float partialTick) {
        return (float) Mth.lerp(partialTick, this.swayPitchO, this.sway.fakePitch);
    }

    public float getSwayRoll(float partialTick) {
        return (float) Mth.lerp(partialTick, this.swayRollO, this.sway.fakeRoll);
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                    this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        if (TelHandover.acceptsServerMotion(this.isControlledByLocalInstance())) {
            super.lerpMotion(x, y, z);
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

    protected int getMaxPassengers() {
        return TelGeometry.SEAT_COUNT;
    }

    private static EntityDataAccessor<Integer> seatData(int seat) {
        return seat == 0 ? DATA_SEAT_0 : seat == 1 ? DATA_SEAT_1 : DATA_SEAT_2;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < this.getMaxPassengers();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.level().isClientSide) {
            this.entityData.set(seatData(this.chooseSeat(passenger)), passenger.getId());
        }
        this.riders.put(passenger.getUUID(), new Rider(this.seatOf(passenger), this.getYRot()));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = this.seatOf(passenger);
        super.removePassenger(passenger);
        Rider rider = this.riders.get(passenger.getUUID());
        if (rider != null) {
            rider.seat = seat;
        }
        if (!this.level().isClientSide) {
            if (seat == 0) {
                this.setDeltaMovement(TelHandover.coastVelocity(this.serverVelocity));
            }
            for (int i = 0; i < TelGeometry.SEAT_COUNT; i++) {
                if (this.entityData.get(seatData(i)) == passenger.getId()) {
                    this.entityData.set(seatData(i), NO_OCCUPANT);
                }
            }
        }
    }

    private int chooseSeat(Entity arriving) {
        Integer saved = this.savedSeats.remove(arriving.getUUID());
        if (saved != null && saved >= 0 && saved < TelGeometry.SEAT_COUNT && this.isSeatFree(saved, arriving)) {
            return saved;
        }
        for (int i = 0; i < TelGeometry.SEAT_COUNT; i++) {
            if (this.isSeatFree(i, arriving)) {
                return i;
            }
        }
        return TelGeometry.SEAT_COUNT - 1;
    }

    private boolean isSeatFree(int seat, Entity arriving) {
        int occupant = this.entityData.get(seatData(seat));
        if (occupant == NO_OCCUPANT) {
            return true;
        }
        for (Entity p : this.getPassengers()) {
            if (p != arriving && p.getId() == occupant) {
                return false;
            }
        }
        return true;
    }

    public int seatOf(Entity passenger) {
        int id = passenger.getId();
        for (int i = 0; i < TelGeometry.SEAT_COUNT; i++) {
            if (this.entityData.get(seatData(i)) == id) {
                return i;
            }
        }
        return Mth.clamp(this.getPassengers().indexOf(passenger), 0, TelGeometry.SEAT_COUNT - 1);
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
        return TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll, TelGeometry.SEATS[this.seatOf(passenger)]);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);

        Rider rider = this.riders.computeIfAbsent(passenger.getUUID(),
                id -> new Rider(this.seatOf(passenger), this.getYRot()));
        rider.seat = this.seatOf(passenger);

        float turned = Mth.wrapDegrees(this.getYRot() - rider.vehicleYaw);
        rider.vehicleYaw = this.getYRot();
        passenger.setYRot(passenger.getYRot() + turned);
        passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        this.clampRotation(passenger);
    }

    @Override
    public void onPassengerTurned(Entity entityToUpdate) {
        this.clampRotation(entityToUpdate);
    }

    protected void clampRotation(Entity rider) {
        rider.setYBodyRot(this.getYRot());

        float yaw = Mth.wrapDegrees(rider.getYRot() - this.getYRot());
        float clampedYaw = Mth.clamp(yaw, -RIDER_YAW_LIMIT, RIDER_YAW_LIMIT);
        rider.yRotO += clampedYaw - yaw;
        rider.setYRot(rider.getYRot() + clampedYaw - yaw);
        rider.setYHeadRot(rider.getYRot());

        float pitch = rider.getXRot();
        float clampedPitch = Mth.clamp(pitch, -RIDER_PITCH_LIMIT, RIDER_PITCH_LIMIT);
        rider.xRotO += clampedPitch - pitch;
        rider.setXRot(clampedPitch);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Rider rider = this.riders.remove(passenger.getUUID());
        int seat = rider != null ? rider.seat : 0;

        Vec3 door = TelGeometry.DISMOUNTS[seat];
        Vec3 otherDoor = new Vec3(-door.x, door.y, door.z);
        Vec3[] candidates = {
                door,
                door.add(0.0, 0.0, -1.5),
                door.add(0.0, 0.0, 1.5),
                door.add(Math.signum(door.x), 0.0, 0.0),
                otherDoor,
                otherDoor.add(0.0, 0.0, -1.5),
        };

        for (Vec3 local : candidates) {
            Vec3 world = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                    this.getYRot(), 0.0f, 0.0f, local);
            BlockPos at = BlockPos.containing(world.x, world.y, world.z);
            for (int dy = 0; dy >= -1; dy--) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), this.level(),
                        at.offset(0, dy, 0), false);
                if (spot != null) {
                    return spot;
                }
            }
            Vec3 up = DismountHelper.findSafeDismountLocation(passenger.getType(), this.level(), at.above(), false);
            if (up != null) {
                return up;
            }
        }

        return new Vec3(this.getX(), this.getY() + TelGeometry.OBB_CENTER.y + TelGeometry.OBB_HALF.y, this.getZ());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (CreateCompat.isCreateFakePlayer(player)) {
            return InteractionResult.FAIL;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean client = this.level().isClientSide;

        if (held.is(ModItems.ISKANDER.get()) && !client) {
            player.displayClientMessage(Component.translatable("message.ata.tel.load_from_cab"), true);
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

        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (this.canPickUp(player)) {
                if (!client) {
                    this.pickUp(player);
                }
                return InteractionResult.sidedSuccess(client);
            }
        }

        if (!client && !player.startRiding(this)) {
            player.displayClientMessage(Component.translatable("message.ata.tel.full"), true);
        }
        return InteractionResult.sidedSuccess(client);
    }

    private boolean isRepairItem(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_BLOCK);
    }

    private void repair(Player player, ItemStack held) {
        float share = held.is(Items.IRON_BLOCK) ? 0.45f : 0.05f;
        this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + this.getMaxHealth() * share));
        this.repairCooldown = REPAIR_DELAY_TICKS;
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(),
                SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.ata.tel.repaired",
                Math.round(this.getHealth()), Math.round(this.getMaxHealth())), true);
    }

    private boolean canPickUp(Player player) {
        return this.getTelState() == TelState.STOWED
                && !this.hasAnyMissile()
                && this.getPassengers().isEmpty()
                && (this.ownerUuid == null || this.ownerUuid.equals(player.getUUID()) || player.getAbilities().instabuild);
    }

    private void pickUp(Player player) {
        ItemStack stack = new ItemStack(ModItems.ISKANDER_TEL.get());
        IskanderTelItem.writeHealth(stack, this.getHealth());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        this.discard();
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
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.ISKANDER_TEL.get());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.destroyed) {
            return false;
        }
        if (CreateCompat.isCreateDamageSource(source)) {
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
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(),
                SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 1.0f, 0.7f);

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

    public static final byte EVENT_DESTROYED = 120;
    public static final byte EVENT_DESTROYED_DETONATED = 121;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_DESTROYED || id == EVENT_DESTROYED_DETONATED) {
            TelInput.destroyedFx.accept(this, id == EVENT_DESTROYED_DETONATED);
            return;
        }
        super.handleEntityEvent(id);
    }

    private void destroy(DamageSource cause) {
        if (this.destroyed || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.destroyed = true;

        int rounds = 0;
        for (ItemStack stack : this.missiles) {
            if (!stack.isEmpty()) {
                rounds++;
            }
        }

        this.ejectPassengers();
        this.returnPendingRound();

        Entity blame = cause.getEntity();
        if (blame == null && this.ownerUuid != null) {
            blame = serverLevel.getPlayerByUUID(this.ownerUuid);
        }
        DamageSource blast = this.damageSources().explosion(this, blame);
        Level.ExplosionInteraction mode = ShahedConfig.doExplosionsBreakBlocks()
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;

        Vec3 centre = TelGeometry.collisionCenter(this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll);
        serverLevel.explode(this, blast, null, centre.x, centre.y, centre.z, 4.0f + 3.0f * rounds, false, mode);

        boolean detonated = false;
        for (int slot = 0; slot < this.missiles.length; slot++) {
            if (this.missiles[slot].isEmpty() || this.random.nextFloat() >= 0.5f) {
                continue;
            }
            detonated = true;
            Vec3 at = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                    this.getYRot(), this.getXRot(), this.roll, TelGeometry.stowedMissileCentre(slot));
            serverLevel.explode(this, blast, null, at.x, at.y, at.z, 10.0f, false, mode);
        }
        serverLevel.broadcastEntityEvent(this, detonated ? EVENT_DESTROYED_DETONATED : EVENT_DESTROYED);

        this.spawnAtLocation(new ItemStack(Items.IRON_INGOT, 4 + this.random.nextInt(5)));
        this.discard();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public void setHealth(float value) {
        this.entityData.set(DATA_HEALTH, Mth.clamp(value, 0.0f, this.getMaxHealth()));
    }

    public float getMaxHealth() {
        try {
            return (float) ShahedConfig.getTelMaxHealth();
        } catch (IllegalStateException e) {
            return DEFAULT_MAX_HEALTH;
        }
    }

    public boolean isLoaded(int slot) {
        return this.entityData.get(slot == 0 ? DATA_LOADED_0 : DATA_LOADED_1);
    }

    public ItemStack getMissile(int slot) {
        return this.missiles[slot];
    }

    public void setMissile(int slot, ItemStack stack) {
        this.missiles[slot] = stack;
        this.sequence.setLoaded(slot, !stack.isEmpty());
        this.entityData.set(slot == 0 ? DATA_LOADED_0 : DATA_LOADED_1, !stack.isEmpty());
    }

    public boolean hasAnyMissile() {
        return !this.missiles[0].isEmpty() || !this.missiles[1].isEmpty();
    }

    public void debugFill() {
        for (int slot = 0; slot < this.missiles.length; slot++) {
            if (this.missiles[slot].isEmpty()) {
                this.setMissile(slot, new ItemStack(ModItems.ISKANDER.get()));
            }
        }
    }

    public float getRoll() {
        return this.roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
        if (!this.level().isClientSide && Math.abs(this.entityData.get(DATA_ROLL) - roll) > 0.05f) {
            this.entityData.set(DATA_ROLL, roll);
        }
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
        tag.putBoolean("Headlights", this.areHeadlightsOn());
        tag.putFloat("Health", this.getHealth());
        tag.putFloat("Roll", this.roll);
        TelSequence.Snapshot snap = this.sequence.snapshot();
        CompoundTag cycle = new CompoundTag();
        cycle.putInt("State", snap.state());
        cycle.putInt("Ticks", snap.ticks());
        cycle.putInt("CoverClip", snap.coverClip());
        cycle.putInt("CoverAge", snap.coverAge());
        cycle.putInt("Lift1Clip", snap.lift1Clip());
        cycle.putInt("Lift1Age", snap.lift1Age());
        cycle.putInt("Lift2Clip", snap.lift2Clip());
        cycle.putInt("Lift2Age", snap.lift2Age());
        cycle.putString("StateName", TelState.byOrdinal(snap.state()).name());
        cycle.putBoolean("JacksDown", snap.jacksDown());
        cycle.putBoolean("CoversOpen", snap.coversOpen());
        cycle.putInt("Raised", snap.raisedMask());
        cycle.putInt("Next", snap.next());
        cycle.putInt("NextMask", snap.nextMask());
        cycle.putBoolean("StowAfter", snap.stowAfter());
        cycle.putInt("Load", snap.load());
        cycle.putInt("PendingSlot", snap.pendingSlot());
        cycle.putBoolean("RoundHeld", snap.roundHeld());
        cycle.putInt("Launch0", snap.launchAt0());
        cycle.putInt("Launch1", snap.launchAt1());
        cycle.putInt("Interval", snap.intervalSeconds());
        tag.put("Cycle", cycle);
        if (!this.pendingRound.isEmpty()) {
            tag.put("PendingRound", this.pendingRound.save(this.registryAccess()));
        }
        if (this.loadingPlayer != null) {
            tag.putUUID("LoadingPlayer", this.loadingPlayer);
        }
        if (this.commandPlayer != null) {
            tag.putUUID("CommandPlayer", this.commandPlayer);
        }
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        for (int slot = 0; slot < 2; slot++) {
            if (this.hasTarget(slot)) {
                tag.putInt("TargetX" + slot, this.getTargetX(slot));
                tag.putInt("TargetZ" + slot, this.getTargetZ(slot));
            }
        }

        ListTag list = new ListTag();
        for (int slot = 0; slot < this.missiles.length; slot++) {
            if (!this.missiles[slot].isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                entry.put("Item", this.missiles[slot].save(this.registryAccess()));
                list.add(entry);
            }
        }
        tag.put("Missiles", list);

        ListTag seats = new ListTag();
        for (Entity p : this.getPassengers()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Rider", p.getUUID());
            entry.putByte("Seat", (byte) this.seatOf(p));
            seats.add(entry);
        }
        tag.put("Seats", seats);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.isLegitSpawn = tag.contains("LegitSpawn") && tag.getBoolean("LegitSpawn");
        this.entityData.set(DATA_HEADLIGHTS, tag.getBoolean("Headlights"));
        if (tag.contains("Health")) {
            this.setHealth(tag.getFloat("Health"));
        }
        this.roll = tag.getFloat("Roll");
        this.rollO = this.roll;
        this.entityData.set(DATA_ROLL, this.roll);
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
        }
        for (int slot = 0; slot < 2; slot++) {
            if (tag.contains("TargetX" + slot)) {
                this.setTarget(slot, tag.getInt("TargetX" + slot), tag.getInt("TargetZ" + slot));
            } else if (tag.contains("TargetX")) {
                this.setTarget(slot, tag.getInt("TargetX"), tag.getInt("TargetZ"));
            }
        }

        this.savedSeats.clear();
        ListTag seats = tag.getList("Seats", Tag.TAG_COMPOUND);
        for (int i = 0; i < seats.size(); i++) {
            CompoundTag entry = seats.getCompound(i);
            if (entry.hasUUID("Rider")) {
                this.savedSeats.put(entry.getUUID("Rider"), (int) entry.getByte("Seat"));
            }
        }

        this.missiles[0] = ItemStack.EMPTY;
        this.missiles[1] = ItemStack.EMPTY;
        this.sequence.setLoaded(0, false);
        this.sequence.setLoaded(1, false);
        ListTag list = tag.getList("Missiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < this.missiles.length) {
                this.setMissile(slot, ItemStack.parse(this.registryAccess(), entry.getCompound("Item"))
                        .orElse(ItemStack.EMPTY));
            }
        }

        if (tag.contains("Cycle", Tag.TAG_COMPOUND)) {
            CompoundTag cycle = tag.getCompound("Cycle");
            if (cycle.contains("StateName", Tag.TAG_STRING)) {
                this.sequence.restore(new TelSequence.Snapshot(stateByName(cycle.getString("StateName")),
                        cycle.getInt("Ticks"),
                        cycle.getInt("CoverClip"), cycle.getInt("CoverAge"), cycle.getInt("Lift1Clip"),
                        cycle.getInt("Lift1Age"), cycle.getInt("Lift2Clip"), cycle.getInt("Lift2Age"),
                        cycle.getBoolean("JacksDown"), cycle.getBoolean("CoversOpen"),
                        cycle.getInt("Raised"), cycle.getInt("Next"), cycle.getInt("NextMask"),
                        cycle.getBoolean("StowAfter"), cycle.getInt("Load"), cycle.getInt("PendingSlot"),
                        cycle.getBoolean("RoundHeld"), cycle.contains("Launch0") ? cycle.getInt("Launch0") : -1,
                        cycle.contains("Launch1") ? cycle.getInt("Launch1") : -1,
                        cycle.contains("Interval") ? cycle.getInt("Interval") : TelSequence.DEFAULT_INTERVAL_SECONDS));
            } else {
                this.sequence.restoreResting(cycle.getInt("State"), cycle.getBoolean("JacksDown"),
                        cycle.getBoolean("CoversOpen"), cycle.getInt("Raised"));
            }
        } else {
            this.sequence.reset();
        }
        if (tag.contains("PendingRound", Tag.TAG_COMPOUND)) {
            this.pendingRound = ItemStack.parse(this.registryAccess(), tag.getCompound("PendingRound"))
                    .orElse(ItemStack.EMPTY);
        }
        if (this.pendingRound.isEmpty()) {
            this.sequence.dropHeldRound();
        }
        if (tag.hasUUID("LoadingPlayer")) {
            this.loadingPlayer = tag.getUUID("LoadingPlayer");
        }
        if (tag.hasUUID("CommandPlayer")) {
            this.commandPlayer = tag.getUUID("CommandPlayer");
        }
        this.publishSequence(true);
    }

    private static int stateByName(String name) {
        try {
            return TelState.valueOf(name).ordinal();
        } catch (IllegalArgumentException e) {
            return TelState.STOWED.ordinal();
        }
    }

    public TelHitboxPose hitboxPose() {
        if (!this.level().isClientSide) {
            return this.sequence.hitboxPose(0.0f).withLoaded(this.isLoaded(0), this.isLoaded(1));
        }
        return this.poseFor(0.0f);
    }

    public TelHitboxPose poseFor(float partialTick) {
        return TelPose.pose(
                this.getGroupClip(TelBoneGroups.COVERS), this.getGroupAge(TelBoneGroups.COVERS, partialTick),
                this.getGroupClip(TelBoneGroups.LIFT_1), this.getGroupAge(TelBoneGroups.LIFT_1, partialTick),
                this.getGroupClip(TelBoneGroups.LIFT_2), this.getGroupAge(TelBoneGroups.LIFT_2, partialTick),
                this.getJackProgress(partialTick), this.isLoaded(0), this.isLoaded(1));
    }

    private void refreshBoxes() {
        long key = ((long) this.tickCount << 1) | (this.level().isClientSide ? 1L : 0L);
        if (this.modelBoxes == null || this.boxKey != key) {
            this.modelBoxes = TelModelHitbox.get().compute(this.hitboxPose(), this.getX(), this.getY(), this.getZ(),
                    this.getYRot(), this.getXRot(), this.roll);
            this.modelObbs = TelHitboxQuery.toObbs(this.modelBoxes);
            this.boxKey = key;
        }
    }

    @Override
    public List<com.synarsis.airtacticalarsenal.combat.hitbox.Obb> getObbs() {
        this.refreshBoxes();
        return this.modelObbs;
    }

    public List<WorldBox> getSolidBoxes() {
        this.refreshBoxes();
        return this.modelBoxes;
    }

    @Override
    public List<com.synarsis.airtacticalarsenal.combat.hitbox.Obb> getRenderObbs(float partialTick) {
        long key = ((long) this.tickCount << 12) | (long) Math.round(partialTick * 1000.0f);
        if (this.renderObbs == null || this.renderKey != key) {
            double x = Mth.lerp(partialTick, this.xo, this.getX());
            double y = Mth.lerp(partialTick, this.yo, this.getY());
            double z = Mth.lerp(partialTick, this.zo, this.getZ());
            float yaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
            float pitch = Mth.lerp(partialTick, this.xRotO, this.getXRot());
            float rollNow = Mth.lerp(partialTick, this.rollO, this.roll);
            this.renderObbs = TelHitboxQuery.toObbs(
                    TelModelHitbox.get().compute(this.level().isClientSide ? this.poseFor(partialTick) : this.hitboxPose(),
                            x, y, z, yaw, pitch, rollNow));
            this.renderKey = key;
        }
        return this.renderObbs;
    }

    public List<com.synarsis.airtacticalarsenal.combat.hitbox.Obb> getPickObbs() {
        return this.level().isClientSide
                ? this.getRenderObbs((float) TelInput.partialTick.getAsDouble())
                : this.getObbs();
    }

    public Vec3 getCarryDelta() {
        return this.carryDelta;
    }

    public float getCarryYaw() {
        return this.carryYaw;
    }

    private void sweepBullets() {
        if (!com.synarsis.airtacticalarsenal.compat.TacZCompat.isTacZLoaded()) {
            return;
        }
        BulletSweep.runTagged(this.level(), this, this.getBoundingBoxForCulling(), this.getObbs(),
                (tag, point, damage, shooter, weaponId) -> this.onBulletHit(tag, point, damage, shooter));
    }

    private void onBulletHit(HitTag tag, Vec3 point, float damage, @Nullable Entity shooter) {
        if (this.destroyed || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Zone zone = tag instanceof Zone z ? z : Zone.CHASSIS;
        DamageSource source = new DamageSource(
                serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(DamageTypes.MOB_PROJECTILE),
                null, shooter, point);
        this.hurt(source, damage * zone.damageMultiplier());

        if (zone.isMissile() && damage >= 15.0f && this.random.nextFloat() < 0.15f) {
            int slot = zone == Zone.MISSILE_1 ? 0 : 1;
            if (!this.missiles[slot].isEmpty()) {
                this.setMissile(slot, ItemStack.EMPTY);
                Level.ExplosionInteraction mode = ShahedConfig.doExplosionsBreakBlocks()
                        ? Level.ExplosionInteraction.TNT
                        : Level.ExplosionInteraction.NONE;
                serverLevel.explode(this, this.damageSources().explosion(this, shooter), null,
                        point.x, point.y, point.z, 10.0f, false, mode);
            }
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        TelRegistry.add(this.level(), this);
    }

    @Override
    public void onRemovedFromLevel() {
        this.dropJolt();
        super.onRemovedFromLevel();
        TelRegistry.remove(this.level(), this);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.dropJolt();
        TelRegistry.remove(this.level(), this);
        if (!this.level().isClientSide && !reason.shouldSave()) {
            this.returnPendingRound();
        }
        super.remove(reason);
    }

    public static final byte CMD_PREPARE = 0;
    public static final byte CMD_LAUNCH = 1;
    public static final byte CMD_ABORT = 2;
    public static final byte CMD_LOAD = 3;
    public static final byte CMD_UNLOAD = 4;
    public static final byte CMD_COVERS = 5;
    public static final byte CMD_LOWER = 6;
    public static final byte CMD_STOW = 7;
    public static final byte CMD_SET_INTERVAL = 8;
    public static final byte CMD_HEADLIGHTS = 9;

    public static final int PREPARING_TICKS = TelSequence.COUNTDOWN_TICKS;

    public TelState getTelState() {
        return TelState.byOrdinal(this.entityData.get(DATA_STATE));
    }

    public TelPose.Clip getGroupClip(int group) {
        return TelPose.Clip.byOrdinal(this.entityData.get(switch (group) {
            case TelBoneGroups.COVERS -> DATA_COVER_CLIP;
            case TelBoneGroups.LIFT_1 -> DATA_LIFT_1_CLIP;
            default -> DATA_LIFT_2_CLIP;
        }));
    }

    public double getGroupAge(int group, float partialTick) {
        int start = this.entityData.get(switch (group) {
            case TelBoneGroups.COVERS -> DATA_COVER_START;
            case TelBoneGroups.LIFT_1 -> DATA_LIFT_1_START;
            default -> DATA_LIFT_2_START;
        });
        return Math.max(0.0, (int) this.level().getGameTime() - start + partialTick);
    }

    public int getRaisedMask() {
        return this.entityData.get(DATA_RAISED_MASK);
    }

    public boolean isRaisedNow(int slot) {
        return this.getGroupClip(TelBoneGroups.liftOf(slot)) == TelPose.Clip.HELD;
    }

    public int getVisualTicks() {
        if (!this.level().isClientSide) {
            return this.sequence.ticks();
        }
        int ticks = this.syncTicks + (this.tickCount - this.syncAt);
        int duration = this.stateDuration(this.getTelState());
        return duration > 0 ? Math.min(ticks, duration - 1) : ticks;
    }

    public int stateDuration(TelState state) {
        if (state != TelState.COVER_LOAD) {
            return state.duration();
        }
        if (!this.level().isClientSide) {
            return this.sequence.durationOf(state);
        }
        try {
            return ShahedConfig.getTelLoadTicks();
        } catch (IllegalStateException e) {
            return state.duration();
        }
    }

    public float getJackProgress(float partialTick) {
        if (!this.level().isClientSide) {
            return this.sequence.jackProgress(partialTick);
        }
        return Mth.lerp(partialTick, this.jackPrev, this.jackNow);
    }

    public int getCountdownLeft(int slot) {
        return this.entityData.get(slot == 0 ? DATA_COUNTDOWN_0 : DATA_COUNTDOWN_1);
    }

    public int getSalvoInterval() {
        return this.entityData.get(DATA_INTERVAL);
    }

    public boolean isCrewSeat(Entity player) {
        return this.isCrew(player);
    }

    private boolean isCrew(Entity player) {
        if (!this.hasPassenger(player)) {
            return false;
        }
        int seat = this.seatOf(player);
        return seat == 0 || seat == 1;
    }

    private double maxLaunchTilt() {
        try {
            return ShahedConfig.getTelMaxLaunchTilt();
        } catch (IllegalStateException e) {
            return 8.0;
        }
    }

    public int skyClearance() {
        try {
            return ShahedConfig.getTelSkyClearance();
        } catch (IllegalStateException e) {
            return 40;
        }
    }

    public TelLaunchRules.Stance stance() {
        double speed = this.level().isClientSide
                ? this.speedKmh / 72.0
                : Math.hypot(this.serverVelocity.x, this.serverVelocity.z);
        return new TelLaunchRules.Stance(speed, this.onGround(), this.isInWater(), this.getXRot(), this.roll);
    }

    public TelLaunchRules.Aim aim(int slot) {
        boolean has = this.hasTarget(slot);
        double distance = has ? TelLaunchRules.groundDistance(this.getX(), this.getZ(),
                this.getTargetX(slot), this.getTargetZ(slot)) : 0.0;
        return new TelLaunchRules.Aim(has, distance, ShahedConfig.getIskanderMinDistance(),
                ShahedConfig.getIskanderMaxRange(),
                com.synarsis.airtacticalarsenal.debug.DebugOverrides.noLaunchLimits(),
                has && ShahedConfig.isInForbiddenZone(this.getTargetX(slot), this.getTargetZ(slot)));
    }

    public record Clearance(@Nullable BlockPos roof, BlockPos[] erector, BlockPos[] sky) {
    }

    public TelClearance.Pose clearancePose() {
        return new TelClearance.Pose(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.roll);
    }

    public TelClearance.Solid worldSolid() {
        Level level = this.level();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            probe.set(x, y, z);
            if (level.isOutsideBuildHeight(y) || !level.hasChunkAt(probe)) {
                return false;
            }
            return !level.getBlockState(probe).getCollisionShape(level, probe).isEmpty();
        };
    }

    @Nullable
    public BlockPos roofBlocker() {
        return TelClearance.roofBlock(this.clearancePose(), this.worldSolid());
    }

    @Nullable
    public BlockPos erectorBlocker(int slot) {
        return TelClearance.arcBlock(slot, this.clearancePose(), this.worldSolid());
    }

    @Nullable
    public BlockPos skyBlocker(int slot) {
        return TelClearance.skyBlock(slot, this.clearancePose(), this.skyClearance(), this.worldSolid());
    }

    public Clearance clearance() {
        if (this.clearanceCache == null || this.tickCount - this.clearanceTick >= 10
                || this.tickCount < this.clearanceTick) {
            this.clearanceTick = this.tickCount;
            this.clearanceCache = new Clearance(this.roofBlocker(),
                    new BlockPos[] {this.erectorBlocker(0), this.erectorBlocker(1)},
                    new BlockPos[] {this.skyBlocker(0), this.skyBlocker(1)});
        }
        return this.clearanceCache;
    }

    private void deny(ServerPlayer player, TelLaunchRules.Denial denial) {
        player.displayClientMessage(Component.translatable(denial.reason().messageKey(), denial.args()), true);
    }

    private static TelLaunchRules.Denial of(TelLaunchRules.Refusal r) {
        return TelLaunchRules.Denial.of(r);
    }

    private TelLaunchRules.Refusal stillEnough() {
        return this.stance().speed() >= TelLaunchRules.STANDSTILL_SPEED
                ? TelLaunchRules.Refusal.MOVING : TelLaunchRules.Refusal.NONE;
    }

    public TelSequence seqView() {
        if (!this.level().isClientSide) {
            return this.sequence;
        }
        return TelSequence.view(this.getTelState(), this.getRaisedMask(),
                (this.isLoaded(0) ? 1 : 0) | (this.isLoaded(1) ? 2 : 0));
    }

    @Nullable
    private BlockPos roofAt() {
        return this.level().isClientSide ? this.clearance().roof() : this.roofBlocker();
    }

    @Nullable
    private BlockPos erectorAt(int slot) {
        return this.level().isClientSide ? this.clearance().erector()[slot] : this.erectorBlocker(slot);
    }

    @Nullable
    private BlockPos skyAt(int slot) {
        return this.level().isClientSide ? this.clearance().sky()[slot] : this.skyBlocker(slot);
    }

    private TelLaunchRules.Denial coversRoom(TelSequence s) {
        if (s.state() != TelState.STOWED) {
            return TelLaunchRules.Denial.NONE;
        }
        BlockPos blocker = this.roofAt();
        return blocker == null ? TelLaunchRules.Denial.NONE
                : new TelLaunchRules.Denial(TelLaunchRules.Refusal.COVERS_BLOCKED, -1, TelClearance.describe(blocker), 0);
    }

    public TelLaunchRules.Denial coversDenial() {
        TelSequence s = this.seqView();
        TelLaunchRules.Refusal r = s.canCovers();
        if (r.ok()) {
            r = this.stillEnough();
        }
        return r.ok() ? this.coversRoom(s) : of(r);
    }

    public TelLaunchRules.Denial loadDenial(Player player, int slot) {
        TelSequence s = this.seqView();
        TelLaunchRules.Refusal r = s.canLoad(slot);
        if (r.ok()) {
            r = this.stillEnough();
        }
        if (!r.ok()) {
            return of(r);
        }
        if (this.roundOf(player) == null) {
            return of(TelLaunchRules.Refusal.NO_ROUND_IN_INVENTORY);
        }
        return this.coversRoom(s);
    }

    public TelLaunchRules.Denial unloadDenial(int slot) {
        TelSequence s = this.seqView();
        TelLaunchRules.Refusal r = s.canUnload(slot);
        if (r.ok()) {
            r = this.stillEnough();
        }
        return r.ok() ? this.coversRoom(s) : of(r);
    }

    public TelLaunchRules.Denial prepareDenial(int mask) {
        TelSequence s = this.seqView();
        TelLaunchRules.Refusal r = s.canPrepare(mask);
        if (!r.ok()) {
            return of(r);
        }
        r = TelLaunchRules.stance(this.stance(), this.maxLaunchTilt());
        if (!r.ok()) {
            return of(r);
        }
        TelLaunchRules.Denial roof = this.coversRoom(s);
        if (!roof.ok()) {
            return roof;
        }
        for (int slot = 0; slot < 2; slot++) {
            if ((mask & (1 << slot)) == 0) {
                continue;
            }
            BlockPos arc = this.erectorAt(slot);
            if (arc != null) {
                return new TelLaunchRules.Denial(TelLaunchRules.Refusal.ERECTOR_BLOCKED, slot,
                        TelClearance.describe(arc), 0);
            }
            BlockPos sky = this.skyAt(slot);
            if (sky != null) {
                return new TelLaunchRules.Denial(TelLaunchRules.Refusal.SKY_BLOCKED, slot,
                        TelClearance.describe(sky), 0);
            }
        }
        return TelLaunchRules.Denial.NONE;
    }

    public TelLaunchRules.Denial launchDenial() {
        TelSequence s = this.seqView();
        TelLaunchRules.Refusal r = s.canLaunch();
        if (!r.ok()) {
            return of(r);
        }
        r = TelLaunchRules.stance(this.stance(), this.maxLaunchTilt());
        if (!r.ok()) {
            return of(r);
        }
        int mask = s.launchMask();
        for (int slot = 0; slot < 2; slot++) {
            if ((mask & (1 << slot)) == 0) {
                continue;
            }
            TelLaunchRules.Denial aim = TelLaunchRules.aim(this.aim(slot), slot);
            if (!aim.ok()) {
                return aim;
            }
            BlockPos sky = this.skyAt(slot);
            if (sky != null) {
                return new TelLaunchRules.Denial(TelLaunchRules.Refusal.SKY_BLOCKED, slot,
                        TelClearance.describe(sky), 0);
            }
        }
        return TelLaunchRules.Denial.NONE;
    }

    @Nullable
    private ItemStack roundOf(Player player) {
        if (player.getMainHandItem().is(ModItems.ISKANDER.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModItems.ISKANDER.get())) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ISKANDER.get())) {
                return stack;
            }
        }
        return null;
    }

    public void handleCommand(ServerPlayer player, byte command, byte mask, int arg) {
        if (this.level().isClientSide || this.destroyed) {
            return;
        }
        if (!this.isCrew(player)) {
            this.deny(player, of(TelLaunchRules.Refusal.NOT_CREW));
            return;
        }
        TelLaunchRules.Denial denial = switch (command) {
            case CMD_PREPARE -> this.orderPrepare(mask);
            case CMD_LAUNCH -> this.orderLaunch(player);
            case CMD_ABORT -> this.orderAbort(player);
            case CMD_LOAD -> this.orderLoad(player, mask);
            case CMD_UNLOAD -> this.orderUnload(player, mask);
            case CMD_COVERS -> this.orderCovers();
            case CMD_LOWER -> this.orderLower(mask);
            case CMD_HEADLIGHTS -> this.orderHeadlights(player);
            case CMD_STOW -> of(this.sequence.tryStow());
            case CMD_SET_INTERVAL -> {
                this.sequence.setIntervalSeconds(arg);
                yield TelLaunchRules.Denial.NONE;
            }
            default -> of(TelLaunchRules.Refusal.BUSY);
        };
        if (!denial.ok()) {
            this.deny(player, denial);
        }
        this.publishSequence(true);
    }

    private TelLaunchRules.Denial orderPrepare(int mask) {
        TelLaunchRules.Denial denial = this.prepareDenial(mask);
        if (denial.ok()) {
            this.sequence.tryPrepare(mask);
        }
        return denial;
    }

    public boolean headlightsWork() {
        return TelHeadlights.works(this.getHealth(), this.getMaxHealth(), this.destroyed, BURNING_FRACTION);
    }

    public boolean areHeadlightsOn() {
        return this.entityData.get(DATA_HEADLIGHTS);
    }

    private TelLaunchRules.Denial orderHeadlights(ServerPlayer player) {
        if (this.seatOf(player) != 0) {
            return of(TelLaunchRules.Refusal.NOT_CREW);
        }
        this.entityData.set(DATA_HEADLIGHTS, TelHeadlights.toggled(this.areHeadlightsOn(), this.headlightsWork()));
        return TelLaunchRules.Denial.NONE;
    }

    public boolean toggleHeadlights() {
        this.entityData.set(DATA_HEADLIGHTS, TelHeadlights.toggled(this.areHeadlightsOn(), this.headlightsWork()));
        return this.areHeadlightsOn();
    }

    public TelLaunchRules.Denial lowerDenial(int mask) {
        return of(this.seqView().canLower(mask));
    }

    private TelLaunchRules.Denial orderLower(int mask) {
        TelLaunchRules.Denial denial = this.lowerDenial(mask);
        if (denial.ok()) {
            this.sequence.tryLower(mask);
        }
        return denial;
    }

    private TelLaunchRules.Denial orderLaunch(ServerPlayer player) {
        TelLaunchRules.Denial denial = this.launchDenial();
        if (denial.ok() && this.sequence.tryLaunch().ok()) {
            this.commandPlayer = player.getUUID();
            player.displayClientMessage(Component.translatable("message.ata.tel.launch_started"), true);
        }
        return denial;
    }

    private TelLaunchRules.Denial orderAbort(ServerPlayer player) {
        if (!this.sequence.abort()) {
            return of(TelLaunchRules.Refusal.BUSY);
        }
        player.displayClientMessage(Component.translatable("message.ata.tel.aborted"), true);
        return TelLaunchRules.Denial.NONE;
    }

    private TelLaunchRules.Denial orderCovers() {
        TelLaunchRules.Denial denial = this.coversDenial();
        if (denial.ok()) {
            this.sequence.tryCovers();
        }
        return denial;
    }

    private TelLaunchRules.Denial orderLoad(ServerPlayer player, int slot) {
        TelLaunchRules.Denial denial = this.loadDenial(player, slot);
        ItemStack source = denial.ok() ? this.roundOf(player) : null;
        if (source != null && this.sequence.tryLoad(slot).ok()) {
            this.pendingRound = source.copyWithCount(1);
            if (!player.getAbilities().instabuild) {
                source.shrink(1);
            }
            this.loadingPlayer = player.getUUID();
        }
        return denial;
    }

    private TelLaunchRules.Denial orderUnload(ServerPlayer player, int slot) {
        TelLaunchRules.Denial denial = this.unloadDenial(slot);
        if (denial.ok() && this.sequence.tryUnload(slot).ok()) {
            this.loadingPlayer = player.getUUID();
        }
        return denial;
    }

    public void handleSetTarget(ServerPlayer player, int slot, int x, int z) {
        if (this.level().isClientSide || this.destroyed) {
            return;
        }
        if (!this.isCrew(player)) {
            this.deny(player, of(TelLaunchRules.Refusal.NOT_CREW));
            return;
        }
        if (slot < 0 || slot > 2 || this.sequence.state() == TelState.COUNTDOWN
                || Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
            this.deny(player, of(TelLaunchRules.Refusal.BUSY));
            return;
        }
        for (int s = 0; s < 2; s++) {
            if (slot == 2 || slot == s) {
                this.setTarget(s, x, z);
            }
        }
        player.displayClientMessage(Component.translatable("message.ata.tel.target_set",
                slot == 2 ? "1+2" : Integer.toString(slot + 1), x, z), true);
    }

    private void setTarget(int slot, int x, int z) {
        this.entityData.set(slot == 0 ? DATA_HAS_TARGET_0 : DATA_HAS_TARGET_1, true);
        this.entityData.set(slot == 0 ? DATA_TARGET_X_0 : DATA_TARGET_X_1, x);
        this.entityData.set(slot == 0 ? DATA_TARGET_Z_0 : DATA_TARGET_Z_1, z);
    }

    public boolean hasTarget(int slot) {
        return this.entityData.get(slot == 0 ? DATA_HAS_TARGET_0 : DATA_HAS_TARGET_1);
    }

    public int getTargetX(int slot) {
        return this.entityData.get(slot == 0 ? DATA_TARGET_X_0 : DATA_TARGET_X_1);
    }

    public int getTargetZ(int slot) {
        return this.entityData.get(slot == 0 ? DATA_TARGET_Z_0 : DATA_TARGET_Z_1);
    }

    private void commitLoad() {
        int slot = this.sequence.pendingSlot();
        if (this.pendingRound.isEmpty() || !this.missiles[slot].isEmpty()) {
            return;
        }
        this.setMissile(slot, this.pendingRound);
        this.pendingRound = ItemStack.EMPTY;
        this.loadingPlayer = null;
    }

    private void commitUnload() {
        int slot = this.sequence.pendingSlot();
        ItemStack out = this.missiles[slot];
        if (out.isEmpty()) {
            return;
        }
        this.setMissile(slot, ItemStack.EMPTY);
        Player who = this.loadingPlayer == null ? null : this.level().getPlayerByUUID(this.loadingPlayer);
        this.loadingPlayer = null;
        if (who != null && who.getInventory().add(out)) {
            return;
        }
        Vec3 at = who != null ? who.position() : this.bayCentre();
        this.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(this.level(), at.x, at.y, at.z, out));
    }

    private Vec3 bayCentre() {
        return TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll, new Vec3(0.0, 2.0, -4.5));
    }

    private void returnPendingRound() {
        if (this.pendingRound.isEmpty() || this.level().isClientSide) {
            return;
        }
        ItemStack round = this.pendingRound;
        this.pendingRound = ItemStack.EMPTY;
        this.sequence.dropHeldRound();
        Player who = this.loadingPlayer == null ? null : this.level().getPlayerByUUID(this.loadingPlayer);
        if (who != null && who.getInventory().add(round)) {
            return;
        }
        Vec3 at = this.bayCentre();
        this.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(this.level(), at.x, at.y, at.z, round));
    }

    private void tickSequence() {
        if (this.sequence.state() != TelState.COVER_LOAD) {
            this.sequence.setLoadTicks(this.configuredLoadTicks());
        }
        TelSequence.Event event = this.sequence.tick();
        switch (event) {
            case LOAD_COMMIT -> this.commitLoad();
            case UNLOAD_COMMIT -> this.commitUnload();
            case LAUNCH_0 -> this.launchRocket(0);
            case LAUNCH_1 -> this.launchRocket(1);
            default -> {
            }
        }
        this.publishSequence(this.sequence.changed());
    }

    private int configuredLoadTicks() {
        try {
            return ShahedConfig.getTelLoadTicks();
        } catch (IllegalStateException e) {
            return TelSequence.DEFAULT_LOAD_TICKS;
        }
    }

    private void publishSequence(boolean changed) {
        this.entityData.set(DATA_STATE, this.sequence.state().ordinal());
        long now = this.level().getGameTime();
        this.publishGroup(TelBoneGroups.COVERS, DATA_COVER_CLIP, DATA_COVER_START, now);
        this.publishGroup(TelBoneGroups.LIFT_1, DATA_LIFT_1_CLIP, DATA_LIFT_1_START, now);
        this.publishGroup(TelBoneGroups.LIFT_2, DATA_LIFT_2_CLIP, DATA_LIFT_2_START, now);
        this.entityData.set(DATA_RAISED_MASK, this.sequence.raisedMask());
        this.entityData.set(DATA_JACKS, Math.round(this.sequence.jackProgress(0.0f) * 100.0f));
        this.entityData.set(DATA_COUNTDOWN_0, this.sequence.launchIn(0));
        this.entityData.set(DATA_COUNTDOWN_1, this.sequence.launchIn(1));
        this.entityData.set(DATA_INTERVAL, this.sequence.intervalSeconds());
        if (changed || this.sequence.ticks() % 5 == 0) {
            this.entityData.set(DATA_STATE_TICKS, this.sequence.ticks());
        }
    }

    private void publishGroup(int group, EntityDataAccessor<Integer> clip, EntityDataAccessor<Integer> start, long now) {
        TelPose.Clip c = this.sequence.groupClip(group);
        this.entityData.set(clip, c.ordinal());
        this.entityData.set(start, c.moving() ? (int) now - this.sequence.groupAge(group) : 0);
    }

    private void tickCycleClient() {
        int state = this.entityData.get(DATA_STATE);
        int ticks = this.entityData.get(DATA_STATE_TICKS);
        if (state != this.syncState || ticks != this.syncTicks) {
            this.syncState = state;
            this.syncTicks = ticks;
            this.syncAt = this.tickCount;
        }
        this.jackPrev = this.jackNow;
        float target = this.entityData.get(DATA_JACKS) / 100.0f;
        this.jackNow = target;
    }

    public Vec3 footOf(int slot) {
        return TelGeometry.missileBase(slot, this.getX(), this.getY(), this.getZ(),
                this.getYRot(), this.getXRot(), this.roll);
    }

    private void launchRocket(int slot) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Player commander = this.commandPlayer == null ? null : level.getPlayerByUUID(this.commandPlayer);
        if (this.missiles[slot].isEmpty() || !this.hasTarget(slot)) {
            this.crewMessage(Component.translatable("message.ata.tel.launch_cancelled", slot + 1));
            return;
        }
        BlockPos blocker = this.skyBlocker(slot);
        if (blocker != null) {
            this.crewMessage(Component.translatable(TelLaunchRules.Refusal.SKY_BLOCKED.messageKey(), slot + 1,
                    TelClearance.describe(blocker)));
            return;
        }

        Vec3 foot = this.footOf(slot);
        Vec3 spawn = foot.add(0.0, 4.0, 0.0);
        BlockPos target = new BlockPos(this.getTargetX(slot), ShahedConfig.TARGET_Y_LEVEL.get(), this.getTargetZ(slot));

        IskanderEntity missile = new IskanderEntity(level, spawn, target, true);
        if (commander != null) {
            missile.setOwner(commander);
        }
        missile.setLauncherVehicle(this.getUUID());
        missile.isLegitSpawn = true;
        level.addFreshEntity(missile);

        this.setMissile(slot, ItemStack.EMPTY);
    }

    private void crewMessage(Component message) {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player && this.isCrew(player)) {
                player.displayClientMessage(message, true);
            }
        }
    }

    public boolean debugSkip() {
        TelState before = this.sequence.state();
        if (!before.isTransition() && before != TelState.COUNTDOWN) {
            return false;
        }
        int scheduled = (this.sequence.launchIn(0) >= 0 ? 1 : 0) + (this.sequence.launchIn(1) >= 0 ? 1 : 0);
        for (int i = 0; i < 2000; i++) {
            this.tickSequence();
            int now = (this.sequence.launchIn(0) >= 0 ? 1 : 0) + (this.sequence.launchIn(1) >= 0 ? 1 : 0);
            if (this.sequence.state() != before || now != scheduled) {
                break;
            }
        }
        return true;
    }

    public String debugState() {
        TelSequence s = this.sequence;
        return String.format(java.util.Locale.ROOT,
                "%s  %d ticks  groups [covers %s, lift 1 %s, lift 2 %s]  covers %s  jacks %s  raised %d  loaded [%s %s]  "
                        + "launch in [%d %d]  interval %d s  targets [%s] [%s]",
                s.state(), s.ticks(), s.groupClip(0), s.groupClip(1), s.groupClip(2), s.coversOpen() ? "open" : "shut",
                s.jacksDown() ? "down" : "up", s.raisedMask(), s.isLoaded(0) ? "x" : "-", s.isLoaded(1) ? "x" : "-",
                s.launchIn(0), s.launchIn(1), s.intervalSeconds(),
                this.hasTarget(0) ? this.getTargetX(0) + ", " + this.getTargetZ(0) : "none",
                this.hasTarget(1) ? this.getTargetX(1) + ", " + this.getTargetZ(1) : "none");
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
                this.getX() - TelGeometry.CULL_HALF_XZ, this.getY(), this.getZ() - TelGeometry.CULL_HALF_XZ,
                this.getX() + TelGeometry.CULL_HALF_XZ, this.getY() + TelGeometry.CULL_HEIGHT,
                this.getZ() + TelGeometry.CULL_HALF_XZ);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<IskanderTelEntity> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
