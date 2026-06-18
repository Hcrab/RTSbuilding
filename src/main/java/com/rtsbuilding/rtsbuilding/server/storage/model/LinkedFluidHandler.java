package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public record LinkedFluidHandler(LinkedStorageRef ref, String name, IFluidHandler handler, boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
