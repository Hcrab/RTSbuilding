package com.rtsbuilding.rtsbuilding.server.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumActionResult;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.world.WorldServer;

/**
 * 远程交互辅助工具集。
 *
 * <p>本类只封装一次原版右键调用所需的主手、潜行和命中参数，不负责切换玩家的远程位置与视角；
 * 后一项职责由 {@link TemporaryContextSwitcher} 统一管理。临时物品路径会把交互后玩家手中的真实堆叠
 * 作为 remainder 返回，并在正常结束或抛出异常时恢复进入方法前的主手引用。
 */
public final class InteractionHelper {

    private InteractionHelper() {
    }

    // ======================================================================
    //  方块交互（processRightClickBlock）
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，在 {@code hit} 位置执行原版方块右键，
     * 然后恢复主手并返回结果与实际剩余物品。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnWithMainHand(EntityPlayerMP player,
            WorldServer level, ItemStack handStack, RayTraceResult hit, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getHeldItem();
        com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, handStack);
        EnumActionResult result;
        ItemStack remainder;
        try {
            result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                    () -> processRightClickBlock(player, level, hit));
        } finally {
            remainder = player.getHeldItem().copy();
            com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    // ======================================================================
    //  空中使用（processRightClick）
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，执行原版空中右键，
     * 然后恢复主手并返回结果与实际剩余物品。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemWithMainHand(EntityPlayerMP player,
            WorldServer level, ItemStack handStack, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getHeldItem();
        com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, handStack);
        EnumActionResult result;
        ItemStack remainder;
        try {
            result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                    () -> processRightClick(player, level));
        } finally {
            remainder = player.getHeldItem().copy();
            com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    /**
     * 使用玩家真实主手执行原版方块右键，不替换堆叠；用于保留耐久、能量和 NBT 变异。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnWithRealMainHand(EntityPlayerMP player,
            WorldServer level, RayTraceResult hit, boolean forceSecondaryUse) {
        EnumActionResult result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                () -> processRightClickBlock(player, level, hit));
        return new TemporaryContextSwitcher.UseOnOutcome(result, player.getHeldItem().copy());
    }

    /**
     * 使用玩家真实主手执行原版空中右键，不替换堆叠；用于方块交互未消费动作后的原版回退。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemWithRealMainHand(EntityPlayerMP player,
            WorldServer level, boolean forceSecondaryUse) {
        EnumActionResult result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                () -> processRightClick(player, level));
        return new TemporaryContextSwitcher.UseOnOutcome(result, player.getHeldItem().copy());
    }

    // ======================================================================
    //  实体交互
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，与实体交互，然后恢复主手并返回真实 remainder。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnEntityWithMainHand(EntityPlayerMP player,
            WorldServer level, ItemStack handStack, Entity entity, Vec3d hit) {
        ItemStack previousMainHand = player.getHeldItem();
        com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, handStack);
        EnumActionResult result;
        ItemStack remainder;
        try {
            result = interactEntityWithMainHand(player, level, entity, hit);
        } finally {
            remainder = player.getHeldItem().copy();
            com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.setHeldItem(player, EnumHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    /**
     * 对实体执行主线同序的交互：普通实体右键、精确命中点交互，最后回退为空中使用物品。
     */
    public static EnumActionResult interactEntityWithMainHand(EntityPlayerMP player, WorldServer level,
            Entity entity, Vec3d hit) {
        EnumActionResult result = EnumActionResult.fromLegacyBoolean(player.interactWith(entity));
        if (!consumesAction(result)) {
            result = processRightClick(player, level);
        }
        return result;
    }

    // ======================================================================
    //  交互位置与 1.12.2 参数适配
    // ======================================================================

    /**
     * 解析远程交互的虚拟玩家脚部位置：
     * <ul>
     *   <li>对实体：从实体中心向击中点反方向偏移 1.8 格；</li>
     *   <li>对方块：从击中点沿方块面的法线反方向偏移 2.2 格；</li>
     *   <li>无目标：返回 {@code hit} 原值。</li>
     * </ul>
     */
    public static Vec3d resolveInteractionPosition(Entity targetEntity, RayTraceResult blockHit, Vec3d hit) {
        if (targetEntity != null) {
            Vec3d center = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(targetEntity.boundingBox).getCenter();
            Vec3d delta = center.subtract(hit);
            if (delta.lengthSquared() < 1.0e-6D) {
                delta = new Vec3d(0.0D, 0.0D, 1.0D);
            }
            Vec3d at = center.subtract(delta.normalize().scale(1.8D));
            return new Vec3d(at.x, at.y + 0.2D, at.z);
        }
        if (blockHit != null) {
            Vec3d normal = new Vec3d(blockHit.sideHit.getDirectionVec());
            Vec3d at = blockHit.hitVec.subtract(normal.scale(2.2D));
            return new Vec3d(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }

    /**
     * 1.12.2 的方块右键接口接收相对方块原点的命中坐标，而不是完整的 RayTraceResult。
     */
    private static EnumActionResult processRightClickBlock(EntityPlayerMP player, WorldServer level,
            RayTraceResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3d hitVec = hit.hitVec;
        return EnumActionResult.fromLegacyBoolean(player.theItemInWorldManager.activateBlockOrUseItem(
                player, level, player.getHeldItem(),
                pos.getX(), pos.getY(), pos.getZ(), hit.sideHit.getIndex(),
                (float) (hitVec.x - pos.getX()),
                (float) (hitVec.y - pos.getY()),
                (float) (hitVec.z - pos.getZ())));
    }

    private static EnumActionResult processRightClick(EntityPlayerMP player, WorldServer level) {
        ItemStack held = player.getHeldItem();
        return held == null ? EnumActionResult.PASS : EnumActionResult.fromLegacyBoolean(
                player.theItemInWorldManager.tryUseItem(player, level, held));
    }

    /**
     * 1.12.2 没有 consumesAction；SUCCESS 是唯一会阻止后续回退路径的结果。
     */
    private static boolean consumesAction(EnumActionResult result) {
        return result == EnumActionResult.SUCCESS;
    }
}
