package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.UseOnOutcome;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Handles RTS remote interaction using a pinned item extracted from the
 * player's linked storage system.
 *
 * <p>Extracts one item from the linked network, uses it against the target,
 * and refunds any remainder back to the network.
 */
public final class RtsLinkedItemInteractor {

    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;

    private RtsLinkedItemInteractor() {
    }

    /**
     * Interacts with a target block or entity using a pinned/linked item.
     * The item is extracted from the player's linked storage, used, and
     * any remainder is refunded.
     */
    public static InteractionResult interactWithLinkedItem(ServerPlayer player, ServerLevel level, RtsStorageSession session,
            Entity targetEntity, BlockHitResult blockHit, Vec3 hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.isBlank() || !RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return InteractionResult.PASS;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = RtsTransferExtractor.extractOneFromNetwork(extractHandlers, player, BuiltInRegistries.ITEM.get(id));
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        UseOnOutcome outcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            if (targetEntity != null) {
                return InteractionHelper.useItemOnEntityWithMainHand(player, level, extracted, targetEntity, hit);
            }
            // Alt interaction: normal item interaction first, build-like secondary interaction later.
            UseOnOutcome primaryOn = InteractionHelper.useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (primaryOn.result().consumesAction()) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().isEmpty() ? extracted.copy() : primaryOn.remainder().copy();

            UseOnOutcome primaryUse = InteractionHelper.useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (primaryUse.result().consumesAction()) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().isEmpty() ? afterPrimaryOn : primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = InteractionHelper.useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (secondaryOn.result().consumesAction()) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().isEmpty() ? afterPrimaryUse : secondaryOn.remainder().copy();
            return InteractionHelper.useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!outcome.remainder().isEmpty()) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, outcome.remainder());
        }
        // Force-refresh slot cache and invalidate page cache after linked-item interaction
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        return outcome.result();
    }
}
