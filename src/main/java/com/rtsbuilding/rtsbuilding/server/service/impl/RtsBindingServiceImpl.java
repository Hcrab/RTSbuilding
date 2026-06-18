package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.*;
import com.rtsbuilding.rtsbuilding.server.service.api.BindingService;
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

public final class RtsBindingServiceImpl implements BindingService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void setMode(ServerPlayer player, BuilderMode mode) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (RtsStorageBindings.setMode(session, mode)) {
            registry.funnel().disableAndFlush(player, session);
            ServiceOperationTemplate.refreshPage(player, session);
        }
    }

    @Override
    public void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) return;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.linkStorage(player, session, pos, linkMode));
    }

    @Override
    public void unlinkStorage(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (removeLinkedRef(session, player.serverLevel().dimension(), pos)) {
            ServiceOperationTemplate.afterModification(player, session);
        }
    }

    private boolean removeLinkedRef(RtsStorageSession session, ResourceKey<Level> dimension, BlockPos pos) {
        if (session == null || dimension == null || pos == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        return session.linkedStorageInfo.remove(ref);
    }

    @Override
    public void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        if (player == null || pos == null) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session,
                RtsStorageBindings.updateLinkedStorageSettings(player, session, pos, linkMode, priority));
    }

    @Override
    public void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (session.funnel.funnelEnabled == enabled) return;
        if (enabled) {
            registry.funnel().enable(player, session);
        } else {
            registry.funnel().disableAndFlush(player, session);
        }
        ServiceOperationTemplate.refreshPage(player, session);
    }

    @Override
    public void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        registry.funnel().updateTarget(player, session, target);
    }

    @Override
    public void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        session.sessionFlags.autoStoreMinedDrops = enabled;
        ServiceOperationTemplate.simpleSave(player, session);
    }

    @Override
    public void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (session.sessionFlags.useBdNetwork == enabled) return;
        session.sessionFlags.useBdNetwork = enabled;
        session.bdCache.handler = null;
        session.bdCache.fluidHandler = null;
        ServiceOperationTemplate.afterModification(player, session);
    }

    @Override
    public void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setQuickSlot(session, slotId, itemId, previewStack));
    }

    @Override
    public void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint));
    }

    @Override
    public void openGuiBinding(ServerPlayer player, byte slotId) {
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) return;
        RtsStorageBindings.UpdateResult result = RtsStorageBindings.openGuiBinding(
                player, session, slotId, 4.0D);
        if (result != null && result.refreshPage()) {
            registry.page().requestPage(player, result.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    @Override
    public void storeHotbarSlot(ServerPlayer player, byte slotId) {
        RtsStorageSession session = registry.session().getIfPresent(player);
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
        ServiceOperationTemplate.afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    @Override
    public void closeRemoteMenu(ServerPlayer player) {
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || session.transfer.remoteMenuContainerId < 0) return;
        RtsMenuRemoteService.closeTracked(player, session);
        RtsMenuRemoteService.clearValidation(player, session);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void applyUpdate(ServerPlayer player, RtsStorageSession session, RtsStorageBindings.UpdateResult update) {
        if (player == null || session == null || update == null) return;
        if (update.saveSession()) {
            registry.session().saveToPlayerNbt(player, session);
        }
        if (update.refreshPage()) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            registry.page().requestPage(player, update.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }
}
