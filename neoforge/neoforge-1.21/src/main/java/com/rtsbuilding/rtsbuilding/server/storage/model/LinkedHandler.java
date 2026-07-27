package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A resolved linked item handler — binds a linked storage reference to its corresponding item handler.
 *
 * <p>Encapsulates the handler's identity reference, display name, whether storing is allowed, and priority.
 *
 * @param ref        The linked storage reference
 * @param name       The display name
 * @param handler    The item handler
 * @param allowStore Whether storing items is allowed (false = extract-only mode)
 * @param priority   Priority (AE-style, affects insertion order)
 */
public record LinkedHandler(LinkedStorageRef ref, String name, IItemHandler handler, boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
