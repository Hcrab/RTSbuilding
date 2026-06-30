package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 内置移动模式实现集合。
 * <p>
 * 提供五种开箱即用的移动模式，涵盖 Minecraft 中所有常见的玩家移动状态：
 * 步行、爬行、游泳、创造飞行、鞘翅飞行。
 * 这些模式通过 {@link RtsMovementModeRegistry#init()} 注册，
 * 检测顺序按优先级从高到低排列，确保最匹配当前状态的模式被优先选中。
 * </p>
 *
 * <h3>检测优先级（从高到低）</h3>
 * <ol>
 *   <li><b>鞘翅飞行</b>（优先级 500）— {@link LocalPlayer#isFallFlying()}</li>
 *   <li><b>创造飞行</b>（优先级 400）— {@code player.getAbilities().flying}</li>
 *   <li><b>游泳</b>（优先级 300）— {@link Pose#SWIMMING} + 水下 + 水中</li>
 *   <li><b>爬行</b>（优先级 200）— {@link Pose#SWIMMING} + 地面 + 非液体</li>
 *   <li><b>步行</b>（优先级 100）— 始终返回 {@code true}，作为兜底模式</li>
 * </ol>
 *
 * <h3>爬行检测说明</h3>
 * 在 Minecraft 1.21.1 中，当玩家处于 1.5 格高的低矮空间时，
 * 原版引擎会自动将姿态设为 {@link Pose#SWIMMING}，同时玩家
 * 处于地面且不在液体中——此即爬行状态（非游泳）。
 * 因此爬行和游泳的检测逻辑都需要仔细判断 {@code isInWater()} 和 {@code onGround()} 状态以避免误判。
 */
public final class BuiltinMovementModes {

    private BuiltinMovementModes() {
    }

    // ======================================================================
    //  鞘翅飞行模式（优先级 500）
    // ======================================================================

    /**
     * 鞘翅飞行模式。
     * <p>
     * <b>检测条件：</b>{@link LocalPlayer#isFallFlying()} 返回 {@code true}。
     * </p>
     * <p>
     * <b>行为特点：</b>使用原版 Input 系统控制（{@code player.input.forwardImpulse = 1.0F}），
     * 由 Minecraft 原版物理引擎（重力、滑翔升力）控制实际速度和轨迹。
     * 寻路引擎仅负责将玩家的朝向（yaw）和俯仰（pitch）对准目标方向。
     * 不直接调用 {@code setDeltaMovement}，以避免覆盖原版滑翔物理模拟。
     * </p>
     * <p>
     * <b>到达判定：</b>仅检查水平距离，飞到目标正上方即算到达。
     * </p>
     */
    static final MovementModeHandler ELYTRA = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.isFallFlying();
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            // speed 参数在此模式下被忽略，由原版物理引擎控制速度
            return new MovementParams(0, true, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, true, true);
        }
    };

    // ======================================================================
    //  创造飞行 / 自由飞行模式（优先级 400）
    // ======================================================================

    /**
     * 创造飞行模式（也兼容其它模组通过 {@code abilities.flying} 实现的飞行）。
     * <p>
     * <b>检测条件：</b>{@code player.getAbilities().flying} 为 {@code true}。
     * 不依赖 {@link Pose#FALL_FLYING}，因此兼容机械动力的气球、天境模组等
     * 通过修改 abilities 实现的第三方飞行方式。
     * </p>
     * <p>
     * <b>行为特点：</b>
     * <ul>
     *   <li>水平 2D 移动，不自动升降</li>
     *   <li>速度 ≈ {@code flySpeed × 4.5}</li>
     *   <li>被卡住时自动抬升以越过障碍物</li>
     *   <li>双击右键时由引擎添加垂直引导，实现精准降落</li>
     * </ul>
     * </p>
     * <p>
     * <b>到达判定：</b>仅检查水平距离，飞到目标正上方即算到达。
     * </p>
     */
    static final MovementModeHandler FLYING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return player.getAbilities().flying;
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
            double speed = player.getAbilities().getFlyingSpeed() * 4.5;
            // 水平移动 + 仅水平到达判定 + 被卡住时抬升
            return new MovementParams(speed, false, false, false, false,
                    MovementParams.StuckBehavior.FLY_UP, false, true);
        }
    };

    // ======================================================================
    //  游泳模式（优先级 300）
    // ======================================================================

    /**
     * 游泳模式。
     * <p>
     * <b>检测条件：</b>
     * 使用 Minecraft 原版的 {@link Pose#SWIMMING} 姿态判断（由 {@code Player#updateSwimming()} 自动管理），
     * 同时加上 {@code isUnderWater()} 排除浅水涉水（脚踝沾水但头在水面上）。
     * 只有 {@link Pose#SWIMMING} + {@code isUnderWater()} + {@code isInWater()} 三者同时满足才判定为游泳，
     * 避免将爬行状态（同样使用 SWIMMING 姿态）误判为游泳。
     * 同时也检测在熔岩中且不在地面的情况。
     * </p>
     * <p>
     * <b>行为特点：</b>
     * <ul>
     *   <li>3D 速度向量，直接朝目标方向移动</li>
     *   <li>速度 ≈ {@code getSpeed() × 1.6}，经水阻力衰减后约 {@code getSpeed() × 1.28}</li>
     *   <li>允许疾跑，疾跑时 {@code getSpeed()} 自带 1.3× 加成</li>
     *   <li>被卡住时自动上浮，模拟液体浮力</li>
     * </ul>
     * </p>
     */
    static final MovementModeHandler SWIMMING = new MovementModeHandler() {
        @Override
        public boolean isActive(LocalPlayer player) {
            return (player.getPose() == Pose.SWIMMING && player.isUnderWater() && player.isInWater())
                    || (player.isInLava() && !player.onGround());
        }

        @Override
        public MovementParams computeParams(LocalPlayer player, Vec3 toTarget, double horizontalDist) {
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
    //  爬行模式（优先级 200）
    // ======================================================================

    /**
     * 爬行模式。
     * <p>
     * <b>检测条件：</b>
     * 在 Minecraft 1.21.1 中，玩家处于仅 1.5 格高的低矮空间时，
     * 原版会将姿态自动设为 {@link Pose#SWIMMING}，同时玩家在
     * 地面上且不在任何液体中——这就是爬行状态（与游泳的区别在于是否在水中）。
     * </p>
     * <p>
     * <b>行为特点：</b>
     * <ul>
     *   <li>2D 水平移动</li>
     *   <li>基础移速为步行的 30%</li>
     *   <li>不允许疾跑</li>
     *   <li>应用方块减速（灵魂沙、蜂蜜块、蜘蛛网）</li>
     *   <li>接近目标时减速防止冲过头</li>
     *   <li>被卡住时跳跃</li>
     * </ul>
     * </p>
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
    //  步行模式（优先级 100，兜底）
    // ======================================================================

    /**
     * 步行模式 — 兜底模式。
     * <p>
     * <b>检测条件：</b>始终返回 {@code true}，确保在前置模式均不匹配时作为最后的选择。
     * </p>
     * <p>
     * <b>行为特点：</b>
     * <ul>
     *   <li>2D 水平移动</li>
     *   <li>完整的物理模拟：摩擦补偿、液体减速、方块减速</li>
     *   <li>允许疾跑</li>
     *   <li>接近目标时减速</li>
     *   <li>被卡住时跳跃</li>
     * </ul>
     * </p>
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
     * 计算地面移动的基础速度，再乘以指定的倍率。
     * <p>
     * <b>计算公式：</b>
     * <pre>
     * speed = player.getSpeed() × 2.15 × (0.6 / blockFriction) × multiplier
     * </pre>
     * 其中：
     * <ul>
     *   <li>{@code getSpeed()} — Minecraft 的速度属性值（包含药水效果、装备附魔等加成）</li>
     *   <li>2.15 — 速度属性值到实际方块/tick 的换算系数</li>
     *   <li>{@code 0.6 / blockFriction} — 反向补偿 {@code aiStep()} 中方块摩擦导致的减速，
     *       使不同材质的实际移速保持一致</li>
     *   <li>{@code multiplier} — 额外倍率参数（1.0 = 标准速度，0.3 = 爬行速度）</li>
     * </ul>
     * </p>
     *
     * @param player     本地玩家
     * @param multiplier 速度倍率（1.0 = 标准步行，0.3 = 爬行）
     * @return 经过摩擦补偿后的目标速度值
     */
    private static double computeGroundSpeed(LocalPlayer player, double multiplier) {
        float blockFriction = player.onGround()
                ? player.level().getBlockState(player.getOnPos()).getBlock().getFriction()
                : 0.6f;
        return player.getSpeed() * 2.15 * (0.6 / blockFriction) * multiplier;
    }

    /**
     * 检测玩家 AABB 包围盒内所有流体的通用减速因子。
     * <p>
     * 扫描玩家碰撞箱所覆盖的所有方块位置，检查是否存在流体。
     * 不同流体的减速效果不同：
     * </p>
     * <ul>
     *   <li>熔岩（{@link FluidTags#LAVA}）— 减速至 15%</li>
     *   <li>水等其它流体 — 减速至 30%</li>
     *   <li>无流体 — 不减速（返回 1.0）</li>
     * </ul>
     *
     * @param player 本地玩家
     * @return 流体减速因子（1.0 = 无减速，越小减速越严重）
     */
    private static double getFluidSlowFactor(LocalPlayer player) {
        BlockPos min = BlockPos.containing(
                player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(
                player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
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
