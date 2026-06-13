package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 存储绑定服务——管理玩家链接存储、快捷槽、GUI 绑定和模式切换。
 *
 * <p>职责范围：
 * <ul>
 *   <li>建造模式设置</li>
 *   <li>存储方块链接/解绑</li>
 *   <li>快捷槽管理</li>
 *   <li>外部 GUI 绑定管理</li>
 *   <li>BD 网络、漏斗、掉落物存储开关</li>
 * </ul>
 */
public final class RtsBindingService {

    private RtsBindingService() {
    }

    // ======================================================================
    // 模式
    // ======================================================================

    /**
     * 设置建造模式。
     */
    public static void setMode(ServerPlayer player, BuilderMode mode) {
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (RtsStorageBindings.setMode(session, mode)) {
            RtsFunnelService.disableAndFlush(player, session);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    // ======================================================================
    // 链接/解绑
    // ======================================================================

    public static void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) return;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.linkStorage(player, session, pos, linkMode));
    }

    public static void unlinkStorage(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (removeLinkedRef(session, player.serverLevel().dimension(), pos)) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsSessionService.saveToPlayerNbt(player, session);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    private static boolean removeLinkedRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorages.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        boolean removed = session.linkedStorages.remove(ref);
        if (removed) {
            session.linkedNames.remove(ref);
            session.linkedModes.remove(ref);
            session.linkedPriorities.remove(ref);
            session.linkedBackpackUuids.remove(ref);
            session.linkedBackpackItemIds.remove(ref);
            session.detachedBackpackRefs.remove(ref);
        }
        return removed;
    }

    public static void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        if (player == null || pos == null) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        applyUpdate(player, session,
                RtsStorageBindings.updateLinkedStorageSettings(player, session, pos, linkMode, priority));
    }

    // ======================================================================
    // 开关
    // ======================================================================

    public static void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (session.funnel.funnelEnabled == enabled) return;
        if (enabled) {
            RtsFunnelService.enable(player, session);
        } else {
            RtsFunnelService.disableAndFlush(player, session);
        }
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    public static void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        RtsFunnelService.updateTarget(player, session, target);
    }

    public static void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        session.autoStoreMinedDrops = enabled;
        RtsSessionService.saveToPlayerNbt(player, session);
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    public static void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (session.useBdNetwork == enabled) return;
        session.useBdNetwork = enabled;
        session.cachedBdHandler = null;
        session.cachedBdFluidHandler = null;
        RtsSessionService.saveToPlayerNbt(player, session);
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    // ======================================================================
    // 快捷槽
    // ======================================================================

    public static void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack) {
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setQuickSlot(session, slotId, itemId, previewStack));
    }

    // ======================================================================
    // GUI 绑定
    // ======================================================================

    public static void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) return;
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint));
    }

    public static void openGuiBinding(ServerPlayer player, byte slotId) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) return;
        RtsStorageBindings.UpdateResult result = RtsStorageBindings.openGuiBinding(
                player, session, slotId, 4.0D);
        if (result != null && result.refreshPage()) {
            RtsPageService.requestPage(player, result.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    // ======================================================================
    // 其他
    // ======================================================================

    public static void storeHotbarSlot(ServerPlayer player, byte slotId) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) return;
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) return;
        var activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) return;
        var handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        int slot = Math.max(0, Math.min(8, slotId));
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) return;

        ItemStack remaining = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, inSlot.copy());
        if (remaining.getCount() == inSlot.getCount()) return;

        player.getInventory().setItem(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        player.containerMenu.broadcastChanges();
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void closeRemoteMenu(ServerPlayer player) {
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || session.transfer.remoteMenuContainerId < 0) return;
        RtsMenuRemoteService.closeTracked(player, session);
        RtsMenuRemoteService.clearValidation(player, session);
    }

    // ======================================================================
    // 内部
    // ======================================================================

    private static void applyUpdate(ServerPlayer player, RtsStorageSession session, RtsStorageBindings.UpdateResult update) {
        if (player == null || session == null || update == null) return;
        if (update.saveSession()) {
            RtsSessionService.saveToPlayerNbt(player, session);
        }
        if (update.refreshPage()) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsPageService.requestPage(player, update.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }
}
