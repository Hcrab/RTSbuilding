package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.data.SessionSerializer;
import com.rtsbuilding.rtsbuilding.server.data.SessionComponents;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
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
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import net.minecraft.nbt.CompoundTag;
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
 *   <li>会话持久化（通过 {@link SessionComponents} 细粒度组件 + {@link SaveScheduler} 管理）</li>
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
        var cluster = SaveScheduler.INSTANCE.player(player);

        // 纯细粒度组件写入——每个组件独立编码、独立脏标记
        cluster.set(SessionComponents.BROWSER, SessionSerializer.serializeBrowser(session.browser));
        cluster.set(SessionComponents.FLAGS, SessionSerializer.serializeFlags(session.sessionFlags));
        cluster.set(SessionComponents.MODE, session.mode);
        cluster.set(SessionComponents.LINKED_STORAGE, SessionSerializer.serializeLinkedStorage(session));
        cluster.set(SessionComponents.UI_MEMORY, SessionSerializer.serializeUiMemory(player, session));
        cluster.set(SessionComponents.PLACEMENT, SessionSerializer.serializePlacement(player, session));
        cluster.set(SessionComponents.DESTROY, SessionSerializer.serializeDestroy(player, session));
        cluster.set(SessionComponents.DROP_BUFFER, SessionSerializer.serializeDropBuffer(player, session));
        cluster.set(SessionComponents.FUNNEL, SessionSerializer.serializeFunnel(player, session));
    }

    @Override
    public void saveFunnelToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        SaveScheduler.INSTANCE.player(player).set(
                SessionComponents.FUNNEL, SessionSerializer.serializeFunnel(player, session));
    }

    @Override
    public long savePlacementToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        return SaveScheduler.INSTANCE.player(player).set(
                SessionComponents.PLACEMENT, SessionSerializer.serializePlacement(player, session));
    }

    @Override
    public long placementRevision(ServerPlayer player) {
        return SaveScheduler.INSTANCE.player(player).revision(SessionComponents.PLACEMENT);
    }

    @Override
    public long persistedPlacementRevision(ServerPlayer player) {
        return SaveScheduler.INSTANCE.player(player).persistedRevision(SessionComponents.PLACEMENT);
    }

    @Override
    public void saveModeToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        SaveScheduler.INSTANCE.player(player).set(SessionComponents.MODE, session.mode);
    }

    @Override
    public void onRtsEnabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);
        // 从 DataCluster 加载最新 mode（确保跨模式切换后状态一致）
        session.mode = SaveScheduler.INSTANCE.player(player).get(SessionComponents.MODE);
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        pageService.requestPage(player, session.browser.page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending, false);
        ServerHistoryManager.sendSync(player);
    }

    @Override
    public void onRtsDisabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);
        RtsTaskEngine.INSTANCE.preparePlayerDetach(player);
        cleanupSession(player, session, true);
        RtsTaskEngine.INSTANCE.pauseAllWorkflowTasks(player);
        RtsWorkflowEngine.getInstance().pauseAllActive(player.getUUID(), true);
        saveToPlayerNbt(player, session);
        cleanupPlayerCaches(player);
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        // 网络会话结束只摘除在线执行载荷；durable task 由 TaskStore 保留，不能误记为取消。
        RtsTaskEngine.INSTANCE.detachPlayer(player.getUUID());
        registry.pathfinding().cancel(player);
        RtsStorageSession session = sessions.get(player.getUUID());

        if (session != null) {
            cleanupSession(player, session, false);
            saveToPlayerNbt(player, session);
        }

        sessions.remove(player.getUUID());
        cleanupPlayerCaches(player);
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
        RtsWorkflowEngine.getInstance().saveAll(player.level().getServer());
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
        RtsDropAbsorber.flushDropBufferToPlayer(player, session);
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
        var cluster = SaveScheduler.INSTANCE.player(player);

        // 合并所有桥接组件的 NBT，统一反序列化
        CompoundTag root = new CompoundTag();
        root.merge(cluster.get(SessionComponents.BROWSER));
        root.merge(cluster.get(SessionComponents.FLAGS));
        root.merge(cluster.get(SessionComponents.LINKED_STORAGE));
        root.merge(cluster.get(SessionComponents.UI_MEMORY));
        root.merge(cluster.get(SessionComponents.PLACEMENT));
        root.merge(cluster.get(SessionComponents.DESTROY));
        root.merge(cluster.get(SessionComponents.DROP_BUFFER));
        root.merge(cluster.get(SessionComponents.FUNNEL));

        if (!root.isEmpty()) {
            SessionSerializer.loadAll(player, session, root);
        }

        // flush 失败后同进程重登会复用 DataCluster 的未确认内存值；重建运行时 gate，
        // 不能把“能读到最新 claim”误当作“该 claim 已经落盘”。
        long placementRevision = cluster.revision(SessionComponents.PLACEMENT);
        long persistedPlacementRevision = cluster.persistedRevision(SessionComponents.PLACEMENT);
        if (persistedPlacementRevision < placementRevision) {
            for (var recoveryJob : session.placement.recoveryJobs) {
                recoveryJob.requirePersistedRevision(placementRevision);
            }
        }

        // MODE 有独立编解码且字段非 final，可直接赋值
        session.mode = cluster.get(SessionComponents.MODE);
    }
}
