package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsProgressionScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.service.BuildPlacementService;
import com.rtsbuilding.rtsbuilding.client.service.CameraOrbitService;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.shape.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

public final class ClientRtsController {
    private static final ClientRtsController INSTANCE = new ClientRtsController();
    private static final int RTS_MINE_RENDER_ID = 0x525453;
    private static final int REMOTE_MENU_OPEN_GRACE_TICKS = 80;
    private static final int SCREENLESS_REMOTE_MENU_RECOVERY_TICKS = 10;
    private static final long QUEST_DETECT_MIN_PROGRESS_MS = 700L;
    private static final long QUEST_DETECT_RESULT_VISIBLE_MS = 3500L;

    private boolean enabled;

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double maxRadius;
    private boolean homeSelectionMode;
    private boolean closeRangeAllowed;
    private boolean suppressBuilderScreenRestoreUntilRtsRestart;
    private boolean startCameraAtPlayerHead;
    private boolean allowPlacedBlockRecovery;
    private boolean toolProtectionEnabled = true;
    private boolean damageSoundEnabled = true;
    private boolean damageAutoReturnEnabled = true;

    private BuilderMode mode = BuilderMode.INTERACT;
    private byte questDetectPhase = -1;
    private long questDetectStartedAtMs;
    private long questDetectFinishedAtMs;
    private long questDetectExpiryMs;
    private int questDetectScannedTasks;
    private int questDetectTotalTasks;
    private int questDetectCompletedTasks;
    private boolean chunkCurtainVisible;

    private final StorageStateManager storageStateManager = new StorageStateManager();
    private final ProgressionStateManager progressionStateManager = new ProgressionStateManager();
    private final CameraOrbitService cameraOrbitService = new CameraOrbitService();
    private final MiningOperationService miningOperationService = new MiningOperationService();
    private final BuildPlacementService buildPlacementService = new BuildPlacementService();

    private BlockPos lastFunnelTarget;
    private int funnelTargetCooldownTicks;
    private boolean pendingCraftTerminalOpen;
    private int pendingCraftTerminalOpenTicks;
    private int pendingRemoteMenuOpenTicks;
    private int screenlessRemoteMenuTicks;
    private AbstractContainerMenu relaxedRemoteMenu;

    private ClientRtsController() {
        RtsClientUiStateStore.UiState uiState = RtsClientUiStateStore.load();
        this.startCameraAtPlayerHead = uiState.startCameraAtPlayerHead;
        this.allowPlacedBlockRecovery = uiState.allowPlacedBlockRecovery;
        this.toolProtectionEnabled = uiState.toolProtectionEnabled;
        this.cameraOrbitService.setInvertPanDragX(uiState.invertPanDragX);
        this.cameraOrbitService.setInvertPanDragY(uiState.invertPanDragY);
        this.cameraOrbitService.setSmoothCamera(uiState.smoothCamera);
        this.damageSoundEnabled = uiState.damageSoundEnabled;
        this.damageAutoReturnEnabled = uiState.damageAutoReturnEnabled;
    }

    public static ClientRtsController get() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canUseStorageOverlay() {
        return this.enabled || this.storageStateManager.hasAnyStorageContent();
    }

