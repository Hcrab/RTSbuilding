package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.UseOnOutcome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Handles RTS remote interaction with an empty hand (no item held).
 *
 * <p>Tries entity interaction first, then block interaction (primary),
 * and falls back to in-air use.
 */
public final class RtsEmptyHandInteractor {

    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;

    private RtsEmptyHandInteractor() {
    }

    /**
     * Interacts with a target block or entity using an empty hand.
     */
    public static InteractionResult interactWithEmptyHand(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, RayContext rayContext) {
        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
                    if (targetEntity != null) {
                        return InteractionHelper.useItemOnEntityWithMainHand(player, level, ItemStack.EMPTY, targetEntity, hit).result();
                    }
                    if (blockHit != null) {
                        UseOnOutcome primary = InteractionHelper.useItemOnWithMainHand(player, level, ItemStack.EMPTY, blockHit, false);
                        if (primary.result().consumesAction()) {
                            return primary.result();
                        }
                    }
                    return InteractionHelper.useItemWithMainHand(player, level, ItemStack.EMPTY, false).result();
                });
    }
}
