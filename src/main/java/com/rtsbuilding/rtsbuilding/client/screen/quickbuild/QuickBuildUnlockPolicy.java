package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;

/**
 * 快速破坏形状的客户端可见性策略。
 *
 * <p>这里只根据服务端已同步的生存平衡状态和插件列表决定按钮是否可用；
 * 最终权限仍由服务端管道校验。把规则集中在这里可以避免连锁挖掘与范围挖掘
 * 再次因为面板默认状态而互相依赖。</p>
 */
final class QuickBuildUnlockPolicy {
    private QuickBuildUnlockPolicy() {
    }

    static boolean canUseDestroyShape(boolean progressionEnabled, boolean chainInstalled,
            boolean areaInstalled, AreaMineShape shape) {
        if (!progressionEnabled) {
            return true;
        }
        return shape == AreaMineShape.CHAIN ? chainInstalled : areaInstalled;
    }

    static boolean canUseAnyDestroyShape(boolean progressionEnabled, boolean chainInstalled,
            boolean areaInstalled) {
        return !progressionEnabled || chainInstalled || areaInstalled;
    }

    static AreaMineShape firstAvailableDestroyShape(boolean progressionEnabled, boolean chainInstalled,
            boolean areaInstalled) {
        if (!progressionEnabled || chainInstalled) {
            return AreaMineShape.CHAIN;
        }
        return areaInstalled ? AreaMineShape.BLOCK : null;
    }

    // ===== 智能放置模式可用性（预留扩展接口） =====

    /**
     * 判断智能放置模式是否对当前玩家可用。
     * <p>当前实现：始终可用。后续如需接入生存平衡系统，
     * 可通过此方法统一控制。</p>
     */
    static boolean canUseSmartPlace(boolean progressionEnabled, boolean smartPlaceInstalled) {
        return !progressionEnabled || smartPlaceInstalled;
    }

    /** 高级破坏解锁策略与范围破坏相同，依赖 AREA_DESTROY_PLUGIN。 */
    static boolean canUseAdvancedDestroy(boolean progressionEnabled, boolean areaInstalled) {
        return !progressionEnabled || areaInstalled;
    }
}
