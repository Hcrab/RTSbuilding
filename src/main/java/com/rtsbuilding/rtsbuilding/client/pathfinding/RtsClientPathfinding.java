package com.rtsbuilding.rtsbuilding.client.pathfinding;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端自动寻路引擎 — 通过每 tick 设置玩家速度将本地玩家移向目标方块。
 * <p>
 * 核心机制：在 {@link ClientTickEvent.Pre} 阶段（即 Minecraft 原版 {@code aiStep()} 之前）
 * 计算并设置玩家的速度向量。之后原版的物理引擎会接手处理碰撞检测、位置同步
 * （自动发送 {@code ServerboundMovePlayerPacket}）和行走动画等。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>调用 {@link #goTo(BlockPos)} 或 {@link #goToAbove(BlockPos, int)} 设置目标</li>
 *   <li>每 tick 由 {@link #tickPre()} 驱动，执行以下步骤：
 *     <ul>
 *       <li>检测玩家是否到达目标</li>
 *       <li>根据当前姿态选择移动模式（飞行/步行/游泳/鞘翅等）</li>
 *       <li>计算速度向量并设置到玩家</li>
 *       <li>调整玩家朝向（yaw/pitch）对准目标</li>
 *       <li>处理被卡住的情况</li>
 *     </ul>
 *   </li>
 *   <li>到达目标后自动停止，触发高亮淡出动画</li>
 * </ol>
 *
 * <h3>移动模式系统</h3>
 * 每种移动模式（飞行、步行、游泳、爬行、鞘翅）是一个独立的 {@link MovementModeHandler} 实现，
 * 通过 {@link RtsMovementModeRegistry} 按优先级注册。
 * 引擎每 tick 自动选择当前最匹配的模式。
 *
 * @see RtsMovementModeRegistry
 * @see MovementModeHandler
 * @see BuiltinMovementModes
 */
public final class RtsClientPathfinding {

    // ======================================================================
    //  寻路状态
    // ======================================================================

    /** 当前目标方块位置。{@code null} 表示没有活跃的移动目标。 */
    private static BlockPos target = null;

    /** 上一个激活的移动模式 handler，用于检测模式切换时的生命周期回调。 */
    private static MovementModeHandler previousMode = null;

    /** 高亮显示的方块位置（与 target 相同，但独立控制淡出动画的生命周期）。 */
    private static BlockPos highlightedTarget = null;

    /** 高亮淡出动画的开始时间戳（ms）。 */
    private static long highlightFadeStartedAtMs = 0L;

    /** 是否正在执行高亮淡出动画。 */
    private static boolean highlightFading = false;

    /**
     * 目标点 Y 轴偏移量（单位：格）。
     * <p>
     * 当值 &gt; 0 时，目标位置为目标方块坐标 + 此偏移量。
     * 用于「飞到目标上方」模式（双击右键），到达判定时要求 3D 接近，
     * 不执行仅水平的到达检查。
     * </p>
     */
    private static int targetYOffset = 0;

    // ======================================================================
    //  常量
    // ======================================================================

    /** 到达判定：水平距离平方阈值。当玩家到目标的水平距离平方小于此值时认为水平到达。 */
    private static final double REACH_DISTANCE_SQ = 0.1 * 0.1;

    /** 向量零长度判断阈值，避免除零异常。 */
    private static final double EPSILON = 0.01;

    /** 目标高亮淡出动画的持续时间（ms）。 */
    private static final long TARGET_HIGHLIGHT_FADE_MS = 350L;

    private RtsClientPathfinding() {
    }

    // ======================================================================
    //  公共 API — 开始 / 停止寻路
    // ======================================================================

    /**
     * 开始向目标方块自动移动。
     * <p>
     * 设置目标后，引擎将在每 tick 驱动玩家移向目标位置。
     * 到达后自动停止（飞行/鞘翅模式仅检查水平距离，步行模式需要同时到达地面高度）。
     * 同时向服务端发送寻路数据包，用于服务端状态追踪和离玩家清理。
     * </p>
     *
     * @param target 目标方块位置
     */
    public static void goTo(BlockPos target) {
        RtsClientPathfinding.target = target.immutable();
        targetYOffset = 0;
        setHighlightedTarget(RtsClientPathfinding.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    /**
     * 开始向目标方块上方移动，实现精准降落。
     * <p>
     * 与 {@link #goTo(BlockPos)} 不同的是，此方法在目标 Y 坐标上增加偏移量，
     * 使玩家飞到目标方块的正上方。到达判定要求同时满足水平和垂直距离，
     * 到达后会关闭创造飞行模式，让玩家在重力作用下自然降落在方块表面上。
     * 适用于玩家在飞行状态下双击右键精准降落在指定方块上。
     * </p>
     *
     * @param target  要降落到的目标方块
     * @param yOffset 目标方块上方的偏移格数（推荐传 1，即降落在方块表面）
     */
    public static void goToAbove(BlockPos target, int yOffset) {
        RtsClientPathfinding.target = target.immutable();
        targetYOffset = Math.max(1, yOffset);
        setHighlightedTarget(RtsClientPathfinding.target);
        RtsClientPacketGateway.sendPathfindingGoTo(target);
    }

    /**
     * 取消当前的寻路移动，清理所有状态。
     * <p>
     * 停止移动、清理模式切换状态、清除目标高亮。
     * 在玩家退出 RTS 模式、死亡或切换维度时自动调用。
     * </p>
     */
    public static void cancel() {
        stopMovement();
        clearHighlightedTarget();
    }

    /**
     * 判断当前是否正在执行寻路移动。
     *
     * @return 如果有活跃的目标则返回 {@code true}
     */
    public static boolean isMoving() {
        return target != null;
    }

    // ======================================================================
    //  目标高亮渲染
    // ======================================================================

    /**
     * 获取目标方块的高亮渲染数据。
     * <p>
     * 当寻路活跃时返回完全不透明（alpha=1.0）的高亮；
     * 到达后返回逐渐淡出的高亮（alpha 从 1.0 线性衰减到 0）；
     * 淡出完成后返回 {@code null}。
     * 由外部的渲染管线调用，用于在世界中绘制目标标记。
     * </p>
     *
     * @return 高亮数据（含方块位置和 alpha 透明度），无可渲染的高亮时返回 {@code null}
     */
    @Nullable
    public static MoveTargetHighlight getMoveTargetHighlight() {
        if (highlightedTarget == null) {
            return null;
        }
        if (!highlightFading) {
            return new MoveTargetHighlight(highlightedTarget, 1.0F);
        }
        long elapsed = System.currentTimeMillis() - highlightFadeStartedAtMs;
        if (elapsed >= TARGET_HIGHLIGHT_FADE_MS) {
            clearHighlightedTarget();
            return null;
        }
        float alpha = 1.0F - (elapsed / (float) TARGET_HIGHLIGHT_FADE_MS);
        return new MoveTargetHighlight(highlightedTarget, Math.max(0.0F, alpha));
    }

    // ======================================================================
    //  Tick 驱动 — 由 ClientTickEvent.Pre 每帧调用
    // ======================================================================

    /**
     * 每帧 tick 的寻路处理入口。
     * <p>
     * 在 {@link ClientTickEvent.Pre} 阶段（原版 {@code aiStep()} 之前）被调用。
     * 如果当前没有活跃目标则直接返回。否则执行以下步骤：
     * </p>
     * <ol>
     *   <li>确保移动模式注册表已初始化</li>
     *   <li>检查 RTS 相机模块是否激活，未激活则取消寻路</li>
     *   <li>计算玩家到目标的 3D 向量</li>
     *   <li>将玩家朝向对准目标方向</li>
     *   <li>解析当前移动模式并获取移动参数</li>
     *   <li>检查是否已到达目标</li>
     *   <li>调整俯仰角</li>
     *   <li>应用疾跑逻辑</li>
     *   <li>计算并设置速度向量</li>
     *   <li>处理被障碍物卡住的情况</li>
     * </ol>
     */
    public static void tickPre() {
        if (target == null) return;

        // 确保移动模式注册表在首次 tick 时完成初始化
        RtsMovementModeRegistry.init();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // 玩家不存在（如正在加载世界）时取消寻路
        if (player == null) {
            cancel();
            return;
        }

        // 检查 RTS 模式是否激活：通过 CameraModule 判断
        // 如果 RTS 模式已关闭，则应停止寻路，让玩家恢复自由移动
        CameraModule cam = RtsClientKernel.get().module(CameraModule.class);
        if (cam == null || !cam.getState().isEnabled()) {
            cancel();
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 targetPos = computeTargetPos();
        Vec3 toTarget = targetPos.subtract(playerPos);
        Vec3 horizontal = new Vec3(toTarget.x, 0, toTarget.z);
        double horizontalDist = horizontal.length();

        // 步骤 1：将玩家的水平朝向（yaw）对准目标方向
        faceTarget(player, toTarget);

        // 步骤 2：根据玩家当前姿态选择移动模式，获取移动参数
        MovementParams params = resolveMode(player);
        if (params == null) {
            // 没有任何移动模式匹配（不应发生），安全取消
            cancel();
            return;
        }

        // 步骤 3：检查是否已经到达目标位置
        if (isArrived(player, playerPos, targetPos, params)) {
            finishArrived();
            return;
        }

        // 步骤 4：调整俯仰角（仅鞘翅模式需要精确俯仰）
        applyPitch(player, toTarget, horizontalDist, params);

        // 步骤 5：应用疾跑逻辑
        applySprint(player, params);

        // 步骤 6：计算并应用速度向量
        applyVelocity(player, toTarget, horizontal, horizontalDist, targetPos, playerPos, params);

        // 步骤 7：如果玩家被障碍物卡住且目标在上方，执行卡住处理
        if (player.horizontalCollision && target.getY() + 1.0 > player.position().y + 0.2) {
            handleStuck(player, params);
        }
    }

    // ======================================================================
    //  内部方法 — 停止移动
    // ======================================================================

    /**
     * 停止所有移动，清理目标状态和模式切换状态。
     * <p>
     * 通知当前激活的模式 handler 执行停用回调，
     * 重置目标位置和 Y 偏移量。
     * </p>
     */
    private static void stopMovement() {
        target = null;
        targetYOffset = 0;
        if (previousMode != null && Minecraft.getInstance().player instanceof LocalPlayer lp) {
            previousMode.onDeactivate(lp);
        }
        previousMode = null;
    }

    /**
     * 到达目标后的收尾处理。
     * <p>
     * 停止移动并开始高亮淡出动画。
     * </p>
     */
    private static void finishArrived() {
        stopMovement();
        beginHighlightFade();
    }

    // ======================================================================
    //  内部方法 — 目标位置计算
    // ======================================================================

    /**
     * 计算目标位置的 3D 坐标。
     * <p>
     * 普通模式（{@code targetYOffset == 0}）：使用目标方块的碰撞箱顶面实际高度，
     * 正确处理台阶（Y+0.5）、地毯（Y+0.0625）、楼梯等非整格方块。
     * 若目标方块无碰撞体积（空气、火把等），则递归查找下方方块的顶面。
     * </p>
     * <p>
     * 精准降落模式（{@code targetYOffset > 0}）：使用固定偏移量，
     * 例如 Y+1 表示降落在方块顶面上方一格。
     * </p>
     *
     * @return 目标位置的 3D 坐标（中心点 + 计算后的 Y 值）
     */
    private static Vec3 computeTargetPos() {
        double y;
        if (targetYOffset > 0) {
            y = target.getY() + targetYOffset;
        } else {
            y = getBlockSurfaceY(target);
        }
        return new Vec3(target.getX() + 0.5, y, target.getZ() + 0.5);
    }

    /**
     * 获取指定方块位置顶面的 Y 坐标。
     * <p>
     * 使用方块的实际碰撞箱形状计算顶面高度，处理逻辑如下：
     * <ul>
     *   <li>完整方块 → 返回 {@code pos.getY() + 1.0}</li>
     *   <li>下半台阶 → 返回 {@code pos.getY() + 0.5}</li>
     *   <li>地毯 → 返回 {@code pos.getY() + 0.0625}</li>
     *   <li>无碰撞方块（空气、火把、告示牌等）→ 检查下方方块</li>
     *   <li>连续两个无碰撞方块 → 返回 {@code pos.getY() + 0.5}（目标方块中心）</li>
     * </ul>
     * </p>
     *
     * @param pos 要查询的方块位置
     * @return 该方块顶面的世界 Y 坐标
     */
    private static double getBlockSurfaceY(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return pos.getY() + 1.0;

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape collisionShape = state.getCollisionShape(mc.level, pos);

        if (!collisionShape.isEmpty()) {
            // 方块有碰撞体积 → 使用实际顶面高度
            return pos.getY() + collisionShape.max(Direction.Axis.Y);
        }

        // 方块无碰撞体积（空气、火把、告示牌等）→ 检查下方方块
        BlockPos below = pos.below();
        BlockState belowState = mc.level.getBlockState(below);
        VoxelShape belowShape = belowState.getCollisionShape(mc.level, below);

        if (!belowShape.isEmpty()) {
            return below.getY() + belowShape.max(Direction.Axis.Y);
        }

        // 连续两个方块都没有碰撞体积 → 目标位置取目标方块中心高度
        return pos.getY() + 0.5;
    }

    // ======================================================================
    //  内部方法 — 朝向与姿态
    // ======================================================================

    /**
     * 将玩家的水平朝向（yaw）对准目标方向。
     * <p>
     * 同时设置 {@code yRot}（实际旋转）、{@code yHeadRot}（头部旋转）、
     * {@code yBodyRot}（身体旋转）和对应的插值旧值，确保渲染无延迟。
     * </p>
     *
     * @param player   本地玩家
     * @param toTarget 从玩家指向目标的 3D 向量
     */
    private static void faceTarget(LocalPlayer player, Vec3 toTarget) {
        float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
    }

    /**
     * 根据移动模式调整玩家的俯仰角（pitch）。
     * <ul>
     *   <li>使用 Input 系统的模式（鞘翅）：仅有精准降落时需要俯仰，飞越模式保持当前俯仰</li>
     *   <li>速度驱动模式：俯仰置零（水平视线），垂直移动由速度向量控制</li>
     * </ul>
     *
     * @param player         本地玩家
     * @param toTarget       从玩家指向目标的 3D 向量
     * @param horizontalDist 水平距离
     * @param params         当前移动参数
     */
    private static void applyPitch(LocalPlayer player, Vec3 toTarget, double horizontalDist, MovementParams params) {
        if (params.useInputSystem()) {
            if (targetYOffset > 0) {
                float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDist + EPSILON));
                player.setXRot(pitch);
            }
            // 飞越模式：保持当前俯仰不变
        } else {
            player.setXRot(0);
        }
    }

    // ======================================================================
    //  内部方法 — 速度计算与施加
    // ======================================================================

    /**
     * 解析当前玩家的移动模式。
     * <p>
     * 通过 {@link RtsMovementModeRegistry#findActive(LocalPlayer)} 获取当前匹配的移动模式，
     * 并自动管理模式切换的生命周期（调用 old mode 的 {@code onDeactivate()} 和 new mode 的
     * {@code onActivate()}）。
     * </p>
     *
     * @param player 本地玩家
     * @return 当前移动参数；如果没有任何模式匹配则返回 {@code null}
     */
    @Nullable
    private static MovementParams resolveMode(LocalPlayer player) {
        MovementModeHandler currentMode = RtsMovementModeRegistry.findActive(player);
        if (currentMode == null) return null;

        // 处理模式切换生命周期
        if (currentMode != previousMode) {
            if (previousMode != null) previousMode.onDeactivate(player);
            currentMode.onActivate(player);
            previousMode = currentMode;
        }

        Vec3 toTarget = new Vec3(
                target.getX() + 0.5 - player.position().x,
                target.getY() + (targetYOffset > 0 ? targetYOffset : 1.0) - player.position().y,
                target.getZ() + 0.5 - player.position().z);
        double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        return currentMode.computeParams(player, toTarget, horizontalDist);
    }

    /**
     * 应用疾跑逻辑。
     * <p>
     * 疾跑仅在满足以下条件时启用：
     * <ul>
     *   <li>移动模式允许疾跑</li>
     *   <li>玩家不在飞行状态</li>
     *   <li>玩家饱食度 &gt; 6</li>
     *   <li>玩家未使用物品</li>
     *   <li>玩家在地面、水中或熔岩中</li>
     * </ul>
     * </p>
     *
     * @param player 本地玩家
     * @param params 当前移动参数
     */
    private static void applySprint(LocalPlayer player, MovementParams params) {
        if (params.allowSprint()) {
            boolean canSprint = !player.getAbilities().flying
                    && player.getFoodData().getFoodLevel() > 6
                    && !player.isUsingItem()
                    && (player.onGround() || player.isInWater() || player.isInLava());
            player.setSprinting(canSprint);
        } else {
            player.setSprinting(false);
        }
    }

    /**
     * 计算并施加速度向量。
     * <p>
     * 根据移动模式采用不同的速度控制策略：
     * <ul>
     *   <li><b>Input 系统模式</b>（鞘翅）：设置 {@code forwardImpulse = 1.0F}，
     *       由原版物理引擎控制速度</li>
     *   <li><b>3D 模式</b>（游泳）：沿 3D 向量直接朝目标移动</li>
     *   <li><b>2D 模式</b>（步行/飞行）：水平移动，保留原垂直速度；
     *       精准降落时添加垂直引导</li>
     * </ul>
     * 接近目标时自动降低速度以防止冲过头。
     * 需要时应用方块减速效果（灵魂沙、蜂蜜块、蜘蛛网）。
     * </p>
     *
     * @param player         本地玩家
     * @param toTarget       从玩家指向目标的 3D 向量
     * @param horizontal     水平分量向量
     * @param horizontalDist 水平距离
     * @param targetPos      目标位置
     * @param playerPos      玩家当前位置
     * @param params         当前移动参数
     */
    private static void applyVelocity(LocalPlayer player, Vec3 toTarget, Vec3 horizontal,
                                       double horizontalDist, Vec3 targetPos, Vec3 playerPos,
                                       MovementParams params) {
        // Input 系统模式：由原版物理引擎接管速度控制
        if (params.useInputSystem()) {
            player.input.forwardImpulse = 1.0F;
            player.hurtMarked = true;
            return;
        }

        if (horizontalDist <= EPSILON) return;

        double speed = params.speed();

        // 接近目标时减速，防止冲过头
        if (params.applyApproachSlowdown() && horizontalDist < 0.5) {
            speed *= horizontalDist / 0.5;
        }

        if (params.threeDimensional()) {
            // 3D 速度：直接沿向量指向目标（用于游泳）
            double dist3D = toTarget.length();
            if (dist3D > EPSILON) {
                player.setDeltaMovement(toTarget.scale(speed / dist3D));
            }
        } else {
            // 2D 速度：仅水平移动
            Vec3 velocity = horizontal.scale(speed / horizontalDist);

            if (targetYOffset > 0) {
                // 精准降落：添加轻柔的垂直引导
                double dy = targetPos.y - playerPos.y;
                double vertSpeed = Math.min(Math.abs(dy) * 0.15, 0.4) * Math.signum(dy);
                velocity = new Vec3(velocity.x, vertSpeed, velocity.z);
            } else {
                // 保留玩家原本的垂直速度（保持重力/浮力效果）
                velocity = new Vec3(velocity.x, player.getDeltaMovement().y, velocity.z);
            }

            // 应用方块减速效果（灵魂沙、蜂蜜块、蜘蛛网）
            if (params.applyEntityInsideSlow()) {
                velocity = applyEntityInsideSlow(player, velocity);
            }
            player.setDeltaMovement(velocity);
        }

        player.hurtMarked = true;
    }

    /**
     * 应用方块实体触碰减速效果。
     * <p>
     * 模拟原版 {@code entityInside()} 对玩家 AABB 覆盖方块的减速效果：
     * </p>
     * <ul>
     *   <li>灵魂沙：速度 × 0.4（水平方向）</li>
     *   <li>蜂蜜块：速度 × 0.5（水平方向）</li>
     *   <li>蜘蛛网：速度 × 0.25（水平） × 0.05（垂直）</li>
     * </ul>
     *
     * @param player   本地玩家
     * @param velocity 当前计算的速度向量
     * @return 应用减速后的速度向量
     */
    private static Vec3 applyEntityInsideSlow(LocalPlayer player, Vec3 velocity) {
        BlockPos min = BlockPos.containing(
                player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(
                player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        Vec3 result = velocity;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.SOUL_SAND)) {
                result = result.multiply(0.4, 1.0, 0.4);
            } else if (state.is(Blocks.HONEY_BLOCK)) {
                result = result.multiply(0.5, 1.0, 0.5);
            } else if (state.is(Blocks.COBWEB)) {
                result = result.multiply(0.25, 0.05, 0.25);
            }
        }
        return result;
    }

    // ======================================================================
    //  内部方法 — 到达检测与卡住处理
    // ======================================================================

    /**
     * 检查玩家是否已经到达目标位置。
     * <p>
     * 两种模式：
     * <ul>
     *   <li><b>精准降落模式</b>（{@code targetYOffset &gt; 0}）：要求水平和垂直距离
     *       同时小于阈值。到达后会关闭创造飞行模式，让玩家在重力下落在地表。</li>
     *   <li><b>普通模式</b>：水平距离达标后根据移动模式决定是否需要检查垂直位置。
     *       飞行/鞘翅模式仅检查水平距离（飞到正上方就算到达），
     *       步行模式还需要玩家 Y 达到目标高度。</li>
     * </ul>
     * </p>
     *
     * @param player    本地玩家
     * @param playerPos 玩家当前位置
     * @param targetPos 目标位置
     * @param params    当前移动参数
     * @return 如果玩家已到达目标位置则返回 {@code true}
     */
    private static boolean isArrived(LocalPlayer player, Vec3 playerPos, Vec3 targetPos, MovementParams params) {
        double dx = playerPos.x - targetPos.x;
        double dz = playerPos.z - targetPos.z;
        double horizDistSq = dx * dx + dz * dz;

        if (targetYOffset > 0) {
            // 精准降落模式：水平和垂直距离都必须足够近
            if (horizDistSq < 0.25) { // 水平距离 < 0.5 格
                double dy = playerPos.y - targetPos.y;
                if (Math.abs(dy) < 0.5) {
                    // 已到达目标点上方 → 关闭创造飞行，让重力完成降落
                    // 利用原版碰撞箱机制处理各种方块形状（台阶、楼梯、地毯等）
                    if (player.getAbilities().flying && !player.isFallFlying()) {
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    return true;
                }
            }
            return false;
        }

        // 普通模式：水平距离检查
        if (horizDistSq >= REACH_DISTANCE_SQ) return false;

        // 水平已到达，根据模式决定是否需要垂直检查
        // 飞行/鞘翅：水平到达即算完成
        // 步行：还需要玩家的 Y 坐标达到目标高度
        return params.arrivalCheckHorizontalOnly() || playerPos.y >= targetPos.y;
    }

    /**
     * 处理玩家被障碍物卡住的情况。
     * <p>
     * 当玩家水平碰撞到障碍物且目标位置高于玩家时触发。
     * 根据移动模式配置的 {@link MovementParams.StuckBehavior} 执行不同的脱困策略：
     * </p>
     * <ul>
     *   <li>{@code JUMP}：在地面上时跳跃，翻过障碍物</li>
     *   <li>{@code FLOAT_UP}：在液体中上浮（模拟浮力），同时清零水平速度防止被推向岸边</li>
     *   <li>{@code FLY_UP}：飞行模式下施加向上速度，越过障碍物</li>
     *   <li>{@code NONE} / {@code null}：不处理</li>
     * </ul>
     *
     * @param player 本地玩家
     * @param params 当前移动参数
     */
    private static void handleStuck(LocalPlayer player, MovementParams params) {
        MovementParams.StuckBehavior behavior = params.stuckBehavior();
        if (behavior == null || behavior == MovementParams.StuckBehavior.NONE) return;

        switch (behavior) {
            case JUMP -> {
                if (player.onGround()) {
                    player.jumpFromGround();
                    player.hurtMarked = true;
                }
            }
            case FLOAT_UP -> {
                // 原版 LivingEntity.travel() 每 tick 在水中 +0.04 上浮速度（自然浮力）
                // 此处模拟同样的行为，让玩家在液体中缓慢上升
                // 关键：水平速度清零，防止游泳分支每 tick 将玩家推向岸边
                double floatSpeed = player.isInWater() ? 0.04 : 0.02;
                player.setDeltaMovement(0, floatSpeed, 0);
                player.hurtMarked = true;
            }
            case FLY_UP -> {
                // 飞行模式下温和地向上抬升以越过障碍物
                player.setDeltaMovement(player.getDeltaMovement().x, 0.1, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
        }
    }

    // ======================================================================
    //  内部方法 — 高亮管理
    // ======================================================================

    /**
     * 设置目标高亮方块。
     *
     * @param pos 要高亮显示的方块位置；{@code null} 清除高亮
     */
    private static void setHighlightedTarget(BlockPos pos) {
        highlightedTarget = pos == null ? null : pos.immutable();
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    /** 开始目标高亮的淡出动画。 */
    private static void beginHighlightFade() {
        if (highlightedTarget == null) {
            return;
        }
        highlightFadeStartedAtMs = System.currentTimeMillis();
        highlightFading = true;
    }

    /** 清除目标高亮，重置淡出动画状态。 */
    private static void clearHighlightedTarget() {
        highlightedTarget = null;
        highlightFadeStartedAtMs = 0L;
        highlightFading = false;
    }

    // ======================================================================
    //  内部数据结构
    // ======================================================================

    /**
     * 目标方块高亮渲染数据。
     *
     * @param target 目标方块位置
     * @param alpha  高亮透明度（0.0 = 完全透明，1.0 = 完全不透明）
     */
    public record MoveTargetHighlight(BlockPos target, float alpha) {
    }
}
