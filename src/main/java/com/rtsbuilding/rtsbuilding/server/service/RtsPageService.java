package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.List;

/**
 * 存储页面服务——管理页面请求、搜索、排序和分类??
 *
 * <p>职责范围??
 * <ul>
 *   <li>存储页面请求生命周期</li>
 *   <li>搜索/分类/排序状态管??/li>
 *   <li>页面构建委托</li>
 *   <li>存储视图脏标??/li>
 *   <li>最近物品记??/li>
 * </ul>
 */
public final class RtsPageService {

    public static final RtsPageService INSTANCE = new RtsPageService();

    private RtsPageService() {
    }

    // ======================================================================
    //  页面请求 (4 层重载链)
    // ======================================================================

    /**
     * 最简重载——自动补全拼音搜索设???
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    /**
     * 带拼音搜索设置——自动补全本地化搜索匹配??
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
     * 带拼音和本地化搜索匹配——自动补全页面大???
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        requestPage(player, page, search, category, sort, ascending, sessionPageSize(player), pinyinSearchEnabled, localizedSearchMatches);
    }

    /**
     * 完整实现——页面请求的最终处???
     */
    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, int pageSize, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        session.search = search == null ? "" : search;
        session.category = RtsStoragePageBuilder.normalizeCategory(category);
        session.sort = sort == null ? RtsStorageSort.QUANTITY : sort;
        session.ascending = ascending;
        session.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
        session.pinyinSearchEnabled = pinyinSearchEnabled;
        session.localizedSearchMatches.clear();
        session.localizedSearchMatches.addAll(RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        session.bdHandlerStale = true;
        session.bdFluidHandlerStale = true;

        List<LinkedHandler> activeHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        // Seed the slot cache for the resolved handlers
        RtsLinkedStorageResolver.registerStorageCaches(player, activeHandlers);
        var result = RtsStoragePageBuilder.build(
                player,
                session,
                page,
                session.pageSize,
                activeHandlers,
                activeFluidHandlers);
        PacketDistributor.sendToPlayer(player, result.payload());
        session.storageViewDirty = false;
        session.page = result.safePage();
        RtsSessionService.saveToPlayerNbt(player, session);
    }

    // ======================================================================
    //  存储视图脏标??
    // ======================================================================

    /**
     * 标记存储视图为脏——下次页面请求前提示客户端刷???
     */
    public static void markStorageViewDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session.storageViewDirty) {
            return;
        }
        session.storageViewDirty = true;
        PacketDistributor.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
    }

    // ======================================================================
    //  最近物品记??
    // ======================================================================

    /**
     * 记录最近使用的物品到会话中??
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
        return session != null && session.pinyinSearchEnabled;
    }

    private static List<String> currentLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.localizedSearchMatches);
    }

    private static int sessionPageSize(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        return session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.pageSize;
    }
}
