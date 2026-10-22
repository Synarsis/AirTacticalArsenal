package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.combat.hitbox.HitTag;
import com.synarsis.airtacticalarsenal.combat.hitbox.Obb;
import com.synarsis.airtacticalarsenal.combat.hitbox.ObbProvider;
import com.synarsis.airtacticalarsenal.combat.sweep.BulletSweep;
import com.synarsis.airtacticalarsenal.compat.CreateCompat;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotSequence.Command;
import com.synarsis.airtacticalarsenal.entity.tel.TelClearance;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelInput;
import com.synarsis.airtacticalarsenal.entity.tel.TelPose;
import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxPose;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.TelHitboxQuery;
import com.synarsis.airtacticalarsenal.entity.tel.hitbox.WorldBox;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import com.synarsis.airtacticalarsenal.entity.tel.physics.TelHandover;
import com.synarsis.airtacticalarsenal.item.ModItems;
import com.synarsis.airtacticalarsenal.item.PatriotItem;
import com.synarsis.airtacticalarsenal.sound.ModSounds;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.HitboxVehicle;
import com.synarsis.airtacticalarsenal.vehicle.hitbox.VehicleRegistry;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.item.Item;
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
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class PatriotVehicleEntity extends Entity implements GeoEntity, ObbProvider, HitboxVehicle {

    public static final byte IMPACT_HORIZONTAL = 0;
    public static final byte IMPACT_VERTICAL = 1;
    public static final byte EVENT_DESTROYED = 122;
    public static final byte EVENT_DESTROYED_DETONATED = 123;

    private static final float BURNING_FRACTION = 0.1f;
    private static final float BURNING_LOSS_PER_TICK = 0.1f;
    public static final double PANEL_RANGE = 6.0;
    private static final float ARMOUR = 2.0f;
    private static final int REPAIR_DELAY_TICKS = 10;
    private static final int IMPACT_COOLDOWN_TICKS = 4;
    private static final int CLEARANCE_REFRESH_TICKS = 10;

    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ART_YAW = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ART_PITCH = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ART_ROLL = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MASK = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OP = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OP_TICKS = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CLIPS = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_START_SUPPORTS = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_START_LIFT = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_START_SERVICE = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_TRAVERSE = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TRAVERSE_TARGET = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HITCHED = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TRACTOR_ID = SynchedEntityData
            .defineId(PatriotVehicleEntity.class, EntityDataSerializers.INT);


    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private final PatriotPart[] parts;
    private final PatriotSequence sequence;
    private PatriotSequence.Gate gate = PatriotSequence.Gate.OPEN;

    public boolean isLegitSpawn = false;
    @Nullable
    private UUID ownerUuid;

    public float roll;
    public float rollO;
    private float artYaw;
    private float artPitch;
    private float artRoll;
    public float artYawO;
    public float artPitchO;
    public float artRollO;

    private int syncOp = -2;
    private int syncTicks;
    private int syncAt;
    private float traverseNow;
    private float traverseO;

    @Nullable
    private TrailerJolt jolt;
    private Vec3 observedVelocity = Vec3.ZERO;
    private Vec3 serverVelocity = Vec3.ZERO;
    @Nullable
    private Vec3 lastTickPos;
    private int impactCooldown;
    private int lastImpactTick = -100;

    private double wheelSpin;
    private double wheelSpinO;

    @Nullable
    private List<WorldBox> modelBoxes;
    @Nullable
    private List<Obb> modelObbs;
    private long boxKey = Long.MIN_VALUE;
    @Nullable
    private List<Obb> renderObbs;
    private long renderKey = Long.MIN_VALUE;
    @Nullable
    private Vec3 carryFrom;
    private float carryFromYaw;
    private Vec3 carryDelta = Vec3.ZERO;
    private float carryYaw;

    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    private boolean destroyed;
    @Nullable
    private UUID coupledTractorUuid;
    private int repairCooldown;
    @Nullable
    private DamageSource lastExplosionSource;
    private int lastExplosionTick = -1;

    @Nullable
    private PatriotClearance.Blocker supportsBlock;
    @Nullable
    private PatriotClearance.Blocker liftBlock;
    private int clearanceTick = -100;
    private int orphanTicks;
    @Nullable
    private Vec3 partnerPos;
    @Nullable
    private StageSevenHitch legacyHitch;

    protected PatriotVehicleEntity(EntityType<? extends PatriotVehicleEntity> type, Level level, PatriotKind kind) {
        super(type, level);
        this.blocksBuilding = true;
        this.sequence = new PatriotSequence(kind);
        PatriotPart[] made = new PatriotPart[PatriotGeometry.PART_Z.length];
        for (int i = 0; i < made.length; i++) {
            made[i] = new PatriotPart(this, i);
        }
        this.parts = made;
        this.setId(ENTITY_COUNTER.getAndAdd(made.length + 1) + 1);
        this.gate = this::checkOperation;
    }

    public abstract PatriotKind kind();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, this.kind().defaultHealth());
        builder.define(DATA_ROLL, 0.0f);
        builder.define(DATA_ART_YAW, 0.0f);
        builder.define(DATA_ART_PITCH, 0.0f);
        builder.define(DATA_ART_ROLL, 0.0f);
        builder.define(DATA_MASK, 0);
        builder.define(DATA_OP, -1);
        builder.define(DATA_OP_TICKS, 0);
        builder.define(DATA_CLIPS, 0);
        builder.define(DATA_START_SUPPORTS, 0);
        builder.define(DATA_START_LIFT, 0);
        builder.define(DATA_START_SERVICE, 0);
        builder.define(DATA_TRAVERSE, 0.0f);
        builder.define(DATA_TRAVERSE_TARGET, 0.0f);
        builder.define(DATA_HITCHED, false);
        builder.define(DATA_TRACTOR_ID, -1);
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
            PatriotPart part = this.parts[i];
            Vec3 p = PatriotGeometry.partPosition(i, this.getX(), this.getY(), this.getZ(), this.getYRot(),
                    this.getXRot(), this.roll);
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
        this.artYawO = this.artYaw;
        this.artPitchO = this.artPitch;
        this.artRollO = this.artRoll;
        this.traverseO = this.traverseNow;
        this.wheelSpinO = this.wheelSpin;
        if (this.impactCooldown > 0) {
            this.impactCooldown--;
        }

        super.tick();
        this.tickLerp();
        if (this.level().isClientSide) {
            this.tickCycleClient();
            this.tickClient();
        } else {
            this.tickSequence();
            this.tickJolt();
            this.tickServer();
        }
        this.applyHitchedPose();

        this.updateParts();
        this.updateCarry();
        this.boxKey = Long.MIN_VALUE;
        VehicleRegistry.add(this.level(), this);
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

    private void applyHitchedPose() {
        if (!this.isHitched()) {
            return;
        }
        M983TractorEntity tractor = this.coupledTractor();
        TractorJolt tractorJolt = tractor == null ? null : tractor.joltView();
        if (tractorJolt != null) {
            tractorJolt.applyStagedTrailerPose(this);
        }
    }

    private void tickOrphan() {
        if (!this.isHitched()) {
            this.orphanTicks = 0;
            return;
        }
        M983TractorEntity tractor = this.coupledTractor();
        boolean held = tractor != null && !tractor.isRemoved() && tractor.isCoupledTo(this);
        if (held) {
            this.partnerPos = tractor.position();
            this.orphanTicks = 0;
            return;
        }
        boolean chunkTicks = this.partnerPos != null && this.level() instanceof ServerLevel serverLevel
                && serverLevel.isPositionEntityTicking(BlockPos.containing(this.partnerPos));
        this.orphanTicks = M983Coupling.nextAbsent(this.orphanTicks, false, this.partnerPos != null, chunkTicks);
        if (M983Coupling.partner(false, this.partnerPos != null, chunkTicks, this.orphanTicks, false)
                == M983Coupling.Partner.GONE) {
            this.tractorDestroyed();
        }
    }

    public void tractorDestroyed() {
        if (!this.level().isClientSide && this.isHitched()) {
            this.setHitched(false, -1, null);
            this.placeOnSupports();
        }
    }

    public boolean inPanelRange(Player player) {
        if (player.distanceToSqr(this) <= PANEL_RANGE * PANEL_RANGE) {
            return true;
        }
        M983TractorEntity tractor = this.coupledTractor();
        return this.isHitched() && tractor != null && player.getVehicle() == tractor;
    }

    private void tickJolt() {
        if (this.isHitched()) {
            if (this.jolt != null) {
                this.jolt.close();
            }
            return;
        }
        if (this.jolt == null) {
            this.jolt = new TrailerJolt(this);
        }
        this.jolt.tick();
    }

    public boolean isHitched() {
        return this.entityData.get(DATA_HITCHED);
    }

    public void setHitched(boolean hitched, int tractorId, @Nullable UUID tractorUuid) {
        this.entityData.set(DATA_HITCHED, hitched);
        this.entityData.set(DATA_TRACTOR_ID, hitched ? tractorId : -1);
        this.coupledTractorUuid = hitched ? tractorUuid : null;
        this.orphanTicks = 0;
        M983TractorEntity tractor = hitched ? this.coupledTractor() : null;
        if (!hitched || tractor != null) {
            this.partnerPos = tractor == null ? null : tractor.position();
        }
    }

    @Nullable
    public M983TractorEntity coupledTractor() {
        Entity entity = this.level().getEntity(this.entityData.get(DATA_TRACTOR_ID));
        if ((!(entity instanceof M983TractorEntity) || entity.isRemoved()
                || this.coupledTractorUuid != null && !this.coupledTractorUuid.equals(entity.getUUID()))
                && this.coupledTractorUuid != null
                && this.level() instanceof ServerLevel serverLevel) {
            entity = serverLevel.getEntity(this.coupledTractorUuid);
        }
        if (entity instanceof M983TractorEntity tractor && !tractor.isRemoved()
                && (this.coupledTractorUuid == null || this.coupledTractorUuid.equals(tractor.getUUID()))) {
            this.entityData.set(DATA_TRACTOR_ID, tractor.getId());
            return tractor;
        }
        return null;
    }

    public void dropJoltBody() {
        if (this.jolt != null) {
            this.jolt.close();
        }
    }

    @Override
    public boolean isControlledByLocalInstance() {
        if (this.isHitched()) {
            M983TractorEntity tractor = this.coupledTractor();
            if (tractor != null) {
                return tractor.isControlledByLocalInstance();
            }
        }
        return super.isControlledByLocalInstance();
    }

    public boolean supportsOutNow() {
        return this.seqView().supports();
    }

    public boolean operationBusy() {
        return this.seqView().busy();
    }

    public boolean canBeCoupledNow() {
        return M983Coupling.trailerAvailable(this.destroyed, this.isRemoved(), this.isHitched(), this.isOverturned(),
                this.getHealth(), this.supportsOutNow(), this.operationBusy(), !this.getPassengers().isEmpty());
    }

    public boolean canBeTowedNow() {
        return !this.destroyed && !this.isRemoved() && !this.isOverturned() && this.seqView().canDrive();
    }

    public PatriotRefusal whyNotTowed() {
        return this.seqView().whyNotDrive();
    }

    public void applyJoltPose(double x, double y, double z, float yaw, float pitch, float roll, float artYaw,
            float artPitch, float artRoll, Vec3 velocity, boolean grounded) {
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setRoll(roll);
        this.setArticulation(artYaw, artPitch, artRoll);
        this.setDeltaMovement(velocity);
        this.setOnGround(grounded);
        this.updateParts();
        this.updateCarry();
        this.boxKey = Long.MIN_VALUE;
    }

    void reportJoltImpact(byte kind, double speed) {
        this.reportImpact(kind, speed);
    }

    public void acceptPose(float trailerRoll, float yaw, float pitch, float rollValue) {
        double yawLimit = VehicleSpecs.train(this.kind().specName()).coupling.yawLimitDegrees + 5.0;
        double pitchLimit = VehicleSpecs.train(this.kind().specName()).coupling.pitchLimitDegrees + 5.0;
        double rollLimit = VehicleSpecs.train(this.kind().specName()).coupling.rollLimitDegrees + 5.0;
        if (!Float.isFinite(trailerRoll) || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(rollValue)
                || Math.abs(trailerRoll) >= 180.0f || Math.abs(yaw) > yawLimit || Math.abs(pitch) > pitchLimit
                || Math.abs(rollValue) > rollLimit) {
            return;
        }
        this.setRoll(trailerRoll);
        this.setArticulation(yaw, pitch, rollValue);
    }

    public boolean isOverturned() {
        return Math.abs(this.getXRot()) > 60.0f || Math.abs(this.roll) > 60.0f;
    }

    public boolean usesJolt() {
        return this.jolt != null && this.jolt.active();
    }

    @Nullable
    public TrailerJolt joltView() {
        return this.jolt;
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        super.absMoveTo(x, y, z, yRot, xRot);
    }

    @Override
    public float maxUpStep() {
        return 0.0f;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.PLAYER || type == MoverType.SELF) {
            this.setPos(this.getX() + movement.x, this.getY() + movement.y, this.getZ() + movement.z);
            return;
        }
        super.move(type, movement);
    }

    private void reportImpact(byte kind, double speed) {
        if (this.impactCooldown > 0) {
            return;
        }
        this.impactCooldown = IMPACT_COOLDOWN_TICKS;
        if (this.level().isClientSide) {
            PacketDistributor.sendToServer(new com.synarsis.airtacticalarsenal.network.PatriotImpactPacket(this.getId(),
                    kind, (float) speed));
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

    private void tickServer() {
        this.tickMigration();
        this.tickOrphan();
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

        Vec3 vel = this.serverVelocity;
        if (vel.lengthSqr() >= 0.09) {
            this.crushScan(vel, ShahedConfig.isTelCrushDamageEnabled());
        }
        if (this.tickCount % 4 == 0 && this.level() instanceof ServerLevel serverLevel
                && ShahedConfig.doesTelBreakSoftBlocks() && Math.hypot(vel.x, vel.z) > 0.09) {
            TelWorld.smashSoftBlocks(serverLevel,
                    PatriotItem.footprint(this.getX(), this.getY(), this.getZ(), this.getYRot()), vel, this);
        }
    }

    private boolean isCrushable(Entity e) {
        if (e == this || e instanceof PartEntity<?> || e.isSpectator() || !e.isAlive() || e.getRootVehicle() == this) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof Boat || e instanceof AbstractMinecart
                || e instanceof PatriotVehicleEntity;
    }

    private void crushScan(Vec3 vel, boolean damage) {
        AABB reach = this.getBoundingBoxForCulling().move(vel);
        List<Entity> targets = this.level().getEntities(this, reach, this::isCrushable);
        if (targets.isEmpty()) {
            return;
        }
        Entity driver = this.getControllingPassenger();
        List<WorldBox> boxes = this.getSolidBoxes();
        for (Entity target : targets) {
            Vec3 rel = vel.subtract(target.getDeltaMovement());
            double speed = rel.length();
            if (speed <= 0.3) {
                continue;
            }
            boolean hit = false;
            for (WorldBox box : boxes) {
                if (box.maxY() < this.getY() + 0.4) {
                    continue;
                }
                if (ObbAabbSat.mtv(box.toObb().translated(vel), target.getBoundingBox()) != null) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            double over = speed - 0.3;
            if (damage) {
                double theirSize = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
                double f1 = Mth.clamp(40.0 / Math.max(theirSize, 0.01), 0.25, 4.0);
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
        }
    }

    private void tickClient() {
        this.observedVelocity = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
        if (!(this.isHitched() && this.isControlledByLocalInstance())) {
            this.roll = Mth.lerp(0.35f, this.roll, this.entityData.get(DATA_ROLL));
            this.artYaw = Mth.lerp(0.35f, this.artYaw, this.entityData.get(DATA_ART_YAW));
            this.artPitch = Mth.lerp(0.35f, this.artPitch, this.entityData.get(DATA_ART_PITCH));
            this.artRoll = Mth.lerp(0.35f, this.artRoll, this.entityData.get(DATA_ART_ROLL));
        }
        this.traverseNow = Mth.lerp(0.5f, this.traverseNow, this.entityData.get(DATA_TRAVERSE));
        double yawRad = Math.toRadians(this.getYRot());
        double forward = this.observedVelocity.z * Math.cos(yawRad) - this.observedVelocity.x * Math.sin(yawRad);
        this.wheelSpin -= Math.toDegrees(forward / PatriotGeometry.TRAILER_WHEEL_RADIUS);
        this.spawnDamageParticles();
    }

    public double getWheelSpin(float partialTick) {
        return Mth.lerp(partialTick, this.wheelSpinO, this.wheelSpin);
    }

    private void spawnDamageParticles() {
        if (!this.isBurning()) {
            return;
        }
        Vec3 deck = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(),
                this.roll, new Vec3(0.0, 3.6, 12.0));
        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                deck.x + (this.random.nextDouble() - 0.5), deck.y, deck.z + (this.random.nextDouble() - 0.5),
                0.0, 0.08, 0.0);
    }

    public boolean isBurning() {
        return this.getHealth() <= this.getMaxHealth() * BURNING_FRACTION;
    }

    public float getRollLerp(float partialTick) {
        return Mth.lerp(partialTick, this.rollO, this.roll);
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

    public float getRoll() {
        return this.roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
        if (!this.level().isClientSide && Math.abs(this.entityData.get(DATA_ROLL) - roll) > 0.05f) {
            this.entityData.set(DATA_ROLL, roll);
        }
    }

    public float getArticulationYaw() {
        return this.artYaw;
    }

    public float getArticulationPitch() {
        return this.artPitch;
    }

    public float getArticulationRoll() {
        return this.artRoll;
    }

    public void setArticulation(float yaw, float pitch, float rollValue) {
        this.artYaw = yaw;
        this.artPitch = pitch;
        this.artRoll = rollValue;
        if (!this.level().isClientSide) {
            if (Math.abs(this.entityData.get(DATA_ART_YAW) - yaw) > 0.05f) {
                this.entityData.set(DATA_ART_YAW, yaw);
            }
            if (Math.abs(this.entityData.get(DATA_ART_PITCH) - pitch) > 0.05f) {
                this.entityData.set(DATA_ART_PITCH, pitch);
            }
            if (Math.abs(this.entityData.get(DATA_ART_ROLL) - rollValue) > 0.05f) {
                this.entityData.set(DATA_ART_ROLL, rollValue);
            }
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (CreateCompat.isCreateFakePlayer(player)) {
            return InteractionResult.FAIL;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean client = this.level().isClientSide;

        if (this.isRepairItem(held) && this.getHealth() < this.getMaxHealth()) {
            if (!client && this.repairCooldown <= 0) {
                this.repair(player, held);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (player.isShiftKeyDown() && held.isEmpty() && this.canPickUp(player)) {
            if (!client) {
                this.pickUp(player);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (this.isHitched() && this.coupledTractor() != null && player.getVehicle() == this.coupledTractor()) {
            return InteractionResult.PASS;
        }
        if (client) {
            PatriotInput.openPanel.accept(this);
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
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("message.ata.patriot.repaired",
                Math.round(this.getHealth()), Math.round(this.getMaxHealth())), true);
    }

    private boolean canPickUp(Player player) {
        return !this.sequence.busy()
                && this.getPassengers().isEmpty()
                && (this.ownerUuid == null || this.ownerUuid.equals(player.getUUID()) || player.getAbilities().instabuild);
    }

    private Item itemOfKind() {
        return this.kind().isLauncher() ? ModItems.PATRIOT_LAUNCHER.get() : ModItems.PATRIOT_RADAR.get();
    }

    private void pickUp(Player player) {
        ItemStack stack = new ItemStack(this.itemOfKind());
        PatriotItem.writeHealth(stack, this.getHealth());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        this.discard();
    }

    public void placeOnSupports() {
        this.sequence.parkOnSupports();
        this.publishSequence(true);
        this.boxKey = Long.MIN_VALUE;
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
        return new ItemStack(this.itemOfKind());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return this.hurtInZone(source, amount, null);
    }

    public boolean hurtInZone(DamageSource source, float amount, @Nullable HitTag zone) {
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
        if (zone instanceof PatriotZone patriotZone) {
            damage *= patriotZone.damageMultiplier();
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

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_DESTROYED || id == EVENT_DESTROYED_DETONATED) {
            PatriotInput.destroyedFx.accept(this, id == EVENT_DESTROYED_DETONATED);
            return;
        }
        super.handleEntityEvent(id);
    }

    private void destroy(DamageSource cause) {
        if (this.destroyed || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.destroyed = true;
        this.ejectPassengers();

        Entity blame = cause.getEntity();
        if (blame == null && this.ownerUuid != null) {
            blame = serverLevel.getPlayerByUUID(this.ownerUuid);
        }
        DamageSource blast = this.damageSources().explosion(this, blame);
        Level.ExplosionInteraction mode = ShahedConfig.doExplosionsBreakBlocks()
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        Vec3 centre = TelGeometry.localToWorld(this.getX(), this.getY(), this.getZ(), this.getYRot(),
                this.getXRot(), this.roll, new Vec3(0.0, 2.5, -0.5));
        serverLevel.explode(this, blast, null, centre.x, centre.y, centre.z, 5.0f, false, mode);
        serverLevel.broadcastEntityEvent(this, EVENT_DESTROYED);
        this.spawnAtLocation(new ItemStack(Items.IRON_INGOT, 6 + this.random.nextInt(7)));
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
            return (float) (this.kind().isLauncher() ? ShahedConfig.getPatriotLauncherMaxHealth()
                    : ShahedConfig.getPatriotRadarMaxHealth());
        } catch (IllegalStateException e) {
            return this.kind().defaultHealth();
        }
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setOwnerUuid(@Nullable UUID owner) {
        this.ownerUuid = owner;
    }

    boolean softBlocksGiveWay() {
        try {
            return ShahedConfig.doesTelBreakSoftBlocks();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("LegitSpawn", this.isLegitSpawn);
        tag.putFloat("Health", this.getHealth());
        tag.putFloat("Roll", this.roll);
        tag.putFloat("ArticulationYaw", this.artYaw);
        tag.putFloat("ArticulationPitch", this.artPitch);
        tag.putFloat("ArticulationRoll", this.artRoll);
        PatriotSequence.Snapshot snap = this.sequence.snapshot();
        CompoundTag cycle = new CompoundTag();
        cycle.putBoolean("Supports", snap.supports());
        cycle.putBoolean("Lift", snap.lift());
        cycle.putBoolean("Service", snap.service());
        cycle.putDouble("Traverse", snap.traverse());
        cycle.putDouble("TraverseTarget", snap.traverseTarget());
        cycle.putInt("Op", snap.op());
        cycle.putInt("Ticks", snap.ticks());
        cycle.putIntArray("Queue", snap.queue());
        tag.put("Cycle", cycle);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.putBoolean("Hitched", this.isHitched());
        if (this.coupledTractorUuid != null) {
            tag.putUUID("CoupledTractor", this.coupledTractorUuid);
            if (this.partnerPos != null) {
                CompoundTag partner = new CompoundTag();
                partner.putDouble("X", this.partnerPos.x);
                partner.putDouble("Y", this.partnerPos.y);
                partner.putDouble("Z", this.partnerPos.z);
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
        this.artYaw = tag.getFloat("ArticulationYaw");
        this.artPitch = tag.getFloat("ArticulationPitch");
        this.artRoll = tag.getFloat("ArticulationRoll");
        this.artYawO = this.artYaw;
        this.artPitchO = this.artPitch;
        this.artRollO = this.artRoll;
        this.entityData.set(DATA_ART_YAW, this.artYaw);
        this.entityData.set(DATA_ART_PITCH, this.artPitch);
        this.entityData.set(DATA_ART_ROLL, this.artRoll);
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
        }
        this.entityData.set(DATA_HITCHED, tag.getBoolean("Hitched"));
        this.coupledTractorUuid = tag.hasUUID("CoupledTractor") ? tag.getUUID("CoupledTractor") : null;
        CompoundTag partner = tag.getCompound("PartnerPos");
        this.partnerPos = partner.contains("X", Tag.TAG_DOUBLE) && partner.contains("Y", Tag.TAG_DOUBLE)
                && partner.contains("Z", Tag.TAG_DOUBLE)
                        ? new Vec3(partner.getDouble("X"), partner.getDouble("Y"), partner.getDouble("Z")) : null;
        this.orphanTicks = 0;
        this.legacyHitch = null;
        if (tag.contains("Cycle", Tag.TAG_COMPOUND)) {
            CompoundTag cycle = tag.getCompound("Cycle");
            boolean stageSeven = StageSevenHitch.isStageSeven(tag);
            this.sequence.restore(new PatriotSequence.Snapshot(cycle.getBoolean("Supports"), cycle.getBoolean("Lift"),
                    cycle.getBoolean("Service"), cycle.getDouble("Traverse"), cycle.getDouble("TraverseTarget"),
                    cycle.contains("Op") ? cycle.getInt("Op") : -1, cycle.getInt("Ticks"), cycle.getIntArray("Queue")),
                    stageSeven);
            if (stageSeven) {
                this.legacyHitch = StageSevenHitch.read(this, tag);
                this.entityData.set(DATA_HITCHED, false);
                this.coupledTractorUuid = null;
            }
        } else {
            this.sequence.reset();
        }
        this.publishSequence(true);
    }

    @Nullable
    public StageSevenHitch pendingMigration() {
        return this.legacyHitch;
    }

    private void tickMigration() {
        StageSevenHitch hitch = this.legacyHitch;
        if (hitch == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.legacyHitch = null;
        StageSevenHitch.migrate(serverLevel, this, hitch);
    }

    public int currentMask() {
        if (!this.level().isClientSide) {
            return this.sequence.mask();
        }
        return this.entityData.get(DATA_MASK);
    }

    public int currentOp() {
        if (!this.level().isClientSide) {
            PatriotSequence.Op op = this.sequence.current();
            return op == null ? -1 : op.ordinal();
        }
        return this.entityData.get(DATA_OP);
    }

    public PatriotSequence seqView() {
        if (!this.level().isClientSide) {
            return this.sequence;
        }
        return PatriotSequence.view(this.kind(), this.entityData.get(DATA_MASK), this.entityData.get(DATA_OP),
                this.entityData.get(DATA_TRAVERSE), this.entityData.get(DATA_TRAVERSE_TARGET));
    }

    public int getVisualTicks() {
        if (!this.level().isClientSide) {
            return this.sequence.ticks();
        }
        int ticks = this.syncTicks + (this.tickCount - this.syncAt);
        PatriotSequence.Op op = PatriotSequence.Op.byOrdinal(this.entityData.get(DATA_OP));
        int duration = op == null ? 0 : this.sequence.durationOf(op);
        return duration > 0 ? Math.min(ticks, duration) : ticks;
    }

    public double operationFraction(float partialTick) {
        PatriotSequence.Op op = PatriotSequence.Op.byOrdinal(this.currentOp());
        if (op == null) {
            return 0.0;
        }
        int duration = this.sequence.durationOf(op);
        if (op == PatriotSequence.Op.CENTER || duration <= 0) {
            return 0.0;
        }
        return Mth.clamp((this.getVisualTicks() + partialTick) / duration, 0.0, 1.0);
    }

    public TelPose.Clip getGroupClip(int group) {
        if (!this.level().isClientSide) {
            return this.sequence.groupClip(group);
        }
        return TelPose.Clip.byOrdinal((this.entityData.get(DATA_CLIPS) >> (group * 2)) & 3);
    }

    public double getGroupAge(int group, float partialTick) {
        if (!this.level().isClientSide) {
            return this.sequence.groupAge(group) + partialTick;
        }
        int start = this.entityData.get(switch (group) {
            case PatriotBones.SUPPORTS -> DATA_START_SUPPORTS;
            case PatriotBones.LIFT -> DATA_START_LIFT;
            default -> DATA_START_SERVICE;
        });
        return Math.max(0.0, (int) this.level().getGameTime() - start + partialTick);
    }

    private PatriotPose groupsPose(float partialTick) {
        PatriotKind kind = this.kind();
        TelHitboxPose.Part supports = PatriotPose.partOf(kind, PatriotBones.SUPPORTS,
                this.getGroupClip(PatriotBones.SUPPORTS), this.getGroupAge(PatriotBones.SUPPORTS, partialTick));
        TelHitboxPose.Part lift = PatriotPose.partOf(kind, PatriotBones.LIFT, this.getGroupClip(PatriotBones.LIFT),
                this.getGroupAge(PatriotBones.LIFT, partialTick));
        TelHitboxPose.Part service = kind.isLauncher() ? TelHitboxPose.Part.REST
                : PatriotPose.partOf(kind, PatriotBones.SERVICE, this.getGroupClip(PatriotBones.SERVICE),
                        this.getGroupAge(PatriotBones.SERVICE, partialTick));
        return PatriotPose.REST.withParts(supports, lift, service);
    }

    private double traverseValue(float partialTick) {
        if (!this.level().isClientSide) {
            return this.sequence.traverse();
        }
        return Mth.lerp(partialTick, this.traverseO, this.traverseNow);
    }

    public PatriotPose hitboxPose() {
        return this.groupsPose(0.0f).withTraverse(this.traverseValue(0.0f));
    }

    public PatriotPose poseFor(float partialTick) {
        double spin = this.getWheelSpin(partialTick);
        return this.groupsPose(partialTick).withTraverse(this.traverseValue(partialTick)).withWheels(spin, spin);
    }

    private void refreshBoxes() {
        long key = ((long) this.tickCount << 1) | (this.level().isClientSide ? 1L : 0L);
        if (this.modelBoxes == null || this.boxKey != key) {
            this.modelBoxes = PatriotHitbox.of(this.kind()).compute(this.hitboxPose(), this.getX(), this.getY(),
                    this.getZ(), this.getYRot(), this.getXRot(), this.roll);
            this.modelObbs = TelHitboxQuery.toObbs(this.modelBoxes);
            this.boxKey = key;
        }
    }

    @Override
    public List<Obb> getObbs() {
        this.refreshBoxes();
        return this.modelObbs;
    }

    @Override
    public List<WorldBox> getSolidBoxes() {
        this.refreshBoxes();
        return this.modelBoxes;
    }

    @Override
    public List<Obb> getRenderObbs(float partialTick) {
        long key = ((long) this.tickCount << 12) | (long) Math.round(partialTick * 1000.0f);
        if (this.renderObbs == null || this.renderKey != key) {
            double x = Mth.lerp(partialTick, this.xo, this.getX());
            double y = Mth.lerp(partialTick, this.yo, this.getY());
            double z = Mth.lerp(partialTick, this.zo, this.getZ());
            float yaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
            float pitch = Mth.lerp(partialTick, this.xRotO, this.getXRot());
            float rollNow = Mth.lerp(partialTick, this.rollO, this.roll);
            this.renderObbs = TelHitboxQuery.toObbs(PatriotHitbox.of(this.kind()).compute(
                    this.level().isClientSide ? this.poseFor(partialTick) : this.hitboxPose(), x, y, z, yaw, pitch,
                    rollNow));
            this.renderKey = key;
        }
        return this.renderObbs;
    }

    @Override
    public List<Obb> getPickObbs() {
        return this.level().isClientSide
                ? this.getRenderObbs((float) TelInput.partialTick.getAsDouble())
                : this.getObbs();
    }

    @Override
    public Vec3 getCarryDelta() {
        return this.carryDelta;
    }

    @Override
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
        DamageSource source = new DamageSource(
                serverLevel.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(DamageTypes.MOB_PROJECTILE),
                null, shooter, point);
        this.hurtInZone(source, damage, tag);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        VehicleRegistry.add(this.level(), this);
    }

    @Override
    public void onRemovedFromLevel() {
        this.dropJolt();
        super.onRemovedFromLevel();
        VehicleRegistry.remove(this.level(), this);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && reason.shouldDestroy() && this.isHitched()) {
            M983TractorEntity tractor = this.coupledTractor();
            if (tractor != null && tractor.isCoupledTo(this)) {
                tractor.trailerDestroyed();
            }
        }
        this.dropJolt();
        VehicleRegistry.remove(this.level(), this);
        super.remove(reason);
    }

    private void dropJolt() {
        if (this.jolt != null) {
            this.jolt.close();
            this.jolt = null;
        }
    }

    public boolean isStanding() {
        double speed = this.level().isClientSide
                ? Math.hypot(this.observedVelocity.x, this.observedVelocity.z)
                : Math.hypot(this.serverVelocity.x, this.serverVelocity.z);
        return speed < 0.03 && !this.isInWater();
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
            BlockState state = level.getBlockState(probe);
            if (state.is(TelWorld.BREAKABLE)) {
                return false;
            }
            return !state.getCollisionShape(level, probe).isEmpty();
        };
    }

    private void refreshClearance() {
        if (this.tickCount - this.clearanceTick < CLEARANCE_REFRESH_TICKS && this.tickCount >= this.clearanceTick) {
            return;
        }
        this.clearanceTick = this.tickCount;
        PatriotHitbox model = PatriotHitbox.of(this.kind());
        PatriotPose pose = this.hitboxPose();
        TelClearance.Pose at = this.clearancePose();
        TelClearance.Solid solid = this.worldSolid();
        this.supportsBlock = PatriotClearance.supportBlocker(model, pose, at, solid);
        this.liftBlock = PatriotClearance.liftBlocker(model, pose, at, solid);
    }

    @Nullable
    public PatriotClearance.Blocker supportsBlocker() {
        this.refreshClearance();
        return this.supportsBlock;
    }

    @Nullable
    public PatriotClearance.Blocker liftBlocker() {
        this.refreshClearance();
        return this.liftBlock;
    }

    private PatriotRefusal.Denial placeDenial(List<PatriotSequence.Op> ops, boolean fresh) {
        if (ops.contains(PatriotSequence.Op.SUPPORTS_OUT)) {
            if (!PatriotClearance.tiltOk(this.getXRot(), this.roll)) {
                return PatriotRefusal.Denial.of(PatriotRefusal.TILT);
            }
        }
        if (ops.contains(PatriotSequence.Op.SUPPORTS_OUT)) {
            PatriotClearance.Blocker b = fresh ? this.freshSupports() : this.supportsBlocker();
            if (b != null) {
                return b.denial();
            }
        }
        if (ops.contains(PatriotSequence.Op.RAISE) || ops.contains(PatriotSequence.Op.ANTENNA_UP)) {
            PatriotClearance.Blocker b = fresh ? this.freshLift() : this.liftBlocker();
            if (b != null) {
                return b.denial();
            }
        }
        return PatriotRefusal.Denial.NONE;
    }

    @Nullable
    private PatriotClearance.Blocker freshSupports() {
        return PatriotClearance.supportBlocker(PatriotHitbox.of(this.kind()), this.hitboxPose(), this.clearancePose(),
                this.worldSolid());
    }

    @Nullable
    private PatriotClearance.Blocker freshLift() {
        return PatriotClearance.liftBlocker(PatriotHitbox.of(this.kind()), this.hitboxPose(), this.clearancePose(),
                this.worldSolid());
    }

    private PatriotRefusal.Denial checkOperation(PatriotSequence.Op op) {
        return this.placeDenial(List.of(op), true);
    }

    public PatriotRefusal.Denial denial(Command command) {
        PatriotSequence view = this.seqView();
        boolean standing = this.isStanding();
        PatriotSequence.Plan plan = view.plan(command, standing);
        if (!plan.refusal().ok()) {
            return PatriotRefusal.Denial.of(plan.refusal());
        }
        return this.placeDenial(plan.ops(), false);
    }

    public PatriotRefusal whyNotDrive() {
        return PatriotRefusal.NEED_COUPLED;
    }

    public void handleCommand(Player player, byte command) {
        if (this.level().isClientSide || this.destroyed) {
            return;
        }
        if (!this.inPanelRange(player)) {
            return;
        }
        Command c = Command.byOrdinal(command);
        if (c == null) {
            return;
        }
        if (!this.isHitched() && (c == Command.STOW || c == Command.SUPPORTS_IN)) {
            player.displayClientMessage(Component.translatable(PatriotRefusal.NEED_COUPLED.messageKey()), true);
            return;
        }
        PatriotRefusal.Denial denial = this.denial(c);
        if (denial.ok()) {
            this.refreshClearance();
            denial = this.placeDenialFresh(c);
        }
        if (denial.ok()) {
            denial = PatriotRefusal.Denial.of(this.sequence.command(c, this.isStanding()));
        }
        if (!denial.ok()) {
            player.displayClientMessage(Component.translatable(denial.reason().messageKey(), denial.args()), true);
        }
        this.publishSequence(true);
    }

    private PatriotRefusal.Denial placeDenialFresh(Command c) {
        PatriotSequence.Plan plan = this.sequence.plan(c, this.isStanding());
        return plan.refusal().ok() ? this.placeDenial(plan.ops(), true) : PatriotRefusal.Denial.of(plan.refusal());
    }

    private void tickSequence() {
        this.sequence.tick(this.gate);
        PatriotSequence.Op started = this.sequence.started();
        if (started != null) {
            this.playOperationSound(started);
        }
        PatriotRefusal.Denial dropped = this.sequence.takeDropped();
        if (!dropped.ok()) {
            this.crewMessage(Component.translatable(dropped.reason().messageKey(), dropped.args()));
        }
        this.publishSequence(this.sequence.changed());
    }

    private void playOperationSound(PatriotSequence.Op op) {
        SoundEvent sound = switch (op) {
            case SUPPORTS_OUT, SUPPORTS_IN -> ModSounds.TEL_JACKS.get();
            case RAISE, LOWER, ANTENNA_UP, ANTENNA_DOWN -> ModSounds.TEL_HYDRAULICS.get();
            default -> ModSounds.TEL_COVERS.get();
        };
        this.level().playSound(null, this.getX(), this.getY() + 1.5, this.getZ(), sound, SoundSource.NEUTRAL, 1.0f,
                1.0f);
    }

    private void crewMessage(Component message) {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private void publishSequence(boolean changed) {
        PatriotSequence s = this.sequence;
        this.entityData.set(DATA_MASK, s.mask());
        PatriotSequence.Op op = s.current();
        this.entityData.set(DATA_OP, op == null ? -1 : op.ordinal());
        int packed = 0;
        long now = this.level().getGameTime();
        for (int group = 0; group < PatriotBones.COUNT; group++) {
            TelPose.Clip clip = s.groupClip(group);
            packed |= clip.ordinal() << (group * 2);
            int start = clip.moving() ? (int) now - s.groupAge(group) : 0;
            this.entityData.set(switch (group) {
                case PatriotBones.SUPPORTS -> DATA_START_SUPPORTS;
                case PatriotBones.LIFT -> DATA_START_LIFT;
                default -> DATA_START_SERVICE;
            }, start);
        }
        this.entityData.set(DATA_CLIPS, packed);
        if (Math.abs(this.entityData.get(DATA_TRAVERSE) - (float) s.traverse()) > 0.01f) {
            this.entityData.set(DATA_TRAVERSE, (float) s.traverse());
        }
        if (this.entityData.get(DATA_TRAVERSE_TARGET) != (float) s.traverseTarget()) {
            this.entityData.set(DATA_TRAVERSE_TARGET, (float) s.traverseTarget());
        }
        if (changed || s.ticks() % 5 == 0) {
            this.entityData.set(DATA_OP_TICKS, s.ticks());
        }
    }

    private void tickCycleClient() {
        int op = this.entityData.get(DATA_OP);
        int ticks = this.entityData.get(DATA_OP_TICKS);
        if (op != this.syncOp || ticks != this.syncTicks) {
            this.syncOp = op;
            this.syncTicks = ticks;
            this.syncAt = this.tickCount;
        }
    }

    public boolean debugSkip() {
        boolean moved = this.sequence.skip(this.gate);
        this.publishSequence(true);
        return moved;
    }

    public String debugState() {
        PatriotSequence s = this.sequence;
        return String.format(java.util.Locale.ROOT,
                "%s  op %s %d ticks (%d queued)  supports %s  lift %s  service %s  traverse %.1f to %.1f  "
                        + "articulation [%.1f %.1f %.1f]  roll %.1f  hitched %s  physics %s",
                this.kind().id(), s.current() == null ? "-" : s.current(), s.ticks(), s.queued(),
                s.supports() ? "out" : "in", s.lift() ? "up" : "down", s.service() ? "open" : "shut",
                s.traverse(), s.traverseTarget(), this.artYaw, this.artPitch, this.artRoll, this.roll,
                this.isHitched(), this.jolt == null ? "none" : (this.jolt.failed() ? "failed" : "static"));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(
                this.getX() - PatriotGeometry.CULL_HALF_XZ, this.getY(), this.getZ() - PatriotGeometry.CULL_HALF_XZ,
                this.getX() + PatriotGeometry.CULL_HALF_XZ, this.getY() + PatriotGeometry.CULL_HEIGHT,
                this.getZ() + PatriotGeometry.CULL_HALF_XZ);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
