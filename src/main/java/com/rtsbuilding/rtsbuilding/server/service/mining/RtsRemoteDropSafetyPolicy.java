package com.rtsbuilding.rtsbuilding.server.service.mining;

/**
 * 远程挖掘的掉落安全边界。
 *
 * <p>这个类只负责判断一次破坏是否已经远到不应把物品实体留在目标处；它不修改玩家的
 * 自动入库设置，也不负责实际存储。实际捕获仍由 {@link RtsMiningDropCapture} 完成。</p>
 */
public final class RtsRemoteDropSafetyPolicy {
    /** 超过两区块后，普通世界掉落不再视为对物理玩家安全。 */
    public static final double SAFE_WORLD_DROP_DISTANCE = 32.0D;
    private static final double SAFE_WORLD_DROP_DISTANCE_SQUARED =
            SAFE_WORLD_DROP_DISTANCE * SAFE_WORLD_DROP_DISTANCE;

    private RtsRemoteDropSafetyPolicy() { }

    public static boolean shouldForceAutoStore(double distanceSquared) {
        return !Double.isNaN(distanceSquared)
                && !Double.isInfinite(distanceSquared)
                && distanceSquared > SAFE_WORLD_DROP_DISTANCE_SQUARED;
    }
}
