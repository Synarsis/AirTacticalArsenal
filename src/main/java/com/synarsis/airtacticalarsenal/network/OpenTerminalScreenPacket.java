package com.synarsis.airtacticalarsenal.network;

import com.synarsis.airtacticalarsenal.AirTacticalArsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record OpenTerminalScreenPacket(
        BlockPos terminalPos,
        int targetY,
        boolean blacklistEnabled,
        boolean whitelistEnabled,
        List<String> blacklistZones,
        List<String> whitelistZones
) implements CustomPacketPayload {

    public static final Type<OpenTerminalScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AirTacticalArsenal.MODID, "open_terminal_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminalScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.terminalPos);
                buf.writeInt(p.targetY);
                buf.writeBoolean(p.blacklistEnabled);
                buf.writeBoolean(p.whitelistEnabled);
                buf.writeInt(p.blacklistZones.size());
                for (String zone : p.blacklistZones) {
                    buf.writeUtf(zone);
                }
                buf.writeInt(p.whitelistZones.size());
                for (String zone : p.whitelistZones) {
                    buf.writeUtf(zone);
                }
            },
            (buf) -> {
                BlockPos terminalPos = buf.readBlockPos();
                int targetY = buf.readInt();
                boolean blacklistEnabled = buf.readBoolean();
                boolean whitelistEnabled = buf.readBoolean();
                int blacklistSize = buf.readInt();
                List<String> blacklistZones = new ArrayList<>();
                for (int i = 0; i < blacklistSize; i++) {
                    blacklistZones.add(buf.readUtf());
                }
                int whitelistSize = buf.readInt();
                List<String> whitelistZones = new ArrayList<>();
                for (int i = 0; i < whitelistSize; i++) {
                    whitelistZones.add(buf.readUtf());
                }
                return new OpenTerminalScreenPacket(terminalPos, targetY,
                        blacklistEnabled, whitelistEnabled, blacklistZones, whitelistZones);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTerminalScreenPacket payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> com.synarsis.airtacticalarsenal.client.ClientScreens.openShahedTerminal(
                payload.terminalPos(), payload.targetY(),
                payload.blacklistEnabled(), payload.whitelistEnabled(),
                payload.blacklistZones(), payload.whitelistZones()));
    }
}
