package com.synarsis.airtacticalarsenal.entity.patriot;

import com.mojang.logging.LogUtils;
import com.synarsis.airtacticalarsenal.vehicle.physics.JoltVehicle;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorld;
import com.synarsis.airtacticalarsenal.vehicle.physics.PhysicsWorlds;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class TrailerJolt implements PhysicsWorlds.Participant {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PatriotVehicleEntity entity;
    private final Level level;
    private PhysicsWorld world;
    private JoltVehicle body;
    private boolean joined;
    private boolean broken;
    private double writtenX;
    private double writtenY;
    private double writtenZ;
    private float writtenYaw;
    private float writtenPitch;
    private float writtenRoll;

    TrailerJolt(PatriotVehicleEntity entity) {
        this.entity = entity;
        this.level = entity.level();
    }

    public boolean active() {
        return this.body != null;
    }

    public boolean failed() {
        return this.broken;
    }

    public int[] bodyIds() {
        return this.body == null ? new int[0] : new int[] {this.body.bodyId()};
    }

    public JoltVehicle body() {
        return this.body;
    }

    void tick() {
        if (this.broken || this.level.isClientSide || !PhysicsWorlds.joltWanted()) {
            this.close();
            return;
        }
        try {
            if (this.body == null) {
                this.create();
            } else if (this.moved()) {
                this.place();
            }
        } catch (RuntimeException | LinkageError error) {
            this.broken = true;
            LOGGER.warn("Jolt physics of the Patriot trailer failed, it stays where it is: {}", error.toString());
            this.close();
        }
    }

    private void create() {
        VehicleSpec spec = VehicleSpecs.train(this.entity.kind().specName()).trailer;
        this.world = PhysicsWorlds.obtain(this.level);
        this.body = new JoltVehicle(this.world, spec, this.entity.getX(), this.entity.getY(), this.entity.getZ(),
                this.entity.getYRot(), this.entity.getXRot(), this.entity.getRoll(), false);
        this.body.setMode(JoltVehicle.Mode.STATIC);
        this.joined = true;
        PhysicsWorlds.join(this.level, this);
        this.remember();
    }

    private boolean moved() {
        double dx = this.entity.getX() - this.writtenX;
        double dy = this.entity.getY() - this.writtenY;
        double dz = this.entity.getZ() - this.writtenZ;
        return dx * dx + dy * dy + dz * dz > 1.0E-6
                || Math.abs(PoseMath.wrapDegrees(this.entity.getYRot() - this.writtenYaw)) > 0.01
                || Math.abs(this.entity.getXRot() - this.writtenPitch) > 0.01
                || Math.abs(this.entity.getRoll() - this.writtenRoll) > 0.01;
    }

    private void place() {
        double[] origin = PoseMath.bodyOrigin(this.entity.getX(), this.entity.getY(), this.entity.getZ(),
                this.entity.getYRot(), this.entity.getXRot(), this.entity.getRoll());
        double[][] rotation = PoseMath.rotation(this.entity.getYRot(), this.entity.getXRot(), this.entity.getRoll());
        this.body.teleportBody(origin, rotation, null, null);
        this.remember();
    }

    private void remember() {
        this.writtenX = this.entity.getX();
        this.writtenY = this.entity.getY();
        this.writtenZ = this.entity.getZ();
        this.writtenYaw = this.entity.getYRot();
        this.writtenPitch = this.entity.getXRot();
        this.writtenRoll = this.entity.getRoll();
    }

    void close() {
        if (this.body != null) {
            this.body.close();
            this.body = null;
        }
        if (this.joined) {
            PhysicsWorlds.leave(this.level, this);
            this.joined = false;
        }
    }

    @Override
    public void beforeStep() {
    }

    @Override
    public void afterStep() {
    }

    @Override
    public void shutdown() {
        this.close();
    }
}
