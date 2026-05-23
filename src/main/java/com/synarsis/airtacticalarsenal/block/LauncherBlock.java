package com.synarsis.airtacticalarsenal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.util.StringRepresentable;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.LoadingHatchBlockEntity;

import javax.annotation.Nullable;

public class LauncherBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE_FULL = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape SHAPE_STAIR_BOTTOM = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape SHAPE_STAIR_TOP_NORTH = Block.box(0, 8, 8, 16, 16, 16);
    private static final VoxelShape SHAPE_STAIR_TOP_SOUTH = Block.box(0, 8, 0, 16, 16, 8);
    private static final VoxelShape SHAPE_STAIR_TOP_EAST = Block.box(0, 8, 0, 8, 16, 16);
    private static final VoxelShape SHAPE_STAIR_TOP_WEST = Block.box(8, 8, 0, 16, 16, 16);

    private static final VoxelShape SHAPE_STAIR_NORTH = Shapes.or(SHAPE_STAIR_BOTTOM, SHAPE_STAIR_TOP_NORTH);
    private static final VoxelShape SHAPE_STAIR_SOUTH = Shapes.or(SHAPE_STAIR_BOTTOM, SHAPE_STAIR_TOP_SOUTH);
    private static final VoxelShape SHAPE_STAIR_EAST = Shapes.or(SHAPE_STAIR_BOTTOM, SHAPE_STAIR_TOP_EAST);
    private static final VoxelShape SHAPE_STAIR_WEST = Shapes.or(SHAPE_STAIR_BOTTOM, SHAPE_STAIR_TOP_WEST);

    private static final VoxelShape SHAPE_CORNER_NW = Shapes.or(SHAPE_STAIR_BOTTOM, Block.box(8, 8, 8, 16, 16, 16));
    private static final VoxelShape SHAPE_CORNER_NE = Shapes.or(SHAPE_STAIR_BOTTOM, Block.box(0, 8, 8, 8, 16, 16));
    private static final VoxelShape SHAPE_CORNER_SW = Shapes.or(SHAPE_STAIR_BOTTOM, Block.box(8, 8, 0, 16, 16, 8));
    private static final VoxelShape SHAPE_CORNER_SE = Shapes.or(SHAPE_STAIR_BOTTOM, Block.box(0, 8, 0, 8, 16, 8));

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        LauncherPart part = state.getValue(PART);
        return switch (part) {
            case STAIR_NORTH -> SHAPE_STAIR_NORTH;
            case STAIR_SOUTH -> SHAPE_STAIR_SOUTH;
            case STAIR_EAST -> SHAPE_STAIR_EAST;
            case STAIR_WEST -> SHAPE_STAIR_WEST;
            case STAIR_CORNER_NW -> SHAPE_CORNER_NW;
            case STAIR_CORNER_NE -> SHAPE_CORNER_NE;
            case STAIR_CORNER_SW -> SHAPE_CORNER_SW;
            case STAIR_CORNER_SE -> SHAPE_CORNER_SE;
            default -> SHAPE_FULL;
        };
    }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<LauncherPart> PART = EnumProperty.create("part", LauncherPart.class);
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    public static final BooleanProperty LAUNCHING = BooleanProperty.create("launching");

    public LauncherBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, LauncherPart.CENTER)
                .setValue(LOADED, false)
                .setValue(LAUNCHING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LOADED, LAUNCHING);
    }

    private static final int MIN_LAUNCHER_DISTANCE = 10;

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();

        if (!canPlaceMultiblock(level, pos, facing)) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.ata.launcher.no_space"), true);
            }
            return null;
        }

        if (isNearOtherLauncher(level, pos)) {
            if (!level.isClientSide && player != null) {
                player.displayClientMessage(Component.translatable("message.ata.launcher.too_close", MIN_LAUNCHER_DISTANCE), true);
            }
            return null;
        }

        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(PART) == LauncherPart.CENTER) {
            Direction facing = state.getValue(FACING);
            createMultiblock(level, pos, facing);
        }
    }

    private boolean canPlaceMultiblock(Level level, BlockPos centerPos, Direction facing) {

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                BlockPos checkPos = centerPos.offset(x, 0, z);
                BlockState checkState = level.getBlockState(checkPos);
                if (!isReplaceable(checkState)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isReplaceable(BlockState state) {
        if (state.isAir()) return true;
        if (state.canBeReplaced()) return true;
        Block block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN || 
            block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH || block == Blocks.SEAGRASS ||
            block == Blocks.TALL_SEAGRASS || block == Blocks.VINE || block == Blocks.SNOW ||
            block == Blocks.FLOWER_POT) {
            return true;
        }
        if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE)) {
            return true;
        }
        return false;
    }

    private java.util.List<BlockPos> getAll5x5Positions(BlockPos centerPos) {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x != 0 || z != 0) {
                    positions.add(centerPos.offset(x, 0, z));
                }
            }
        }
        return positions;
    }

    private void createMultiblock(Level level, BlockPos centerPos, Direction facing) {

        BlockPos hatchPos = centerPos.relative(facing, 1);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue; 

                BlockPos pos = centerPos.offset(x, 0, z);

                if (pos.equals(hatchPos)) continue;

                LauncherPart part = getPartForOffset(x, z);

                level.setBlock(pos, this.defaultBlockState()
                        .setValue(FACING, facing)
                        .setValue(PART, part)
                        .setValue(LOADED, false)
                        .setValue(LAUNCHING, false), 2);
            }
        }

        level.setBlock(hatchPos, ModBlocks.LOADING_HATCH.get().defaultBlockState()
                .setValue(LoadingHatchBlock.FACING, facing.getOpposite()) 
                .setValue(LoadingHatchBlock.LOADED, false), 2);

        BlockEntity hatchBE = level.getBlockEntity(hatchPos);
        if (hatchBE instanceof LoadingHatchBlockEntity hatch) {
            hatch.setParentLauncher(centerPos);
        }

        BlockEntity launcherBE = level.getBlockEntity(centerPos);
        if (launcherBE instanceof LauncherBlockEntity launcher) {
            launcher.setLoadingHatch(hatchPos);
        }

        level.sendBlockUpdated(centerPos, level.getBlockState(centerPos), level.getBlockState(centerPos), 3);
        level.sendBlockUpdated(hatchPos, level.getBlockState(hatchPos), level.getBlockState(hatchPos), 3);
    }

    private LauncherPart getPartForOffset(int x, int z) {
        boolean isOuter = Math.abs(x) == 2 || Math.abs(z) == 2;

        if (!isOuter) {
            return LauncherPart.PLATFORM;
        }

        if (x == -2 && z == -2) return LauncherPart.STAIR_CORNER_NW;
        if (x == 2 && z == -2) return LauncherPart.STAIR_CORNER_NE;
        if (x == -2 && z == 2) return LauncherPart.STAIR_CORNER_SW;
        if (x == 2 && z == 2) return LauncherPart.STAIR_CORNER_SE;

        if (z == -2) return LauncherPart.STAIR_NORTH;
        if (z == 2) return LauncherPart.STAIR_SOUTH;
        if (x == -2) return LauncherPart.STAIR_WEST;
        if (x == 2) return LauncherPart.STAIR_EAST;

        return LauncherPart.PLATFORM;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {

            if (StructureRemovalHelper.isRemoving(pos)) {
                super.onRemove(state, level, pos, newState, isMoving);
                return;
            }

            LauncherPart part = state.getValue(PART);
            BlockPos centerPos = getCenterPos(level, pos, state);

            if (centerPos != null) {
                if (part == LauncherPart.CENTER) {

                    removeMultiblock(level, centerPos, state.getValue(FACING));
                } else {

                    StructureRemovalHelper.startRemoving(pos);
                    try {
                        BlockState centerState = level.getBlockState(centerPos);
                        if (centerState.getBlock() instanceof LauncherBlock) {

                            level.destroyBlock(centerPos, true);
                        }
                    } finally {
                        StructureRemovalHelper.finishRemoving(pos);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private BlockPos getCenterPos(Level level, BlockPos pos, BlockState state) {
        LauncherPart part = state.getValue(PART);

        if (part == LauncherPart.CENTER) return pos;

        return findCenterByScanning(level, pos);
    }

    private BlockPos findCenterByScanning(Level level, BlockPos pos) {
        if (level == null) return null;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = pos.offset(x, 0, z);
                BlockState checkState = level.getBlockState(checkPos);
                if (checkState.getBlock() instanceof LauncherBlock) {
                    if (checkState.getValue(PART) == LauncherPart.CENTER) {
                        return checkPos;
                    }
                }
            }
        }
        return null; 
    }

    private void removeMultiblock(Level level, BlockPos centerPos, Direction facing) {

        if (StructureRemovalHelper.isRemoving(centerPos)) return;

        java.util.List<BlockPos> allPositions = new java.util.ArrayList<>();
        allPositions.add(centerPos);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x != 0 || z != 0) {
                    allPositions.add(centerPos.offset(x, 0, z));
                }
            }
        }

        BlockEntity launcherBE = level.getBlockEntity(centerPos);
        BlockPos hatchPos = null;
        if (launcherBE instanceof LauncherBlockEntity launcher) {
            hatchPos = launcher.getLoadingHatchPos();
            if (hatchPos != null) {
                allPositions.add(hatchPos);
            }

            if (launcher.hasConnectedTerminal()) {
                BlockPos termPos = launcher.getConnectedTerminal();
                BlockEntity termBE = level.getBlockEntity(termPos);
                if (termBE instanceof com.synarsis.airtacticalarsenal.block.entity.UnifiedTerminalBlockEntity terminal) {
                    terminal.removeConnectedLauncher(centerPos);
                }
            }
        }

        for (BlockPos p : allPositions) {
            StructureRemovalHelper.startRemoving(p);
        }

        try {

            BlockPos rocketPos = centerPos.above();
            BlockState rocketState = level.getBlockState(rocketPos);
            if (rocketState.getBlock() instanceof IskanderRocketBlock) {
                level.removeBlock(rocketPos, false);
            }

            if (hatchPos != null) {
                BlockEntity hatchBE = level.getBlockEntity(hatchPos);
                if (hatchBE instanceof LoadingHatchBlockEntity hatch) {
                    if (hatch.isLoaded()) {
                        net.minecraft.world.item.ItemStack rocket = hatch.getRocket();
                        if (!rocket.isEmpty()) {
                            net.minecraft.world.Containers.dropItemStack(level, 
                                centerPos.getX(), centerPos.getY(), centerPos.getZ(), rocket);
                        }
                        hatch.clearRocket(); 
                    }
                }

                level.removeBlock(hatchPos, false);
            }

            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && z == 0) continue; 
                    BlockPos p = centerPos.offset(x, 0, z);
                    BlockState bs = level.getBlockState(p);
                    if (bs.getBlock() instanceof LauncherBlock) {
                        level.removeBlock(p, false); 
                    }
                }
            }

        } finally {
            StructureRemovalHelper.clearAll();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {

        if (state.getValue(PART) == LauncherPart.CENTER) {
            return new LauncherBlockEntity(pos, state);
        }
        return null;
    }

    public void setLoaded(Level level, BlockPos anyPartPos, boolean loaded) {
        BlockState state = level.getBlockState(anyPartPos);
        if (state.getBlock() instanceof LauncherBlock) {
            BlockPos centerPos = getCenterPos(level, anyPartPos, state);
            if (centerPos != null) {
                Direction facing = state.getValue(FACING);
                setAllPartsLoaded(level, centerPos, facing, loaded);
            }
        }
    }

    private void setAllPartsLoaded(Level level, BlockPos centerPos, Direction facing, boolean loaded) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = centerPos.offset(x, 0, z);
                BlockState bs = level.getBlockState(p);
                if (bs.getBlock() instanceof LauncherBlock) {
                    level.setBlock(p, bs.setValue(LOADED, loaded), 3);
                }
            }
        }
    }

    public void setLaunching(Level level, BlockPos anyPartPos, boolean launching) {
        BlockState state = level.getBlockState(anyPartPos);
        if (state.getBlock() instanceof LauncherBlock) {
            BlockPos centerPos = getCenterPos(level, anyPartPos, state);
            if (centerPos != null) {
                setAllPartsLaunching(level, centerPos, launching);
            }
        }
    }

    private void setAllPartsLaunching(Level level, BlockPos centerPos, boolean launching) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = centerPos.offset(x, 0, z);
                BlockState bs = level.getBlockState(p);
                if (bs.getBlock() instanceof LauncherBlock) {
                    level.setBlock(p, bs.setValue(LAUNCHING, launching), 3);
                }
            }
        }
    }

    public boolean isLaunching(Level level, BlockPos anyPartPos) {
        BlockState state = level.getBlockState(anyPartPos);
        if (state.getBlock() instanceof LauncherBlock) {
            return state.getValue(LAUNCHING);
        }
        return false;
    }

    public BlockPos getLaunchPosition(Level level, BlockPos anyPartPos) {
        BlockState state = level.getBlockState(anyPartPos);
        if (state.getBlock() instanceof LauncherBlock) {
            BlockPos centerPos = getCenterPos(level, anyPartPos, state);
            if (centerPos != null) {
                return centerPos.above();
            }
        }
        return anyPartPos.above();
    }

    private boolean isNearOtherLauncher(Level level, BlockPos centerPos) {
        int searchRadius = MIN_LAUNCHER_DISTANCE + 2;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos checkPos = centerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (state.getBlock() instanceof LauncherBlock) {
                        BlockPos otherCenter = getCenterPos(level, checkPos, state);
                        if (otherCenter != null && !otherCenter.equals(centerPos)) {

                            double newCenterX = centerPos.getX() + 0.5;
                            double newCenterZ = centerPos.getZ() + 0.5;
                            double otherCenterX = otherCenter.getX() + 0.5;
                            double otherCenterZ = otherCenter.getZ() + 0.5;
                            double distance = Math.sqrt(
                                Math.pow(newCenterX - otherCenterX, 2) +
                                Math.pow(newCenterZ - otherCenterZ, 2)
                            );
                            if (distance < MIN_LAUNCHER_DISTANCE) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static final int CLEAR_HEIGHT = 40;

    public boolean isBlocked(Level level, BlockPos anyPartPos) {
        BlockState state = level.getBlockState(anyPartPos);
        if (!(state.getBlock() instanceof LauncherBlock)) {
            return true;
        }

        BlockPos centerPos = getCenterPos(level, anyPartPos, state);
        if (centerPos == null) {
            return true;
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    BlockPos check = centerPos.offset(x, y, z);
                    BlockState checkState = level.getBlockState(check);
                    if (!checkState.isAir() && !(checkState.getBlock() instanceof IskanderRocketBlock)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getBlockedReason(Level level, BlockPos anyPartPos) {
        BlockState state = level.getBlockState(anyPartPos);
        if (!(state.getBlock() instanceof LauncherBlock)) {
            return "not_launcher";
        }

        BlockPos centerPos = getCenterPos(level, anyPartPos, state);
        if (centerPos == null) {
            return "invalid";
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= CLEAR_HEIGHT; y++) {
                    BlockPos check = centerPos.offset(x, y, z);
                    BlockState checkState = level.getBlockState(check);
                    if (!checkState.isAir() && !(checkState.getBlock() instanceof IskanderRocketBlock)) {
                        return "blocked";
                    }
                }
            }
        }

        return null;
    }

    public enum LauncherPart implements StringRepresentable {
        CENTER("center"),
        PLATFORM("platform"),
        STAIR_NORTH("stair_north"),
        STAIR_SOUTH("stair_south"),
        STAIR_EAST("stair_east"),
        STAIR_WEST("stair_west"),
        STAIR_CORNER_NE("stair_corner_ne"),
        STAIR_CORNER_NW("stair_corner_nw"),
        STAIR_CORNER_SE("stair_corner_se"),
        STAIR_CORNER_SW("stair_corner_sw");

        private final String name;

        LauncherPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
