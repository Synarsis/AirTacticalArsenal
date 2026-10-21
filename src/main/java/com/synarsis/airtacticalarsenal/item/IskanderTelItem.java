package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.entity.ModEntities;
import com.synarsis.airtacticalarsenal.entity.tel.IskanderTelEntity;
import com.synarsis.airtacticalarsenal.entity.tel.TelGeometry;
import com.synarsis.airtacticalarsenal.entity.tel.TelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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

import java.util.List;

public class IskanderTelItem extends Item {

    private static final String HEALTH_KEY = "TelHealth";

    private static final double CONTACT_SHRINK = 0.02;

    public IskanderTelItem(Properties properties) {
        super(properties);
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

        if (y + TelGeometry.CULL_HEIGHT > serverLevel.getMaxBuildHeight()) {
            player.displayClientMessage(Component.translatable("message.ata.tel.no_room"), true);
            return InteractionResult.FAIL;
        }
        if (!hasSupport(serverLevel, x, y, z, yaw)) {
            player.displayClientMessage(Component.translatable("message.ata.tel.no_support"), true);
            return InteractionResult.FAIL;
        }
        if (!isHullFree(serverLevel, player, x, y, z, yaw)) {
            player.displayClientMessage(Component.translatable("message.ata.tel.no_room"), true);
            return InteractionResult.FAIL;
        }

        IskanderTelEntity tel = ModEntities.ISKANDER_TEL.get().create(serverLevel);
        if (tel == null) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        tel.moveTo(x, y, z, yaw, 0.0f);
        tel.setOwnerUuid(player.getUUID());
        tel.setHealth(readHealth(stack, tel.getMaxHealth()));
        tel.isLegitSpawn = true;
        tel.updateParts();
        serverLevel.addFreshEntity(tel);

        serverLevel.playSound(null, x, y, z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.8f, 0.6f);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean hasSupport(ServerLevel level, double x, double y, double z, float yaw) {
        int supported = 0;
        for (double along : new double[] {-6.0, 0.0, 6.0}) {
            Vec3 p = TelGeometry.localToWorld(x, y, z, yaw, 0.0f, 0.0f, new Vec3(0.0, 0.0, along));
            AABB probe = new AABB(p.x - 0.2, y - 0.6, p.z - 0.2, p.x + 0.2, y + 0.02, p.z + 0.2);
            if (!level.noCollision(probe)) {
                supported++;
            }
        }
        return supported >= 2;
    }

    private static boolean isHullFree(ServerLevel level, Player placer, double x, double y, double z, float yaw) {
        AABB area = TelGeometry.obbWorldAabb(x, y, z, yaw, 0.0f, 0.0f);

        for (VoxelShape shape : level.getBlockCollisions(null, area)) {
            for (AABB box : shape.toAabbs()) {
                if (TelGeometry.obbIntersectsAabb(x, y, z, yaw, 0.0f, 0.0f, box, CONTACT_SHRINK)) {
                    return false;
                }
            }
        }

        for (Entity other : level.getEntities((Entity) null, area, e -> blocksPlacement(e, placer))) {
            if (TelGeometry.obbIntersectsAabb(x, y, z, yaw, 0.0f, 0.0f, other.getBoundingBox(), CONTACT_SHRINK)) {
                return false;
            }
        }
        return true;
    }

    private static boolean blocksPlacement(Entity e, Player placer) {
        if (e == placer || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof IskanderTelEntity || e instanceof TelPart;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ata.iskander_tel.tooltip.1"));
        tooltip.add(Component.translatable("item.ata.iskander_tel.tooltip.2"));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("item.ata.iskander_tel.tooltip.place"));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(HEALTH_KEY)) {
            tooltip.add(Component.translatable("item.ata.iskander_tel.tooltip.health",
                    Math.round(data.copyTag().getFloat(HEALTH_KEY))));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
