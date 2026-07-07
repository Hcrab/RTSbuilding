package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 范围剔除的客户端全局状态桥。
 *
 * <p>Mixin 和世界渲染器不能持有 BuilderScreen 实例，因此通过这里查询当前
 * 打开的 RTS 页面。它只转发只读判断，不主动创建或修改剔除区域。
 */
public final class RtsCullingClientState {
    private static final RtsCullingManager PERSISTENT_MANAGER = new RtsCullingManager();
    private static RtsCullingManager activeManager;

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
