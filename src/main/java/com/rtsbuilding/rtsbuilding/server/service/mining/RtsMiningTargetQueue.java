package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared target-locking and queue-draining rules for multi-block mining.
 *
 * <p>This class owns only deterministic target selection and queue order. It
 * deliberately does not own tool leasing, durability protection, drop pickup,
 * history, networking, or session reset. Those side effects stay in
 * {@link RtsUltimineProcessor}.</p>
 */
public final class RtsMiningTargetQueue {

    private RtsMiningTargetQueue() {
    }

    /**
     * Locks explicit destroy targets in the same order the server receives
     * them, while applying caller-supplied access and acceptance rules.
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

    /** Returns true when the current server tick may process another queued target. */
    public static boolean canProcessAnotherTargetThisTick(int processedThisTick, Deque<BlockPos> targets) {
        return processedThisTick < RtsMiningValidator.ultimineBlocksPerTick()
                && targets != null
                && !targets.isEmpty();
    }

    /** Removes and returns the next locked target from the queue. */
    public static BlockPos pollNextTarget(Deque<BlockPos> targets) {
        return targets == null || targets.isEmpty() ? null : targets.removeFirst();
    }
}
