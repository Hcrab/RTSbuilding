package com.rtsbuilding.rtsbuilding.server.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * 临时上下文切换工具集。
 *
 * <p>在 RTS 模式下，玩家处于自由视角而非第一人称，放置/交互时需要临时
 * 切换玩家的位置、朝向、主手物品、Shift 状态等上下文，模拟"在目标位置
 * 以正确姿态执行操作"。所有切换都会在操作完成后自动恢复。
 *
 * <p>每个方法都是纯静态的：临时状态是函数式作用域（try/finally 自动还原），
 * 不会泄漏到玩家实体上。
 */
public final class TemporaryContextSwitcher {

    private TemporaryContextSwitcher() {
    }

    // ======================================================================
    //  Ray Context
    // ======================================================================

    /**
     * 从客户端发送的射线原点和方向构造 {@link RayContext}。
     * 无效输入（NaN、零向量）返回 null。
     */
    public static RayContext parseRayContext(
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(dirX) || !Double.isFinite(dirY) || !Double.isFinite(dirZ)) {
            return null;
        }
        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        if (dir.lengthSqr() < 1.0e-6D) {
            return null;
        }
        return new RayContext(new Vec3(originX, originY, originZ), dir.normalize());
    }

    // ======================================================================
    //  Position & Look Context
    // ======================================================================

    /**
     * 基于客户端射线方向构造虚拟交互上下文（位置 + 注视方向），
     * 执行 {@code action} 后自动恢复玩家的原始位置和朝向。
     */
    public static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            RayContext rayContext, double reach, Supplier<T> action) {
        if (rayContext == null) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        Vec3 rayDir = rayContext.dir();
        if (!Double.isFinite(rayDir.x) || !Double.isFinite(rayDir.y) || !Double.isFinite(rayDir.z)
                || rayDir.lengthSqr() < 1.0e-6D) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        double clampedReach = Math.max(2.0D, Math.min(8.0D, reach));
        double offset = Math.max(0.5D, clampedReach - 0.1D);
        Vec3 normalizedDir = rayDir.normalize();
        Vec3 virtualEye = fallbackLookAt.subtract(normalizedDir.scale(offset));
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 virtualFeet = new Vec3(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        Vec3 lookAt = virtualEye.add(normalizedDir.scale(clampedReach));
        return withTemporaryInteractionPosition(player, virtualFeet, lookAt, action);
    }

    public static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            double reach, Supplier<T> action) {
        return withTemporaryUseItemContext(player, fallbackPos, fallbackLookAt, null, reach, action);
    }

    // ======================================================================
    //  Shift Key 状态
    // ======================================================================

    /**
     * 临时设置玩家的潜行状态，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporaryShiftKey(ServerPlayer player, boolean active, Supplier<T> action) {
        boolean previous = player.isShiftKeyDown();
        if (previous == active) {
            return action.get();
        }
        player.setShiftKeyDown(active);
        try {
            return action.get();
        } finally {
            player.setShiftKeyDown(previous);
        }
    }

    // ======================================================================
    //  Main Hand Item
    // ======================================================================

    /**
     * 临时替换玩家的主手物品，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    // ======================================================================
    //  OnGround 状态
    // ======================================================================

    /**
     * 临时设置玩家的 onGround 状态（影响挖掘速度计算），执行后恢复。
     */
    public static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    // ======================================================================
    //  Selected Hotbar Slot
    // ======================================================================

    /**
     * 临时切换玩家的选中快捷栏格，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = Math.max(0, Math.min(8, toolSlot));
        int prevSelected = player.getInventory().selected;
        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    // ======================================================================
    //  Internal: Position + LookAt
    // ======================================================================

    private static <T> T withTemporaryInteractionPosition(ServerPlayer player, Vec3 position, Vec3 lookAt, Supplier<T> action) {
        Vec3 prevPos = player.position();
        float prevYRot = player.getYRot();
        float prevXRot = player.getXRot();
        float prevYHeadRot = player.getYHeadRot();
        float prevYBodyRot = player.yBodyRot;

        player.setPos(position.x, position.y, position.z);
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 eyePos = new Vec3(position.x, position.y + eyeHeight, position.z);
        float[] look = yawPitchTo(eyePos, lookAt);
        player.setYRot(look[0]);
        player.setXRot(look[1]);
        player.setYHeadRot(look[0]);
        player.yBodyRot = look[0];
        try {
            return action.get();
        } finally {
            player.setPos(prevPos.x, prevPos.y, prevPos.z);
            player.setYRot(prevYRot);
            player.setXRot(prevXRot);
            player.setYHeadRot(prevYHeadRot);
            player.yBodyRot = prevYBodyRot;
        }
    }

    private static float[] yawPitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, xz)));
        return new float[]{yaw, pitch};
    }

    // ======================================================================
    //  Data Records
    // ======================================================================

    /**
     * 从客户端射线数据解析出的原点和方向向量。
     */
    public record RayContext(Vec3 origin, Vec3 dir) {
    }

    /**
     * 远程使用物品的结果：操作结果 + 剩余物品（可能被消耗或改变）。
     */
    public record UseOnOutcome(InteractionResult result, ItemStack remainder) {
    }
}
