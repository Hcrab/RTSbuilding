package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public record EntityEntry(int entityId, Entity entity, String displayName, Vec3 hitLocation)
        implements SelectableEntry {

    @Override
    public Object identifier() {
        return entityId;
    }
}
