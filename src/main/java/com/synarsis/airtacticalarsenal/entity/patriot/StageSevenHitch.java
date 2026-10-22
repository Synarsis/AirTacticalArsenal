package com.synarsis.airtacticalarsenal.entity.patriot;

import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.vehicle.physics.PoseMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record StageSevenHitch(double x, double y, double z, float yaw, float pitch, float roll, float bend,
        boolean tractorOut) {

    public static final double KINGPIN_Z = 5.875;
    public static final double TRACTOR_OFFSET_BLOCKS = 95.0 / 16.0;

    public static boolean isStageSeven(CompoundTag tag) {
        return tag.contains("Cycle", Tag.TAG_COMPOUND) && tag.getCompound("Cycle").contains("TractorOut");
    }

    public static StageSevenHitch read(PatriotVehicleEntity trailer, CompoundTag tag) {
        CompoundTag cycle = tag.getCompound("Cycle");
        return new StageSevenHitch(trailer.getX(), trailer.getY(), trailer.getZ(), trailer.getYRot(),
                trailer.getXRot(), tag.getFloat("Roll"), tag.getFloat("ArticulationYaw"),
                cycle.getBoolean("TractorOut"));
    }

    public double[] tractorPose() {
        double[][] r = PoseMath.rotation(this.yaw, this.pitch, this.roll);
        double[] origin = PoseMath.bodyOrigin(this.x, this.y, this.z, this.yaw, this.pitch, this.roll);
        double along = KINGPIN_Z + (this.tractorOut ? TRACTOR_OFFSET_BLOCKS : 0.0);
        double[] bodyOrigin = {origin[0] + r[0][2] * along, origin[1] + r[1][2] * along, origin[2] + r[2][2] * along};
        Vec3 position = M983Coupling.entityPosForBodyOrigin(bodyOrigin, this.yaw, this.pitch, this.roll);
        return new double[] {position.x, position.y, position.z, this.yaw, this.pitch, this.roll};
    }

    public static void migrate(ServerLevel level, PatriotVehicleEntity trailer, StageSevenHitch old) {
        M983TractorEntity tractor = ModEntities.M983_TRACTOR.get().create(level);
        if (tractor == null) {
            return;
        }
        double[] pose = old.tractorPose();
        tractor.isLegitSpawn = true;
        tractor.setOwnerUuid(trailer.getOwnerUuid());
        tractor.moveTo(pose[0], pose[1], pose[2], old.yaw(), old.pitch());
        tractor.setRoll(old.roll());
        level.addFreshEntity(tractor);
        if (old.tractorOut()) {
            if (!trailer.supportsOutNow()) {
                trailer.placeOnSupports();
            }
            return;
        }
        tractor.adoptTrailer(trailer, old.bend());
    }
}
