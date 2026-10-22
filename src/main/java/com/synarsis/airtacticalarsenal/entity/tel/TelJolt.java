package com.synarsis.airtacticalarsenal.entity.tel;

import com.github.stephengold.joltjni.Jolt;
import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatch;
import com.synarsis.airtacticalarsenal.vehicle.physics.GroundPatchBuilder;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.LevelColumns;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.Units;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleGround;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class TelJolt implements PhysicsWorlds.Participant {

    public enum Role {
        NONE,
        SIMULATE,
        FOLLOW,
        HOLD
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<TelJolt> LOCAL_SIMULATORS = new ArrayList<>();
    private static final double PROXY_RANGE = 120.0;
    private static final double TICK_SECONDS = 0.05;
    private static final int RIGHTING_TICKS = 20;

    private final IskanderTelEntity tel;
    private final Level level;
    private PhysicsWorld world;
    private JoltVehicle vehicle;
    private VehicleGround ground;
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

    private int rightingTicks;
    private double rightingFromY;
    private double rightingFromPitch;
    private double rightingFromRoll;

    private StagedPose staged;

    private record StagedPose(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
    }

    TelJolt(IskanderTelEntity tel) {
        this.tel = tel;
        this.level = tel.level();
    }

    static Role decide(boolean clientSide, boolean simulating, boolean driven, boolean jacked, boolean proxyWanted) {
        if (clientSide) {
            if (simulating) {
                return jacked ? Role.HOLD : Role.SIMULATE;
            }
            return proxyWanted ? Role.FOLLOW : Role.NONE;
        }
        if (driven) {
            return Role.FOLLOW;
        }
        return jacked ? Role.HOLD : Role.SIMULATE;
    }

    public static JoltVehicle.Controls controlsFor(int keys, boolean stowed) {
        if (!stowed) {
            return new JoltVehicle.Controls(0.0, 0.0, 1.0, 1.0, false);
        }
        return TelDriving.controls(keys);
    }

    static boolean overturned(double pitchDeg, double rollDeg) {
        return Math.abs(pitchDeg) > 60.0 || Math.abs(rollDeg) > 60.0;
    }

    public Role role() {
        return this.role;
    }

    boolean active() {
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
        return new int[] {this.vehicle.bodyId(), groundId};
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

    double steerDegrees() {
        return this.steerDegrees;
    }

    double wheelRot(boolean left) {
        return left ? this.wheelRotLeft : this.wheelRotRight;
    }

    double wheelOffset(int i) {
        return this.wheelOffsets[i];
    }

    public int rollbacks() {
        return this.rollbacks;
    }

    void update(boolean simulating, boolean driven, boolean jacked, boolean stowed, int keys, Vec3 observedVelocity,
            double observedYawRate) {
        if (this.broken) {
            return;
        }
        this.driven = driven;
        boolean client = this.level.isClientSide;
        boolean proxyWanted = client && !simulating && this.nearLocalSimulator();
        Role wanted = decide(client, simulating, driven, jacked, proxyWanted);
        try {
            if (wanted != this.role || (wanted != Role.NONE && this.specRevision != VehicleSpecs.revision())) {
                this.switchTo(wanted, observedVelocity, observedYawRate);
            }
            if (this.vehicle != null && this.role == Role.SIMULATE) {
                this.vehicle.setDriven(driven);
                this.controls = driven && simulating ? controlsFor(keys, stowed) : JoltVehicle.Controls.IDLE;
                this.vehicle.setControls(this.controls);
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private boolean nearLocalSimulator() {
        for (TelJolt other : LOCAL_SIMULATORS) {
            if (other != this && other.level == this.level
                    && other.tel.distanceToSqr(this.tel) < PROXY_RANGE * PROXY_RANGE) {
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
        VehicleSpec spec = VehicleSpecs.current();
        boolean rebuild = this.vehicle != null && this.specRevision != VehicleSpecs.revision();
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
            this.vehicle = new JoltVehicle(this.world, spec, this.tel.getX(), this.tel.getY(), this.tel.getZ(),
                    this.tel.getYRot(), this.tel.getXRot(), this.tel.roll, needsWheels);
            this.ground = null;
            this.specRevision = VehicleSpecs.revision();
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
                    new LevelColumns(this.level, this.tel.softBlocksGiveWay()));
        }
        if (!needsGround && this.ground != null) {
            this.ground.close();
            this.ground = null;
        }
        switch (wanted) {
            case SIMULATE -> {
                if (previous == Role.FOLLOW || previous == Role.HOLD || previous == Role.NONE) {
                    this.vehicle.teleport(this.tel.getX(), this.tel.getY(), this.tel.getZ(), this.tel.getYRot(),
                            this.tel.getXRot(), this.tel.roll, null, null);
                    if (previous == Role.FOLLOW || previous == Role.HOLD) {
                        this.vehicle.setVelocity(velocity.x * Units.TICKS_PER_SECOND, velocity.y * Units.TICKS_PER_SECOND,
                                velocity.z * Units.TICKS_PER_SECOND, 0.0, yawRate, 0.0);
                    }
                }
                this.vehicle.setMode(JoltVehicle.Mode.DYNAMIC);
                if (this.level.isClientSide) {
                    LOCAL_SIMULATORS.add(this);
                }
            }
            case FOLLOW -> this.vehicle.setMode(JoltVehicle.Mode.KINEMATIC);
            case HOLD -> {
                this.vehicle.setMode(JoltVehicle.Mode.STATIC);
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

    private void releaseAll() {
        this.staged = null;
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
        LOGGER.warn("Jolt vehicle physics failed, the launcher goes back to the built-in physics: {}", error.toString());
        this.releaseAll();
        this.role = Role.NONE;
    }

    boolean failed() {
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
                case HOLD -> {
                    if (this.ground != null) {
                        this.ground.update(this.tel.getX(), this.tel.getY(), this.tel.getZ());
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException | LinkageError error) {
            this.fail(error);
        }
    }

    private void beforeSimulate() {
        if (this.rightingTicks > 0) {
            this.advanceRighting();
            return;
        }
        if (this.ground != null) {
            this.ground.update(this.tel.getX(), this.tel.getY(), this.tel.getZ());
        }
        if (this.hasWritten && this.movedByOthers()) {
            JoltVehicle.Snapshot s = this.vehicle.snapshot();
            this.vehicle.teleport(this.tel.getX(), this.tel.getY(), this.tel.getZ(), this.tel.getYRot(),
                    this.tel.getXRot(), this.tel.roll,
                    new com.github.stephengold.joltjni.Vec3((float) s.vx(), (float) s.vy(), (float) s.vz()),
                    new com.github.stephengold.joltjni.Vec3((float) s.wx(), (float) s.wy(), (float) s.wz()));
        }
    }

    private boolean movedByOthers() {
        double dx = this.tel.getX() - this.writtenX;
        double dy = this.tel.getY() - this.writtenY;
        double dz = this.tel.getZ() - this.writtenZ;
        double turned = Math.abs(PoseMath.wrapDegrees(this.tel.getYRot() - this.writtenYaw));
        return dx * dx + dy * dy + dz * dz > 0.0025 || turned > 0.5;
    }

    private void beforeFollow() {
        if (this.ground != null) {
            this.ground.update(this.tel.getX(), this.tel.getY(), this.tel.getZ());
        }
        this.vehicle.moveKinematic(this.tel.getX(), this.tel.getY(), this.tel.getZ(), this.tel.getYRot(),
                this.tel.getXRot(), this.tel.roll, TICK_SECONDS);
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
        double hit = this.vehicle.takeImpactSpeed();
        double normalY = this.vehicle.lastImpactNormalY();
        double landing = this.vehicle.takeLanding();
        if (hit / Units.TICKS_PER_SECOND >= 0.3 && Math.abs(normalY) < 0.7) {
            this.tel.reportJoltImpact(IskanderTelEntity.IMPACT_HORIZONTAL, hit / Units.TICKS_PER_SECOND);
        } else if (landing / Units.TICKS_PER_SECOND > 0.4) {
            this.tel.reportJoltImpact(IskanderTelEntity.IMPACT_VERTICAL, landing / Units.TICKS_PER_SECOND);
        } else if (hit / Units.TICKS_PER_SECOND > 0.4 && Math.abs(normalY) >= 0.7) {
            this.tel.reportJoltImpact(IskanderTelEntity.IMPACT_VERTICAL, hit / Units.TICKS_PER_SECOND);
        }
    }

    private void afterFollow() {
        JoltVehicle.Snapshot s = this.vehicle.snapshot();
        this.readGauges(s);
        if (this.level.isClientSide || !this.vehicle.wheeled() || this.ground == null) {
            return;
        }
        VehicleSpec spec = this.vehicle.spec();
        int now = this.tel.tickCount;
        double x = this.tel.getX();
        double y = this.tel.getY();
        double z = this.tel.getZ();
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
        if (tooFast || depth > spec.limits.groundDepthMetres) {
            String reason = tooFast
                    ? String.format(java.util.Locale.ROOT, "speed %.0f km/h", Units.msToKmh(speed))
                    : String.format(java.util.Locale.ROOT, "body %.2f m into the ground", depth);
            String driver = this.tel.getControllingPassenger() == null ? "nobody"
                    : this.tel.getControllingPassenger().getName().getString();
            String line = this.rollbackLog.note(now, this.tel.getId(), driver, reason);
            if (line != null) {
                LOGGER.info(line);
            }
            this.tel.rollBackTo(this.acceptedX, this.acceptedY, this.acceptedZ);
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
        double[][] r = PoseMath.rotation(this.tel.getYRot(), this.tel.getXRot(), this.tel.roll);
        double[] origin = PoseMath.bodyOrigin(this.tel.getX(), this.tel.getY(), this.tel.getZ(), this.tel.getYRot(),
                this.tel.getXRot(), this.tel.roll);
        for (double lz = -6.5; lz <= 6.6; lz += 3.25) {
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

    void startRighting() {
        if (this.vehicle == null || this.role != Role.SIMULATE) {
            return;
        }
        this.rightingTicks = RIGHTING_TICKS;
        this.rightingFromY = this.tel.getY();
        this.rightingFromPitch = this.tel.getXRot();
        this.rightingFromRoll = this.tel.roll;
        this.vehicle.setMode(JoltVehicle.Mode.KINEMATIC);
    }

    private void advanceRighting() {
        double done = 1.0 - (double) this.rightingTicks / RIGHTING_TICKS;
        double next = 1.0 - (double) (this.rightingTicks - 1) / RIGHTING_TICKS;
        double smooth = next * next * (3.0 - 2.0 * next);
        double lift = 2.5 * Math.sin(Math.PI * next);
        double y = this.rightingFromY + lift;
        double pitch = this.rightingFromPitch * (1.0 - smooth);
        double roll = this.rightingFromRoll * (1.0 - smooth);
        this.vehicle.moveKinematic(this.tel.getX(), y, this.tel.getZ(), this.tel.getYRot(), pitch, roll, TICK_SECONDS);
        this.stage(this.tel.getX(), y, this.tel.getZ(), this.tel.getYRot(), (float) pitch, (float) roll, Vec3.ZERO,
                false);
        this.rightingTicks--;
        if (this.rightingTicks == 0) {
            this.vehicle.setVelocity(0, 0, 0, 0, 0, 0);
            this.vehicle.setMode(JoltVehicle.Mode.DYNAMIC);
        }
    }

    private void stage(double x, double y, double z, float yaw, float pitch, float roll, Vec3 velocity,
            boolean grounded) {
        this.staged = new StagedPose(x, y, z, yaw, pitch, roll, velocity, grounded);
    }

    boolean applyStagedPose() {
        StagedPose pose = this.staged;
        if (pose == null) {
            return false;
        }
        this.staged = null;
        this.tel.applyJoltPose(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), pose.roll(), pose.velocity(),
                pose.grounded());
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
        }
    }

    void push(double ix, double iy, double iz) {
        if (this.vehicle != null && this.role == Role.SIMULATE && this.rightingTicks == 0) {
            this.vehicle.addImpulse(ix, iy, iz);
        }
    }

    boolean spinsFreely() {
        return this.vehicle != null && this.role == Role.SIMULATE;
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
        boolean skipSoft = this.tel.softBlocksGiveWay();
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
        if (this.vehicle != null) {
            this.vehicle.close();
            this.vehicle = null;
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
