package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * 批量挖掘/破坏的目标锁定和逐 tick 出队规则。
 *
 * <p>这个类只拥有确定性的目标选择和队列消耗顺序。工具借取、耐久保护、
 * 掉落回收、历史记录、网络同步和 session 重置仍然留在
 * {@link RtsUltimineProcessor} / {@link RtsMiningStateMachine}。</p>
 */
public final class RtsMiningTargetQueue {
    private RtsMiningTargetQueue() {
    }

    /**
     * 按服务端收到的位置顺序锁定显式破坏目标，同时应用调用方传入的访问和接受规则。
     */
    public static Deque<BlockPos> collectExplicitDestroyTargets(
            List<BlockPos> positions,
            Predicate<BlockPos> canAccessTarget,
            Predicate<BlockPos> acceptsTarget) {
        if (positions == null || positions.isEmpty() || canAccessTarget == null || acceptsTarget == null) {
            return new ArrayDeque<>();
        }
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        int maxTargets = RtsMiningValidator.areaDestroyMaxTargets();
        for (BlockPos raw : positions) {
            if (raw == null || unique.size() >= maxTargets) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!canAccessTarget.test(pos)) {
                continue;
            }
            if (!acceptsTarget.test(pos)) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    public static boolean canProcessAnotherTargetThisTick(int processedThisTick, Deque<BlockPos> targets) {
        return processedThisTick < RtsMiningValidator.ultimineBlocksPerTick()
                && targets != null
                && !targets.isEmpty();
    }

    public static BlockPos pollNextTarget(Deque<BlockPos> targets) {
        return targets == null || targets.isEmpty() ? null : targets.removeFirst();
    }
}
