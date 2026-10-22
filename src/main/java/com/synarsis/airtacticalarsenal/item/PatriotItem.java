package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotGeometry;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotKind;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotPart;
import com.synarsis.airtacticalarsenal.entity.patriot.PatriotVehicleEntity;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelPart;
import com.synarsis.airtacticalarsenal.entity.tel.physics.Obb;
import com.synarsis.airtacticalarsenal.entity.tel.physics.ObbAabbSat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class PatriotItem extends Item {

    private static final String HEALTH_KEY = "PatriotHealth";
    private static final double CONTACT_SHRINK = 0.02;
    private static final double SUPPORT_STEP = 4.0;

    private final PatriotKind kind;

    public PatriotItem(Properties properties, PatriotKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PatriotKind kind() {
        return this.kind;
    }

    public static void writeHealth(ItemStack stack, float health) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putFloat(HEALTH_KEY, health));
    }

    public static float readHealth(ItemStack stack, float fallback) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(HEALTH_KEY)) {
            return fallback;
        }
        return data.copyTag().getFloat(HEALTH_KEY);
    }

    public static Obb footprint(double x, double y, double z, float yaw) {
        double centreZ = (PatriotGeometry.REAR_Z + PatriotGeometry.FRONT_Z) * 0.5;
        double halfZ = PatriotGeometry.HITCH_LENGTH * 0.5;
        double halfY = PatriotGeometry.HEIGHT * 0.5;
        return Obb.of(TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f, new Vec3(0.0, halfY, centreZ)),
                TelGeometry.rotateToWorld(yaw, 0.0f, 0.0f, new Vec3(1, 0, 0)),
                TelGeometry.rotateToWorld(yaw, 0.0f, 0.0f, new Vec3(0, 1, 0)),
                TelGeometry.rotateToWorld(yaw, 0.0f, 0.0f, new Vec3(0, 0, 1)),
                PatriotGeometry.HALF_WIDTH_WITH_FEET, halfY, halfZ);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Direction face = context.getClickedFace();
        BlockPos placePos = face == Direction.UP
                ? context.getClickedPos().above()
                : context.getClickedPos().relative(face);

        Vec3 click = context.getClickLocation();
        double x = face == Direction.UP ? click.x : placePos.getX() + 0.5;
        double y = face == Direction.UP ? click.y : placePos.getY();
        double z = face == Direction.UP ? click.z : placePos.getZ() + 0.5;
        float yaw = player.getYRot();

        String problem = placementProblem(serverLevel, player, x, y, z, yaw);
        if (problem != null) {
            player.displayClientMessage(Component.translatable(problem), true);
            return InteractionResult.FAIL;
        }

        EntityType<? extends PatriotVehicleEntity> type = this.kind.isLauncher()
                ? ModEntities.PATRIOT_LAUNCHER.get()
                : ModEntities.PATRIOT_RADAR.get();
        PatriotVehicleEntity vehicle = type.create(serverLevel);
        if (vehicle == null) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        vehicle.moveTo(x, y, z, yaw, 0.0f);
        vehicle.setOwnerUuid(player.getUUID());
        vehicle.setHealth(readHealth(stack, vehicle.getMaxHealth()));
        vehicle.isLegitSpawn = true;
        vehicle.placeOnSupports();
        vehicle.updateParts();
        serverLevel.addFreshEntity(vehicle);

        serverLevel.playSound(null, x, y, z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.8f, 0.6f);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    public static String placementProblem(ServerLevel level, Player placer, double x, double y, double z, float yaw) {
        if (y + PatriotGeometry.CULL_HEIGHT > level.getMaxBuildHeight()) {
            return "message.ata.patriot.no_room";
        }
        if (!hasSupport(level, x, y, z, yaw)) {
            return "message.ata.patriot.no_support";
        }
        if (!isFootprintFree(level, placer, x, y, z, yaw)) {
            return "message.ata.patriot.no_room";
        }
        return null;
    }

    private static boolean hasSupport(ServerLevel level, double x, double y, double z, float yaw) {
        int samples = 0;
        int supported = 0;
        for (double along = PatriotGeometry.REAR_Z + 0.5; along <= PatriotGeometry.FRONT_Z - 0.5;
                along += SUPPORT_STEP) {
            Vec3 p = TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f, new Vec3(0.0, 0.0, along));
            AABB probe = new AABB(p.x - 0.2, y - 0.6, p.z - 0.2, p.x + 0.2, y + 0.02, p.z + 0.2);
            samples++;
            if (!level.noCollision(probe)) {
                supported++;
            }
        }
        return supported * 4 >= samples * 3;
    }

    private static boolean isFootprintFree(ServerLevel level, Player placer, double x, double y, double z,
            float yaw) {
        Obb area = footprint(x, y + 0.05, z, yaw);
        AABB box = area.worldAabb();

        for (VoxelShape shape : level.getBlockCollisions(null, box)) {
            for (AABB block : shape.toAabbs()) {
                if (ObbAabbSat.mtv(area, block.deflate(CONTACT_SHRINK)) != null) {
                    return false;
                }
            }
        }

        for (Entity other : level.getEntities((Entity) null, box, e -> blocksPlacement(e, placer))) {
            if (ObbAabbSat.mtv(area, other.getBoundingBox().deflate(CONTACT_SHRINK)) != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean blocksPlacement(Entity e, Player placer) {
        if (e == placer || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof PatriotVehicleEntity || e instanceof PatriotPart
                || e instanceof IskanderTelEntity || e instanceof TelPart;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String key = "item.ata." + this.kind.id();
        tooltip.add(Component.translatable(key + ".tooltip.1"));
        tooltip.add(Component.translatable(key + ".tooltip.2"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.ata.patriot.tooltip.place"));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(HEALTH_KEY)) {
            tooltip.add(Component.translatable("item.ata.patriot.tooltip.health",
                    Math.round(data.copyTag().getFloat(HEALTH_KEY))));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
