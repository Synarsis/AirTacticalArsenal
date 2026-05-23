package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.entity.ShahedLauncherEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OpenShahedTabletPacket;
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

public class ControlTabletItem extends Item {

    private static final String TAG_LAUNCHERS = "LinkedLaunchers";
    private static final String TAG_SAVED_COORDS = "SavedCoords";

    public static final double MAX_CONTROL_RANGE = 30.0;

    public static final int MAX_LAUNCHERS = 10;

    public ControlTabletItem(Properties properties) {
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

            List<BlockPos> validLaunchers = validateLaunchers(level, launcherPositions);
            if (validLaunchers.size() != launcherPositions.size()) {
                setLinkedLaunchers(stack, validLaunchers);
            }

            List<Boolean> hasDroneList = new ArrayList<>();
            List<BlockPos> targetList = new ArrayList<>();
            List<Float> launcherYawList = new ArrayList<>();
            List<List<BlockPos>> savedRoutes = new ArrayList<>();

            for (BlockPos pos : validLaunchers) {
                ShahedLauncherEntity launcher = findLauncherEntity(level, pos);
                if (launcher != null) {
                    hasDroneList.add(launcher.hasDrone());
                    targetList.add(launcher.getTargetPos());
                    launcherYawList.add(launcher.getYRot());
                    savedRoutes.add(launcher.hasSavedRoute() ? launcher.getSavedRoute() : new ArrayList<>());
                } else {
                    hasDroneList.add(false);
                    targetList.add(null);
                    launcherYawList.add(0.0f);
                    savedRoutes.add(new ArrayList<>());
                }
            }

            Map<Integer, int[]> savedCoords = getSavedCoords(stack);

            List<int[]> flyingShahedsData = new ArrayList<>();
            for (ShahedEntity shahed : level.getEntitiesOfClass(ShahedEntity.class, 
                    player.getBoundingBox().inflate(5000), e -> e.isAlive() && e.getOwner() == player)) {
                ShahedEntity.FlightPhase phase = shahed.getFlightPhase();
                flyingShahedsData.add(new int[]{
                    shahed.getId(),
                    (int) shahed.getX(),
                    (int) shahed.getZ(),
                    (int) shahed.getTargetAltitude(),
                    phase.ordinal()
                });
            }

            NetworkHandler.sendToPlayer(serverPlayer, new OpenShahedTabletPacket(
                validLaunchers, hasDroneList, targetList, launcherYawList, savedCoords, savedRoutes, flyingShahedsData
            ));

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    public static boolean addLinkedLauncher(ItemStack stack, ShahedLauncherEntity launcher) {
        List<BlockPos> launchers = getLinkedLaunchers(stack);
        BlockPos pos = launcher.blockPosition();

        for (BlockPos existing : launchers) {
            if (existing.equals(pos)) {
                return false; 
            }
        }

        if (launchers.size() >= MAX_LAUNCHERS) {
            return false; 
        }

        launchers.add(pos);
        setLinkedLaunchers(stack, launchers);
        return true;
    }

    public static boolean removeLinkedLauncher(ItemStack stack, BlockPos pos) {
        List<BlockPos> launchers = getLinkedLaunchers(stack);
        boolean removed = launchers.removeIf(p -> p.equals(pos));
        if (removed) {
            setLinkedLaunchers(stack, launchers);
        }
        return removed;
    }

    public static List<BlockPos> getLinkedLaunchers(ItemStack stack) {
        List<BlockPos> result = new ArrayList<>();
        CompoundTag tag = stack.getTag();

        if (tag != null && tag.contains(TAG_LAUNCHERS)) {
            ListTag listTag = tag.getList(TAG_LAUNCHERS, 10); 
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag posTag = listTag.getCompound(i);
                result.add(new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
                ));
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

    public static int getLauncherCount(ItemStack stack) {
        return getLinkedLaunchers(stack).size();
    }

    private List<BlockPos> validateLaunchers(Level level, List<BlockPos> positions) {
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos pos : positions) {
            ShahedLauncherEntity launcher = findLauncherEntity(level, pos);
            if (launcher != null && launcher.isAlive()) {
                valid.add(pos);
            }
        }
        return valid;
    }

    @Nullable
    public static ShahedLauncherEntity findLauncherEntity(Level level, BlockPos pos) {

        AABB searchBox = new AABB(pos).inflate(1.5);
        List<ShahedLauncherEntity> launchers = level.getEntitiesOfClass(
            ShahedLauncherEntity.class, 
            searchBox,
            entity -> entity.isAlive()
        );

        if (!launchers.isEmpty()) {

            Vec3 targetPos = Vec3.atCenterOf(pos);
            for (ShahedLauncherEntity launcher : launchers) {
                double dist = launcher.position().distanceTo(targetPos);
                if (dist <= 1.0) {
                    return launcher;
                }
            }

        }

        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        List<BlockPos> launchers = getLinkedLaunchers(stack);

        if (!launchers.isEmpty()) {
            tooltip.add(Component.literal("§aПривязано ПУ: §f" + launchers.size() + "/" + MAX_LAUNCHERS));
            for (int i = 0; i < Math.min(launchers.size(), 5); i++) {
                BlockPos pos = launchers.get(i);
                tooltip.add(Component.literal("§7  " + (i+1) + ". [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]"));
            }
            if (launchers.size() > 5) {
                tooltip.add(Component.literal("§7  ... и ещё " + (launchers.size() - 5)));
            }
        } else {
            tooltip.add(Component.literal("§7Нет привязанных ПУ"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§eПКМ по ПУ§7 - привязать ПУ"));
        tooltip.add(Component.literal("§eПКМ§7 - открыть управление"));
        tooltip.add(Component.literal("§eShift + ПКМ§7 - сбросить все привязки"));
    }

    @Override
    public boolean isFoil(ItemStack stack) {

        return false;
    }
}
