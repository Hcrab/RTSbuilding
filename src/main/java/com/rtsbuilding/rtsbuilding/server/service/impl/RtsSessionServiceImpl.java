package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionCodec;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.RtsMenuRemoteService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.PageService;
import com.rtsbuilding.rtsbuilding.server.service.api.SessionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCore;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsSessionServiceImpl implements SessionService {

    private final Map<UUID, RtsStorageSession> sessions = new ConcurrentHashMap<>();
    private final ServiceRegistry registry = ServiceRegistry.getInstance();
    private final PageService pageService = registry.page();

    @Override
    public RtsStorageSession getOrCreate(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), uuid -> {
            RtsStorageSession session = new RtsStorageSession();
            loadFromPersistentStorage(player, session);
            return session;
        });
    }

    @Override
    public RtsStorageSession getIfPresent(ServerPlayer player) {
        return sessions.get(player.getUUID());
    }

    @Override
    public Map<UUID, RtsStorageSession> allSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    @Override
    public void saveToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        var root = RtsStorageSessionCodec.serialize(player, session);
        player.getPersistentData().put(RtsStorageSessionCodec.ROOT_KEY, root.copy());
        RtsStorageSessionStore.saveSession(player, root);
    }

    @Override
    public void onRtsEnabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        pageService.requestPage(player, session.browser.page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending, false);
        ServerHistoryManager.sendSync(player);
    }

    @Override
    public void onRtsDisabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);

        // Release mining resources without cancelling workflow entry
        RtsMiningStateMachine.releaseMiningResources(player, session);

        // Pause active workflow threads for resumability
        RtsWorkflowEngine.getInstance().pauseAllActive(player.getUUID(), true);

        registry.pathfinding().cancel(player);
        registry.funnel().disableAndFlush(player, session);
        RtsMenuRemoteService.closeTracked(player, session);
        RtsMenuRemoteService.clearValidation(player, session);
        session.bdCache.release();
        saveToPlayerNbt(player, session);
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsPageCore.clearCache(player.getUUID());
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        registry.pathfinding().cancel(player);
        RtsStorageSession session = sessions.get(player.getUUID());

        if (session != null) {
            RtsMiningStateMachine.releaseMiningResources(player, session);
        }

        RtsWorkflowEngine.getInstance().pauseAllActive(player.getUUID(), false);

        if (session != null) {
            saveToPlayerNbt(player, session);
            session.placement.placeBatchJobs.clear();
            registry.funnel().disableAndFlush(player, session);
            RtsMenuRemoteService.closeTracked(player, session);
            RtsMenuRemoteService.clearValidation(player, session);
            session.bdCache.release();
        }
        sessions.remove(player.getUUID());
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsPageCore.clearCache(player.getUUID());
        TickablePipelineRegistry.removeAll(player.getUUID());
        RtsWorkflowEngine.getInstance().saveAll(player.getServer());
    }

    @Override
    public BuilderMode getMode(ServerPlayer player) {
        RtsStorageSession session = sessions.get(player.getUUID());
        return session == null ? BuilderMode.INTERACT : session.mode;
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void loadFromPersistentStorage(ServerPlayer player, RtsStorageSession session) {
        var root = RtsStorageSessionStore.loadSession(player);
        boolean loadedFromWorldStore = !root.isEmpty();
        if (!loadedFromWorldStore) {
            root = player.getPersistentData().getCompound(RtsStorageSessionCodec.ROOT_KEY);
        }
        if (root.isEmpty()) {
            return;
        }
        RtsStorageSessionCodec.load(player, session, root);
        if (!loadedFromWorldStore) {
            saveToPlayerNbt(player, session);
        }
    }
}
