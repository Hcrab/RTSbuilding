package com.rtsbuilding.rtsbuilding.server.util;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumActionResult;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

import java.util.Objects;
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
    //  射线上下文
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
        Vec3d dir = new Vec3d(dirX, dirY, dirZ);
        if (dir.lengthSquared() < 1.0e-6D) {
            return null;
        }
        return new RayContext(new Vec3d(originX, originY, originZ), dir.normalize());
    }

    // ======================================================================
    //  位置与视角上下文
    // ======================================================================

    /**
     * 基于客户端射线方向构造虚拟交互上下文（位置 + 注视方向），
     * 执行 {@code action} 后自动恢复玩家的原始位置和朝向。
     */
    public static <T> T withTemporaryUseItemContext(EntityPlayerMP player, Vec3d fallbackPos, Vec3d fallbackLookAt,
            RayContext rayContext, double reach, Supplier<T> action) {
        if (rayContext == null) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        Vec3d rayDir = rayContext.dir();
        if (!Double.isFinite(rayDir.x) || !Double.isFinite(rayDir.y) || !Double.isFinite(rayDir.z)
                || rayDir.lengthSquared() < 1.0e-6D) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        double clampedReach = Math.max(2.0D, Math.min(8.0D, reach));
        double offset = Math.max(0.5D, clampedReach - 0.1D);
        Vec3d normalizedDir = rayDir.normalize();
        Vec3d virtualEye = fallbackLookAt.subtract(normalizedDir.scale(offset));
        double eyeHeight = player.getEyeHeight();
        Vec3d virtualFeet = new Vec3d(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        Vec3d lookAt = virtualEye.add(normalizedDir.scale(clampedReach));
        return withTemporaryInteractionPosition(player, virtualFeet, lookAt, action);
    }

    public static <T> T withTemporaryUseItemContext(EntityPlayerMP player, Vec3d fallbackPos, Vec3d fallbackLookAt,
            double reach, Supplier<T> action) {
        return withTemporaryUseItemContext(player, fallbackPos, fallbackLookAt, null, reach, action);
    }

    // ======================================================================
    //  潜行键状态
    // ======================================================================

    /**
     * 临时设置玩家的潜行状态，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporaryShiftKey(EntityPlayerMP player, boolean active, Supplier<T> action) {
        boolean previous = player.isSneaking();
        if (previous == active) {
            return action.get();
        }
        player.setSneaking(active);
        try {
            return action.get();
        } finally {
            player.setSneaking(previous);
        }
    }

    // ======================================================================
    //  主手物品
    // ======================================================================

    /**
     * 临时替换玩家的主手物品，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporaryMainHandItem(EntityPlayerMP player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getHeldItem();
        com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, previousMainHand);
        }
    }

    // ======================================================================
    //  OnGround 状态
    // ======================================================================

    /**
     * 临时设置玩家的 onGround 状态（影响挖掘速度计算），执行后恢复。
     */
    public static <T> T withTemporaryOnGround(EntityPlayerMP player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround;
        player.onGround = onGround;
        try {
            return action.get();
        } finally {
            player.onGround = previous;
        }
    }

    // ======================================================================
    //  选中快捷栏
    // ======================================================================

    /**
     * 临时切换玩家的选中快捷栏格，执行 {@code action} 后恢复。
     */
    public static <T> T withTemporarySelectedSlot(EntityPlayerMP player, int toolSlot, Supplier<T> action) {
        int slot = Math.max(0, Math.min(8, toolSlot));
        int prevSelected = player.inventory.currentItem;
        player.inventory.currentItem = slot;
        try {
            return action.get();
        } finally {
            player.inventory.currentItem = prevSelected;
        }
    }

    // ======================================================================
    //  内部：位置 + 视角目标
    // ======================================================================

    private static <T> T withTemporaryInteractionPosition(EntityPlayerMP player, Vec3d position,
            Vec3d lookAt, Supplier<T> action) {
        Vec3d prevPos = new Vec3d(player.posX, player.posY, player.posZ);
        float prevYRot = player.rotationYaw;
        float prevXRot = player.rotationPitch;
        float prevYHeadRot = player.rotationYawHead;
        float prevYBodyRot = player.renderYawOffset;

        player.setPosition(position.x, position.y, position.z);
        double eyeHeight = player.getEyeHeight();
        Vec3d eyePos = new Vec3d(position.x, position.y + eyeHeight, position.z);
        float[] look = yawPitchTo(eyePos, lookAt);
        player.rotationYaw = look[0];
        player.rotationPitch = look[1];
        player.rotationYawHead = look[0];
        player.renderYawOffset = look[0];
        try {
            return action.get();
        } finally {
            player.setPosition(prevPos.x, prevPos.y, prevPos.z);
            player.rotationYaw = prevYRot;
            player.rotationPitch = prevXRot;
            player.rotationYawHead = prevYHeadRot;
            player.renderYawOffset = prevYBodyRot;
        }
    }

    private static float[] yawPitchTo(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, xz)));
        return new float[]{yaw, pitch};
    }

    // ======================================================================
    //  数据记录
    // ======================================================================

    /**
     * 从客户端射线数据解析出的原点和方向向量。
     */
    public static final class RayContext {
        private final Vec3d origin;
        private final Vec3d dir;

        public RayContext(Vec3d origin, Vec3d dir) {
            this.origin = origin;
            this.dir = dir;
        }

        public Vec3d origin() { return this.origin; }
        public Vec3d dir() { return this.dir; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RayContext)) return false;
            RayContext that = (RayContext) other;
            return Objects.equals(this.origin, that.origin) && Objects.equals(this.dir, that.dir);
        }

        @Override
        public int hashCode() { return Objects.hash(this.origin, this.dir); }

        @Override
        public String toString() { return "RayContext[origin=" + this.origin + ", dir=" + this.dir + "]"; }
    }

    /**
     * 远程使用物品的结果：操作结果 + 剩余物品（可能被消耗或改变）。
     */
    public static final class UseOnOutcome {
        private final EnumActionResult result;
        private final ItemStack remainder;

        public UseOnOutcome(EnumActionResult result, ItemStack remainder) {
            this.result = result;
            this.remainder = remainder;
        }

        public EnumActionResult result() { return this.result; }
        public ItemStack remainder() { return this.remainder; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UseOnOutcome)) return false;
            UseOnOutcome that = (UseOnOutcome) other;
            return this.result == that.result && Objects.equals(this.remainder, that.remainder);
        }

        @Override
        public int hashCode() { return Objects.hash(this.result, this.remainder); }

        @Override
        public String toString() { return "UseOnOutcome[result=" + this.result + ", remainder=" + this.remainder + "]"; }
    }
}
