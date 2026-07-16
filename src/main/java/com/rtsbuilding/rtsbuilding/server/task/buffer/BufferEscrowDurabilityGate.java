package com.rtsbuilding.rtsbuilding.server.task.buffer;

import java.util.Objects;

/**
 * 把 escrow 阶段映射为中央 Engine 的唯一下一步。
 *
 * <p>调用方应把 {@code currentRevisionAcknowledged} 直接绑定到
 * {@code TaskPersistenceCoordinator.hasAcknowledged(taskId, snapshot.revision())}，不能用“已写入内存
 * TaskStore”冒充磁盘 ACK。该门禁本身不持有 repository 或运行时对象。</p>
 */
public final class BufferEscrowDurabilityGate {
    private BufferEscrowDurabilityGate() {
    }

    public static Action nextAction(
            BufferEscrowState state, boolean currentRevisionAcknowledged) {
        Objects.requireNonNull(state, "state");
        if (state.requiresRecovery()) return Action.RECOVERY_REQUIRED;
        if (state.isEmpty()) return Action.COMPLETE;
        if (state.phaseCount(BufferEscrowPhase.DRAIN_APPLIED) > 0) {
            return currentRevisionAcknowledged ? Action.CONFIRM_APPLIED : Action.WAIT_DURABLE_ACK;
        }
        if (state.phaseCount(BufferEscrowPhase.SOURCE_PREPARED) > 0) {
            return currentRevisionAcknowledged ? Action.CLAIM_SOURCE : Action.WAIT_DURABLE_ACK;
        }
        if (state.phaseCount(BufferEscrowPhase.DRAIN_RESERVED) > 0) {
            return currentRevisionAcknowledged ? Action.EXECUTE_DRAIN : Action.WAIT_DURABLE_ACK;
        }
        if (state.phaseCount(BufferEscrowPhase.ESCROWED) > 0) return Action.RESERVE_DRAIN;
        return Action.RECOVERY_REQUIRED;
    }

    public enum Action {
        WAIT_DURABLE_ACK,
        CLAIM_SOURCE,
        RESERVE_DRAIN,
        EXECUTE_DRAIN,
        CONFIRM_APPLIED,
        COMPLETE,
        RECOVERY_REQUIRED
    }
}
