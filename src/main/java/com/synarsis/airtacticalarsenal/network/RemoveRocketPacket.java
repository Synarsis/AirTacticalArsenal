package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.entity.IskanderRocketBlockEntity;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.LoadingHatchBlockEntity;

public record RemoveRocketPacket(BlockPos launcherPos) implements CustomPacketPayload {

    public static final Type<RemoveRocketPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "remove_rocket"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveRocketPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RemoveRocketPacket::launcherPos,
            RemoveRocketPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ItemStack takeFromHatch(Level level, BlockPos launcherPos) {
        if (!(level.getBlockEntity(launcherPos) instanceof LauncherBlockEntity launcherBE)) return ItemStack.EMPTY;
        BlockPos hatchPos = launcherBE.getLoadingHatchPos();
        if (hatchPos == null) return ItemStack.EMPTY;
        if (level.getBlockEntity(hatchPos) instanceof LoadingHatchBlockEntity hatch && hatch.isLoaded()) {
            return hatch.unloadRocket();
        }
        return ItemStack.EMPTY;
    }

    public static void handle(RemoveRocketPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player rawPlayer = context.player();
            if (!(rawPlayer instanceof ServerPlayer player)) return;

            Level level = player.level();
            BlockPos launcherPos = payload.launcherPos();

            BlockState launcherState = level.getBlockState(launcherPos);
            if (!(launcherState.getBlock() instanceof LauncherBlock launcher)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.invalid_launcher"), true);
                return;
            }

            if (!launcherState.getValue(LauncherBlock.LOADED)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.not_loaded"), true);
                return;
            }

            BlockPos rocketPos = launcherPos.above();
            BlockEntity be = level.getBlockEntity(rocketPos);
            if (!(be instanceof IskanderRocketBlockEntity rocketEntity)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.not_found"), true);
                return;
            }

            if (rocketEntity.isLaunching()) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.already_launching"), true);
                return;
            }

            level.removeBlock(rocketPos, false);

            launcher.setLoaded(level, launcherPos, false);

            ItemStack rocketItem = takeFromHatch(level, launcherPos);
            if (rocketItem.isEmpty()) {
                rocketItem = new ItemStack(ModItems.ISKANDER.get());
            }
            if (!player.getInventory().add(rocketItem)) {
                player.drop(rocketItem, false);
            }

            player.displayClientMessage(Component.translatable("message.ata.rocket.removed"), true);
        });
    }
}
