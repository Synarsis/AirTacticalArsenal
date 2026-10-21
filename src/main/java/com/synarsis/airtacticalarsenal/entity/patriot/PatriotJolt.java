package com.synarsis.airtacticalarsenal.entity.patriot;

import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.entity.tel.RollbackLog;
import com.synarsis.airtacticalarsenal.entity.tel.TelDriving;
import com.synarsis.airtacticalarsenal.entity.tel.TelWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatch;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatchBuilder;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltTrain;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.LevelColumns;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.TrainSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleGround;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class PatriotJolt implements PhysicsWorlds.Participant {

    public enum Role {
        NONE,
        SIMULATE,
        FOLLOW,
        HOLD
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<PatriotJolt> LOCAL_SIMULATORS = new ArrayList<>();
    private static final double PROXY_RANGE = 120.0;
    private static final double TICK_SECONDS = 0.05;
    private static final int RIGHTING_TICKS = 20;
    private static final double ARTICULATION_CHEAT_MARGIN = 5.0;

    private final PatriotVehicleEntity entity;
    private final Level level;
    private PhysicsWorld world;
    private JoltTrain train;
    private VehicleGround ground;
    private Role role = Role.NONE;
    private int specRevision = -1;
    private boolean joined;
    private boolean broken;
    private boolean tractorFree;

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
    private double steerFront;
    private double steerSecond;
    private final double[] wheelSpin = new double[4];
    private final double[] wheelOffsets = new double[PatriotPose.TRACTOR_WHEELS + PatriotPose.TRAILER_WHEELS];

    private int rightingTicks;
    private double rightingFromY;
    private double rightingFromPitch;
    private double rightingFromRoll;
    private double[] rightingFromArticulation = new double[3];

    private StagedPose staged;

    private record StagedPose(double x, double y, double z, float yaw, float pitch, float roll, float artYaw,
            float artPitch, float artRoll, Vec3 velocity, boolean grounded) {
    }

    PatriotJolt(PatriotVehicleEntity entity) {
        this.entity = entity;
        this.level = entity.level();
    }

    static Role decide(boolean clientSide, boolean simulating, boolean driven, boolean held, boolean proxyWanted) {
        if (clientSide) {
            if (simulating) {
                return held ? Role.HOLD : Role.SIMULATE;
            }
            return proxyWanted ? Role.FOLLOW : Role.NONE;
        }
        if (held) {
            return Role.HOLD;
        }
        return driven ? Role.FOLLOW : Role.SIMULATE;
    }

    public static JoltVehicle.Controls controlsFor(int keys) {
        return TelDriving.controls(keys);
    }

    public static boolean overturned(double pitchDeg, double rollDeg) {
        return Math.abs(pitchDeg) > 60.0 || Math.abs(rollDeg) > 60.0;
    }

    public Role role() {
        return this.role;
    }

    public boolean active() {
        return this.train != null && this.role != Role.NONE;
    }

    public JoltTrain train() {
        return this.train;
    }

    public VehicleGround ground() {
        return this.ground;
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

    public double steerFront() {
        return this.steerFront;
    }

    public double steerSecond() {
        return this.steerSecond;
    }

    public double wheelSpin(int index) {
        return this.wheelSpin[index];
    }

    public double wheelOffset(int wheel) {
        return this.wheelOffsets[wheel];
    }

    public int rollbacks() {
        return this.rollbacks;
    }

    public boolean failed() {
        return this.broken;
    }

    public boolean linked() {
        return this.train != null && this.train.linked();
    }

    void update(boolean simulating, boolean driven, boolean held, int keys, double testThrottle,
            Vec3 observedVelocity, double observedYawRate) {
        if (this.broken) {
            return;
        }
        this.driven = driven;
        boolean client = this.level.isClientSide;
        boolean proxyWanted = client && !simulating && this.nearLocalSimulator();
        Role wanted = decide(client, simulating, driven, held, proxyWanted);
        String name = this.entity.kind().specName();
        try {
            if (wanted != this.role || (wanted != Role.NONE && this.specRevision != VehicleSpecs.revision(name))) {
                this.switchTo(wanted, observedVelocity, observedYawRate);
            }
            if (this.train != null && this.role == Role.SIMULATE) {
                boolean remote = !driven && !client && testThrottle != 0.0;
                this.train.tractor().setDriven(driven || remote);
                this.train.tractor().setControls(driven && simulating ? controlsFor(keys)
                        : remote ? new JoltVehicle.Controls(testThrottle, 0.0, 0.0, 0.0) : JoltVehicle.Controls.IDLE);
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private boolean nearLocalSimulator() {
        for (PatriotJolt other : LOCAL_SIMULATORS) {
            if (other != this && other.level == this.level
                    && other.entity.distanceToSqr(this.entity) < PROXY_RANGE * PROXY_RANGE) {
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
        TrainSpec spec = VehicleSpecs.train(this.entity.kind().specName());
        boolean rebuild = this.train != null && this.specRevision != VehicleSpecs.revision(this.entity.kind().specName());
        Vec3 velocity = observedVelocity;
        double yawRate = observedYawRate;
        if (rebuild) {
            JoltVehicle.Snapshot s = this.train.tractor().snapshot();
            velocity = new Vec3(s.vx() / Units.TICKS_PER_SECOND, s.vy() / Units.TICKS_PER_SECOND,
                    s.vz() / Units.TICKS_PER_SECOND);
            yawRate = s.wy();
        }
        boolean needsWheels = wanted == Role.SIMULATE || wanted == Role.HOLD || !this.level.isClientSide;
        if (this.train != null && (rebuild || (needsWheels && !this.train.tractor().wheeled()))) {
            this.releaseAll();
        }
        if (this.train == null) {
            this.world = PhysicsWorlds.obtain(this.level);
            this.train = new JoltTrain(this.world, spec, this.entity.getX(), this.entity.getY(), this.entity.getZ(),
                    this.entity.getYRot(), this.entity.getXRot(), this.entity.getRoll(), this.entity.getArticulationYaw(),
                    this.entity.getArticulationPitch(), this.entity.getArticulationRoll(), needsWheels);
            this.ground = null;
            this.tractorFree = false;
            this.specRevision = VehicleSpecs.revision(this.entity.kind().specName());
            if (!this.joined) {
                PhysicsWorlds.join(this.level, this);
                this.joined = true;
            }
            if (wanted == Role.SIMULATE) {
                this.train.setVelocity(velocity.x * Units.TICKS_PER_SECOND, velocity.y * Units.TICKS_PER_SECOND,
                        velocity.z * Units.TICKS_PER_SECOND, 0.0, yawRate, 0.0);
            }
        }
        boolean needsGround = wanted == Role.SIMULATE || (wanted == Role.FOLLOW && !this.level.isClientSide)
                || wanted == Role.HOLD;
        if (needsGround && this.ground == null && this.train.tractor().wheeled()) {
            this.ground = new VehicleGround(this.world, this.train.slot(),
                    new LevelColumns(this.level, this.entity.softBlocksGiveWay()));
        }
        if (!needsGround && this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        switch (wanted) {
            case SIMULATE -> {
                if (previous != Role.SIMULATE) {
                    this.placeAtEntity(previous == Role.FOLLOW || previous == Role.HOLD ? velocity : null, yawRate);
                }
                this.train.setMode(JoltVehicle.Mode.DYNAMIC);
                if (this.level.isClientSide) {
                    LOCAL_SIMULATORS.add(this);
                }
            }
            case FOLLOW -> this.train.setMode(JoltVehicle.Mode.KINEMATIC);
            case HOLD -> {
                this.placeAtEntity(null, 0.0);
                this.train.setMode(JoltVehicle.Mode.STATIC);
                this.tractorFree = false;
                if (this.level.isClientSide) {
                    LOCAL_SIMULATORS.add(this);
                }
            }
            default -> {
            }
        }
        this.hasWritten = false;
        this.acceptedTick = Integer.MIN_VALUE;
        this.role = wanted;
    }

    private void placeAtEntity(Vec3 velocity, double yawRate) {
        com.github.stephengold.joltjni.Vec3 linear = velocity == null ? null
                : new com.github.stephengold.joltjni.Vec3((float) (velocity.x * Units.TICKS_PER_SECOND),
                        (float) (velocity.y * Units.TICKS_PER_SECOND), (float) (velocity.z * Units.TICKS_PER_SECOND));
        com.github.stephengold.joltjni.Vec3 angular = velocity == null ? null
                : new com.github.stephengold.joltjni.Vec3(0f, (float) yawRate, 0f);
        this.train.teleport(this.entity.getX(), this.entity.getY(), this.entity.getZ(), this.entity.getYRot(),
                this.entity.getXRot(), this.entity.getRoll(), this.entity.getArticulationYaw(),
                this.entity.getArticulationPitch(), this.entity.getArticulationRoll(), linear, angular);
    }

    private void releaseAll() {
        this.staged = null;
        if (this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        if (this.train != null) {
            this.train.close();
            this.train = null;
        }
        LOCAL_SIMULATORS.remove(this);
        if (this.joined) {
            PhysicsWorlds.leave(this.level, this);
            this.joined = false;
        }
    }

    private void fail(Throwable error) {
        this.broken = true;
        LOGGER.warn("Jolt vehicle physics of the Patriot failed, it stays where it is: {}", error.toString());
        this.releaseAll();
        this.role = Role.NONE;
    }

    @Override
    public void beforeStep() {
        if (this.train == null || this.world == null || this.world.isClosed()) {
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

    private void updateGround() {
        if (this.ground != null) {
            this.ground.update(this.entity.getX(), this.entity.getY(), this.entity.getZ());
        }
    }

    private void beforeSimulate() {
        if (this.rightingTicks > 0) {
            this.advanceRighting();
            return;
        }
        this.updateGround();
        if (this.hasWritten && this.movedByOthers()) {
            JoltVehicle.Snapshot s = this.train.tractor().snapshot();
            this.train.teleport(this.entity.getX(), this.entity.getY(), this.entity.getZ(), this.entity.getYRot(),
                    this.entity.getXRot(), this.entity.getRoll(), this.entity.getArticulationYaw(),
                    this.entity.getArticulationPitch(), this.entity.getArticulationRoll(),
                    new com.github.stephengold.joltjni.Vec3((float) s.vx(), (float) s.vy(), (float) s.vz()),
                    new com.github.stephengold.joltjni.Vec3((float) s.wx(), (float) s.wy(), (float) s.wz()));
        }
    }

    private boolean movedByOthers() {
        double dx = this.entity.getX() - this.writtenX;
        double dy = this.entity.getY() - this.writtenY;
        double dz = this.entity.getZ() - this.writtenZ;
        double turned = Math.abs(PoseMath.wrapDegrees(this.entity.getYRot() - this.writtenYaw));
        return dx * dx + dy * dy + dz * dz > 0.0025 || turned > 0.5;
    }

    private void beforeFollow() {
        this.updateGround();
        this.train.moveKinematic(this.entity.getX(), this.entity.getY(), this.entity.getZ(), this.entity.getYRot(),
                this.entity.getXRot(), this.entity.getRoll(), this.entity.getArticulationYaw(),
                this.entity.getArticulationPitch(), this.entity.getArticulationRoll(), TICK_SECONDS);
    }

    private void beforeHold() {
        this.updateGround();
        double offset = this.entity.tractorOffsetBlocks();
        boolean free = offset > 1.0E-4 || this.entity.tractorMoving();
        if (free != this.tractorFree) {
            this.tractorFree = free;
            if (free) {
                this.train.detachTractor();
            } else {
                this.train.setMode(JoltVehicle.Mode.STATIC);
            }
        }
        if (free) {
            Vec3 shift = com.synarsis.airtacticalarsenal.entity.tel.TelGeometry.rotateToWorld(this.entity.getYRot(),
                    this.entity.getXRot(), this.entity.getRoll(), new Vec3(0.0, 0.0, offset));
            this.train.tractor().moveKinematic(this.entity.getX() + shift.x, this.entity.getY() + shift.y,
                    this.entity.getZ() + shift.z, this.entity.getYRot(), this.entity.getXRot(), this.entity.getRoll(),
                    TICK_SECONDS);
        }
    }

    @Override
    public void afterStep() {
        if (this.train == null || this.world == null || this.world.isClosed()) {
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
        JoltVehicle.Snapshot s = this.train.tractor().snapshot();
        if (!Double.isFinite(s.x()) || !Double.isFinite(s.y()) || !Double.isFinite(s.z())) {
            this.fail(new IllegalStateException("the body left the numbers"));
            return;
        }
        double[] art = this.train.linked() ? this.train.articulation() : new double[] {
                this.entity.getArticulationYaw(), this.entity.getArticulationPitch(), this.entity.getArticulationRoll()};
        this.stage(s.x(), s.y(), s.z(), (float) s.yawDeg(), (float) s.pitchDeg(), (float) s.rollDeg(),
                (float) art[0], (float) art[1], (float) art[2],
                new Vec3(s.vx() / Units.TICKS_PER_SECOND, s.vy() / Units.TICKS_PER_SECOND,
                        s.vz() / Units.TICKS_PER_SECOND), this.train.tractor().wheelsOnGround() > 0);
        this.writtenX = s.x();
        this.writtenY = s.y();
        this.writtenZ = s.z();
        this.writtenYaw = (float) s.yawDeg();
        this.hasWritten = true;
        this.readGauges(s);
        this.reportImpacts();
    }

    private void reportImpacts() {
        double hit = Math.max(this.train.tractor().takeImpactSpeed(), this.train.trailer().takeImpactSpeed());
        double normalY = hit > 0.0 ? this.train.tractor().lastImpactNormalY() : 0.0;
        double landing = Math.max(this.train.tractor().takeLanding(), this.train.trailer().takeLanding());
        if (hit / Units.TICKS_PER_SECOND >= 0.3 && Math.abs(normalY) < 0.7) {
            this.entity.reportJoltImpact(PatriotVehicleEntity.IMPACT_HORIZONTAL, hit / Units.TICKS_PER_SECOND);
        } else if (landing / Units.TICKS_PER_SECOND > 0.4) {
            this.entity.reportJoltImpact(PatriotVehicleEntity.IMPACT_VERTICAL, landing / Units.TICKS_PER_SECOND);
        } else if (hit / Units.TICKS_PER_SECOND > 0.4 && Math.abs(normalY) >= 0.7) {
            this.entity.reportJoltImpact(PatriotVehicleEntity.IMPACT_VERTICAL, hit / Units.TICKS_PER_SECOND);
        }
    }

    private void afterFollow() {
        JoltVehicle.Snapshot s = this.train.tractor().snapshot();
        this.readGauges(s);
        if (this.level.isClientSide || !this.train.tractor().wheeled() || this.ground == null) {
            return;
        }
        TrainSpec spec = this.train.spec();
        int now = this.entity.tickCount;
        double x = this.entity.getX();
        double y = this.entity.getY();
        double z = this.entity.getZ();
        if (this.acceptedTick == Integer.MIN_VALUE) {
            this.accept(x, y, z, now);
            return;
        }
        int elapsed = Math.max(1, now - this.acceptedTick);
        double moved = Math.sqrt(Mth.square(x - this.acceptedX) + Mth.square(y - this.acceptedY)
                + Mth.square(z - this.acceptedZ));
        double limit = spec.tractor.forwardLimitMs() * spec.tractor.limits.boostFactor
                * spec.tractor.limits.cheatSpeedFactor;
        double speed = moved / (elapsed * TICK_SECONDS);
        boolean tooFast = moved > 4.0 && speed > limit;
        double depth = Math.max(this.deepestIntoGround(this.train.tractor(), 4.2, 15.9),
                this.deepestIntoGround(this.train.trailer(), -6.2, 6.2));
        double hitchLimit = spec.coupling.yawLimitDegrees + ARTICULATION_CHEAT_MARGIN;
        boolean folded = Math.abs(this.entity.getArticulationYaw()) > hitchLimit
                || Math.abs(this.entity.getArticulationPitch()) > spec.coupling.pitchLimitDegrees + ARTICULATION_CHEAT_MARGIN
                || Math.abs(this.entity.getArticulationRoll()) > spec.coupling.rollLimitDegrees + ARTICULATION_CHEAT_MARGIN;
        if (tooFast || folded || depth > spec.tractor.limits.groundDepthMetres) {
            String reason = tooFast
                    ? String.format(java.util.Locale.ROOT, "speed %.0f km/h", Units.msToKmh(speed))
                    : folded ? "hitch bent beyond its limit"
                    : String.format(java.util.Locale.ROOT, "body %.2f m into the ground", depth);
            String driver = this.entity.getControllingPassenger() == null ? "nobody"
                    : this.entity.getControllingPassenger().getName().getString();
            String line = this.rollbackLog.note("Patriot", now, this.entity.getId(), driver, reason);
            if (line != null) {
                LOGGER.info(line);
            }
            this.entity.rollBackTo(this.acceptedX, this.acceptedY, this.acceptedZ);
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
        if (this.train == null || this.role != Role.SIMULATE) {
            return;
        }
        this.rightingTicks = RIGHTING_TICKS;
        this.rightingFromY = this.entity.getY();
        this.rightingFromPitch = this.entity.getXRot();
        this.rightingFromRoll = this.entity.getRoll();
        this.rightingFromArticulation = new double[] {this.entity.getArticulationYaw(),
                this.entity.getArticulationPitch(), this.entity.getArticulationRoll()};
        this.train.unlink();
        this.train.tractor().setMode(JoltVehicle.Mode.KINEMATIC);
        this.train.trailer().setMode(JoltVehicle.Mode.KINEMATIC);
    }

    private void advanceRighting() {
        double next = 1.0 - (double) (this.rightingTicks - 1) / RIGHTING_TICKS;
        double smooth = next * next * (3.0 - 2.0 * next);
        double lift = 2.5 * Math.sin(Math.PI * next);
        double y = this.rightingFromY + lift;
        double pitch = this.rightingFromPitch * (1.0 - smooth);
        double roll = this.rightingFromRoll * (1.0 - smooth);
        double ay = this.rightingFromArticulation[0] * (1.0 - smooth);
        double ap = this.rightingFromArticulation[1] * (1.0 - smooth);
        double ar = this.rightingFromArticulation[2] * (1.0 - smooth);
        this.train.moveKinematic(this.entity.getX(), y, this.entity.getZ(), this.entity.getYRot(), pitch, roll, ay, ap,
                ar, TICK_SECONDS);
        this.stage(this.entity.getX(), y, this.entity.getZ(), this.entity.getYRot(), (float) pitch, (float) roll,
                (float) ay, (float) ap, (float) ar, Vec3.ZERO, false);
        this.rightingTicks--;
        if (this.rightingTicks == 0) {
            this.train.setVelocity(0, 0, 0, 0, 0, 0);
            this.train.setMode(JoltVehicle.Mode.DYNAMIC);
        }
    }

    private void stage(double x, double y, double z, float yaw, float pitch, float roll, float artYaw, float artPitch,
            float artRoll, Vec3 velocity, boolean grounded) {
        this.staged = new StagedPose(x, y, z, yaw, pitch, roll, artYaw, artPitch, artRoll, velocity, grounded);
    }

    boolean applyStagedPose() {
        StagedPose pose = this.staged;
        if (pose == null) {
            return false;
        }
        this.staged = null;
        this.entity.applyJoltPose(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.roll(), pose.artYaw(),
                pose.artPitch(), pose.artRoll(), pose.velocity(), pose.grounded());
        return true;
    }

    private void readGauges(JoltVehicle.Snapshot s) {
        this.speedMs = Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz());
        JoltVehicle tractor = this.train.tractor();
        JoltVehicle trailer = this.train.trailer();
        if (!tractor.wheeled() || this.role != Role.SIMULATE) {
            return;
        }
        this.rpm = tractor.rpm();
        this.gear = tractor.gear();
        this.load = tractor.engineLoad();
        int axles = tractor.wheelCount() / 2;
        this.steerFront = -Math.toDegrees(tractor.wheelSteer((axles - 1) * 2));
        this.steerSecond = -Math.toDegrees(tractor.wheelSteer((axles - 2) * 2));
        this.wheelSpin[PatriotPose.TRACTOR_LEFT] = -Math.toDegrees(this.averageAngle(tractor, 1));
        this.wheelSpin[PatriotPose.TRACTOR_RIGHT] = -Math.toDegrees(this.averageAngle(tractor, 0));
        this.wheelSpin[PatriotPose.TRAILER_LEFT] = -Math.toDegrees(this.averageAngle(trailer, 1));
        this.wheelSpin[PatriotPose.TRAILER_RIGHT] = -Math.toDegrees(this.averageAngle(trailer, 0));
        for (int axle = 0; axle < axles; axle++) {
            for (int side = 0; side < 2; side++) {
                int jolt = axle * 2 + side;
                int bone = axle * 2 + (1 - side);
                this.wheelOffsets[bone] = Mth.clamp(tractor.wheelOffset(jolt), -0.3, 0.3);
            }
        }
        int trailerAxles = trailer.wheelCount() / 2;
        for (int axle = 0; axle < trailerAxles; axle++) {
            for (int side = 0; side < 2; side++) {
                int jolt = axle * 2 + side;
                int bone = PatriotPose.TRACTOR_WHEELS + axle * 2 + (1 - side);
                this.wheelOffsets[bone] = Mth.clamp(trailer.wheelOffset(jolt), -0.3, 0.3);
            }
        }
    }

    private double averageAngle(JoltVehicle vehicle, int side) {
        int axles = vehicle.wheelCount() / 2;
        double sum = 0.0;
        for (int axle = 0; axle < axles; axle++) {
            sum += vehicle.wheelAngle(axle * 2 + side);
        }
        return axles == 0 ? 0.0 : sum / axles;
    }

    public void push(double ix, double iy, double iz) {
        if (this.train != null && this.role == Role.SIMULATE && this.rightingTicks == 0) {
            this.train.tractor().addImpulse(ix * 0.4, iy * 0.4, iz * 0.4);
            this.train.trailer().addImpulse(ix * 0.6, iy * 0.6, iz * 0.6);
        }
    }

    public boolean spinsFreely() {
        return this.train != null && this.role == Role.SIMULATE;
    }

    @Override
    public void blockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (this.ground == null) {
            return;
        }
        GroundPatch patch = this.ground.patch();
        if (!GroundPatchBuilder.touches(patch, pos.getX(), pos.getZ()) || !GroundPatchBuilder.inWindow(patch, pos.getY())) {
            return;
        }
        boolean skipSoft = this.entity.softBlocksGiveWay();
        if (this.leavesGroundAlone(oldState, pos, skipSoft) && this.leavesGroundAlone(newState, pos, skipSoft)) {
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
        if (this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        if (this.train != null) {
            this.train.close();
            this.train = null;
        }
        LOCAL_SIMULATORS.remove(this);
        this.joined = false;
        this.role = Role.NONE;
        this.world = null;
    }

    void close() {
        this.releaseAll();
        this.role = Role.NONE;
    }
}