    public double getAnchorX() {
        return anchorX;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public double getAnchorZ() {
        return anchorZ;
    }

    public double getMaxRadius() {
        return maxRadius;
    }

    public boolean hasBounds() {
        return enabled && maxRadius > 0.0D;
    }

    public boolean isHomeSelectionMode() {
        return this.homeSelectionMode;
    }

    public boolean isProgressionEnabled() {
        return this.progressionStateManager.isProgressionEnabled();
    }

    public boolean isProgressionHomeSet() {
        return this.progressionStateManager.isProgressionHomeSet();
    }

    public BlockPos getProgressionHomePos() {
        return this.progressionStateManager.getProgressionHomePos();
    }

    public String getProgressionHomeDimension() {
        return this.progressionStateManager.getProgressionHomeDimension();
    }

    public long getProgressionHomeCooldownTicks() {
        return this.progressionStateManager.getProgressionHomeCooldownTicks();
    }

    public int getProgressionRadiusBlocks() {
        return this.progressionStateManager.getProgressionRadiusBlocks();
    }

    public int getProgressionFluidCapacityBuckets() {
        return this.progressionStateManager.getProgressionFluidCapacityBuckets();
    }

    public int getProgressionUltimineLimit() {
        return this.progressionStateManager.getProgressionUltimineLimit();
    }

    public boolean isProgressionBypassHomeRadius() {
        return this.progressionStateManager.isProgressionBypassHomeRadius();
    }

    public Set<String> getUnlockedProgressionNodes() {
        return this.progressionStateManager.getUnlockedProgressionNodes();
    }

    public Set<String> getUnlockableProgressionNodes() {
        return this.progressionStateManager.getUnlockableProgressionNodes();
    }

    public BuilderMode getMode() {
        return this.mode;
    }

    public void setMode(BuilderMode mode) {
        this.mode = mode;
        RtsClientPacketGateway.sendSetMode(mode);
    }

    public boolean isFunnelEnabled() {
        return this.storageStateManager.isFunnelEnabled();
    }

    public void setFunnelEnabled(boolean enabled) {
        this.storageStateManager.setFunnelEnabled(enabled);
        if (!enabled) {
            this.lastFunnelTarget = null;
            this.funnelTargetCooldownTicks = 0;
        }
    }

    public void toggleFunnelEnabled() {
        setFunnelEnabled(!this.storageStateManager.isFunnelEnabled());
    }

    public boolean isStorageCollapsed() {
        return this.storageStateManager.isStorageCollapsed();
    }

    public void toggleStorageCollapsed() {
        this.storageStateManager.toggleStorageCollapsed();
    }

    public double getStoragePanelXNormalized() {
        return this.storageStateManager.getStoragePanelXNormalized();
    }

    public double getStoragePanelYNormalized() {
        return this.storageStateManager.getStoragePanelYNormalized();
    }

    public double getStoragePanelWidthNormalized() {
        return this.storageStateManager.getStoragePanelWidthNormalized();
    }

    public double getStoragePanelHeightNormalized() {
        return this.storageStateManager.getStoragePanelHeightNormalized();
    }

    public void updateStoragePanelLayout(double xNormalized, double yNormalized, double widthNormalized, double heightNormalized) {
        this.storageStateManager.updateStoragePanelLayout(xNormalized, yNormalized, widthNormalized, heightNormalized);
    }

    public boolean isStorageLinked() {
        return this.storageStateManager.isStorageLinked();
    }

    public String getLinkedStorageName() {
        return this.storageStateManager.getLinkedStorageName();
    }

    public List<BlockPos> getLinkedStoragePositions() {
        return this.storageStateManager.getLinkedStoragePositions();
    }

    public List<LinkedStorageEntry> getLinkedStorageEntries() {
        return this.storageStateManager.getLinkedStorageEntries();
    }

    public int getStoragePage() {
        return this.storageStateManager.getStoragePage();
    }

    public int getStorageTotalPages() {
        return this.storageStateManager.getStorageTotalPages();
    }

    public int getStorageTotalEntries() {
        return this.storageStateManager.getStorageTotalEntries();
    }

    public int getStorageRevision() {
        return this.storageStateManager.getStorageRevision();
    }

    public String getStorageSearch() {
        return this.storageStateManager.getStorageSearch();
    }

    public RtsStorageSort getStorageSort() {
        return this.storageStateManager.getStorageSort();
    }

    public boolean isStorageSortAscending() {
        return this.storageStateManager.isStorageSortAscending();
    }

    public String getStorageCategory() {
        return this.storageStateManager.getStorageCategory();
    }

    public List<String> getStorageCategories() {
        return this.storageStateManager.getStorageCategories();
    }

    public String getSelectedItemId() {
        return this.buildPlacementService.getSelectedItemId();
    }

    public String getSelectedItemLabel() {
        return this.buildPlacementService.getSelectedItemLabel();
    }

    public String getSelectedFluidId() {
        return this.buildPlacementService.getSelectedFluidId();
    }

    public String getSelectedFluidLabel() {
        return this.buildPlacementService.getSelectedFluidLabel();
    }

    public boolean hasSelectedItem() {
        return this.buildPlacementService.hasSelectedItem();
    }

    public boolean hasSelectedFluid() {
        return this.buildPlacementService.hasSelectedFluid();
    }

    public boolean isEmptyHandSelected() {
        return this.buildPlacementService.isEmptyHandSelected();
    }

    public ItemStack getSelectedItemPreview() {
        return this.buildPlacementService.getSelectedItemPreview();
    }

    public ItemStack getSelectedFluidPreview() {
        return this.buildPlacementService.getSelectedFluidPreview();
    }

    public int getPlaceRotateDegrees() {
        return this.buildPlacementService.getPlaceRotateDegrees();
    }

    public List<StorageEntry> getStorageEntries() {
        return this.storageStateManager.getStorageEntries();
    }

    public long getStorageTotalCount(String itemId) {
        return this.storageStateManager.getStorageTotalCount(itemId);
    }

    public List<FluidEntry> getFluidEntries() {
        return this.storageStateManager.getFluidEntries();
    }

    public List<RecentEntry> getRecentEntries() {
        return this.storageStateManager.getRecentEntries();
    }

    public long getRecentDisplayAmount(RecentEntry entry) {
        return this.storageStateManager.getRecentDisplayAmount(entry);
    }

    public String getCraftablesSearch() {
        return this.storageStateManager.getCraftablesSearch();
    }

    public boolean isCraftablesShowUnavailable() {
        return this.storageStateManager.isCraftablesShowUnavailable();
    }

    public List<CraftableEntry> getCraftableEntries() {
        return this.storageStateManager.getCraftableEntries();
    }

    public int getCraftablesRevision() {
        return this.storageStateManager.getCraftablesRevision();
    }

    public boolean hasMoreCraftables() {
        return this.storageStateManager.hasMoreCraftables();
    }

    public String getCraftFeedbackItemId() {
        return this.storageStateManager.getCraftFeedbackItemId();
    }

    public int getCraftFeedbackCount() {
        return this.storageStateManager.getCraftFeedbackCount();
    }

    public long getCraftFeedbackExpiryMs() {
        return this.storageStateManager.getCraftFeedbackExpiryMs();
    }

    public List<CraftFeedbackIngredient> getCraftFeedbackIngredients() {
        return this.storageStateManager.getCraftFeedbackIngredients();
    }

    public boolean isQuestDetectPopupVisible() {
        if (this.questDetectPhase < 0 || this.questDetectStartedAtMs <= 0L) {
            return false;
        }
        if (this.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            return true;
        }
        return System.currentTimeMillis() < this.questDetectExpiryMs;
    }

    public boolean isQuestDetectRunning() {
        return this.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED;
    }

    public byte getQuestDetectPhase() {
        return this.questDetectPhase;
    }

    public float getQuestDetectProgress() {
        if (!isQuestDetectPopupVisible()) {
            return 0.0F;
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.questDetectStartedAtMs);
        if (this.questDetectPhase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            return (float) Math.min(0.92D, elapsed / 1000.0D * 0.92D);
        }
        return (float) Mth.clamp(elapsed / (double) QUEST_DETECT_MIN_PROGRESS_MS, 0.0D, 1.0D);
    }

    public boolean isStorageScanPopupVisible() {
        return this.storageStateManager.isStorageScanPopupVisible();
    }

    public boolean isStorageScanRunning() {
        return this.storageStateManager.isStorageScanRunning();
    }

    public boolean isStorageViewDirty() {
        return this.storageStateManager.isStorageViewDirty();
    }

    public boolean shouldHighlightStorageRefresh() {
        return this.storageStateManager.isStorageViewDirty() && !RtsClientUiStateStore.isStorageRefreshQuietEnabled();
    }

    public float getStorageScanProgress() {
        return this.storageStateManager.getStorageScanProgress();
    }

    public boolean hasStoragePageSnapshot() {
        return this.storageStateManager.hasStoragePageSnapshot();
    }

    public int getQuestDetectScannedTasks() {
        return this.questDetectScannedTasks;
    }

    public int getQuestDetectTotalTasks() {
        return this.questDetectTotalTasks;
    }

    public int getQuestDetectCompletedTasks() {
        return this.questDetectCompletedTasks;
    }

    public List<FunnelBufferEntry> getFunnelBufferEntries() {
        return this.storageStateManager.getFunnelBufferEntries();
    }

    public boolean isAutoStoreMinedDrops() {
        return this.storageStateManager.isAutoStoreMinedDrops();
    }

    public boolean isBdNetworkEnabled() {
        return this.storageStateManager.isBdNetworkEnabled();
    }

    public void setBdNetworkEnabled(boolean enabled) {
        this.storageStateManager.setBdNetworkEnabled(enabled);
    }

    public void toggleBdNetworkEnabled() {
        this.storageStateManager.toggleBdNetworkEnabled();
    }

    public AreaMineShape getAreaMineShape() {
        return this.miningOperationService.getAreaMineShape();
    }

    public void setAreaMineShape(AreaMineShape shape) {
        this.miningOperationService.setAreaMineShape(shape);
    }

    public BuildShape getBuildShape() {
        return this.buildPlacementService.getBuildShape();
    }

    public void setBuildShape(BuildShape shape) {
        this.buildPlacementService.setBuildShape(shape);
    }

    public boolean isChunkCurtainVisible() {
        return this.chunkCurtainVisible;
    }

    public void setChunkCurtainVisible(boolean visible) {
        this.chunkCurtainVisible = visible;
    }

    public void cycleBuildShape(int step) {
        this.buildPlacementService.cycleBuildShape(step);
    }

    public int getQuickSlotCount() {
        return this.storageStateManager.getQuickSlotCount();
    }

    public String getQuickSlotItemId(int index) {
        return this.storageStateManager.getQuickSlotItemId(index);
    }

    public String getQuickSlotLabel(int index) {
        return this.storageStateManager.getQuickSlotLabel(index);
    }

    public ItemStack getQuickSlotPreview(int index) {
        return this.storageStateManager.getQuickSlotPreview(index);
    }

    public float getRotateSensitivity() {
        return this.cameraOrbitService.getRotateSensitivity();
    }

    public int getGuiBindingCount() {
        return this.storageStateManager.getGuiBindingCount();
    }

    public String getGuiBindingLabel(int index) {
        return this.storageStateManager.getGuiBindingLabel(index);
    }

    public ItemStack getGuiBindingPreview(int index) {
        return this.storageStateManager.getGuiBindingPreview(index);
    }

    public boolean hasGuiBinding(int index) {
        return this.storageStateManager.hasGuiBinding(index);
    }

    public String getInputSensitivityLabel() {
        return this.cameraOrbitService.getInputSensitivityLabel();
    }

    public int getInputSensitivityIndex() {
        return this.cameraOrbitService.getInputSensitivityIndex();
    }

    public int getInputSensitivityPresetCount() {
        return this.cameraOrbitService.getInputSensitivityPresetCount();
    }

    public void setInputSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setInputSensitivityByFraction(fraction);
    }

