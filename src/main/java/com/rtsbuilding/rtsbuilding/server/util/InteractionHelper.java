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
 * 远程交互辅助工具集。
 *
 * <p>封装 RTS 模式下远程使用物品/与方块交互/与实体交互的通用操作。
 * 每个方法临时替换玩家的主手物品、处理 Shift 右键等，执行后自动恢复。
 */
public final class InteractionHelper {

    private InteractionHelper() {
    }

    // ======================================================================
    //  方块交互（useItemOn）
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，在 {@code hit} 位置执行
     * {@code useItemOn}，然后恢复主手并返回结果 + 剩余物品。
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
    //  物品使用（useItem）
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，执行 {@code useItem}，
     * 然后恢复主手并返回结果 + 剩余物品。
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

    /**
     * 使用玩家真实主手执行 {@code useItemOn}，不替换堆叠；用于普通右键保持耐久、能量和 NBT 变异。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemOnWithRealMainHand(ServerPlayer player,
            ServerLevel level, BlockHitResult hit, boolean forceSecondaryUse) {
        InteractionResult result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                () -> player.gameMode.useItemOn(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND,
                        hit));
        return new TemporaryContextSwitcher.UseOnOutcome(result, player.getMainHandItem().copy());
    }

    /**
     * 使用玩家真实主手执行 {@code useItem}，不替换堆叠；用于方块交互失败后的原版空中使用回退。
     */
    public static TemporaryContextSwitcher.UseOnOutcome useItemWithRealMainHand(ServerPlayer player,
            ServerLevel level, boolean forceSecondaryUse) {
        InteractionResult result = TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse,
                () -> player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND));
        return new TemporaryContextSwitcher.UseOnOutcome(result, player.getMainHandItem().copy());
    }

    // ======================================================================
    //  实体交互
    // ======================================================================

    /**
     * 临时将 {@code handStack} 放入玩家主手，与实体交互，然后恢复主手。
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
     * 对实体执行交互操作：先调 player.interactOn，再试 entity.interactAt，最后 fallback 到 useItem。
     */
    public static InteractionResult interactEntityWithMainHand(ServerPlayer player, ServerLevel level, Entity entity, Vec3 hit) {
        Vec3 localHit = hit.subtract(entity.position());
        InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND, localHit);
        if (!result.consumesAction()) {
            result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return result;
    }

    // ======================================================================
    //  交互位置
    // ======================================================================

    /**
     * 解析远程交互的"虚拟玩家脚部位置"：
     * <ul>
     *   <li>对实体：从实体中心向击中点反方向偏移 1.8 格</li>
     *   <li>对方块：从击中点沿法线反方向偏移 2.2 格</li>
     *   <li>无目标：返回 {@code hit} 原值</li>
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
            Vec3 n = Vec3.atLowerCornerOf(blockHit.getDirection().getUnitVec3i());
            Vec3 at = blockHit.getLocation().subtract(n.scale(2.2D));
            return new Vec3(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }
}
