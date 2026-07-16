package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 存储页面服务，管理页面请求、搜索、排序和分类。
 *
 * <p>职责范围：</p>
 * <ul>
 *   <li>存储页面请求生命周期。</li>
 *   <li>搜索、分类、排序状态管理。</li>
 *   <li>页面构建委托。</li>
 *   <li>存储视图脏标记。</li>
 *   <li>最近物品记录。</li>
 * </ul>
 */
public final class RtsPageService {

    public static final RtsPageService INSTANCE = new RtsPageService();

    private RtsPageService() {
    }

    // ======================================================================
    //  页面请求（4 层重载链）
    // ======================================================================

    /**
     * 最简重载，自动补全拼音搜索设置。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    /**
     * 带拼音搜索设置，自动补全本地化搜索匹配。
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
     * 带拼音和本地化搜索匹配，自动补全页面大小。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        requestPage(player, page, search, category, sort, ascending, sessionPageSize(player), pinyinSearchEnabled, localizedSearchMatches);
    }

    /**
     * 页面请求的最终处理入口。
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, int pageSize, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        String safeSearch = search == null ? "" : search;
        String safeCategory = RtsStoragePageBuilder.normalizeCategory(category);
        RtsStorageSort safeSort = sort == null ? RtsStorageSort.QUANTITY : sort;
        int safePageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        List<String> safeLocalizedMatches = List.copyOf(
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));
        RtsStoragePageRequestCoalescer.enqueue(player, () -> buildPageNow(
                player, page, safeSearch, safeCategory, safeSort, ascending,
                safePageSize, pinyinSearchEnabled, safeLocalizedMatches));
    }

    /** Tick 末由合并器调用；只有这里允许真正解析储存网络并构建页面。 */
    private static void buildPageNow(ServerPlayer player, int page, String search, String category,
            RtsStorageSort sort, boolean ascending, int pageSize,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        session.browser.search = search;
        session.browser.category = category;
        session.browser.sort = sort;
        session.browser.ascending = ascending;
        session.browser.pageSize = pageSize;
        session.browser.pinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(localizedSearchMatches);

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        markBdHandlersDirtyWhenNeeded(session);

        List<LinkedHandler> activeHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        // 为已解析 handler 种下槽位缓存，避免后续分页重复扫描。
        RtsLinkedStorageResolver.registerStorageCaches(player, activeHandlers);
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
     * 标记存储视图为脏，下次页面请求前提示客户端刷新。
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

    /**
     * BD 网络快照只在首次挂载或存储视图变脏时强制刷新。
     * <p>搜索、翻页和排序会频繁触发页面请求；如果每次都重建 BD 快照，
     * 大型整合包后期的拼音/本地化搜索会把纯 UI 过滤变成完整网络扫描。</p>
     */
    private static void markBdHandlersDirtyWhenNeeded(RtsStorageSession session) {
        if (session == null || !session.useBdNetwork) {
            return;
        }
        if (session.transfer.storageViewDirty || session.cachedBdHandler == null) {
            session.bdHandlerStale = true;
        }
        if (session.transfer.storageViewDirty || session.cachedBdFluidHandler == null) {
            session.bdFluidHandlerStale = true;
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
