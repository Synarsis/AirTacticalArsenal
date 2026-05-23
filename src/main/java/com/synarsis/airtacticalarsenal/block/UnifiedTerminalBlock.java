package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.ModBlockEntities;
import com.synarsis.airtacticalarsenal.block.entity.UnifiedTerminalBlockEntity;
import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OpenUnifiedTerminalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class UnifiedTerminalBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public UnifiedTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UnifiedTerminalBlockEntity(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UnifiedTerminalBlockEntity terminal) {

                for (BlockPos launcherPos : terminal.getConnectedLaunchers()) {
                    BlockEntity launcherBE = level.getBlockEntity(launcherPos);
                    if (launcherBE instanceof LauncherBlockEntity launcher) {
                        launcher.setConnectedTerminal(null);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof UnifiedTerminalBlockEntity terminal)) {
                return InteractionResult.FAIL;
            }

            terminal.validateConnectedLaunchers();

            List<BlockPos> launchers = terminal.getConnectedLaunchers();
            boolean hasLauncher = !launchers.isEmpty();
            int launcherCount = launchers.size();

            int freeLaunchers = 0;
            List<Boolean> loadedStates = new ArrayList<>();
            List<Boolean> launchingStates = new ArrayList<>();
            for (BlockPos launcherPos : launchers) {
                BlockState launcherState = level.getBlockState(launcherPos);
                if (launcherState.getBlock() instanceof LauncherBlock) {
                    boolean isLoaded = launcherState.getValue(LauncherBlock.LOADED);
                    boolean isLaunching = launcherState.getValue(LauncherBlock.LAUNCHING);

                    BlockEntity launcherBE2 = level.getBlockEntity(launcherPos);
                    if (launcherBE2 instanceof LauncherBlockEntity lbe && lbe.isInFlight()) {
                        isLaunching = true;
                    }
                    loadedStates.add(isLoaded);
                    launchingStates.add(isLaunching);
                    if (!isLoaded && !isLaunching) {
                        freeLaunchers++;
                    }
                } else {
                    loadedStates.add(false);
                    launchingStates.add(false);
                }
            }

            int launchCostShahed = ShahedConfig.LAUNCH_COST_COINS.get();
            int launchCostIskander = ShahedConfig.getIskanderLaunchCost();
            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
            boolean blacklistEnabled = ShahedConfig.isBlacklistEnabled();
            boolean whitelistEnabled = ShahedConfig.isWhitelistEnabled();
            List<String> blacklistZones = new ArrayList<>(ShahedConfig.FORBIDDEN_ZONES.get());
            List<String> whitelistZones = new ArrayList<>(ShahedConfig.WHITELIST_ZONES.get());

            List<BlockPos> launcherTargets = new ArrayList<>();
            List<Double> launcherDistances = new ArrayList<>();
            List<Double> launcherCEPs = new ArrayList<>();
            for (BlockPos launcherPos : launchers) {
                BlockEntity launcherBE = level.getBlockEntity(launcherPos);
                if (launcherBE instanceof LauncherBlockEntity launcherEntity) {
                    BlockPos target = launcherEntity.getTarget();
                    launcherTargets.add(target); 
                    launcherDistances.add(launcherEntity.getDistanceToTarget());
                    launcherCEPs.add(launcherEntity.getCEP());
                } else {
                    launcherTargets.add(null);
                    launcherDistances.add(0.0);
                    launcherCEPs.add(0.0);
                }
            }

            NetworkHandler.sendToPlayer(serverPlayer, new OpenUnifiedTerminalPacket(
                pos, launchers, loadedStates, launchingStates, hasLauncher, launcherCount, freeLaunchers, 
                launchCostShahed, launchCostIskander, targetY, blacklistEnabled, whitelistEnabled, 
                blacklistZones, whitelistZones, launcherTargets, launcherDistances, launcherCEPs));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
