package com.rtsbuilding.rtsbuilding.server.task.buffer;

/** 固定、可审计且有界的恢复原因；网络和 NBT 中不保存任意长度的异常文本。 */
public enum BufferRecoveryCode {
    NONE,
    SOURCE_MISSING_OR_CHANGED,
    DRAIN_OUTCOME_UNKNOWN,
    /** 阶段 B 早期 schema 1 的 Session 快照没有可证明的单一所有权，禁止自动发放。 */
    LEGACY_OWNERSHIP_UNPROVEN,
    INVALID_TRANSITION
}
