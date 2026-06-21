package com.rtsbuilding.rtsbuilding.client.controller;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.client.screen.RtsHomeScreen;
import com.rtsbuilding.rtsbuilding.client.screen.RtsProgressionScreen;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientLayoutStore;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.storage.RecentEntry;
import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage.RtsSophisticatedStorageCompat;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class ClientRtsController {
    private static final ClientRtsController INSTANCE = new ClientRtsController();
    private static final int DEFAULT_STORAGE_PAGE_SIZE = 90;
    private static final int MAX_STORAGE_PAGE_SIZE = 180;
    private static final long STORAGE_AUTO_REFRESH_INTERVAL_MS = 30_000L;

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final float ROT_SENS_MIN = 1.00F;
    private static final float ROT_SENS_MAX = 10.00F;
    private static final float ROT_SENS_STEP = 0.50F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final float[] INPUT_SENS_PRESETS = new float[] { 0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F };
    private static final int INPUT_SENS_DEFAULT_INDEX = 2;
    private static final int QUICK_SLOT_COUNT = 27;
    private static final int GUI_BINDING_SLOT_COUNT = 8;
    private static final int CRAFTABLE_BATCH_SIZE = 12;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private static final float ROT_EMA_ALPHA = 0.28F;
    private static final float ROT_EMA_DECAY = 0.78F;
    private static final int RTS_MINE_RENDER_ID = 0x525453;
    private static final int CLIENT_MAX_WORKFLOWS = 8;
    private static final int REMOTE_MENU_OPEN_GRACE_TICKS = 80;
    private static final int SCREENLESS_REMOTE_MENU_RECOVERY_TICKS = 10;
    private static final long QUEST_DETECT_MIN_PROGRESS_MS = 700L;
    private static final long QUEST_DETECT_RESULT_VISIBLE_MS = 3500L;
    private static final long STORAGE_SCAN_RESULT_VISIBLE_MS = 450L;
    private static final double MIN_CAMERA_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_CAMERA_HEIGHT_OFFSET = 110.0D;
    private static final double MAX_HORIZONTAL_CAMERA_DISTANCE = 72.0D;
    private static final float MIN_CAMERA_PITCH = -90.0F;
    private static final float MAX_CAMERA_PITCH = 90.0F;
    private static final float CAMERA_INPUT_EPSILON = 1.0e-4F;
    private static final int CAMERA_IDLE_HEARTBEAT_TICKS = 20;
    private static final int CAMERA_RESTORE_COOLDOWN_TICKS = 10;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final float MAX_SMOOTH_FRAME_TICKS = 2.00F;

    private boolean enabled;
    private int serverCameraEntityId = -1;

    private Entity previousCameraEntity;
    private CameraType previousCameraType = CameraType.FIRST_PERSON;
    private boolean previousBobView = true;
    private double previousFovEffectScale = 1.0D;

    private boolean rotateCaptured;
    private double restoreCursorX;
    private double restoreCursorY;

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
    private boolean playerStatusOverlayEnabled = true;
    private boolean invertPanDragX;
    private boolean invertPanDragY;
    private boolean smoothCamera;
    private boolean damageSoundEnabled = true;
    private boolean damageAutoReturnEnabled = true;

    private boolean localStateReady;
    private double localX;
    private double localY;
    private double localZ;
    private double localHeightOffset;
    private float localYawDeg;
    private float localPitchDeg;

    private float pendingPanX;
    private float pendingPanY;
    private float pendingScroll;
    private int pendingRotateSteps;

    private float pendingRawRotateX;
    private float pendingRawRotateY;
    private float emaRotateX;
    private float emaRotateY;
    private int cameraMoveHeartbeatTicks;
    private int cameraRestoreCooldownTicks;
    private long lastSmoothCameraFrameNanos;

    private float rotateSensitivity = 5.00F;
    private int inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;

    private BuilderMode mode = BuilderMode.INTERACT;
    private boolean storageCollapsed;
    private boolean storageLinked;
    private boolean bdNetworkEnabled = true;
    private String linkedStorageName = "No Storage";
    private final List<BlockPos> linkedStoragePositions = new ArrayList<>();
    private int storagePage;
    private int storagePageSize = DEFAULT_STORAGE_PAGE_SIZE;
    private int storageTotalPages = 1;
    private int storageTotalEntries;
    private int storageRevision;
    private String storageSearch = "";
    private String storageCategory = "all";
    private RtsStorageSort storageSort = RtsStorageSort.QUANTITY;
    private boolean storageSortAscending;
    private final List<String> storageCategories = new ArrayList<>();
    private final List<StorageEntry> storageEntries = new ArrayList<>();
    private final Map<String, Long> storageTotalCounts = new HashMap<>();
    private final List<FluidEntry> fluidEntries = new ArrayList<>();
    private final List<RecentEntry> recentEntries = new ArrayList<>();
    private boolean storageScanRunning;
    private long storageScanStartedAtMs;
    private long storageScanVisibleUntilMs;
    private long storagePageReceivedAtMs;
    private boolean storageViewDirty;
    private long storageViewDirtySinceMs;
    private String craftablesSearch = "";
    private boolean craftablesShowUnavailable;
    private final List<CraftableEntry> craftableEntries = new ArrayList<>();
    private int craftablesRevision;
    private boolean craftablesHasMore;
    private final Set<Integer> pendingCraftableOffsets = new HashSet<>();
    private String craftFeedbackItemId = "";
    private int craftFeedbackCount;
    private long craftFeedbackExpiryMs;
    private final List<CraftFeedbackIngredient> craftFeedbackIngredients = new ArrayList<>();
    private byte questDetectPhase = -1;
    private long questDetectStartedAtMs;
    private long questDetectFinishedAtMs;
    private long questDetectExpiryMs;
    private int questDetectScannedTasks;
    private int questDetectTotalTasks;
    private int questDetectCompletedTasks;
    private String selectedItemId = "";
    private String selectedItemLabel = "";
    private ItemStack selectedItemPreview = ItemStack.EMPTY;
    private String selectedFluidId = "";
    private String selectedFluidLabel = "";
    private ItemStack selectedFluidPreview = ItemStack.EMPTY;
    private boolean emptyHandSelected = false;
    private int placeRotateSteps;
    private BlockPos activeMinePos;
    private int activeMineFace = -1;
    private int activeMineToolSlot;
    private BlockPos mineRenderPos;
    private int mineRenderStage = -1;
    private int ultimineProgressProcessed = -1;
    private int ultimineProgressTotal = 0;
    private final RtsWorkflowStatus[] workflowStatuses = new RtsWorkflowStatus[CLIENT_MAX_WORKFLOWS];
    private int workflowActiveCount;
    private boolean hasPendingJobs;
    private BuildShape buildShape = BuildShape.BLOCK;
    private boolean pendingCraftTerminalOpen;
    private int pendingCraftTerminalOpenTicks;
    private int pendingRemoteMenuOpenTicks;
    private int screenlessRemoteMenuTicks;
    private AbstractContainerMenu relaxedRemoteMenu;
    private boolean autoStoreMinedDrops = true;
    private final String[] quickSlotItemIds = new String[QUICK_SLOT_COUNT];
    private final String[] quickSlotLabels = new String[QUICK_SLOT_COUNT];
    private final ItemStack[] quickSlotPreviews = new ItemStack[QUICK_SLOT_COUNT];
    private final String[] guiBindingLabels = new String[GUI_BINDING_SLOT_COUNT];
    private final String[] guiBindingItemIds = new String[GUI_BINDING_SLOT_COUNT];
    private final ItemStack[] guiBindingPreviews = new ItemStack[GUI_BINDING_SLOT_COUNT];
    private boolean funnelEnabled;
    private BlockPos lastFunnelTarget;
    private int funnelTargetCooldownTicks;
    private final List<FunnelBufferEntry> funnelBufferEntries = new ArrayList<>();
    private boolean progressionEnabled;
    private boolean progressionHomeSet;
    private BlockPos progressionHomePos = BlockPos.ZERO;
    private String progressionHomeDimension = "";
    private long progressionHomeCooldownTicks;
    private int progressionRadiusBlocks = 48;
    private int progressionFluidCapacityBuckets = 100;
    private int progressionUltimineLimit = 256;
    private boolean progressionBypassHomeRadius;
    private final Set<String> unlockedProgressionNodes = new HashSet<>();
    private final Set<String> unlockableProgressionNodes = new HashSet<>();
    private double storagePanelXNormalized;
    private double storagePanelYNormalized;
    private double storagePanelWidthNormalized;
    private double storagePanelHeightNormalized;
    private boolean chunkCurtainVisible;

    // Local render-only camera entity to isolate rendering from network interpolation.
    private RtsCameraEntity localMirrorCamera;

    private ClientRtsController() {
        RtsClientUiStateStore.UiState uiState = RtsClientUiStateStore.load();
        this.startCameraAtPlayerHead = uiState.startCameraAtPlayerHead;
        this.allowPlacedBlockRecovery = uiState.allowPlacedBlockRecovery;
        this.toolProtectionEnabled = uiState.toolProtectionEnabled;
        this.playerStatusOverlayEnabled = uiState.playerStatusOverlayEnabled;
        this.invertPanDragX = uiState.invertPanDragX;
        this.invertPanDragY = uiState.invertPanDragY;
        this.smoothCamera = uiState.smoothCamera;
        this.damageSoundEnabled = uiState.damageSoundEnabled;
        this.damageAutoReturnEnabled = uiState.damageAutoReturnEnabled;
        applyStoredLayout(RtsClientLayoutStore.loadStoragePanelLayout());
        this.storageCategories.add("all");
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            this.quickSlotItemIds[i] = "";
            this.quickSlotLabels[i] = "";
            this.quickSlotPreviews[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < GUI_BINDING_SLOT_COUNT; i++) {
            this.guiBindingLabels[i] = "";
            this.guiBindingItemIds[i] = "";
            this.guiBindingPreviews[i] = ItemStack.EMPTY;
        }
    }

    public static ClientRtsController get() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canUseStorageOverlay() {
        return this.enabled
                || this.storageLinked
                || !this.linkedStoragePositions.isEmpty()
                || !this.storageEntries.isEmpty()
                || !this.fluidEntries.isEmpty();
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
        return this.progressionEnabled;
    }

    public boolean isProgressionHomeSet() {
        return this.progressionHomeSet;
    }

    public BlockPos getProgressionHomePos() {
        return this.progressionHomePos;
    }

    public String getProgressionHomeDimension() {
        return this.progressionHomeDimension;
    }

    public long getProgressionHomeCooldownTicks() {
        return this.progressionHomeCooldownTicks;
    }

    public int getProgressionRadiusBlocks() {
        return this.progressionRadiusBlocks;
    }

    public int getProgressionFluidCapacityBuckets() {
        return this.progressionFluidCapacityBuckets;
    }

    public int getProgressionUltimineLimit() {
        return this.progressionUltimineLimit;
    }

    public boolean isProgressionBypassHomeRadius() {
        return this.progressionBypassHomeRadius;
    }

    public Set<String> getUnlockedProgressionNodes() {
        return Collections.unmodifiableSet(this.unlockedProgressionNodes);
    }

    public Set<String> getUnlockableProgressionNodes() {
        return Collections.unmodifiableSet(this.unlockableProgressionNodes);
    }

    public BuilderMode getMode() {
        return this.mode;
    }

    public void setMode(BuilderMode mode) {
        this.mode = mode;
        RtsClientPacketGateway.sendSetMode(mode);
    }

    public boolean isFunnelEnabled() {
        return this.funnelEnabled;
    }

    public void setFunnelEnabled(boolean enabled) {
        if (this.funnelEnabled == enabled) {
            return;
        }
        this.funnelEnabled = enabled;
        RtsClientPacketGateway.sendSetFunnelEnabled(enabled);
        if (!enabled) {
            this.lastFunnelTarget = null;
            this.funnelTargetCooldownTicks = 0;
        }
    }

    public void toggleFunnelEnabled() {
        setFunnelEnabled(!this.funnelEnabled);
    }

    public boolean isStorageCollapsed() {
        return this.storageCollapsed;
    }

    public void toggleStorageCollapsed() {
        this.storageCollapsed = !this.storageCollapsed;
    }

    public double getStoragePanelXNormalized() {
        return this.storagePanelXNormalized;
    }

    public double getStoragePanelYNormalized() {
        return this.storagePanelYNormalized;
    }

    public double getStoragePanelWidthNormalized() {
        return this.storagePanelWidthNormalized;
    }

    public double getStoragePanelHeightNormalized() {
        return this.storagePanelHeightNormalized;
    }

    public void updateStoragePanelLayout(double xNormalized, double yNormalized, double widthNormalized, double heightNormalized) {
        this.storagePanelXNormalized = clampLayoutNormalized(xNormalized);
        this.storagePanelYNormalized = clampLayoutNormalized(yNormalized);
        this.storagePanelWidthNormalized = clampLayoutNormalized(widthNormalized);
        this.storagePanelHeightNormalized = clampLayoutNormalized(heightNormalized);
        RtsClientLayoutStore.saveStoragePanelLayout(new RtsClientLayoutStore.StoragePanelLayout(
                this.storagePanelXNormalized,
                this.storagePanelYNormalized,
                this.storagePanelWidthNormalized,
                this.storagePanelHeightNormalized));
    }

    public boolean isStorageLinked() {
        return this.storageLinked;
    }

    public String getLinkedStorageName() {
        return this.linkedStorageName;
    }

    public List<BlockPos> getLinkedStoragePositions() {
        return Collections.unmodifiableList(this.linkedStoragePositions);
    }

    public int getStoragePage() {
        return this.storagePage;
    }

    public int getStorageTotalPages() {
        return this.storageTotalPages;
    }

    public int getStorageTotalEntries() {
        return this.storageTotalEntries;
    }

    public int getStorageRevision() {
        return this.storageRevision;
    }

    public String getStorageSearch() {
        return this.storageSearch;
    }

    public RtsStorageSort getStorageSort() {
        return this.storageSort;
    }

    public boolean isStorageSortAscending() {
        return this.storageSortAscending;
    }

    public String getStorageCategory() {
        return this.storageCategory;
    }

    public List<String> getStorageCategories() {
        return Collections.unmodifiableList(this.storageCategories);
    }

    public String getSelectedItemId() {
        return this.selectedItemId;
    }

    public String getSelectedItemLabel() {
        return this.selectedItemLabel;
    }

    public String getSelectedFluidId() {
        return this.selectedFluidId;
    }

    public String getSelectedFluidLabel() {
        return this.selectedFluidLabel;
    }

    public boolean hasSelectedItem() {
        return !this.selectedItemId.isBlank();
    }

    public boolean hasSelectedFluid() {
        return !this.selectedFluidId.isBlank();
    }

    public boolean isEmptyHandSelected() {
        return this.emptyHandSelected;
    }

    public ItemStack getSelectedItemPreview() {
        return this.selectedItemPreview;
    }

    public ItemStack getSelectedFluidPreview() {
        return this.selectedFluidPreview;
    }

    public int getPlaceRotateDegrees() {
        return this.placeRotateSteps * 90;
    }

    public List<StorageEntry> getStorageEntries() {
        return Collections.unmodifiableList(this.storageEntries);
    }

    public long getStorageTotalCount(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return 0L;
        }
        return Math.max(0L, this.storageTotalCounts.getOrDefault(itemId, 0L));
    }

    public List<FluidEntry> getFluidEntries() {
        return Collections.unmodifiableList(this.fluidEntries);
    }

    public List<RecentEntry> getRecentEntries() {
        return Collections.unmodifiableList(this.recentEntries);
    }

    public long getRecentDisplayAmount(RecentEntry entry) {
        if (entry == null) {
            return 0L;
        }
        if (entry.fluid()) {
            return getStorageFluidAmount(entry.id());
        }
        return getStorageTotalCount(entry.id());
    }

    public String getCraftablesSearch() {
        return this.craftablesSearch;
    }

    public boolean isCraftablesShowUnavailable() {
        return this.craftablesShowUnavailable;
    }

    public List<CraftableEntry> getCraftableEntries() {
        return Collections.unmodifiableList(this.craftableEntries);
    }

    public int getCraftablesRevision() {
        return this.craftablesRevision;
    }

    public boolean hasMoreCraftables() {
        return this.craftablesHasMore;
    }

    public String getCraftFeedbackItemId() {
        return this.craftFeedbackItemId;
    }

    public int getCraftFeedbackCount() {
        return this.craftFeedbackCount;
    }

    public long getCraftFeedbackExpiryMs() {
        return this.craftFeedbackExpiryMs;
    }

    public List<CraftFeedbackIngredient> getCraftFeedbackIngredients() {
        return Collections.unmodifiableList(this.craftFeedbackIngredients);
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
        return false;
    }

    public boolean isStorageScanRunning() {
        return this.storageScanRunning;
    }

    public boolean isStorageViewDirty() {
        return this.storageViewDirty;
    }

    public boolean shouldHighlightStorageRefresh() {
        return this.storageViewDirty && !RtsClientUiStateStore.isStorageRefreshQuietEnabled();
    }

    public float getStorageScanProgress() {
        if (!isStorageScanPopupVisible()) {
            return 0.0F;
        }
        if (this.storageScanRunning) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - this.storageScanStartedAtMs);
            return (float) Math.min(0.92D, elapsed / 900.0D * 0.92D);
        }
        return 1.0F;
    }

    public boolean hasStoragePageSnapshot() {
        return this.storagePageReceivedAtMs > 0L || this.storageRevision > 0;
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
        return Collections.unmodifiableList(this.funnelBufferEntries);
    }

    public boolean isAutoStoreMinedDrops() {
        return this.autoStoreMinedDrops;
    }

    public boolean isBdNetworkEnabled() {
        return this.bdNetworkEnabled;
    }

    public void setBdNetworkEnabled(boolean enabled) {
        this.bdNetworkEnabled = enabled;
    }

    public void toggleBdNetworkEnabled() {
        setBdNetworkEnabled(!this.bdNetworkEnabled);
    }

    public BuildShape getBuildShape() {
        return this.buildShape;
    }

    public void setBuildShape(BuildShape shape) {
        this.buildShape = shape == null ? BuildShape.BLOCK : shape;
    }

    public boolean isChunkCurtainVisible() {
        return this.chunkCurtainVisible;
    }

    /**
     * 濂楃敤鏈嶅姟绔彂鏉ョ殑鍗曚釜宸ヤ綔娴佽繘搴︺€俿lot 鍙礋璐?UI 鎺掑垪锛屾寜閽搷浣滀粛浣跨敤 entryId銆?     */
    public void applyWorkflowProgress(S2CRtsWorkflowProgressPayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.isIdle()) {
            clearWorkflowData();
            return;
        }
        this.workflowActiveCount = payload.workflowCount() & 0xFF;
        int index = payload.workflowIndex() & 0xFF;
        if (index < 0 || index >= CLIENT_MAX_WORKFLOWS) {
            return;
        }

        RtsWorkflowType[] types = RtsWorkflowType.values();
        byte workflowType = payload.workflowType();
        RtsWorkflowType type = workflowType >= 0 && workflowType < types.length ? types[workflowType] : null;
        if (type == null) {
            this.workflowStatuses[index] = RtsWorkflowStatus.idle();
            return;
        }

        this.workflowStatuses[index] = RtsWorkflowStatus.fromRaw(
                type,
                RtsWorkflowPriority.byRank(payload.priority()),
                payload.totalBlocks(),
                payload.completedBlocks(),
                payload.failedBlocks(),
                payload.missingItems(),
                payload.detailMessage(),
                payload.suspended() != 0,
                payload.paused() != 0,
                payload.workflowEntryId());
    }

    /**
     * 濂楃敤鏈嶅姟绔壒閲忓悓姝ャ€傚厛娓呯┖鍐嶅～鍏咃紝閬垮厤宸插垹闄や换鍔″湪瀹㈡埛绔棯鍥炪€?     */
    public void applyWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload) {
        for (int i = 0; i < CLIENT_MAX_WORKFLOWS; i++) {
            this.workflowStatuses[i] = null;
        }
        this.workflowActiveCount = 0;
        if (payload == null || payload.entries() == null) {
            return;
        }
        for (S2CRtsWorkflowProgressPayload entry : payload.entries()) {
            applyWorkflowProgress(entry);
        }
    }

    public RtsWorkflowStatus getWorkflowStatus(int slot) {
        if (slot < 0 || slot >= CLIENT_MAX_WORKFLOWS || this.workflowStatuses[slot] == null) {
            return RtsWorkflowStatus.idle();
        }
        return this.workflowStatuses[slot];
    }

    public List<RtsWorkflowStatus> getActiveWorkflows() {
        List<RtsWorkflowStatus> result = new ArrayList<>();
        int count = Math.min(this.workflowActiveCount, CLIENT_MAX_WORKFLOWS);
        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = this.workflowStatuses[i];
            if (status != null && status.isActive()) {
                result.add(status);
            }
        }
        return result;
    }

    public void clearWorkflowData() {
        for (int i = 0; i < CLIENT_MAX_WORKFLOWS; i++) {
            this.workflowStatuses[i] = null;
        }
        this.workflowActiveCount = 0;
        this.hasPendingJobs = false;
    }

    public int getWorkflowActiveCount() {
        return this.workflowActiveCount;
    }

    public RtsWorkflowStatus[] getWorkflowStatuses() {
        return this.workflowStatuses;
    }

    public RtsWorkflowStatus findActiveDestroyWorkflow() {
        for (RtsWorkflowStatus status : this.workflowStatuses) {
            if (status == null || status.type() == null) {
                continue;
            }
            switch (status.type()) {
                case AREA_DESTROY, ULTIMINE, AREA_MINE -> {
                    return status;
                }
                default -> {
                }
            }
        }
        return null;
    }

    public boolean hasPendingJobs() {
        return this.hasPendingJobs;
    }

    public void setHasPendingJobs(boolean hasPendingJobs) {
        this.hasPendingJobs = hasPendingJobs;
    }

    public boolean hasActiveWorkflow() {
        for (RtsWorkflowStatus status : this.workflowStatuses) {
            if (status != null && status.isActive()) {
                return true;
            }
        }
        return false;
    }

    public void setChunkCurtainVisible(boolean visible) {
        this.chunkCurtainVisible = visible;
    }

    public void cycleBuildShape(int step) {
        BuildShape[] values = BuildShape.values();
        int index = this.buildShape.ordinal();
        int next = Math.floorMod(index + step, values.length);
        this.buildShape = values[next];
    }

    public int getQuickSlotCount() {
        return QUICK_SLOT_COUNT;
    }

    public String getQuickSlotItemId(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return "";
        }
        return this.quickSlotItemIds[index];
    }

    public String getQuickSlotLabel(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return "";
        }
        return this.quickSlotLabels[index];
    }

    public ItemStack getQuickSlotPreview(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return this.quickSlotPreviews[index];
    }

    public float getRotateSensitivity() {
        return this.rotateSensitivity;
    }

    public int getGuiBindingCount() {
        return GUI_BINDING_SLOT_COUNT;
    }

    public String getGuiBindingLabel(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return "";
        }
        return this.guiBindingLabels[index];
    }

    public ItemStack getGuiBindingPreview(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return this.guiBindingPreviews[index];
    }

    public boolean hasGuiBinding(int index) {
        return !getGuiBindingLabel(index).isBlank();
    }

    public String getInputSensitivityLabel() {
        return String.format(Locale.ROOT, "x%.2f", getInputSensitivityScale());
    }

    public int getInputSensitivityIndex() {
        if (this.inputSensitivityIndex < 0 || this.inputSensitivityIndex >= INPUT_SENS_PRESETS.length) {
            this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        }
        return this.inputSensitivityIndex;
    }

    public int getInputSensitivityPresetCount() {
        return INPUT_SENS_PRESETS.length;
    }

    public void setInputSensitivityByFraction(double fraction) {
        double clamped = Mth.clamp(fraction, 0.0D, 1.0D);
        int next = (int) Math.round(clamped * (INPUT_SENS_PRESETS.length - 1));
        this.inputSensitivityIndex = Mth.clamp(next, 0, INPUT_SENS_PRESETS.length - 1);
    }

    public void cycleInputSensitivity() {
        this.inputSensitivityIndex = (this.inputSensitivityIndex + 1) % INPUT_SENS_PRESETS.length;
    }

    public void increaseRotateSensitivity() {
            this.rotateSensitivity = Mth.clamp(this.rotateSensitivity + ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public void decreaseRotateSensitivity() {
            this.rotateSensitivity = Mth.clamp(this.rotateSensitivity - ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public void beginRotateCapture(double cursorX, double cursorY) {
        if (this.rotateCaptured) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        this.rotateCaptured = true;
        this.restoreCursorX = cursorX;
        this.restoreCursorY = cursorY;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    public void endRotateCapture(double fallbackX, double fallbackY) {
        if (!this.rotateCaptured) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        this.rotateCaptured = false;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        double x = this.restoreCursorX == 0.0D ? fallbackX : this.restoreCursorX;
        double y = this.restoreCursorY == 0.0D ? fallbackY : this.restoreCursorY;
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x, y);
    }

    public boolean isRotateCaptured() {
        return this.rotateCaptured;
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

    public boolean isPlayerStatusOverlayEnabled() {
        return this.playerStatusOverlayEnabled;
    }

    public void setPlayerStatusOverlayEnabled(boolean playerStatusOverlayEnabled) {
        this.playerStatusOverlayEnabled = playerStatusOverlayEnabled;
    }

    public void togglePlayerStatusOverlayEnabled() {
        this.playerStatusOverlayEnabled = !this.playerStatusOverlayEnabled;
    }

    public boolean isInvertPanDragX() {
        return this.invertPanDragX;
    }

    public void setInvertPanDragX(boolean invertPanDragX) {
        this.invertPanDragX = invertPanDragX;
    }

    public void toggleInvertPanDragX() {
        this.invertPanDragX = !this.invertPanDragX;
    }

    public boolean isInvertPanDragY() {
        return this.invertPanDragY;
    }

    public void setInvertPanDragY(boolean invertPanDragY) {
        this.invertPanDragY = invertPanDragY;
    }

    public void toggleInvertPanDragY() {
        this.invertPanDragY = !this.invertPanDragY;
    }

    public boolean isSmoothCamera() {
        return this.smoothCamera;
    }

    public void setSmoothCamera(boolean smoothCamera) {
        if (this.smoothCamera != smoothCamera) {
            this.lastSmoothCameraFrameNanos = 0L;
        }
        this.smoothCamera = smoothCamera;
    }

    public void toggleSmoothCamera() {
        setSmoothCamera(!this.smoothCamera);
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
            this.serverCameraEntityId = payload.cameraEntityId();
            this.anchorX = payload.anchorX();
            this.anchorY = payload.anchorY();
            this.anchorZ = payload.anchorZ();
            this.maxRadius = payload.maxRadius();
            this.homeSelectionMode = payload.homeSelection();
            this.closeRangeAllowed = payload.closeRangeAllowed();

            if (freshEnable) {
                this.previousCameraEntity = minecraft.getCameraEntity();
                this.previousCameraType = minecraft.options.getCameraType();
                this.previousBobView = minecraft.options.bobView().get();
                this.previousFovEffectScale = minecraft.options.fovEffectScale().get();
            }

            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            minecraft.options.bobView().set(false);
            minecraft.options.fovEffectScale().set(0.0D);

            if (!(minecraft.screen instanceof BuilderScreen)) {
                minecraft.setScreen(new BuilderScreen(this));
            }

            this.localHeightOffset = payload.heightOffset();
            this.localYawDeg = payload.yawDeg();
            this.localPitchDeg = payload.pitchDeg();
            this.localX = payload.anchorX();
            this.localY = payload.anchorY() + payload.heightOffset();
            this.localZ = payload.anchorZ();
            this.localStateReady = true;

            this.pendingPanX = 0.0F;
            this.pendingPanY = 0.0F;
            this.pendingScroll = 0.0F;
            this.pendingRotateSteps = 0;
            this.pendingRawRotateX = 0.0F;
            this.pendingRawRotateY = 0.0F;
            this.emaRotateX = 0.0F;
            this.emaRotateY = 0.0F;
            this.cameraMoveHeartbeatTicks = 0;
            this.cameraRestoreCooldownTicks = 0;
            this.lastSmoothCameraFrameNanos = 0L;
            this.mode = BuilderMode.INTERACT;
            this.storageCollapsed = false;
            this.storageEntries.clear();
            this.fluidEntries.clear();
            this.storageLinked = false;
            this.linkedStorageName = "No Storage";
            this.linkedStoragePositions.clear();
            this.storagePage = 0;
            this.storageTotalPages = 1;
            this.storageTotalEntries = 0;
            this.storageSearch = "";
            this.storageCategory = "all";
            this.storageSort = RtsStorageSort.QUANTITY;
            this.storageSortAscending = false;
            this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
            this.storageCategories.clear();
            this.storageCategories.add("all");
            clearStorageScanState();
            clearStorageViewDirty();
            this.storagePageReceivedAtMs = 0L;
            this.selectedItemId = "";
            this.selectedItemLabel = "";
            this.selectedItemPreview = ItemStack.EMPTY;
            this.selectedFluidId = "";
            this.selectedFluidLabel = "";
            this.selectedFluidPreview = ItemStack.EMPTY;
            this.emptyHandSelected = false;
            this.placeRotateSteps = 0;
            this.activeMinePos = null;
            this.activeMineFace = -1;
            this.mineRenderPos = null;
            this.mineRenderStage = -1;
            this.buildShape = BuildShape.BLOCK;
            this.funnelEnabled = false;
            this.lastFunnelTarget = null;
            this.funnelTargetCooldownTicks = 0;
            this.funnelBufferEntries.clear();
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;
            this.pendingRemoteMenuOpenTicks = 0;
            this.screenlessRemoteMenuTicks = 0;
            clearRemoteMenuValidationState();
            clearQuickSlotsLocal();
            clearGuiBindingsLocal();

            this.ensureLocalMirrorCamera(minecraft);
            this.syncVisualCameraFrame();
            requestStoragePage(0);
            return;
        }

        this.enabled = false;
        this.serverCameraEntityId = -1;
        this.localStateReady = false;
        this.homeSelectionMode = false;
        this.closeRangeAllowed = false;
        this.lastSmoothCameraFrameNanos = 0L;
        this.funnelEnabled = false;
        this.lastFunnelTarget = null;
        this.funnelTargetCooldownTicks = 0;
        this.funnelBufferEntries.clear();
        this.pendingCraftTerminalOpen = false;
        this.pendingCraftTerminalOpenTicks = 0;
        this.pendingRemoteMenuOpenTicks = 0;
        this.screenlessRemoteMenuTicks = 0;
        clearRemoteMenuValidationState();

        if (this.rotateCaptured) {
            this.endRotateCapture(0.0D, 0.0D);
        }

        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        this.selectedItemId = "";
        this.selectedItemLabel = "";
        this.selectedItemPreview = ItemStack.EMPTY;
        this.selectedFluidId = "";
        this.selectedFluidLabel = "";
        this.selectedFluidPreview = ItemStack.EMPTY;
        this.emptyHandSelected = false;
        this.placeRotateSteps = 0;
        this.activeMinePos = null;
        this.activeMineFace = -1;
        this.buildShape = BuildShape.BLOCK;
        clearQuickSlotsLocal();
        clearGuiBindingsLocal();
        if (minecraft.level != null && this.mineRenderPos != null) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        this.mineRenderPos = null;
        this.mineRenderStage = -1;
        clearStorageScanState();
        clearStorageViewDirty();
        this.storagePageReceivedAtMs = 0L;

        if (minecraft.screen instanceof BuilderScreen) {
            minecraft.setScreen(null);
        }

        Entity restore = this.previousCameraEntity != null ? this.previousCameraEntity : minecraft.player;
        minecraft.setCameraEntity(restore);
        minecraft.options.setCameraType(this.previousCameraType);
        minecraft.options.bobView().set(this.previousBobView);
        minecraft.options.fovEffectScale().set(this.previousFovEffectScale);

        this.previousCameraEntity = null;
        this.localMirrorCamera = null;
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
                && !(minecraft.screen instanceof RtsCraftTerminalScreen)
                && shouldUseRtsCraftTerminalScreen(craftingScreen)) {
            CraftingMenu craftingMenu = craftingScreen.getMenu();
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

        this.ensureLocalMirrorCamera(minecraft);
        tickStorageAutoRefresh();

        CameraInput cameraInput = readCameraInput(minecraft);
        float forward = cameraInput.forward;
        float strafe = cameraInput.strafe;
        float vertical = cameraInput.vertical;
        boolean fast = cameraInput.fast;

        float safeRawX = Mth.clamp(this.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float safeRawY = Mth.clamp(this.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        this.emaRotateX += (safeRawX - this.emaRotateX) * ROT_EMA_ALPHA;
        this.emaRotateY += (safeRawY - this.emaRotateY) * ROT_EMA_ALPHA;

        if (Math.abs(safeRawX) < 0.0001F) {
            this.emaRotateX *= ROT_EMA_DECAY;
        }
        if (Math.abs(safeRawY) < 0.0001F) {
            this.emaRotateY *= ROT_EMA_DECAY;
        }

        float inputSensScale = getInputSensitivityScale();
        float rotateXForTick = Mth.clamp(this.emaRotateX * this.rotateSensitivity * inputSensScale, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rotateYForTick = Mth.clamp(this.emaRotateY * this.rotateSensitivity * inputSensScale, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float scrollForTick = this.pendingScroll * inputSensScale;
        if (Math.abs(rotateXForTick) < CAMERA_INPUT_EPSILON) {
            rotateXForTick = 0.0F;
            this.emaRotateX = 0.0F;
        }
        if (Math.abs(rotateYForTick) < CAMERA_INPUT_EPSILON) {
            rotateYForTick = 0.0F;
            this.emaRotateY = 0.0F;
        }
        if (Math.abs(scrollForTick) < CAMERA_INPUT_EPSILON) {
            scrollForTick = 0.0F;
        }

        boolean hasCameraInput = forward != 0.0F || strafe != 0.0F || vertical != 0.0F
                || Math.abs(this.pendingPanX) > CAMERA_INPUT_EPSILON
                || Math.abs(this.pendingPanY) > CAMERA_INPUT_EPSILON
                || rotateXForTick != 0.0F || rotateYForTick != 0.0F
                || scrollForTick != 0.0F || this.pendingRotateSteps != 0;
        if (hasCameraInput && !this.smoothCamera) {
            this.applyLocalPrediction(
                    forward,
                    strafe,
                    vertical,
                    this.pendingPanX,
                    this.pendingPanY,
                    rotateXForTick,
                    rotateYForTick,
                    scrollForTick,
                    this.pendingRotateSteps,
                    fast);
        }

        if (hasCameraInput || ++this.cameraMoveHeartbeatTicks >= CAMERA_IDLE_HEARTBEAT_TICKS) {
            RtsClientPacketGateway.sendCameraMove(
                    forward,
                    strafe,
                    hasCameraInput ? vertical : 0.0F,
                    hasCameraInput ? this.pendingPanX : 0.0F,
                    hasCameraInput ? this.pendingPanY : 0.0F,
                    hasCameraInput ? rotateXForTick : 0.0F,
                    hasCameraInput ? rotateYForTick : 0.0F,
                    hasCameraInput ? scrollForTick : 0.0F,
                    hasCameraInput ? this.pendingRotateSteps : 0,
                    fast);
            this.cameraMoveHeartbeatTicks = 0;
        }

        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;

        this.syncVisualCameraFrame();
    }

    private CameraInput readCameraInput(Minecraft minecraft) {
        BuilderScreen builderScreen = minecraft.screen instanceof BuilderScreen screen ? screen : null;
        boolean suppressMoveKeys = builderScreen != null && builderScreen.isSearchFocused();
        if (suppressMoveKeys) {
            return CameraInput.NONE;
        }

        long window = minecraft.getWindow().getWindow();
        boolean w = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
        boolean s = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
        boolean a = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
        boolean d = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
        boolean up = ClientKeyMappings.CAMERA_UP.isDown()
                || ClientKeyMappings.CAMERA_UP_SECONDARY.isDown()
                || (builderScreen != null && builderScreen.isCameraUpActionHeld());
        boolean down = ClientKeyMappings.CAMERA_DOWN.isDown()
                || (builderScreen != null && builderScreen.isCameraDownActionHeld());
        boolean fast = minecraft.options.keySprint.isDown();

        return new CameraInput(
                (w ? 1.0F : 0.0F) - (s ? 1.0F : 0.0F),
                (a ? 1.0F : 0.0F) - (d ? 1.0F : 0.0F),
                (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F),
                fast);
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
        this.activeMinePos = null;
        this.activeMineFace = -1;
        if (minecraft.level != null && this.mineRenderPos != null) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        this.mineRenderPos = null;
        this.mineRenderStage = -1;
        clearRemoteMenuValidationState();

        if (minecraft.screen instanceof BuilderScreen
                || minecraft.screen instanceof RtsHomeScreen
                || minecraft.screen instanceof RtsProgressionScreen
                || minecraft.screen instanceof RtsCraftTerminalScreen) {
            minecraft.setScreen(null);
        }

        Entity restore = this.previousCameraEntity != null ? this.previousCameraEntity : minecraft.player;
        minecraft.setCameraEntity(restore);
        minecraft.options.setCameraType(this.previousCameraType);
        minecraft.options.bobView().set(this.previousBobView);
        minecraft.options.fovEffectScale().set(this.previousFovEffectScale);

        this.enabled = false;
        this.serverCameraEntityId = -1;
        this.localStateReady = false;
        this.closeRangeAllowed = false;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
        this.previousCameraEntity = null;
        this.localMirrorCamera = null;
        RtsClientPacketGateway.sendToggleCamera(false);
        return true;
    }

    public void queuePanDrag(double dragX, double dragY) {
        float signedDragX = (float) dragX;
        float signedDragY = (float) dragY;
        float panX = this.invertPanDragX ? signedDragX : -signedDragX;
        float panY = this.invertPanDragY ? signedDragY : -signedDragY;
        this.pendingPanX += panX;
        this.pendingPanY += panY;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, panX, panY, 0.0F, 0.0F, 0.0F, 0, false);
        }
    }

    public void queueRotateDrag(double dragX, double dragY) {
        this.pendingRawRotateX += (float) dragX;
        this.pendingRawRotateY += (float) dragY;
        if (this.smoothCamera) {
            applyImmediateRotation((float) dragX, (float) dragY);
        }
    }

    public void queueScroll(double scrollY) {
        this.pendingScroll += (float) scrollY;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                    (float) scrollY * getInputSensitivityScale(), 0, false);
        }
    }

    public void queueRotateQuarter(int direction) {
        this.pendingRotateSteps += direction;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, direction, false);
        }
    }

    private void applyImmediateRotation(float dragX, float dragY) {
        float sens = getInputSensitivityScale() * this.rotateSensitivity;
        float yawDelta = Mth.clamp(dragX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP) * sens;
        float pitchDelta = Mth.clamp(dragY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP) * sens;
        applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, yawDelta, pitchDelta, 0.0F, 0, false);
    }

    private void applyImmediateCameraInput(float forward, float strafe, float vertical, float panX, float panY,
            float rotateX, float rotateY, float scroll, int rotateSteps, boolean fast) {
        if (!this.enabled || !this.localStateReady) {
            return;
        }
        applyLocalPrediction(forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll, rotateSteps, fast);
        snapLocalMirrorCameraPose();
    }

    public void updateFunnelTarget(BlockPos target) {
        if (!this.funnelEnabled || target == null) {
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
        linkStorage(pos, true);
    }

    public void linkStorage(BlockPos pos, boolean allowStore) {
        if (pos == null) {
            return;
        }
        RtsClientPacketGateway.sendLinkStorage(pos, allowStore);
    }

    public void requestStoragePage(int page) {
        markStorageScanStarted();
        RtsClientPacketGateway.sendRequestStoragePage(
                page,
                this.storageSearch,
                this.storageCategory,
                this.storageSort,
                this.storageSortAscending,
                this.storagePageSize);
    }

    public void updateStoragePageSize(int pageSize) {
        int safePageSize = Mth.clamp(pageSize, 1, MAX_STORAGE_PAGE_SIZE);
        if (this.storagePageSize == safePageSize) {
            return;
        }
        this.storagePageSize = safePageSize;
        if (hasStoragePageSnapshot() && !this.storageScanRunning) {
            requestStoragePage(this.storagePage);
        }
    }

    public void requestStoragePageIfNoSnapshot(int page) {
        if (!hasStoragePageSnapshot() && !this.storageScanRunning) {
            requestStoragePage(page);
        }
    }

    public void refreshStoragePage() {
        requestStoragePage(this.storagePage);
    }

    private void tickStorageAutoRefresh() {
        if (!this.storageViewDirty
                || this.storageScanRunning
                || !hasStoragePageSnapshot()
                || !RtsClientUiStateStore.isStorageAutoRefreshEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.storageViewDirtySinceMs <= 0L) {
            this.storageViewDirtySinceMs = now;
            return;
        }
        if (now - this.storageViewDirtySinceMs < STORAGE_AUTO_REFRESH_INTERVAL_MS) {
            return;
        }
        requestStoragePage(this.storagePage);
    }

    public void requestCraftables() {
        this.craftablesSearch = normalizeCraftablesSearch(this.craftablesSearch);
        clearCraftablesState();
        if (this.craftablesSearch.isBlank()) {
            return;
        }
        requestCraftablesPage(0, CRAFTABLE_BATCH_SIZE);
    }

    public void requestMoreCraftables() {
        if (this.craftablesSearch.isBlank() || !this.craftablesHasMore) {
            return;
        }
        requestCraftablesPage(this.craftableEntries.size(), CRAFTABLE_BATCH_SIZE);
    }

    public void setAutoStoreMinedDrops(boolean enabled) {
        this.autoStoreMinedDrops = enabled;
        RtsClientPacketGateway.sendSetAutoStoreMinedDrops(enabled);
    }

    public void toggleAutoStoreMinedDrops() {
        setAutoStoreMinedDrops(!this.autoStoreMinedDrops);
    }

    public void setStorageSearch(String search) {
        this.storageSearch = search == null ? "" : search;
        requestStoragePage(0);
    }

    public void setStorageCategory(String category) {
        String normalized = normalizeCategory(category);
        if (this.storageCategory.equals(normalized)) {
            return;
        }
        this.storageCategory = normalized;
        requestStoragePage(0);
    }

    public void cycleSort() {
        int next = (this.storageSort.ordinal() + 1) % RtsStorageSort.values().length;
        this.storageSort = RtsStorageSort.byId(next);
        requestStoragePage(0);
    }

    public void toggleSortDirection() {
        this.storageSortAscending = !this.storageSortAscending;
        requestStoragePage(0);
    }

    public void prevPage() {
        requestStoragePage(Math.max(0, this.storagePage - 1));
    }

    public void nextPage() {
        requestStoragePage(Math.min(this.storageTotalPages - 1, this.storagePage + 1));
    }

    public void setCraftablesSearch(String search) {
        String normalized = normalizeCraftablesSearch(search);
        if (this.craftablesSearch.equals(normalized)) {
            return;
        }
        this.craftablesSearch = normalized;
        requestCraftables();
    }

    public void setCraftablesShowUnavailable(boolean showUnavailable) {
        if (this.craftablesShowUnavailable == showUnavailable) {
            return;
        }
        this.craftablesShowUnavailable = showUnavailable;
        requestCraftables();
    }

    public void toggleCraftablesShowUnavailable() {
        setCraftablesShowUnavailable(!this.craftablesShowUnavailable);
    }

    public void craftRecipeToLinked(String recipeId) {
        craftRecipeToLinked(recipeId, 1);
    }

    public void craftRecipeToLinked(String recipeId, int craftCount) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendCraftRecipe(recipeId, craftCount);
    }

    public void openCraftTerminal() {
        setStorageSearch("");
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

    public void applyStorageDirty(S2CRtsStorageDirtyPayload payload) {
        if (payload == null || !payload.dirty()) {
            clearStorageViewDirty();
            return;
        }
        if (!this.storageViewDirty) {
            this.storageViewDirtySinceMs = System.currentTimeMillis();
        }
        this.storageViewDirty = true;
    }

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        markStorageScanFinished();
        clearStorageViewDirty();
        this.storageLinked = payload.linked();
        this.linkedStorageName = payload.linkedName();
        this.autoStoreMinedDrops = payload.autoStoreMinedDrops();
        this.bdNetworkEnabled = true;
        this.linkedStoragePositions.clear();
        for (Long packed : payload.linkedPositions()) {
            if (packed == null) {
                continue;
            }
            this.linkedStoragePositions.add(BlockPos.of(packed.longValue()));
        }
        this.storagePage = payload.page();
        this.storageTotalPages = Math.max(1, payload.totalPages());
        this.storageTotalEntries = payload.totalEntries();
        this.storageSearch = payload.search();
        this.storageCategory = normalizeCategory(payload.category());
        this.storageSort = RtsStorageSort.byId(payload.sort());
        this.storageSortAscending = payload.ascending();
        this.storageCategories.clear();
        this.storageCategories.add("all");
        for (String category : payload.categories()) {
            String normalized = normalizeCategory(category);
            if (!this.storageCategories.contains(normalized)) {
                this.storageCategories.add(normalized);
            }
        }
        if (!this.storageCategories.contains(this.storageCategory)) {
            this.storageCategory = "all";
        }
        this.storageEntries.clear();
        this.storageTotalCounts.clear();
        this.fluidEntries.clear();
        this.recentEntries.clear();

        int size = Math.min(payload.itemStacks().size(), payload.counts().size());
        for (int i = 0; i < size; i++) {
            ItemStack stack = payload.itemStacks().get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack preview = stack.copy();
            preview.setCount(1);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(preview.getItem());
            if (id == null) {
                continue;
            }
            this.storageEntries.add(new StorageEntry(preview, id.toString(), payload.counts().get(i), id.getNamespace(), id.getPath()));
        }

        int totalItemSize = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
        for (int i = 0; i < totalItemSize; i++) {
            String itemId = payload.totalItemIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            this.storageTotalCounts.put(itemId, Math.max(0L, payload.totalItemCounts().get(i)));
        }

        int fluidSize = Math.min(payload.fluidIds().size(),
                Math.min(payload.fluidAmounts().size(), payload.fluidCapacities().size()));
        for (int i = 0; i < fluidSize; i++) {
            String fluidId = payload.fluidIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(fluidId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
            ItemStack preview = FluidUtil.getFilledBucket(fluidStack);
            String label = fluid.getFluidType().getDescription(fluidStack).getString();
            this.fluidEntries.add(new FluidEntry(
                    fluidId,
                    label,
                    payload.fluidAmounts().get(i),
                    payload.fluidCapacities().get(i),
                    id.getNamespace(),
                    id.getPath(),
                    preview));
        }

        int recentSize = Math.min(
                payload.recentIds().size(),
                Math.min(
                        payload.recentAmounts().size(),
                        Math.min(payload.recentCapacities().size(), payload.recentKinds().size())));
        for (int i = 0; i < recentSize; i++) {
            RecentEntry entry = decodeRecentEntry(
                    payload.recentIds().get(i),
                    payload.recentAmounts().get(i),
                    payload.recentCapacities().get(i),
                    payload.recentKinds().get(i));
            if (entry != null) {
                this.recentEntries.add(entry);
            }
        }

        applyQuickSlotPayload(payload.quickSlotItemIds(), payload.quickSlotPreviews());
        applyGuiBindingPayload(payload.guiBindingLabels(), payload.guiBindingItemIds());
        refreshSelectedItemPreviewFromStorage();

        this.funnelEnabled = payload.funnelEnabled();
        this.funnelBufferEntries.clear();
        int funnelBufferSize = Math.min(payload.funnelBufferItemIds().size(), payload.funnelBufferCounts().size());
        for (int i = 0; i < funnelBufferSize; i++) {
            String itemId = payload.funnelBufferItemIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            long count = Math.max(0L, payload.funnelBufferCounts().get(i));
            if (count <= 0L) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            this.funnelBufferEntries.add(new FunnelBufferEntry(stack, itemId, count));
        }
        this.storageRevision++;
        if (!this.storageLinked && this.linkedStoragePositions.isEmpty()) {
            clearCraftablesState();
        }
    }

    private void markStorageScanStarted() {
        this.storageScanRunning = true;
        this.storageScanStartedAtMs = System.currentTimeMillis();
        this.storageScanVisibleUntilMs = 0L;
    }

    private void markStorageScanFinished() {
        if (!this.storageScanRunning && this.storageScanStartedAtMs <= 0L) {
            return;
        }
        this.storageScanRunning = false;
        long now = System.currentTimeMillis();
        this.storagePageReceivedAtMs = now;
        this.storageScanVisibleUntilMs = now + STORAGE_SCAN_RESULT_VISIBLE_MS;
    }

    private void clearStorageScanState() {
        this.storageScanRunning = false;
        this.storageScanStartedAtMs = 0L;
        this.storageScanVisibleUntilMs = 0L;
    }

    private void clearStorageViewDirty() {
        this.storageViewDirty = false;
        this.storageViewDirtySinceMs = 0L;
    }

    private void refreshSelectedItemPreviewFromStorage() {
        if (this.selectedItemId == null || this.selectedItemId.isBlank()) {
            return;
        }
        for (StorageEntry entry : this.storageEntries) {
            if (entry != null && this.selectedItemId.equals(entry.itemId())) {
                this.selectedItemPreview = entry.stack().copy();
                this.selectedItemPreview.setCount(1);
                return;
            }
        }
        if (shouldAutoClearSelectedItemWhenUnavailable()
                && hasStoragePageSnapshot()
                && getStorageTotalCount(this.selectedItemId) <= 0L) {
            selectEmptyHandPreserveMode();
        }
    }

    private boolean shouldAutoClearSelectedItemWhenUnavailable() {
        if (isLocalPlayerCreative()) {
            return false;
        }
        return this.selectedItemPreview != null
                && !this.selectedItemPreview.isEmpty()
                && this.selectedItemPreview.getItem() instanceof BlockItem;
    }

    public void applyRemoteMenuHint(S2CRtsRemoteMenuHintPayload payload) {
        beginRemoteMenuOpenGrace();
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        String payloadSearch = normalizeCraftablesSearch(payload.search());
        if (!this.craftablesSearch.equals(payloadSearch)
                || this.craftablesShowUnavailable != payload.showUnavailable()) {
            return;
        }

        int offset = Math.max(0, payload.offset());
        this.pendingCraftableOffsets.remove(offset);
        if (!payload.append() || offset == 0) {
            this.craftableEntries.clear();
        } else if (offset != this.craftableEntries.size()) {
            return;
        }

        int size = Math.min(
                payload.recipeIds().size(),
                Math.min(
                        payload.resultItemIds().size(),
                        Math.min(
                                payload.resultCounts().size(),
                                Math.min(payload.craftable().size(), payload.missingSummaries().size()))));
        int optionFlatIndex = 0;
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.tryParse(payload.resultItemIds().get(i));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                optionFlatIndex += i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            int resultCount = Math.max(1, payload.resultCounts().get(i));
            stack.setCount(Math.min(resultCount, stack.getMaxStackSize()));
            int optionCount = i < payload.recipeOptionCounts().size() ? Math.max(0, payload.recipeOptionCounts().get(i)) : 0;
            List<CraftRecipeOption> recipeOptions = new ArrayList<>(optionCount);
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                if (optionFlatIndex >= payload.optionRecipeIds().size()
                        || optionFlatIndex >= payload.optionResultCounts().size()
                        || optionFlatIndex >= payload.optionCraftable().size()
                        || optionFlatIndex >= payload.optionSummaries().size()
                        || optionFlatIndex >= payload.optionMissingSummaries().size()) {
                    break;
                }
                recipeOptions.add(new CraftRecipeOption(
                        payload.optionRecipeIds().get(optionFlatIndex),
                        Math.max(1, payload.optionResultCounts().get(optionFlatIndex)),
                        payload.optionCraftable().get(optionFlatIndex),
                        payload.optionSummaries().get(optionFlatIndex),
                        payload.optionMissingSummaries().get(optionFlatIndex)));
                optionFlatIndex++;
            }
            if (recipeOptions.isEmpty()) {
                recipeOptions.add(new CraftRecipeOption(
                        payload.recipeIds().get(i),
                        resultCount,
                        payload.craftable().get(i),
                        stack.getHoverName().getString(),
                        payload.missingSummaries().get(i)));
            }
            this.craftableEntries.add(new CraftableEntry(
                    stack,
                    payload.recipeIds().get(i),
                    payload.resultItemIds().get(i),
                    resultCount,
                    payload.craftable().get(i),
                    payload.missingSummaries().get(i),
                    id.getNamespace(),
                    id.getPath(),
                    List.copyOf(recipeOptions)));
        }
        this.craftablesSearch = payloadSearch;
        this.craftablesShowUnavailable = payload.showUnavailable();
        this.craftablesHasMore = payload.hasMore();
        this.craftablesRevision++;
    }

    private void requestCraftablesPage(int offset, int limit) {
        int normalizedOffset = Math.max(0, offset);
        int normalizedLimit = Math.max(1, limit);
        if (!this.pendingCraftableOffsets.add(normalizedOffset)) {
            return;
        }
        RtsClientPacketGateway.sendRequestCraftables(
                this.craftablesSearch,
                this.craftablesShowUnavailable,
                normalizedOffset,
                normalizedLimit);
    }

    private void clearCraftablesState() {
        boolean changed = !this.craftableEntries.isEmpty()
                || this.craftablesHasMore
                || !this.pendingCraftableOffsets.isEmpty();
        this.craftableEntries.clear();
        this.craftablesHasMore = false;
        this.pendingCraftableOffsets.clear();
        if (changed) {
            this.craftablesRevision++;
        }
    }

    private static String normalizeCraftablesSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private void applyStoredLayout(RtsClientLayoutStore.StoragePanelLayout layout) {
        RtsClientLayoutStore.StoragePanelLayout safe = layout == null
                ? RtsClientLayoutStore.loadStoragePanelLayout()
                : layout;
        this.storagePanelXNormalized = clampLayoutNormalized(safe.xNormalized());
        this.storagePanelYNormalized = clampLayoutNormalized(safe.yNormalized());
        this.storagePanelWidthNormalized = clampLayoutNormalized(safe.widthNormalized());
        this.storagePanelHeightNormalized = clampLayoutNormalized(safe.heightNormalized());
    }

    private static double clampLayoutNormalized(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Mth.clamp(value, 0.0D, 1.0D);
    }

    private long getStorageFluidAmount(String fluidId) {
        if (fluidId == null || fluidId.isBlank()) {
            return 0L;
        }
        for (FluidEntry entry : this.fluidEntries) {
            if (fluidId.equals(entry.fluidId())) {
                return Math.max(0L, entry.amount());
            }
        }
        return 0L;
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        String itemId = payload.itemId() == null ? "" : payload.itemId();
        int craftedCount = Math.max(0, payload.craftedCount());
        if (itemId.isBlank() || craftedCount <= 0) {
            return;
        }
        List<CraftFeedbackIngredient> decodedIngredients = new ArrayList<>();
        int ingredientSize = Math.min(payload.consumedItemIds().size(), payload.consumedCounts().size());
        for (int i = 0; i < ingredientSize; i++) {
            String consumedItemId = payload.consumedItemIds().get(i);
            ResourceLocation consumedKey = ResourceLocation.tryParse(consumedItemId);
            if (consumedKey == null || !BuiltInRegistries.ITEM.containsKey(consumedKey)) {
                continue;
            }
            ItemStack preview = new ItemStack(BuiltInRegistries.ITEM.get(consumedKey));
            decodedIngredients.add(new CraftFeedbackIngredient(
                    consumedItemId,
                    preview.getHoverName().getString(),
                    preview,
                    Math.max(0, payload.consumedCounts().get(i))));
        }
        long now = System.currentTimeMillis();
        boolean mergeWithActive = itemId.equals(this.craftFeedbackItemId) && now <= this.craftFeedbackExpiryMs;
        if (mergeWithActive) {
            this.craftFeedbackCount += craftedCount;
        } else {
            this.craftFeedbackItemId = itemId;
            this.craftFeedbackCount = craftedCount;
        }
        if (mergeWithActive) {
            mergeCraftFeedbackIngredients(decodedIngredients);
        } else {
            this.craftFeedbackIngredients.clear();
            this.craftFeedbackIngredients.addAll(decodedIngredients);
        }
        this.craftFeedbackExpiryMs = now + 2200L;
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

    private void mergeCraftFeedbackIngredients(List<CraftFeedbackIngredient> added) {
        if (added == null || added.isEmpty()) {
            return;
        }
        Map<String, CraftFeedbackIngredient> merged = new LinkedHashMap<>();
        for (CraftFeedbackIngredient ingredient : this.craftFeedbackIngredients) {
            if (ingredient == null || ingredient.itemId() == null || ingredient.itemId().isBlank()) {
                continue;
            }
            merged.put(ingredient.itemId(), ingredient);
        }
        for (CraftFeedbackIngredient ingredient : added) {
            if (ingredient == null || ingredient.itemId() == null || ingredient.itemId().isBlank()) {
                continue;
            }
            CraftFeedbackIngredient existing = merged.get(ingredient.itemId());
            if (existing == null) {
                merged.put(ingredient.itemId(), ingredient);
                continue;
            }
            merged.put(
                    ingredient.itemId(),
                    new CraftFeedbackIngredient(
                            ingredient.itemId(),
                            ingredient.label(),
                            ingredient.preview().copy(),
                            existing.count() + ingredient.count()));
        }
        this.craftFeedbackIngredients.clear();
        this.craftFeedbackIngredients.addAll(merged.values());
    }

    public void applyMineProgress(S2CRtsMineProgressPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos pos = payload.pos();
        int stage = payload.stage();
        if (stage < 0) {
            if (this.mineRenderPos != null) {
                minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
                this.mineRenderPos = null;
            } else {
                minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, pos, -1);
            }
            this.mineRenderStage = -1;
            return;
        }

        if (this.mineRenderPos != null && !this.mineRenderPos.equals(pos)) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, pos, Math.min(9, stage));
        this.mineRenderPos = pos.immutable();
        this.mineRenderStage = Math.min(9, stage);
    }

    public void applyUltimineProgress(S2CRtsUltimineProgressPayload payload) {
        if (payload == null || payload.processed() < 0 || payload.total() <= 0) {
            this.ultimineProgressProcessed = -1;
            this.ultimineProgressTotal = 0;
            return;
        }
        this.ultimineProgressProcessed = payload.processed();
        this.ultimineProgressTotal = payload.total();
    }

    public void applyProgressionState(S2CRtsProgressionStatePayload payload) {
        this.progressionEnabled = payload.enabled();
        this.progressionHomeSet = payload.homeSet();
        this.progressionHomePos = payload.homePos();
        this.progressionHomeDimension = payload.homeDimension() == null ? "" : payload.homeDimension();
        this.progressionHomeCooldownTicks = payload.homeCooldownTicks();
        this.progressionRadiusBlocks = payload.radiusBlocks();
        this.progressionFluidCapacityBuckets = payload.fluidCapacityBuckets();
        this.progressionUltimineLimit = payload.ultimineLimit();
        this.progressionBypassHomeRadius = payload.bypassHomeRadius();
        this.unlockedProgressionNodes.clear();
        this.unlockedProgressionNodes.addAll(payload.unlockedNodes());
        this.unlockableProgressionNodes.clear();
        this.unlockableProgressionNodes.addAll(payload.unlockableNodes());
        if (!this.progressionEnabled) {
            clearProgressionLocksForDisabled(payload.radiusBlocks(), payload.fluidCapacityBuckets(), payload.ultimineLimit());
        }
        RtsProgressionNodes.applySyncedCostOverrides(payload.costOverrides());
    }

    private void clearProgressionLocksForDisabled() {
        clearProgressionLocksForDisabled(128, 100, 256);
    }

    private void clearProgressionLocksForDisabled(int radiusBlocks, int fluidCapacityBuckets, int ultimineLimit) {
        this.progressionEnabled = false;
        this.progressionHomeSet = false;
        this.progressionHomePos = BlockPos.ZERO;
        this.progressionHomeDimension = "";
        this.progressionHomeCooldownTicks = 0L;
        this.progressionRadiusBlocks = Math.max(1, radiusBlocks);
        this.progressionFluidCapacityBuckets = Math.max(1, fluidCapacityBuckets);
        this.progressionUltimineLimit = Math.max(1, ultimineLimit);
        this.progressionBypassHomeRadius = true;
        this.unlockedProgressionNodes.clear();
        this.unlockableProgressionNodes.clear();
        this.homeSelectionMode = false;
    }

    public void requestProgressionState() {
        RtsClientPacketGateway.sendRequestProgressionState();
    }

    public void unlockProgressionNode(ResourceLocation nodeId) {
        RtsClientPacketGateway.sendUnlockProgressionNode(nodeId);
    }

    public void setSurvivalProgressionEnabled(boolean enabled) {
        if (!enabled) {
            clearProgressionLocksForDisabled();
        }
        RtsClientPacketGateway.sendSetSurvivalProgression(enabled);
        RtsClientPacketGateway.sendRequestProgressionState();
    }

    public void setProgressionCost(ResourceLocation nodeId, String costsText) {
        if (nodeId == null) {
            return;
        }
        RtsClientPacketGateway.sendSetProgressionCost(nodeId, costsText);
    }

    public void setHome(BlockPos pos) {
        RtsClientPacketGateway.sendSetHome(pos);
    }

    public void beginHomeSelection() {
        RtsClientPacketGateway.sendBeginHomeSelection();
    }

    public void selectStorageEntry(int index) {
        if (index < 0 || index >= this.storageEntries.size()) {
            return;
        }
        StorageEntry entry = this.storageEntries.get(index);
        setSelectedItem(entry.itemId(), entry.stack().getHoverName().getString(), entry.stack().copy());
        clearSelectedFluid();
        setMode(BuilderMode.INTERACT);
    }

    public void selectFluidEntry(int index) {
        if (index < 0 || index >= this.fluidEntries.size()) {
            return;
        }
        FluidEntry entry = this.fluidEntries.get(index);
        setSelectedFluid(entry.fluidId(), entry.label(), entry.preview().copy());
        clearSelectedItemOnly();
        setMode(BuilderMode.INTERACT);
    }

    public void clearSelectedItem() {
        clearPlacementSelectionPreserveMode();
        setMode(BuilderMode.INTERACT);
    }

    public void clearPlacementSelectionPreserveMode() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = false;
        this.placeRotateSteps = 0;
    }

    public void selectEmptyHand() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
        setMode(BuilderMode.INTERACT);
    }

    public void selectRecentEntry(int index) {
        if (index < 0 || index >= this.recentEntries.size()) {
            return;
        }
        RecentEntry entry = this.recentEntries.get(index);
        if (entry.fluid()) {
            setSelectedFluid(entry.id(), entry.label(), entry.preview().copy());
            clearSelectedItemOnly();
        } else {
            setSelectedItem(entry.id(), entry.label(), entry.preview().copy());
            clearSelectedFluid();
        }
        setMode(BuilderMode.INTERACT);
    }

    public void assignQuickSlotFromSelected(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        if (this.selectedItemId.isBlank() || this.selectedItemPreview.isEmpty()) {
            clearQuickSlot(index);
            return;
        }
        setQuickSlotLocal(index, this.selectedItemId, this.selectedItemPreview.copy());
        RtsClientPacketGateway.sendSetQuickSlot(index, this.selectedItemId, this.selectedItemPreview);
    }

    public void assignQuickSlotFromToolItem(int index, ItemStack stack) {
        if (index < 0 || index >= QUICK_SLOT_COUNT || stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        String itemId = id.toString();
        setQuickSlotLocal(index, itemId, stack.copy());
        RtsClientPacketGateway.sendSetQuickSlot(index, itemId, stack);
    }

    public void clearQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        setQuickSlotLocal(index, "", ItemStack.EMPTY);
        RtsClientPacketGateway.sendSetQuickSlot(index, "", ItemStack.EMPTY);
    }

    public void selectQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        String itemId = this.quickSlotItemIds[index];
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        ItemStack preview = this.quickSlotPreviews[index];
        if (preview.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return;
            }
            preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        String label = this.quickSlotLabels[index];
        if (label == null || label.isBlank()) {
            label = preview.getHoverName().getString();
        }
        setSelectedItem(itemId, label, preview.copy());
        clearSelectedFluid();
        setMode(BuilderMode.INTERACT);
    }

    public void selectItemForPlacement(String itemId, String label, ItemStack preview) {
        if (itemId == null || itemId.isBlank() || preview == null || preview.isEmpty()) {
            return;
        }
        ItemStack safePreview = preview.copy();
        safePreview.setCount(1);
        setSelectedItem(itemId, label == null || label.isBlank() ? safePreview.getHoverName().getString() : label, safePreview);
        clearSelectedFluid();
        setMode(BuilderMode.INTERACT);
    }

    public void setGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT || pos == null) {
            return;
        }
        RtsClientPacketGateway.sendSetGuiBinding(index, pos, face, itemIdHint);
    }

    public void clearGuiBinding(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT) {
            return;
        }
        this.guiBindingLabels[index] = "";
        RtsClientPacketGateway.sendClearGuiBinding(index);
    }

    public void openGuiBinding(int index) {
        if (index < 0 || index >= GUI_BINDING_SLOT_COUNT || !hasGuiBinding(index)) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendOpenGuiBinding(index);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        placeSelected(hit, forcePlace, rayOrigin, rayDir, false);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
        placeSelected(hit, forcePlace, rayOrigin, rayDir, skipIfOccupied, false);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied,
            boolean quickBuild) {
        beginRemoteMenuOpenGrace();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable();
        if (!itemId.isBlank() && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }
        boolean clearAfterPlace = !itemId.isBlank() && autoClearUnavailable && selectedCount <= 1L;
        ItemStack itemPrototype = itemId.isBlank() ? ItemStack.EMPTY : this.selectedItemPreview;
        RtsClientPacketGateway.sendPlace(
                hit,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemPrototype,
                itemId.isBlank() ? 0 : this.placeRotateSteps,
                rayOrigin,
                rayDir,
                quickBuild);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage(this.storagePage);
        }
    }

    public void placeSelectedBatch(List<BlockHitResult> hits, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
            boolean skipIfOccupied) {
        beginRemoteMenuOpenGrace();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable();
        if (!itemId.isBlank() && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }
        int attemptedPlacements = hits == null ? 0 : hits.size();
        boolean clearAfterPlace = !itemId.isBlank()
                && autoClearUnavailable
                && selectedCount <= Math.max(1, attemptedPlacements);
        RtsClientPacketGateway.sendPlaceBatch(
                hits,
                forcePlace,
                skipIfOccupied,
                itemId,
                itemId.isBlank() ? ItemStack.EMPTY : this.selectedItemPreview,
                itemId.isBlank() ? 0 : this.placeRotateSteps,
                rayOrigin,
                rayDir);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage(this.storagePage);
        }
    }

    public void placeSelectedFluid(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null || this.selectedFluidId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendPlaceFluid(hit, forcePlace, this.selectedFluidId, rayOrigin, rayDir);
    }

    public void storeFluidFromStorageItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, 0, itemId);
    }

    public void storeFluidFromPinnedItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM, 0, itemId);
    }

    public void storeFluidFromToolSlot(int toolSlot) {
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT, toolSlot, "");
    }

    private void clearQuickSlotsLocal() {
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            this.quickSlotItemIds[i] = "";
            this.quickSlotLabels[i] = "";
            this.quickSlotPreviews[i] = ItemStack.EMPTY;
        }
    }

    private void clearGuiBindingsLocal() {
        for (int i = 0; i < GUI_BINDING_SLOT_COUNT; i++) {
            this.guiBindingLabels[i] = "";
            this.guiBindingItemIds[i] = "";
            this.guiBindingPreviews[i] = ItemStack.EMPTY;
        }
    }

    private void applyQuickSlotPayload(List<String> payloadQuickSlots) {
        applyQuickSlotPayload(payloadQuickSlots, List.of());
    }

    private void applyQuickSlotPayload(List<String> payloadQuickSlots, List<ItemStack> payloadQuickSlotPreviews) {
        clearQuickSlotsLocal();
        int size = Math.min(QUICK_SLOT_COUNT, payloadQuickSlots == null ? 0 : payloadQuickSlots.size());
        for (int i = 0; i < size; i++) {
            String itemId = payloadQuickSlots.get(i);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            ItemStack preview = payloadQuickSlotPreviews != null && i < payloadQuickSlotPreviews.size()
                    ? payloadQuickSlotPreviews.get(i)
                    : ItemStack.EMPTY;
            if (preview == null || preview.isEmpty() || !preview.is(BuiltInRegistries.ITEM.get(key))) {
                preview = resolveQuickSlotFallbackPreview(itemId, key);
            } else {
                preview = preview.copyWithCount(1);
            }
            setQuickSlotLocal(i, itemId, preview);
        }
    }

    private ItemStack resolveQuickSlotFallbackPreview(String itemId, ResourceLocation key) {
        for (StorageEntry entry : this.storageEntries) {
            if (entry != null && itemId.equals(entry.itemId()) && entry.stack() != null && !entry.stack().isEmpty()) {
                return entry.stack().copyWithCount(1);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (ItemStack stack : mc.player.getInventory().items) {
                if (stack != null && !stack.isEmpty() && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                    return stack.copyWithCount(1);
                }
            }
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private void applyGuiBindingPayload(List<String> payloadGuiBindings, List<String> payloadGuiBindingItemIds) {
        clearGuiBindingsLocal();
        int size = Math.min(
                GUI_BINDING_SLOT_COUNT,
                Math.min(
                        payloadGuiBindings == null ? 0 : payloadGuiBindings.size(),
                        payloadGuiBindingItemIds == null ? 0 : payloadGuiBindingItemIds.size()));
        for (int i = 0; i < size; i++) {
            String label = payloadGuiBindings.get(i);
            this.guiBindingLabels[i] = label == null ? "" : label;
            String itemId = payloadGuiBindingItemIds.get(i);
            this.guiBindingItemIds[i] = itemId == null ? "" : itemId;
            ResourceLocation key = ResourceLocation.tryParse(this.guiBindingItemIds[i]);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                this.guiBindingItemIds[i] = "";
                this.guiBindingPreviews[i] = ItemStack.EMPTY;
                continue;
            }
            this.guiBindingPreviews[i] = new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
    }

    private void setQuickSlotLocal(int index, String itemId, ItemStack preview) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        String normalizedItemId = itemId == null ? "" : itemId;
        ItemStack normalizedPreview = preview == null ? ItemStack.EMPTY : preview.copy();
        this.quickSlotItemIds[index] = normalizedItemId;
        if (normalizedItemId.isBlank() || normalizedPreview.isEmpty()) {
            this.quickSlotLabels[index] = "";
            this.quickSlotPreviews[index] = ItemStack.EMPTY;
            return;
        }
        this.quickSlotLabels[index] = normalizedPreview.getHoverName().getString();
        this.quickSlotPreviews[index] = normalizedPreview;
    }

    public void interactEmpty(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendEmptyHandPlace(hit, rayOrigin, rayDir);
    }

    public void interactEntityEmpty(int entityId, Vec3 hitLocation, Vec3 rayOrigin, Vec3 rayDir) {
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendInteractEntityEmptyHand(entityId, hitLocation, rayOrigin, rayDir);
    }

    public void interactBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendInteractBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void useItemInAirWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendUseItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void interactBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null || itemId == null || itemId.isBlank()) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendInteractBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir);
    }

    public void interactEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        if (entityId < 0 || hitLocation == null) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendInteractEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir);
    }

    public void interactEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        if (entityId < 0 || hitLocation == null || itemId == null || itemId.isBlank()) {
            return;
        }
        beginRemoteMenuOpenGrace();
        RtsClientPacketGateway.sendInteractEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir);
    }

    public void breakPlaced(BlockPos pos) {
        breakPlaced(pos, Direction.UP, false);
    }

    public void breakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        if (pos == null) {
            return;
        }
        Direction resolvedFace = face == null ? Direction.UP : face;
        RtsClientPacketGateway.sendBreakPlaced(pos, resolvedFace, allowAdjacentFallback);
    }

    public void startMining(BlockPos pos, int face, int toolSlot) {
        if (pos == null) {
            return;
        }
        this.activeMinePos = pos.immutable();
        this.activeMineFace = face;
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = this.activeMinePos;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendMineStart(
                this.activeMinePos,
                face,
                this.activeMineToolSlot,
                selectedMiningToolItemId(),
                selectedMiningToolPrototype(),
                this.allowPlacedBlockRecovery,
                this.toolProtectionEnabled);
    }

    public void startUltimine(BlockPos pos, int face, int toolSlot, int limit, byte mode) {
        if (pos == null) {
            return;
        }
        this.activeMinePos = pos.immutable();
        this.activeMineFace = face;
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = this.activeMinePos;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendUltimineStart(
                this.activeMinePos,
                face,
                this.activeMineToolSlot,
                selectedMiningToolItemId(),
                selectedMiningToolPrototype(),
                limit,
                mode,
                this.toolProtectionEnabled);
    }

    public void continueMining(int toolSlot) {
        // Mining progress is maintained server-side after START; no per-tick packet needed.
    }

    public void confirmShapeAreaDestroy(List<BlockPos> targets, int toolSlot) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        BlockPos first = targets.get(0).immutable();
        this.activeMinePos = first;
        this.activeMineFace = Direction.UP.get3DDataValue();
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        this.mineRenderPos = first;
        this.mineRenderStage = 0;
        RtsClientPacketGateway.sendAreaDestroy(
                targets,
                this.activeMineToolSlot,
                selectedMiningToolItemId(),
                selectedMiningToolPrototype(),
                this.toolProtectionEnabled);
    }

    public void abortMining(int toolSlot) {
        if (this.activeMinePos == null || this.activeMineFace < 0) {
            return;
        }
        RtsClientPacketGateway.sendMineAbort(this.activeMinePos, this.activeMineFace, toolSlot);
        this.activeMinePos = null;
        this.activeMineFace = -1;
        this.mineRenderStage = -1;
    }

    private String selectedMiningToolItemId() {
        return selectedMiningToolPrototype().isEmpty() ? "" : this.selectedItemId;
    }

    private ItemStack selectedMiningToolPrototype() {
        if (this.selectedItemId == null || this.selectedItemId.isBlank() || this.selectedItemPreview == null || this.selectedItemPreview.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (this.selectedItemPreview.getItem() instanceof BlockItem) {
            return ItemStack.EMPTY;
        }
        ItemStack prototype = this.selectedItemPreview.copy();
        prototype.setCount(1);
        return prototype;
    }

    public int getMineProgressStage() {
        return this.mineRenderStage;
    }

    public int getUltimineProgressProcessed() {
        return this.ultimineProgressProcessed;
    }

    public int getUltimineProgressTotal() {
        return this.ultimineProgressTotal;
    }

    public BlockPos getMineProgressPos() {
        return this.mineRenderPos;
    }

    private void beginRemoteMenuOpenGrace() {
        this.pendingRemoteMenuOpenTicks = Math.max(this.pendingRemoteMenuOpenTicks, REMOTE_MENU_OPEN_GRACE_TICKS);
        this.screenlessRemoteMenuTicks = 0;
        RtsRemoteMenuCompat.beginClientRemoteMenuOpen();
        RtsSophisticatedStorageCompat.beginClientRemoteMenuOpen();
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
        RtsSophisticatedStorageCompat.clearClientRemoteMenu();
    }

    private void clearSelectedItemOnly() {
        setSelectedItem("", "", ItemStack.EMPTY);
    }

    private void clearSelectedFluid() {
        setSelectedFluid("", "", ItemStack.EMPTY);
    }

    private void selectEmptyHandPreserveMode() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
    }

    private long getSelectedItemCountForPlacement(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Long.MAX_VALUE;
        }
        if (isLocalPlayerCreative()) {
            return Long.MAX_VALUE;
        }
        return hasStoragePageSnapshot() ? getStorageTotalCount(itemId) : Long.MAX_VALUE;
    }

    private boolean isLocalPlayerCreative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    private void setSelectedItem(String itemId, String label, ItemStack preview) {
        this.selectedItemId = itemId == null ? "" : itemId;
        this.selectedItemLabel = label == null ? "" : label;
        this.selectedItemPreview = preview == null ? ItemStack.EMPTY : preview;
        if (!this.selectedItemId.isBlank()) {
            this.emptyHandSelected = false;
        }
    }

    private void setSelectedFluid(String fluidId, String label, ItemStack preview) {
        this.selectedFluidId = fluidId == null ? "" : fluidId;
        this.selectedFluidLabel = label == null ? "" : label;
        this.selectedFluidPreview = preview == null ? ItemStack.EMPTY : preview;
        if (!this.selectedFluidId.isBlank()) {
            this.emptyHandSelected = false;
        }
    }

    public void rotatePlacementClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 1) & 3;
    }

    public void rotatePlacementCounterClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 3) & 3;
    }

    public void syncVisualCameraFrame() {
        if (!this.enabled || !this.localStateReady) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        this.ensureLocalMirrorCamera(minecraft);
        if (this.localMirrorCamera == null) {
            return;
        }

        if (this.smoothCamera) {
            applySmoothFrameMovement(minecraft);
        } else {
            this.lastSmoothCameraFrameNanos = 0L;
        }

        snapLocalMirrorCameraPose();

        if (minecraft.getCameraEntity() != this.localMirrorCamera) {
            if (this.cameraRestoreCooldownTicks <= 0) {
                minecraft.setCameraEntity(this.localMirrorCamera);
                this.cameraRestoreCooldownTicks = CAMERA_RESTORE_COOLDOWN_TICKS;
            } else {
                this.cameraRestoreCooldownTicks--;
            }
        } else if (this.cameraRestoreCooldownTicks > 0) {
            this.cameraRestoreCooldownTicks--;
        }
    }

    private void applySmoothFrameMovement(Minecraft minecraft) {
        long now = System.nanoTime();
        if (this.lastSmoothCameraFrameNanos == 0L) {
            this.lastSmoothCameraFrameNanos = now;
            return;
        }

        long elapsed = now - this.lastSmoothCameraFrameNanos;
        this.lastSmoothCameraFrameNanos = now;
        if (elapsed <= 0L) {
            return;
        }

        float tickDelta = Mth.clamp(elapsed / (float) NANOS_PER_TICK, 0.0F, MAX_SMOOTH_FRAME_TICKS);
        if (tickDelta <= CAMERA_INPUT_EPSILON) {
            return;
        }

        CameraInput input = readCameraInput(minecraft);
        if (!input.hasMovement()) {
            return;
        }

        applyLocalPrediction(
                input.forward * tickDelta,
                input.strafe * tickDelta,
                input.vertical * tickDelta,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0,
                input.fast);
    }

    private void snapLocalMirrorCameraPose() {
        if (this.localMirrorCamera != null) {
            this.localMirrorCamera.snapTo(this.localX, this.localY, this.localZ, this.localYawDeg, this.localPitchDeg);
        }
    }

    private void ensureLocalMirrorCamera(Minecraft minecraft) {
        if (minecraft.level == null) {
            this.localMirrorCamera = null;
            return;
        }

        if (this.localMirrorCamera != null && this.localMirrorCamera.level() == minecraft.level) {
            return;
        }

        this.localMirrorCamera = new RtsCameraEntity(RtsbuildingMod.RTS_CAMERA_ENTITY.get(), minecraft.level);
        this.localMirrorCamera.snapTo(this.localX, this.localY, this.localZ, this.localYawDeg, this.localPitchDeg);
    }

    private void applyLocalPrediction(float forward, float strafe, float vertical, float panX, float panY, float rotateX, float rotateY,
            float scroll, int rotateSteps, boolean fast) {
        this.localYawDeg += rotateX * ROTATE_GAIN_X;
        if (rotateSteps != 0) {
            this.localYawDeg = snapQuarter(this.localYawDeg + (90.0F * rotateSteps));
        }
        this.localPitchDeg = Mth.clamp(this.localPitchDeg + (rotateY * ROTATE_GAIN_Y), MIN_CAMERA_PITCH, MAX_CAMERA_PITCH);

        double speed = fast ? 0.80D : 0.45D;
        double yawRad = Math.toRadians(this.localYawDeg);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetX = this.localX;
        double targetY = this.localY;
        double targetZ = this.localZ;

        float safeVertical = Mth.clamp(vertical, -1.0F, 1.0F);
        double dx = (-sin * forward + cos * strafe) * speed;
        double dz = (cos * forward + sin * strafe) * speed;

        double dragScale = 0.020D * Math.max(8.0D, this.localHeightOffset);
        double moveRight = panX * dragScale;
        double moveForward = -panY * dragScale;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        dx += rightX * moveRight + fwdX * moveForward;
        dz += rightZ * moveRight + fwdZ * moveForward;

        targetX += dx;
        targetY += safeVertical * (fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        targetZ += dz;

        if (scroll != 0.0F) {
            double pitchRad = Math.toRadians(this.localPitchDeg);
            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double dolly = scroll * DOLLY_PER_SCROLL;
            targetX += lookX * dolly;
            targetY += lookY * dolly;
            targetZ += lookZ * dolly;
        }

        double adx = targetX - this.anchorX;
        double adz = targetZ - this.anchorZ;
        // 娴ｈ法鏁ゅ锝嗘煙娴ｆ捁绔熼悾宀勬閸掓湹鍞弴鍨妇瑜般垼绔熼悾宀嬬礉娑撳孩鏂佺純顕€妾洪崚鍓佸缁惧じ绻氶幐浣风??
        double halfExtent = this.maxRadius;
        targetX = Mth.clamp(targetX, this.anchorX - halfExtent, this.anchorX + halfExtent);
        targetZ = Mth.clamp(targetZ, this.anchorZ - halfExtent, this.anchorZ + halfExtent);

        targetY = Mth.clamp(targetY, this.anchorY + MIN_CAMERA_HEIGHT_OFFSET, this.anchorY + MAX_CAMERA_HEIGHT_OFFSET);

        // Keep movement bounds square so they match the visible build boundary.

        this.localX = targetX;
        this.localY = targetY;
        this.localZ = targetZ;
        this.localHeightOffset = this.localY - this.anchorY;
    }

    private static float snapQuarter(float yaw) {
        int quarter = Math.round(yaw / 90.0F);
        return quarter * 90.0F;
    }

    private float getInputSensitivityScale() {
        if (this.inputSensitivityIndex < 0 || this.inputSensitivityIndex >= INPUT_SENS_PRESETS.length) {
            this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        }
        return INPUT_SENS_PRESETS[this.inputSensitivityIndex];
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        return CATEGORY_MOD_PREFIX + value;
    }

    public record StorageEntry(ItemStack stack, String itemId, long count, String mod, String name) {
    }

    public record FluidEntry(
            String fluidId,
            String label,
            long amount,
            long capacity,
            String mod,
            String name,
            ItemStack preview) {
    }

    public record FunnelBufferEntry(ItemStack stack, String itemId, long count) {
    }

    public record RecentEntry(
            boolean fluid,
            String id,
            String label,
            long amount,
            long capacity,
            byte kind,
            ItemStack preview) {
    }

    public record CraftRecipeOption(
            String recipeId,
            int resultCount,
            boolean craftable,
            String summary,
            String missingSummary) {
    }

    public record CraftFeedbackIngredient(
            String itemId,
            String label,
            ItemStack preview,
            int count) {
    }

    public record CraftableEntry(
            ItemStack stack,
            String recipeId,
            String itemId,
            int resultCount,
            boolean craftable,
            String missingSummary,
            String mod,
            String name,
            List<CraftRecipeOption> recipeOptions) {
    }

    public enum BuildShape {
        BLOCK,
        LINE,
        SQUARE,
        WALL,
        CIRCLE,
        BOX
    }

    private static final class CameraInput {
        private static final CameraInput NONE = new CameraInput(0.0F, 0.0F, 0.0F, false);

        final float forward;
        final float strafe;
        final float vertical;
        final boolean fast;

        CameraInput(float forward, float strafe, float vertical, boolean fast) {
            this.forward = forward;
            this.strafe = strafe;
            this.vertical = vertical;
            this.fast = fast;
        }

        boolean hasMovement() {
            return this.forward != 0.0F || this.strafe != 0.0F || this.vertical != 0.0F;
        }
    }

    private static RecentEntry decodeRecentEntry(String idText, long amount, long capacity, byte kind) {
        if (idText == null || idText.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) {
            return null;
        }
        boolean fluidKind = kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
        if (fluidKind) {
            if (!BuiltInRegistries.FLUID.containsKey(id)) {
                return null;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidStack fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
            ItemStack preview = FluidUtil.getFilledBucket(fluidStack);
            String label = fluid.getFluidType().getDescription(fluidStack).getString();
            return new RecentEntry(true, idText, label, Math.max(0L, amount), Math.max(0L, capacity), kind, preview);
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        ItemStack preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return new RecentEntry(false, idText, preview.getHoverName().getString(), Math.max(0L, amount), 0L, kind, preview);
    }

}



