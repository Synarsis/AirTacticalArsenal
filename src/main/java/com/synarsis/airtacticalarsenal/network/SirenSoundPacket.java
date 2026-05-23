package com.synarsis.airtacticalarsenal.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SirenSoundPacket {
    private final BlockPos pos;
    private final boolean stop;

    public SirenSoundPacket(BlockPos pos) {
        this.pos = pos;
        this.stop = false;
    }

    public SirenSoundPacket(BlockPos pos, boolean stop) {
        this.pos = pos;
        this.stop = stop;
    }

    public SirenSoundPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.stop = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.stop);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> handleClient());
        });
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        if (this.stop) {
            com.synarsis.airtacticalarsenal.client.sound.SirenSoundInstance.stopSiren(this.pos);
        } else {
            com.synarsis.airtacticalarsenal.client.sound.SirenSoundInstance.playSiren(this.pos);
        }
    }
}
