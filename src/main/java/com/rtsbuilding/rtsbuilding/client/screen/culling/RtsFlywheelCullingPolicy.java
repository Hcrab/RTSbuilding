package com.rtsbuilding.rtsbuilding.client.screen.culling;

/**
 * Flywheel 方块实体实例与 RTS 剔除状态之间的纯决策边界。
 *
 * <p>本类不保存位置、范围或实例集合；剔除事实始终只来自
 * {@link RtsCullingClientState}。把“隐藏即移除、可见才准入”固定在这里，
 * 是为了让状态切换同步和 Flywheel admission guard 共用同一语义。</p>
 */
public final class RtsFlywheelCullingPolicy {
    private RtsFlywheelCullingPolicy() {
    }

    public static boolean shouldAdmit(boolean culled) {
        return !culled;
    }

    public static SyncAction actionFor(boolean culled) {
        return culled ? SyncAction.REMOVE : SyncAction.ADD;
    }

    public enum SyncAction {
        ADD,
        REMOVE
    }
}
