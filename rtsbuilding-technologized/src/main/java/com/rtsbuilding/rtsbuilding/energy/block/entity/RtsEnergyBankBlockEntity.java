package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import com.rtsbuilding.rtsbuilding.common.energy.BasicEnergyContainer;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the energy bank. Holds a large {@link BasicEnergyContainer}
 * that counts toward the owner's energy grid and is exposed through the
 * standard {@code IEnergyStorage} capability.
 */
public class RtsEnergyBankBlockEntity extends RtsEnergyBlockEntity {

    /** Total FE capacity of one energy bank. */
    public static final long CAPACITY = 4_000_000L;

    private static final String NBT_ENERGY = "energy";

    private final BasicEnergyContainer buffer = BasicEnergyContainer.create(CAPACITY, this::markChanged);

    public RtsEnergyBankBlockEntity(BlockPos pos, BlockState state) {
        super(RtsEnergyBlockEntities.ENERGY_BANK.get(), pos, state);
    }

    private void markChanged() {
        setChanged();
    }

    @Override
    public IEnergyContainer getEnergyBuffer() {
        return buffer;
    }

    @Override
    public long getGeneration() {
        return 0;
    }

    public BasicEnergyContainer getBuffer() {
        return buffer;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(NBT_ENERGY, buffer.serializeNBT(provider));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains(NBT_ENERGY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(provider, tag.getCompound(NBT_ENERGY));
        }
    }
}
