package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.C2SRtsBreakPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsFunnelTargetPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlacePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsQuickDropPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetAutoStorePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetFunnelPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetModePayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsStoragePagePayload;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

public final class ClientRtsController {
    private static final ClientRtsController INSTANCE = new ClientRtsController();

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final float ROT_SENS_MIN = 1.00F;
    private static final float ROT_SENS_MAX = 10.00F;
    private static final float ROT_SENS_STEP = 0.50F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final float[] INPUT_SENS_PRESETS = new float[] { 0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F };
    private static final int INPUT_SENS_DEFAULT_INDEX = 2;
    private static final int QUICK_SLOT_COUNT = 27;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private static final float ROT_EMA_ALPHA = 0.28F;
    private static final float ROT_EMA_DECAY = 0.78F;
    private static final int RTS_MINE_RENDER_ID = 0x525453;

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

    private float rotateSensitivity = 5.00F;
    private int inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;

    private BuilderMode mode = BuilderMode.INTERACT;
    private boolean storageCollapsed;
    private boolean storageLinked;
    private String linkedStorageName = "No Storage";
    private final List<BlockPos> linkedStoragePositions = new ArrayList<>();
    private int storagePage;
    private int storageTotalPages = 1;
    private int storageTotalEntries;
    private int storageRevision;
    private String storageSearch = "";
    private String storageCategory = "all";
    private RtsStorageSort storageSort = RtsStorageSort.QUANTITY;
    private boolean storageSortAscending;
    private final List<String> storageCategories = new ArrayList<>();
    private final List<StorageEntry> storageEntries = new ArrayList<>();
    private final List<FluidEntry> fluidEntries = new ArrayList<>();
    private String craftablesSearch = "";
    private boolean craftablesShowUnavailable;
    private final List<CraftableEntry> craftableEntries = new ArrayList<>();
    private int craftablesRevision;
    private String craftFeedbackItemId = "";
    private int craftFeedbackCount;
    private long craftFeedbackExpiryMs;
    private String selectedItemId = "";
    private String selectedItemLabel = "";
    private ItemStack selectedItemPreview = ItemStack.EMPTY;
    private String selectedFluidId = "";
    private String selectedFluidLabel = "";
    private ItemStack selectedFluidPreview = ItemStack.EMPTY;
    private int placeRotateSteps;
    private BlockPos activeMinePos;
    private int activeMineFace = -1;
    private int activeMineToolSlot;
    private BlockPos mineRenderPos;
    private BuildShape buildShape = BuildShape.BLOCK;
    private boolean pendingCraftTerminalOpen;
    private int pendingCraftTerminalOpenTicks;
    private boolean autoStoreMinedDrops = true;
    private final String[] quickSlotItemIds = new String[QUICK_SLOT_COUNT];
    private final String[] quickSlotLabels = new String[QUICK_SLOT_COUNT];
    private final ItemStack[] quickSlotPreviews = new ItemStack[QUICK_SLOT_COUNT];
    private boolean funnelEnabled;
    private BlockPos lastFunnelTarget;
    private int funnelTargetCooldownTicks;
    private final List<FunnelBufferEntry> funnelBufferEntries = new ArrayList<>();

    // Local render-only camera entity to isolate rendering from network interpolation.
    private RtsCameraEntity localMirrorCamera;

