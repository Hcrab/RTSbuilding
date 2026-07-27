package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public interface MovementModeHandler {

    
    boolean isActive(LocalPlayer player);

    
    MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist);

    
    default void onActivate(LocalPlayer player) {
    }

    
    default void onDeactivate(LocalPlayer player) {
    }
}
