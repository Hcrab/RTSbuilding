package com.rtsbuilding.rtsbuilding.server.task;

import java.util.Deque;
import java.util.function.Predicate;

/** 在固定检查上限内轮转队列，避免不可运行的队头饿死后续任务。 */
public final class BoundedQueueSelector {
    private BoundedQueueSelector() {
    }

    public static <T> Selection<T> rotateToRunnable(
            Deque<T> queue, Predicate<T> runnable, int maxInspections) {
        if (queue == null || queue.isEmpty() || maxInspections <= 0) {
            return new Selection<>(null, 0, queue == null || queue.isEmpty());
        }
        int roundSize = queue.size();
        int limit = Math.min(roundSize, maxInspections);
        for (int inspected = 1; inspected <= limit; inspected++) {
            T candidate = queue.peekFirst();
            if (candidate != null && runnable.test(candidate)) {
                return new Selection<>(candidate, inspected, false);
            }
            queue.addLast(queue.removeFirst());
        }
        return new Selection<>(null, limit, limit == roundSize);
    }

    public record Selection<T>(T value, int inspected, boolean fullRoundExhausted) {
        public boolean found() {
            return value != null;
        }
    }
}
