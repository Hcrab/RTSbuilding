package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
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
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

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
            ItemStack remain = handlers.isEmpty()
                    ? original.copy()
                    : RtsTransferInserter.storeToLinkedOnly(handlers, original);
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

    /**
     * Convenience wrapper: calls {@link #absorbNearbyMinedDrops} and, if any
     * drops were absorbed, triggers quest detection.
     */
    public static void absorbMinedDropsImmediately(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return;
        }
        if (absorbNearbyMinedDrops(player, pos, session)) {
            QuestService.runQuestDetect(player, session, false);
        }
    }
}
