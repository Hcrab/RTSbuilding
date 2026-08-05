package com.rtsbuilding.rtsbuilding.client.infrastructure.module.pathfinding;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;

public final class PathfindingModule implements FeatureModule {

    private boolean active;

    @Override
    public String moduleId() {
        return "pathfinding";
    }

    @Override
    public void init(RtsClientKernel kernel) {
        this.active = true;
    }

    @Override
    public void tickPre(long epochMs, int tickIndex) {
        if (!active) return;
        RtsClientPathfinding.tickPre();
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
