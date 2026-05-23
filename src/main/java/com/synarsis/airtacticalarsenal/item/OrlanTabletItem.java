package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.entity.OrlanLauncherEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OpenOrlanTabletPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrlanTabletItem extends Item {

    private static final String TAG_LAUNCHERS = "LinkedLaunchers";
    private static final String TAG_SAVED_COORDS = "SavedCoords";
    private static final String TAG_LINKED_ORLAN = "LinkedOrlanId";
    private static final String TAG_CAMERA_MODE = "CameraMode";

    public static final double MAX_CONTROL_RANGE = 30.0;
    public static final int MAX_LAUNCHERS = 10;

    public OrlanTabletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            List<BlockPos> launchers = getLinkedLaunchers(stack);
            if (!launchers.isEmpty()) {
                clearAllLaunchers(stack);
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("§eВсе привязки ПУ сброшены"), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            List<BlockPos> launcherPositions = getLinkedLaunchers(stack);

            List<BlockPos> validLaunchers = launcherPositions.isEmpty() 
                ? new ArrayList<>() 
                : validateLaunchers(level, launcherPositions);
            if (validLaunchers.size() != launcherPositions.size()) {
                setLinkedLaunchers(stack, validLaunchers);
            }

            List<Boolean> hasDroneList = new ArrayList<>();
            List<Float> launcherYawList = new ArrayList<>();

            for (BlockPos pos : validLaunchers) {
                OrlanLauncherEntity launcher = findOrlanLauncherEntity(level, pos);
                if (launcher != null) {
                    hasDroneList.add(launcher.hasDrone());
                    launcherYawList.add(launcher.getYRot());
                } else {
                    hasDroneList.add(false);
                    launcherYawList.add(0.0f);
                }
            }

            List<int[]> activeOrlans = new ArrayList<>();
            List<OrlanEntity> orlans = OrlanEntity.getActiveOrlansByOwner(player.getUUID());
            for (OrlanEntity orlan : orlans) {
                BlockPos tp = orlan.getTargetPos();
                int remainingTicks = orlan.getRemainingFlightTicks();
                activeOrlans.add(new int[]{orlan.getId(), tp.getX(), tp.getZ(), remainingTicks});
            }

            Map<Integer, int[]> savedCoords = getSavedCoords(stack);

            List<List<BlockPos>> savedRoutes = new ArrayList<>();
            for (BlockPos pos : validLaunchers) {
                OrlanLauncherEntity launcher = findOrlanLauncherEntity(level, pos);
                if (launcher != null && launcher.hasSavedRoute()) {
                    savedRoutes.add(new ArrayList<>(launcher.getSavedRoute()));
                } else {
                    savedRoutes.add(new ArrayList<>());
                }
            }

            NetworkHandler.sendToPlayer(serverPlayer, new OpenOrlanTabletPacket(
                    validLaunchers, hasDroneList, launcherYawList, savedCoords, activeOrlans, savedRoutes
            ));

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    public static boolean addLinkedLauncher(ItemStack stack, OrlanLauncherEntity launcher) {
        List<BlockPos> launchers = getLinkedLaunchers(stack);
        BlockPos pos = launcher.blockPosition();
        for (BlockPos existing : launchers) {
            if (existing.equals(pos)) return false;
        }
        if (launchers.size() >= MAX_LAUNCHERS) return false;
        launchers.add(pos);
        setLinkedLaunchers(stack, launchers);
        return true;
    }

    public static boolean removeLinkedLauncher(ItemStack stack, BlockPos pos) {
        List<BlockPos> launchers = getLinkedLaunchers(stack);
        boolean removed = launchers.removeIf(p -> p.equals(pos));
        if (removed) setLinkedLaunchers(stack, launchers);
        return removed;
    }

    public static List<BlockPos> getLinkedLaunchers(ItemStack stack) {
        List<BlockPos> result = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_LAUNCHERS)) {
            ListTag listTag = tag.getList(TAG_LAUNCHERS, 10);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag posTag = listTag.getCompound(i);
                result.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
            }
        }
        return result;
    }

    public static void setLinkedLaunchers(ItemStack stack, List<BlockPos> launchers) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag listTag = new ListTag();
        for (BlockPos pos : launchers) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            listTag.add(posTag);
        }
        tag.put(TAG_LAUNCHERS, listTag);
    }

    public static void clearAllLaunchers(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_LAUNCHERS);
            tag.remove(TAG_SAVED_COORDS);
        }
    }

    public static Map<Integer, int[]> getSavedCoords(ItemStack stack) {
        Map<Integer, int[]> result = new HashMap<>();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_SAVED_COORDS)) {
            CompoundTag coordsTag = tag.getCompound(TAG_SAVED_COORDS);
            for (String key : coordsTag.getAllKeys()) {
                try {
                    int index = Integer.parseInt(key);
                    int[] coords = coordsTag.getIntArray(key);
                    if (coords.length >= 2) {
                        result.put(index, new int[]{coords[0], coords[1]});
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    public static void setSavedCoords(ItemStack stack, Map<Integer, int[]> coords) {
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag coordsTag = new CompoundTag();
        for (Map.Entry<Integer, int[]> entry : coords.entrySet()) {
            coordsTag.putIntArray(String.valueOf(entry.getKey()), entry.getValue());
        }
        tag.put(TAG_SAVED_COORDS, coordsTag);
    }

    public static int getLinkedOrlanId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getInt(TAG_LINKED_ORLAN) : -1;
    }

    public static void setLinkedOrlanId(ItemStack stack, int entityId) {
        stack.getOrCreateTag().putInt(TAG_LINKED_ORLAN, entityId);
    }

    public static boolean isCameraMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_CAMERA_MODE);
    }

    public static void setCameraMode(ItemStack stack, boolean mode) {
        stack.getOrCreateTag().putBoolean(TAG_CAMERA_MODE, mode);
    }

    private List<BlockPos> validateLaunchers(Level level, List<BlockPos> positions) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : positions) {
            OrlanLauncherEntity launcher = findOrlanLauncherEntity(level, pos);
            if (launcher != null && launcher.isAlive()) {
                valid.add(pos);
            }
        }
        return valid;
    }

    public static OrlanLauncherEntity findOrlanLauncherEntity(Level level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(2.0);
        List<OrlanLauncherEntity> launchers = level.getEntitiesOfClass(
                OrlanLauncherEntity.class, searchBox);
        if (!launchers.isEmpty()) {
            return launchers.get(0);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        List<BlockPos> launchers = getLinkedLaunchers(stack);

        tooltip.add(Component.literal("§b✈ Планшет разведки Орлан-10"));
        tooltip.add(Component.literal(""));

        if (!launchers.isEmpty()) {
            tooltip.add(Component.literal("§aПривязано ПУ: §f" + launchers.size() + "/" + MAX_LAUNCHERS));
            for (int i = 0; i < Math.min(launchers.size(), 3); i++) {
                BlockPos pos = launchers.get(i);
                tooltip.add(Component.literal("§7  " + (i + 1) + ". [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]"));
            }
            if (launchers.size() > 3) {
                tooltip.add(Component.literal("§7  ... и ещё " + (launchers.size() - 3)));
            }
        } else {
            tooltip.add(Component.literal("§7Нет привязанных ПУ"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§eПКМ по ПУ§7 - привязать ПУ"));
        tooltip.add(Component.literal("§eПКМ§7 - управление / камера"));
        tooltip.add(Component.literal("§eShift + ПКМ§7 - сбросить привязки"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isCameraMode(stack);
    }
}
