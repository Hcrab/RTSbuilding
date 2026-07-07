package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * RTS 快速建造（预解析状态）放置逻辑，用于储存浏览器批处理作业。
 *
 * <p>快速建造为每个批处理作业预计算一个 {@link StatePlacementPlan}，
 * 使得作业内所有目标位置共享同一组解析后的方块状态、点击上下文模板
 * 和物品提取规则。这显著消除了大批量放置中重复的
 * {@link BlockPlaceContext} 创建和状态查找开销。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #resolveStatePlacementPlan(ServerPlayer, RtsPlacementBatch.PlaceBatchJob)} —
 *       从批处理作业的第一个位置解析放置计划，缓存物品、模板堆叠、旋转状态和来源 ID</li>
 *   <li>{@link #placeStateBatchEntry(ServerPlayer, RtsStorageSession, BlockPos, StatePlacementPlan)} —
 *       使用预解析计划放置单个方块，提取物品、设置方块、触发动画/声音</li>
 *   <li>{@link #canPlaceStateAt(ServerLevel, ServerPlayer, BlockPos, BlockState)} —
 *       检查目标位置是否可以放置给定方块状态（空气/可替换检查 + 碰撞检测）</li>
 * </ul>
 *
 * <p><b>内部记录：</b>{@link StatePlacementPlan} 包含物品引用、单次计数模板堆叠、
 * 完全旋转后的方块状态、是否从储存提取的标志和物品 ID。
 *
 * <p><b>设计原则：</b>此类故意不处理交互式主手放置、批处理作业生命周期管理、
 * 声音播放或提取编排——这些职责位于 {@code RtsPlacementExecutor}、
 * {@code RtsPlacementBatch}、{@code RtsPlacementSound} 和 {@code RtsPlacementExtractor} 中。
 */
public final class RtsPlacementQuickBuild {

    private RtsPlacementQuickBuild() {
    }

    /**
     * 从批处理作业的第一个位置解析 {@link StatePlacementPlan}。
     * 该计划缓存物品、单个计数的模板堆叠、最终的旋转方块状态、
     * 是否使用选中的储存物品以及来源物品 ID，
     * 以便同一作业中的每个位置重用相同的计划。
     *
     * <p>当玩家、作业或放置上下文无效时返回 {@code null}。
     */
    public static StatePlacementPlan resolveStatePlacementPlan(ServerPlayer player,
                                                               RtsPlacementBatch.PlaceBatchJob job) {
        if (player == null || job == null || !job.quickBuild()) {
            return null;
        }

        // 完全改为使用储存空间的方块进行放置。
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
     * 使用预解析的 {@link StatePlacementPlan} 放置单个方块。
     * 这是快速建造批处理作业采用的快速路径：它提取物品一次
     * （或重用主手堆叠），直接设置方块，并触发成功效果。
     *
     * @return {@code true} 继续处理批次，{@code false} 中止当前作业
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
        if (!RtsClaimProtectionService.canPlaceBlock(player, targetPos)) {
            return true;
        }
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

        boolean placed = BlockPlacer.setBlock(level, targetPos, plan.state());
        if (!placed) {
            if (refundExtractedOnFailure && !extracted.isEmpty()) {
                RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            }
            return true;
        }

        BlockState placedState = level.getBlockState(targetPos);
        if (placedState.is(plan.state().getBlock())) {
            BlockPlacer.applyQuickBuildBlockEntity(level, targetPos, placementStack, placedState, player);
        }
        // 完全改为使用储存空间的方块进行放置，不再从主手扣除
        BlockPlacer.trackPlaced(level, targetPos);
        RtsPlacementSound.playRemotePlacedBlockAnimation(player, targetPos);
        RtsPlacementSound.playRemotePlacedBlockSound(player, level, targetPos);
        ServiceRegistry.getInstance().page().recordRecentItem(session, plan.itemId(), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
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
     * 快速建造路径的预计算放置计划。
     *
     * @param item                  要放置的方块物品
     * @param templateStack         单次计数模板堆叠（组件保留）
     * @param state                 完全旋转后的方块状态
     * @param selectedStorageItem   此计划是从储存中提取（{@code true}）
     *                              还是使用主手堆叠（{@code false}）
     * @param itemId                用于最近物品追踪的字符串编码物品 ID
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
