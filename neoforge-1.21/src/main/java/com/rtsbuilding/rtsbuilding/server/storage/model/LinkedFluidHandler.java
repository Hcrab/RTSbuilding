package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A resolved linked fluid handler — binds a linked storage reference to its corresponding fluid handler.
 *
 * <p>Encapsulates the handler's identity reference, display name, whether storing is allowed, and priority.
 *
 * @param ref        The linked storage reference
 * @param name       The display name
 * @param handler    The fluid handler
 * @param allowStore Whether storing fluid is allowed (false = extract-only mode)
 * @param priority   Priority (AE-style, affects insertion order)
 */
public record LinkedFluidHandler(LinkedStorageRef ref, String name, IFluidHandler handler, boolean allowStore, int priority) {
    public BlockPos pos() {
        return this.ref.pos();
    }
}
