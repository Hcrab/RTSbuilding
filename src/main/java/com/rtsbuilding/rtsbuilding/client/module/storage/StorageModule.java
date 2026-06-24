package com.rtsbuilding.rtsbuilding.client.module.storage;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 存储模块——管理存储页面、合成、漏斗、快槽和 GUI 绑定。
 *
 * <p>替代原 {@code StorageStateManager} + {@code ClientRtsController} 中
 * 的存储相关逻辑。通过事件驱动更新，无需 tick 轮询。</p>
 */
public final class StorageModule implements FeatureModule {

    private final StorageState state = new StorageState();

    @Override
    public String moduleId() {
        return "storage";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) state.clearSessionState();
        } else if (event instanceof StateEvent.PlayerDied) {
            state.clearSessionState();
        }
    }

    // ======================================================================
    //  Tick (only auto-refresh)
    // ======================================================================

    @Override
    public void tick(long epochMs, int tickIndex) {
        state.tickAutoRefresh(epochMs);
    }

    // ======================================================================
    //  Network callbacks (called from network handler)
    // ======================================================================

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        state.applyStoragePage(payload);
        kernel().dispatch(new StateEvent.StoragePageLoaded(state.getRevision(), payload));
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        state.applyCraftables(payload);
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        state.applyCraftFeedback(payload);
    }

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        state.applyStorageDirty(payload);
    }

    // ======================================================================
    //  Public actions
    // ======================================================================

    public void requestPage(int page) {
        state.requestStoragePage(page);
    }

    public void requestCraftables() {
        state.requestCraftables();
    }

    public void setSearch(String search) {
        state.setStorageSearch(search);
    }

    public void setCraftablesSearch(String search) {
        state.setCraftablesSearch(search);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        RtsClientPacketGateway.sendLinkStorage(pos, allowStore);
    }

    public void craftRecipe(String recipeId, int count) {
        if (recipeId == null || recipeId.isBlank()) return;
        RtsClientPacketGateway.sendCraftRecipe(recipeId, count);
    }

    // ======================================================================
    //  State accessors (for UI)
    // ======================================================================

    public StorageState getState() {
        return this.state;
    }

    public boolean isLinked() { return state.isStorageLinked(); }
    public boolean isFunnelEnabled() { return state.isFunnelEnabled(); }
    public boolean isStorageCollapsed() { return state.isStorageCollapsed(); }
    public boolean hasAnyContent() { return state.hasAnyStorageContent(); }
    public int getRevision() { return state.getRevision(); }
    public List<?> getEntries() { return state.getStorageEntries(); }
    public List<?> getFluidEntries() { return state.getFluidEntries(); }
    public List<?> getRecentEntries() { return state.getRecentEntries(); }
    public List<?> getFunnelBufferEntries() { return state.getFunnelBufferEntries(); }
    public List<?> getCraftableEntries() { return state.getCraftableEntries(); }

    // ======================================================================
    //  Convenience
    // ======================================================================

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
