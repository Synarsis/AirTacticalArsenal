package com.synarsis.airtacticalarsenal.block.entity;

import com.synarsis.airtacticalarsenal.block.LauncherBlock;
import com.synarsis.airtacticalarsenal.block.entity.LauncherBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UnifiedTerminalBlockEntity extends BlockEntity {

    private final List<BlockPos> connectedLaunchers = new ArrayList<>();

    public UnifiedTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIFIED_TERMINAL.get(), pos, state);
    }

    public boolean addConnectedLauncher(BlockPos pos) {
        if (!connectedLaunchers.contains(pos)) {
            connectedLaunchers.add(pos);
            setChanged();
            syncToClient();
            return true;
        }
        return false;
    }

    public boolean removeConnectedLauncher(BlockPos pos) {
        boolean removed = connectedLaunchers.remove(pos);
        if (removed) {

            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof LauncherBlockEntity launcher) {
                    launcher.setConnectedTerminal(null);
                }
            }
            setChanged();
            syncToClient();
        }
        return removed;
    }

    public List<BlockPos> getConnectedLaunchers() {
        return new ArrayList<>(connectedLaunchers);
    }

    public boolean isLauncherConnected(BlockPos pos) {
        return connectedLaunchers.contains(pos);
    }

    public int getConnectedLauncherCount() {
        return connectedLaunchers.size();
    }

    public void validateConnectedLaunchers() {
        if (level == null) return;

        boolean changed = false;
        Iterator<BlockPos> iterator = connectedLaunchers.iterator();

        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof LauncherBlock)) {
                iterator.remove();
                changed = true;
            } else if (state.getValue(LauncherBlock.PART) != LauncherBlock.LauncherPart.CENTER) {
                iterator.remove();
                changed = true;
            } else {

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof LauncherBlockEntity launcher) {
                    if (!worldPosition.equals(launcher.getConnectedTerminal())) {
                        launcher.setConnectedTerminal(worldPosition);
                    }
                }
            }
        }

        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        ListTag launcherList = new ListTag();
        for (BlockPos pos : connectedLaunchers) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            launcherList.add(posTag);
        }
        tag.put("ConnectedLaunchers", launcherList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        connectedLaunchers.clear();
        if (tag.contains("ConnectedLaunchers")) {
            ListTag launcherList = tag.getList("ConnectedLaunchers", 10); 
            for (int i = 0; i < launcherList.size(); i++) {
                CompoundTag posTag = launcherList.getCompound(i);
                BlockPos pos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
                );
                connectedLaunchers.add(pos);
            }
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
