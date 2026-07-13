package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.api.PageService;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsStoragePageRequestCoalescer;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@link PageService} 的默认实现——处理远程储存浏览器的页面构建和刷新。
 *
 * <p>该实现类负责：
 * <ul>
 *   <li>接收并处理客户端的页面请求（搜索、排序、分类、分页）</li>
 *   <li>调用 {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder} 构建页面数据</li>
 *   <li>通过 {@link com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService} 注册缓存</li>
 *   <li>记录最近使用的物品到会话</li>
 *   <li>标记存储视图为脏以触发客户端刷新</li>
 * </ul>
 */
public final class RtsPageServiceImpl implements PageService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        boolean pinyinSearchEnabled = session != null && session.browser.pinyinSearchEnabled;
        List<String> localizedSearchMatches = session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
        int pageSize = session == null ? RtsStoragePageBuilder.defaultPageSize() : session.browser.pageSize;
        requestPage(player, page, search, category, sort, ascending,
                pageSize, pinyinSearchEnabled, localizedSearchMatches);
    }

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        List<String> localizedSearchMatches = session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
        int pageSize = session == null ? RtsStoragePageBuilder.defaultPageSize() : session.browser.pageSize;
        requestPage(player, page, search, category, sort, ascending,
                pageSize, pinyinSearchEnabled, localizedSearchMatches);
    }

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, int pageSize,
                            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
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
    private void buildPageNow(ServerPlayer player, int page, String search, String category,
                              RtsStorageSort sort, boolean ascending, int pageSize,
                              boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        RtsStorageSession session = registry.session().getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        session.browser.search = search;
        session.browser.category = category;
        session.browser.sort = sort;
        session.browser.ascending = ascending;
        session.browser.pageSize = pageSize;
        session.browser.pinyinSearchEnabled = pinyinSearchEnabled;
        session.browser.localizedSearchMatches.clear();
        session.browser.localizedSearchMatches.addAll(
                RtsStoragePageBuilder.sanitizeLocalizedSearchMatches(localizedSearchMatches));

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;

        List<LinkedHandler> activeHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        RtsLinkedHandlerResolutionService.registerStorageCaches(player, activeHandlers);
        var result = RtsStoragePageBuilder.build(
                player, session, page, session.browser.pageSize,
                activeHandlers, activeFluidHandlers);
        RtsClientboundPackets.sendToPlayer(player, result.payload());
        RtsDeveloperMetrics.recordPageSend(player);
        session.transfer.storageViewDirty = false;
        session.browser.page = result.safePage();
        registry.session().saveToPlayerNbt(player, session);
    }

    @Override
    public void markStorageViewDirty(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) return;
        if (session.transfer.storageViewDirty) return;
        session.transfer.storageViewDirty = true;
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
    }

    @Override
    public void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        RtsStorageRecentEntries.recordRecentItem(session, itemId, kind, amount);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        if (RtsStorageBindings.refreshMissingGuiBindingIcons(player, session)) {
            registry.session().saveToPlayerNbt(player, session);
        }
    }

    private int sessionPageSize(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session == null ? RtsStoragePageBuilder.defaultPageSize() : session.browser.pageSize;
    }
}