    public void cycleInputSensitivity() {
        this.cameraOrbitService.cycleInputSensitivity();
    }

    public void increaseRotateSensitivity() {
        this.cameraOrbitService.increaseRotateSensitivity();
    }

    public void decreaseRotateSensitivity() {
        this.cameraOrbitService.decreaseRotateSensitivity();
    }

    public void beginRotateCapture(double cursorX, double cursorY) {
        this.cameraOrbitService.beginRotateCapture(cursorX, cursorY);
    }

    public void endRotateCapture(double fallbackX, double fallbackY) {
        this.cameraOrbitService.endRotateCapture(fallbackX, fallbackY);
    }

    public boolean isRotateCaptured() {
        return this.cameraOrbitService.isRotateCaptured();
    }

    public boolean isStartCameraAtPlayerHead() {
        return this.startCameraAtPlayerHead;
    }

    public void setStartCameraAtPlayerHead(boolean startCameraAtPlayerHead) {
        this.startCameraAtPlayerHead = startCameraAtPlayerHead;
    }

    public void toggleStartCameraAtPlayerHead() {
        this.startCameraAtPlayerHead = !this.startCameraAtPlayerHead;
    }

    public boolean isAllowPlacedBlockRecovery() {
        return this.allowPlacedBlockRecovery;
    }

    public void setAllowPlacedBlockRecovery(boolean allowPlacedBlockRecovery) {
        this.allowPlacedBlockRecovery = allowPlacedBlockRecovery;
    }

    public void toggleAllowPlacedBlockRecovery() {
        this.allowPlacedBlockRecovery = !this.allowPlacedBlockRecovery;
    }

    public boolean isToolProtectionEnabled() {
        return this.toolProtectionEnabled;
    }

    public void setToolProtectionEnabled(boolean toolProtectionEnabled) {
        this.toolProtectionEnabled = toolProtectionEnabled;
    }

    public void toggleToolProtectionEnabled() {
        this.toolProtectionEnabled = !this.toolProtectionEnabled;
    }

    public boolean isInvertPanDragX() {
        return this.cameraOrbitService.isInvertPanDragX();
    }

    public void setInvertPanDragX(boolean invertPanDragX) {
        this.cameraOrbitService.setInvertPanDragX(invertPanDragX);
    }

    public void toggleInvertPanDragX() {
        this.cameraOrbitService.toggleInvertPanDragX();
    }

    public boolean isInvertPanDragY() {
        return this.cameraOrbitService.isInvertPanDragY();
    }

    public void setInvertPanDragY(boolean invertPanDragY) {
        this.cameraOrbitService.setInvertPanDragY(invertPanDragY);
    }

    public void toggleInvertPanDragY() {
        this.cameraOrbitService.toggleInvertPanDragY();
    }

    public boolean isSmoothCamera() {
        return this.cameraOrbitService.isSmoothCamera();
    }

    public void setSmoothCamera(boolean smoothCamera) {
        this.cameraOrbitService.setSmoothCamera(smoothCamera);
    }

    public void toggleSmoothCamera() {
        this.cameraOrbitService.toggleSmoothCamera();
    }

    public boolean isDamageSoundEnabled() {
        return this.damageSoundEnabled;
    }

    public void setDamageSoundEnabled(boolean damageSoundEnabled) {
        this.damageSoundEnabled = damageSoundEnabled;
    }

    public void toggleDamageSoundEnabled() {
        this.damageSoundEnabled = !this.damageSoundEnabled;
    }

    public boolean isDamageAutoReturnEnabled() {
        return this.damageAutoReturnEnabled;
    }

    public void setDamageAutoReturnEnabled(boolean damageAutoReturnEnabled) {
        this.damageAutoReturnEnabled = damageAutoReturnEnabled;
    }

    public void toggleDamageAutoReturnEnabled() {
        this.damageAutoReturnEnabled = !this.damageAutoReturnEnabled;
    }

