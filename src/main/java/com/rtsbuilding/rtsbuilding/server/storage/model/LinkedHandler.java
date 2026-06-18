package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

public record LinkedHandler(LinkedStorageRef ref, String name, IItemHandler handler, boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
