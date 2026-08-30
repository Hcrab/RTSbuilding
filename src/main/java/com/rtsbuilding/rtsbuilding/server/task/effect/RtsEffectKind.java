package com.rtsbuilding.rtsbuilding.server.task.effect;

/**
 * 可以延迟到 tick 末提交的幂等副作用类型。
 *
 * <p>这里只允许“最新状态覆盖旧状态”的投影或持久化请求。材料扣除、工具耐久、世界修改、
 * ItemEntity 和 Capability 读写都不是副作用投影，必须继续在任务执行器主线程内立即完成。</p>
 */
public enum RtsEffectKind {
    STORAGE_VIEW_DIRTY(RtsEffectScope.PLAYER_DIMENSION),
    WORKFLOW_SNAPSHOT(RtsEffectScope.PLAYER_DIMENSION),

    /**
     * 会话持久化请求。屏障在完整快照成功进入 DataCluster 后确认这一副作用；物理刷盘仍由
     * DataCluster 和 SaveScheduler 负责。快照生成或暂存抛出异常时，本类型会保留到后续 tick；
     * 物理刷盘失败也不会清除 DataCluster 的 dirty 状态。
     */
    SESSION_PERSISTENCE(RtsEffectScope.PLAYER_GLOBAL),
    HISTORY_SNAPSHOT(RtsEffectScope.PLAYER_GLOBAL),

    /**
     * 插件状态的完整快照包。这里只代表“重新生成并发送最新插件状态”，不携带某次事件；
     * 它不适用于有顺序语义、增量语义或游戏事务语义的数据包。其他包必须先建立自己的
     * 明确投影类型，不能借用此类型后被错误合并。
     */
    PLUGIN_STATE_SNAPSHOT(RtsEffectScope.PLAYER_GLOBAL),
    PROGRESSION_STATE_SNAPSHOT(RtsEffectScope.PLAYER_GLOBAL);

    private final RtsEffectScope scope;

    RtsEffectKind(RtsEffectScope scope) {
        this.scope = scope;
    }

    public RtsEffectScope scope() {
        return scope;
    }
}
