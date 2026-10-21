package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.IskanderRocketBlock;
import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import com.synarsis.airtacticalarsenal.block.entity.LoadingHatchBlockEntity;

public record InstallRocketPacket(BlockPos launcherPos, BlockPos terminalPos) implements CustomPacketPayload {

    public static final Type<InstallRocketPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "install_rocket"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InstallRocketPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, InstallRocketPacket::launcherPos,
            BlockPos.STREAM_CODEC, InstallRocketPacket::terminalPos,
            InstallRocketPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void mirrorIntoHatch(Level level, BlockPos launcherPos, ItemStack rocket) {
        if (!(level.getBlockEntity(launcherPos) instanceof LauncherBlockEntity launcherBE)) return;
        BlockPos hatchPos = launcherBE.getLoadingHatchPos();
        if (hatchPos == null) return;
        if (level.getBlockEntity(hatchPos) instanceof LoadingHatchBlockEntity hatch && !hatch.isLoaded()) {
            hatch.loadRocket(rocket);
        }
    }

    public static void handle(InstallRocketPacket payload, IPayloadContext context) {
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

            if (launcherState.getValue(LauncherBlock.PART) != LauncherBlock.LauncherPart.CENTER) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.invalid_launcher"), true);
                return;
            }

            if (launcherState.getValue(LauncherBlock.LOADED)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.already_loaded"), true);
                return;
            }

            if (launcher.isBlocked(level, launcherPos)) {
                player.displayClientMessage(Component.translatable("message.ata.launcher.blocked"), true);
                return;
            }

            int rocketSlot = -1;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (stack.is(ModItems.ISKANDER.get())) {
                    rocketSlot = i;
                    break;
                }
            }

            if (rocketSlot == -1) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.no_rocket"), true);
                return;
            }

            Direction facing = launcherState.getValue(LauncherBlock.FACING);
            if (!IskanderRocketBlock.placeRocket(level, launcherPos, facing)) {
                player.displayClientMessage(Component.translatable("message.ata.rocket.install_failed"), true);
                return;
            }

            ItemStack paid = player.getInventory().items.get(rocketSlot).split(1);
            launcher.setLoaded(level, launcherPos, true);

            mirrorIntoHatch(level, launcherPos, paid);

            player.displayClientMessage(Component.translatable("message.ata.rocket.installed"), true);
        });
    }
}
