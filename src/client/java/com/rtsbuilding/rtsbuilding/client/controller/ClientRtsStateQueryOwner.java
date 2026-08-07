package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.service.BuildPlacementService;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.S2CRtsPluginStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

final class ClientRtsStateQueryOwner {
    private final ClientRtsController controller;

    ClientRtsStateQueryOwner(ClientRtsController controller) {
        this.controller = controller;
    }

    boolean isEnabled() {
            return controller.enabled;
        }

    boolean canUseStorageOverlay() {
            return controller.enabled || controller.storageStateManager.hasAnyStorageContent();
        }

    double getAnchorX() {
            return controller.anchorX;
        }

    double getAnchorY() {
            return controller.anchorY;
        }

    double getAnchorZ() {
            return controller.anchorZ;
        }

    double getMaxRadius() {
            return controller.maxRadius;
        }

    boolean hasBounds() {
            return controller.enabled && controller.maxRadius > 0.0D;
        }

    boolean isHomeSelectionMode() {
            return controller.homeSelectionMode;
        }

    boolean isProgressionEnabled() {
            return controller.progressionStateManager.isProgressionEnabled();
        }

    boolean isProgressionHomeSet() {
            return controller.progressionStateManager.isProgressionHomeSet();
        }

    BlockPos getProgressionHomePos() {
            return controller.progressionStateManager.getProgressionHomePos();
        }

    String getProgressionHomeDimension() {
            return controller.progressionStateManager.getProgressionHomeDimension();
        }

    long getProgressionHomeCooldownTicks() {
            return controller.progressionStateManager.getProgressionHomeCooldownTicks();
        }

    int getProgressionRadiusBlocks() {
            return controller.progressionStateManager.getProgressionRadiusBlocks();
        }

    int getProgressionFluidCapacityBuckets() {
            return controller.progressionStateManager.getProgressionFluidCapacityBuckets();
        }

    int getProgressionUltimineLimit() {
            return controller.progressionStateManager.getProgressionUltimineLimit();
        }

    boolean isProgressionBypassHomeRadius() {
            return controller.progressionStateManager.isProgressionBypassHomeRadius();
        }

    List<PluginStateManager.InstalledPluginView> getInstalledPlugins() {
            return controller.pluginStateManager.installedPlugins();
        }

    String getPluginTeamName() {
            return controller.pluginStateManager.teamName();
        }

    boolean hasInstalledPlugin(String pluginId) {
            return controller.pluginStateManager.hasPlugin(pluginId);
        }

    BuilderMode getMode() {
            return controller.mode;
        }

    void setMode(BuilderMode mode) {
            controller.mode = mode;
            RtsClientPacketGateway.sendSetMode(mode);
        }

    boolean isFunnelEnabled() {
            return controller.storageStateManager.isFunnelEnabled();
        }

    void setFunnelEnabled(boolean enabled) {
            controller.storageStateManager.setFunnelEnabled(enabled);
            if (!enabled) {
                controller.lastFunnelTarget = null;
                controller.funnelTargetCooldownTicks = 0;
            }
        }

    void toggleFunnelEnabled() {
            controller.setFunnelEnabled(!controller.storageStateManager.isFunnelEnabled());
        }

    boolean isStorageCollapsed() {
            return controller.storageStateManager.isStorageCollapsed();
        }

    void toggleStorageCollapsed() {
            controller.storageStateManager.toggleStorageCollapsed();
        }

    double getStoragePanelXNormalized() {
            return controller.storageStateManager.getStoragePanelXNormalized();
        }

    double getStoragePanelYNormalized() {
            return controller.storageStateManager.getStoragePanelYNormalized();
        }

    double getStoragePanelWidthNormalized() {
            return controller.storageStateManager.getStoragePanelWidthNormalized();
        }

    double getStoragePanelHeightNormalized() {
            return controller.storageStateManager.getStoragePanelHeightNormalized();
        }

    void updateStoragePanelLayout(double xNormalized, double yNormalized, double widthNormalized, double heightNormalized) {
            controller.storageStateManager.updateStoragePanelLayout(xNormalized, yNormalized, widthNormalized, heightNormalized);
        }

    boolean isStorageLinked() {
            return controller.storageStateManager.isStorageLinked();
        }

    String getLinkedStorageName() {
            return controller.storageStateManager.getLinkedStorageName();
        }

