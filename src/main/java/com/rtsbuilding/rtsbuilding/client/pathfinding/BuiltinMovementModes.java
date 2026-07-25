package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 内置移动模式实现集合：步行、爬行、游泳、飞行、鞘翅飞行。
 * <p>
 * 每个模式通过 {@link RtsMovementModeRegistry#init()} 注册，
 * 检测顺序按优先级从高到低：鞘翅 → 飞行 → 游泳 → 爬行 → 步行。
 * <p>
 * 爬行检测：在 1.21.1 中，当玩家在 1.5 格高空间时，原版会将姿态
 * 设为 {@link Pose#SWIMMING} 同时 {@code onGround()} 且不在水中，
 * 此即「爬行」状态。
 */
public final class BuiltinMovementModes {

    private BuiltinMovementModes() {
    }

    // ======================================================================
    //  鞘翅飞行
    // ======================================================================

    /**
     * 鞘翅飞行模式。
     * <p>
     * 检测：{@link LocalPlayer#isFallFlying()}
     * 行为：使用原版 Input 系统（{@code player.input.forwardImpulse}），
     *      由 Minecraft 原版物理（重力、滑翔）控制速度。引擎仅负责
     *      将玩家朝向（yaw）和俯仰（pitch）指向目标。
     *      不直接 {@code setDeltaMovement}，避免覆盖原版滑翔物理。
     */
    static final MovementModeHandler ELYTRA = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.isFallFlying();
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            // 使用 Input 系统，speed 字段被忽略；仅 XY 到达判定
            return new MovementParams(0, true, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, true, true);
        }
    };

    // ======================================================================
    //  创造飞行 / 自由飞行
    // ======================================================================

    /**
     * 创造飞行模式。
     * <p>
     * 检测：{@code player.getAbilities().flying}
     * 行为：2D 水平移动，不自动升降；双击降落时由引擎添加垂直引导。
     *      速度 ≈ flySpeed * 4.5，被卡住时抬升。
     * <p>
     * 注意：不依赖 {@link Pose#FALL_FLYING}，仅检查 abilities — 兼容其它模组
     * 通过修改 abilities 实现的飞行（如机械动力的气球、天境模组等）。
     */
    static final MovementModeHandler FLYING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.getAbilities().flying;
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = player.getAbilities().getFlyingSpeed() * 4.5;
            // 水平移动，不自动升降；双击降落时由引擎添加垂直引导
            return new MovementParams(speed, false, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, false, true);
        }
    };

    // ======================================================================
    //  游泳
    // ======================================================================

    /**
     * 游泳模式。
     * <p>
     * 检测：使用 Minecraft 的 {@link Pose#SWIMMING} 状态（由原版 {@code net.minecraft.world.entity.player.Player#updateSwimming()}
     * 自动管理），结合 {@code isUnderWater()} 排除浅水涉水（脚踝沾水但头在水面上）。
     * <p>
     * 对于 {@link Pose#SWIMMING} 应当 {@code isUnderWater()} 且 {@code isInWater()} 才判定为游泳，
     * 否则可能将爬行（CROWLING）误判为游泳。
     */
    static final MovementModeHandler SWIMMING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return (player.getPose() == Pose.SWIMMING && player.isUnderWater() && player.isInWater())
                    || (player.isInLava() && !player.onGround());
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            // 游泳速度：getSpeed() * 1.6，经水阻力 0.8 折算后 ≈ getSpeed() * 1.28
            // 疾跑游泳自动通过 getSpeed() 获得 1.3x 加成
            double speed = player.getSpeed() * 1.6;
            return new MovementParams(speed, true, true, false, false,
                    MovementParams.StuckBehavior.FLOAT_UP);
        }

        @Override
        public void onActivate(LocalPlayer player) {
            player.setSwimming(true);
        }

        @Override
        public void onDeactivate(LocalPlayer player) {
            player.setSwimming(false);
        }
    };

    // ======================================================================
    //  爬行
    // ======================================================================

    /**
     * 爬行模式。
     * <p>
     * 检测：在 1.21.1 中，玩家处于低矮空间（1.5 格高）时原版会将姿态
     * 设为 {@link Pose#SWIMMING} 且 {@code onGround()} 但不在液体中，
     * 此即为爬行状态（非游泳）。
     * <p>
     * 行为：2D 水平移动，基础移速为步行的 30% ，不允许疾跑。
     */
    static final MovementModeHandler CRAWLING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.getPose() == Pose.SWIMMING
                    && player.onGround()
                    && !player.isInWater()
                    && !player.isInLava();
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = computeGroundSpeed(player, 0.3);
            speed *= getFluidSlowFactor(player);
            return new MovementParams(speed, false, false, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }
    };

    // ======================================================================
    //  步行（兜底模式）
    // ======================================================================

    /**
     * 步行模式（兜底）。
     * <p>
     * 检测：始终返回 {@code true}（作为最后一个检测项）。
     * 行为：2D 水平移动，完整物理（摩擦补偿、液体减速、方块减速），被卡住时跳跃。
     */
    static final MovementModeHandler WALKING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return true; // 兜底模式
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = computeGroundSpeed(player, 1.0);
            speed *= getFluidSlowFactor(player);
            return new MovementParams(speed, false, true, true, true,
                    MovementParams.StuckBehavior.JUMP);
        }

        @Override
        public void onActivate(LocalPlayer player) {
            player.setSwimming(false);
        }
    };

    // ======================================================================
    //  工具方法
    // ======================================================================

    /**
     * 计算地面移动的基础速度（含摩擦补偿），再乘以 {@code multiplier}。
     * <p>
     * 公式：{@code getSpeed() * 2.15 * (0.6 / blockFriction) * multiplier}
     * <p>
     * 其中 2.15 是速度属性值到实际 blocks/tick 的换算系数，
     * {@code 0.6 / blockFriction} 反向补偿 {@code aiStep()} 中
     * 应用方块摩擦导致的减速，使不同地面的实际移速保持一致。
     *
     * @param player     本地玩家
     * @param multiplier 额外倍率（1.0 = 标准步行，0.3 = 爬行）
     * @return 修正后的目标速度
     */
    private static double computeGroundSpeed(LocalPlayer player, double multiplier) {
        float blockFriction = player.onGround()
                ? player.level().getBlockState(player.getOnPos()).getBlock().getFriction()
                : 0.6f;
        return player.getSpeed() * 2.15 * (0.6 / blockFriction) * multiplier;
    }

    /**
     * 检测玩家 AABB 内所有流体的通用减速因子。
     * 对 {@link FluidTags#LAVA} 标签流体返回 0.15，其它流体返回 0.3，无流体返回 1.0。
     */
    private static double getFluidSlowFactor(LocalPlayer player) {
        BlockPos min = BlockPos.containing(player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            FluidState fluidState = player.level().getFluidState(pos);
            if (!fluidState.isEmpty()) {
                if (fluidState.is(FluidTags.LAVA)) return 0.15;
                return 0.3;
            }
        }
        return 1.0;
    }
}
