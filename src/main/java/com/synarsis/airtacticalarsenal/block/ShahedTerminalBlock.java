package com.synarsis.airtacticalarsenal.block;

import com.synarsis.airtacticalarsenal.config.ShahedConfig;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.OpenTerminalScreenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class ShahedTerminalBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ShahedTerminalBlock(BlockBehaviour.Properties properties) {
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            int launchCost = ShahedConfig.LAUNCH_COST_COINS.get();
            int targetY = ShahedConfig.TARGET_Y_LEVEL.get();
            boolean blacklistEnabled = ShahedConfig.isBlacklistEnabled();
            boolean whitelistEnabled = ShahedConfig.isWhitelistEnabled();
            List<String> blacklistZones = new ArrayList<>(ShahedConfig.FORBIDDEN_ZONES.get());
            List<String> whitelistZones = new ArrayList<>(ShahedConfig.WHITELIST_ZONES.get());

            NetworkHandler.sendToPlayer(serverPlayer, new OpenTerminalScreenPacket(
                pos, launchCost, targetY, blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
