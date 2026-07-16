package com.rtsbuilding.rtsbuilding.server.task.buffer;

/**
 * 旧 Session 掉落缓存向 TaskStore 交接时的所有权阶段。
 *
 * <p>阶段只能向前推进。{@link #TASK_PREPARED_ACKED} 期间 Session 中的物品只是尚未确认清除的
 * shadow，既不能再次发给玩家，Task 也不能开始写入外部储存；只有
 * {@link #SESSION_CLEAR_ACKED} 才允许 Task 消耗 escrow。</p>
 */
public enum LegacyBufferOwnershipPhase {
    /** Session 是唯一可发放权威，TaskStore 尚未确认接管。 */
    SESSION_OWNED,
    /** Task 准备快照已 durable ACK；Session 副本只能等待清除，禁止再次发放。 */
    TASK_PREPARED_ACKED,
    /** Session 清除记录已 durable ACK；Task 成为唯一可执行权威。 */
    SESSION_CLEAR_ACKED
}
