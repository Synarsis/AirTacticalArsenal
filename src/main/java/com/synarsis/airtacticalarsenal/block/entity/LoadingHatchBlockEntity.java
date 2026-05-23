package com.synarsis.airtacticalarsenal.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.synarsis.airtacticalarsenal.block.LoadingHatchBlock;

import javax.annotation.Nullable;

public class LoadingHatchBlockEntity extends BlockEntity {

    private BlockPos parentLauncherPos = null;
    private ItemStack rocketStack = ItemStack.EMPTY;
    private boolean isLoaded = false;

    public LoadingHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOADING_HATCH.get(), pos, state);
    }

    public void setParentLauncher(BlockPos pos) {
        this.parentLauncherPos = pos;
        setChanged();
        syncToClient();
    }

    @Nullable
    public BlockPos getParentLauncher() {
        return parentLauncherPos;
    }

    public void loadRocket(ItemStack rocket) {
        this.rocketStack = rocket.copy();
        this.isLoaded = true;
        setChanged();
        syncToClient();
    }

    public ItemStack unloadRocket() {
        ItemStack result = this.rocketStack.copy();
        this.rocketStack = ItemStack.EMPTY;
        this.isLoaded = false;
        setChanged();
        syncToClient();
        return result;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public ItemStack getRocket() {
        return rocketStack.copy();
    }

    public String getRocketName() {
        if (rocketStack.isEmpty()) {
            return "";
        }
        return rocketStack.getHoverName().getString();
    }

    public void clearRocket() {
        this.rocketStack = ItemStack.EMPTY;
        this.isLoaded = false;
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {

            BlockState currentState = level.getBlockState(worldPosition);
            if (currentState.getBlock() instanceof LoadingHatchBlock) {
                boolean stateLoaded = currentState.getValue(LoadingHatchBlock.LOADED);
                if (stateLoaded != this.isLoaded) {
                    BlockState newState = currentState.setValue(LoadingHatchBlock.LOADED, this.isLoaded);
                    level.setBlock(worldPosition, newState, 3);
                }
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("isLoaded", isLoaded);
        if (parentLauncherPos != null) {
            tag.putInt("launcherX", parentLauncherPos.getX());
            tag.putInt("launcherY", parentLauncherPos.getY());
            tag.putInt("launcherZ", parentLauncherPos.getZ());
        }
        if (!rocketStack.isEmpty()) {
            tag.put("rocket", rocketStack.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isLoaded = tag.getBoolean("isLoaded");
        if (tag.contains("launcherX")) {
            parentLauncherPos = new BlockPos(
                tag.getInt("launcherX"),
                tag.getInt("launcherY"),
                tag.getInt("launcherZ")
            );
        }
        if (tag.contains("rocket")) {
            rocketStack = ItemStack.of(tag.getCompound("rocket"));
        } else {
            rocketStack = ItemStack.EMPTY;
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
