package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.page.PageResult;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCore;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCreativeTabIndexer;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageSharedHelpers;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 存储浏览器页面构建的外观（Facade）。
 *
 * <p>所有方法委托给 {@code page} 包中的相应子模块。
 * 本类仅用于保留 {@link com.rtsbuilding.rtsbuilding.server.RtsStorageManager}
 * 和网络层中现有的调用点，无需更改导入。
 *
 * <p>实际实现位于：
 * <ul>
 *   <li>{@link RtsPageCore}——页面构建（计数、排序、过滤、分页）</li>
 *   <li>{@link RtsPageCreativeTabIndexer}——创造模式标签页索引缓存</li>
 *   <li>{@link RtsPageSharedHelpers}——搜索、排序、分类辅助方法</li>
 * </ul>
 */
public final class RtsStoragePageBuilder {

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

    public static int defaultPageSize() {
        return RtsPageSharedHelpers.defaultPageSize();
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

    // ---- 为合成暴露的计数辅助方法 ------------------------------------

    public static long saturatedAdd(long a, long b) {
        return RtsPageCore.saturatedAdd(a, b);
    }

    public static long sanitizeCount(long value) {
        return RtsPageCore.sanitizeCount(value);
    }
}
