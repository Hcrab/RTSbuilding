package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 工具槽远程交互器——处理使用玩家快捷栏工具槽中的物品进行 RTS 远程交互。
 *
 * <p>两种子模式：
 * <ul>
 *   <li><b>方块/实体交互（{@link #interactWithToolSlot}）</b>——对目标方块或实体使用物品。
 *   按客户端真实修饰键依次尝试对块和空中使用。</li>
 *   <li><b>空中使用（{@link #useItemInAirWithToolSlot}）</b>——在空中使用物品（无目标）。</li>
 * </ul>
 *
 * <p>操作前会临时将快捷栏选中槽位切换到工具槽，操作完成后恢复。
 * 通过 {@link TemporaryContextSwitcher} 实现安全的临时上下文切换和潜行键模拟。
 */
public final class RtsToolSlotInteractor {

    private RtsToolSlotInteractor() {
    }

    /**
     * 使用指定快捷栏槽位中的物品与目标方块或实体交互。
     * 只执行客户端实际请求的普通或潜行模式，避免一次点击触发两套物品语义。
     */
    public static InteractionResult interactWithToolSlot(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, int toolSlot, RayContext rayContext, boolean shiftDown) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                Config.remotePovBlockReach(),
                () -> {
            player.getInventory().selected = slot;
            try {
                if (targetEntity != null) {
                    return TemporaryContextSwitcher.withTemporaryShiftKey(player, shiftDown,
                            () -> InteractionHelper.interactEntityWithMainHand(player, level, targetEntity, hit));
                }
                if (blockHit != null) {
                    InteractionResult blockResult = TemporaryContextSwitcher.withTemporaryShiftKey(player, shiftDown, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (blockResult.consumesAction()) {
                        return blockResult;
                    }
                    return TemporaryContextSwitcher.withTemporaryShiftKey(player, shiftDown, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                }
                return InteractionResult.PASS;
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    /**
     * 在空中使用指定快捷栏槽位中的物品（无目标方块/实体）。
     */
    public static InteractionResult useItemInAirWithToolSlot(ServerPlayer player, ServerLevel level, Vec3 hit,
            int toolSlot, RayContext rayContext, boolean shiftDown) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 fallback = hit == null ? player.getEyePosition() : hit;
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                fallback,
                fallback,
                rayContext,
                Config.remotePovBlockReach(),
                () -> {
            player.getInventory().selected = slot;
            try {
                return TemporaryContextSwitcher.withTemporaryShiftKey(player, shiftDown, () -> player.gameMode.useItem(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND));
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    // ---- internals -------------------------------------------------------------

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }
}
