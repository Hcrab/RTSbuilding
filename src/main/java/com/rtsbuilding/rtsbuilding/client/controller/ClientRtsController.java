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
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.block.state.IBlockState;
import org.lwjgl.input.Keyboard;

import java.util.List;

public final class ClientRtsController extends ClientRtsWorkflowFacade {

    private static final ClientRtsController INSTANCE = new ClientRtsController();
    static final int RTS_MINE_RENDER_ID = 0x525453;
    static final int REMOTE_MENU_OPEN_GRACE_TICKS = 80;
    static final int SCREENLESS_REMOTE_MENU_RECOVERY_TICKS = 10;
    static final long QUEST_DETECT_MIN_PROGRESS_MS = 700L;
    static final long QUEST_DETECT_RESULT_VISIBLE_MS = 3500L;

    boolean enabled;

    double anchorX;
    double anchorY;
    double anchorZ;
    double maxRadius;
    boolean homeSelectionMode;
    boolean closeRangeAllowed;
    boolean suppressBuilderScreenRestoreUntilRtsRestart;

    BuilderMode mode = BuilderMode.INTERACT;
    byte questDetectPhase = -1;
    long questDetectStartedAtMs;
    long questDetectFinishedAtMs;
    long questDetectExpiryMs;
    int questDetectScannedTasks;
    int questDetectTotalTasks;
    int questDetectCompletedTasks;
    boolean chunkCurtainVisible;

    final StorageStateManager storageStateManager = new StorageStateManager();
    final ProgressionStateManager progressionStateManager = new ProgressionStateManager();
    final PluginStateManager pluginStateManager = new PluginStateManager();
    final MiningOperationService miningOperationService = new MiningOperationService();
    final BuildPlacementService buildPlacementService = new BuildPlacementService();

    BlockPos lastFunnelTarget;
    int funnelTargetCooldownTicks;
    boolean pendingCraftTerminalOpen;
    int pendingCraftTerminalOpenTicks;
    int pendingRemoteMenuOpenTicks;
    int screenlessRemoteMenuTicks;
    Container relaxedRemoteMenu;

