package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 生成器共享的有界目标集合与停止信号。
 * 目标容量是实际任务/预览能够表达的唯一坐标数，不是包围盒体积。
 */
final class ShapeGeometryBudget {
    private ShapeGeometryBudget() {
    }

    static final class LimitExceeded extends RuntimeException {
        final long discoveredTargets;

        LimitExceeded(long discoveredTargets) {
            this.discoveredTargets = discoveredTargets;
        }
    }

    static final class TargetSet extends LinkedHashSet<BlockPos> {
        final int limit;

        TargetSet(int requestedLimit) {
            this.limit = Math.max(1, Math.min(MiningLimits.MAX_VOLUME, requestedLimit));
        }

        @Override
        public boolean add(BlockPos position) {
            if (position == null || contains(position)) {
                return false;
            }
            if (size() >= this.limit) {
                throw new LimitExceeded((long) this.limit + 1L);
            }
            return super.add(position);
        }

        @Override
        public boolean addAll(Collection<? extends BlockPos> positions) {
            boolean changed = false;
            if (positions != null) {
                for (BlockPos position : positions) {
                    changed |= add(position);
                }
            }
            return changed;
        }
    }

    static int targetLimit(Set<BlockPos> targets) {
        return targets instanceof TargetSet bounded
                ? bounded.limit
                : MiningLimits.MAX_VOLUME;
    }
}
