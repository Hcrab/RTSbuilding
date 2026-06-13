package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageCore;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RTS 会话管理器——会话生命周期与玩家状态的管理核心。
 *
 * <p>职责范围：
 * <ul>
 *   <li>玩家 RTS 会话（{@link RtsStorageSession}）的创建、获取、持久化</li>
 *   <li>全局会话映射（SESSIONS）的维护</li>
 *   <li>生命周期钩子的统一调度（启用/停用 RTS、登出、Tick）</li>
 * </ul>
 */
public final class RtsSessionService {

    private static final Map<UUID, RtsStorageSession> SESSIONS = new ConcurrentHashMap<>();

    private RtsSessionService() {
    }

    // ======================================================================
    // 会话获取
    // ======================================================================

    /**
     * 获取或创建玩家的 RTS 会话。
     */
    public static RtsStorageSession getOrCreate(ServerPlayer player) {
        RtsStorageSession existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        RtsStorageSession created = new RtsStorageSession();
        loadFromPersistentStorage(player, created);
        SESSIONS.put(player.getUUID(), created);
        return created;
    }

    /**
     * 获取玩家会话但不创建（可能返回 null）。
     */
    public static RtsStorageSession getIfPresent(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /**
     * 获取所有活跃会话。
     */
    public static Map<UUID, RtsStorageSession> allSessions() {
        return Collections.unmodifiableMap(SESSIONS);
    }

    // ======================================================================
    // 持久化
    // ======================================================================

    private static void loadFromPersistentStorage(ServerPlayer player, RtsStorageSession session) {
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

    public static void saveToPlayerNbt(ServerPlayer player, RtsStorageSession session) {
        var root = RtsStorageSessionCodec.serialize(player, session);
        player.getPersistentData().put(RtsStorageSessionCodec.ROOT_KEY, root.copy());
        RtsStorageSessionStore.saveSession(player, root);
    }

    // ======================================================================
    // 生命周期
    // ======================================================================

    public static void onRtsEnabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending, false);
        ServerHistoryManager.sendSync(player);
    }

    public static void onRtsDisabled(ServerPlayer player) {
        RtsStorageSession session = getOrCreate(player);
        RtsMiningStateMachine.stopActiveMining(player, session);
        session.placement.placeBatchJobs.clear();
        RtsFunnelService.disableAndFlush(player, session);
        RtsMenuRemoteService.closeTracked(player, session);
        RtsMenuRemoteService.clearValidation(player, session);
        // Clear runtime BD network caches so their internal data can be
        // GC'd before the session object is dropped.
        session.cachedBdHandler = null;
        session.cachedBdFluidHandler = null;
        saveToPlayerNbt(player, session);
        // Free storage cache memory immediately instead of holding it
        // until the player logs out.
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsPageCore.clearCache(player.getUUID());
    }

    public static void onPlayerLogout(ServerPlayer player) {
        RtsStorageSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.placement.placeBatchJobs.clear();
            RtsFunnelService.disableAndFlush(player, session);
            RtsMenuRemoteService.closeTracked(player, session);
            RtsMenuRemoteService.clearValidation(player, session);
            // Clear runtime BD network caches so their internal data can be
            // GC'd before the session object is dropped.
            session.cachedBdHandler = null;
            session.cachedBdFluidHandler = null;
            saveToPlayerNbt(player, session);
        }
        SESSIONS.remove(player.getUUID());
        // Clean up storage cache
        RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        RtsPageCore.clearCache(player.getUUID());
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        RtsStorageSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.containerMenu)) {
            RtsMenuRemoteService.clearValidation(player, session);
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.transfer.remoteMenuContainerId)) {
            RtsMenuRemoteService.clearValidation(player, session);
        }
        RtsPlacementBatch.tickPlaceBatchJobs(player, session);
    }

    public static void warmCreativeTabCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (RtsSessionService.class) {
            RtsStoragePageBuilder.clearCreativeTabCacheState();
            ServerLevel level = server.overworld();
            if (level == null) {
                return;
            }
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, false);
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, true);
        }
    }

    public static void onPlayerTickPre(ServerPlayer player) {
        // RTS no longer spoofs player position for Sophisticated Storage menu validation.
    }

    public static void tickMining(MinecraftServer server) {
        // Tick storage cache refresh (every N ticks per player)
        var changes = RtsStorageTickService.INSTANCE.tick();

        // When cache detects item changes, push updated page to the client
        if (!changes.isEmpty()) {
            for (var entry : changes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                RtsStorageSession session = SESSIONS.get(entry.getKey());
                if (session == null) continue;
                // Increment data version so the page cache in RtsPageCore
                // knows the storage data has changed and should rebuild.
                session.transfer.pageDataVersion.incrementAndGet();
                if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) continue;
                RtsPageService.requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
            }
        }

        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RtsStorageSession session = entry.getValue();
            RtsMiningStateMachine.tickActiveMining(player, session);
            RtsFunnelService.tick(player, session);
            RtsPlacedRecoveryService.tick(player, session);
        }
    }

    // ======================================================================
    // 会话操作封装
    // ======================================================================

    public static BuilderMode getMode(ServerPlayer player) {
        RtsStorageSession session = SESSIONS.get(player.getUUID());
        return session == null ? BuilderMode.INTERACT : session.mode;
    }

    /**
     * 通知存储视图已过期。
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
}