    private final ClientRtsStateQueryOwner stateQueryOwner = new ClientRtsStateQueryOwner(this);
    private final ClientRtsLifecycleOwner lifecycleOwner = new ClientRtsLifecycleOwner(this);
    private final ClientRtsCommandOwner commandOwner = new ClientRtsCommandOwner(this);
    private final ClientRtsInteractionOwner interactionOwner = new ClientRtsInteractionOwner(this);

private ClientRtsController() {
    }

public static ClientRtsController get() {
        return INSTANCE;
    }
    public boolean isEnabled() { return this.stateQueryOwner.isEnabled(); }
    public boolean canUseStorageOverlay() { return this.stateQueryOwner.canUseStorageOverlay(); }
    public double getAnchorX() { return this.stateQueryOwner.getAnchorX(); }
    public double getAnchorY() { return this.stateQueryOwner.getAnchorY(); }
    public double getAnchorZ() { return this.stateQueryOwner.getAnchorZ(); }
    public double getMaxRadius() { return this.stateQueryOwner.getMaxRadius(); }
    public boolean hasBounds() { return this.stateQueryOwner.hasBounds(); }
    public boolean isHomeSelectionMode() { return this.stateQueryOwner.isHomeSelectionMode(); }
    public boolean isProgressionEnabled() { return this.stateQueryOwner.isProgressionEnabled(); }
    public boolean isProgressionHomeSet() { return this.stateQueryOwner.isProgressionHomeSet(); }
    public BlockPos getProgressionHomePos() { return this.stateQueryOwner.getProgressionHomePos(); }
    public String getProgressionHomeDimension() { return this.stateQueryOwner.getProgressionHomeDimension(); }
    public long getProgressionHomeCooldownTicks() { return this.stateQueryOwner.getProgressionHomeCooldownTicks(); }
    public int getProgressionRadiusBlocks() { return this.stateQueryOwner.getProgressionRadiusBlocks(); }
    public int getProgressionFluidCapacityBuckets() { return this.stateQueryOwner.getProgressionFluidCapacityBuckets(); }
    public int getProgressionUltimineLimit() { return this.stateQueryOwner.getProgressionUltimineLimit(); }
    public boolean isProgressionBypassHomeRadius() { return this.stateQueryOwner.isProgressionBypassHomeRadius(); }
    public List<PluginStateManager.InstalledPluginView> getInstalledPlugins() { return this.stateQueryOwner.getInstalledPlugins(); }
    public String getPluginTeamName() { return this.stateQueryOwner.getPluginTeamName(); }
    public boolean hasInstalledPlugin(String pluginId) { return this.stateQueryOwner.hasInstalledPlugin(pluginId); }
    public BuilderMode getMode() { return this.stateQueryOwner.getMode(); }
    public void setMode(BuilderMode mode) { this.stateQueryOwner.setMode(mode); }
    public boolean isFunnelEnabled() { return this.stateQueryOwner.isFunnelEnabled(); }
    public void setFunnelEnabled(boolean enabled) { this.stateQueryOwner.setFunnelEnabled(enabled); }
    public void toggleFunnelEnabled() { this.stateQueryOwner.toggleFunnelEnabled(); }
    public boolean isStorageCollapsed() { return this.stateQueryOwner.isStorageCollapsed(); }
    public void toggleStorageCollapsed() { this.stateQueryOwner.toggleStorageCollapsed(); }
    public double getStoragePanelXNormalized() { return this.stateQueryOwner.getStoragePanelXNormalized(); }
    public double getStoragePanelYNormalized() { return this.stateQueryOwner.getStoragePanelYNormalized(); }
    public double getStoragePanelWidthNormalized() { return this.stateQueryOwner.getStoragePanelWidthNormalized(); }
    public double getStoragePanelHeightNormalized() { return this.stateQueryOwner.getStoragePanelHeightNormalized(); }
    public void updateStoragePanelLayout(double xNormalized, double yNormalized, double widthNormalized, double heightNormalized) { this.stateQueryOwner.updateStoragePanelLayout(xNormalized, yNormalized, widthNormalized, heightNormalized); }
    public boolean isStorageLinked() { return this.stateQueryOwner.isStorageLinked(); }
    public String getLinkedStorageName() { return this.stateQueryOwner.getLinkedStorageName(); }
    public List<BlockPos> getLinkedStoragePositions() { return this.stateQueryOwner.getLinkedStoragePositions(); }
    public List<LinkedStorageEntry> getLinkedStorageEntries() { return this.stateQueryOwner.getLinkedStorageEntries(); }
    public int getStoragePage() { return this.stateQueryOwner.getStoragePage(); }
    public int getStorageTotalPages() { return this.stateQueryOwner.getStorageTotalPages(); }
    public int getStorageTotalEntries() { return this.stateQueryOwner.getStorageTotalEntries(); }
    public int getStorageRevision() { return this.stateQueryOwner.getStorageRevision(); }
    public String getStorageSearch() { return this.stateQueryOwner.getStorageSearch(); }
    public RtsStorageSort getStorageSort() { return this.stateQueryOwner.getStorageSort(); }
    public boolean isStorageSortAscending() { return this.stateQueryOwner.isStorageSortAscending(); }
    public String getStorageCategory() { return this.stateQueryOwner.getStorageCategory(); }
    public List<String> getStorageCategories() { return this.stateQueryOwner.getStorageCategories(); }
    public String getSelectedItemId() { return this.stateQueryOwner.getSelectedItemId(); }
    public String getSelectedItemLabel() { return this.stateQueryOwner.getSelectedItemLabel(); }
    public String getSelectedFluidId() { return this.stateQueryOwner.getSelectedFluidId(); }
    public String getSelectedFluidLabel() { return this.stateQueryOwner.getSelectedFluidLabel(); }
    public boolean hasSelectedItem() { return this.stateQueryOwner.hasSelectedItem(); }
    public boolean hasSelectedFluid() { return this.stateQueryOwner.hasSelectedFluid(); }
    public boolean isEmptyHandSelected() { return this.stateQueryOwner.isEmptyHandSelected(); }
    public ItemStack getSelectedItemPreview() { return this.stateQueryOwner.getSelectedItemPreview(); }
    public ItemStack getSelectedFluidPreview() { return this.stateQueryOwner.getSelectedFluidPreview(); }
    public int getPlaceRotateDegrees() { return this.stateQueryOwner.getPlaceRotateDegrees(); }
    public String getPlacementStatePreset() { return this.stateQueryOwner.getPlacementStatePreset(); }
    public List<StorageEntry> getStorageEntries() { return this.stateQueryOwner.getStorageEntries(); }
    public long getStorageTotalCount(String itemId) { return this.stateQueryOwner.getStorageTotalCount(itemId); }
    public List<FluidEntry> getFluidEntries() { return this.stateQueryOwner.getFluidEntries(); }
    public List<RecentEntry> getRecentEntries() { return this.stateQueryOwner.getRecentEntries(); }
    public long getRecentDisplayAmount(RecentEntry entry) { return this.stateQueryOwner.getRecentDisplayAmount(entry); }
    public String getCraftablesSearch() { return this.stateQueryOwner.getCraftablesSearch(); }
    public boolean isCraftablesShowUnavailable() { return this.stateQueryOwner.isCraftablesShowUnavailable(); }
    public List<CraftableEntry> getCraftableEntries() { return this.stateQueryOwner.getCraftableEntries(); }
    public int getCraftablesRevision() { return this.stateQueryOwner.getCraftablesRevision(); }
    public boolean hasMoreCraftables() { return this.stateQueryOwner.hasMoreCraftables(); }
    public String getCraftFeedbackItemId() { return this.stateQueryOwner.getCraftFeedbackItemId(); }
    public int getCraftFeedbackCount() { return this.stateQueryOwner.getCraftFeedbackCount(); }
    public long getCraftFeedbackExpiryMs() { return this.stateQueryOwner.getCraftFeedbackExpiryMs(); }
    public List<CraftFeedbackIngredient> getCraftFeedbackIngredients() { return this.stateQueryOwner.getCraftFeedbackIngredients(); }
    public boolean isQuestDetectPopupVisible() { return this.stateQueryOwner.isQuestDetectPopupVisible(); }
    public boolean isQuestDetectRunning() { return this.stateQueryOwner.isQuestDetectRunning(); }
    public byte getQuestDetectPhase() { return this.stateQueryOwner.getQuestDetectPhase(); }
    public float getQuestDetectProgress() { return this.stateQueryOwner.getQuestDetectProgress(); }
    public boolean isStorageScanPopupVisible() { return this.stateQueryOwner.isStorageScanPopupVisible(); }
    public boolean isStorageScanRunning() { return this.stateQueryOwner.isStorageScanRunning(); }
    public boolean isStorageViewDirty() { return this.stateQueryOwner.isStorageViewDirty(); }
    public boolean shouldHighlightStorageRefresh() { return this.stateQueryOwner.shouldHighlightStorageRefresh(); }
    public float getStorageScanProgress() { return this.stateQueryOwner.getStorageScanProgress(); }
    public void clearStorageScanPopupState() { this.stateQueryOwner.clearStorageScanPopupState(); }
    public boolean hasStoragePageSnapshot() { return this.stateQueryOwner.hasStoragePageSnapshot(); }
    public int getQuestDetectScannedTasks() { return this.stateQueryOwner.getQuestDetectScannedTasks(); }
    public int getQuestDetectTotalTasks() { return this.stateQueryOwner.getQuestDetectTotalTasks(); }
    public int getQuestDetectCompletedTasks() { return this.stateQueryOwner.getQuestDetectCompletedTasks(); }
    public List<FunnelBufferEntry> getFunnelBufferEntries() { return this.stateQueryOwner.getFunnelBufferEntries(); }
    public boolean isAutoStoreMinedDrops() { return this.stateQueryOwner.isAutoStoreMinedDrops(); }
    public boolean isBdNetworkEnabled() { return this.stateQueryOwner.isBdNetworkEnabled(); }
    public void setBdNetworkEnabled(boolean enabled) { this.stateQueryOwner.setBdNetworkEnabled(enabled); }
    public void toggleBdNetworkEnabled() { this.stateQueryOwner.toggleBdNetworkEnabled(); }
    public AreaMineShape getAreaMineShape() { return this.stateQueryOwner.getAreaMineShape(); }
    public void setAreaMineShape(AreaMineShape shape) { this.stateQueryOwner.setAreaMineShape(shape); }
    public BuildShape getBuildShape() { return this.stateQueryOwner.getBuildShape(); }
    public void setBuildShape(BuildShape shape) { this.stateQueryOwner.setBuildShape(shape); }
    public boolean isChunkCurtainVisible() { return this.stateQueryOwner.isChunkCurtainVisible(); }
    public void setChunkCurtainVisible(boolean visible) { this.stateQueryOwner.setChunkCurtainVisible(visible); }
    public void cycleBuildShape(int step) { this.stateQueryOwner.cycleBuildShape(step); }
    public int getQuickSlotCount() { return this.stateQueryOwner.getQuickSlotCount(); }
    public String getQuickSlotItemId(int index) { return this.stateQueryOwner.getQuickSlotItemId(index); }
    public String getQuickSlotLabel(int index) { return this.stateQueryOwner.getQuickSlotLabel(index); }
    public ItemStack getQuickSlotPreview(int index) { return this.stateQueryOwner.getQuickSlotPreview(index); }
    public int getGuiBindingCount() { return this.stateQueryOwner.getGuiBindingCount(); }
    public String getGuiBindingLabel(int index) { return this.stateQueryOwner.getGuiBindingLabel(index); }
    public ItemStack getGuiBindingPreview(int index) { return this.stateQueryOwner.getGuiBindingPreview(index); }
    public boolean hasGuiBinding(int index) { return this.stateQueryOwner.hasGuiBinding(index); }
    public void applyServerCameraState(S2CRtsCameraStatePayload payload) { this.lifecycleOwner.applyServerCameraState(payload); }
    public void applyServerCameraAnchor(S2CRtsCameraAnchorPayload payload) { this.lifecycleOwner.applyServerCameraAnchor(payload); }
    public void preTick() { this.lifecycleOwner.preTick(); }
    public void tick() { this.lifecycleOwner.tick(); }
    boolean handleDeathScreenHandoff(Minecraft minecraft) { return this.lifecycleOwner.handleDeathScreenHandoff(minecraft); }
    public void queuePanDrag(double dragX, double dragY) { this.lifecycleOwner.queuePanDrag(dragX, dragY); }
    public void queueRotateDrag(double dragX, double dragY) { this.lifecycleOwner.queueRotateDrag(dragX, dragY); }
    public void queueScroll(double scrollY) { this.lifecycleOwner.queueScroll(scrollY); }
    public void queueRotateQuarter(int direction) { this.lifecycleOwner.queueRotateQuarter(direction); }
    public void updateFunnelTarget(BlockPos target) { this.lifecycleOwner.updateFunnelTarget(target); }
    public void linkStorage(BlockPos pos) { this.commandOwner.linkStorage(pos); }
    public void linkStorage(BlockPos pos, boolean allowStore) { this.commandOwner.linkStorage(pos, allowStore); }
    public void requestStoragePage(int page) { this.commandOwner.requestStoragePage(page); }
    public void updateStoragePageSize(int pageSize) { this.commandOwner.updateStoragePageSize(pageSize); }
    public void requestStoragePageIfNoSnapshot(int page) { this.commandOwner.requestStoragePageIfNoSnapshot(page); }
    public void refreshStoragePage() { this.commandOwner.refreshStoragePage(); }
    public void requestCraftables() { this.commandOwner.requestCraftables(); }
    public void requestMoreCraftables() { this.commandOwner.requestMoreCraftables(); }
    public void setAutoStoreMinedDrops(boolean enabled) { this.commandOwner.setAutoStoreMinedDrops(enabled); }
    public void toggleAutoStoreMinedDrops() { this.commandOwner.toggleAutoStoreMinedDrops(); }
    public void setStorageSearch(String search) { this.commandOwner.setStorageSearch(search); }
    public void setStorageCategory(String category) { this.commandOwner.setStorageCategory(category); }
    public void cycleSort() { this.commandOwner.cycleSort(); }
    public void toggleSortDirection() { this.commandOwner.toggleSortDirection(); }
    public void prevPage() { this.commandOwner.prevPage(); }
    public void nextPage() { this.commandOwner.nextPage(); }
    public void setCraftablesSearch(String search) { this.commandOwner.setCraftablesSearch(search); }
    public void setCraftablesShowUnavailable(boolean showUnavailable) { this.commandOwner.setCraftablesShowUnavailable(showUnavailable); }
    public void toggleCraftablesShowUnavailable() { this.commandOwner.toggleCraftablesShowUnavailable(); }
    public void craftRecipeToLinked(String recipeId) { this.commandOwner.craftRecipeToLinked(recipeId); }
    public void craftRecipeToLinked(String recipeId, int craftCount) { this.commandOwner.craftRecipeToLinked(recipeId, craftCount); }
    public void openCraftTerminal() { this.commandOwner.openCraftTerminal(); }
    public void detectQuestsNow() { this.commandOwner.detectQuestsNow(); }
    void beginQuestDetectScan() { this.commandOwner.beginQuestDetectScan(); }
    public void rotateBlock(BlockPos pos) { this.commandOwner.rotateBlock(pos); }
    public void rotateBlockStep( BlockPos pos, EnumFacing axisDirection, int quarterTurns) { this.commandOwner.rotateBlockStep(pos, axisDirection, quarterTurns); }
    public void storeHotbarSlotToLinked(int slot) { this.commandOwner.storeHotbarSlotToLinked(slot); }
    public void fillInventoryFromLinked() { this.commandOwner.fillInventoryFromLinked(); }
    public void unlinkLinkedStorage(BlockPos pos) { this.commandOwner.unlinkLinkedStorage(pos); }
    public void updateLinkedStorageSettings(BlockPos pos, boolean extractOnly, int priority) { this.commandOwner.updateLinkedStorageSettings(pos, extractOnly, priority); }
    boolean shouldUseRtsCraftTerminalScreen(GuiCrafting craftingScreen) { return this.commandOwner.shouldUseRtsCraftTerminalScreen(craftingScreen); }
    public void quickDropSelectedItem(String itemId, int amount, Vec3d dropPos) { this.commandOwner.quickDropSelectedItem(itemId, amount, dropPos); }
    public void applyStoragePage(S2CRtsStoragePagePayload payload) { this.commandOwner.applyStoragePage(payload); }
    public void applyCraftables(S2CRtsCraftablesPayload payload) { this.commandOwner.applyCraftables(payload); }
    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) { this.commandOwner.applyCraftFeedback(payload); }
    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) { this.commandOwner.applyStorageDirty(payload); }
    void refreshSelectedItemPreviewFromStorage() { this.commandOwner.refreshSelectedItemPreviewFromStorage(); }
    public void applyRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload) { this.commandOwner.applyRemoteMenuHint(payload); }
    public void applyRemoteMenuResult(S2CRtsRemoteMenuResultPayload payload) { this.commandOwner.applyRemoteMenuResult(payload); }
    public void applyDamageFeedback(S2CRtsDamageFeedbackPayload payload) { this.commandOwner.applyDamageFeedback(payload); }
    public void applyQuestDetectStatus(S2CRtsQuestDetectStatusPayload payload) { this.commandOwner.applyQuestDetectStatus(payload); }
    public void applyMineProgress(S2CRtsMineProgressPayload payload) { this.commandOwner.applyMineProgress(payload); }
    public void applyProgressionState(S2CRtsProgressionStatePayload payload) { this.commandOwner.applyProgressionState(payload); }
    public void applyPluginState(S2CRtsPluginStatePayload payload) { this.commandOwner.applyPluginState(payload); }
    public void requestPluginState() { this.commandOwner.requestPluginState(); }
    public void installPluginFromInventorySlot(int inventorySlot) { this.commandOwner.installPluginFromInventorySlot(inventorySlot); }
    public void uninstallPlugin(String pluginId) { this.commandOwner.uninstallPlugin(pluginId); }
    public void requestProgressionState() { this.commandOwner.requestProgressionState(); }
    public void setSurvivalProgressionEnabled(boolean enabled) { this.commandOwner.setSurvivalProgressionEnabled(enabled); }
    public void setHome(BlockPos pos) { this.commandOwner.setHome(pos); }
    public void beginHomeSelection() { this.commandOwner.beginHomeSelection(); }
    public void selectStorageEntry(int index) { this.interactionOwner.selectStorageEntry(index); }
    public void selectFluidEntry(int index) { this.interactionOwner.selectFluidEntry(index); }
    public void clearSelectedItem() { this.interactionOwner.clearSelectedItem(); }
    public void clearPlacementSelectionPreserveMode() { this.interactionOwner.clearPlacementSelectionPreserveMode(); }
    public void selectEmptyHand() { this.interactionOwner.selectEmptyHand(); }
    public void selectRecentEntry(int index) { this.interactionOwner.selectRecentEntry(index); }
    public void assignQuickSlotFromSelected(int index) { this.interactionOwner.assignQuickSlotFromSelected(index); }
    public void assignQuickSlotFromToolItem(int index, ItemStack stack) { this.interactionOwner.assignQuickSlotFromToolItem(index, stack); }
    public void clearQuickSlot(int index) { this.interactionOwner.clearQuickSlot(index); }
    public void selectQuickSlot(int index) { this.interactionOwner.selectQuickSlot(index); }
    public void selectItemForPlacement(String itemId, String label, ItemStack preview) { this.interactionOwner.selectItemForPlacement(itemId, label, preview); }
    public void setGuiBinding(int index, BlockPos pos, EnumFacing face, String itemIdHint) { this.interactionOwner.setGuiBinding(index, pos, face, itemIdHint); }
    public void clearGuiBinding(int index) { this.interactionOwner.clearGuiBinding(index); }
    public void openGuiBinding(int index) { this.interactionOwner.openGuiBinding(index); }
    public void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.placeSelected(hit, forcePlace, rayOrigin, rayDir); }
    public void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied) { this.interactionOwner.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied); }
    public void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied, boolean quickBuild) { this.interactionOwner.placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, quickBuild); }
    public void placeSelectedBatch(List<RayTraceResult> hits, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied) { this.interactionOwner.placeSelectedBatch(hits, forcePlace, rayOrigin, rayDir, skipIfOccupied); }
    public void placeSelectedBatch(List<RayTraceResult> hits, RayTraceResult templateHit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied) { this.interactionOwner.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied); }
    public void placeSelectedBatch(List<RayTraceResult> hits, RayTraceResult templateHit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir, boolean skipIfOccupied, boolean overwriteExisting) { this.interactionOwner.placeSelectedBatch(hits, templateHit, forcePlace, rayOrigin, rayDir, skipIfOccupied, overwriteExisting); }
    public void placeSelectedFluid(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir); }
    public void storeFluidFromStorageItem(String itemId) { this.interactionOwner.storeFluidFromStorageItem(itemId); }
    public void storeFluidFromPinnedItem(String itemId) { this.interactionOwner.storeFluidFromPinnedItem(itemId); }
    public void storeFluidFromToolSlot(int toolSlot) { this.interactionOwner.storeFluidFromToolSlot(toolSlot); }
    public void interactEmpty(RayTraceResult hit, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactEmpty(hit, rayOrigin, rayDir); }
    public void interactEntityEmpty(int entityId, Vec3d hitLocation, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactEntityEmpty(entityId, hitLocation, rayOrigin, rayDir); }
    public void interactBlockWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir); }
    public void useItemInAirWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.useItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir); }
    public void interactBlockWithPinnedItem(RayTraceResult hit, String itemId, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir); }
    public void interactEntityWithToolSlot(int entityId, Vec3d hitLocation, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir); }
    public void interactEntityWithPinnedItem(int entityId, Vec3d hitLocation, String itemId, Vec3d rayOrigin, Vec3d rayDir) { this.interactionOwner.interactEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir); }
    public void breakPlaced(BlockPos pos) { this.interactionOwner.breakPlaced(pos); }
    public void breakPlaced(BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) { this.interactionOwner.breakPlaced(pos, face, allowAdjacentFallback); }
    public void startMining(BlockPos pos, int face, int toolSlot) { this.interactionOwner.startMining(pos, face, toolSlot); }
    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode) { this.interactionOwner.startUltimine(pos, face, toolSlot, limit, mode); }
    public void continueMining(int toolSlot) { this.interactionOwner.continueMining(toolSlot); }
    public int getAreaMinePhase() { return this.interactionOwner.getAreaMinePhase(); }
    public BlockPos getAreaMinePointA() { return this.interactionOwner.getAreaMinePointA(); }
    public BlockPos getAreaMinePointB() { return this.interactionOwner.getAreaMinePointB(); }
    public int getAreaMineHeightOffset() { return this.interactionOwner.getAreaMineHeightOffset(); }
