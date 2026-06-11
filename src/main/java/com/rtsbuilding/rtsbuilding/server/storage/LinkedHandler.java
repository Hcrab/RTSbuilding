package com.rtsbuilding.rtsbuilding.server.storage;


import net.minecraft.core.BlockPos;
import net.minecraftforge.items.IItemHandler;

public record LinkedHandler(LinkedStorageRef ref, String name, IItemHandler handler, boolean allowStore) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
