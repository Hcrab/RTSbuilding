package com.rtsbuilding.rtsbuilding.client.kernel;

/**
 * Feature Module 激活状态。<p>
 * 每个模块通过此状态控制是否参与 tick/事件分发。
 *
 * <ul>
 *   <li>{@link #OFF} — 跳过所有 tick() 和事件分发调用</li>
 *   <li>{@link #ON} — 正常参与所有生命周期</li>
 * </ul>
 */
public enum ModuleState {
    OFF,
    ON
}
