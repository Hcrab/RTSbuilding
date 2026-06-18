package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.PageService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class RtsPageServiceImpl implements PageService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled) {
        requestPage(player, page, search, category, sort, ascending,
                pinyinSearchEnabled, currentLocalizedSearchMatches(player));
    }

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, boolean pinyinSearchEnabled,
                            List<String> localizedSearchMatches) {
        requestPage(player, page, search, category, sort, ascending,
                sessionPageSize(player), pinyinSearchEnabled, localizedSearchMatches);
    }

    @Override
    public void requestPage(ServerPlayer player, int page, String search, String category,
                            RtsStorageSort sort, boolean ascending, int pageSize,
                            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        RtsStorageSession session = registry.session().getOrCreate(player);
        refreshMissingGuiBindingIcons(player, session);
        session.browser.search = search == null ? "" : search;
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(category);
        session.browser.sort = sort == null ? RtsStorageSort.QUANTITY : sort;
        session.browser.ascending = ascending;
        session.browser.pageSize = RtsStoragePageBuilder.sanitizePageSize(pageSize);
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
        PacketDistributor.sendToPlayer(player, result.payload());
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
        PacketDistributor.sendToPlayer(player, new S2CRtsStorageDirtyPayload(true));
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

    private boolean currentPinyinSearchEnabled(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session != null && session.browser.pinyinSearchEnabled;
    }

    private List<String> currentLocalizedSearchMatches(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session == null ? List.of() : List.copyOf(session.browser.localizedSearchMatches);
    }

    private int sessionPageSize(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        return session == null ? RtsStoragePageBuilder.DEFAULT_PAGE_SIZE : session.browser.pageSize;
    }
}
