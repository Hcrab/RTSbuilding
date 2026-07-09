package com.rtsbuilding.rtsbuilding.client.screen;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.state.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialWindowPanel;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintNameWindowPanel;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuideTypes;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionWheelPanel;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.UltimineMode;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.UltiminePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;
/**
 * The main RTS Builder screen ??the primary UI entry point for the RTS building mode.
 * <p>
 * This screen overlays the Minecraft game view and provides all RTS functionality
 * including quick building, vein-mining (ultimine), item storage browsing, the
 * interaction wheel, shape-based building, blueprint placement, guide panels,
 * the gear/settings menu, and associated UI interactions.
 * <p>
 * The screen layout is divided into three main regions:
 * <ul>
 *   <li><b>Top Bar:</b> Mode switching, action buttons, shape selection, guide entry.</li>
 *   <li><b>Bottom Panel:</b> Item storage grid, crafting panel, blueprint panel.</li>
 *   <li><b>Overlays:</b> Interaction wheel, gear/settings menu, guide
 *       panel, dialogs (name entry, material list, craft quantity).</li>
 * </ul>
 * <p>
 * This class interacts with game logic through {@link ClientRtsController}.
 * All actual building operations are delegated to the server. UI state is
 * persisted via {@link RtsClientUiStateStore}.
 * <p>
 * <b>Design notes:</b> This is the central screen class that manages all UI
 * interaction. To keep the code maintainable, layout constants are extracted
 * into {@link BuilderScreenConstants}, geometry calculations are delegated to
 * {@link ShapeGeometryUtil}, and shape/blueprint preview models are extracted
 * as standalone records.
 *
 * @see ClientRtsController
 * @see BuilderScreenConstants
 * @see ShapeGeometryUtil
 */