    public void applyServerCameraState(S2CRtsCameraStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (payload.enabled()) {
            boolean freshEnable = !this.enabled;
            this.enabled = true;
            this.cameraOrbitService.setServerCameraEntityId(payload.cameraEntityId());
            this.anchorX = payload.anchorX();
            this.anchorY = payload.anchorY();
            this.anchorZ = payload.anchorZ();
            this.maxRadius = payload.maxRadius();
            this.homeSelectionMode = payload.homeSelection();
            this.closeRangeAllowed = payload.closeRangeAllowed();

            if (freshEnable) {
                this.cameraOrbitService.capturePreviousView(minecraft);
                // Clear stale player input to prevent WASD presses from before entering RTS mode from affecting movement
                if (minecraft.player instanceof LocalPlayer localPlayer) {
                    localPlayer.input.forwardImpulse = 0.0F;
                    localPlayer.input.leftImpulse = 0.0F;
                    localPlayer.input.jumping = false;
                    localPlayer.input.shiftKeyDown = false;
                }
            }

            this.cameraOrbitService.applyRtsView(minecraft);

            if (!(minecraft.screen instanceof BuilderScreen)) {
                minecraft.setScreen(new BuilderScreen(this));
            }

            this.cameraOrbitService.applyEnabledPose(
                    payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                    payload.heightOffset(), payload.yawDeg(), payload.pitchDeg());
            this.mode = BuilderMode.INTERACT;
            this.storageStateManager.clearStorageState();
            this.buildPlacementService.clearPlacementSelectionPreserveMode();
            this.miningOperationService.clearMiningState();
            this.buildPlacementService.setBuildShape(BuildShape.BLOCK);
            this.lastFunnelTarget = null;
            this.funnelTargetCooldownTicks = 0;
            this.storageStateManager.setFunnelWithoutPacket(false);
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
            this.pendingRemoteMenuOpenTicks = 0;
            this.screenlessRemoteMenuTicks = 0;
            clearRemoteMenuValidationState();
            this.storageStateManager.clearQuickSlotsLocal();
            this.storageStateManager.clearGuiBindingsLocal();

            this.cameraOrbitService.setBounds(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
            this.cameraOrbitService.syncVisualCameraFrame(minecraft,
                    payload.anchorX(), payload.anchorY(), payload.anchorZ(),
                    payload.maxRadius(), true);
            requestStoragePage(0);
            return;
        }

        this.enabled = false;
        this.cameraOrbitService.resetServerCameraEntityId();
        this.cameraOrbitService.setLocalStateReady(false);
        this.homeSelectionMode = false;
        this.closeRangeAllowed = false;
        this.cameraOrbitService.clearState();
        this.storageStateManager.clearStorageStateOnDisable();
        this.lastFunnelTarget = null;
        this.funnelTargetCooldownTicks = 0;
        this.pendingCraftTerminalOpen = false;
        this.pendingCraftTerminalOpenTicks = 0;
        this.pendingRemoteMenuOpenTicks = 0;
        this.screenlessRemoteMenuTicks = 0;
        clearRemoteMenuValidationState();

        this.cameraOrbitService.endRotateCapture(0.0D, 0.0D);

        this.buildPlacementService.clearPlacementSelectionPreserveMode();
        this.miningOperationService.clearMiningRenderState();
        this.buildPlacementService.setBuildShape(BuildShape.BLOCK);
        this.storageStateManager.clearQuickSlotsLocal();
        this.storageStateManager.clearGuiBindingsLocal();
        this.storageStateManager.clearStorageScanState();
        this.storageStateManager.clearStorageViewDirty();

        if (minecraft.screen instanceof BuilderScreen) {
            minecraft.setScreen(null);
        }

        this.cameraOrbitService.restorePreviousView(minecraft, minecraft.player);
    }

    /**
     * Updates the local anchor position and camera bounds from a server
     * anchor update payload, keeping client visuals in sync when the
     * server moves the anchor to follow the player entity.
     */
    public void applyServerCameraAnchor(S2CRtsCameraAnchorPayload payload) {
        if (!this.enabled) {
            return;
        }
        this.anchorX = payload.anchorX();
        this.anchorY = payload.anchorY();
        this.anchorZ = payload.anchorZ();
        this.maxRadius = payload.maxRadius();
        this.cameraOrbitService.setBounds(payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.maxRadius());
    }

    public void preTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !this.enabled) {
            clearRemoteMenuValidationState();
        }
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.enabled) {
            this.suppressBuilderScreenRestoreUntilRtsRestart = false;
            return;
        }

        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (handleDeathScreenHandoff(minecraft)) {
            return;
        }

        if (this.funnelTargetCooldownTicks > 0) {
            this.funnelTargetCooldownTicks--;
        }

        boolean hasRemoteMenuOpen = minecraft.player.containerMenu != null
                && minecraft.player.containerMenu.containerId != 0;

        if (hasRemoteMenuOpen
                && minecraft.screen == null
                && this.pendingRemoteMenuOpenTicks <= 0) {
            this.screenlessRemoteMenuTicks++;
            if (this.screenlessRemoteMenuTicks >= SCREENLESS_REMOTE_MENU_RECOVERY_TICKS) {
                RtsClientPacketGateway.sendCloseRemoteMenu();
                minecraft.player.closeContainer();
                clearRemoteMenuValidationState();
                this.relaxedRemoteMenu = null;
                hasRemoteMenuOpen = false;
                this.screenlessRemoteMenuTicks = 0;
            }
        } else {
            this.screenlessRemoteMenuTicks = 0;
        }

        if (this.pendingCraftTerminalOpen
                && minecraft.player.containerMenu instanceof CraftingMenu pendingMenu
                && minecraft.player.containerMenu.containerId != 0
                && !(minecraft.screen instanceof RtsCraftTerminalScreen)) {
            Component pendingTitle = minecraft.screen != null ? minecraft.screen.getTitle() : Component.literal("RTS Craft Terminal");
            minecraft.setScreen(new RtsCraftTerminalScreen(pendingMenu, minecraft.player.getInventory(), pendingTitle));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        }

        if (minecraft.screen instanceof CraftingScreen craftingScreen
                && minecraft.player != null
                && craftingScreen.getMenu() instanceof CraftingMenu craftingMenu
                && !(minecraft.screen instanceof RtsCraftTerminalScreen)
                && shouldUseRtsCraftTerminalScreen(craftingScreen)) {
            minecraft.setScreen(new RtsCraftTerminalScreen(craftingMenu, minecraft.player.getInventory(), craftingScreen.getTitle()));
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
        } else if (this.pendingCraftTerminalOpen) {
            if (this.pendingCraftTerminalOpenTicks > 0) {
                this.pendingCraftTerminalOpenTicks--;
            } else {
                this.pendingCraftTerminalOpen = false;
            }
        }

        if (hasRemoteMenuOpen) {
            this.pendingRemoteMenuOpenTicks = 0;
            try {
                AbstractContainerMenu activeRemoteMenu = RtsClientRemoteMenuCompat.install(minecraft, minecraft.player.containerMenu);
                if (this.relaxedRemoteMenu != activeRemoteMenu) {
                    RtsClientRemoteMenuCompat.relaxValidation(activeRemoteMenu);
                    this.relaxedRemoteMenu = activeRemoteMenu;
                }
                if (minecraft.screen instanceof BuilderScreen) {
                    // First-open GUI construction can leave a brief null-screen handoff. Once a real
                    // container menu exists, let it take over instead of keeping BuilderScreen active.
                    minecraft.setScreen(null);
                }
            } catch (Throwable throwable) {
                handleRemoteMenuOpenFailure(minecraft, throwable);
                hasRemoteMenuOpen = false;
            }
        } else if (this.pendingRemoteMenuOpenTicks > 0) {
            this.pendingRemoteMenuOpenTicks--;
        } else {
            clearRemoteMenuValidationState();
            this.relaxedRemoteMenu = null;
        }

        if (minecraft.screen == null
                && !this.suppressBuilderScreenRestoreUntilRtsRestart
                && !hasRemoteMenuOpen
                && this.pendingRemoteMenuOpenTicks <= 0) {
            minecraft.setScreen(new BuilderScreen(this));
        }

        this.cameraOrbitService.tick(minecraft, this.anchorX, this.anchorY, this.anchorZ, this.maxRadius);
        this.storageStateManager.tickStorageAutoRefresh(this.storageStateManager.isStorageViewDirty());

        // Don't override player.input in RTS mode so the player entity can
        // properly respond to knockback and physics effects.
        // BuilderScreen intercepts input events preventing KeyMapping updates, but
        // the entity's own physics (knockback, gravity) are unaffected since
        // ServerPlayer's input is always null.
        // In RTS mode, prevent keyboard from controlling the player entity
        // (including jumping and sneaking).
        // isControlledCamera() is overridden by LocalPlayerMixin to return true,
        // so Minecraft's native sync mechanism handles position/rotation packets automatically.
        if (minecraft.player instanceof LocalPlayer localPlayer) {
            localPlayer.input.jumping = false;
            localPlayer.input.shiftKeyDown = false;
            localPlayer.input.forwardImpulse = 0.0F;
            localPlayer.input.leftImpulse = 0.0F;

            // RTS flight vertical control: when player is flying in RTS mode,
            // Ctrl+Space = ascend, Shift = descend (direct GLFW key state queries)
            if (localPlayer.getAbilities().flying) {
                long window = minecraft.getWindow().getWindow();
                boolean ctrlHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                boolean spaceHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
                boolean shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

                if (ctrlHeld && spaceHeld) {
                    double upSpeed = localPlayer.getAbilities().getFlyingSpeed() * 3.0;
                    localPlayer.setDeltaMovement(
                            localPlayer.getDeltaMovement().x,
                            upSpeed,
                            localPlayer.getDeltaMovement().z);
                } else if (ctrlHeld && shiftHeld) {
                    double downSpeed = localPlayer.getAbilities().getFlyingSpeed() * 3.0;
                    localPlayer.setDeltaMovement(
                            localPlayer.getDeltaMovement().x,
                            -downSpeed,
                            localPlayer.getDeltaMovement().z);
                }
            }
        }

        this.cameraOrbitService.syncVisualCameraFrame(minecraft, this.anchorX, this.anchorY, this.anchorZ, this.maxRadius, this.enabled);
    }

    private boolean handleDeathScreenHandoff(Minecraft minecraft) {
        boolean dead = !minecraft.player.isAlive() || minecraft.player.isDeadOrDying();
        if (!dead) {
            return false;
        }

        this.suppressBuilderScreenRestoreUntilRtsRestart = true;
        this.homeSelectionMode = false;
        this.pendingCraftTerminalOpen = false;
        this.pendingCraftTerminalOpenTicks = 0;
        this.pendingRemoteMenuOpenTicks = 0;
        this.screenlessRemoteMenuTicks = 0;
        this.miningOperationService.clearMiningRenderState();
        clearRemoteMenuValidationState();

        if (minecraft.screen instanceof BuilderScreen
                || minecraft.screen instanceof RtsHomeScreen
                || minecraft.screen instanceof RtsProgressionScreen
                || minecraft.screen instanceof RtsCraftTerminalScreen) {
            minecraft.setScreen(null);
        }

        this.cameraOrbitService.restorePreviousView(minecraft, minecraft.player);

        this.enabled = false;
        this.closeRangeAllowed = false;
        this.cameraOrbitService.clearStateOnDeath();
        this.cameraOrbitService.resetServerCameraEntityId();
        RtsClientPacketGateway.sendToggleCamera(false);
        return true;
    }

    public void queuePanDrag(double dragX, double dragY) {
        this.cameraOrbitService.queuePanDrag(dragX, dragY);
    }

    public void queueRotateDrag(double dragX, double dragY) {
        this.cameraOrbitService.queueRotateDrag(dragX, dragY);
    }

    public void queueScroll(double scrollY) {
        this.cameraOrbitService.queueScroll(scrollY);
    }

    public void queueRotateQuarter(int direction) {
        this.cameraOrbitService.queueRotateQuarter(direction);
    }

    public void updateFunnelTarget(BlockPos target) {
        if (!this.storageStateManager.isFunnelEnabled() || target == null) {
            return;
        }
        if (this.funnelTargetCooldownTicks > 0) {
            return;
        }
        if (this.lastFunnelTarget != null && this.lastFunnelTarget.equals(target)) {
            return;
        }
        this.lastFunnelTarget = target.immutable();
        this.funnelTargetCooldownTicks = 2;
        RtsClientPacketGateway.sendFunnelTarget(this.lastFunnelTarget);
    }

    public void linkStorage(BlockPos pos) {
        this.storageStateManager.linkStorage(pos);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        this.storageStateManager.linkStorage(pos, allowStore);
    }

    public void requestStoragePage(int page) {
        this.storageStateManager.requestStoragePage(page);
    }

    public void updateStoragePageSize(int pageSize) {
        this.storageStateManager.updateStoragePageSize(pageSize);
    }

    public void requestStoragePageIfNoSnapshot(int page) {
        this.storageStateManager.requestStoragePageIfNoSnapshot(page);
    }

    public void refreshStoragePage() {
        this.storageStateManager.refreshStoragePage();
    }



    public void requestCraftables() {
        this.storageStateManager.requestCraftables();
    }

    public void requestMoreCraftables() {
        this.storageStateManager.requestMoreCraftables();
    }

    public void setAutoStoreMinedDrops(boolean enabled) {
        this.storageStateManager.setAutoStoreMinedDrops(enabled);
    }

    public void toggleAutoStoreMinedDrops() {
        this.storageStateManager.toggleAutoStoreMinedDrops();
    }

    public void setStorageSearch(String search) {
        this.storageStateManager.setStorageSearch(search);
    }

    public void setStorageCategory(String category) {
        this.storageStateManager.setStorageCategory(category);
    }

    public void cycleSort() {
        this.storageStateManager.cycleSort();
    }

    public void toggleSortDirection() {
        this.storageStateManager.toggleSortDirection();
    }

    public void prevPage() {
        this.storageStateManager.prevPage();
    }

    public void nextPage() {
        this.storageStateManager.nextPage();
    }

    public void setCraftablesSearch(String search) {
        this.storageStateManager.setCraftablesSearch(search);
    }

    public void setCraftablesShowUnavailable(boolean showUnavailable) {
        this.storageStateManager.setCraftablesShowUnavailable(showUnavailable);
    }

    public void toggleCraftablesShowUnavailable() {
        this.storageStateManager.toggleCraftablesShowUnavailable();
    }

    public void craftRecipeToLinked(String recipeId) {
        this.storageStateManager.craftRecipeToLinked(recipeId);
    }

    public void craftRecipeToLinked(String recipeId, int craftCount) {
        this.storageStateManager.craftRecipeToLinked(recipeId, craftCount);
    }

    public void openCraftTerminal() {
        this.storageStateManager.setStorageSearch("");
        this.pendingCraftTerminalOpen = true;
        this.pendingCraftTerminalOpenTicks = 120;
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendOpenCraftTerminal();
    }

    public void detectQuestsNow() {
        beginQuestDetectScan();
        RtsClientPacketGateway.sendQuestDetectManual();
    }

    private void beginQuestDetectScan() {
        long now = System.currentTimeMillis();
        this.questDetectPhase = S2CRtsQuestDetectStatusPayload.PHASE_STARTED;
        this.questDetectStartedAtMs = now;
        this.questDetectFinishedAtMs = 0L;
        this.questDetectExpiryMs = 0L;
        this.questDetectScannedTasks = 0;
        this.questDetectTotalTasks = 0;
        this.questDetectCompletedTasks = 0;
    }

    public void rotateBlock(BlockPos pos) {
        if (pos == null) {
            return;
        }
        RtsClientPacketGateway.sendRotateBlock(pos);
    }

    public void storeHotbarSlotToLinked(int slot) {
        RtsClientPacketGateway.sendStoreHotbarSlot(slot);
    }

    public void fillInventoryFromLinked() {
        RtsClientPacketGateway.sendFillInventory();
    }

    public void unlinkLinkedStorage(BlockPos pos) {
        RtsClientPacketGateway.sendUnlinkStorage(pos);
    }

    public void updateLinkedStorageSettings(BlockPos pos, boolean extractOnly, int priority) {
        RtsClientPacketGateway.sendUpdateLinkedStorage(pos, extractOnly, priority);
    }

    private boolean shouldUseRtsCraftTerminalScreen(CraftingScreen craftingScreen) {
        if (this.pendingCraftTerminalOpen) {
            return true;
        }
        return craftingScreen.getTitle() != null
                && "RTS Craft Terminal".equals(craftingScreen.getTitle().getString());
    }

    public void quickDropSelectedItem(String itemId, int amount, Vec3 dropPos) {
        if (itemId == null || itemId.isBlank() || dropPos == null) {
            return;
        }
        RtsClientPacketGateway.sendQuickDrop(itemId, amount, dropPos);
    }

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        this.storageStateManager.applyStoragePage(payload, this::refreshSelectedItemPreviewFromStorage);
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        this.storageStateManager.applyCraftables(payload);
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        this.storageStateManager.applyCraftFeedback(payload);
    }

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        this.storageStateManager.applyStorageDirty(payload);
    }





    private void refreshSelectedItemPreviewFromStorage() {
        this.buildPlacementService.syncSelectedPreviewFromStorage(
                this.storageStateManager.getInternalStorageEntries(),
                this.storageStateManager.hasStoragePageSnapshot(),
                this.storageStateManager.getStorageTotalCount(this.buildPlacementService.getSelectedItemId()));
    }

    private boolean shouldAutoClearSelectedItemWhenUnavailable() {
        if (isLocalPlayerCreative()) {
            return false;
        }
        ItemStack preview = this.buildPlacementService.getSelectedItemPreview();
        return preview != null
                && !preview.isEmpty()
                && preview.getItem() instanceof BlockItem;
    }

    public void applyRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload) {
        beginRemoteMenuOpenGrace();
    }





    private static String normalizeCraftablesSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private static double clampLayoutNormalized(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Mth.clamp(value, 0.0D, 1.0D);
    }





    public void applyDamageFeedback(S2CRtsDamageFeedbackPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen instanceof BuilderScreen builderScreen) {
            builderScreen.triggerDamageFlash();
        }
        if (this.damageSoundEnabled) {
            float volume = Mth.clamp(0.45F + Math.max(0.0F, payload.amount()) * 0.08F, 0.45F, 1.2F);
            minecraft.player.playSound(SoundEvents.PLAYER_HURT, volume, 1.0F);
        }
        if (payload.lowHealth() && this.damageAutoReturnEnabled && this.enabled) {
            RtsClientPacketGateway.sendToggleCamera(this.startCameraAtPlayerHead);
        }
    }

    public void applyQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload) {
        if (payload == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (payload.phase() == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            if (this.questDetectPhase != S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
                beginQuestDetectScan();
            }
            this.questDetectScannedTasks = Math.max(0, payload.scannedTasks());
            this.questDetectTotalTasks = Math.max(0, payload.totalTasks());
            this.questDetectCompletedTasks = Math.max(0, payload.completedTasks());
            return;
        }
        if (this.questDetectStartedAtMs <= 0L) {
            this.questDetectStartedAtMs = now;
        }
        this.questDetectPhase = payload.phase();
        this.questDetectFinishedAtMs = now;
        this.questDetectExpiryMs = now + QUEST_DETECT_RESULT_VISIBLE_MS;
        this.questDetectScannedTasks = Math.max(0, payload.scannedTasks());
        this.questDetectTotalTasks = Math.max(0, payload.totalTasks());
        this.questDetectCompletedTasks = Math.max(0, payload.completedTasks());
    }



    public void applyMineProgress(S2CRtsMineProgressPayload payload) {
        this.miningOperationService.applyMineProgress(payload.pos(), payload.stage());
    }

    public void applyUltimineProgress(S2CRtsUltimineProgressPayload payload) {
        this.miningOperationService.applyUltimineProgress(payload.processed(), payload.total());
    }

    public void applyProgressionState(S2CRtsProgressionStatePayload payload) {
        this.progressionStateManager.applyProgressionState(payload, () -> this.homeSelectionMode = false);
    }

    public void requestProgressionState() {
        this.progressionStateManager.requestProgressionState();
    }

    public void unlockProgressionNode(ResourceLocation nodeId) {
        this.progressionStateManager.unlockProgressionNode(nodeId);
    }

    public void setSurvivalProgressionEnabled(boolean enabled) {
        this.progressionStateManager.setSurvivalProgressionEnabled(enabled, () -> this.homeSelectionMode = false);
    }

    public void setProgressionCost(ResourceLocation nodeId, String costsText) {
        this.progressionStateManager.setProgressionCost(nodeId, costsText);
    }

    public void setHome(BlockPos pos) {
        this.progressionStateManager.setHome(pos);
    }

    public void beginHomeSelection() {
        this.progressionStateManager.beginHomeSelection();
    }

    public void selectStorageEntry(int index) {
        this.buildPlacementService.selectStorageEntry(index, this.storageStateManager.getStorageEntries(),
                () -> setMode(BuilderMode.INTERACT));
    }

    public void selectFluidEntry(int index) {
        this.buildPlacementService.selectFluidEntry(index, this.storageStateManager.getFluidEntries(),
                () -> setMode(BuilderMode.INTERACT));
    }

    public void clearSelectedItem() {
        this.buildPlacementService.clearSelectedItem(() -> setMode(BuilderMode.INTERACT));
    }

    public void clearPlacementSelectionPreserveMode() {
        this.buildPlacementService.clearPlacementSelectionPreserveMode();
    }

    public void selectEmptyHand() {
        this.buildPlacementService.selectEmptyHand(() -> setMode(BuilderMode.INTERACT));
    }

    public void selectRecentEntry(int index) {
        this.buildPlacementService.selectRecentEntry(index, this.storageStateManager.getRecentEntries(),
                () -> setMode(BuilderMode.INTERACT));
    }

    public void assignQuickSlotFromSelected(int index) {
        this.storageStateManager.assignQuickSlotFromSelected(index,
                this.buildPlacementService.getSelectedItemId(),
                this.buildPlacementService.getSelectedItemPreview());
    }

    public void assignQuickSlotFromToolItem(int index, ItemStack stack) {
        this.storageStateManager.assignQuickSlotFromToolItem(index, stack);
    }

    public void clearQuickSlot(int index) {
        this.storageStateManager.clearQuickSlot(index);
    }

    public void selectQuickSlot(int index) {
        if (index < 0 || index >= StorageStateManager.QUICK_SLOT_COUNT) {
            return;
        }
        this.buildPlacementService.selectQuickSlot(index,
                this.storageStateManager.getQuickSlotItemId(index),
                this.storageStateManager.getQuickSlotPreview(index),
                this.storageStateManager.getQuickSlotLabel(index),
                () -> setMode(BuilderMode.INTERACT));
    }

    public void selectItemForPlacement(String itemId, String label, ItemStack preview) {
        this.buildPlacementService.selectItemForPlacement(itemId, label, preview,
                () -> setMode(BuilderMode.INTERACT));
    }

    public void setGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        this.storageStateManager.setGuiBinding(index, pos, face, itemIdHint);
    }

    public void clearGuiBinding(int index) {
        this.storageStateManager.clearGuiBinding(index);
    }

    public void openGuiBinding(int index) {
        this.storageStateManager.openGuiBinding(index);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        placeSelected(hit, forcePlace, rayOrigin, rayDir, false, false);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
        placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied,
            boolean quickBuild) {
        String itemId = this.buildPlacementService.getSelectedItemId();
        this.buildPlacementService.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, quickBuild,
                this::beginRemoteMenuOpenGrace,
                () -> {
                    if (isLocalPlayerCreative()) return false;
                    ItemStack preview = this.buildPlacementService.getSelectedItemPreview();
                    return preview != null && !preview.isEmpty()
                            && preview.getItem() instanceof BlockItem
                            && this.storageStateManager.hasStoragePageSnapshot()
                            && this.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                },
                () -> requestStoragePage(this.storageStateManager.getStoragePage()),
                isLocalPlayerCreative(),
                this.storageStateManager.getStorageTotalCount(itemId),
                this.storageStateManager.hasStoragePageSnapshot());
    }

    public void placeSelectedBatch(List<BlockHitResult> hits, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
            boolean skipIfOccupied) {
        placeSelectedBatch(hits, hits == null || hits.isEmpty() ? null : hits.get(0), forcePlace, rayOrigin, rayDir,
                skipIfOccupied);
    }

    public void placeSelectedBatch(List<BlockHitResult> hits, BlockHitResult templateHit, boolean forcePlace,
            Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
        String itemId = this.buildPlacementService.getSelectedItemId();
        this.buildPlacementService.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied,
                this::beginRemoteMenuOpenGrace,
                () -> {
                    if (isLocalPlayerCreative()) return false;
                    ItemStack preview = this.buildPlacementService.getSelectedItemPreview();
                    return preview != null && !preview.isEmpty()
                            && preview.getItem() instanceof BlockItem
                            && this.storageStateManager.hasStoragePageSnapshot()
                            && this.storageStateManager.getStorageTotalCount(itemId) <= 0L;
                },
                () -> requestStoragePage(this.storageStateManager.getStoragePage()),
                isLocalPlayerCreative(),
                this.storageStateManager.getStorageTotalCount(itemId),
                this.storageStateManager.hasStoragePageSnapshot());
    }

    public void placeSelectedFluid(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
    }

    public void storeFluidFromStorageItem(String itemId) {
        this.buildPlacementService.storeFluidFromStorageItem(itemId);
    }

    public void storeFluidFromPinnedItem(String itemId) {
        this.buildPlacementService.storeFluidFromPinnedItem(itemId);
    }

    public void storeFluidFromToolSlot(int toolSlot) {
        this.buildPlacementService.storeFluidFromToolSlot(toolSlot);
    }


    public void interactEmpty(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactEmpty(hit, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void interactEntityEmpty(int entityId, Vec3 hitLocation, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactEntityEmpty(entityId, hitLocation, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void interactBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void useItemInAirWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.useItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void interactBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void interactEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void interactEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        this.buildPlacementService.interactEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir, this::beginRemoteMenuOpenGrace);
    }

    public void breakPlaced(BlockPos pos) {
        this.buildPlacementService.breakPlaced(pos, Direction.UP, false);
    }

    public void breakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        this.buildPlacementService.breakPlaced(pos, face, allowAdjacentFallback);
    }

    public void startMining(BlockPos pos, int face, int toolSlot) {
        this.miningOperationService.startMining(pos, face, toolSlot,
                this.buildPlacementService.getSelectedItemId(),
                this.buildPlacementService.getSelectedItemPreview(),
                this.allowPlacedBlockRecovery, this.toolProtectionEnabled);
    }

    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode) {
        this.miningOperationService.startUltimine(pos, face, toolSlot, limit, mode,
                this.buildPlacementService.getSelectedItemId(),
                this.buildPlacementService.getSelectedItemPreview(),
                this.toolProtectionEnabled);
    }

    public void continueMining(int toolSlot) {
        this.miningOperationService.continueMining(toolSlot);
    }

    public int getAreaMinePhase() {
        return this.miningOperationService.getAreaMinePhase();
    }

    public BlockPos getAreaMinePointA() {
        return this.miningOperationService.getAreaMinePointA();
    }

    public BlockPos getAreaMinePointB() {
        return this.miningOperationService.getAreaMinePointB();
    }

    public int getAreaMineHeightOffset() {
        return this.miningOperationService.getAreaMineHeightOffset();
    }

    public static AreaMineBounds computeAreaMineBounds(BlockPos pointA, BlockPos pointB, int heightOffset) {
        return MiningOperationService.computeAreaMineBounds(pointA, pointB, heightOffset);
    }

    public void setAreaMineHeightOffset(int offset) {
        this.miningOperationService.setAreaMineHeightOffset(offset);
    }

    public void adjustAreaMineHeightOffset(int delta) {
        this.miningOperationService.adjustAreaMineHeightOffset(delta);
    }

    public void setAreaMinePointA(BlockPos pos) {
        this.miningOperationService.setAreaMinePointA(pos, this.anchorX, this.anchorZ, this.maxRadius, hasBounds());
    }

    public void setAreaMinePointB(BlockPos pos) {
        this.miningOperationService.setAreaMinePointB(pos, this.anchorX, this.anchorZ, this.maxRadius, hasBounds());
    }

    public void clearAreaMineSession() {
        this.miningOperationService.clearAreaMineSession();
    }

    public void confirmAreaMine(int toolSlot, ShapeFillMode fillMode) {
        this.miningOperationService.confirmAreaMine(toolSlot, fillMode,
                this.buildPlacementService.getSelectedItemId(),
                this.buildPlacementService.getSelectedItemPreview(),
                this.toolProtectionEnabled);
    }

    public void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot) {
        this.miningOperationService.confirmShapeAreaDestroy(targets, toolSlot,
                this.buildPlacementService.getSelectedItemId(),
                this.buildPlacementService.getSelectedItemPreview(),
                this.toolProtectionEnabled);
    }

    public void abortMining(int toolSlot) {
        this.miningOperationService.abortMining(toolSlot);
    }

    public int getMineProgressStage() {
        return this.miningOperationService.getMineProgressStage();
    }

    public int getUltimineProgressProcessed() {
        return this.miningOperationService.getUltimineProgressProcessed();
    }

    public int getUltimineProgressTotal() {
        return this.miningOperationService.getUltimineProgressTotal();
    }

    public BlockPos getMineProgressPos() {
        return this.miningOperationService.getMineProgressPos();
    }

    public BlockPos getMineProgressCompletedPos() {
        return this.miningOperationService.getMineProgressCompletedPos();
    }

    public long getMineProgressCompletedAtMs() {
        return this.miningOperationService.getMineProgressCompletedAtMs();
    }

    private void beginRemoteMenuOpenGrace() {
        this.pendingRemoteMenuOpenTicks = Math.max(this.pendingRemoteMenuOpenTicks, REMOTE_MENU_OPEN_GRACE_TICKS);
        this.screenlessRemoteMenuTicks = 0;
        RtsRemoteMenuCompat.beginClientRemoteMenuOpen();
    }

    private void handleRemoteMenuOpenFailure(Minecraft minecraft, Throwable throwable) {
        String menuClass = minecraft.player != null && minecraft.player.containerMenu != null
                ? minecraft.player.containerMenu.getClass().getName()
                : "null";
        String screenClass = minecraft.screen != null ? minecraft.screen.getClass().getName() : "null";
        RtsbuildingMod.LOGGER.error(
                "RTS remote menu open failed for menu {} on screen {}; closing container to prevent a client crash.",
                menuClass,
                screenClass,
                throwable);
        clearRemoteMenuValidationState();
        this.pendingRemoteMenuOpenTicks = 0;
        if (minecraft.player != null) {
            RtsClientPacketGateway.sendCloseRemoteMenu();
            minecraft.player.closeContainer();
            minecraft.player.displayClientMessage(Component.literal("Open failed."), true);
        }
        minecraft.setScreen(null);
    }

    private void clearRemoteMenuValidationState() {
        this.relaxedRemoteMenu = null;
        RtsRemoteMenuCompat.clearClientRemoteMenu();
    }


    private boolean isLocalPlayerCreative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }


    public void rotatePlacementClockwise() {
        this.buildPlacementService.rotatePlacementClockwise();
    }

    public void rotatePlacementCounterClockwise() {
        this.buildPlacementService.rotatePlacementCounterClockwise();
    }

    public void syncVisualCameraFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        this.cameraOrbitService.syncVisualCameraFrame(minecraft, this.anchorX, this.anchorY, this.anchorZ, this.maxRadius, this.enabled);
    }

}