public static AreaMineBounds computeAreaMineBounds(BlockPos pointA, BlockPos pointB, int heightOffset) {
        return MiningOperationService.computeAreaMineBounds(pointA, pointB, heightOffset);
    }
    public void setAreaMineHeightOffset(int offset) { this.interactionOwner.setAreaMineHeightOffset(offset); }
    public void adjustAreaMineHeightOffset(int delta) { this.interactionOwner.adjustAreaMineHeightOffset(delta); }
    public void setAreaMinePointA(BlockPos pos) { this.interactionOwner.setAreaMinePointA(pos); }
    public void setAreaMinePointB(BlockPos pos) { this.interactionOwner.setAreaMinePointB(pos); }
    public void clearAreaMineSession() { this.interactionOwner.clearAreaMineSession(); }
    public void confirmAreaMine(int toolSlot, ShapeFillMode fillMode) { this.interactionOwner.confirmAreaMine(toolSlot, fillMode); }
    public void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot) { this.interactionOwner.confirmShapeAreaDestroy(targets, toolSlot); }
    public void abortMining(int toolSlot) { this.interactionOwner.abortMining(toolSlot); }
    public int getMineProgressStage() { return this.interactionOwner.getMineProgressStage(); }
    public BlockPos getMineProgressPos() { return this.interactionOwner.getMineProgressPos(); }
    public BlockPos getMineProgressCompletedPos() { return this.interactionOwner.getMineProgressCompletedPos(); }
    public long getMineProgressCompletedAtMs() { return this.interactionOwner.getMineProgressCompletedAtMs(); }
    public int getUltimineProgressProcessed() { return this.interactionOwner.getUltimineProgressProcessed(); }
    public int getUltimineProgressTotal() { return this.interactionOwner.getUltimineProgressTotal(); }
    public void applyUltimineProgress(S2CRtsUltimineProgressPayload payload) { this.interactionOwner.applyUltimineProgress(payload); }
    void beginRemoteMenuOpenGrace() { this.commandOwner.beginRemoteMenuOpenGrace(); }
    void handleRemoteMenuOpenFailure(Minecraft minecraft, Throwable throwable) { this.commandOwner.handleRemoteMenuOpenFailure(minecraft, throwable); }
    void clearRemoteMenuValidationState() { this.commandOwner.clearRemoteMenuValidationState(); }
    boolean isLocalPlayerCreative() { return this.commandOwner.isLocalPlayerCreative(); }
    public void rotatePlacementClockwise() { this.interactionOwner.rotatePlacementClockwise(); }
    public void rotatePlacementCounterClockwise() { this.interactionOwner.rotatePlacementCounterClockwise(); }
    public void setPlacementStateProperty(String propertyName, String valueName) { this.interactionOwner.setPlacementStateProperty(propertyName, valueName); }
    public void copyPlacementState(IBlockState state) { this.interactionOwner.copyPlacementState(state); }
    public void syncVisualCameraFrame() { this.interactionOwner.syncVisualCameraFrame(); }
}
