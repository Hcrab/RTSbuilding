package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.RtsService;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.imageio.spi.ServiceRegistry;

/**
 * {@link RtsBindingServiceImpl} 的默认实现——处理所有存储绑定相关的服务端逻辑。
 *
 * <p>该实现类通过 {@link ServiceRegistry} 调用其他子服务：
 * <ul>
 *   <li>使用 {@code server.session()} 获取/保存玩家会话</li>
 *   <li>使用 {@code server.page()} 刷新储存页面</li>
 *   <li>使用 {@code server.serviceOp()} 执行修改后操作</li>
 * </ul>
 *
 * <p>Phase 2 服务解耦的一部分。从静态方法 {@code RtsStorageBindings} 迁移而来。
 */
public final class RtsBindingServiceImpl implements RtsService {

    private final RtsServer server = RtsServer.get();

    public void setMode(ServerPlayer player, BuilderMode mode) {
        RtsStorageSession session = server.session().getOrCreate(player);
        if (RtsStorageBindings.setMode(session, mode)) {
            server.session().saveToPlayerNbt(player, session);
            server.serviceOp().refreshPage(player, session);
        }
    }

    public void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) return;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) return;
        RtsStorageSession session = server.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.linkStorage(player, session, pos, linkMode));
    }

    public void unlinkStorage(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return;
        RtsStorageSession session = server.session().getOrCreate(player);
        if (removeLinkedRef(session, player.serverLevel().dimension(), pos)) {
            server.serviceOp().afterModification(player, session);
        }
    }

    private boolean removeLinkedRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        return session.linkedStorageInfo.remove(ref);
    }

    public void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        if (player == null || pos == null) return;
        RtsStorageSession session = server.session().getOrCreate(player);
        applyUpdate(player, session,
                RtsStorageBindings.updateLinkedStorageSettings(player, session, pos, linkMode, priority));
    }

    public void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) return;
        RtsStorageSession session = server.session().getOrCreate(player);
        session.sessionFlags.autoStoreMinedDrops = enabled;
        server.serviceOp().simpleSave(player, session);
    }

    public void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        RtsStorageSession session = server.session().getOrCreate(player);
        if (session.sessionFlags.useBdNetwork == enabled) return;
        session.sessionFlags.useBdNetwork = enabled;
        session.bdCache.handler = null;
        session.bdCache.fluidHandler = null;
        server.serviceOp().afterModification(player, session);
    }

    public void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack) {
        RtsStorageSession session = server.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setQuickSlot(session, slotId, itemId, previewStack));
    }

    public void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) return;
        RtsStorageSession session = server.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint));
    }

    public void openGuiBinding(ServerPlayer player, byte slotId) {
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) return;
        RtsStorageBindings.UpdateResult result = RtsStorageBindings.openGuiBinding(
                player, session, slotId, 4.0D);
        if (result != null && result.refreshPage()) {
            server.page().requestPage(player, result.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    public void storeHotbarSlot(ServerPlayer player, byte slotId) {
        RtsStorageSession session = server.session().getIfPresent(player);
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
        server.serviceOp().afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    public void closeRemoteMenu(ServerPlayer player) {
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null || session.transfer.remoteMenuContainerId < 0) return;
        RtsRemoteMenuService.closeTracked(player, session);
        RtsRemoteMenuService.clearValidation(player, session);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void applyUpdate(ServerPlayer player, RtsStorageSession session, RtsStorageBindings.UpdateResult update) {
        if (player == null || session == null || update == null) return;
        if (update.saveSession()) {
            server.session().saveToPlayerNbt(player, session);
        }
        if (update.refreshPage()) {
            server.serviceOp().markDirty(player, session);
            server.page().requestPage(player, update.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }
}
