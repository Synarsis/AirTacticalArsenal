package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShahedSpawnPacket(int entityId, Vec3 startPos, BlockPos targetPos, String diveMode) implements CustomPacketPayload {
    public static final Type<ShahedSpawnPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "shahed_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedSpawnPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.entityId);
                buf.writeDouble(p.startPos.x);
                buf.writeDouble(p.startPos.y);
                buf.writeDouble(p.startPos.z);
                buf.writeBlockPos(p.targetPos);
                buf.writeUtf(p.diveMode);
            },
            (buf) -> new ShahedSpawnPacket(
                    buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readBlockPos(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() { return entityId; }
    public Vec3 getStartPos() { return startPos; }
    public BlockPos getTargetPos() { return targetPos; }
    public String getDiveMode() { return diveMode; }

    public static void handle(ShahedSpawnPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Client-side: forwarded to client packet handler
            ClientPacketHandler.handleShahedSpawn(
                    payload.entityId(), payload.startPos(), payload.targetPos(), payload.diveMode());
        });
    }
}
