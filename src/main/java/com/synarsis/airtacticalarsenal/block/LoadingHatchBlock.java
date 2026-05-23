package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.block.entity.LoadingHatchBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.item.IskanderItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class LoadingHatchBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    public LoadingHatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoadingHatchBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof LoadingHatchBlockEntity hatch)) return InteractionResult.FAIL;

        ItemStack heldItem = player.getItemInHand(hand);
        BlockPos launcherPos = hatch.getParentLauncher();

        if (launcherPos == null) {
            player.sendSystemMessage(Component.literal("§cОшибка: ПУ не найдена!"));
            return InteractionResult.FAIL;
        }

        if (heldItem.getItem() instanceof IskanderItem) {
            if (hatch.isLoaded()) {
                player.sendSystemMessage(Component.literal("§cПУ уже загружена!"));
                return InteractionResult.FAIL;
            }

            BlockEntity launcherBECheck = world.getBlockEntity(launcherPos);
            if (launcherBECheck instanceof LauncherBlockEntity launcherCheck && launcherCheck.isInFlight()) {
                player.sendSystemMessage(Component.literal("§cС этой ПУ уже запущен Искандер!"));
                return InteractionResult.FAIL;
            }

            BlockState launcherState = world.getBlockState(launcherPos);
            if (launcherState.getBlock() instanceof LauncherBlock launcher) {
                if (launcher.isBlocked(world, launcherPos)) {
                    player.sendSystemMessage(Component.literal("§cПУ заблокирована! Уберите препятствия сверху."));
                    return InteractionResult.FAIL;
                }
            }

            ItemStack rocketCopy = heldItem.copy();
            rocketCopy.setCount(1);
            hatch.loadRocket(rocketCopy);

            heldItem.shrink(1);

            player.sendSystemMessage(Component.literal("§aРакета загружена в ПУ [" 
                + launcherPos.getX() + ", " + launcherPos.getY() + ", " + launcherPos.getZ() + "]"));

            BlockState launcherState2 = world.getBlockState(launcherPos);
            if (launcherState2.getBlock() instanceof LauncherBlock launcher) {
                Direction facing = launcherState2.getValue(LauncherBlock.FACING);

                IskanderRocketBlock.placeRocket(world, launcherPos, facing);

                launcher.setLoaded(world, launcherPos, true);
            }

            BlockEntity launcherBE = world.getBlockEntity(launcherPos);
            if (launcherBE instanceof LauncherBlockEntity launcherEntity) {
                launcherEntity.setRocketLoaded(true);
            }

            return InteractionResult.SUCCESS;
        }

        if (heldItem.isEmpty()) {
            if (!hatch.isLoaded()) {
                player.sendSystemMessage(Component.literal("§eПУ пуста. Загрузите ракету."));
                return InteractionResult.FAIL;
            }

            BlockPos rocketPos = launcherPos.above();
            BlockEntity rocketBE = world.getBlockEntity(rocketPos);
            if (rocketBE instanceof com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity rocketEntity) {
                if (rocketEntity.isLaunching()) {
                    player.sendSystemMessage(Component.literal("§cНельзя выгрузить ракету во время запуска!"));
                    return InteractionResult.FAIL;
                }
            }

            ItemStack rocket = hatch.unloadRocket();
            if (!player.getInventory().add(rocket)) {
                player.drop(rocket, false);
            }

            player.sendSystemMessage(Component.literal("§aРакета выгружена из ПУ [" 
                + launcherPos.getX() + ", " + launcherPos.getY() + ", " + launcherPos.getZ() + "]"));

            BlockPos rocketBlockPos = launcherPos.above();
            BlockState rocketState = world.getBlockState(rocketBlockPos);
            if (rocketState.getBlock() instanceof IskanderRocketBlock) {
                world.removeBlock(rocketBlockPos, false);
            }

            BlockState launcherState = world.getBlockState(launcherPos);
            if (launcherState.getBlock() instanceof LauncherBlock launcher) {
                launcher.setLoaded(world, launcherPos, false);
            }

            BlockEntity launcherBE = world.getBlockEntity(launcherPos);
            if (launcherBE instanceof LauncherBlockEntity launcherEntity) {
                launcherEntity.setRocketLoaded(false);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, 
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !world.isClientSide) {

            if (StructureRemovalHelper.isRemoving(pos)) {

                super.onRemove(state, world, pos, newState, isMoving);
                return;
            }

            StructureRemovalHelper.startRemoving(pos);

            try {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof LoadingHatchBlockEntity hatch) {

                    BlockPos launcherPos = hatch.getParentLauncher();
                    if (launcherPos != null) {
                        BlockState launcherState = world.getBlockState(launcherPos);
                        if (launcherState.getBlock() instanceof LauncherBlock) {

                            world.destroyBlock(launcherPos, true);
                        }
                    }
                }
            } finally {
                StructureRemovalHelper.finishRemoving(pos);
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
