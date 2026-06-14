package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 移动参数记录，由 {@link MovementModeHandler#computeParams} 返回，
 * 驱动 {@link RtsClientPathfinding#tickPre()} 的核心移动逻辑。
 *
 * @param speed                  本 tick 的目标速率（m/s，即方块/tick）；
 *                               当 {@code useInputSystem} 为 true 时此项忽略
 * @param threeDimensional       是否使用 3D 速度向量（直接指向目标）；
 *                               若为 false 则仅用水平分量，保留垂直速度（用于步行/地面）
 * @param allowSprint            此模式下是否允许疾跑
 * @param applyApproachSlowdown  接近目标时是否减速（防止越过目标）
 * @param applyEntityInsideSlow  是否应用方块减速（灵魂沙、蜂蜜块、蜘蛛网）
 * @param stuckBehavior          被卡住时的处理策略；null 表示不处理
 * @param useInputSystem         是否使用原版 Input 系统（{@code player.input.forwardImpulse}）
 *                               而非直接 {@code setDeltaMovement}。用于鞘翅飞行等需要
 *                               依赖原版物理（重力/滑翔）的移动方式。
 * @param arrivalCheckHorizontalOnly  到达检测是否仅检查 XY 平面距离，忽略 Y 轴高度。
 *                                    用于飞行/鞘翅等模式——飞过头顶就算到达。
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
     * 紧凑构造器：默认 {@code useInputSystem = false}、
     * {@code arrivalCheckHorizontalOnly = false}（使用 setDeltaMovement）。
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
     * 被卡住时的行为枚举。
     */
    public enum StuckBehavior {
        /**
         * 在地面跳跃（{@link LocalPlayer#jumpFromGround()}）。
         */
        JUMP,
        /**
         * 在液体中上浮（模拟自然浮力）。
         */
        FLOAT_UP,
        /**
         * 飞行模式下垂直抬升。
         */
        FLY_UP,
        /**
         * 不处理卡住。
         */
        NONE
    }
}