    List<BlockPos> getLinkedStoragePositions() {
            return controller.storageStateManager.getLinkedStoragePositions();
        }

    List<LinkedStorageEntry> getLinkedStorageEntries() {
            return controller.storageStateManager.getLinkedStorageEntries();
        }

    int getStoragePage() {
            return controller.storageStateManager.getStoragePage();
        }

    int getStorageTotalPages() {
            return controller.storageStateManager.getStorageTotalPages();
        }

    int getStorageTotalEntries() {
            return controller.storageStateManager.getStorageTotalEntries();
        }

    int getStoragePageSize() {
            return controller.storageStateManager.getStoragePageSize();
        }

    int getStorageRevision() {
            return controller.storageStateManager.getStorageRevision();
        }

    String getStorageSearch() {
            return controller.storageStateManager.getStorageSearch();
        }

    RtsStorageSort getStorageSort() {
            return controller.storageStateManager.getStorageSort();
        }

    boolean isStorageSortAscending() {
            return controller.storageStateManager.isStorageSortAscending();
        }

    String getStorageCategory() {
            return controller.storageStateManager.getStorageCategory();
        }

    List<String> getStorageCategories() {
            return controller.storageStateManager.getStorageCategories();
        }

    String getSelectedItemId() {
            return controller.buildPlacementService.getSelectedItemId();
        }

    String getSelectedItemLabel() {
            return controller.buildPlacementService.getSelectedItemLabel();
        }

    String getSelectedFluidId() {
            return controller.buildPlacementService.getSelectedFluidId();
        }

    String getSelectedFluidLabel() {
            return controller.buildPlacementService.getSelectedFluidLabel();
        }

    boolean hasSelectedItem() {
            return controller.buildPlacementService.hasSelectedItem();
        }

    boolean hasSelectedFluid() {
            return controller.buildPlacementService.hasSelectedFluid();
        }

    boolean isEmptyHandSelected() {
            return controller.buildPlacementService.isEmptyHandSelected();
        }

    ItemStack getSelectedItemPreview() {
            return controller.buildPlacementService.getSelectedItemPreview();
        }

    ItemStack getSelectedFluidPreview() {
            return controller.buildPlacementService.getSelectedFluidPreview();
        }

    int getPlaceRotateDegrees() {
            return controller.buildPlacementService.getPlaceRotateDegrees();
        }

    String getPlacementStatePreset() {
            return controller.buildPlacementService.getPlacementStatePreset();
        }

    List<StorageEntry> getStorageEntries() {
            return controller.storageStateManager.getStorageEntries();
        }

    long getStorageTotalCount(String itemId) {
            return controller.storageStateManager.getStorageTotalCount(itemId);
        }

    List<FluidEntry> getFluidEntries() {
            return controller.storageStateManager.getFluidEntries();
        }

    List<RecentEntry> getRecentEntries() {
            return controller.storageStateManager.getRecentEntries();
        }

    long getRecentDisplayAmount(RecentEntry entry) {
            return controller.storageStateManager.getRecentDisplayAmount(entry);
        }

    String getCraftablesSearch() {
            return controller.storageStateManager.getCraftablesSearch();
        }

    boolean isCraftablesShowUnavailable() {
            return controller.storageStateManager.isCraftablesShowUnavailable();
        }

    List<CraftableEntry> getCraftableEntries() {
            return controller.storageStateManager.getCraftableEntries();
        }

    int getCraftablesRevision() {
            return controller.storageStateManager.getCraftablesRevision();
        }

    boolean hasMoreCraftables() {
            return controller.storageStateManager.hasMoreCraftables();
        }

    String getCraftFeedbackItemId() {
            return controller.storageStateManager.getCraftFeedbackItemId();
        }

    int getCraftFeedbackCount() {
            return controller.storageStateManager.getCraftFeedbackCount();
        }

    long getCraftFeedbackExpiryMs() {
            return controller.storageStateManager.getCraftFeedbackExpiryMs();
        }

    List<CraftFeedbackIngredient> getCraftFeedbackIngredients() {
            return controller.storageStateManager.getCraftFeedbackIngredients();
        }

