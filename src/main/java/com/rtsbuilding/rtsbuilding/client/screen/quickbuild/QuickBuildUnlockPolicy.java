package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;

/**
 * 快速破坏形状的客户端可见性策略。
 *
 * <p>这里只有按钮可用性和安全回退；服务端管道仍是最终权限来源。
 * 连锁形状与范围形状必须分别由各自插件解锁。</p>
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
}
