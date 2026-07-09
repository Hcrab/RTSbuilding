package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Handles post-break drop absorption: scans for {@link ItemEntity}s near the
 * mined position and stores them into linked storage (or the player's inventory
 * as a fallback) when {@code autoStoreMinedDrops} is enabled.
 *
 * <p>This is a stateless utility class.  All configuration lives in the
 * session and progression system.</p>
 */
public final class RtsDropAbsorber {

    /** Radius around the block break position to search for item entities. */
    private static final double DROP_SCAN_RADIUS = 1.25D;

    private RtsDropAbsorber() {
    }

    /**
     * Scans for {@link ItemEntity}s within a 1.25-block radius of the mined
     * position and stores each matching drop into linked storage first, then the
     * player's inventory. If both destinations are full, the remaining item
     * stays in the world.
     *
     * @return {@code true} if at least one drop was absorbed
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
        AABB box = new AABB(center).inflate(DROP_SCAN_RADIUS);
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
     * Convenience wrapper: calls {@link #absorbNearbyMinedDrops} and, if any
     * drops were absorbed, triggers quest detection.
     */
    public static boolean absorbMinedDropsImmediately(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return false;
        }
        boolean changed = absorbNearbyMinedDrops(player, pos, session);
        if (changed) {
            QuestService.runQuestDetect(player, session, false);
        }
        RtsPendingPlacementService.tryResumeAfterStorageChange(player);
        return changed;
    }

    /**
     * 批量吸收连锁/区域挖掘产生的掉落物。
     *
     * <p>同一 tick 内只解析一次储存上下文，避免大型网络里每个方块都重新扫描 linked storage。</p>
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