    boolean isQuestDetectPopupVisible() {
            if (controller.questDetectPhase < 0 || controller.questDetectStartedAtMs <= 0L) {
                return false;
            }
            if (controller.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
                return true;
            }
            return System.currentTimeMillis() < controller.questDetectExpiryMs;
        }

    boolean isQuestDetectRunning() {
            return controller.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED;
        }

    byte getQuestDetectPhase() {
            return controller.questDetectPhase;
        }

    float getQuestDetectProgress() {
            if (!controller.isQuestDetectPopupVisible()) {
                return 0.0F;
            }
            long elapsed = Math.max(0L, System.currentTimeMillis() - controller.questDetectStartedAtMs);
            if (controller.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
                return (float) Math.min(0.92D, elapsed / 1000.0D * 0.92D);
            }
            return (float) Mth.clamp(elapsed / (double) ClientRtsController.QUEST_DETECT_MIN_PROGRESS_MS, 0.0D, 1.0D);
        }

    boolean isStorageScanPopupVisible() {
            return controller.storageStateManager.isStorageScanPopupVisible();
        }

    boolean isStorageScanRunning() {
            return controller.storageStateManager.isStorageScanRunning();
        }

    boolean isStorageViewDirty() {
            return controller.storageStateManager.isStorageViewDirty();
        }

    boolean shouldHighlightStorageRefresh() {
            return controller.storageStateManager.isStorageViewDirty() && !RtsClientUiStateStore.isStorageRefreshQuietEnabled();
        }

    float getStorageScanProgress() {
            return controller.storageStateManager.getStorageScanProgress();
        }

    void clearStorageScanPopupState() {
            controller.storageStateManager.clearStorageScanState();
        }

    boolean hasStoragePageSnapshot() {
            return controller.storageStateManager.hasStoragePageSnapshot();
        }

    int getQuestDetectScannedTasks() {
            return controller.questDetectScannedTasks;
        }

    int getQuestDetectTotalTasks() {
            return controller.questDetectTotalTasks;
        }

    int getQuestDetectCompletedTasks() {
            return controller.questDetectCompletedTasks;
        }

    List<FunnelBufferEntry> getFunnelBufferEntries() {
            return controller.storageStateManager.getFunnelBufferEntries();
        }

    boolean isAutoStoreMinedDrops() {
            return controller.storageStateManager.isAutoStoreMinedDrops();
        }

    boolean isBdNetworkEnabled() {
            return controller.storageStateManager.isBdNetworkEnabled();
        }

    void setBdNetworkEnabled(boolean enabled) {
            controller.storageStateManager.setBdNetworkEnabled(enabled);
        }

    void toggleBdNetworkEnabled() {
            controller.storageStateManager.toggleBdNetworkEnabled();
        }

    AreaMineShape getAreaMineShape() {
            return controller.miningOperationService.getAreaMineShape();
        }

    void setAreaMineShape(AreaMineShape shape) {
            controller.miningOperationService.setAreaMineShape(shape);
        }

    BuildShape getBuildShape() {
            return controller.buildPlacementService.getBuildShape();
        }

    void setBuildShape(BuildShape shape) {
            controller.buildPlacementService.setBuildShape(shape);
        }

    boolean isChunkCurtainVisible() {
            return controller.chunkCurtainVisible;
        }

    void setChunkCurtainVisible(boolean visible) {
            controller.chunkCurtainVisible = visible;
        }

    void cycleBuildShape(int step) {
            controller.buildPlacementService.cycleBuildShape(step);
        }

    int getQuickSlotCount() {
            return controller.storageStateManager.getQuickSlotCount();
        }

    String getQuickSlotItemId(int index) {
            return controller.storageStateManager.getQuickSlotItemId(index);
        }

    String getQuickSlotLabel(int index) {
            return controller.storageStateManager.getQuickSlotLabel(index);
        }

    ItemStack getQuickSlotPreview(int index) {
            return controller.storageStateManager.getQuickSlotPreview(index);
        }

    int getGuiBindingCount() {
            return controller.storageStateManager.getGuiBindingCount();
        }

    String getGuiBindingLabel(int index) {
            return controller.storageStateManager.getGuiBindingLabel(index);
        }

    ItemStack getGuiBindingPreview(int index) {
            return controller.storageStateManager.getGuiBindingPreview(index);
        }

    boolean hasGuiBinding(int index) {
            return controller.storageStateManager.hasGuiBinding(index);
        }

}