public final class BuilderScreen extends Screen {
    /** The central controller that bridges the screen with game logic and server communication. */
    private final ClientRtsController controller;
    /** Search box for filtering storage items. */
    private EditBox searchBox;
    /** Search box for filtering craftable entries in the crafting panel. */
    private EditBox craftSearchBox;
    /** Panel showing items queued in the funnel buffer (item collection mode). */
    private final FunnelBufferPanel funnelBufferPanel = new FunnelBufferPanel();
    /** Panel for quick-build remote placement (place items from storage at a distance). */
    private final QuickBuildPanel quickBuildPanel = new QuickBuildPanel();
    /** Panel for configuring and triggering vein-mining (ultimine) operations. */
    private final UltiminePanel ultiminePanel = new UltiminePanel();
    /** Windowed blueprint capture/placement controls. */
    private final BlueprintWindowPanel blueprintWindowPanel = new BlueprintWindowPanel();
    /** Windowed blueprint save/rename name prompt. */
    private final BlueprintNameWindowPanel blueprintNameWindowPanel = new BlueprintNameWindowPanel();
    /** Windowed blueprint material details prompt. */
    private final BlueprintMaterialWindowPanel blueprintMaterialWindowPanel = new BlueprintMaterialWindowPanel();
    /** Client-side range-culling editor state. */
    private final RtsCullingManager cullingManager = RtsCullingClientState.persistentManager();
    /** Windowed range-culling management controls. */
    private final RtsCullingPanel cullingPanel = new RtsCullingPanel(this.cullingManager);
    /** Top bar panel with mode buttons, shape selection, and action controls. */
    private final TopBarPanel topBarPanel = new TopBarPanel();
    /** Bottom panel containing storage grid, crafting, blueprints, and pin slots. */
    private final BottomPanel bottomPanel = new BottomPanel();
    /** 紧凑显示玩家生命、饥饿、护甲和吸收状态的渲染器。 */
    private final PlayerStatusRenderer playerStatusRenderer = new PlayerStatusRenderer(this);
    /** Controller managing shape-building sessions (geometry, fill mode, rotation, undo/redo). */
    private final ScreenShapeController shapeController = new ScreenShapeController();
    /** Picker for raycasting blocks, entities, and blueprint placement targets from the cursor. */
    private final ScreenCursorPicker cursorPicker = new ScreenCursorPicker();
    /** Handler for camera movement, drag rotation, panning, and mining actions. */
    private final CameraInputHandler cameraInput = new CameraInputHandler();
    /** Guide/onboarding panel that explains UI elements and controls. */
    private final GuidePanel guidePanel = new GuidePanel();
    /** Gear (settings) menu panel with configuration toggles and sliders. */
    private final GearMenuPanel gearMenuPanel = new GearMenuPanel();
    /** Radial interaction wheel for advanced block/entity interactions. */
    private final InteractionWheelPanel interactionWheelPanel = new InteractionWheelPanel();
    /** Window showing active server workflow progress and pause/cancel actions. */
    private final RtsWorkflowPanel workflowPanel = new RtsWorkflowPanel();
    /** 挂起放置任务恢复前的扫描确认窗口。 */
    private final RtsResumePlacementPanel resumePlacementPanel = new RtsResumePlacementPanel();
    /** Front-to-back input routing for movable RTS windows. */
    private final RtsFloatingWindowLayer floatingWindowLayer;
    /** Whether the user is currently dragging the input sensitivity slider. */
    private boolean draggingInputSensitivity = false;
    /** Timestamp (System.currentTimeMillis) when the last damage flash was triggered, or -1 if none. */
    private long damageFlashStartMs = -1L;
    /** Whether the funnel hotkey (quick-activate funnel mode) is currently held down. */
    private boolean funnelHotkeyHeld = false;
    /** The builder mode that was active before the funnel hotkey was pressed, for restoration on release. */
    private BuilderMode modeBeforeFunnelHotkey = BuilderMode.INTERACT;
    /** Tracks whether the native GLFW cursor has been hidden (e.g. for funnel cursor display). */
    private boolean nativeCursorHidden = false;
    /** Current native cursor style when not hidden. */
    private RtsWindowPanel.ResizeCursor nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;
    /** Whether we are currently inside a fixed-RTS-scale render pass (for UI scaling). */
    private boolean fixedRtsScaleRenderPass = false;
    /** Whether we are currently inside a fixed-RTS-scale input pass (for UI scaling). */
    private boolean fixedRtsScaleInputPass = false;
    /** The actual render scale factor active during the current fixed-scale render pass. */
    private double activeRtsGuiRenderScale = 1.0D;
    /** The user-configured fixed RTS GUI scale (independent of Minecraft's native GUI scale). */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;
    /** Stable hover anchor above the left "RTS" label; keeps item tooltips from chasing the cursor. */
    private static final int LEFT_TOOLTIP_X_OFFSET = 8;
    private static final int LEFT_TOOLTIP_Y_OFFSET = 24;
    private static final int LEFT_TOOLTIP_DETAIL_Y_OFFSET = 18;
    /** Last recorded mouse X position, updated each render frame for input consistency. */
    private int lastMouseX = 0;
    /** Last recorded mouse Y position, updated each render frame for input consistency. */
    private int lastMouseY = 0;
    /** Ctrl+右键移动玩家的最近点击时间，用于识别双击精准降落。 */
    private long lastCtrlRightClickTime = 0L;
    private static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300L;
    /**
     * When >= 0, the player is in "GUI binding" mode and must click a block in the world
     * to bind it to the specified slot. Reset to -1 after binding or on cancel.
     */
    private int pendingGuiBindSlot = -1;
    /**
     * Constructs the main RTS Builder screen.
     *
     * @param controller the central client-side RTS controller for bridging screen and game logic
     */
    public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
        this.floatingWindowLayer = new RtsFloatingWindowLayer(
                this.gearMenuPanel,
                this.guidePanel,
                this.blueprintNameWindowPanel,
                this.blueprintMaterialWindowPanel,
                this.blueprintWindowPanel,
                this.resumePlacementPanel,
                this.workflowPanel,
                this.cullingPanel,
                this.quickBuildPanel);
        this.guidePanel.init(this, this.controller);
        this.gearMenuPanel.init(this, this.controller);
        this.blueprintWindowPanel.init(this, this.controller);
        this.blueprintNameWindowPanel.init(this, this.controller);
        this.blueprintMaterialWindowPanel.init(this, this.controller);
        this.cullingPanel.init(this, this.controller);
        this.workflowPanel.init(this, this.controller);
        this.resumePlacementPanel.init(this, this.controller);
        this.interactionWheelPanel.init(this, this.controller);
        this.funnelBufferPanel.init(this, this.controller);
        this.quickBuildPanel.init(this, this.controller);
        this.ultiminePanel.init(this, this.controller);
        this.topBarPanel.init(this, this.controller);
        this.bottomPanel.init(this, this.controller);
        this.shapeController.init(this, this.controller);
        this.cursorPicker.init(this, this.controller, this.shapeController);
        this.cameraInput.init(this, this.controller);
        RtsCullingClientState.setActiveManager(this.cullingManager);
    }
    /** Returns the Minecraft font renderer for use by sub-panels and utilities. */
    public Font font() {
        return this.font;
    }
    /** 返回顶部栏底部 Y 坐标，供浮动面板避让顶部操作区。 */
    public int topBarBottomY() {
        return TOP_H;
    }
    /** 返回挂起放置任务的恢复确认窗口。 */
    public RtsResumePlacementPanel getResumePlacementPanel() {
        return this.resumePlacementPanel;
    }
    /** Triggers a red flash overlay on the screen to indicate the player took damage while in RTS mode. */
    public void triggerDamageFlash() {
        this.damageFlashStartMs = System.currentTimeMillis();
    }
    /** Sets which funnel buffer entry is currently hovered, for tooltip rendering. */
    public void setHoveredFunnelBufferEntry(int index) {
        this.funnelBufferPanel.setHoveredEntry(index);
    }
    /** 切换容器覆盖层是否显示。 */
    public void toggleContainerOverlayEnabled() {
        RtsClientUiStateStore.setContainerOverlayEnabled(!RtsClientUiStateStore.isContainerOverlayEnabled());
    }
    /** 切换覆盖层 Shift 导入是否启用。 */
    public void toggleOverlayShiftImportEnabled() {
        RtsClientUiStateStore.setOverlayShiftImportEnabled(!RtsClientUiStateStore.isOverlayShiftImportEnabled());
    }
    /** 切换存储刷新完成后的“已就绪”提示。 */
    public void toggleShowStorageReadyPopup() {
        RtsClientUiStateStore.setShowStorageReadyPopupEnabled(!RtsClientUiStateStore.isShowStorageReadyPopupEnabled());
        if (!RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            clearStorageScanPopupState();
        }
    }
    public void clearStorageScanPopupState() {
        this.controller.clearStorageScanPopupState();
    }
    /** 切换安静刷新模式。 */
    public void toggleStorageRefreshQuietEnabled() {
        RtsClientUiStateStore.setStorageRefreshQuietEnabled(!RtsClientUiStateStore.isStorageRefreshQuietEnabled());
    }
    /** 切换存储自动刷新。 */
    public void toggleStorageAutoRefreshEnabled() {
        RtsClientUiStateStore.setStorageAutoRefreshEnabled(!RtsClientUiStateStore.isStorageAutoRefreshEnabled());
    }
    /** 切换工作流进度面板显示。 */
    public void toggleShowWorkflowPanelEnabled() {
        RtsClientUiStateStore.setShowWorkflowPanelEnabled(!RtsClientUiStateStore.isShowWorkflowPanelEnabled());
    }
    /** Returns whether the user is currently dragging the input sensitivity slider. */
    public boolean isDraggingInputSensitivity() {
        return this.draggingInputSensitivity;
    }
    /** Returns the current shape fill mode (e.g. FILL, HOLLOW, WIREFRAME). Delegates to the shape controller. */
    public ShapeBuildTypes.ShapeFillMode getShapeFillMode() {
        return this.shapeController.getShapeFillMode();
    }
    /** Sets the shape fill mode via the shape controller. */
    public void setShapeFillMode(ShapeBuildTypes.ShapeFillMode mode) {
        this.shapeController.setShapeFillMode(mode);
    }
    /** Returns the current shape rotation in degrees. Delegates to the shape controller. */
    public int getShapeRotateDegrees() {
        return this.shapeController.getShapeRotateDegrees();
    }
    /** Clears the current shape build session (pending preview, undo/redo history). */
    public void clearShapeBuildSession() {
        this.shapeController.clearShapeBuildSession();
    }
    /** Rotates the current shape by a fixed number of steps (positive = clockwise, negative = counter-clockwise). */
    public void rotateShapeByStep(int step) {
        this.shapeController.rotateShapeByStep(step);
    }
    /** Returns the ghost preview data for the current shape build (used for world overlay rendering). */
    public ShapeDataRecords.GhostPreview getShapeGhostPreview() {
        return this.shapeController.getShapeGhostPreview();
    }
    /** Returns confirmed range-destroy work areas that should remain visible during mining. */
    public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        return this.shapeController.getConfirmedRangeDestroyPreviews();
    }
    /** Ensures the current fill mode is compatible with the given shape type, adjusting if necessary. */
    public void ensureFillModeForShape(ClientRtsController.BuildShape shape) {
        this.shapeController.ensureFillModeForShape(shape);
    }
    /** Returns whether the quick-build panel is currently open. */
    public boolean isQuickBuildOpen() {
        return canUseQuickBuild() && this.quickBuildPanel.isQuickBuildOpen();
    }
    /** Opens or closes the quick-build panel. */
    public void setQuickBuildOpen(boolean open) {
        if (open && !canUseQuickBuild()) {
            showQuickBuildLockedMessage();
            this.quickBuildPanel.setQuickBuildOpen(false);
            return;
        }
        this.quickBuildPanel.setQuickBuildOpen(open);
    }
    /** Returns whether quick-build/remote shape placement is unlocked for the player. */
    public boolean canUseQuickBuild() {
        return hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE);
    }
    /** Syncs active shape state so a hidden or locked quick-build panel cannot affect normal placement. */
    public void syncQuickBuildActiveState() {
        if (!this.quickBuildPanel.isQuickBuildOpen() || !canUseQuickBuild()) {
            this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
            this.shapeController.clearShapeBuildSession();
            ensureFillModeForShape(ClientRtsController.BuildShape.BLOCK);
            return;
        }
        ensureFillModeForShape(this.controller.getBuildShape());
    }
    /** Shows the remote-placement locked hint without covering the RTS panels. */
    public void showQuickBuildLockedMessage() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("message.rtsbuilding.quick_build.remote_place_locked"), true);
        }
    }
    /** Returns the Minecraft client instance for access by sub-panels and utilities. */
    public net.minecraft.client.Minecraft getMinecraft() {
        return this.minecraft;
    }
    /** Gives render/input helpers read-only access to open RTS floating windows. */
    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }
    /** Returns the last recorded mouse X position (updated each render frame). */
    public double getCurrentMouseX() {
        return this.lastMouseX;
    }
    /** Returns the last recorded mouse Y position (updated each render frame). */
    public double getCurrentMouseY() {
        return this.lastMouseY;
    }
    /** Returns whether the retired shape-selection wheel overlay is currently open. */
    public boolean isShapeWheelOpen() {
        return false;
    }
    /** Returns whether the interaction wheel overlay is currently open. */
    public boolean isInteractionWheelOpen() {
        return this.interactionWheelPanel.isOpen();
    }
    /** Retained for old extracted callers; the Alt shape wheel is retired. */
    public void handleShapeWheelAltRelease(double mouseX, double mouseY) {
    }
    /** Returns the storage search box (for filtering items in the storage grid). */
    public EditBox getSearchBox() {
        return this.searchBox;
    }
    /** Returns the craftable-items search box (for filtering in the crafting panel). */
    public EditBox getCraftSearchBox() {
        return this.craftSearchBox;
    }
    @Override
    /** Initialises the screen: creates search boxes, applies persisted UI state, and requests craftables. */
    protected void init() {
        super.init();
        applyStoredUiState();
        this.searchBox = new EditBox(this.font, 8, this.height - 52, 150, 14, Component.literal("Search"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setBordered(true);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.setValue(this.controller.getStorageSearch());
        this.craftSearchBox = new EditBox(this.font, 8, this.height - 52, 74, 10, Component.literal("Craft Search"));
        this.craftSearchBox.setMaxLength(128);
        this.craftSearchBox.setBordered(false);
        this.craftSearchBox.setCanLoseFocus(true);
        this.craftSearchBox.setTextColor(0xEAF2FF);
        this.craftSearchBox.setTextColorUneditable(0xAAB8C8);
        if (this.bottomPanel.craftSearchDraft == null) {
            this.bottomPanel.craftSearchDraft = this.controller.getCraftablesSearch();
        }
        this.craftSearchBox.setValue(this.bottomPanel.craftSearchDraft);
        this.craftSearchBox.setResponder(value -> this.bottomPanel.craftSearchDraft = value == null ? "" : value);
        this.controller.requestCraftables();
    }
    @Override
    /** Prevents the game from pausing when the RTS screen is open (since it is an overlay, not a menu). */
    public boolean isPauseScreen() {
        return false;
    }
    @Override
    /** Pressing Escape closes this screen and returns to normal gameplay. */
    public boolean shouldCloseOnEsc() {
        return true;
    }
    @Override
    /**
     * Called when the screen is closed. Cleans up UI state: closes wheels, persists state,
     * resets input handlers, disables funnel mode, toggles camera if needed, and restores cursor.
     */
    public void onClose() {
        closeInteractionWheel();
        closeShapeWheel();
        this.shapeController.clearShapeBuildSession();
        persistUiState();
        this.pendingGuiBindSlot = -1;
        this.funnelHotkeyHeld = false;
        this.cameraInput.resetCameraVerticalHeld();
        this.cameraInput.stopActiveMining();
        if (this.controller.isFunnelEnabled()) {
            this.controller.setFunnelEnabled(false);
        }
        if (this.controller.isEnabled()) {
            RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
        }
        this.bottomPanel.craftQuantityDialog.close();
        RtsCullingClientState.clearActiveManager(this.cullingManager);
        updateNativeCursorVisibility(false);
    }
    @Override
    /* Called when the screen is fully removed from the display stack. Resets camera vertical input and cursor. */
    public void removed() {
        super.removed();
        this.cameraInput.resetCameraVerticalHeld();
        RtsCullingClientState.clearActiveManager(this.cullingManager);
        updateNativeCursorVisibility(false);
    }
    @Override
    /*
      Called every client tick. Updates shape state,
      updates funnel target position, syncs craftables panel state, and checks if
      active mining input is still held (stopping if released).
     */
    public void tick() {
        super.tick();
        enforceBlueprintPlacementModeLock();
        this.shapeController.updateAltShapeWheelLifecycle();
        if (this.controller.getMode() == BuilderMode.FUNNEL && this.controller.isFunnelEnabled()) {
            BlockHitResult hit = this.cursorPicker.pickBlockHit();
            if (hit != null) {
                this.controller.updateFunnelTarget(hit.getBlockPos());
            }
        }
        this.bottomPanel.syncCraftablesPanelState();
        if (!this.cameraInput.isLeftMiningActive()) {
            return;
        }
        if (this.minecraft == null || !this.controller.isEnabled()) {
            this.cameraInput.stopActiveMining();
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        boolean miningInputDown = this.cameraInput.isKeyboardMining()
                ? ClientKeyMappings.ACTION_BREAK.isDown()
                : this.cameraInput.getActiveMiningMouseButton() >= 0
                        && GLFW.glfwGetMouseButton(window, this.cameraInput.getActiveMiningMouseButton()) == GLFW.GLFW_PRESS;
        if (!miningInputDown) {
            this.cameraInput.stopActiveMining();
            return;
        }
    }
    @Override
    /*
      Handles mouse click input with RTS GUI scale remapping. Routes clicks through
      the various UI components in priority order: quantity dialog, blueprint dialogs,
      capture mode, ultimine editing, home selection, shape wheel, interaction wheel,
      guide panel, gear menu, and world interaction (building, linking, rotating, etc.).

      @return true if the click was consumed by this screen, false otherwise
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    return mouseClicked(mouseX / frame.scale(), mouseY / frame.scale(), button);
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        enforceBlueprintPlacementModeLock();
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            boolean handled = this.bottomPanel.craftQuantityDialog.mouseClicked(mouseX, mouseY, button, this.width, this.height);
            this.bottomPanel.submitCraftQuantityDialogIfReady();
            return handled;
        }
        if (this.floatingWindowLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (BlueprintPanel.isCaptureModeActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.cameraInput.stopActiveMining();
                if (isWorldArea(mouseX, mouseY)) {
                    BlockHitResult hit = this.cursorPicker.pickBlockHit();
                    BlueprintPanel.handleCaptureWorldAction(
                            hit,
                            this.cursorPicker.currentRayOrigin(),
                            this.cursorPicker.computeCursorRayDirection());
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                return false;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && this.ultiminePanel.isLimitEditing()
                && !this.ultiminePanel.isInsideLimitInput(mouseX, mouseY)) {
            this.ultiminePanel.commitEdit();
        }
        if (this.controller.isHomeSelectionMode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null) {
                    this.controller.setHome(hit.getBlockPos());
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
                return true;
            }
            return true;
        }
        if (this.interactionWheelPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.guidePanel.isOpen() || this.gearMenuPanel.isOpen()) {
            return true;
        }
        if (handleRangeCullingSelectionClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.topBarPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.funnelBufferPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.bottomPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.pendingGuiBindSlot >= 0 && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null) {
                    this.controller.setGuiBinding(
                            this.pendingGuiBindSlot,
                            hit.getBlockPos(),
                            hit.getDirection(),
                            resolveGuiBindingItemId(hit));
                    this.pendingGuiBindSlot = -1;
                }
                return true;
            }
            if (isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.LINK_STORAGE) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null) {
                    this.controller.linkStorage(hit.getBlockPos());
                    return true;
                }
            }
        }
        if (handleBatchConfirmMouse(mouseX, mouseY, button)) {
            return true;
        }
        if (CameraInputHandler.isBreakActionMouse(button)
                && CameraInputHandler.canStartBreakActionOnMouse(button)
                && this.cameraInput.startMiningAt(mouseX, mouseY, button, false)) {
            return true;
        }
        boolean primaryMouse = CameraInputHandler.isPrimaryActionMouse(button);
        boolean rotateMouse = CameraInputHandler.isRotateDragActionMouse(button);
        if (primaryMouse || rotateMouse) {
            if (isSearchFocused()) {
                blurSearchFocus();
            }
            if (primaryMouse && this.pendingGuiBindSlot >= 0 && isWorldArea(mouseX, mouseY)) {
                return true;
            }
            if (primaryMouse && !rotateMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.LINK_STORAGE) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null) {
                    this.controller.linkStorage(hit.getBlockPos(), false);
                }
                return true;
            }
            if (primaryMouse && isInsideBottomPanel(mouseX, mouseY)) {
                return this.bottomPanel.handleRightClick(mouseX, mouseY);
            }
            if (primaryMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.ROTATE && !rotateMouse) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null) {
                    clearShapeBuildSession();
                    this.controller.rotateBlock(hit.getBlockPos());
                }
                return true;
            }
            if (isMovePlayerActionMouse(button) && isWorldArea(mouseX, mouseY)) {
                return handleMovePlayerActionAt(mouseX, mouseY);
            }
            if (isWorldArea(mouseX, mouseY)) {
                this.cameraInput.beginRightPress(mouseX, mouseY, button, primaryMouse, rotateMouse);
                return true;
            }
            return true;
        }
        boolean panMouse = CameraInputHandler.isPanDragActionMouse(button);
        boolean pickMouse = CameraInputHandler.isPickBlockActionMouse(button);
        if (panMouse || pickMouse) {
            this.cameraInput.beginMiddlePress(isWorldArea(mouseX, mouseY), button, panMouse, pickMouse);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleBatchConfirmMouse(double mouseX, double mouseY, int button) {
        if (!Config.isKeyboardBatchConfirmEnabled() || !isWorldArea(mouseX, mouseY) || isSearchFocused()) {
            return false;
        }
        InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
        if (this.shapeController.isAwaitingBatchDestroyConfirm()
                && ClientKeyMappings.CONFIRM_BATCH_DESTROY.isActiveAndMatches(mouseKey)) {
            this.shapeController.tryConfirmPendingRangeDestroy();
            return true;
        }
        if (this.shapeController.isAwaitingBatchPlaceConfirm()
                && ClientKeyMappings.CONFIRM_BATCH_PLACE.isActiveAndMatches(mouseKey)) {
            this.shapeController.tryConfirmPendingShapeBuild(hasShiftDown());
            return true;
        }
        return false;
    }

    @Override
    /**
     * Handles mouse release with RTS GUI scale remapping. Routes release events to
     * open dialogs, dragging state, wheel panels, and camera input handlers.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    return mouseReleased(mouseX / frame.scale(), mouseY / frame.scale(), button);
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return true;
        }
        if (this.draggingInputSensitivity) {
            this.draggingInputSensitivity = false;
            return true;
        }
        if (this.interactionWheelPanel.isOpen()) {
            return true;
        }
        if (this.cameraInput.isLeftMiningActive() && !this.cameraInput.isKeyboardMining() && button == this.cameraInput.getActiveMiningMouseButton()) {
            this.cameraInput.stopActiveMining();
            return true;
        }
        if (this.floatingWindowLayer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (this.guidePanel.isOpen() || this.gearMenuPanel.isOpen()) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && BlueprintPanel.releaseCaptureActiveHandleIfDragged()) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && this.cullingManager.isManagementMode()
                && this.cullingManager.releaseActiveHandleIfDragged()) {
            return true;
        }
        if (this.cameraInput.isRightDragActive(button)) {
            return this.cameraInput.endRightPress(mouseX, mouseY, button)
                    ? runPrimaryActionAt(mouseX, mouseY, button)
                    : true;
        }
        if (this.cameraInput.endMiddlePress(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override
    /**
     * Handles mouse drag with RTS GUI scale remapping. Routes drag events to
     * open dialogs, sensitivity slider dragging, wheel panels, camera drag handlers,
     * and search box focus logic.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    return mouseDragged(mouseX / frame.scale(), mouseY / frame.scale(), button, dragX / frame.scale(), dragY / frame.scale());
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return true;
        }
        if (this.draggingInputSensitivity) {
            this.cameraInput.updateInputSensitivityFromMouse(mouseX);
            return true;
        }
        if (this.interactionWheelPanel.isOpen()) {
            return true;
        }
        if (this.floatingWindowLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (this.guidePanel.isOpen() || this.gearMenuPanel.isOpen()) {
            return true;
        }
        if (handleBoxHandleDrag(button, dragX, dragY)) {
            return true;
        }
        if (this.cameraInput.handleRightDrag(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (this.cameraInput.handleMiddleDrag(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (this.cameraInput.handleKeyboardPanDragAt(mouseX, mouseY, dragX, dragY)) {
            return true;
        }
        if (isSearchFocused()) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override
    /** Handles mouse movement with RTS GUI scale remapping. Updates keyboard-pan drag state. */
    public void mouseMoved(double mouseX, double mouseY) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    mouseMoved(mouseX / frame.scale(), mouseY / frame.scale());
                    return;
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        this.cameraInput.updateKeyboardPanDrag(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

   /** Returns whether the camera "move up" action is currently held (e.g. via keybind). */
    public boolean isCameraUpActionHeld() {
        return this.cameraInput.isCameraUpActionHeld();
    }
   /** Returns whether the camera "move down" action is currently held (e.g. via keybind). */
    public boolean isCameraDownActionHeld() {
        return this.cameraInput.isCameraDownActionHeld();
    }
    /**
     * Executes the primary build/interact action at the given screen coordinates.
     * This is the main action route for left-click / primary-keybind:
     * handles GUI binding, blueprint capture, storage linking, funnel, rotation,
     * interaction wheel opening, shape placement confirmation, blueprint placement,
     * and regular block/entity interactions.
     *
     * @param mouseX screen X coordinate
     * @param mouseY screen Y coordinate
     * @return true if the action was consumed
     */
    private boolean runPrimaryActionAt(double mouseX, double mouseY) {
        return runPrimaryActionAt(mouseX, mouseY, -1);
    }
    /**
     * Executes the primary build/interact action at the given screen coordinates.
     * Overload that accepts a specific mouse button for storage linking distinction.
     *
     * @param mouseX     screen X coordinate
     * @param mouseY     screen Y coordinate
     * @param mouseButton the GLFW mouse button that triggered the action, or -1 if keyboard-triggered
     * @return true if the action was consumed
     */
    private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton) {
        enforceBlueprintPlacementModeLock();
        if (this.pendingGuiBindSlot >= 0) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                BlueprintPanel.handleCaptureWorldAction(
                        hit,
                        this.cursorPicker.currentRayOrigin(),
                        this.cursorPicker.computeCursorRayDirection());
            }
            return true;
        }
        if (isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleRightClick(mouseX, mouseY);
        }
        if (!isWorldArea(mouseX, mouseY)) {
            return true;
        }
        if (this.controller.getMode() == BuilderMode.LINK_STORAGE) {
            this.shapeController.clearShapeBuildSession();
            BlockHitResult hit = this.cursorPicker.pickBlockHit();
            if (hit != null) {
                this.controller.linkStorage(hit.getBlockPos(), mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            return true;
        }
        if (this.controller.getMode() == BuilderMode.FUNNEL) {
            this.shapeController.clearShapeBuildSession();
            return true;
        }
        if (this.controller.getMode() == BuilderMode.ROTATE) {
            InteractionTypes.InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
            if (target != null && target.blockHit() != null) {
                this.shapeController.clearShapeBuildSession();
                this.controller.rotateBlock(target.blockHit().getBlockPos());
            }
            return true;
        }
        boolean forcePlace = hasShiftDown();
        boolean rangeDestroyMode = isQuickBuildRangeDestroyMode();
        if (!rangeDestroyMode && this.shapeController.isAwaitingBatchPlaceConfirm()) {
            if (Config.isKeyboardBatchConfirmEnabled()) {
                return true;
            }
            return this.shapeController.tryConfirmPendingShapeBuild(forcePlace);
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.hasSelectedBlueprint()) {
            if (BlueprintPanel.hasPinnedPreview()) {
                BlueprintPanel.confirmPinnedPreview();
                return true;
            }
            BlockHitResult blueprintHit = this.cursorPicker.pickBlueprintPlacementHit();
            if (blueprintHit != null) {
                BlockPos anchor = this.cursorPicker.resolveBlueprintAnchor(blueprintHit);
                if (anchor != null) {
                    BlueprintPanel.pinSelected(anchor);
                }
            }
            return true;
        }
        InteractionTypes.InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
        if (target == null) {
            tryUseMainHandItemInAir();
            return true;
        }
        if (this.controller.hasSelectedFluid()) {
            if (target.blockHit() != null) {
                if (rangeDestroyMode) {
                    this.controller.placeSelectedFluid(target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
                } else {
                    this.shapeController.placeWithShape(
                            target.blockHit(),
                            forcePlace,
                            target.rayOrigin(),
                            target.rayDir(),
                            mouseY,
                            true,
                            InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                            "",
                            -1);
                }
            }
            return true;
        }
        if (this.controller.hasSelectedItem()) {
            if (target.isEntityTarget()) {
                this.shapeController.clearShapeBuildSession();
                this.controller.interactEntityWithPinnedItem(
                        target.entityId(),
                        target.hitLocation(),
                        this.controller.getSelectedItemId(),
                        target.rayOrigin(),
                        target.rayDir());
            } else if (target.blockHit() != null) {
                if (!forcePlace && !rangeDestroyMode && this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK) {
                    this.shapeController.clearShapeBuildSession();
                    this.controller.interactBlockWithPinnedItem(
                            target.blockHit(),
                            this.controller.getSelectedItemId(),
                            target.rayOrigin(),
                            target.rayDir());
                    return true;
                }
                if (rangeDestroyMode) {
                    this.controller.placeSelected(target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
                    this.shapeController.recordSinglePlacementForUndo(
                            target.blockHit(),
                            InteractionTypes.PlacementReplayKind.PIN_ITEM,
                            this.controller.getSelectedItemId(),
                            -1);
                    return true;
                }
                this.shapeController.placeWithShape(
                        target.blockHit(),
                        forcePlace,
                        target.rayOrigin(),
                        target.rayDir(),
                        mouseY,
                        false,
                        InteractionTypes.PlacementReplayKind.PIN_ITEM,
                        this.controller.getSelectedItemId(),
                        -1);
            }
            return true;
        }
        if (target.blockHit() != null
                && this.controller.getBuildShape() != ClientRtsController.BuildShape.BLOCK
                && !rangeDestroyMode
                && canUseToolSlotShapeSource()) {
            this.shapeController.placeWithShape(
                    target.blockHit(),
                    forcePlace,
                    target.rayOrigin(),
                    target.rayDir(),
                    mouseY,
                    false,
                    InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                    "",
                    getSelectedToolSlot());
            return true;
        }
        this.shapeController.clearShapeBuildSession();
        if (this.controller.isEmptyHandSelected()) {
            if (target.isEntityTarget()) {
                this.controller.interactEntityEmpty(
                        target.entityId(),
                        target.hitLocation(),
                        target.rayOrigin(),
                        target.rayDir());
            } else if (target.blockHit() != null) {
                this.controller.interactEmpty(target.blockHit(), target.rayOrigin(), target.rayDir());
            }
            return true;
        }
        if (target.isEntityTarget()) {
            if (hasMainHandItem()) {
                this.controller.interactEntityWithToolSlot(
                        target.entityId(),
                        target.hitLocation(),
                        getSelectedToolSlot(),
                        target.rayOrigin(),
                        target.rayDir());
            }
        } else if (target.blockHit() != null) {
            if (hasMainHandItem()) {
                if (forcePlace) {
                    this.controller.placeSelected(target.blockHit(), true, target.rayOrigin(), target.rayDir());
                    this.shapeController.recordSinglePlacementForUndo(
                            target.blockHit(),
                            InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                            "",
                            getSelectedToolSlot());
                } else {
                    this.controller.interactBlockWithToolSlot(
                            target.blockHit(),
                            getSelectedToolSlot(),
                            target.rayOrigin(),
                            target.rayDir());
                }
            } else {
                this.controller.interactEmpty(target.blockHit(), target.rayOrigin(), target.rayDir());
            }
        }
        return true;
    }

    private boolean tryUseMainHandItemInAir() {
        if (!canUseMainHandItemInAir()) {
            return false;
        }
        InteractionTypes.InteractionTarget target = this.cursorPicker.pickItemAirInteractionTarget();
        if (target == null || target.blockHit() == null) {
            return false;
        }
        this.shapeController.clearShapeBuildSession();
        this.controller.useItemInAirWithToolSlot(
                target.blockHit(),
                getSelectedToolSlot(),
                target.rayOrigin(),
                target.rayDir());
        return true;
    }

    private boolean canUseMainHandItemInAir() {
        return hasMainHandItem()
                && !this.controller.hasSelectedItem()
                && !this.controller.hasSelectedFluid()
                && !this.controller.isEmptyHandSelected()
                && this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK;
    }

    /**
     * Handles mouse scroll with RTS GUI scale remapping. Routes scroll to open
     * dialogs, gear menu, wheel panels, guide panel, bottom panel, rotation mode,
     * and item slot scrolling.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return mouseScrolled(mouseX, mouseY, 0.0D, scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    return mouseScrolled(mouseX / frame.scale(), mouseY / frame.scale(), scrollX, scrollY);
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return this.bottomPanel.craftQuantityDialog.mouseScrolled(scrollY);
        }
        if (this.interactionWheelPanel.mouseScrolled(scrollY)) {
            return true;
        }
        if (this.floatingWindowLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (this.guidePanel.isOpen() || this.gearMenuPanel.isOpen()) {
            return true;
        }
        if (BlueprintPanel.mouseScrolledCaptureHeight(scrollY, isAltDown())) {
            return true;
        }
        if (this.cullingManager.isManagementMode()
                && (this.cullingManager.activeHandleDirection() != null || isWorldArea(mouseX, mouseY))
                && this.cullingManager.handleScroll(scrollY, isAltDown())) {
            return true;
        }
        if (isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleMouseScrolled(mouseX, mouseY, scrollY);
        }
        if (!isSearchFocused() && this.shapeController.handleShapeHeightMouseScrolled(scrollY)) {
            return true;
        }
        if (this.controller.getMode() == BuilderMode.ROTATE) {
            if (scrollY > 0.0D) {
                this.controller.rotatePlacementClockwise();
            } else if (scrollY < 0.0D) {
                this.controller.rotatePlacementCounterClockwise();
            }
            return true;
        }
        this.controller.queueScroll(scrollY);
        return true;
    }
    @Override
    /**
     * Handles key press events. Processes input for: quantity dialog, blueprint dialogs,
     * blueprint capture, home selection, ultimine editing, wheel panels, guide panel,
     * gear menu, undo/redo (Ctrl+Z/Y), shape height adjustments (Page Up/Down),
     * camera vertical movement, break action, pick block, primary action, mode switching,
     * funnel hotkey, quick drop, shape rotation, craft terminal, search focus management,
     * tool slot selection (1-9), quick-slot pinning, and input sensitivity adjustment.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            boolean handled = this.bottomPanel.craftQuantityDialog.keyPressed(keyCode, scanCode, modifiers);
            this.bottomPanel.submitCraftQuantityDialogIfReady();
            return handled;
        }
        if (this.floatingWindowLayer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (BlueprintPanel.isCaptureModeActive() && BlueprintPanel.keyPressed(keyCode, scanCode, this.controller)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                && BlueprintPanel.keyPressed(keyCode, scanCode, this.controller)) {
            return true;
        }
        if (this.controller.isHomeSelectionMode()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
            }
            return true;
        }
        if (this.ultiminePanel.isLimitEditing()) {
            return this.ultiminePanel.handleKeyPressed(keyCode);
        }
        if (this.interactionWheelPanel.keyPressed(keyCode)) {
            return true;
        }
        if (this.guidePanel.isOpen() || this.gearMenuPanel.isOpen()) {
            return true;
        }
        if (this.pendingGuiBindSlot >= 0 && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.pendingGuiBindSlot = -1;
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            return this.shapeController.undoLastPlacementBatch();
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            return this.shapeController.redoLastPlacementBatch();
        }
        if (!isSearchFocused()
                && this.shapeController.canAdjustCurrentShapeHeight()
                && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int delta = keyCode == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1;
            if (isAltDown()) {
                delta *= 4;
            }
            if (this.shapeController.adjustShapeHeightNudge(delta)) {
                return true;
            }
        }
        if (!isSearchFocused() && this.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, true)) {
            return true;
        }
        if (!isSearchFocused() && handleBatchConfirmKey(keyCode, scanCode)) {
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
            if (this.cameraInput.startMiningAt(currentMouseX(), currentMouseY(), -1, true)) {
                return true;
            }
        }
        if (!isSearchFocused() && ClientKeyMappings.PICK_BLOCK.matches(keyCode, scanCode)) {
            if (isWorldArea(currentMouseX(), currentMouseY())) {
                this.cameraInput.tryPickHoveredBlockForPlacement();
            }
            return true;
        }
        if (!isSearchFocused() && isMovePlayerActionKey(keyCode, scanCode)) {
            return handleMovePlayerActionAt(currentMouseX(), currentMouseY());
        }
        if (!isSearchFocused() && ClientKeyMappings.ACTION_PRIMARY.matches(keyCode, scanCode)) {
            return runPrimaryActionAt(currentMouseX(), currentMouseY());
        }
        if (!isSearchFocused() && handleModeKeyPressed(keyCode, scanCode)) {
            return true;
        }
        if (!isSearchFocused()
                && isBlueprintPlacementModeLocked()
                && ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode)) {
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode)) {
            activateFunnelHotkey();
            this.funnelHotkeyHeld = true;
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.QUICK_DROP.matches(keyCode, scanCode)) {
            quickDropSelectedAtCursor();
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.ROTATE_SHAPE.matches(keyCode, scanCode) && !hasControlDown()) {
            if (hasRecipeViewerLoaded()) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            this.shapeController.rotateShapeByStep(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (!isSearchFocused()
                && ClientKeyMappings.OPEN_CRAFT_TERMINAL.matches(keyCode, scanCode)
                && !hasControlDown()
                && hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)) {
            persistUiState();
            this.controller.openCraftTerminal();
            return true;
        }
        if (isSearchFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchBox != null && this.searchBox.isFocused()) {
                this.searchBox.setValue("");
                this.bottomPanel.handleStorageSearchChanged("");
                blurSearchFocus();
                return true;
            }
            if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
                this.bottomPanel.craftSearchDraft = "";
                this.craftSearchBox.setValue("");
                this.controller.setCraftablesSearch("");
                blurSearchFocus();
                return true;
            }
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                this.bottomPanel.handleStorageSearchChanged(this.searchBox.getValue());
            }
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.bottomPanel.applyCraftSearchDraft();
                blurSearchFocus();
                return true;
            }
            this.craftSearchBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (!isSearchFocused() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int slot = keyCode - GLFW.GLFW_KEY_1;
            setSelectedToolSlot(slot);
            this.controller.clearPlacementSelectionPreserveMode();
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.PIN_QUICK_SLOT.matches(keyCode, scanCode)) {
            if (this.bottomPanel.hoveredPinPageButton) {
                return true;
            }
            if (this.bottomPanel.hoveredPinIndex >= 0) {
                if (this.controller.hasSelectedItem()) {
                    this.controller.assignQuickSlotFromSelected(this.bottomPanel.hoveredPinIndex);
                    return true;
                }
                if (tryAssignQuickSlotFromToolSelection(this.bottomPanel.hoveredPinIndex)) {
                    return true;
                }
            }
        }
        if (ClientKeyMappings.DECREASE_SENSITIVITY.matches(keyCode, scanCode)) {
            this.controller.decreaseRotateSensitivity();
            return true;
        }
        if (ClientKeyMappings.INCREASE_SENSITIVITY.matches(keyCode, scanCode)) {
            this.controller.increaseRotateSensitivity();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleBatchConfirmKey(int keyCode, int scanCode) {
        if (!Config.isKeyboardBatchConfirmEnabled()) {
            return false;
        }
        if (this.shapeController.isAwaitingBatchDestroyConfirm()
                && ClientKeyMappings.CONFIRM_BATCH_DESTROY.matches(keyCode, scanCode)) {
            this.shapeController.tryConfirmPendingRangeDestroy();
            return true;
        }
        if (this.shapeController.isAwaitingBatchPlaceConfirm()
                && ClientKeyMappings.CONFIRM_BATCH_PLACE.matches(keyCode, scanCode)) {
            this.shapeController.tryConfirmPendingShapeBuild(hasShiftDown());
            return true;
        }
        return false;
    }

    @Override
    /** Handles key release for funnel hotkey and camera vertical movement states. */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.cameraInput.isLeftMiningActive()
                && this.cameraInput.isKeyboardMining()
                && ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
            this.cameraInput.stopActiveMining();
            return true;
        }
        if (ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode) && this.funnelHotkeyHeld) {
            this.funnelHotkeyHeld = false;
            deactivateFunnelHotkey();
            return true;
        }
        if (this.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, false)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    /**
     * Routes a key press to the appropriate builder mode switch based on keybind matching.
     *
     * @return true if a mode switch was performed
     */
    private boolean handleModeKeyPressed(int keyCode, int scanCode) {
        boolean modeKey = ClientKeyMappings.MODE_INTERACT.matches(keyCode, scanCode)
                || ClientKeyMappings.MODE_LINK_STORAGE.matches(keyCode, scanCode)
                || ClientKeyMappings.MODE_ROTATE.matches(keyCode, scanCode)
                || ClientKeyMappings.MODE_FUNNEL.matches(keyCode, scanCode);
        if (isBlueprintPlacementModeLocked() && modeKey) {
            enforceBlueprintPlacementModeLock();
            return true;
        }
        if (ClientKeyMappings.MODE_INTERACT.matches(keyCode, scanCode)) {
            return switchToModeFromKey(BuilderMode.INTERACT, false);
        }
        if (ClientKeyMappings.MODE_LINK_STORAGE.matches(keyCode, scanCode)) {
            return switchToModeFromKey(BuilderMode.LINK_STORAGE, false);
        }
        if (ClientKeyMappings.MODE_ROTATE.matches(keyCode, scanCode)) {
            return switchToModeFromKey(BuilderMode.ROTATE, false);
        }
        if (ClientKeyMappings.MODE_FUNNEL.matches(keyCode, scanCode)) {
            return switchToModeFromKey(BuilderMode.FUNNEL, true);
        }
        return false;
    }
    /**
     * Switches the builder mode from a keybind, cleaning up active input state.
     *
     * @param mode          the target builder mode
     * @param funnelEnabled whether funnel mode should be enabled on switch
     * @return true if the mode was actually changed
     */
    private boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) {
        if (mode == null || (this.controller.getMode() == mode && this.controller.isFunnelEnabled() == funnelEnabled)) {
            return false;
        }
        this.cameraInput.stopActiveMining();
        this.shapeController.clearShapeBuildSession();
        closeInteractionWheel();
        closeShapeWheel();
        this.controller.setMode(mode);
        this.controller.setFunnelEnabled(funnelEnabled);
        this.funnelHotkeyHeld = false;
        return true;
    }
    @Override
    /** Handles character-typed input, routing to quantity dialog, floating windows, search boxes, and ultimine limit input. */
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return this.bottomPanel.craftQuantityDialog.charTyped(codePoint, modifiers);
        }
        if (this.floatingWindowLayer.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.charTyped(codePoint)) {
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(codePoint, modifiers)) {
                this.bottomPanel.handleStorageSearchChanged(this.searchBox.getValue());
            }
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.charTyped(codePoint, modifiers);
            return true;
        }
        if (this.ultiminePanel.isLimitEditing()) {
            return this.ultiminePanel.handleCharTyped(codePoint);
        }
        return super.charTyped(codePoint, modifiers);
    }
    // ======================== Rendering Methods ========================
    @Override
    /**
     * Main render entry point. Uses fixed RTS GUI scaling when enabled.
     * Resets hover states, draws the top bar background, renders all panels and overlays
     * in priority order: top bar, bottom panel, quick-build, ultimine, funnel buffer,
     * quest/storage scan popups, blueprint capture/placement HUD,
     * tooltips, cursor preview, damage flash, and modal layers (wheel, gear, guide, dialogs).
     */
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.fixedRtsScaleRenderPass && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            return;
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.shapeController.setShapeCursorY(mouseY);
        updateRangeCullingHover(mouseX, mouseY);
        this.funnelBufferPanel.resetHoveredEntry();
        this.bottomPanel.hoveredEntry = -1;
        this.bottomPanel.hoveredRecentEntry = -1;
        this.bottomPanel.hoveredFluidEntry = -1;
        this.bottomPanel.hoveredCreativeEntry = -1;
        this.bottomPanel.hoveredCraftableEntry = -1;
        this.bottomPanel.hoveredToolSlot = -1;
        this.bottomPanel.hoveredEmptyHandSlot = false;
        this.bottomPanel.hoveredPinIndex = -1;
        this.bottomPanel.hoveredGuiBindingSlot = -1;
        this.bottomPanel.hoveredPinPageButton = false;
        guiGraphics.fill(0, 0, this.width, TOP_H, 0xC0101116);
        if (this.controller.isHomeSelectionMode()) {
            renderHomeSelectionOverlay(guiGraphics, mouseX, mouseY);
            renderDamageFlash(guiGraphics);
            return;
        }
        this.topBarPanel.render(guiGraphics, mouseX, mouseY);
        if (this.controller.isPlayerStatusOverlayEnabled()) {
            this.playerStatusRenderer.render(guiGraphics);
        }
        this.bottomPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        this.funnelBufferPanel.render(guiGraphics, mouseX, mouseY);
        renderQuestDetectPopup(guiGraphics);
        renderStorageScanPopup(guiGraphics);
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            BlockHitResult hit = isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
            BlueprintPanel.updateCaptureHover(
                    isWorldArea(mouseX, mouseY) ? this.cursorPicker.currentRayOrigin() : null,
                    isWorldArea(mouseX, mouseY) ? this.cursorPicker.computeCursorRayDirection() : null,
                    hit == null ? null : hit.getBlockPos());
        }
        this.blueprintWindowPanel.syncWithBlueprintState();
        this.blueprintMaterialWindowPanel.syncWithBlueprintState();
        this.blueprintNameWindowPanel.syncWithBlueprintState();
        this.floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        this.floatingWindowLayer.renderFloatingWindowOverlays(guiGraphics, mouseX, mouseY);
        RtsWindowPanel.ResizeCursor resizeCursor = this.floatingWindowLayer.resizeCursorAt(mouseX, mouseY);
        boolean modalOpen = isMouseOverFloatingWindow(mouseX, mouseY);
        boolean placementSelectionActive = this.controller.hasSelectedItem() || this.controller.hasSelectedFluid();
        if (!modalOpen) {
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredCreativeEntry >= 0) {
                var entry = this.bottomPanel.getCreativeEntryForTooltip(this.bottomPanel.hoveredCreativeEntry);
                if (entry != null) {
                    renderLeftDockedTooltip(guiGraphics, entry.stack());
                }
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredEntry >= 0
                    && this.bottomPanel.hoveredEntry < this.controller.getStorageEntries().size()) {
                var entry = this.controller.getStorageEntries().get(this.bottomPanel.hoveredEntry);
                renderLeftDockedTooltip(guiGraphics, entry.stack());
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredRecentEntry >= 0
                    && this.bottomPanel.hoveredRecentEntry < this.controller.getRecentEntries().size()) {
                var entry = this.controller.getRecentEntries().get(this.bottomPanel.hoveredRecentEntry);
                if (!entry.preview().isEmpty()) {
                    renderLeftDockedTooltip(guiGraphics, entry.preview());
                } else {
                    renderLeftDockedTooltip(guiGraphics, Component.literal(entry.label()));
                }
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredFluidEntry >= 0
                    && this.bottomPanel.hoveredFluidEntry < this.controller.getFluidEntries().size()) {
                var fluid = this.controller.getFluidEntries().get(this.bottomPanel.hoveredFluidEntry);
                if (!fluid.preview().isEmpty()) {
                    renderLeftDockedTooltip(guiGraphics, fluid.preview());
                } else {
                    renderLeftDockedTooltip(guiGraphics, Component.literal(fluid.label()));
                }
            }
            if (this.bottomPanel.hoveredCraftableEntry >= 0 && this.bottomPanel.hoveredCraftableEntry < this.controller.getCraftableEntries().size()) {
                var entry = this.controller.getCraftableEntries().get(this.bottomPanel.hoveredCraftableEntry);
                renderLeftDockedTooltip(guiGraphics, entry.stack());
                String detail = entry.craftable()
                        ? text("screen.rtsbuilding.tooltip.craft_choose")
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    renderLeftDockedTooltipDetail(guiGraphics, detail, entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
                }
            }
            if (this.funnelBufferPanel.getHoveredEntry() >= 0 && this.funnelBufferPanel.getHoveredEntry() < this.controller.getFunnelBufferEntries().size()) {
                var entry = this.controller.getFunnelBufferEntries().get(this.funnelBufferPanel.getHoveredEntry());
                renderLeftDockedTooltip(guiGraphics, entry.stack());
                renderLeftDockedTooltipDetail(guiGraphics, text("screen.rtsbuilding.tooltip.buffered", entry.count()), 0xFFD8B8);
            }
            if (this.bottomPanel.hoveredGuiBindingSlot >= 0 && this.bottomPanel.hoveredGuiBindingSlot < this.controller.getGuiBindingCount()) {
                String detail = this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                        ? this.controller.getGuiBindingLabel(this.bottomPanel.hoveredGuiBindingSlot)
                        : text("screen.rtsbuilding.tooltip.gui_empty");
                renderLeftDockedTooltip(guiGraphics, Component.literal(detail));
                renderLeftDockedTooltipDetail(
                        guiGraphics,
                        this.pendingGuiBindSlot == this.bottomPanel.hoveredGuiBindingSlot
                                ? text("screen.rtsbuilding.tooltip.gui_cancel_bind")
                                : (this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                                        ? text("screen.rtsbuilding.tooltip.gui_bound")
                                        : text("screen.rtsbuilding.tooltip.gui_unbound")),
                        0xFFCFE3F7);
            }
            if (this.bottomPanel.hoveredEmptyHandSlot) {
                renderLeftDockedTooltip(guiGraphics, Component.translatable("screen.rtsbuilding.tooltip.empty_hand"));
                renderLeftDockedTooltipDetail(guiGraphics, text("screen.rtsbuilding.tooltip.empty_hand_detail"), 0xFFD8B8);
            }
            boolean funnelCursor = shouldRenderFunnelCursor();
            if (funnelCursor) {
                updateNativeCursorVisibility(true);
                guiGraphics.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
            } else {
                updateNativeCursor(resizeCursor);
                if (this.pendingGuiBindSlot >= 0) {
                    drawGuiBindCursor(guiGraphics, mouseX, mouseY);
                } else {
                    ItemStack cursorPreview = resolveCursorPreview();
                    if (!cursorPreview.isEmpty() && !isSearchFocused() && !isMouseOverFloatingWindow(mouseX, mouseY)) {
                        guiGraphics.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
                    }
                }
            }
        } else {
            updateNativeCursor(resizeCursor);
        }
        this.bottomPanel.renderCraftFeedback(guiGraphics);
        if (this.interactionWheelPanel.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z, () -> renderInteractionWheel(guiGraphics, mouseX, mouseY));
        }
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 60.0F,
                    () -> this.bottomPanel.craftQuantityDialog.render(guiGraphics, this.font, this.width, this.height, mouseX, mouseY));
        }
        renderDamageFlash(guiGraphics);
    }
    /**
     * Renders a red flash overlay over the entire screen that fades out over time,
     * indicating the player took damage while the RTS screen was open.
     */
    private void renderDamageFlash(GuiGraphics guiGraphics) {
        if (this.damageFlashStartMs < 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.damageFlashStartMs;
        if (elapsed >= DAMAGE_FLASH_DURATION_MS) {
            this.damageFlashStartMs = -1L;
            return;
        }
        float alpha = 1.0F - (float) elapsed / (float) DAMAGE_FLASH_DURATION_MS;
        int argb = ((int) (alpha * 128.0F) << 24) | 0x00FF0000;
        guiGraphics.fill(0, 0, this.width, this.height, argb);
    }
    /**
     * Renders a sub-component at an elevated Z-layer to ensure it appears above other
     * game/screen elements.
     *
     * @param g        the GuiGraphics context
     * @param z        the Z-layer offset
     * @param renderer the rendering action to execute at the elevated layer
     */
    private void renderAtGuiLayer(GuiGraphics g, float z, Runnable renderer) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, z);
        try {
            renderer.run();
        } finally {
            g.pose().popPose();
        }
    }
    /**
     * Scales the rendering to the user-configured fixed RTS GUI scale, then recursively
     * calls {@link #render(GuiGraphics, int, int, float)} with adjusted coordinates.
     *
     * @return true if the render was handled at a non-unit scale (calling code should return)
     */
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame == null || Math.abs(frame.scale() - 1.0D) < 0.001D) {
            if (frame != null) {
                frame.close();
            }
            return false;
        }
        this.fixedRtsScaleRenderPass = true;
        double previousActiveRenderScale = this.activeRtsGuiRenderScale;
        this.activeRtsGuiRenderScale = frame.scale();
        g.pose().pushPose();
        g.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            render(g, (int) Math.round(mouseX / frame.scale()), (int) Math.round(mouseY / frame.scale()), partialTick);
        } finally {
            g.pose().popPose();
            this.activeRtsGuiRenderScale = previousActiveRenderScale;
            this.fixedRtsScaleRenderPass = false;
            frame.close();
        }
        return true;
    }
    /**
     * Enters a fixed RTS GUI scale frame by temporarily adjusting the screen width/height
     * to virtual dimensions that produce the desired render scale. Returns an
     * {@link RtsUiScaleFrame} that restores the original dimensions when closed.
     */
    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        if (this.minecraft == null || this.minecraft.getWindow() == null || this.width <= 0 || this.height <= 0) {
            return null;
        }
        double currentScale = this.minecraft.getWindow().getScreenWidth() / (double) Math.max(1, this.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return null;
        }
        double renderScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale) / currentScale;
        if (renderScale <= 0.0D || !Double.isFinite(renderScale)) {
            return null;
        }
        int oldW = this.width;
        int oldH = this.height;
        int virtualW = Math.max(1, (int) Math.round(oldW / renderScale));
        int virtualH = Math.max(1, (int) Math.round(oldH / renderScale));
        this.width = virtualW;
        this.height = virtualH;
        return new RtsUiScaleFrame(oldW, oldH, renderScale, () -> {
            this.width = oldW;
            this.height = oldH;
        });
    }
    /**
     * Renders the home-selection overlay, allowing the player to pick a block position
     * in the world to set as the RTS camera home / spawn point.
     */
    private void renderHomeSelectionOverlay(GuiGraphics g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.width - 24);
        int panelX = (this.width - panelW) / 2;
        int panelY = 12;
        Component cooldown = Component.translatable("screen.rtsbuilding.home_select.cooldown");
        var cooldownLines = this.font.split(cooldown, panelW - 20);
        int panelH = 58 + Math.max(1, cooldownLines.size()) * 10;
        RtsClientUiUtil.drawPanelFrame(g, panelX, panelY, panelW, panelH, 0xCC101820, 0xFF6E8799, 0xFF0D1218);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.title"), panelX + panelW / 2, panelY + 8, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.area"), panelX + panelW / 2, panelY + 22, 0xD8E6F5);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.confirm"), panelX + panelW / 2, panelY + 34, 0xBFD2E6);
        int cooldownY = panelY + 46;
        for (var line : cooldownLines) {
            g.drawString(this.font, line, panelX + (panelW - this.font.width(line)) / 2, cooldownY, 0xFFE7C46A);
            cooldownY += 10;
        }
        BlockHitResult hit = isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()), this.width / 2, panelY + panelH + 14, 0xFFE7C46A);
        }
    }
    /**
     * Renders a hint message at the top of the screen related to the guide panel when
     * the top bar buttons are visible.
     */
    public void renderTopGuideHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
        this.guidePanel.renderTopHint(g, topButtons);
    }
    /**
     * Draws a small "+" icon inside a green-bordered slot at the cursor position,
     * indicating the player is in GUI binding mode and should click a block to bind it.
     */
    private void drawGuiBindCursor(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX + 8;
        int y = mouseY + 8;
        RtsClientUiUtil.drawPanelFrame(g, x, y, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE, 0xCC2D6B47, 0xFF78B28C, 0xFF0F151C);
        g.drawCenteredString(this.font, "+", x + CRAFT_DOCK_SLOT_SIZE / 2, y + 1, 0xFFFFFF);
    }
    /**
     * Renders the quest detection popup, showing scan progress and results
     * (detecting quest items across linked storage).
     */
    private void renderQuestDetectPopup(GuiGraphics g) {
        if (!this.controller.isQuestDetectPopupVisible()) {
            return;
        }
        int x = Mth.clamp((this.width - QUEST_DETECT_POPUP_W) / 2, 8, Math.max(8, this.width - QUEST_DETECT_POPUP_W - 8));
        int y = TOP_H + 8;
        RtsClientUiUtil.drawPanelFrame(g, x, y, QUEST_DETECT_POPUP_W, QUEST_DETECT_POPUP_H, 0xEE151A22, 0xFF61758A, 0xFF0D1117);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.quest_scan.title"), x + 9, y + 7, 0xF2F7FF, false);
        byte phase = this.controller.getQuestDetectPhase();
        String status = questDetectStatusText(phase).getString();
        int statusColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? 0xFFFFB0B0
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE
                        ? 0xFFE7C46A
                        : 0xFFCFE3F7;
        g.drawString(this.font, trimToWidth(status, QUEST_DETECT_POPUP_W - 18), x + 9, y + 19, statusColor, false);
        int barX = x + 9;
        int barY = y + 34;
        int barW = QUEST_DETECT_POPUP_W - 18;
        int barH = 6;
        float progress = this.controller.getQuestDetectProgress();
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * progress)));
        int progressColor = phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                ? 0xFFE07070
                : phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                        ? 0xFF78B28C
                        : 0xFF88BEF4;
        g.fill(barX, barY, barX + barW, barY + barH, 0xAA202832);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, progressColor);
        }
        g.hLine(barX, barX + barW, barY, 0xFF405064);
        g.hLine(barX, barX + barW, barY + barH, 0xFF0A0D12);
        g.vLine(barX, barY, barY + barH, 0xFF405064);
        g.vLine(barX + barW, barY, barY + barH, 0xFF0A0D12);
    }
    /**
     * Builds the status text component for the quest detection popup based on the
     * current detection phase (started, complete, unavailable, error, or ready).
     */
    private Component questDetectStatusText(byte phase) {
        int scanned = this.controller.getQuestDetectScannedTasks();
        int total = Math.max(scanned, this.controller.getQuestDetectTotalTasks());
        int completed = this.controller.getQuestDetectCompletedTasks();
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_STARTED) {
            return Component.translatable("screen.rtsbuilding.quest_scan.scanning");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE) {
            if (completed > 0) {
                return completed == 1
                        ? Component.translatable("screen.rtsbuilding.quest_scan.completed_one")
                        : Component.translatable("screen.rtsbuilding.quest_scan.completed_many", completed);
            }
            return total > 0
                    ? Component.translatable("screen.rtsbuilding.quest_scan.none_completed")
                    : Component.translatable("screen.rtsbuilding.quest_scan.no_item_tasks");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE) {
            return Component.translatable("screen.rtsbuilding.quest_scan.unavailable");
        }
        if (phase == S2CRtsQuestDetectStatusPayload.PHASE_ERROR) {
            return Component.translatable("screen.rtsbuilding.quest_scan.failed");
        }
        return Component.translatable("screen.rtsbuilding.quest_scan.ready");
    }
    /**
     * Renders the storage scan popup, showing a progress bar for the ongoing or
     * completed storage rescan operation.
     */
    private void renderStorageScanPopup(GuiGraphics g) {
        if (!this.controller.isStorageScanPopupVisible()) {
            return;
        }
        if (!this.controller.isStorageScanRunning()
                && !RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            return;
        }
        BottomPanelLayoutTypes.BottomPanelLayout layout = this.bottomPanel.resolveBottomPanelLayout();
        int popupW = Math.min(STORAGE_SCAN_POPUP_W, Math.max(96, this.width - 16));
        int x = Mth.clamp(
                layout.panelX() + (layout.panelW() - popupW) / 2,
                8,
                Math.max(8, this.width - popupW - 8));
        int y = Math.max(TOP_H + 8, layout.panelY() - STORAGE_SCAN_POPUP_H - 6);
        RtsClientUiUtil.drawPanelFrame(g, x, y, popupW, STORAGE_SCAN_POPUP_H, 0xEE151A22, 0xFF61758A, 0xFF0D1117);
        Component label = Component.translatable(this.controller.isStorageScanRunning()
                ? "screen.rtsbuilding.storage_scan.scanning"
                : "screen.rtsbuilding.storage_scan.ready");
        g.drawString(this.font, trimToWidth(label.getString(), popupW - 18), x + 9, y + 6, 0xF2F7FF, false);
        int barX = x + 9;
        int barY = y + 20;
        int barW = popupW - 18;
        int barH = 5;
        int fillW = Math.max(0, Math.min(barW, Math.round(barW * this.controller.getStorageScanProgress())));
        g.fill(barX, barY, barX + barW, barY + barH, 0xAA202832);
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH,
                    this.controller.isStorageScanRunning() ? 0xFF88BEF4 : 0xFF78B28C);
        }
        g.hLine(barX, barX + barW, barY, 0xFF405064);
        g.hLine(barX, barX + barW, barY + barH, 0xFF0A0D12);
        g.vLine(barX, barY, barY + barH, 0xFF405064);
        g.vLine(barX + barW, barY, barY + barH, 0xFF0A0D12);
    }
    /**
     * Checks whether the given progression node has been unlocked by the player.
     * If progression is disabled, all nodes are considered unlocked.
     */
    public boolean hasProgressionNode(ResourceLocation nodeId) {
        return !this.controller.isProgressionEnabled()
                || nodeId == null
                || this.controller.getUnlockedProgressionNodes().contains(nodeId.toString());
    }
    /** Returns true if any recipe viewer mod (JEI, EMI, REI) is loaded. */
    private static boolean hasRecipeViewerLoaded() {
        return ModList.get().isLoaded("jei")
                || ModList.get().isLoaded("emi")
                || ModList.get().isLoaded("roughlyenoughitems");
    }

    /**
     * Applies persisted UI state from {@link RtsClientUiStateStore} to all panels,
     * the shape controller, camera settings, and debug toggle.
     */
    private void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        this.quickBuildPanel.setQuickBuildOpen(state.quickBuildOpen);
        this.quickBuildPanel.setQuickBuildModeName(state.quickBuildMode);
        this.quickBuildPanel.setDestroyChainSelected(state.quickBuildDestroyChainSelected);
        this.quickBuildPanel.setChainDestroyLimit(state.quickBuildChainDestroyLimit);
        this.ultiminePanel.setOpen(false);
        this.ultiminePanel.setLimit(state.ultimineLimit);
        try {
            this.ultiminePanel.setMode(UltimineMode.valueOf(state.ultimineMode));
        } catch (IllegalArgumentException ignored) {
            this.ultiminePanel.setMode(UltimineMode.CHAIN);
        }
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setToolProtectionEnabled(state.toolProtectionEnabled);
        this.controller.setPlayerStatusOverlayEnabled(state.playerStatusOverlayEnabled);
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);        int sensitivityPresetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        double sensitivityFraction = sensitivityPresetCount <= 1
                ? 0.0D
                : Mth.clamp(state.inputSensitivityIndex, 0, sensitivityPresetCount - 1) / (double) (sensitivityPresetCount - 1);
        this.controller.setInputSensitivityByFraction(sensitivityFraction);
        this.controller.setChunkCurtainVisible(state.chunkCurtainVisible);
        try {
            this.controller.setBuildShape(ClientRtsController.BuildShape.valueOf(state.buildShape));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
        }
        try {
            this.shapeController.setShapeFillMode(ShapeBuildTypes.ShapeFillMode.valueOf(state.fillMode));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setShapeFillMode(ShapeBuildTypes.ShapeFillMode.FILL);
        }
        this.shapeController.setLineConnected(state.lineConnected);
        this.shapeController.rotateToDegrees(Math.floorMod(state.rotationDegrees, 360));
        this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
        syncQuickBuildActiveState();
    }
    /**
     * Persists the current UI state (shape, fill mode, rotation, panel toggles,
     * camera preferences, debug visibility) to {@link RtsClientUiStateStore}.
     */
    public void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.controller.getBuildShape().name();
        state.fillMode = this.shapeController.getShapeFillMode().name();
        state.lineConnected = this.shapeController.isLineConnected();
        state.rotationDegrees = this.shapeController.getShapeRotateDegrees();
        state.quickBuildOpen = isQuickBuildOpen();
        state.quickBuildMode = this.quickBuildPanel.getQuickBuildModeName();
        state.quickBuildDestroyChainSelected = this.quickBuildPanel.isDestroyChainSelected();
        state.quickBuildChainDestroyLimit = this.quickBuildPanel.getChainDestroyLimit();
        state.ultimineOpen = false;
        state.ultimineLimit = this.quickBuildPanel.getChainDestroyLimit();
        state.ultimineMode = this.ultiminePanel.getMode().name();
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.inputSensitivityIndex = this.controller.getInputSensitivityIndex();
        state.startCameraAtPlayerHead = this.controller.isStartCameraAtPlayerHead();
        state.allowPlacedBlockRecovery = this.controller.isAllowPlacedBlockRecovery();
        state.toolProtectionEnabled = this.controller.isToolProtectionEnabled();
        state.playerStatusOverlayEnabled = this.controller.isPlayerStatusOverlayEnabled();
        state.invertPanDragX = this.controller.isInvertPanDragX();
        state.invertPanDragY = this.controller.isInvertPanDragY();
        state.smoothCamera = this.controller.isSmoothCamera();
        state.damageSoundEnabled = this.controller.isDamageSoundEnabled();
        state.damageAutoReturnEnabled = this.controller.isDamageAutoReturnEnabled();        RtsClientUiStateStore.save(state);
    }
    /** Adjusts the fixed RTS GUI scale by a delta and persists the change. */
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }
    /** Returns the current RTS GUI scale as a human-readable label (e.g. "1.0x", "1.5x"). */
    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }
    /**
     * Clamps and snaps the given GUI scale to the allowed range and step intervals.
     *
     * @return the sanitized scale value, or {@link BuilderScreenConstants#DEFAULT_RTS_GUI_SCALE} if invalid
     */
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }
    /** Resolves the layout metadata for the quick-build panel (used for positioning and hit-testing). */
    public PanelLayouts.QuickBuildPanelLayout resolveQuickBuildPanelLayout() {
        return this.quickBuildPanel.resolveLayout();
    }

    /** Adjusts the ultimine (vein-mining) block limit by a delta. */
    private void adjustUltimineLimit(int delta) {
        this.ultiminePanel.adjustLimit(delta);
    }
    /** Returns whether the mouse is inside the ultimine limit text input field. */
    private boolean isInsideUltimineLimitInput(double mouseX, double mouseY) {
        return this.ultiminePanel.isInsideLimitInput(mouseX, mouseY);
    }

    /** Returns whether the ultimine panel is currently open. */
    public boolean isUltimineOpen() {
        return false;
    }
    /** Returns the current ultimine block limit. */
    public int getUltimineLimit() {
        return this.quickBuildPanel.getChainDestroyLimit();
    }
    /** Returns the current ultimine mode selected in the floating panel. */
    public UltimineMode getUltimineMode() {
        return this.ultiminePanel.getMode();
    }
    /** Returns true when Quick Build is showing the range-destroy workflow. */
    public boolean isQuickBuildRangeDestroyMode() {
        return this.quickBuildPanel.isQuickBuildOpen() && this.quickBuildPanel.isRangeDestroyMode();
    }
    /** Returns true when Quick Build range destroy is using connected-chain mode. */
    public boolean isQuickBuildRangeDestroyChainMode() {
        return this.quickBuildPanel.isQuickBuildOpen() && this.quickBuildPanel.isRangeDestroyChainMode();
    }
    /** Player-facing shape label for the top status row. */
    public String activeQuickBuildShapeLabel() {
        if (isQuickBuildRangeDestroyChainMode()) {
            return text("screen.rtsbuilding.shape.chain");
        }
        return shapeLabel(this.controller.getBuildShape());
    }
    /** Handles the left-click selection flow for Quick Build range destroy. */
    public boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY) {
        if (!isQuickBuildRangeDestroyMode() || isQuickBuildRangeDestroyChainMode() || !isWorldArea(mouseX, mouseY)) {
            return false;
        }
        if (this.shapeController.isAwaitingBatchDestroyConfirm()) {
            if (Config.isKeyboardBatchConfirmEnabled()) {
                return true;
            }
            return this.shapeController.tryConfirmPendingRangeDestroy();
        }
        InteractionTypes.InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
        if (target != null && target.blockHit() != null) {
            this.shapeController.selectRangeDestroyShape(target.blockHit(), mouseY, target.rayDir());
            return true;
        }
        return true;
    }
    /** Gives input/render helpers controlled access to the shape state machine. */
    public ScreenShapeController getShapeController() {
        return this.shapeController;
    }
    /** Sets the last sent ultimine limit value (to avoid redundant network packets). */
    public void setUltimineLastSentLimit(int limit) {
        this.ultiminePanel.setLastSentLimit(limit);
    }
    /** Returns the number of available undo steps for shape placement. */
    public int getShapeUndoSize() {
        return this.shapeController.getShapeUndoSize();
    }
    /** Returns the number of available redo steps for shape placement. */
    public int getShapeRedoSize() {
        return this.shapeController.getShapeRedoSize();
    }
    /** Returns the pending GUI bind slot index, or -1 if not binding. */
    public int getPendingGuiBindSlot() {
        return this.pendingGuiBindSlot;
    }
    /** Sets the pending GUI bind slot (entering or exiting bind mode). */
    public void setPendingGuiBindSlot(int slot) {
        this.pendingGuiBindSlot = slot;
    }
    /** Cancels the current GUI bind operation. */
    public void clearPendingGuiBind() {
        this.pendingGuiBindSlot = -1;
    }
    /** Toggles the quick-build panel open/closed. */
    public void toggleQuickBuild() {
        if (!this.quickBuildPanel.isQuickBuildOpen() && !canUseQuickBuild()) {
            showQuickBuildLockedMessage();
            this.quickBuildPanel.setQuickBuildOpen(false);
            return;
        }
        this.quickBuildPanel.toggleOpen();
    }
    /** Toggles the ultimine panel open/closed. */
    public void toggleUltimine() {
        if (!canUseQuickBuild()) {
            showQuickBuildLockedMessage();
            this.quickBuildPanel.setQuickBuildOpen(false);
            return;
        }
        this.quickBuildPanel.setQuickBuildOpen(true);
        this.quickBuildPanel.setQuickBuildModeName("DESTROY");
        this.quickBuildPanel.setDestroyChainSelected(true);
        persistUiState();
    }
    /** Closes the gear (settings) menu. */
    public void closeGearMenu() {
        this.gearMenuPanel.close();
    }
    /** Toggles the gear (settings) menu open/closed. */
    public void toggleGearMenu() {
        if (this.gearMenuPanel.isOpen()) {
            this.gearMenuPanel.close();
        } else {
            this.gearMenuPanel.open();
        }
    }
    /**
     * Toggles the top guide panel on or off. If the guide is already open in TOP context,
     * closes it; otherwise opens it at the given position.
     */
    public void toggleTopGuide(int x, int y) {
        if (this.guidePanel.isOpen() && this.guidePanel.getContext() == GuideTypes.GuideContext.TOP) {
            this.guidePanel.close();
        } else {
            this.guidePanel.open(GuideTypes.GuideContext.TOP, x, y);
        }
    }
    /** Returns whether the current player can open the range-culling editor. */
    public boolean canUseRangeCulling() {
        return true;
    }
    /** Returns whether range-culling management mode is currently active. */
    public boolean isRangeCullingManagementActive() {
        return this.cullingManager.isManagementMode();
    }
    /** Toggles range-culling management mode from the top bar. */
    public void toggleRangeCullingManagement() {
        if (!canUseRangeCulling()) {
            return;
        }
        this.cameraInput.stopActiveMining();
        this.shapeController.clearShapeBuildSession();
        this.cullingManager.toggleManagementMode();
        this.cullingPanel.setOpen(this.cullingManager.isManagementMode());
        persistUiState();
    }
    /** Opens the bottom guide panel at the given position. */
    public void openBottomGuide(int x, int y) {
        this.guidePanel.open(GuideTypes.GuideContext.BOTTOM, x, y);
    }
    /** Returns whether the guide panel is currently open. */
    public boolean isGuideOpen() {
        return this.guidePanel.isOpen();
    }
    /** Returns whether the gear menu is currently open. */
    public boolean isGearMenuOpen() {
        return this.gearMenuPanel.isOpen();
    }
    /** Returns whether the craft quantity dialog is currently open. */
    public boolean isCraftQuantityDialogOpen() {
        return this.bottomPanel.craftQuantityDialog.isOpen();
    }
    /** Returns whether the cursor is over any open floating RTS window or resize border. */
    public boolean isMouseOverFloatingWindow(double mouseX, double mouseY) {
        return this.floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
    }
    /**
     * Activates the funnel hotkey: stops mining, clears shape preview, closes wheels,
     * saves the current mode, and switches to funnel mode with funnel enabled.
     */
    private void activateFunnelHotkey() {
        if (isBlueprintPlacementModeLocked()) {
            enforceBlueprintPlacementModeLock();
            return;
        }
        this.cameraInput.stopActiveMining();
        this.shapeController.clearShapeBuildSession();
        closeInteractionWheel();
        closeShapeWheel();
        if (this.controller.getMode() != BuilderMode.FUNNEL) {
            this.modeBeforeFunnelHotkey = this.controller.getMode();
        }
        this.controller.setMode(BuilderMode.FUNNEL);
        this.controller.setFunnelEnabled(true);
    }

    public boolean isBlueprintPlacementModeLocked() {
        return BlueprintPanel.isPlacementSessionActive();
    }

    private void enforceBlueprintPlacementModeLock() {
        if (!isBlueprintPlacementModeLocked()) {
            return;
        }
        if (this.controller.getMode() == BuilderMode.INTERACT && !this.controller.isFunnelEnabled()) {
            return;
        }
        this.cameraInput.stopActiveMining();
        this.shapeController.clearShapeBuildSession();
        closeInteractionWheel();
        closeShapeWheel();
        this.controller.setFunnelEnabled(false);
        this.controller.setMode(BuilderMode.INTERACT);
        this.funnelHotkeyHeld = false;
    }

    /**
     * Deactivates the funnel hotkey: disables funnel and restores the mode that was
     * active before the hotkey was pressed.
     */
    private void deactivateFunnelHotkey() {
        if (this.controller.getMode() == BuilderMode.FUNNEL || this.controller.isFunnelEnabled()) {
            this.controller.setFunnelEnabled(false);
            this.controller.setMode(this.modeBeforeFunnelHotkey == BuilderMode.FUNNEL
                    ? BuilderMode.INTERACT
                    : this.modeBeforeFunnelHotkey);
        }
    }

    private boolean handleRangeCullingSelectionClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !this.cullingManager.isManagementMode() || !isWorldArea(mouseX, mouseY)) {
            return false;
        }
        return RtsCullingWorldInput.handleWorldAction(this.cullingManager, this.cursorPicker);
    }

    private boolean handleBoxHandleDrag(int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Direction blueprintDirection = BlueprintPanel.getCaptureActiveHandleDirection();
        if (BlueprintPanel.isCaptureModeActive() && blueprintDirection != null) {
            double[] axis = screenAxisForDirection(blueprintDirection);
            return BlueprintPanel.mouseDraggedCaptureHandle(dragX, dragY, axis[0], axis[1]);
        }
        Direction cullingDirection = this.cullingManager.activeHandleDirection();
        if (this.cullingManager.isManagementMode() && cullingDirection != null) {
            double[] axis = screenAxisForDirection(cullingDirection);
            return this.cullingManager.handleActiveHandleDrag(dragX, dragY, axis[0], axis[1]);
        }
        return false;
    }

    /**
     * 把世界里的六向箭头投影成屏幕拖拽轴；沿箭头视觉方向拖动为扩大，反向为缩小。
     */
    private double[] screenAxisForDirection(Direction direction) {
        if (direction == null || this.minecraft == null || this.minecraft.gameRenderer == null) {
            return new double[] {0.0D, -1.0D};
        }
        float yawDeg = this.minecraft.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = this.minecraft.gameRenderer.getMainCamera().getXRot();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        Vec3 up = forward.cross(right).normalize();
        Vec3 normal = new Vec3(
                direction.getNormal().getX(),
                direction.getNormal().getY(),
                direction.getNormal().getZ());
        return new double[] {-normal.dot(right), -normal.dot(up)};
    }

    private void updateRangeCullingHover(double mouseX, double mouseY) {
        if (!this.cullingManager.isManagementMode()) {
            this.cullingManager.updateHover(null, null);
            return;
        }
        if (!isWorldArea(mouseX, mouseY) || isMouseOverFloatingWindow(mouseX, mouseY)) {
            this.cullingManager.updateHover(null, null);
            return;
        }
        this.cullingManager.updateHover(
                this.cursorPicker.currentRayOrigin(),
                this.cursorPicker.computeCursorRayDirection());
    }
    /**
     * Drops one item of the currently selected item (or the tool slot item) at the
     * cursor's target position in the world. Used by the quick-drop keybind.
     */
    private void quickDropSelectedAtCursor() {
        if (this.minecraft == null || this.minecraft.getCameraEntity() == null) {
            return;
        }
        String dropItemId = "";
        if (this.controller.hasSelectedItem() && !this.controller.getSelectedItemId().isBlank()) {
            dropItemId = this.controller.getSelectedItemId();
        } else {
            ItemStack toolStack = getSelectedToolStack();
            if (toolStack.isEmpty()) {
                return;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(toolStack.getItem());
            if (id == null) {
                return;
            }
            dropItemId = id.toString();
        }
        Vec3 origin = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = this.cursorPicker.computeCursorRayDirection();
        Vec3 dropPos = origin.add(dir.scale(3.25D));
        BlockHitResult hit = this.cursorPicker.pickBlockHit(true);
        if (hit != null) {
            dropPos = Vec3.atCenterOf(hit.getBlockPos()).add(0.0D, 1.05D, 0.0D);
        }
        this.controller.quickDropSelectedItem(dropItemId, 1, dropPos);
    }
    /**
     * Removes focus from any focused search box (storage or craft search).
     */
    public void blurSearchFocus() {
        boolean blurred = false;
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            blurred = true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.setFocused(false);
            blurred = true;
        }
        if (blurred) {
            this.setFocused(null);
        }
    }
    /** Moves focus to the storage search box, removing focus from the craft search box if needed. */
    public void focusStorageSearchBox() {
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.setFocused(false);
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
        }
    }
    /** Moves focus to the craft search box, removing focus from the storage search box if needed. */
    public void focusCraftSearchBox() {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        if (this.craftSearchBox != null) {
            this.craftSearchBox.setFocused(true);
            this.setFocused(this.craftSearchBox);
        }
    }

    /**
     * Returns whether the given screen coordinates are within the "world area" ??
     * below the top bar and outside the bottom panel. Clicks in this area interact
     * with the Minecraft world.
     */
    public boolean isWorldArea(double mouseX, double mouseY) {
        return mouseY > TOP_H && !this.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
    }

    /** Returns the top edge (Y coordinate) of the bottom panel. */
    public int getBottomY() {
        return this.bottomPanel.getBottomY();
    }
    /**
     * Returns the available height for floating panels between a given panelY and
     * the bottom panel, with a 6-pixel margin.
     */
    public int getFloatingPanelAvailableHeight(int panelY) {
        return Math.max(0, getBottomY() - panelY - 6);
    }

    /** Returns whether the given coordinates are inside the bottom panel region. */
    private boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return this.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
    }


    /** Returns whether either search box is currently focused. */
    public boolean isSearchFocused() {
        return (this.searchBox != null && this.searchBox.isFocused())
                || (this.craftSearchBox != null && this.craftSearchBox.isFocused());
    }
    /** Returns the player's currently selected hotbar slot index (0-8). */
    public int getSelectedToolSlot() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 0;
        }
        return Mth.clamp(this.minecraft.player.getInventory().selected, 0, 8);
    }
    /** Returns the ItemStack in the player's currently selected hotbar slot. */
    private ItemStack getSelectedToolStack() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return this.minecraft.player.getInventory().getItem(getSelectedToolSlot());
    }
    /**
     * Resolves the item ID string for GUI binding at the given block hit.
     * Tries the block's pick item first, then falls back to AE2 compat resolution.
     */
    private String resolveGuiBindingItemId(BlockHitResult hit) {
        if (hit == null || this.minecraft == null || this.minecraft.level == null) {
            return "";
        }
        BlockPos pos = hit.getBlockPos();
        if (!this.minecraft.level.hasChunkAt(pos)) {
            return "";
        }
        BlockState state = this.minecraft.level.getBlockState(pos);
        ItemStack preview = state.getBlock().getCloneItemStack(this.minecraft.level, pos, state);
        if (preview.isEmpty()) {
            preview = new ItemStack(state.getBlock().asItem());
        }
        if (preview.isEmpty() || preview.is(Items.AIR)) {
            return RtsAe2Compat.resolveGuiBindingIconItemId(this.minecraft.level, pos, hit.getDirection(), "");
        }
        var id = BuiltInRegistries.ITEM.getKey(preview.getItem());
        return id == null ? "" : id.toString();
    }
    /**
     * Returns whether the tool slot can be used as a shape build source:
     * the player must NOT have a selected item/fluid/empty hand, and the tool
     * slot must contain a BlockItem.
     */
    public boolean canUseToolSlotShapeSource() {
        if (this.controller.hasSelectedItem() || this.controller.hasSelectedFluid() || this.controller.isEmptyHandSelected()) {
            return false;
        }
        ItemStack stack = getSelectedToolStack();
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }
    /**
     * Attempts to assign a quick-slot (pin) from the currently hovered tool slot
     * or the selected hotbar slot. Used by the pin quick-slot keybind.
     *
     * @return true if the slot was assigned
     */
    private boolean tryAssignQuickSlotFromToolSelection(int pinIndex) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.controller.isEmptyHandSelected()) {
            return false;
        }
        int slot = this.bottomPanel.hoveredToolSlot >= 0 ? this.bottomPanel.hoveredToolSlot : getSelectedToolSlot();
        slot = Mth.clamp(slot, 0, 8);
        ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        this.controller.assignQuickSlotFromToolItem(pinIndex, stack);
        return true;
    }
    /** Sets the player's selected hotbar slot (clamped to 0-8). */
    public void setSelectedToolSlot(int slot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.getInventory().selected = Mth.clamp(slot, 0, 8);
    }

    /**
     * Computes how many pin (quick-slot) cells are visible within the available
     * horizontal space, given a starting X and right bound.
     */
    private int computeVisiblePinCells(int pinStartX, int rightBoundExclusive) {
        int visible = 0;
        for (int i = 0; i < this.controller.getQuickSlotCount(); i++) {
            int cx = pinStartX + i * HOTBAR_PITCH;
            if (cx + HOTBAR_SLOT > rightBoundExclusive) {
                break;
            }
            visible++;
        }
        return visible;
    }
    /** Returns whether a pin pager (left/right arrows) should be shown for quick-slots. */
    private boolean shouldUsePinPager(int visibleCells, int totalPins) {
        return visibleCells >= 2 && totalPins > visibleCells;
    }
    /**
     * Computes how many pin slots fit on a single page. If a pager is needed,
     * one cell is reserved for the pager button.
     */
    private int computePinSlotsPerPage(int visibleCells, int totalPins) {
        if (visibleCells <= 0) {
            return 1;
        }
        if (shouldUsePinPager(visibleCells, totalPins)) {
            return Math.max(1, visibleCells - 1);
        }
        return visibleCells;
    }

    /**
     * Returns the ghost preview data for the currently selected blueprint, or
     * {@link BlueprintGhostPreview#EMPTY} if no blueprint is active.
     */
    public BlueprintGhostPreview getBlueprintGhostPreview() {
        if (this.bottomPanel.bottomPanelTab != BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                || BlueprintPanel.isCaptureModeActive()
                || !BlueprintPanel.hasSelectedBlueprint()) {
            return BlueprintGhostPreview.EMPTY;
        }
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        if (anchor == null) {
            anchor = this.cursorPicker.resolveBlueprintAnchor(this.cursorPicker.pickBlueprintPlacementHit());
        }
        if (anchor == null) {
            return BlueprintGhostPreview.EMPTY;
        }
        var preview = BlueprintPanel.createGhostPreview(anchor, BlueprintPanel.getYRotationSteps(), this.controller);
        if (preview.blocks().isEmpty()) {
            return BlueprintGhostPreview.EMPTY;
        }
        return new BlueprintGhostPreview(preview.blocks(), preview.materialsReady(), preview.truncated());
    }
    /**
     * Collects the list of block positions that would be affected by an ultimine
     * (vein-mining) operation starting from the current mining seed position
     * or the block under the cursor.
     */
    public List<BlockPos> collectUltiminePreviewBlocks() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        if (!isQuickBuildRangeDestroyChainMode()) {
            return List.of();
        }
        BlockPos seed = this.controller.getMineProgressPos();
        if (seed == null || this.minecraft.level.getBlockState(seed).isAir()) {
            BlockHitResult hit = this.cursorPicker.pickBlockHit();
            if (hit == null) {
                return List.of();
            }
            seed = hit.getBlockPos();
        }
        BlockState seedState = this.minecraft.level.getBlockState(seed);
        if (seedState.isAir()) {
            return List.of();
        }
        boolean creative = this.minecraft.player != null && this.minecraft.player.isCreative();
        return RtsUltimineCollector.collect(
                this.minecraft.level,
                seed,
                getUltimineLimit(),
                (pos, state, originalState) -> !state.isAir()
                        && state.getBlock() == originalState.getBlock()
                        && (creative || state.getDestroySpeed(this.minecraft.level, pos) >= 0.0F));
    }

    /**
     * Returns the effective RTS GUI render scale for the current frame,
     * factoring in the fixed RTS scale and the Minecraft window's actual scale.
     */
    private double currentRtsGuiRenderScale() {
        if (this.minecraft == null || this.minecraft.getWindow() == null || this.width <= 0) {
            return 1.0D;
        }
        double currentScale = this.minecraft.getWindow().getScreenWidth() / (double) Math.max(1, this.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return 1.0D;
        }
        double renderScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale) / currentScale;
        return renderScale > 0.0D && Double.isFinite(renderScale) ? renderScale : 1.0D;
    }

    private boolean isMovePlayerActionMouse(int button) {
        return ClientKeyMappings.MOVE_PLAYER.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button));
    }

    private boolean isMovePlayerActionKey(int keyCode, int scanCode) {
        return ClientKeyMappings.MOVE_PLAYER.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    private boolean handleMovePlayerActionAt(double mouseX, double mouseY) {
        if (!isWorldArea(mouseX, mouseY)) {
            return true;
        }
        // 移动玩家是独立键位，默认 Ctrl+右键；双击保留“飞到目标上方”的精准降落。
        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - this.lastCtrlRightClickTime) < CTRL_DOUBLE_CLICK_THRESHOLD_MS;
        this.lastCtrlRightClickTime = now;

        BlockHitResult hit = this.cursorPicker.pickBlockHit();
        if (hit != null) {
            if (isDoubleClick) {
                this.lastCtrlRightClickTime = 0L;
                RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
            } else {
                RtsClientPathfinding.goTo(hit.getBlockPos());
            }
        }
        return true;
    }

    /**
     * Performs a direct tool interaction (interact entity or block) using the
     * currently selected tool slot, without shape building.
     *
     * @return true if the interaction was performed
     */
    private boolean tryDirectToolInteraction() {
        InteractionTypes.InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
        if (target == null) {
            return false;
        }
        int slot = getSelectedToolSlot();
        if (target.isEntityTarget()) {
            this.controller.interactEntityWithToolSlot(
                    target.entityId(),
                    target.hitLocation(),
                    slot,
                    target.rayOrigin(),
                    target.rayDir());
            return true;
        }
        if (target.blockHit() != null) {
            this.controller.interactBlockWithToolSlot(target.blockHit(), slot, target.rayOrigin(), target.rayDir());
            return true;
        }
        return false;
    }
    /** Opens the interaction wheel at the given screen position. */
    private boolean openInteractionWheel(double mouseX, double mouseY) {
        return this.interactionWheelPanel.open(mouseX, mouseY);
    }
    /** Closes the interaction wheel overlay. */
    public void closeInteractionWheel() {
        this.interactionWheelPanel.close();
    }
    /** Retired shape-wheel hook kept so extracted controllers can stay source-compatible. */
    public void openShapeWheel(double mouseX, double mouseY) {
    }
    /** Retired shape-wheel hook kept so existing close paths remain harmless. */
    private void closeShapeWheel() {
    }
    /** Resolves which interaction option is under the mouse cursor on the interaction wheel. */
    private InteractionTypes.InteractionOption resolveInteractionWheelOption(double mouseX, double mouseY) {
        return this.interactionWheelPanel.resolveOption(mouseX, mouseY);
    }
    /** Delegates rendering of the interaction wheel overlay. */
    private void renderInteractionWheel(GuiGraphics g, int mouseX, int mouseY) {
        this.interactionWheelPanel.render(g, mouseX, mouseY);
    }
    /**
     * Enables a scissor region for clipping, adjusting coordinates for the
     * active RTS GUI render scale if a fixed-scale pass is in progress.
     */
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        double scale = this.fixedRtsScaleRenderPass ? this.activeRtsGuiRenderScale : 1.0D;
        if (scale > 0.0D && Double.isFinite(scale) && Math.abs(scale - 1.0D) >= 0.001D) {
            g.enableScissor(
                    (int) Math.floor(x1 * scale),
                    (int) Math.floor(y1 * scale),
                    (int) Math.ceil(x2 * scale),
                    (int) Math.ceil(y2 * scale));
            return;
        }
        g.enableScissor(x1, y1, x2, y2);
    }

    /** Truncates the given text to fit within the specified pixel width. */
    public String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(this.font, text, maxWidth);
    }
    /** Translates the given i18n key and formats with the provided arguments. */
    public String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private void renderLeftDockedTooltip(GuiGraphics g, ItemStack stack) {
        int x = leftTooltipAnchorX();
        int y = leftTooltipAnchorY();
        g.renderTooltip(this.font, stack, x, y);
    }

    private void renderLeftDockedTooltip(GuiGraphics g, Component text) {
        int x = leftTooltipAnchorX();
        int y = leftTooltipAnchorY();
        g.renderTooltip(this.font, text, x, y);
    }

    private void renderLeftDockedTooltipDetail(GuiGraphics g, String detail, int color) {
        if (detail == null || detail.isBlank()) {
            return;
        }
        g.drawString(this.font, detail, leftTooltipAnchorX() + 10,
                leftTooltipAnchorY() + LEFT_TOOLTIP_DETAIL_Y_OFFSET, color);
    }

    private int leftTooltipAnchorX() {
        return this.bottomPanel.resolveBottomPanelLayout().panelX() + LEFT_TOOLTIP_X_OFFSET;
    }

    private int leftTooltipAnchorY() {
        return Math.max(TOP_H + 8, this.bottomPanel.getBottomY() - LEFT_TOOLTIP_Y_OFFSET);
    }

    /**
     * Returns a status label for the currently selected item, including durability
     * information if the item is damageable (e.g. "Stone Pickaxe 123/250").
     */
    public String selectedItemStatusLabel() {
        ItemStack preview = this.controller.getSelectedItemPreview();
        String label = this.controller.getSelectedItemLabel();
        if (preview != null && !preview.isEmpty() && preview.isDamageableItem()) {
            int max = preview.getMaxDamage();
            int durability = Math.max(0, max - preview.getDamageValue());
            return label + " " + durability + "/" + max;
        }
        return label;
    }
    /**
     * Draws text at a scaled size, useful for rendering labels that need to be
     * smaller or larger than the default font size.
     */
    private void drawScaledText(GuiGraphics g, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }
    /** Returns whether the player has a non-empty main hand item. */
    private boolean hasMainHandItem() {
        return this.minecraft != null
                && this.minecraft.player != null
                && !this.minecraft.player.getMainHandItem().isEmpty();
    }
    /**
     * Resolves the ItemStack to render as a cursor preview, based on the current
     * selection state: selected item, selected fluid, empty hand, or main hand item.
     */
    private ItemStack resolveCursorPreview() {
        if (this.controller.hasSelectedItem()) {
            return this.controller.getSelectedItemPreview();
        }
        if (this.controller.hasSelectedFluid()) {
            return this.controller.getSelectedFluidPreview();
        }
        if (this.controller.isEmptyHandSelected()) {
            return ItemStack.EMPTY;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack hand = this.minecraft.player.getMainHandItem();
        return hand.isEmpty() ? ItemStack.EMPTY : hand;
    }
    /**
     * Returns whether the interaction-wheel modifier (Alt + Ctrl) is currently held.
     * This modifier opens the interaction wheel when the primary action is triggered.
     */
    private boolean isWheelModifierDown() {
        if (this.minecraft == null) {
            return false;
        }
        long window = this.minecraft.getWindow().getWindow();
        return (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS)
                && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
    }
    /**
     * Returns whether the funnel cursor (a hopper/funnel icon) should be rendered
     * at the mouse position instead of the normal cursor preview.
     */
    private boolean shouldRenderFunnelCursor() {
        return this.controller.isEnabled()
                && this.controller.getMode() == BuilderMode.FUNNEL
                && this.controller.isFunnelEnabled()
                && !isSearchFocused()
                && !this.guidePanel.isOpen()
                && !this.interactionWheelPanel.isOpen();
    }
    /**
     * Toggles the native GLFW cursor visibility. Hides the OS cursor when the RTS
     * funnel cursor (custom texture) is being rendered, and restores it otherwise.
     */
    private void updateNativeCursorVisibility(boolean hide) {
        if (this.minecraft == null) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        if (hide) {
            if (this.nativeCursorStyle != RtsWindowPanel.ResizeCursor.DEFAULT) {
                GLFW.glfwSetCursor(window, 0L);
                this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            }
            if (this.nativeCursorHidden) {
                return;
            }
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
            this.nativeCursorHidden = true;
            return;
        }
        updateNativeCursor(RtsWindowPanel.ResizeCursor.DEFAULT);
    }

    private void updateNativeCursor(RtsWindowPanel.ResizeCursor cursor) {
        if (this.minecraft == null) {
            this.nativeCursorHidden = false;
            this.nativeCursorStyle = RtsWindowPanel.ResizeCursor.DEFAULT;
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        if (this.nativeCursorHidden) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            this.nativeCursorHidden = false;
        }
        RtsWindowPanel.ResizeCursor safeCursor = cursor == null
                ? RtsWindowPanel.ResizeCursor.DEFAULT
                : cursor;
        if (safeCursor == this.nativeCursorStyle) {
            return;
        }
        GLFW.glfwSetCursor(window, cursorHandle(safeCursor));
        this.nativeCursorStyle = safeCursor;
    }

    private long cursorHandle(RtsWindowPanel.ResizeCursor cursor) {
        return switch (cursor) {
            case RESIZE_EW -> {
                if (this.resizeEwCursor == 0L) {
                    this.resizeEwCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
                }
                yield this.resizeEwCursor;
            }
            case RESIZE_NS -> {
                if (this.resizeNsCursor == 0L) {
                    this.resizeNsCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR);
                }
                yield this.resizeNsCursor;
            }
            case RESIZE_NWSE -> {
                if (this.resizeNwseCursor == 0L) {
                    this.resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
                }
                yield this.resizeNwseCursor;
            }
            case RESIZE_NESW -> {
                if (this.resizeNeswCursor == 0L) {
                    this.resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
                }
                yield this.resizeNeswCursor;
            }
            case DEFAULT -> 0L;
        };
    }
    /** Delegates to the cursor picker to compute the ray direction from the current cursor position. */
    public Vec3 computeCursorRayDirection() {
        return this.cursorPicker.computeCursorRayDirection();
    }
    /** Delegates to the cursor picker to perform a block raycast from the current cursor position. */
    public BlockHitResult pickBlockHit() {
        return this.cursorPicker.pickBlockHit();
    }
    /** Delegates to the cursor picker to perform an interaction target pick (block or entity). */
    public InteractionTypes.InteractionTarget pickInteractionTarget(boolean includeFluidSource) {
        return this.cursorPicker.pickInteractionTarget(includeFluidSource);
    }
    /** Returns true if (mouseX, mouseY) is inside the rectangle (x, y, w, h). */
    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
    /** Returns a short label string for the given storage sort mode. */
    private static String sortLabel(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Qty";
            case MOD -> "Mod";
            case NAME -> "Name";
        };
    }
    /** Returns the localized label for the given shape fill mode. */
    public String fillModeLabel(ShapeBuildTypes.ShapeFillMode mode) {
        return this.shapeController.fillModeLabel(mode);
    }
    /** Returns the localized dimension label (e.g. "3x3x3") for the given build shape. */
    public static String shapeDimensionLabel(ClientRtsController.BuildShape shape) {
        return ScreenShapeController.shapeDimensionLabel(shape);
    }
    /** Returns a text description of the current shape's dimensions (e.g. "5x3x5"). */
    public String currentShapeSizeText() {
        return this.shapeController.currentShapeSizeText();
    }
    /** Returns a text description of the current shape's material cost (e.g. "40 blocks"). */
    public String currentShapeCostText() {
        return this.shapeController.currentShapeCostText();
    }
    /** Returns a text description of the pending shape build status (e.g. "Click to confirm"). */
    public String pendingShapeStatusText() {
        return this.shapeController.pendingShapeStatusText();
    }
    /** Returns the localized display label for the given build shape. */
    public String shapeLabel(ClientRtsController.BuildShape shape) {
        return this.shapeController.shapeLabel(shape);
    }

    /** Returns whether the Alt key is currently held down. */
    private boolean isAltDown() {
        if (this.minecraft == null) return false;
        long window = this.minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    /** Returns the last recorded mouse X coordinate. */
    private double currentMouseX() {
        return this.lastMouseX;
    }

    /** Returns the last recorded mouse Y coordinate. */
    private double currentMouseY() {
        return this.lastMouseY;
    }

}

