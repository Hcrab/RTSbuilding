package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Quick-build (pre-resolved state) placement logic for RTS storage builder.
 *
 * <p>Quick-build pre-computes a {@link StatePlacementPlan} once per batch job
 * so that every target position shares the same resolved block state, hit
 * template, and item extraction rules. This eliminates redundant
 * {@link BlockPlaceContext} creation and state lookups for large batches.
 *
 * <p>This helper deliberately does not execute interactive (main-hand)
 * placement, manage batch-job lifecycle, play sounds, or handle extraction
 * orchestration — those responsibilities live in their dedicated helpers.
 */
public final class RtsPlacementQuickBuild {

    private RtsPlacementQuickBuild() {
    }

    /**
     * Resolves a {@link StatePlacementPlan} from the first position of a batch
     * job. The plan caches the item, a single-count template stack, the
     * final rotated block state, whether it uses a selected storage item, and
     * the source item id so that every position in the same job reuses the same
     * plan.
     *
     * <p>Returns {@code null} when the player, job, or placement context is
     * invalid.
     */
    public static StatePlacementPlan resolveStatePlacementPlan(ServerPlayer player,
                                                               RtsPlacementBatch.PlaceBatchJob job) {
        if (player == null || job == null || !job.quickBuild()) {
            return null;
        }

        // 完全改为使用储存空间的方块进行放置，快捷栏只用来看。
        // 必须存在有效的 itemId，否则拒绝
        String jobItemId = job.itemId();
        if (jobItemId == null || jobItemId.isBlank()) {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(jobItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack templateStack = job.itemPrototype();
        if (templateStack.isEmpty()) {
            templateStack = new ItemStack(item);
        }

        if (!(item instanceof BlockItem blockItem)) {
            return null;
        }

        BlockPos templatePos = job.templatePosition();
        if (templatePos == null || job.face() == null || !player.serverLevel().hasChunkAt(templatePos)) {
            return null;
        }
        templateStack.setCount(1);
        BlockPlaceContext context = new BlockPlaceContext(
                player.serverLevel(),
                player,
                InteractionHand.MAIN_HAND,
                templateStack,
                job.templateHit(templatePos));
        BlockState state = blockItem.getBlock().getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(item);
        if (sourceId == null) {
            return null;
        }
        return new StatePlacementPlan(
                item,
                templateStack,
                RtsPlacementHelper.rotateState(state, job.rotateSteps()),
                true,
                sourceId.toString());
    }

    /**
     * Places a single block using a pre-resolved {@link StatePlacementPlan}.
     * This is the fast path taken by quick-build batch jobs: it extracts the
     * item once (or reuses a main-hand stack), sets the block directly, and
     * fires the success effects.
     *
     * @return {@code true} to continue processing the batch, {@code false}
     *         to abort the current job
     */
    public static boolean placeStateBatchEntry(ServerPlayer player, RtsStorageSession session, BlockPos targetPos,
                                               StatePlacementPlan plan) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return false;
        }
        if (session == null || targetPos == null || plan == null) {
            return false;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, targetPos)) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        ServerLevel level = player.serverLevel();
        if (!canPlaceStateAt(level, player, targetPos, plan.state())) {
            return true;
        }

        ItemStack placementStack = plan.templateStack();
        ItemStack extracted = ItemStack.EMPTY;
        boolean refundExtractedOnFailure = false;
        List<IItemHandler> insertHandlers = List.of();
        // 完全改为使用储存空间的方块进行放置
        {
            List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            boolean includePlayerMainInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
            boolean creativeSource = player.isCreative();
            if (activeLinked.isEmpty() && !includePlayerMainInventory && !creativeSource) {
                return false;
            }
            List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
            insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
            extracted = creativeSource
                    ? RtsPlacementExtractor.creativeStack(plan.item(), plan.templateStack())
                    : includePlayerMainInventory
                            ? RtsPlacementExtractor.extractSelectedFromNetwork(extractHandlers, player, plan.item(), plan.templateStack())
                            : RtsPlacementExtractor.extractSelectedFromLinked(extractHandlers, plan.item(), plan.templateStack());
            if (extracted.isEmpty()) {
                return false;
            }
            refundExtractedOnFailure = !creativeSource;
            placementStack = extracted.copy();
            placementStack.setCount(1);
        }

        boolean placed = level.setBlock(targetPos, plan.state(), 3);
        if (!placed) {
            if (refundExtractedOnFailure && !extracted.isEmpty()) {
                RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            }
            return true;
        }

        BlockState placedState = level.getBlockState(targetPos);
        if (placedState.is(plan.state().getBlock())) {
            BlockItem.updateCustomBlockEntityTag(level, player, targetPos, placementStack);
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
            placedState.getBlock().setPlacedBy(level, targetPos, placedState, player, placementStack);
        }
        // 完全改为使用储存空间的方块进行放置，不再从主手扣除
        PlacedBlockTrackerData.get(level).mark(targetPos);
        RtsPlacementSound.playRemotePlacedBlockAnimation(player, targetPos);
        RtsPlacementSound.playRemotePlacedBlockSound(player, level, targetPos);
        RtsPageService.recordRecentItem(session, plan.itemId(), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        return true;
    }

    static boolean canPlaceStateAt(ServerLevel level, ServerPlayer player, BlockPos targetPos, BlockState state) {
        if (level == null || targetPos == null || state == null || !level.hasChunkAt(targetPos)) {
            return false;
        }
        BlockState current = level.getBlockState(targetPos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return false;
        }
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        return state.canSurvive(level, targetPos) && level.isUnobstructed(state, targetPos, collision);
    }

    /**
     * Pre-computed placement plan for the quick-build path.
     *
     * @param item                  the block item to place
     * @param templateStack         single-count template stack (components preserved)
     * @param state                 fully rotated block state
     * @param selectedStorageItem   whether this plan extracts from storage ({@code true})
     *                              or uses the main-hand stack ({@code false})
     * @param itemId                string-encoded item id for recent-item tracking
     */
    public record StatePlacementPlan(
            Item item,
            ItemStack templateStack,
            BlockState state,
            boolean selectedStorageItem,
            String itemId) {
        public StatePlacementPlan {
            templateStack = templateStack == null ? ItemStack.EMPTY : templateStack.copy();
            if (!templateStack.isEmpty()) {
                templateStack.setCount(1);
            }
        }
    }
}
