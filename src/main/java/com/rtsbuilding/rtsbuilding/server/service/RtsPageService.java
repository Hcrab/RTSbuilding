package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.*;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.List;

/**
 * 存储页面服务——管理页面请求、搜索、排序和分类。
 *
 * <p>职责范围：
 * <ul>
 *   <li>存储页面请求生命周期</li>
 *   <li>搜索/分类/排序状态管理</li>
 *   <li>页面构建委托</li>
 *   <li>存储视图脏标记</li>
 *   <li>最近物品记录</li>
 * </ul>
 */
public final class RtsPageService {

    private RtsPageService() {
    }

    // ======================================================================
    //  页面请求 (4 层重载链)
    // ======================================================================

    /**
     * 最简重载——自动补全拼音搜索设置。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    /**
     * 带拼音搜索设置——自动补全本地化搜索匹配。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled) {
        requestPage(
                player,
                page,
                search,
                category,
                sort,
                ascending,
                pinyinSearchEnabled,
                currentLocalizedSearchMatches(player));
    }

    /**
     * 带拼音和本地化搜索匹配——自动补全页面大小。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        requestPage(player, page, search, category, sort, ascending, sessionPageSize(player), pinyinSearchEnabled, localizedSearchMatches);
    }

    /**
     * 完整实现——页面请求的最终处理。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, int pageSize, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        session.browser.search = search == null ? "" : search;
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(category);
        session.browser.sort = sort == null ? RtsStorageSort.QUANTITY : sort;
        session.browser.ascending = ascending;
        session.browser.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        session.browser.pinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;

        List<LinkedHandler> activeHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        // Seed the slot cache for the resolved handlers
        RtsLinkedHandlerResolutionService.registerStorageCaches(player, activeHandlers);
        var result = RtsStoragePageBuilder.build(
                player,
                session,
                page,
                session.browser.pageSize,
                activeHandlers,
                activeFluidHandlers);
        PacketDistributor.sendToPlayer(player, result.payload());
        session.transfer.storageViewDirty = false;
        session.browser.page = result.safePage();
        RtsSessionService.saveToPlayerNbt(player, session);
    }

    // ======================================================================
    //  存储视图脏标记
    // ======================================================================

    /**
     * 标记存储视图为脏——下次页面请求前提示客户端刷新。
     */
    public static void markStorageViewDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session.transfer.storageViewDirty) {
            return;
        }
        session.transfer.storageViewDirty = true;
        PacketDistributor.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
    }

    // ======================================================================
    //  最近物品记录
    // ======================================================================

    /**
     * 记录最近使用的物品到会话中。
     */
    public static void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        RtsStorageRecentEntries.recordRecentItem(session, itemId, kind, amount);
    }

    // ======================================================================
    //  内部辅助
    // ======================================================================

    private static void refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        if (RtsStorageBindings.refreshMissingGuiBindingIcons(player, session)) {
            RtsSessionService.saveToPlayerNbt(player, session);
        }
    }

    private static boolean currentPinyinSearchEnabled(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session != null && session.browser.pinyinSearchEnabled;
    }

    private static List<String> currentLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
    }

    private static int sessionPageSize(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.browser.pageSize;
    }
}

