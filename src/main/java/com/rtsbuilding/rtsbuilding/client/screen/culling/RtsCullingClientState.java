package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Client-global bridge for range-culling state.
 *
 * <p>Mixins and world renderers cannot hold a BuilderScreen instance, so they query the active RTS screen here.
 * This class forwards read-only culling checks and never creates or mutates culling regions itself.
 */
public final class RtsCullingClientState {
    private static final RtsCullingManager PERSISTENT_MANAGER = new RtsCullingManager();
    // Embeddium 在后台网格线程读取隐藏状态，必须安全发布当前管理器。
    private static volatile RtsCullingManager activeManager;

    private RtsCullingClientState() {
    }

    public static RtsCullingManager persistentManager() {
        return PERSISTENT_MANAGER;
    }

    public static void setActiveManager(RtsCullingManager manager) {
        activeManager = manager;
        if (manager != null) {
            manager.refreshWorldCullRendering();
        }
    }

    public static void clearActiveManager(RtsCullingManager manager) {
        if (activeManager == manager) {
            activeManager = null;
        }
    }

    public static RtsCullingManager activeManager() {
        return activeManager;
    }

    public static boolean shouldCull(BlockPos pos) {
        return activeManager != null && activeManager.shouldCullWorldBlock(pos);
    }

    public static void revealLikelyPlacement(BlockPos clickedPos, Direction face) {
        if (activeManager == null) {
            return;
        }
        activeManager.revealWorldBlock(clickedPos);
        if (clickedPos != null && face != null) {
            activeManager.revealWorldBlock(clickedPos.relative(face));
        }
    }

    public static double distanceAfterCulledBlock(Vec3 origin, Vec3 direction, BlockPos pos, double maxDistance) {
        if (activeManager == null) {
            return -1.0D;
        }
        return activeManager.distanceAfterCulledBlock(origin, direction, pos, maxDistance);
    }
}
