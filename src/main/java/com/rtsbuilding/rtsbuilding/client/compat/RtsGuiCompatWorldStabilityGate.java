package com.rtsbuilding.rtsbuilding.client.compat;

import net.minecraft.core.BlockPos;

/**
 * 判定 GUI 兼容探针所处的客户端玩家坐标是否已经稳定。
 *
 * <p>该类只负责比较连续 tick 的方块坐标，不启动测试、不布置方块，也不发送网络包。
 * 把这一小段时序状态独立出来，可以稳定复现“死亡重生后玩家对象已恢复、坐标随后才跳到
 * 出生点”的竞态，避免自动探针用旧坐标布置、再用新坐标点击。</p>
 */
final class RtsGuiCompatWorldStabilityGate {
    private final int requiredStableTicks;
    private BlockPos lastPosition;
    private int stableTicks;

    RtsGuiCompatWorldStabilityGate(int requiredStableTicks) {
        if (requiredStableTicks < 1) {
            throw new IllegalArgumentException("requiredStableTicks must be positive");
        }
        this.requiredStableTicks = requiredStableTicks;
    }

    boolean tick(boolean playable, BlockPos position) {
        if (!playable || position == null) {
            reset();
            return false;
        }
        if (!position.equals(this.lastPosition)) {
            this.lastPosition = position.immutable();
            this.stableTicks = 1;
            return this.requiredStableTicks == 1;
        }
        this.stableTicks++;
        return this.stableTicks >= this.requiredStableTicks;
    }

    void reset() {
        this.lastPosition = null;
        this.stableTicks = 0;
    }

    int stableTicks() {
        return this.stableTicks;
    }
}