    private ClientRtsController() {
        this.storageCategories.add("all");
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            this.quickSlotItemIds[i] = "";
            this.quickSlotLabels[i] = "";
            this.quickSlotPreviews[i] = ItemStack.EMPTY;
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

    public BuilderMode getMode() {
        return this.mode;
    }

    public void setMode(BuilderMode mode) {
        this.mode = mode;
        PacketDistributor.sendToServer(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    public boolean isFunnelEnabled() {
        return this.funnelEnabled;
    }

    public void setFunnelEnabled(boolean enabled) {
        if (this.funnelEnabled == enabled) {
            return;
        }
        this.funnelEnabled = enabled;
        PacketDistributor.sendToServer(new C2SRtsSetFunnelPayload(enabled));
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

    public List<FluidEntry> getFluidEntries() {
        return Collections.unmodifiableList(this.fluidEntries);
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

    public String getCraftFeedbackItemId() {
        return this.craftFeedbackItemId;
    }

    public int getCraftFeedbackCount() {
        return this.craftFeedbackCount;
    }

    public long getCraftFeedbackExpiryMs() {
        return this.craftFeedbackExpiryMs;
    }

    public List<FunnelBufferEntry> getFunnelBufferEntries() {
        return Collections.unmodifiableList(this.funnelBufferEntries);
    }

    public boolean isAutoStoreMinedDrops() {
        return this.autoStoreMinedDrops;
    }

    public BuildShape getBuildShape() {
        return this.buildShape;
    }

    public void setBuildShape(BuildShape shape) {
        this.buildShape = shape == null ? BuildShape.BLOCK : shape;
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

    public String getInputSensitivityLabel() {
        return String.format(Locale.ROOT, "x%.2f", getInputSensitivityScale());
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
            this.selectedItemId = "";
            this.selectedItemLabel = "";
            this.selectedItemPreview = ItemStack.EMPTY;
            this.selectedFluidId = "";
            this.selectedFluidLabel = "";
            this.selectedFluidPreview = ItemStack.EMPTY;
            this.placeRotateSteps = 0;
            this.activeMinePos = null;
            this.activeMineFace = -1;
            this.mineRenderPos = null;
            this.buildShape = BuildShape.BLOCK;
            this.funnelEnabled = false;
            this.lastFunnelTarget = null;
            this.funnelTargetCooldownTicks = 0;
            this.funnelBufferEntries.clear();
            this.pendingCraftTerminalOpen = false;
            this.pendingCraftTerminalOpenTicks = 0;

            this.ensureLocalMirrorCamera(minecraft);
            this.syncVisualCameraFrame();
            requestStoragePage(0);
            return;
        }

        this.enabled = false;
        this.serverCameraEntityId = -1;
        this.localStateReady = false;
        this.funnelEnabled = false;
        this.lastFunnelTarget = null;
        this.funnelTargetCooldownTicks = 0;
        this.funnelBufferEntries.clear();
        this.pendingCraftTerminalOpen = false;
        this.pendingCraftTerminalOpenTicks = 0;

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
        this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        this.selectedItemId = "";
        this.selectedItemLabel = "";
        this.selectedItemPreview = ItemStack.EMPTY;
        this.selectedFluidId = "";
        this.selectedFluidLabel = "";
        this.selectedFluidPreview = ItemStack.EMPTY;
        this.placeRotateSteps = 0;
        this.activeMinePos = null;
        this.activeMineFace = -1;
        this.buildShape = BuildShape.BLOCK;
        if (minecraft.level != null && this.mineRenderPos != null) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        this.mineRenderPos = null;

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

    public void tick() {
        if (!this.enabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (this.funnelTargetCooldownTicks > 0) {
            this.funnelTargetCooldownTicks--;
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

        if (minecraft.screen == null) {
            minecraft.setScreen(new BuilderScreen(this));
        }

        this.ensureLocalMirrorCamera(minecraft);

        long window = minecraft.getWindow().getWindow();
        boolean suppressMoveKeys = minecraft.screen instanceof BuilderScreen builderScreen && builderScreen.isSearchFocused();
        boolean w = !suppressMoveKeys
                && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_UP));
        boolean s = !suppressMoveKeys
                && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DOWN));
        boolean a = !suppressMoveKeys
                && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT));
        boolean d = !suppressMoveKeys
                && (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT));
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

        float forward = (w ? 1.0F : 0.0F) - (s ? 1.0F : 0.0F);
        float strafe = (a ? 1.0F : 0.0F) - (d ? 1.0F : 0.0F);

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

        if (forward != 0.0F || strafe != 0.0F
                || this.pendingPanX != 0.0F || this.pendingPanY != 0.0F
                || rotateXForTick != 0.0F || rotateYForTick != 0.0F
                || scrollForTick != 0.0F || this.pendingRotateSteps != 0) {
            this.applyLocalPrediction(
                    forward,
                    strafe,
                    this.pendingPanX,
                    this.pendingPanY,
                    rotateXForTick,
                    rotateYForTick,
                    scrollForTick,
                    this.pendingRotateSteps,
                    shift);
        }

        // Fixed packet frequency: one packet per client tick while RTS is enabled.
        PacketDistributor.sendToServer(new C2SRtsCameraMovePayload(
                forward,
                strafe,
                this.pendingPanX,
                this.pendingPanY,
                rotateXForTick,
                rotateYForTick,
                scrollForTick,
                this.pendingRotateSteps,
                shift));

        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;

        this.syncVisualCameraFrame();
    }

    public void queuePanDrag(double dragX, double dragY) {
        this.pendingPanX += (float) dragX;
        this.pendingPanY += (float) dragY;
    }

    public void queueRotateDrag(double dragX, double dragY) {
        this.pendingRawRotateX += (float) dragX;
        this.pendingRawRotateY += (float) dragY;
    }

    public void queueScroll(double scrollY) {
        this.pendingScroll += (float) scrollY;
    }

    public void queueRotateQuarter(int direction) {
        this.pendingRotateSteps += direction;
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
        PacketDistributor.sendToServer(new C2SRtsFunnelTargetPayload(this.lastFunnelTarget));
    }

    public void linkStorage(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsLinkStoragePayload(pos));
    }

