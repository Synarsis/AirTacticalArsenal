package com.synarsis.airtacticalarsenal.client.fx.light;

import com.synarsis.airtacticalarsenal.client.fx.light.EntityShadowMath.Box;
import com.synarsis.airtacticalarsenal.entity.patriot.M983TractorEntity;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpec;
import com.synarsis.airtacticalarsenal.vehicle.physics.VehicleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class EntityShadowBoxes {

    static final int PER_VEHICLE = 3;

    record Source(Vec3 position, double radius) {
    }

    private EntityShadowBoxes() {
    }

    static List<Box> collect(ClientLevel level, Minecraft mc, Vec3 cam, float partial, List<Source> sources,
            double originX, double originY, double originZ) {
        List<Candidate> found = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isSpectator() || entity.isInvisible()) {
                continue;
            }
            Vec3 at = new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                    Mth.lerp(partial, entity.zo, entity.getZ()));
            if (!near(sources, at, entity.getBbHeight() + 8.0) && !(isVehicle(entity) && near(sources, at, 20.0))) {
                continue;
            }
            double distance = at.distanceToSqr(cam);
            if (isVehicle(entity)) {
                found.add(new Candidate(distance, vehicleBoxes(entity, at, partial)));
            } else if (entity instanceof LivingEntity living && living.isAlive() && !riding(living)) {
                found.add(new Candidate(distance, List.of(livingBox(living, at))));
            }
        }
        found.sort(Comparator.comparingDouble(Candidate::distance));
        List<Box> out = new ArrayList<>();
        for (Candidate candidate : found) {
            if (out.size() + candidate.boxes().size() > EntityShadowMath.MAX_BOXES) {
                continue;
            }
            for (Box box : candidate.boxes()) {
                out.add(new Box(box.cx() - originX, box.cy() - originY, box.cz() - originZ, box.hx(), box.hy(),
                        box.hz(), box.axisX(), box.axisZ(), box.owner()));
            }
        }
        return out;
    }

    private record Candidate(double distance, List<Box> boxes) {
    }

    private static boolean near(List<Source> sources, Vec3 at, double slack) {
        for (Source s : sources) {
            double reach = s.radius() + slack;
            if (s.position().distanceToSqr(at) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVehicle(Entity entity) {
        return entity instanceof IskanderTelEntity || entity instanceof M983TractorEntity
                || entity instanceof PatriotVehicleEntity;
    }

    private static boolean riding(LivingEntity entity) {
        return isVehicle(entity.getRootVehicle());
    }

    private static Box livingBox(LivingEntity entity, Vec3 at) {
        AABB bb = entity.getBoundingBox().move(at.x - entity.getX(), at.y - entity.getY(), at.z - entity.getZ());
        return new Box(0.5 * (bb.minX + bb.maxX), 0.5 * (bb.minY + bb.maxY), 0.5 * (bb.minZ + bb.maxZ),
                0.5 * (bb.maxX - bb.minX), 0.5 * (bb.maxY - bb.minY), 0.5 * (bb.maxZ - bb.minZ), 1.0, 0.0,
                entity.getId());
    }

    private static List<Box> vehicleBoxes(Entity entity, Vec3 at, float partial) {
        VehicleSpec spec;
        if (entity instanceof IskanderTelEntity) {
            spec = VehicleSpecs.current();
        } else if (entity instanceof M983TractorEntity) {
            spec = VehicleSpecs.m983Tractor();
        } else {
            spec = VehicleSpecs.train(((PatriotVehicleEntity) entity).kind().specName()).trailer;
        }
        float yaw = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
        List<VehicleSpec.Box> biggest = new ArrayList<>(spec.body.boxes);
        biggest.sort(Comparator.comparingDouble((VehicleSpec.Box b) -> b.half[0] * b.half[1] * b.half[2]).reversed());
        Vec3 axis = TelGeometry.rotateToWorld(yaw, 0.0f, 0.0f, new Vec3(1.0, 0.0, 0.0));
        List<Box> out = new ArrayList<>();
        for (int i = 0; i < Math.min(PER_VEHICLE, biggest.size()); i++) {
            VehicleSpec.Box b = biggest.get(i);
            Vec3 centre = TelGeometry.localToWorld(at.x, at.y, at.z, yaw, 0.0f, 0.0f,
                    new Vec3(b.centre[0], b.centre[1], b.centre[2]));
            out.add(new Box(centre.x, centre.y, centre.z, b.half[0], b.half[1], b.half[2], axis.x, axis.z,
                    entity.getId()));
        }
        return out;
    }
}
