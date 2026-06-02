package com.synarsis.airtacticalarsenal.item;

import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.UnifiedTerminalBlock;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.UnifiedTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class LaunchInterfaceCableItem extends Item {

    public static final int MAX_CABLE_DISTANCE = 60;

    public LaunchInterfaceCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player == null) return InteractionResult.PASS;

        if (state.getBlock() instanceof LauncherBlock) {

            BlockPos centerPos = findLauncherCenter(level, pos, state);
            if (centerPos == null) {
                if (!level.isClientSide) {
                    player.sendSystemMessage(Component.literal("§cОшибка: Не удалось найти центр ПУ!"));
                }
                return InteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                stack.set(ModDataComponents.REGISTERED_LAUNCHER.get(), centerPos);
                stack.set(ModDataComponents.REGISTERED_LAUNCHER_DIMENSION.get(),
                        level.dimension().location().toString());

                player.sendSystemMessage(Component.literal("§aПУ зарегистрирована: ["
                        + centerPos.getX() + ", " + centerPos.getY() + ", " + centerPos.getZ() + "]"));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof UnifiedTerminalBlock) {
            BlockPos launcherPos = stack.get(ModDataComponents.REGISTERED_LAUNCHER.get());

            if (launcherPos == null) {
                if (!level.isClientSide) {
                    player.sendSystemMessage(Component.literal("§cСначала зарегистрируйте ПУ (ПКМ по пусковой установке)!"));
                }
                return InteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                double distance = Math.sqrt(pos.distSqr(launcherPos));

                if (distance > MAX_CABLE_DISTANCE) {
                    player.sendSystemMessage(Component.literal(
                            "§cОшибка: Слишком далеко! Расстояние: " + (int) distance + " блоков (макс. " + MAX_CABLE_DISTANCE + ")"));
                    return InteractionResult.FAIL;
                }

                BlockState launcherState = level.getBlockState(launcherPos);
                if (!(launcherState.getBlock() instanceof LauncherBlock)) {
                    player.sendSystemMessage(Component.literal("§cОшибка: ПУ по сохранённым координатам не найдена!"));
                    clearCableData(stack);
                    return InteractionResult.FAIL;
                }

                BlockEntity launcherBE = level.getBlockEntity(launcherPos);
                if (launcherBE instanceof LauncherBlockEntity launcherEntity) {
                    if (launcherEntity.hasConnectedTerminal()) {
                        BlockPos existingTerminal = launcherEntity.getConnectedTerminal();
                        if (existingTerminal != null && !existingTerminal.equals(pos)) {
                            player.sendSystemMessage(Component.literal("§cОшибка: Эта ПУ уже подключена к другому терминалу ["
                                    + existingTerminal.getX() + ", " + existingTerminal.getY() + ", " + existingTerminal.getZ() + "]!"));
                            clearCableData(stack);
                            return InteractionResult.FAIL;
                        }
                    }
                }

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof UnifiedTerminalBlockEntity terminal) {
                    if (terminal.isLauncherConnected(launcherPos)) {
                        player.sendSystemMessage(Component.literal("§eЭта ПУ уже подключена к этому терминалу!"));
                    } else {
                        terminal.addConnectedLauncher(launcherPos);

                        if (launcherBE instanceof LauncherBlockEntity launcherEntity2) {
                            launcherEntity2.setConnectedTerminal(pos);
                        }
                        player.sendSystemMessage(Component.literal("§aПУ подключена к терминалу успешно!"));
                        player.sendSystemMessage(Component.literal("§7Подключено ПУ: " + terminal.getConnectedLauncherCount()));
                    }

                    clearCableData(stack);
                } else {
                    player.sendSystemMessage(Component.literal("§cОшибка: Терминал повреждён!"));
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    private void clearCableData(ItemStack stack) {
        stack.remove(ModDataComponents.REGISTERED_LAUNCHER.get());
        stack.remove(ModDataComponents.REGISTERED_LAUNCHER_DIMENSION.get());
    }

    @Nullable
    private BlockPos findLauncherCenter(Level level, BlockPos clickedPos, BlockState clickedState) {
        if (!(clickedState.getBlock() instanceof LauncherBlock)) {
            return null;
        }

        LauncherBlock.LauncherPart part = clickedState.getValue(LauncherBlock.PART);

        if (part == LauncherBlock.LauncherPart.CENTER) {
            return clickedPos;
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = clickedPos.offset(x, 0, z);
                BlockState checkState = level.getBlockState(checkPos);
                if (checkState.getBlock() instanceof LauncherBlock) {
                    if (checkState.getValue(LauncherBlock.PART) == LauncherBlock.LauncherPart.CENTER) {
                        return checkPos;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos launcherPos = stack.get(ModDataComponents.REGISTERED_LAUNCHER.get());
        if (launcherPos != null) {
            tooltip.add(Component.literal("§7Зарегистрирована ПУ:"));
            tooltip.add(Component.literal("§a[" + launcherPos.getX()
                    + ", " + launcherPos.getY()
                    + ", " + launcherPos.getZ() + "]"));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("§7Shift + ПКМ по терминалу - подключить"));
        } else {
            tooltip.add(Component.literal("§7ПКМ по ПУ - зарегистрировать"));
            tooltip.add(Component.literal("§7Shift + ПКМ по терминалу - подключить"));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("§8Макс. дистанция: " + MAX_CABLE_DISTANCE + " блоков"));
        }
    }
}
