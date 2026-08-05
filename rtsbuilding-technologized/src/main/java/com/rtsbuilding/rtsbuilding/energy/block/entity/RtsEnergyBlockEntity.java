package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyNetworkManager;
import com.rtsbuilding.rtsbuilding.energy.server.RtsEnergyNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base block entity for all energy blocks (bank / generator).
 * <p>
 * Handles the grid registration lifecycle: nodes are registered with
 * {@link RtsEnergyNetworkManager} when loaded/placed and unregistered when
 * broken or unloaded. The placing player is persisted as the node owner so the
 * server can attribute storage and generation to the correct player's grid.
 */
public abstract class RtsEnergyBlockEntity extends BlockEntity implements RtsEnergyNode {

    private static final String NBT_OWNER = "owner";

    @Nullable
    private UUID owner;

    protected RtsEnergyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            RtsEnergyNetworkManager.INSTANCE.register(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide && com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            RtsEnergyNetworkManager.INSTANCE.unregister(level, worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide && com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            RtsEnergyNetworkManager.INSTANCE.register(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && com.rtsbuilding.rtsbuilding.Config.isTechnologizedEnabled()) {
            RtsEnergyNetworkManager.INSTANCE.unregister(level, worldPosition);
        }
        super.setRemoved();
    }

    @Nullable
    @Override
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    @Override
    public net.minecraft.world.level.Level getLevel() {
        return level;
    }

    @Override
    public net.minecraft.core.BlockPos getBlockPos() {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (owner != null) {
            tag.putUUID(NBT_OWNER, owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID(NBT_OWNER)) {
            owner = tag.getUUID(NBT_OWNER);
        }
    }
}
