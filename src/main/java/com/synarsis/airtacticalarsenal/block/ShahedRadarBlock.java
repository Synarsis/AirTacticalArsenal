package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
import com.synarsis.airtacticalarsenal.entity.IskanderEntity;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OpenRadarScreenPacket;
import com.synarsis.airtacticalarsenal.radar.ServerTerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ShahedRadarBlock extends Block {

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int RADAR_RANGE = 1500;

    public ShahedRadarBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToPlayer(serverPlayer, new OpenRadarScreenPacket(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            ServerTerrainScanner.remove(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int newPower = calculateSignalStrength(level, pos);
        int oldPower = state.getValue(POWER);

        if (newPower != oldPower) {
            level.setBlock(pos, state.setValue(POWER, newPower), 3);
            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }

        ServerTerrainScanner.getOrCreate(pos, level);

        level.scheduleTick(pos, this, 20);
    }

    private int calculateSignalStrength(ServerLevel level, BlockPos pos) {
        Vec3 radarCenter = Vec3.atCenterOf(pos);
        AABB searchBox = new AABB(
            radarCenter.x - RADAR_RANGE, radarCenter.y - 64, radarCenter.z - RADAR_RANGE,
            radarCenter.x + RADAR_RANGE, radarCenter.y + 400, radarCenter.z + RADAR_RANGE
        );

        double closestDistance = Double.MAX_VALUE;

        List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
            boolean isFlying = entity instanceof ShahedEntity shahed
                    && (shahed.getFlightPhase() == ShahedEntity.FlightPhase.LAUNCHING
                        || shahed.getFlightPhase() == ShahedEntity.FlightPhase.CLIMBING
                        || shahed.getFlightPhase() == ShahedEntity.FlightPhase.CRUISING
                        || shahed.getFlightPhase() == ShahedEntity.FlightPhase.DIVING);
            if (isFlying || entity instanceof IskanderEntity) {
                double dist = entity.position().distanceTo(radarCenter);
                if (dist < closestDistance) {
                    closestDistance = dist;
                }
            }
        }

        if (closestDistance == Double.MAX_VALUE) {
            return 0;
        }

        if (closestDistance < 50) return 15;
        if (closestDistance < 100) return 14;
        if (closestDistance < 200) return 13;
        if (closestDistance < 300) return 12;
        if (closestDistance < 400) return 10;
        if (closestDistance < 600) return 8;
        if (closestDistance < 800) return 6;
        if (closestDistance < 1000) return 5;
        if (closestDistance < 1100) return 4;
        if (closestDistance < 1200) return 3;
        if (closestDistance < 1350) return 2;
        if (closestDistance < 1500) return 1;
        return 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}
