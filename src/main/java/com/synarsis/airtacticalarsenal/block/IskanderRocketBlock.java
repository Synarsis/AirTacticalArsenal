package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class IskanderRocketBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Block.box(-2, 0, -2, 18, 48, 18);

    private static final VoxelShape COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public IskanderRocketBlock(BlockBehaviour.Properties properties) {
        super(properties.strength(-1.0F, 3600000.0F).noLootTable());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {

        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IskanderRocketBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.ISKANDER_ROCKET.get() 
            ? (lvl, pos, st, be) -> IskanderRocketBlockEntity.tick(lvl, pos, st, (IskanderRocketBlockEntity) be) 
            : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof IskanderRocketBlockEntity rocketEntity) {

                if (!rocketEntity.isLaunching()) {

                    releaseLauncher(level, pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void releaseLauncher(Level level, BlockPos rocketPos) {

        BlockPos launcherPos = rocketPos.below();
        BlockState state = level.getBlockState(launcherPos);
        if (state.getBlock() instanceof LauncherBlock launcher) {
            launcher.setLoaded(level, launcherPos, false);
        }
    }

    public static boolean placeRocket(Level level, BlockPos launcherCenterPos, Direction facing) {
        BlockPos rocketPos = launcherCenterPos.above();

        if (!level.getBlockState(rocketPos).isAir()) {
            return false;
        }

        BlockState rocketState = ModBlocks.ISKANDER_ROCKET.get().defaultBlockState().setValue(FACING, facing);
        level.setBlock(rocketPos, rocketState, Block.UPDATE_ALL);

        return true;
    }
}
