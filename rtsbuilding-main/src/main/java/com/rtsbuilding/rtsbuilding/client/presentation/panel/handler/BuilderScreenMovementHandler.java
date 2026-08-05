package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

public final class BuilderScreenMovementHandler {

    
    private long lastCtrlRightClickTime = 0;

    
    private static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300;

    
    public boolean handleMovePlayerActionAt(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) {
            return true;
        }
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) {
            return true;
        }
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - this.lastCtrlRightClickTime) < CTRL_DOUBLE_CLICK_THRESHOLD_MS;
        this.lastCtrlRightClickTime = now;

        if (isDoubleClick) {
            this.lastCtrlRightClickTime = 0;
            RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
        } else {
            RtsClientPathfinding.goTo(hit.getBlockPos());
        }
        return true;
    }
}
