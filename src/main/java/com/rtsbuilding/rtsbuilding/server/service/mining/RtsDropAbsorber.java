package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 挖掘掉落物吸收器，负责在方块被远程破坏后自动收集掉落物品。
 *
 * <p>当会话启用了 {@code autoStoreMinedDrops}（且进度/插件系统允许 {@code AUTO_STORE_MINED_DROPS} 功能）时，
 * 在破坏位置周围 1.25 格半径内扫描 {@link ItemEntity}，优先存入链接存储处理器，
 * 回退到玩家背包。若两个目标均已满，剩余物品留在世界中。
 *
 * <p>无状态工具类，所有配置存在于会话和进度/插件系统中。
 * 核心方法：
 * <ul>
 *   <li>{@link #absorbNearbyMinedDrops} — 执行扫描和吸收逻辑</li>
 *   <li>{@link #absorbMinedDropsImmediately} — 便捷包装，吸收后自动触发任务检测和恢复挂起放置</li>
 *   <li>{@link #absorbMinedDropsBatch} — 连锁/区域挖掘批量入口，复用同一个储存上下文</li>
 * </ul>
 */
public final class RtsDropAbsorber {

    /** 方块破坏位置周围搜索物品实体的半径。 */
    private RtsDropAbsorber() {
    }

    /**
     * 扫描开采位置周围 1.25 格半径内的 {@link ItemEntity}，将每个匹配的掉落物优先存入
     * 链接储存，再存入玩家背包。如果两个目标都已满，剩余物品留在世界中。
     *
     * @return 至少吸收了一个掉落物时返回 {@code true}
     */
    public static boolean absorbNearbyMinedDrops(ServerPlayer player, BlockPos center, RtsStorageSession session) {
        if (player == null || center == null || session == null) {
            return false;
        }
        DropInsertContext insertContext = createInsertContext(player, session);
        boolean changed = absorbNearbyMinedDrops(player, center, insertContext);
        notifyStorageChanged(player, insertContext, changed);
        return changed;
    }

    private static boolean absorbNearbyMinedDrops(ServerPlayer player, BlockPos center, DropInsertContext insertContext) {
        if (player == null || center == null || insertContext == null) {
            return false;
        }
        AABB box = new AABB(center).inflate(Config.dropScanRadius());
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = insertContext.store(original);
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    private static DropInsertContext createInsertContext(ServerPlayer player, RtsStorageSession session) {
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            return new DropInsertContext(aggregate, List.of());
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        return new DropInsertContext(null, handlers);
    }

    private static void notifyStorageChanged(ServerPlayer player, DropInsertContext insertContext, boolean changed) {
        if (changed && insertContext.usesAggregate()) {
            RtsStorageTickService.INSTANCE.alert(player.getUUID());
        }
    }

    private record DropInsertContext(RtsAggregateStorage aggregate, List<IItemHandler> handlers) {
        ItemStack store(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (aggregate != null && !aggregate.isEmpty()) {
                return aggregate.insert(stack, false);
            }
            return handlers.isEmpty()
                    ? stack.copy()
                    : RtsTransferInserter.storeToLinkedOnly(handlers, stack);
        }

        boolean usesAggregate() {
            return aggregate != null && !aggregate.isEmpty();
        }
    }

    /**
     * 便捷包装方法：调用 {@link #absorbNearbyMinedDrops}，如果吸收了任何掉落物，
     * 则触发任务检测。
     */
    public static boolean absorbMinedDropsImmediately(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return false;
        }
        boolean changed = absorbNearbyMinedDrops(player, pos, session);
        if (changed) {
            QuestService.runQuestDetect(player, session, false);
        }
        // 挖掘吸物后自动尝试恢复挂起放置作业
        RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        return changed;
    }

    /**
     * 批量吸收连锁/区域挖掘产生的掉落物。
     * <p>
     * 这里故意只解析一次储存上下文，避免每个方块都重新解析大量 linked storage。
     * 如果聚合储存缓存已经挂载，则优先走缓存的批量插入路径；否则回退到旧的 handler 列表。
     *
     * @return 本批次至少吸收了一个掉落物时返回 {@code true}
     */
    public static boolean absorbMinedDropsBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions) {
        if (player == null || session == null || positions == null || positions.isEmpty()) {
            return false;
        }
        DropInsertContext insertContext = createInsertContext(player, session);
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (pos != null && absorbNearbyMinedDrops(player, pos, insertContext)) {
                changed = true;
            }
        }
        notifyStorageChanged(player, insertContext, changed);
        if (changed) {
            QuestService.runQuestDetect(player, session, false);
        }
        RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        return changed;
    }
}
