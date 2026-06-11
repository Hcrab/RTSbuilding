package com.rtsbuilding.rtsbuilding.server.storage;


import net.minecraft.core.BlockPos;
import net.minecraftforge.fluids.capability.IFluidHandler;

public record LinkedFluidHandler(LinkedStorageRef ref, String name, IFluidHandler handler, boolean allowStore) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
