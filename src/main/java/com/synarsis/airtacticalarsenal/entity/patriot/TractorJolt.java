package com.synarsis.airtacticalarsenal.entity.patriot;

import com.github.stephengold.joltjni.Jolt;
import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatch;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatchBuilder;
import com.synarsis.airtacticalarsenal.vehicle.physics.Hitch;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.LevelColumns;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.TrainSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleGround;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import com.synarsis.airtacticalarsenal.entity.tel.RollbackLog;
import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class TractorJolt implements PhysicsWorlds.Participant {

    public enum Role {
        NONE,
        SIMULATE,
        FOLLOW,
        HOLD
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<TractorJolt> LOCAL_SIMULATORS = new ArrayList<>();
    private static final double PROXY_RANGE = 120.0;
    private static final double TICK_SECONDS = 0.05;
    private static final int RIGHTING_TICKS = 20;
    private static final double ARTICULATION_CHEAT_MARGIN = 5.0;
    private static final double[] TRACTOR_ANCHOR = {0.0, 1.3125, 0.0};
    private static final double[] TRAILER_ANCHOR = {0.0, 1.3125, 5.875};

    private final M983TractorEntity tractor;
    private final Level level;
    private PhysicsWorld world;
    private JoltVehicle vehicle;
    private VehicleGround ground;
    @Nullable
    private Hitch hitch;
    @Nullable
    private PatriotVehicleEntity trainTrailer;
    @Nullable
    private PatriotVehicleEntity hitchTrailer;
    private int hitchRevision = -1;
    private Role role = Role.NONE;
    private int specRevision = -1;
    private boolean joined;
    private boolean broken;

    private JoltVehicle.Controls controls = JoltVehicle.Controls.IDLE;
    private boolean driven;

    private double writtenX;
    private double writtenY;
    private double writtenZ;
    private double writtenYaw;
    private boolean hasWritten;

    private double acceptedX;
    private double acceptedY;
    private double acceptedZ;
    private int acceptedTick = Integer.MIN_VALUE;
    private int rollbacks;
    private final RollbackLog rollbackLog = new RollbackLog();

    private double speedMs;
    private double rpm;
    private int gear;
    private double load;
    private double steerDegrees;
    private double wheelRotLeft;
    private double wheelRotRight;
    private final double[] wheelOffsets = new double[8];
    private double trailerWheelRotLeft;
    private double trailerWheelRotRight;
    private final double[] trailerWheelOffsets = new double[4];
    private final double[] articulation = new double[3];

    private int rightingTicks;
    private double rightingFromY;
    private double rightingFromPitch;
    private double rightingFromRoll;
    private double rightingFromBend;

    private StagedPose staged;
    private StagedTrailer stagedTrailer;

    private record StagedPose(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
    }

    private record StagedTrailer(double x, double y, double z, float yaw, float pitch, float roll, float artYaw,
            float artPitch, float artRoll, Vec3 velocity, boolean grounded) {
    }

    TractorJolt(M983TractorEntity tractor) {
        this.tractor = tractor;
        this.level = tractor.level();
    }

    static Role decide(boolean clientSide, boolean simulating, boolean driven, boolean held, boolean proxyWanted) {
        if (clientSide) {
            if (simulating) {
                return held ? Role.HOLD : Role.SIMULATE;
            }
            return proxyWanted ? Role.FOLLOW : Role.NONE;
        }
        if (driven) {
            return Role.FOLLOW;
        }
        return held ? Role.HOLD : Role.SIMULATE;
    }

    public static JoltVehicle.Controls controlsFor(int keys, boolean held) {
        if (held) {
            return new JoltVehicle.Controls(0.0, 0.0, 1.0, 1.0, false);
        }
        return TractorDrive.controlsFor(keys);
    }

    public Role role() {
        return this.role;
    }

    public boolean active() {
        return this.vehicle != null && this.role != Role.NONE;
    }

    public JoltVehicle vehicle() {
        return this.vehicle;
    }

    public VehicleGround ground() {
        return this.ground;
    }

    public int[] bodyIds() {
        if (this.vehicle == null) {
            return new int[0];
        }
        int groundId = this.ground == null ? Jolt.cInvalidBodyId : this.ground.bodyId();
        if (this.hitch != null) {
            return new int[] {this.vehicle.bodyId(), groundId, this.hitch.trailer().bodyId()};
        }
        return new int[] {this.vehicle.bodyId(), groundId};
    }

    public Hitch hitch() {
        return this.hitch;
    }

    public boolean hitchLinked() {
        return this.hitch != null && this.hitch.linked();
    }

    public void setTrainTrailer(@Nullable PatriotVehicleEntity trailer) {
        this.trainTrailer = trailer;
    }

    public double[] articulation() {
        return this.articulation;
    }

    public double trailerWheelRot(boolean left) {
        return left ? this.trailerWheelRotLeft : this.trailerWheelRotRight;
    }

    public double trailerWheelOffset(int i) {
        return this.trailerWheelOffsets[i];
    }

    public double speedMs() {
        return this.speedMs;
    }

    public double rpm() {
        return this.rpm;
    }

    public int gear() {
        return this.gear;
    }

    public double load() {
        return this.load;
    }

    public double steerDegrees() {
        return this.steerDegrees;
    }

    public double wheelRot(boolean left) {
        return left ? this.wheelRotLeft : this.wheelRotRight;
    }

    public double wheelOffset(int i) {
        return this.wheelOffsets[i];
    }

    public int rollbacks() {
        return this.rollbacks;
    }

    void update(boolean simulating, boolean driven, boolean held, int keys, double testThrottle, double testSteer,
            Vec3 observedVelocity, double observedYawRate) {
        if (this.broken) {
            return;
        }
        this.driven = driven;
        boolean client = this.level.isClientSide;
        boolean proxyWanted = client && !simulating && this.nearLocalSimulator();
        Role wanted = decide(client, simulating, driven, held, proxyWanted);
        try {
            if (wanted != this.role || (wanted != Role.NONE
                    && this.specRevision != VehicleSpecs.revision(VehicleSpecs.M983_TRACTOR))) {
                this.switchTo(wanted, observedVelocity, observedYawRate);
            }
            this.syncHitch();
            if (this.vehicle != null && this.role == Role.SIMULATE) {
                this.vehicle.setDriven(driven || testThrottle != 0.0);
                if (testThrottle != 0.0) {
                    this.controls = new JoltVehicle.Controls(testThrottle, testSteer, 0.0, 0.0, false);
                } else {
                    this.controls = driven && simulating ? controlsFor(keys, held) : JoltVehicle.Controls.IDLE;
                }
                this.vehicle.setControls(this.controls);
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private boolean nearLocalSimulator() {
        for (TractorJolt other : LOCAL_SIMULATORS) {
            if (other != this && other.level == this.level
                    && other.tractor.distanceToSqr(this.tractor) < PROXY_RANGE * PROXY_RANGE) {
                return true;
            }
        }
        return false;
    }

    private void switchTo(Role wanted, Vec3 observedVelocity, double observedYawRate) {
        Role previous = this.role;
        LOCAL_SIMULATORS.remove(this);
        if (wanted == Role.NONE) {
            this.releaseAll();
            this.role = Role.NONE;
            return;
        }
        VehicleSpec spec = VehicleSpecs.m983Tractor();
        boolean rebuild = this.vehicle != null
                && this.specRevision != VehicleSpecs.revision(VehicleSpecs.M983_TRACTOR);
        Vec3 velocity = observedVelocity;
        double yawRate = observedYawRate;
        if (rebuild) {
            JoltVehicle.Snapshot s = this.vehicle.snapshot();
            velocity = new Vec3(s.vx() / Units.TICKS_PER_SECOND, s.vy() / Units.TICKS_PER_SECOND,
                    s.vz() / Units.TICKS_PER_SECOND);
            yawRate = s.wy();
        }
        boolean needsWheels = wanted == Role.SIMULATE || wanted == Role.HOLD || !this.level.isClientSide;
        if (this.vehicle != null && (rebuild || (needsWheels && !this.vehicle.wheeled()))) {
            this.releaseAll();
        }
        if (this.vehicle == null) {
            this.world = PhysicsWorlds.obtain(this.level);
            this.vehicle = new JoltVehicle(this.world, spec, this.tractor.getX(), this.tractor.getY(),
                    this.tractor.getZ(), this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll,
                    needsWheels);
            this.ground = null;
            this.specRevision = VehicleSpecs.revision(VehicleSpecs.M983_TRACTOR);
            if (!this.joined) {
                PhysicsWorlds.join(this.level, this);
                this.joined = true;
            }
            if (wanted == Role.SIMULATE) {
                this.vehicle.setVelocity(velocity.x * Units.TICKS_PER_SECOND, velocity.y * Units.TICKS_PER_SECOND,
                        velocity.z * Units.TICKS_PER_SECOND, 0.0, yawRate, 0.0);
            }
        }
        boolean needsGround = wanted == Role.SIMULATE || (wanted == Role.FOLLOW && !this.level.isClientSide)
                || wanted == Role.HOLD;
        if (needsGround && this.ground == null && this.vehicle.wheeled()) {
            this.ground = new VehicleGround(this.world, this.vehicle.slot(),
                    new LevelColumns(this.level, this.tractor.softBlocksGiveWay()));
        }
        if (!needsGround && this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        switch (wanted) {
            case SIMULATE -> {
                if (previous == Role.FOLLOW || previous == Role.HOLD || previous == Role.NONE) {
                    this.vehicle.teleport(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(),
                            this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll, null, null);
                    if (previous == Role.FOLLOW || previous == Role.HOLD) {
                        this.vehicle.setVelocity(velocity.x * Units.TICKS_PER_SECOND,
                                velocity.y * Units.TICKS_PER_SECOND, velocity.z * Units.TICKS_PER_SECOND, 0.0,
                                yawRate, 0.0);
                    }
                }
                this.vehicle.setMode(JoltVehicle.Mode.DYNAMIC);
                if (this.hitch != null) {
                    this.placeHitch(previous == Role.FOLLOW || previous == Role.HOLD ? velocity : null, yawRate);
                    this.hitch.setMode(JoltVehicle.Mode.DYNAMIC);
                }
                if (this.level.isClientSide) {
                    LOCAL_SIMULATORS.add(this);
                }
            }
            case FOLLOW -> {
                this.vehicle.setMode(JoltVehicle.Mode.KINEMATIC);
                if (this.hitch != null) {
                    this.hitch.setMode(JoltVehicle.Mode.KINEMATIC);
                }
            }
            case HOLD -> {
                this.placeHeld();
                this.vehicle.setMode(JoltVehicle.Mode.STATIC);
                if (this.hitch != null) {
                    this.hitch.setMode(JoltVehicle.Mode.STATIC);
                }
                if (this.level.isClientSide) {
                    LOCAL_SIMULATORS.add(this);
                }
            }
            default -> {
            }
        }
        this.hasWritten = wanted == Role.HOLD;
        this.acceptedTick = Integer.MIN_VALUE;
        this.role = wanted;
    }

    private void placeHeld() {
        this.vehicle.teleport(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(), this.tractor.getYRot(),
                this.tractor.getXRot(), this.tractor.roll, new com.github.stephengold.joltjni.Vec3(0f, 0f, 0f),
                new com.github.stephengold.joltjni.Vec3(0f, 0f, 0f));
        if (this.hitch != null) {
            this.placeHitch(Vec3.ZERO, 0.0);
        }
        this.writtenX = this.tractor.getX();
        this.writtenY = this.tractor.getY();
        this.writtenZ = this.tractor.getZ();
        this.writtenYaw = this.tractor.getYRot();
    }

    private void placeHitch(@Nullable Vec3 velocity, double yawRate) {
        PatriotVehicleEntity trailer = this.hitchTrailer;
        if (this.hitch == null || trailer == null) {
            return;
        }
        com.github.stephengold.joltjni.Vec3 linear = velocity == null ? null
                : new com.github.stephengold.joltjni.Vec3((float) (velocity.x * Units.TICKS_PER_SECOND),
                        (float) (velocity.y * Units.TICKS_PER_SECOND), (float) (velocity.z * Units.TICKS_PER_SECOND));
        com.github.stephengold.joltjni.Vec3 angular = velocity == null ? null
                : new com.github.stephengold.joltjni.Vec3(0f, (float) yawRate, 0f);
        this.hitch.teleport(trailer.getX(), trailer.getY(), trailer.getZ(), trailer.getYRot(), trailer.getXRot(),
                trailer.getRoll(), linear, angular);
    }

    private void syncHitch() {
        PatriotVehicleEntity wanted = this.trainTrailer;
        if (this.vehicle == null || this.role == Role.NONE || wanted == null || wanted.isRemoved()) {
            this.releaseHitch();
            return;
        }
        int revision = VehicleSpecs.revision(wanted.kind().specName());
        if (this.hitch != null && (this.hitchTrailer != wanted || this.hitchRevision != revision
                || this.hitch.trailer().wheeled() != this.vehicle.wheeled())) {
            this.releaseHitch();
        }
        if (this.hitch == null) {
            this.buildHitch(wanted, revision);
        }
    }

    private void buildHitch(PatriotVehicleEntity trailer, int revision) {
        TrainSpec train = VehicleSpecs.train(trailer.kind().specName());
        double[] pose;
        if (this.level.isClientSide && this.role == Role.FOLLOW) {
            pose = new double[] {trailer.getX(), trailer.getY(), trailer.getZ(), trailer.getYRot(),
                    trailer.getXRot(), trailer.getRoll()};
        } else {
            double bend = M983Coupling.bend(this.tractor.getYRot(), trailer.getYRot());
            pose = M983Coupling.trailerPoseFor(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(),
                    this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll, bend);
            M983Coupling.placeTrailer(trailer, pose, bend);
        }
        this.hitch = Hitch.attach(this.world, this.vehicle, train.trailer, train.coupling, TRACTOR_ANCHOR,
                TRAILER_ANCHOR, pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]);
        this.hitchTrailer = trailer;
        this.hitchRevision = revision;
        this.vehicle.setForwardCapMs(Units.kmhToMs(train.coupling.trainLimitKmh));
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        if (this.vehicle.mode() == JoltVehicle.Mode.DYNAMIC) {
            this.hitch.trailer().setVelocity(s.vx(), s.vy(), s.vz(), s.wx(), s.wy(), s.wz());
        }
        this.hitch.setMode(this.vehicle.mode());
    }

    private void releaseHitch() {
        if (this.hitch != null) {
            this.hitch.close();
            this.hitch = null;
        }
        if (this.vehicle != null) {
            this.vehicle.setForwardCapMs(0.0);
        }
        this.hitchTrailer = null;
        this.stagedTrailer = null;
        this.articulation[0] = 0.0;
        this.articulation[1] = 0.0;
        this.articulation[2] = 0.0;
    }

    private void releaseAll() {
        this.staged = null;
        this.releaseHitch();
        if (this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        if (this.vehicle != null) {
            this.vehicle.close();
            this.vehicle = null;
        }
        LOCAL_SIMULATORS.remove(this);
        if (this.joined) {
            PhysicsWorlds.leave(this.level, this);
            this.joined = false;
        }
    }

    private void fail(Throwable error) {
        this.broken = true;
        LOGGER.warn("Jolt vehicle physics failed, the tractor goes back to the built-in physics: {}", error.toString());
        this.releaseAll();
        this.role = Role.NONE;
    }

    public boolean failed() {
        return this.broken;
    }

    @Override
    public void beforeStep() {
        if (this.vehicle == null || this.world == null || this.world.isClosed()) {
            return;
        }
        try {
            switch (this.role) {
                case SIMULATE -> this.beforeSimulate();
                case FOLLOW -> this.beforeFollow();
                case HOLD -> this.beforeHold();
                default -> {
                }
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private void beforeHold() {
        if (this.ground != null) {
            this.ground.update(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ());
        }
        if (this.hasWritten && this.movedBy(1.0E-6, 0.01)) {
            this.placeHeld();
        }
    }

    private void beforeSimulate() {
        if (this.rightingTicks > 0) {
            this.advanceRighting();
            return;
        }
        if (this.ground != null) {
            this.ground.update(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ());
        }
        if (this.hasWritten && this.movedBy(0.0025, 0.5)) {
            JoltVehicle.Snapshot s = this.vehicle.snapshot();
            com.github.stephengold.joltjni.Vec3 linear = new com.github.stephengold.joltjni.Vec3((float) s.vx(),
                    (float) s.vy(), (float) s.vz());
            com.github.stephengold.joltjni.Vec3 angular = new com.github.stephengold.joltjni.Vec3((float) s.wx(),
                    (float) s.wy(), (float) s.wz());
            this.vehicle.teleport(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(),
                    this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll, linear, angular);
            if (this.hitch != null && this.hitchTrailer != null) {
                double bend = this.articulation[0];
                double[] pose = M983Coupling.trailerPoseFor(this.tractor.getX(), this.tractor.getY(),
                        this.tractor.getZ(), this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll, bend);
                M983Coupling.placeTrailer(this.hitchTrailer, pose, bend);
                this.hitch.teleport(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5], linear, angular);
            }
        }
    }

    private boolean movedBy(double distanceSquared, double degrees) {
        double dx = this.tractor.getX() - this.writtenX;
        double dy = this.tractor.getY() - this.writtenY;
        double dz = this.tractor.getZ() - this.writtenZ;
        double turned = Math.abs(PoseMath.wrapDegrees(this.tractor.getYRot() - this.writtenYaw));
        return dx * dx + dy * dy + dz * dz > distanceSquared || turned > degrees;
    }

    private void beforeFollow() {
        if (this.ground != null) {
            this.ground.update(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ());
        }
        this.vehicle.moveKinematic(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(),
                this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll, TICK_SECONDS);
        PatriotVehicleEntity trailer = this.hitchTrailer;
        if (this.hitch != null && trailer != null) {
            this.hitch.moveKinematic(trailer.getX(), trailer.getY(), trailer.getZ(), trailer.getYRot(),
                    trailer.getXRot(), trailer.getRoll(), TICK_SECONDS);
        }
    }

    @Override
    public void afterStep() {
        if (this.vehicle == null || this.world == null || this.world.isClosed()) {
            return;
        }
        try {
            switch (this.role) {
                case SIMULATE -> this.afterSimulate();
                case FOLLOW -> this.afterFollow();
                default -> {
                }
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private void afterSimulate() {
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        if (!Double.isFinite(s.x()) || !Double.isFinite(s.y()) || !Double.isFinite(s.z())) {
            this.fail(new IllegalStateException("the body left the numbers"));
            return;
        }
        this.stage(s.x(), s.y(), s.z(), (float) s.yawDeg(), (float) s.pitchDeg(), (float) s.rollDeg(),
                new Vec3(s.vx() / Units.TICKS_PER_SECOND, s.vy() / Units.TICKS_PER_SECOND,
                        s.vz() / Units.TICKS_PER_SECOND), this.vehicle.wheelsOnGround() > 0);
        this.writtenX = s.x();
        this.writtenY = s.y();
        this.writtenZ = s.z();
        this.writtenYaw = (float) s.yawDeg();
        this.hasWritten = true;
        this.readGauges(s);
        this.stageTrailer();
        double hit = this.vehicle.takeImpactSpeed();
        double normalY = this.vehicle.lastImpactNormalY();
        double landing = this.vehicle.takeLanding();
        if (hit / Units.TICKS_PER_SECOND >= 0.3 && Math.abs(normalY) < 0.7) {
            this.tractor.reportJoltImpact(M983TractorEntity.IMPACT_HORIZONTAL, hit / Units.TICKS_PER_SECOND);
        } else if (landing / Units.TICKS_PER_SECOND > 0.4) {
            this.tractor.reportJoltImpact(M983TractorEntity.IMPACT_VERTICAL, landing / Units.TICKS_PER_SECOND);
        } else if (hit / Units.TICKS_PER_SECOND > 0.4 && Math.abs(normalY) >= 0.7) {
            this.tractor.reportJoltImpact(M983TractorEntity.IMPACT_VERTICAL, hit / Units.TICKS_PER_SECOND);
        }
        this.reportTrailerImpacts();
    }

    private void stageTrailer() {
        PatriotVehicleEntity trailer = this.hitchTrailer;
        if (this.hitch == null || trailer == null) {
            this.stagedTrailer = null;
            return;
        }
        JoltVehicle.Snapshot t = this.hitch.snapshot();
        double[] art = this.hitch.articulation();
        this.articulation[0] = art[0];
        this.articulation[1] = art[1];
        this.articulation[2] = art[2];
        this.stagedTrailer = new StagedTrailer(t.x(), t.y(), t.z(), (float) t.yawDeg(), (float) t.pitchDeg(),
                (float) t.rollDeg(), (float) art[0], (float) art[1], (float) art[2],
                new Vec3(t.vx() / Units.TICKS_PER_SECOND, t.vy() / Units.TICKS_PER_SECOND,
                        t.vz() / Units.TICKS_PER_SECOND), this.hitch.trailer().wheelsOnGround() > 0);
    }

    private void reportTrailerImpacts() {
        PatriotVehicleEntity trailer = this.hitchTrailer;
        if (this.hitch == null || trailer == null) {
            return;
        }
        JoltVehicle body = this.hitch.trailer();
        double hit = body.takeImpactSpeed();
        double normalY = body.lastImpactNormalY();
        double landing = body.takeLanding();
        if (hit / Units.TICKS_PER_SECOND >= 0.3 && Math.abs(normalY) < 0.7) {
            trailer.reportJoltImpact(PatriotVehicleEntity.IMPACT_HORIZONTAL, hit / Units.TICKS_PER_SECOND);
        } else if (landing / Units.TICKS_PER_SECOND > 0.4) {
            trailer.reportJoltImpact(PatriotVehicleEntity.IMPACT_VERTICAL, landing / Units.TICKS_PER_SECOND);
        } else if (hit / Units.TICKS_PER_SECOND > 0.4 && Math.abs(normalY) >= 0.7) {
            trailer.reportJoltImpact(PatriotVehicleEntity.IMPACT_VERTICAL, hit / Units.TICKS_PER_SECOND);
        }
    }

    public boolean applyStagedTrailerPose(PatriotVehicleEntity trailer) {
        StagedTrailer pose = this.stagedTrailer;
        if (pose == null || trailer != this.hitchTrailer) {
            return false;
        }
        this.stagedTrailer = null;
        trailer.applyJoltPose(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.roll(), pose.artYaw(),
                pose.artPitch(), pose.artRoll(), pose.velocity(), pose.grounded());
        return true;
    }

    private void afterFollow() {
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        this.readGauges(s);
        if (this.hitch != null) {
            double[] art = this.hitch.articulation();
            this.articulation[0] = art[0];
            this.articulation[1] = art[1];
            this.articulation[2] = art[2];
        }
        if (this.level.isClientSide || !this.vehicle.wheeled() || this.ground == null) {
            return;
        }
        VehicleSpec spec = this.vehicle.spec();
        int now = this.tractor.tickCount;
        double x = this.tractor.getX();
        double y = this.tractor.getY();
        double z = this.tractor.getZ();
        if (this.acceptedTick == Integer.MIN_VALUE) {
            this.accept(x, y, z, now);
            return;
        }
        int elapsed = Math.max(1, now - this.acceptedTick);
        double moved = Math.sqrt(Mth.square(x - this.acceptedX) + Mth.square(y - this.acceptedY)
                + Mth.square(z - this.acceptedZ));
        double limit = spec.forwardLimitMs() * spec.limits.boostFactor * spec.limits.cheatSpeedFactor;
        double speed = moved / (elapsed * TICK_SECONDS);
        boolean tooFast = moved > 4.0 && speed > limit;
        double depth = this.deepestIntoGround();
        if (this.hitch != null) {
            depth = Math.max(depth, this.deepestIntoGround(this.hitch.trailer(), -6.2, 6.2));
        }
        boolean folded = this.hitchFolded();
        if (tooFast || folded || depth > spec.limits.groundDepthMetres) {
            String reason = tooFast
                    ? String.format(java.util.Locale.ROOT, "speed %.0f km/h", Units.msToKmh(speed))
                    : folded ? "hitch bent beyond its limit"
                    : String.format(java.util.Locale.ROOT, "body %.2f m into the ground", depth);
            String driver = this.tractor.getControllingPassenger() == null ? "nobody"
                    : this.tractor.getControllingPassenger().getName().getString();
            String line = this.rollbackLog.note("Tractor", now, this.tractor.getId(), driver, reason);
            if (line != null) {
                LOGGER.info(line);
            }
            this.tractor.rollBackTo(this.acceptedX, this.acceptedY, this.acceptedZ);
            this.rollbacks++;
            return;
        }
        this.accept(x, y, z, now);
    }

    private void accept(double x, double y, double z, int tick) {
        this.acceptedX = x;
        this.acceptedY = y;
        this.acceptedZ = z;
        this.acceptedTick = tick;
    }

    private double deepestIntoGround() {
        VehicleSpec spec = this.vehicle.spec();
        double bottom = 0.55;
        double half = 1.7;
        double deepest = 0.0;
        double[][] r = PoseMath.rotation(this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll);
        double[] origin = PoseMath.bodyOrigin(this.tractor.getX(), this.tractor.getY(), this.tractor.getZ(),
                this.tractor.getYRot(), this.tractor.getXRot(), this.tractor.roll);
        for (double lz = -1.75; lz <= 10.1; lz += 2.96) {
            for (int side = -1; side <= 1; side += 2) {
                double[] p = PoseMath.toWorld(origin, r, side * half, bottom, lz);
                double h = this.ground.heightAt(p[0], p[2]);
                if (!Double.isNaN(h)) {
                    deepest = Math.max(deepest, h - p[1]);
                }
            }
        }
        return deepest;
    }

    private boolean hitchFolded() {
        PatriotVehicleEntity trailer = this.hitchTrailer;
        if (this.hitch == null || trailer == null) {
            return false;
        }
        TrainSpec.Coupling c = VehicleSpecs.train(trailer.kind().specName()).coupling;
        return Math.abs(trailer.getArticulationYaw()) > c.yawLimitDegrees + ARTICULATION_CHEAT_MARGIN
                || Math.abs(trailer.getArticulationPitch()) > c.pitchLimitDegrees + ARTICULATION_CHEAT_MARGIN
                || Math.abs(trailer.getArticulationRoll()) > c.rollLimitDegrees + ARTICULATION_CHEAT_MARGIN;
    }

    private double deepestIntoGround(JoltVehicle body, double fromZ, double toZ) {
        double bottom = 0.55;
        double half = 1.7;
        double deepest = 0.0;
        double[][] r = body.rotation();
        double[] origin = body.origin();
        double step = (toZ - fromZ) / 3.0;
        for (double lz = fromZ; lz <= toZ + 1.0E-6; lz += step) {
            for (int side = -1; side <= 1; side += 2) {
                double[] p = PoseMath.toWorld(origin, r, side * half, bottom, lz);
                double h = this.ground.heightAt(p[0], p[2]);
                if (!Double.isNaN(h)) {
                    deepest = Math.max(deepest, h - p[1]);
                }
            }
        }
        return deepest;
    }

    public void startRighting() {
        if (this.vehicle == null || this.role != Role.SIMULATE) {
            return;
        }
        this.rightingTicks = RIGHTING_TICKS;
        this.rightingFromY = this.tractor.getY();
        this.rightingFromPitch = this.tractor.getXRot();
        this.rightingFromRoll = this.tractor.roll;
        this.rightingFromBend = this.articulation[0];
        if (this.hitch != null) {
            this.hitch.setMode(JoltVehicle.Mode.KINEMATIC);
        }
        this.vehicle.setMode(JoltVehicle.Mode.KINEMATIC);
    }

    private void advanceRighting() {
        double next = 1.0 - (double) (this.rightingTicks - 1) / RIGHTING_TICKS;
        double smooth = next * next * (3.0 - 2.0 * next);
        double lift = 2.5 * Math.sin(Math.PI * next);
        double y = this.rightingFromY + lift;
        double pitch = this.rightingFromPitch * (1.0 - smooth);
        double roll = this.rightingFromRoll * (1.0 - smooth);
        this.vehicle.moveKinematic(this.tractor.getX(), y, this.tractor.getZ(), this.tractor.getYRot(), pitch,
                roll, TICK_SECONDS);
        this.stage(this.tractor.getX(), y, this.tractor.getZ(), this.tractor.getYRot(), (float) pitch,
                (float) roll, Vec3.ZERO, false);
        if (this.hitch != null) {
            double bend = this.rightingFromBend * (1.0 - smooth);
            double[] pose = M983Coupling.trailerPoseFor(this.tractor.getX(), y, this.tractor.getZ(),
                    this.tractor.getYRot(), (float) pitch, (float) roll, bend);
            this.hitch.moveKinematic(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5], TICK_SECONDS);
            this.stagedTrailer = new StagedTrailer(pose[0], pose[1], pose[2], (float) pose[3], (float) pose[4],
                    (float) pose[5], (float) bend, 0.0f, 0.0f, Vec3.ZERO, false);
        }
        this.rightingTicks--;
        if (this.rightingTicks == 0) {
            this.vehicle.setVelocity(0, 0, 0, 0, 0, 0);
            this.vehicle.setMode(JoltVehicle.Mode.DYNAMIC);
            if (this.hitch != null) {
                this.hitch.trailer().setVelocity(0, 0, 0, 0, 0, 0);
                this.hitch.setMode(JoltVehicle.Mode.DYNAMIC);
            }
        }
    }

    private void stage(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
        this.staged = new StagedPose(x, y, z, yaw, pitch, roll, velocity, grounded);
    }

    public boolean applyStagedPose() {
        StagedPose pose = this.staged;
        if (pose == null) {
            return false;
        }
        this.staged = null;
        this.tractor.applyJoltPose(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.roll(),
                pose.velocity(), pose.grounded());
        return true;
    }

    private void readGauges(JoltVehicle.Snapshot s) {
        this.speedMs = Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz());
        if (this.vehicle.wheeled() && this.role == Role.SIMULATE) {
            this.rpm = this.vehicle.rpm();
            this.gear = this.vehicle.gear();
            this.load = this.vehicle.engineLoad();
            int axles = this.vehicle.wheelCount() / 2;
            if (axles > 0) {
                int front = (axles - 1) * 2;
                this.steerDegrees = -Math.toDegrees(this.vehicle.wheelSteer(front));
                double left = 0.0;
                double right = 0.0;
                for (int axle = 0; axle < axles; axle++) {
                    left += this.vehicle.wheelAngle(axle * 2);
                    right += this.vehicle.wheelAngle(axle * 2 + 1);
                }
                this.wheelRotLeft = left / axles;
                this.wheelRotRight = right / axles;
                for (int i = 0; i < Math.min(8, this.vehicle.wheelCount()); i++) {
                    this.wheelOffsets[i] = Mth.clamp(this.vehicle.wheelOffset(i), -0.3, 0.3);
                }
            }
            if (this.hitch != null && this.hitch.trailer().wheeled()) {
                this.readTrailerGauges(this.hitch.trailer());
            }
        }
    }

    private void readTrailerGauges(JoltVehicle trailer) {
        int axles = trailer.wheelCount() / 2;
        if (axles == 0) {
            return;
        }
        double left = 0.0;
        double right = 0.0;
        for (int axle = 0; axle < axles; axle++) {
            left += trailer.wheelAngle(axle * 2);
            right += trailer.wheelAngle(axle * 2 + 1);
        }
        this.trailerWheelRotLeft = left / axles;
        this.trailerWheelRotRight = right / axles;
        for (int i = 0; i < Math.min(4, trailer.wheelCount()); i++) {
            this.trailerWheelOffsets[i] = Mth.clamp(trailer.wheelOffset(i), -0.3, 0.3);
        }
    }

    public void push(double ix, double iy, double iz) {
        if (this.vehicle != null && this.role == Role.SIMULATE && this.rightingTicks == 0) {
            this.vehicle.addImpulse(ix, iy, iz);
        }
    }

    public boolean spinsFreely() {
        return this.vehicle != null && this.role == Role.SIMULATE;
    }

    @Override
    public void blockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.ground == null) {
            return;
        }
        GroundPatch patch = this.ground.patch();
        if (!GroundPatchBuilder.touches(patch, pos.getX(), pos.getZ())
                || !GroundPatchBuilder.inWindow(patch, pos.getY())) {
            return;
        }
        boolean skipSoft = this.tractor.softBlocksGiveWay();
        if (this.leavesGroundAlone(oldState, pos, skipSoft)
                && this.leavesGroundAlone(newState, pos, skipSoft)) {
            return;
        }
        this.ground.markChanged(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean leavesGroundAlone(BlockState state, BlockPos pos, boolean skipSoft) {
        if (state.isAir() || (skipSoft && state.is(TelWorld.BREAKABLE))) {
            return true;
        }
        return state.getCollisionShape(this.level, pos, CollisionContext.empty()).isEmpty();
    }

    @Override
    public void shutdown() {
        this.releaseHitch();
        if (this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        if (this.vehicle != null) {
            this.vehicle.close();
            this.vehicle = null;
        }
        LOCAL_SIMULATORS.remove(this);
        this.joined = false;
        this.role = Role.NONE;
        this.world = null;
    }

    public void close() {
        this.releaseAll();
        this.role = Role.NONE;
    }
}
