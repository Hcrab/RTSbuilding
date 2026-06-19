package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionCodec;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
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

/**
 * {@link SessionService} 的默认实现——管理 RTS 模式会话的完整生命周期。
 *
 * <p>使用 {@link ConcurrentHashMap} 维护玩家 UUID 到 {@link RtsStorageSession} 的映射。
 * 负责：
 * <ul>
 *   <li>会话的懒加载创建（{@link #getOrCreate}）</li>
 *   <li>会话持久化（通过 {@link com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionCodec} 序列化到 NBT）</li>
 *   <li>玩家启用/禁用 RTS 模式时的资源初始化/清理</li>
 *   <li>玩家登出时的完整清理（释放挖掘、漏斗、远程菜单、BD 缓存等资源）</li>
 * </ul>
 */
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
        cleanupSession(player, session, true);
        RtsWorkflowEngine.getInstance().pauseAllActive(player.getUUID(), true);
        saveToPlayerNbt(player, session);
        cleanupPlayerCaches(player);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        registry.pathfinding().cancel(player);
        RtsStorageSession session = sessions.get(player.getUUID());

        if (session != null) {
            cleanupSession(player, session, false);
            saveToPlayerNbt(player, session);
            session.placement.placeBatchJobs.clear();
        }

        sessions.remove(player.getUUID());
        cleanupPlayerCaches(player);
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

    /**
     * 清理会话资源——释放挖掘、路径规划、漏斗、远程菜单和 BD 缓存。
     * 注意：不会保存会话或清除玩家缓存。
     */
    private void cleanupSession(ServerPlayer player, RtsStorageSession session, boolean notify) {
        RtsMiningStateMachine.releaseMiningResources(player, session);
        registry.pathfinding().cancel(player);
        registry.funnel().disableAndFlush(player, session);
        RtsRemoteMenuService.closeTracked(player, session);
        RtsRemoteMenuService.clearValidation(player, session);
        session.bdCache.release();
    }

    /**
     * 清理玩家级别的缓存——存储 tick 服务和页面缓存。
     */
    private void cleanupPlayerCaches(ServerPlayer player) {
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsPageCore.clearCache(player.getUUID());
    }

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
