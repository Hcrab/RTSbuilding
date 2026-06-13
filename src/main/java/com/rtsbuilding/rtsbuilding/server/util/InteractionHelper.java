package com.rtsbuilding.rtsbuilding.server.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 远程交互辅助工具???
 *
 * <p>封装 RTS 模式下远程使用物??与方块交??与实体交互的通用操作??
 * 每个方法临时替换玩家的主手物品、处??Shift 右键等，执行后自动恢???
 */
public final class InteractionHelper {

    private InteractionHelper() {
    }

    // ======================================================================
    //  Block Interaction (useItemOn)
    // ======================================================================

    /**
     * 临时??{@code handStack} 放入玩家主手，在 {@code hit} 位置执行
     * {@code useItemOn}，然后恢复主手并返回结果 + 剩余物品??
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnWithMainHand(ServerPlayer player, ServerLevel level,
            ItemStack handStack, BlockHitResult hit, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    hit));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    // ======================================================================
    //  Item Use (useItem)
    // ======================================================================

    /**
     * 临时??{@code handStack} 放入玩家主手，执??{@code useItem}??
     * 然后恢复主手并返回结??+ 剩余物品??
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemWithMainHand(ServerPlayer player, ServerLevel level,
            ItemStack handStack, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItem(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    // ======================================================================
    //  Entity Interaction
    // ======================================================================

    /**
     * 临时??{@code handStack} 放入玩家主手，与实体交互，然后恢复主???
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnEntityWithMainHand(ServerPlayer player, ServerLevel level,
            ItemStack handStack, Entity entity, Vec3 hit) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = interactEntityWithMainHand(player, level, entity, hit);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new TemporaryContextSwitcher.UseOnOutcome(result, remainder);
    }

    /**
     * 对实体执行交互操作：先调 player.interactOn，再??entity.interactAt，最??fallback ??useItem??
     */
    public static InteractionResult interactEntityWithMainHand(ServerPlayer player, ServerLevel level, Entity entity, Vec3 hit) {
        InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            Vec3 localHit = hit.subtract(entity.position());
            result = entity.interactAt(player, localHit, InteractionHand.MAIN_HAND);
        }
        if (!result.consumesAction()) {
            result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return result;
    }

    // ======================================================================
    //  Interaction Position
    // ======================================================================

    /**
     * 解析远程交互??虚拟玩家脚部位置"??
     * <ul>
     *   <li>对实体：从实体中心向击中点反方向偏移 1.8 ??/li>
     *   <li>对方块：从击中点沿法线反方向偏移 2.2 ??/li>
     *   <li>无目标：返回 {@code hit} ???/li>
     * </ul>
     */
    public static Vec3 resolveInteractionPosition(Entity targetEntity, BlockHitResult blockHit, Vec3 hit) {
        if (targetEntity != null) {
            Vec3 center = targetEntity.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(hit);
            if (delta.lengthSqr() < 1.0e-6D) {
                delta = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 at = center.subtract(delta.normalize().scale(1.8D));
            return new Vec3(at.x, at.y + 0.2D, at.z);
        }
        if (blockHit != null) {
            Vec3 n = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            Vec3 at = blockHit.getLocation().subtract(n.scale(2.2D));
            return new Vec3(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }
}
