package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 移动模式策略接口。
 * <p>
 * 每一种移动模式（步行、游泳、飞行、鞘翅等）都实现此接口，
 * 封装各自的速度计算逻辑、速度向量类型（2D 水平 / 3D 直达）以及被障碍物卡住时的行为。
 * 寻路引擎在每 tick 通过 {@link #isActive(LocalPlayer)} 检测玩家当前处于哪种模式，
 * 然后使用该模式的 {@link #computeParams(LocalPlayer, Vec3, double)} 获取驱动参数。
 * </p>
 *
 * <h3>扩展机制</h3>
 * 其它模组可以通过 {@link RtsMovementModeRegistry#register(MovementModeHandler, int)}
 * 注册自定义移动模式。注册时指定的优先级越高，越优先被检测到。
 *
 * @see RtsMovementModeRegistry
 * @see RtsClientPathfinding
 * @see BuiltinMovementModes
 */
public interface MovementModeHandler {

    /**
     * 判断当前玩家是否处于此移动模式。
     * <p>
     * 实现应使用 Minecraft 原生的状态检测 API，例如：
     * <ul>
     *   <li>{@code player.getPose()} — 姿态（站立、游泳等）</li>
     *   <li>{@code player.isFallFlying()} — 鞘翅飞行</li>
     *   <li>{@code player.getAbilities().flying} — 创造飞行 / 自由飞行</li>
     *   <li>{@code player.isInWater()} / {@code player.isInLava()} — 液体检测</li>
     * </ul>
     * 这样可确保对所有模组添加的额外移动方式保持兼容。
     * </p>
     *
     * @param player 本地玩家实例
     * @return 如果玩家当前处于此移动模式则返回 {@code true}
     */
    boolean isActive(LocalPlayer player);

    /**
     * 计算当前 tick 的移动参数。
     * <p>
     * 返回的 {@link MovementParams} 包含速度值、速度向量维度（2D/3D）、
     * 是否允许疾跑、接近目标时是否减速、是否应用方块减速效果、
     * 被卡住时的行为策略等全部驱动参数。
     * 寻路引擎根据这些参数决定如何移动玩家。
     * </p>
     *
     * @param player         本地玩家实例
     * @param toTarget       从玩家当前位置指向目标位置的 3D 向量
     * @param horizontalDist 玩家到目标之间的水平距离（XZ 平面）
     * @return 驱动移动引擎的完整参数对象，不可为 {@code null}
     */
    MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist);

    /**
     * 当此模式被激活时的回调。
     * <p>
     * 当寻路引擎从其它移动模式切换到本模式时调用。
     * 可用于执行状态副作用，例如开启游泳状态：
     * {@code player.setSwimming(true)}。
     * </p>
     *
     * @param player 本地玩家实例
     */
    default void onActivate(LocalPlayer player) {
    }

    /**
     * 当此模式被停用时的回调。
     * <p>
     * 当寻路引擎从本模式切换到其它模式时调用。
     * 可用于清理状态副作用，例如关闭游泳状态：
     * {@code player.setSwimming(false)}。
     * </p>
     *
     * @param player 本地玩家实例
     */
    default void onDeactivate(LocalPlayer player) {
    }
}