    public void requestStoragePage(int page) {
        PacketDistributor.sendToServer(new C2SRtsRequestStoragePagePayload(
                page,
                this.storageSearch,
                this.storageCategory,
                (byte) this.storageSort.ordinal(),
                this.storageSortAscending));
    }

    public void requestCraftables() {
        PacketDistributor.sendToServer(new C2SRtsRequestCraftablesPayload(
                this.craftablesSearch,
                this.craftablesShowUnavailable));
    }

    public void setAutoStoreMinedDrops(boolean enabled) {
        this.autoStoreMinedDrops = enabled;
        PacketDistributor.sendToServer(new C2SRtsSetAutoStorePayload(enabled));
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
        this.craftablesSearch = search == null ? "" : search;
        requestCraftables();
    }

    public void setCraftablesShowUnavailable(boolean showUnavailable) {
        this.craftablesShowUnavailable = showUnavailable;
        requestCraftables();
    }

    public void toggleCraftablesShowUnavailable() {
        setCraftablesShowUnavailable(!this.craftablesShowUnavailable);
    }

    public void craftRecipeToLinked(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsCraftRecipePayload(recipeId));
    }

    public void openCraftTerminal() {
        setStorageSearch("");
        this.pendingCraftTerminalOpen = true;
        this.pendingCraftTerminalOpenTicks = 120;
        PacketDistributor.sendToServer(new C2SRtsOpenCraftTerminalPayload());
    }

    public void detectQuestsNow() {
        PacketDistributor.sendToServer(new C2SRtsQuestDetectPayload(C2SRtsQuestDetectPayload.MODE_MANUAL));
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
        PacketDistributor.sendToServer(new C2SRtsQuickDropPayload(
                itemId,
                (byte) Mth.clamp(amount, 1, 64),
                dropPos.x,
                dropPos.y,
                dropPos.z));
    }

    public void applyStoragePage(S2CRtsStoragePagePayload payload) {
        this.storageLinked = payload.linked();
        this.linkedStorageName = payload.linkedName();
        this.autoStoreMinedDrops = payload.autoStoreMinedDrops();
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
        this.fluidEntries.clear();

        int size = Math.min(payload.itemIds().size(), payload.counts().size());
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.tryParse(payload.itemIds().get(i));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            this.storageEntries.add(new StorageEntry(stack, payload.itemIds().get(i), payload.counts().get(i), id.getNamespace(), id.getPath()));
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
            this.craftableEntries.clear();
            this.craftablesRevision++;
        }
    }

    public void applyCraftables(S2CRtsCraftablesPayload payload) {
        this.craftablesSearch = payload.search() == null ? "" : payload.search();
        this.craftablesShowUnavailable = payload.showUnavailable();
        this.craftableEntries.clear();

        int size = Math.min(
                payload.recipeIds().size(),
                Math.min(
                        payload.resultItemIds().size(),
                        Math.min(
                                payload.resultCounts().size(),
                                Math.min(payload.craftable().size(), payload.missingSummaries().size()))));
        for (int i = 0; i < size; i++) {
            ResourceLocation id = ResourceLocation.tryParse(payload.resultItemIds().get(i));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                continue;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            int resultCount = Math.max(1, payload.resultCounts().get(i));
            stack.setCount(Math.min(resultCount, stack.getMaxStackSize()));
            this.craftableEntries.add(new CraftableEntry(
                    stack,
                    payload.recipeIds().get(i),
                    payload.resultItemIds().get(i),
                    resultCount,
                    payload.craftable().get(i),
                    payload.missingSummaries().get(i),
                    id.getNamespace(),
                    id.getPath()));
        }
        this.craftablesRevision++;
    }

    public void applyCraftFeedback(S2CRtsCraftFeedbackPayload payload) {
        String itemId = payload.itemId() == null ? "" : payload.itemId();
        int craftedCount = Math.max(0, payload.craftedCount());
        if (itemId.isBlank() || craftedCount <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (itemId.equals(this.craftFeedbackItemId) && now <= this.craftFeedbackExpiryMs) {
            this.craftFeedbackCount += craftedCount;
        } else {
            this.craftFeedbackItemId = itemId;
            this.craftFeedbackCount = craftedCount;
        }
        this.craftFeedbackExpiryMs = now + 900L;
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
            return;
        }

        if (this.mineRenderPos != null && !this.mineRenderPos.equals(pos)) {
            minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, this.mineRenderPos, -1);
        }
        minecraft.level.destroyBlockProgress(RTS_MINE_RENDER_ID, pos, Math.min(9, stage));
        this.mineRenderPos = pos.immutable();
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
        this.placeRotateSteps = 0;
    }

    public void assignQuickSlotFromSelected(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        if (this.selectedItemId.isBlank() || this.selectedItemPreview.isEmpty()) {
            clearQuickSlot(index);
            return;
        }
        this.quickSlotItemIds[index] = this.selectedItemId;
        this.quickSlotLabels[index] = this.selectedItemLabel;
        this.quickSlotPreviews[index] = this.selectedItemPreview.copy();
    }

    public void assignQuickSlotFromToolItem(int index, ItemStack stack) {
        if (index < 0 || index >= QUICK_SLOT_COUNT || stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        this.quickSlotItemIds[index] = id.toString();
        this.quickSlotLabels[index] = stack.getHoverName().getString();
        this.quickSlotPreviews[index] = stack.copy();
    }

    public void clearQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        this.quickSlotItemIds[index] = "";
        this.quickSlotLabels[index] = "";
        this.quickSlotPreviews[index] = ItemStack.EMPTY;
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

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        placeSelected(hit, forcePlace, rayOrigin, rayDir, false);
    }

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, boolean skipIfOccupied) {
        PacketDistributor.sendToServer(new C2SRtsPlacePayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                (byte) (this.selectedItemId.isBlank() ? 0 : this.placeRotateSteps),
                forcePlace,
                skipIfOccupied,
                this.selectedItemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void placeSelectedFluid(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null || this.selectedFluidId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsPlaceFluidPayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                forcePlace,
                this.selectedFluidId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void storeFluidFromStorageItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsStoreFluidPayload(
                C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM,
                (byte) 0,
                itemId));
    }

    public void storeFluidFromPinnedItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsStoreFluidPayload(
                C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM,
                (byte) 0,
                itemId));
    }

    public void storeFluidFromToolSlot(int toolSlot) {
        PacketDistributor.sendToServer(new C2SRtsStoreFluidPayload(
                C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT,
                (byte) Mth.clamp(toolSlot, 0, 8),
                ""));
    }

    public void interactEmpty(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsPlacePayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                (byte) 0,
                false,
                false,
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void interactBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void interactBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null || itemId == null || itemId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0,
                itemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void interactEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        if (entityId < 0 || hitLocation == null) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                entityId,
                BlockPos.containing(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void interactEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        if (entityId < 0 || hitLocation == null || itemId == null || itemId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                entityId,
                BlockPos.containing(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0,
                itemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public void breakPlaced(BlockPos pos) {
        breakPlaced(pos, Direction.UP, false);
    }

    public void breakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        if (pos == null) {
            return;
        }
        Direction resolvedFace = face == null ? Direction.UP : face;
        PacketDistributor.sendToServer(new C2SRtsBreakPayload(
                pos,
                (byte) resolvedFace.get3DDataValue(),
                allowAdjacentFallback));
    }

    public void startMining(BlockPos pos, int face, int toolSlot) {
        if (pos == null) {
            return;
        }
        this.activeMinePos = pos.immutable();
        this.activeMineFace = face;
        this.activeMineToolSlot = Mth.clamp(toolSlot, 0, 8);
        PacketDistributor.sendToServer(new C2SRtsMinePayload(this.activeMinePos, (byte) face, true, (byte) this.activeMineToolSlot));
    }

    public void continueMining(int toolSlot) {
        // Mining progress is maintained server-side after START; no per-tick packet needed.
    }

    public void abortMining(int toolSlot) {
        if (this.activeMinePos == null || this.activeMineFace < 0) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                this.activeMinePos,
                (byte) this.activeMineFace,
                false,
                (byte) toolSlot));
        this.activeMinePos = null;
        this.activeMineFace = -1;
    }

    private void clearSelectedItemOnly() {
        setSelectedItem("", "", ItemStack.EMPTY);
    }

    private void clearSelectedFluid() {
        setSelectedFluid("", "", ItemStack.EMPTY);
    }

    private void setSelectedItem(String itemId, String label, ItemStack preview) {
        this.selectedItemId = itemId == null ? "" : itemId;
        this.selectedItemLabel = label == null ? "" : label;
        this.selectedItemPreview = preview == null ? ItemStack.EMPTY : preview;
    }

    private void setSelectedFluid(String fluidId, String label, ItemStack preview) {
        this.selectedFluidId = fluidId == null ? "" : fluidId;
        this.selectedFluidLabel = label == null ? "" : label;
        this.selectedFluidPreview = preview == null ? ItemStack.EMPTY : preview;
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

        this.localMirrorCamera.snapTo(this.localX, this.localY, this.localZ, this.localYawDeg, this.localPitchDeg);

        if (minecraft.getCameraEntity() != this.localMirrorCamera) {
            minecraft.setCameraEntity(this.localMirrorCamera);
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

    private void applyLocalPrediction(float forward, float strafe, float panX, float panY, float rotateX, float rotateY,
            float scroll, int rotateSteps, boolean fast) {
        this.localYawDeg += rotateX * ROTATE_GAIN_X;
        if (rotateSteps != 0) {
            this.localYawDeg = snapQuarter(this.localYawDeg + (90.0F * rotateSteps));
        }
        this.localPitchDeg = Mth.clamp(this.localPitchDeg + (rotateY * ROTATE_GAIN_Y), 25.0F, 85.0F);

        double speed = fast ? 0.80D : 0.45D;
        double yawRad = Math.toRadians(this.localYawDeg);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetX = this.localX;
        double targetY = this.localY;
        double targetZ = this.localZ;

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
        double distSqr = adx * adx + adz * adz;
        double maxSqr = this.maxRadius * this.maxRadius;
        if (distSqr > maxSqr) {
            double dist = Math.sqrt(distSqr);
            double scale = this.maxRadius / dist;
            targetX = this.anchorX + (adx * scale);
            targetZ = this.anchorZ + (adz * scale);
        }

        targetY = Mth.clamp(targetY, this.anchorY + 4.0D, this.anchorY + 80.0D);

        Vec3 toCam = new Vec3(targetX - this.anchorX, targetY - this.anchorY, targetZ - this.anchorZ);
        double dist = toCam.length();
        if (dist > 1.0e-6) {
            double clamped = Mth.clamp(dist, 8.0D, 72.0D);
            if (Math.abs(clamped - dist) > 1.0e-4) {
                Vec3 n = toCam.scale(clamped / dist);
                targetX = this.anchorX + n.x;
                targetY = this.anchorY + n.y;
                targetZ = this.anchorZ + n.z;
            }
        }

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

    public record CraftableEntry(
            ItemStack stack,
            String recipeId,
            String itemId,
            int resultCount,
            boolean craftable,
            String missingSummary,
            String mod,
            String name) {
    }

    public enum BuildShape {
        BLOCK,
        LINE,
        SQUARE,
        WALL,
        CIRCLE,
        BOX
    }
}



