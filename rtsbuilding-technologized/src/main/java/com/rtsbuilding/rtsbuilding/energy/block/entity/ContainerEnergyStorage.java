package com.rtsbuilding.rtsbuilding.energy.block.entity;

import com.rtsbuilding.rtsbuilding.api.energy.Action;
import com.rtsbuilding.rtsbuilding.api.energy.AutomationType;
import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Adapts an {@link IEnergyContainer} to NeoForge's standard {@link IEnergyStorage}
 * interface so energy blocks can be charged/discharged by other mods.
 */
public class ContainerEnergyStorage implements IEnergyStorage {

    private final IEnergyContainer container;
    private final boolean canReceive;
    private final boolean canExtract;

    public ContainerEnergyStorage(IEnergyContainer container, boolean canReceive, boolean canExtract) {
        this.container = container;
        this.canReceive = canReceive;
        this.canExtract = canExtract;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive || maxReceive <= 0) {
            return 0;
        }
        long remaining = container.insert(maxReceive, Action.get(!simulate), AutomationType.EXTERNAL);
        return (int) Math.max(0, maxReceive - remaining);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract || maxExtract <= 0) {
            return 0;
        }
        return (int) container.extract(maxExtract, Action.get(!simulate), AutomationType.EXTERNAL);
    }

    @Override
    public int getEnergyStored() {
        return (int) container.getEnergy();
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) container.getMaxEnergy();
    }

    @Override
    public boolean canExtract() {
        return canExtract;
    }

    @Override
    public boolean canReceive() {
        return canReceive;
    }
}
