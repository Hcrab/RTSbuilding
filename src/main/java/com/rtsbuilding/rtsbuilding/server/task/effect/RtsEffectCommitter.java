package com.rtsbuilding.rtsbuilding.server.task.effect;

/**
 * Wiring 层实现的单目标提交器。
 *
 * <p>实现可以只确认部分类型；未确认类型由 Barrier 留到后续 Tick 重试。实现不得在这里执行
 * 材料、工具、世界或 Capability 事务。</p>
 */
@FunctionalInterface
public interface RtsEffectCommitter<K extends RtsEffectTarget> {
    RtsEffectCommitResult commit(K key, RtsEffectSet effects) throws Exception;
}
