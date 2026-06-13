package com.rtsbuilding.rtsbuilding.server.service.interaction;

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
 * Handles RTS remote interaction using items from the player's hotbar tool slot.
 *
 * <p>Two sub-modes:
 * <ul>
 *   <li><b>Block/entity interaction</b> — use item on a target block or entity
 *       (primary non-shift, primary shift, secondary non-shift, secondary shift)</li>
 *   <li><b>Air use</b> — use item in the air (no target)</li>
 * </ul>
 */
public final class RtsToolSlotInteractor {

    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;

    private RtsToolSlotInteractor() {
    }

    /**
     * Interacts with a target block or entity using the item in the specified hotbar slot.
     * Tries four interaction modes in sequence: non-shift on-block, non-shift in-air,
     * shift on-block, shift in-air.
     */
    public static InteractionResult interactWithToolSlot(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                if (targetEntity != null) {
                    return InteractionHelper.interactEntityWithMainHand(player, level, targetEntity, hit);
                }
                if (blockHit != null) {
                    InteractionResult primaryResult = TemporaryContextSwitcher.withTemporaryShiftKey(player, false, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (primaryResult.consumesAction()) {
                        return primaryResult;
                    }
                    InteractionResult primaryUseResult = TemporaryContextSwitcher.withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                    if (primaryUseResult.consumesAction()) {
                        return primaryUseResult;
                    }
                    InteractionResult secondaryResult = TemporaryContextSwitcher.withTemporaryShiftKey(player, true, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (secondaryResult.consumesAction()) {
                        return secondaryResult;
                    }
                    return TemporaryContextSwitcher.withTemporaryShiftKey(player, true, () -> player.gameMode.useItem(
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
     * Uses the item from the specified hotbar slot in the air (no target block/entity).
     */
    public static InteractionResult useItemInAirWithToolSlot(ServerPlayer player, ServerLevel level, Vec3 hit,
            int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 fallback = hit == null ? player.getEyePosition() : hit;
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                fallback,
                fallback,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                return TemporaryContextSwitcher.withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
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