package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCore;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCreativeTabIndexer;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageSharedHelpers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for storage browser page building.
 *
 * <p>All methods delegate to the appropriate sub-module in the
 * {@code page} package.  This class exists solely to preserve the
 * existing call sites in {@link com.rtsbuilding.rtsbuilding.server.RtsStorageManager} and the network
 * layer without import changes.
 *
 * <p>The actual implementation lives in:
 * <ul>
 *   <li>{@link RtsPageCore}  — page build (count, sort, filter, paginate)</li>
 *   <li>{@link RtsPageCreativeTabIndexer}  — creative-tab index cache</li>
 *   <li>{@link RtsPageSharedHelpers}  — search, sort, category helpers</li>
 * </ul>
 */
public final class RtsStoragePageBuilder {

    public static final int DEFAULT_PAGE_SIZE = RtsPageSharedHelpers.DEFAULT_PAGE_SIZE;

    private RtsStoragePageBuilder() {
    }

    public static PageResult build(
            ServerPlayer player, RtsStorageSession session,
            int requestedPage, int requestedPageSize,
            List<LinkedHandler> activeHandlers,
            List<LinkedFluidHandler> activeFluidHandlers) {
        return RtsPageCore.build(player, session, requestedPage, requestedPageSize,
                activeHandlers, activeFluidHandlers);
    }

    public static int sanitizePageSize(int pageSize) {
        return RtsPageSharedHelpers.sanitizePageSize(pageSize);
    }

    public static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        return RtsPageSharedHelpers.sanitizeLocalizedSearchMatches(localizedSearchMatches);
    }

    public static String normalizeCategory(String category) {
        return RtsPageSharedHelpers.normalizeCategory(category);
    }

    public static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        RtsPageCreativeTabIndexer.warmCreativeTabCacheMode(level, operatorTabs);
    }

    public static void clearCreativeTabCacheState() {
        RtsPageCreativeTabIndexer.clearCreativeTabCacheState();
    }

    public static long getHandlerReportedCount(IItemHandler handler, int slot, ItemStack stack) {
        return RtsPageCore.getHandlerReportedCount(handler, slot, stack);
    }

    public static long internalFluidCapacityMb(ServerPlayer player) {
        return RtsStorageFluids.internalFluidCapacityMb(player);
    }

    public static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        return RtsPageSharedHelpers.shouldIncludePlayerMainInventoryInStorageView(player, session);
    }

    public static int getPlayerMainInventoryStart(ServerPlayer player) {
        return RtsPageSharedHelpers.getPlayerMainInventoryStart(player);
    }

    public static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        return RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
    }

    public static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
            Map<String, Long> namespaceTotals) {
        RtsPageCore.accumulatePlayerMainInventoryCounts(player, counts, namespaceTotals);
    }

    // ---- count helpers exposed for crafting ------------------------------------

    public static long saturatedAdd(long a, long b) {
        return RtsPageCore.saturatedAdd(a, b);
    }

    public static long sanitizeCount(long value) {
        return RtsPageCore.sanitizeCount(value);
    }
}
