package com.rtsbuilding.rtsbuilding.server.task.effect;

/**
 * Effect Ledger 的强类型目标。
 *
 * <p>wiring 层必须把真实游戏对象转换为稳定键；账本不持有玩家、世界或菜单实例。</p>
 */
public interface RtsEffectTarget {
    RtsEffectScope scope();
}
