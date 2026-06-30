package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 移动参数记录 —— 驱动寻路引擎的核心数据包。
 * <p>
 * 由 {@link MovementModeHandler#computeParams} 返回，
 * {@link RtsClientPathfinding#tickPre()} 根据此参数决定每一 tick 的移动行为。
 * </p>
 *
 * @param speed                  本 tick 的目标速率（单位：方块/tick）。
 *                               当 {@link #useInputSystem} 为 {@code true} 时此项被忽略。
 * @param threeDimensional       是否使用 3D 速度向量。
 *                               {@code true} 时直接沿向量指向目标移动（用于游泳），
 *                               {@code false} 时仅水平移动，保留玩家当前的垂直速度。
 * @param allowSprint            此模式下是否允许疾跑。
 * @param applyApproachSlowdown  接近目标时是否自动减速，以防止冲过头。
 * @param applyEntityInsideSlow  是否应用方块减速效果（灵魂沙×0.4、蜂蜜块×0.5、蜘蛛网×0.25）。
 * @param stuckBehavior          被障碍物卡住时的处理策略；{@code null} 表示不处理。
 * @param useInputSystem         是否使用原版 {@code player.input.forwardImpulse} 输入系统。
 *                               {@code true} 时依赖原版物理引擎（重力、滑翔）控制速度，
 *                               寻路引擎不直接调用 {@code setDeltaMovement}。
 *                               适用于鞘翅飞行等需要原版物理模拟的模式。
 * @param arrivalCheckHorizontalOnly  到达检测是否仅检查水平（XZ）距离，忽略垂直高度。
 *        {@code true} 时只要飞到目标正上方就算到达（用于飞行/鞘翅模式）。
 */
public record MovementParams(
        double speed,
        boolean threeDimensional,
        boolean allowSprint,
        boolean applyApproachSlowdown,
        boolean applyEntityInsideSlow,
        @Nullable StuckBehavior stuckBehavior,
        boolean useInputSystem,
        boolean arrivalCheckHorizontalOnly
) {

    /**
     * 紧凑构造器，提供常用参数的便捷创建方式。
     * <p>
     * 与完整构造器相比，自动设置以下默认值：
     * <ul>
     *   <li>{@code useInputSystem = false} — 使用直接速度控制</li>
     *   <li>{@code arrivalCheckHorizontalOnly = false} — 需要同时检查垂直位置</li>
     * </ul>
     * 适用于步行、爬行、游泳等使用 {@code setDeltaMovement} 控制速度的模式。
     * </p>
     *
     * @param speed                  本 tick 的目标速率
     * @param threeDimensional       是否使用 3D 速度向量
     * @param allowSprint            是否允许疾跑
     * @param applyApproachSlowdown  接近目标时是否减速
     * @param applyEntityInsideSlow  是否应用方块减速
     * @param stuckBehavior          被卡住时的行为
     */
    public MovementParams(
            double speed,
            boolean threeDimensional,
            boolean allowSprint,
            boolean applyApproachSlowdown,
            boolean applyEntityInsideSlow,
            @Nullable StuckBehavior stuckBehavior
    ) {
        this(speed, threeDimensional, allowSprint, applyApproachSlowdown,
                applyEntityInsideSlow, stuckBehavior, false, false);
    }

    /**
     * 被障碍物卡住时的行为策略枚举。
     */
    public enum StuckBehavior {
        /**
         * 跳跃：在地面上时调用 {@link LocalPlayer#jumpFromGround()} 跳过障碍物。
         * 适用于步行和爬行模式。
         */
        JUMP,

        /**
         * 上浮：在液体中缓慢上浮，模拟自然的浮力效果。
         * 水中的上浮速度为 0.04 格/tick，其它液体为 0.02 格/tick。
         * 适用于游泳模式。
         */
        FLOAT_UP,

        /**
         * 抬升：飞行模式下垂直抬升以越过障碍物。
         * 每次施加 0.1 格/tick 的向上速度。
         * 适用于飞行和鞘翅模式。
         */
        FLY_UP,

        /**
         * 不处理：什么也不做，让玩家卡在原地。
         */
        NONE
    }
}
