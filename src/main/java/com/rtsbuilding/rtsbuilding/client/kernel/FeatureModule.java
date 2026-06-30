package com.rtsbuilding.rtsbuilding.client.kernel;

import net.minecraft.client.Minecraft;

/**
 * Feature Module 接口——所有功能模块（Camera、Storage、Building 等）的基础契约。
 *
 * <p>每个 Module 自包含状态、业务逻辑、输入处理和网络回调，
 * 按功能聚合而非按层拆分。</p>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #init(RtsClientKernel)}——注册模块、订阅事件</li>
 *   <li>{@link #tick(long, int)}——每 tick 调用（时钟由 {@link EpochClock} 注入）</li>
 *   <li>{@link #onStateChange(ModuleState)}——激活级别变化通知</li>
 *   <li>{@link #onSessionEvent(StateEvent)}——事件驱动更新（替代轮询）</li>
 * </ol>
 */
public interface FeatureModule {

    /** 初始化模块。在 {@link RtsClientKernel#register(FeatureModule)} 时调用。 */
    default void init(RtsClientKernel kernel) {}

    /** 每 tick 调用一次。{@code epochMs} 和 {@code tickIndex} 来自 {@link EpochClock}。 */
    default void tick(long epochMs, int tickIndex) {}

    /** 激活级别变化通知。可用于按需订阅/取消订阅事件。 */
    default void onStateChange(ModuleState newState) {}

    /** 事件驱动更新。替代 tick 中轮询状态。 */
    default void onSessionEvent(StateEvent event) {}

    /** 获取 Minecraft 实例（便利方法）。 */
    default Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** 获取模块的唯一标识名称。 */
    String moduleId();
}
