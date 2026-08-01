package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 策略接口：每一种移动模式实现此接口，封装速度计算、速度向量类型和被卡住时的行为。
 * <p>
 * 其它模组可以通过 {@link RtsMovementModeRegistry#register(MovementModeHandler)}
 * 注册自定义移动模式，优先级越高的 handler 会先被检测。
 *
 * @see RtsMovementModeRegistry
 * @see RtsClientPathfinding
 */
public interface MovementModeHandler {

    /**
     * 判断当前玩家是否处于此移动模式。
     * <p>
     * 实现应使用 Minecraft 原生的状态检测（{@code player.getPose()}、
     * {@code player.isFallFlying()}、{@code player.getAbilities().flying} 等），
     * 以确保对所有模组添加的移动方式兼容。
     */
    boolean isActive(LocalPlayer player);

    /**
     * 计算当前 tick 的移动参数（速度、向量维度、行为开关）。
     *
     * @param player         本地玩家
     * @param toTarget       从玩家位置指向目标位置的向量（3D）
     * @param horizontalDist 水平距离
     * @return 驱动移动引擎的完整参数，不可为 null
     */
    MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist);

    /**
     * 当此模式被激活时调用（即从其它模式切换到此模式）。
     * 可用于设置 {@code player.setSwimming(true)} 等副作用。
     */
    default void onActivate(LocalPlayer player) {
    }

    /**
     * 当此模式被停用时调用（即从此模式切换到其它模式）。
     * 可用于清理 {@code player.setSwimming(false)} 等副作用。
     */
    default void onDeactivate(LocalPlayer player) {
    }
}
