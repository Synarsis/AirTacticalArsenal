package com.synarsis.airtacticalarsenal.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class LauncherBlockEntity extends BlockEntity {

    private BlockPos loadingHatchPos = null;
    private BlockPos targetPos = null;
    private BlockPos connectedTerminalPos = null;
    private double distanceToTarget = 0;
    private double calculatedCEP = 0;
    private boolean rocketLoaded = false;
    private String rocketName = "";
    private boolean inFlight = false;

    public LauncherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCHER.get(), pos, state);
    }

    public void setLoadingHatch(BlockPos pos) {
        this.loadingHatchPos = pos;
        setChanged();
        syncToClient();
    }

    @Nullable
    public BlockPos getLoadingHatchPos() {
        return loadingHatchPos;
    }

    public void setConnectedTerminal(@Nullable BlockPos pos) {
        this.connectedTerminalPos = pos;
        setChanged();
        syncToClient();
    }

    @Nullable
    public BlockPos getConnectedTerminal() {
        return connectedTerminalPos;
    }

    public boolean hasConnectedTerminal() {
        return connectedTerminalPos != null;
    }

    public void setTarget(BlockPos target) {
        this.targetPos = target;
        if (target != null) {
            calculateTargetParameters();
        } else {
            this.distanceToTarget = 0;
            this.calculatedCEP = 0;
        }
        setChanged();
        syncToClient();
    }

    @Nullable
    public BlockPos getTarget() {
        return targetPos;
    }

    public boolean hasTarget() {
        return targetPos != null;
    }

    public void calculateTargetParameters() {
        if (targetPos == null) {
            distanceToTarget = 0;
            calculatedCEP = 0;
            return;
        }

        double dx = targetPos.getX() - worldPosition.getX();
        double dz = targetPos.getZ() - worldPosition.getZ();

        distanceToTarget = Math.sqrt(dx*dx + dz*dz);

        double baseCEP = 10.0;  
        double distanceFactor = distanceToTarget / 100.0;
        calculatedCEP = baseCEP + distanceFactor;

        calculatedCEP = Math.round(calculatedCEP);
    }

    public double getDistanceToTarget() {
        return distanceToTarget;
    }

    public double getCEP() {
        return calculatedCEP;
    }

    public void setRocketLoaded(boolean loaded) {
        this.rocketLoaded = loaded;
        if (!loaded) {
            this.rocketName = "";
        }
        setChanged();
        syncToClient();
    }

    public void setRocketName(String name) {
        this.rocketName = name;
        setChanged();
        syncToClient();
    }

    public boolean isRocketLoaded() {
        return rocketLoaded;
    }

    public String getRocketName() {
        return rocketName;
    }

    public boolean isInFlight() {
        return inFlight;
    }

    public void setInFlight(boolean inFlight) {
        this.inFlight = inFlight;
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putBoolean("rocketLoaded", rocketLoaded);
        tag.putString("rocketName", rocketName);
        tag.putBoolean("inFlight", inFlight);
        tag.putDouble("distance", distanceToTarget);
        tag.putDouble("cep", calculatedCEP);

        if (loadingHatchPos != null) {
            tag.putInt("hatchX", loadingHatchPos.getX());
            tag.putInt("hatchY", loadingHatchPos.getY());
            tag.putInt("hatchZ", loadingHatchPos.getZ());
        }

        if (targetPos != null) {
            tag.putInt("targetX", targetPos.getX());
            tag.putInt("targetY", targetPos.getY());
            tag.putInt("targetZ", targetPos.getZ());
        }

        if (connectedTerminalPos != null) {
            tag.putInt("termX", connectedTerminalPos.getX());
            tag.putInt("termY", connectedTerminalPos.getY());
            tag.putInt("termZ", connectedTerminalPos.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        rocketLoaded = tag.getBoolean("rocketLoaded");
        rocketName = tag.getString("rocketName");
        inFlight = tag.getBoolean("inFlight");
        distanceToTarget = tag.getDouble("distance");
        calculatedCEP = tag.getDouble("cep");

        if (tag.contains("hatchX")) {
            loadingHatchPos = new BlockPos(
                tag.getInt("hatchX"),
                tag.getInt("hatchY"),
                tag.getInt("hatchZ")
            );
        }

        if (tag.contains("targetX")) {
            targetPos = new BlockPos(
                tag.getInt("targetX"),
                tag.getInt("targetY"),
                tag.getInt("targetZ")
            );
        }

        if (tag.contains("termX")) {
            connectedTerminalPos = new BlockPos(
                tag.getInt("termX"),
                tag.getInt("termY"),
                tag.getInt("termZ")
            );
        } else {
            connectedTerminalPos = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
