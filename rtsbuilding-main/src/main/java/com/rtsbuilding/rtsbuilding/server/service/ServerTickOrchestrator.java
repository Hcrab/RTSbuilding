package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.page.RtsPageSharedHelpers;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide global Tick orchestrator — manages all tick loop logic not tied to player lifecycle.
 *
 * <p>Global tick dispatch center extracted from {@code RtsRtsSessionServiceImpl} during Phase 3 refactoring.
 * Singleton pattern, accessed via {@link #getInstance()}.
 *
 * <p><b>Core dispatch methods:</b>
 * <ul>
 *   <li>{@link #onPlayerTickPost(ServerPlayer)} — Player post-tick processing:
 *       <ul>
 *         <li>Remote menu validation — clears validation state if remote menu container ID doesn't match or is closed</li>
 *         <li>Batch placement tick — {@link RtsPlacementBatch#tickPlaceBatchJobs}</li>
 *       </ul>
 *   </li>
 *   <li>{@link #tickMining(MinecraftServer)} — Server global tick:
 *       <ul>
 *         <li>Storage cache refresh — {@link RtsStorageTickService#tick()},
 *             increments data version on changes, pushes refreshed page, attempts to resume pending jobs</li>
 *         <li>Per-player tick — iterates over all sessions: mining state machine tick, placement recovery tick</li>
 *         <li>Pipeline instance tick — {@link TickablePipelineRegistry#tickAll()}</li>
 *       </ul>
 *   </li>
 *   <li>{@link #warmCreativeTabCaches(MinecraftServer)} — Warms up creative tab caches:
 *       clears cache state, then warms once each in normal mode and search mode</li>
 * </ul>
 */
public final class ServerTickOrchestrator {

    private static final ServerTickOrchestrator INSTANCE = new ServerTickOrchestrator();

    /** 每玩家主背包槽位签名缓存，用于检测背包变化（背包不在链接存储缓存中，需单独轮询）。 */
    private final Map<UUID, long[]> inventorySignatures = new HashMap<>();

    private ServerTickOrchestrator() {
    }

    public static ServerTickOrchestrator getInstance() {
        return INSTANCE;
    }

    /**
     * 释放玩家的背包签名缓存（下线/切换维度时调用，防止签名残留导致首次检测误判）。
     */
    public void forgetPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            this.inventorySignatures.remove(playerUuid);
        }
    }

    /**
     * 检测玩家主背包（0~35 槽）自上次检测以来的变化。
     * 玩家背包条目（MODE_PLAYER_INVENTORY）不在 {@link RtsStorageTickService}
     * 的 handler 缓存中，物品进出背包不会产生缓存 changes，
     * 因此需要独立轮询以驱动网格刷新。首次观察不视为变化。
     */
    private boolean detectPlayerInventoryChanges(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int size = RtsPageSharedHelpers.getPlayerMainInventoryEndExclusive(player);
        long[] current = new long[size];
        for (int i = 0; i < size; i++) {
            current[i] = slotSignature(inventory.getItem(i));
        }
        long[] prev = this.inventorySignatures.put(player.getUUID(), current);
        if (prev == null || prev.length != size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (prev[i] != current[i]) {
                return true;
            }
        }
        return false;
    }

    private static long slotSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        long h = stack.getItem().hashCode();
        h = h * 31L + stack.getCount();
        return h;
    }

    // ======================================================================
    //  Lifecycle Tick
    // ======================================================================

    /**
     * Player Post-Tick — handles remote menu validation and batch placement tick.
     */
    public void onPlayerTickPost(ServerPlayer player) {
        var registry = RtsServer.get();
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        if (session.transfer.remoteMenuContainerId < 0
                && !RtsRemoteMenuCompat.isSupportedRemoteMenu(player.containerMenu)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
        if (session.transfer.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.transfer.remoteMenuContainerId)) {
            RtsRemoteMenuService.clearValidation(player, session);
        }
        RtsPlacementBatch.tickPlaceBatchJobs(player, session);
        RtsDestructionBatch.tickDestroyJobs(player, session);
    }

    // ======================================================================
    //  Server Global Tick
    // ======================================================================

    /**
     * Global tick — storage cache refresh + per-player tick (mining, placement recovery) + Pipeline tick.
     */
    public void tickMining(MinecraftServer server) {
        var registry = RtsServer.get();
        var RtsSessionServiceImpl = registry.session();
        var serviceOp = registry.serviceOp();

        // Tick storage cache refresh (every N ticks per player)
        var changes = RtsStorageTickService.INSTANCE.tick();

        // When cache detects item changes, push updated page to the client
        if (!changes.isEmpty()) {
            for (var entry : changes.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) continue;
                RtsStorageSession session = RtsSessionServiceImpl.getIfPresent(player);
                if (session == null) continue;
                // Increment data version so the page cache in RtsPageCore
                // knows the storage data has changed and should rebuild.
                session.transfer.pageDataVersion.incrementAndGet();
                serviceOp.refreshPage(player, session);
                // Automatically attempt to resume pending placement jobs after storage changes
                RtsPendingPlacementService.tryResumeAfterStorageChange(player);
                // Also attempt to resume pending destruction jobs after storage changes (new tools may have been stored)
                RtsDestructionBatch.tryResumePendingDestroyJobs(player, session);
            }
        }

        // Iterate over online players instead of allSessions(), to avoid iterating over expired offline sessions
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            RtsStorageSession session = RtsSessionServiceImpl.getIfPresent(player);
            if (session == null) continue;
            // Player inventory is not part of the handler cache, so inventory-only changes
            // (e.g. drops stored into the backpack) would never produce storage changes.
            // Poll the main inventory signature and push a refreshed page when it changes.
            if (RtsPageSharedHelpers.shouldIncludePlayerMainInventoryInStorageView(player, session)
                    && detectPlayerInventoryChanges(player)) {
                session.transfer.pageDataVersion.incrementAndGet();
                serviceOp.refreshPage(player, session);
            }
            RtsMiningStateMachine.tickActiveMining(player, session);
            RtsPlacedRecoveryService.tick(player, session);
        }

        // Tick all active tickable pipeline instances (ultimine/area-mine monitoring)
        TickablePipelineRegistry.tickAll();
    }

    // ======================================================================
    //  Cache Warming
    // ======================================================================

    /**
     * Warms up creative tab caches.
     */
    public void warmCreativeTabCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (ServerTickOrchestrator.class) {
            RtsStoragePageBuilder.clearCreativeTabCacheState();
            ServerLevel level = server.overworld();
            if (level == null) {
                return;
            }
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, false);
            RtsStoragePageBuilder.warmCreativeTabCacheMode(level, true);
        }
    }
}
