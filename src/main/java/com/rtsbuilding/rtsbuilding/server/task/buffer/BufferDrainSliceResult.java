package com.rtsbuilding.rtsbuilding.server.task.buffer;

import java.util.Objects;

/** detached drain slice 的纯值结果；外部副作用之后必须先持久化该结果，再确认 remainder。 */
public record BufferDrainSliceResult(
        BufferEscrowState state,
        int processedStacks,
        int storedItems,
        int fallbackItems,
        boolean storageChanged,
        Outcome outcome) {

    public BufferDrainSliceResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(outcome, "outcome");
        if (processedStacks < 0 || storedItems < 0 || fallbackItems < 0) {
            throw new IllegalArgumentException("slice 计数不能为负数");
        }
    }

    public enum Outcome {
        CONTINUE,
        WAITING_RESERVATION_ACK,
        WAITING_APPLIED_ACK,
        COMPLETE,
        RECOVERY_REQUIRED
    }
}
