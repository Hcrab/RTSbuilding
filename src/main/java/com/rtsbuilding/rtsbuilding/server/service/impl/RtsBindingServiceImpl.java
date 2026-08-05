package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BindingService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.List;

/**
 * {@link BindingService} 的默认实现——处理所有存储绑定相关的服务端逻辑。
 *
 * <p>该实现类通过 {@link ServiceRegistry} 调用其他子服务：
 * <ul>
 *   <li>使用 {@code registry.funnel()} 管理漏斗生命周期</li>
 *   <li>使用 {@code registry.session()} 获取/保存玩家会话</li>
 *   <li>使用 {@code registry.page()} 刷新储存页面</li>
 *   <li>使用 {@code registry.serviceOp()} 执行修改后操作</li>
 * </ul>
 *
 * <p>Phase 2 服务解耦的一部分。从静态方法 {@code RtsStorageBindings} 迁移而来。
 */
public final class RtsBindingServiceImpl implements BindingService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void setMode(EntityPlayerMP player, BuilderMode mode) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        BuilderMode previous = session.mode;
        boolean shouldFlushFunnel = RtsStorageBindings.setMode(session, mode);
        if (previous == session.mode) return;
        if (shouldFlushFunnel) {
            registry.funnel().disableAndFlush(player, session);
            registry.session().saveFunnelToPlayerNbt(player, session);
        }
        registry.session().saveModeToPlayerNbt(player, session);
        registry.serviceOp().refreshPage(player, session);
    }

    @Override
    public void linkStorage(EntityPlayerMP player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) return;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.linkStorage(player, session, pos, linkMode));
    }

    @Override
    public void unlinkStorage(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (removeLinkedRef(session, player.dimension, pos)) {
            RtsEndpointLeaseCache.INSTANCE.invalidate(
                    player.getUniqueID(), player.dimension, pos);
            registry.serviceOp().afterModification(player, session);
        }
    }

    private boolean removeLinkedRef(RtsStorageSession session, int dimension, BlockPos pos) {
        if (session == null || pos == null || session.linkedStorageInfo.isEmpty()) {
            return false;
        }
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.toImmutable());
        return session.linkedStorageInfo.remove(ref);
    }

    @Override
    public void updateLinkedStorageSettings(EntityPlayerMP player, BlockPos pos, byte linkMode, int priority) {
        if (player == null || pos == null) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        RtsStorageBindings.UpdateResult update = RtsStorageBindings.updateLinkedStorageSettings(
                player, session, pos, linkMode, priority);
        if (update != null && update.saveSession()) {
            // 权限变更必须立即使旧聚合挂载失效；下一次页面请求会按新模式重建。
            // 这是低频设置操作，牺牲一次快照复用可换取 Extract Only 的失败关闭语义。
            RtsStorageTickService.INSTANCE.unregisterPlayer(player);
        }
        LinkedStorageRef ref = new LinkedStorageRef(player.dimension, pos);
        RtsbuildingMod.LOGGER.info(
                "[RTS-STORAGE] side=S event=LINK_POLICY_UPDATED player={} pos={} linked={} requestedMode={} appliedMode={} priority={} cacheReset={}",
                player.getGameProfile().getName(), pos, session.linkedStorageInfo.contains(ref), linkMode,
                session.linkedStorageInfo.getMode(ref), session.linkedStorageInfo.getPriority(ref),
                update != null && update.saveSession());
        applyUpdate(player, session, update);
    }

    @Override
    public void setFunnelEnabled(EntityPlayerMP player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (session.funnel.funnelEnabled == enabled) return;
        if (enabled) {
            registry.funnel().enable(player, session);
        } else {
            registry.funnel().disableAndFlush(player, session);
            registry.session().saveFunnelToPlayerNbt(player, session);
        }
        registry.serviceOp().refreshPage(player, session);
    }

    @Override
    public void updateFunnelTarget(EntityPlayerMP player, BlockPos target) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        registry.funnel().updateTarget(player, session, target);
    }

    @Override
    public void setAutoStoreMinedDrops(EntityPlayerMP player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        session.sessionFlags.autoStoreMinedDrops = enabled;
        registry.serviceOp().simpleSave(player, session);
    }

    @Override
    public void setBdNetworkEnabled(EntityPlayerMP player, boolean enabled) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (session.sessionFlags.useBdNetwork == enabled) return;
        session.sessionFlags.useBdNetwork = enabled;
        session.bdCache.handler = null;
        session.bdCache.fluidHandler = null;
        registry.serviceOp().afterModification(player, session);
    }

    @Override
    public void setQuickSlot(EntityPlayerMP player, byte slotId, String itemId, ItemStack previewStack) {
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setQuickSlot(session, slotId, itemId, previewStack));
    }

    @Override
    public void setGuiBinding(EntityPlayerMP player, byte slotId, boolean clear, BlockPos pos, EnumFacing face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) return;
        RtsStorageSession session = registry.session().getOrCreate(player);
        applyUpdate(player, session, RtsStorageBindings.setGuiBinding(player, session, slotId, clear, pos, face, itemIdHint));
    }

    @Override
    public void openGuiBinding(EntityPlayerMP player, byte slotId) {
        openGuiBinding(player, slotId, 0L);
    }

    @Override
    public void openGuiBinding(EntityPlayerMP player, byte slotId, long traceId) {
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) return;
        RtsStorageBindings.UpdateResult result = RtsStorageBindings.openGuiBinding(
                player, session, slotId, 4.0D, traceId);
        if (result != null && result.refreshPage()) {
            registry.page().requestPage(player, result.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    @Override
    public void storeHotbarSlot(EntityPlayerMP player, byte slotId) {
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) return;
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) return;
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) return;
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        int slot = Math.max(0, Math.min(8, slotId));
        ItemStack inSlot = player.inventory.getStackInSlot(slot);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(inSlot)) return;

        ItemStack remaining = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, inSlot.copy());
        if (remaining.stackSize == inSlot.stackSize) return;

        player.inventory.setInventorySlotContents(slot, com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remaining) ? null : remaining);
        player.inventoryContainer.detectAndSendChanges();
        registry.serviceOp().afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    @Override
    public void closeRemoteMenu(EntityPlayerMP player) {
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || session.transfer.remoteMenuContainerId < 0) return;
        RtsRemoteMenuService.closeTracked(player, session);
        RtsRemoteMenuService.clearValidation(player, session);
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private void applyUpdate(EntityPlayerMP player, RtsStorageSession session, RtsStorageBindings.UpdateResult update) {
        if (player == null || session == null || update == null) return;
        if (update.saveSession()) {
            RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
        }
        if (update.refreshPage()) {
            registry.serviceOp().markDirty(player, session);
            registry.page().requestPage(player, update.page(), session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }
}
