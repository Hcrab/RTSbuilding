package com.rtsbuilding.rtsbuilding.server.task.buffer;

/**
 * 单个物品 claim 的持久化阶段。
 *
 * <p>{@code *_RESERVED} 是必须先写入 TaskStore 并收到 ACK 的写前意图；
 * {@code DRAIN_APPLIED} 是外部储存返回结果后的写后记录。两者之间不能伪装成数据库事务。</p>
 */
public enum BufferEscrowPhase {
    /** 世界实体仍拥有物品；只能在本阶段已持久化后尝试 claim。 */
    SOURCE_PREPARED,
    /** Escrow 已拥有完整的 {@code ownedStack}。 */
    ESCROWED,
    /** Drain 写前意图已建立，等待持久化 ACK 后才允许访问外部储存。 */
    DRAIN_RESERVED,
    /** 外部写入结果已知，等待该结果持久化 ACK 后确认 remainder。 */
    DRAIN_APPLIED,
    /** 结果存在歧义，禁止自动重试、自动复制或自动丢弃。 */
    RECOVERY_REQUIRED
}
