package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetLauncherTargetPacket(BlockPos launcherPos, BlockPos targetPos, boolean hasTarget) implements CustomPacketPayload {

    public SetLauncherTargetPacket(BlockPos launcherPos, BlockPos targetPos) {
        this(launcherPos, targetPos != null ? targetPos : BlockPos.ZERO, targetPos != null);
    }

    public static final Type<SetLauncherTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "set_launcher_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLauncherTargetPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.launcherPos);
                buf.writeBoolean(p.hasTarget);
                if (p.hasTarget) {
                    buf.writeBlockPos(p.targetPos);
                }
            },
            (buf) -> {
                BlockPos lp = buf.readBlockPos();
                boolean has = buf.readBoolean();
                BlockPos tp = has ? buf.readBlockPos() : BlockPos.ZERO;
                return new SetLauncherTargetPacket(lp, tp, has);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetLauncherTargetPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(payload.launcherPos());
            if (be instanceof LauncherBlockEntity launcher) {
                BlockPos target = payload.hasTarget() ? payload.targetPos() : null;
                launcher.setTarget(target);

                if (target != null) {
                    player.displayClientMessage(Component.literal("§aЦель установлена: ["
                            + target.getX() + ", " + target.getY() + ", " + target.getZ() + "]"), true);
                } else {
                    player.displayClientMessage(Component.literal("§eЦель сброшена"), true);
                }
            }
        });
    }
}
