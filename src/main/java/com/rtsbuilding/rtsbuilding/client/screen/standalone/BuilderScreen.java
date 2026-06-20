package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuideTypes;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelLayouts;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
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
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;
/**
 * The main RTS Builder screen — the primary UI entry point for the RTS building mode.
 * <p>
 * This screen overlays the Minecraft game view and provides all RTS functionality
 * including quick building, vein-mining (ultimine), item storage browsing, the
 * shape-based building, blueprint placement, guide panels,
 * the gear/settings menu, and associated UI interactions.
 * <p>
 * <b>Dispatch design:</b> This class acts as a central coordinator. All UI logic
 * is delegated to dedicated sub-components. Lifecycle methods (render, mouseClicked,
 * keyPressed, etc.) are thin dispatchers that route to the appropriate handler.
 * <p>
 * The screen layout is divided into three main regions:
 * <ul>
 *   <li><b>Top Bar:</b> Mode switching, action buttons, shape selection, guide entry.</li>
 *   <li><b>Bottom Panel:</b> Item storage grid, crafting panel, blueprint panel.</li>
 *   <li><b>Overlays:</b> gear/settings menu, guide
 *       panel, dialogs (name entry, material list, craft quantity).</li>
 * </ul>
 * <p>
 * This class interacts with game logic through {@link ClientRtsController}.
 * All actual building operations are delegated to the server. UI state is
 * persisted via {@link RtsClientUiStateStore}.
 *
 * @see ClientRtsController
 * @see BuilderScreenConstants
 * @see ShapeGeometryUtil
 * @see StorageLinkDetailHandler
 * @see RtsScreenOverlayRenderer
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
    /** Windowed view for inspecting and unlinking bound storage blocks. */
    private final LinkedStoragePanel linkedStoragePanel = new LinkedStoragePanel();
    /** Windowed blueprint capture/placement controls. */
    private final BlueprintWindowPanel blueprintWindowPanel = new BlueprintWindowPanel();
    /** Windowed craft quantity picker. */
    private final RtsCraftQuantityWindowPanel craftQuantityWindowPanel = new RtsCraftQuantityWindowPanel();
    /** Windowed blueprint save/rename name prompt. */
    private final BlueprintNameWindowPanel blueprintNameWindowPanel = new BlueprintNameWindowPanel();
    /** Windowed blueprint material details prompt. */
    private final BlueprintMaterialWindowPanel blueprintMaterialWindowPanel = new BlueprintMaterialWindowPanel();
    /** Top bar panel with mode buttons, shape selection, and action controls. */
    private final TopBarPanel topBarPanel = new TopBarPanel();
    /** Bottom panel containing storage grid, crafting, blueprints, and pin slots. */
    private final BottomPanel bottomPanel = new BottomPanel();
    /** Controller managing shape-building sessions (geometry, fill mode, rotation, undo). */
    private final ScreenShapeController shapeController = new ScreenShapeController();
    /** Picker for raycasting blocks, entities, and blueprint placement targets from the cursor. */
    private final ScreenCursorPicker cursorPicker = new ScreenCursorPicker();
    /** Handler for camera movement, drag rotation, panning, and mining actions. */
    private final CameraInputHandler cameraInput = new CameraInputHandler();
    /** Guide/onboarding panel that explains UI elements and controls. */
    private final GuidePanel guidePanel = new GuidePanel();
    /** Gear (settings) menu panel with configuration toggles and sliders. */
    private final GearMenuPanel gearMenuPanel = new GearMenuPanel();
    /** Client-only persisted UI preferences for this screen. */
    private final RtsScreenUiStateManager uiStateManager;
    /** Lightweight overlay/popup renderer split out from the main screen. */
    private final RtsScreenOverlayRenderer overlayRenderer;
    /** Renders player health, food, armor and absorption bars. */
    private final PlayerStatusRenderer playerStatusRenderer;
    /** Front-to-back input routing for movable RTS windows. */
    private final RtsFloatingWindowLayer floatingWindowLayer;
    /** Handler for storage link detail action rendering and clicks. */
    private final StorageLinkDetailHandler storageLinkDetailHandler;
    /** Movable window panel for workflow progress and controls. */
    private final RtsWorkflowPanel workflowPanel = new RtsWorkflowPanel();
    /** Panel for reviewing and resuming suspended placement jobs. */
    private final RtsResumePlacementPanel resumePlacementPanel = new RtsResumePlacementPanel();
    /** Panel for reviewing and resuming suspended blueprint placement jobs. */
    private final RtsBlueprintResumePanel blueprintResumePanel = new RtsBlueprintResumePanel();
    /** Whether the user is currently dragging the input sensitivity slider. */
    private boolean draggingInputSensitivity = false;
    /** Whether the funnel hotkey (quick-activate funnel mode) is currently held down. */
    private boolean funnelHotkeyHeld = false;
    /** The builder mode that was active before the funnel hotkey was pressed, for restoration on release. */
    private BuilderMode modeBeforeFunnelHotkey = BuilderMode.INTERACT;
    /** Whether we are currently inside a fixed-RTS-scale render pass (for UI scaling). */
    private boolean fixedRtsScaleRenderPass = false;
    /** Whether we are currently inside a fixed-RTS-scale input pass (for UI scaling). */
    private boolean fixedRtsScaleInputPass = false;
    /** The actual render scale factor active during the current fixed-scale render pass. */
    private double activeRtsGuiRenderScale = 1.0D;
    /** Stable hover anchor above the left "RTS" label; keeps item tooltips from chasing the cursor. */
    /** Last recorded mouse X position, updated each render frame for input consistency. */
    private int lastMouseX = 0;
    /** Last recorded mouse Y position, updated each render frame for input consistency. */
    private int lastMouseY = 0;
    /**
     * When >= 0, the player is in "GUI binding" mode and must click a block in the world
     * to bind it to the specified slot. Reset to -1 after binding or on cancel.
     */
    private int pendingGuiBindSlot = -1;
    /**
     * 上一次 Ctrl+右键的时刻（ms），用于双击检测以触发「飞到目标上方」。
     */
    private long lastCtrlRightClickTime = 0;
    /**
     * Ctrl+右键双击时间阈值（ms）。
     */
    private static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300;
    /**
     * 冷却计数器，防止 Alt+Space 按键重复事件导致飞行开关快速抖动。
     */
    private int rtsFlightToggleCooldownTicks = 0;
    /**
     * Constructs the main RTS Builder screen.
     *
     * @param controller the central client-side RTS controller for bridging screen and game logic
     */
    public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
        this.uiStateManager = new RtsScreenUiStateManager(this.controller, this.shapeController, this.quickBuildPanel);
        this.overlayRenderer = new RtsScreenOverlayRenderer(this, this.controller, this.cursorPicker, this.bottomPanel);
        this.playerStatusRenderer = new PlayerStatusRenderer(this);
        this.storageLinkDetailHandler = new StorageLinkDetailHandler(this, this.controller, this.topBarPanel, this.linkedStoragePanel);
        this.floatingWindowLayer = new RtsFloatingWindowLayer(
                this.storageLinkDetailHandler,
                this.linkedStoragePanel,
                this.blueprintWindowPanel,
                this.blueprintMaterialWindowPanel,
                this.blueprintNameWindowPanel,
                this.craftQuantityWindowPanel,
                this.gearMenuPanel,
                this.guidePanel,
                this.quickBuildPanel,
                this.workflowPanel,
                this.resumePlacementPanel,
                this.blueprintResumePanel);
        this.uiStateManager.registerWindowPanel("settings", this.gearMenuPanel);
        this.uiStateManager.registerWindowPanel("blueprints", this.blueprintWindowPanel);
        this.uiStateManager.registerWindowPanel("guide", this.guidePanel);
        this.uiStateManager.registerWindowPanel("linked_storage", this.linkedStoragePanel);
        this.uiStateManager.registerWindowPanel("craft_quantity", this.craftQuantityWindowPanel);
        this.uiStateManager.registerWindowPanel("blueprint_name", this.blueprintNameWindowPanel);
        this.uiStateManager.registerWindowPanel("blueprint_materials", this.blueprintMaterialWindowPanel);
        this.uiStateManager.registerWindowPanel("workflow", this.workflowPanel);
        this.uiStateManager.registerWindowPanel("resume_placement", this.resumePlacementPanel);
        this.uiStateManager.registerWindowPanel("blueprint_resume", this.blueprintResumePanel);
        this.storageLinkDetailHandler.init(this, this.controller);
        this.guidePanel.init(this, this.controller);
        this.gearMenuPanel.init(this, this.controller);
        this.blueprintWindowPanel.init(this, this.controller);
        this.blueprintNameWindowPanel.init(this, this.controller);
        this.blueprintMaterialWindowPanel.init(this, this.controller);
        this.craftQuantityWindowPanel.init(this, this.controller);
        this.funnelBufferPanel.init(this, this.controller);
        this.quickBuildPanel.init(this, this.controller);
        this.workflowPanel.init(this, this.controller);
        this.resumePlacementPanel.init(this, this.controller);
        this.blueprintResumePanel.init(this, this.controller);
        this.linkedStoragePanel.init(this, this.controller);
        this.topBarPanel.init(this, this.controller);
        this.bottomPanel.init(this, this.controller);
        this.shapeController.init(this, this.controller);
        this.cursorPicker.init(this, this.controller, this.shapeController);
        this.cameraInput.init(this, this.controller);
    }
    /** Returns the Minecraft font renderer for use by sub-panels and utilities. */
    public Font font() {
        return this.font;
    }
    /** Triggers a red flash overlay on the screen to indicate the player took damage while in RTS mode. */
    public void triggerDamageFlash() {
        this.overlayRenderer.triggerDamageFlash();
    }
    /** Sets which funnel buffer entry is currently hovered, for tooltip rendering. */
    public void setHoveredFunnelBufferEntry(int index) {
        this.funnelBufferPanel.setHoveredEntry(index);
    }
    /** Toggles the visibility of the debug button in the top bar. */
    public void toggleDebugButton() {
        this.uiStateManager.toggleDebugButton();
    }
    /** Returns whether the debug button is currently visible in the top bar. */
    public boolean isDebugButtonVisible() {
        return this.uiStateManager.isDebugButtonVisible();
    }
    /** 切换容器覆盖层可见性。 */
    public void toggleContainerOverlayEnabled() { this.uiStateManager.toggleContainerOverlayEnabled(); }
    /** 切换覆盖层 Shift 导入。 */
    public void toggleOverlayShiftImportEnabled() { this.uiStateManager.toggleOverlayShiftImportEnabled(); }
    /** 切换存储就绪弹窗。 */
    public void toggleShowStorageReadyPopup() { this.uiStateManager.toggleShowStorageReadyPopup(); }
    /** 切换工作流面板显示。 */
    public void toggleShowWorkflowPanelEnabled() { this.uiStateManager.toggleShowWorkflowPanelEnabled(); }
    /** 切换安静刷新。 */
    public void toggleStorageRefreshQuietEnabled() { this.uiStateManager.toggleStorageRefreshQuietEnabled(); }
    /** 切换自动刷新。 */
    public void toggleStorageAutoRefreshEnabled() { this.uiStateManager.toggleStorageAutoRefreshEnabled(); }
    /** Returns whether the user is currently dragging the input sensitivity slider. */
    public boolean isDraggingInputSensitivity() {
        return this.draggingInputSensitivity;
    }
    /** Returns the current shape fill mode (e.g. FILL, HOLLOW, WIREFRAME). Delegates to the shape controller. */
    public ShapeFillMode getShapeFillMode() {
        return this.shapeController.getShapeFillMode();
    }
    /** Sets the shape fill mode via the shape controller. */
    public void setShapeFillMode(ShapeFillMode mode) {
        this.shapeController.setShapeFillMode(mode);
    }
    /** Returns the current shape rotation in degrees. Delegates to the shape controller. */
    public int getShapeRotateDegrees() {
        return this.shapeController.getShapeRotateDegrees();
    }
    /** Clears the current shape build session (pending preview, undo history). */
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
    /** Returns in-flight Range Destroy work areas that should stay visible beside the current cursor preview. */
    public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        return this.shapeController.getConfirmedRangeDestroyPreviews();
    }
    /** Ensures the current fill mode is compatible with the given shape type, adjusting if necessary. */
    public void ensureFillModeForShape(BuildShape shape) {
        this.shapeController.ensureFillModeForShape(shape);
    }
    /** Returns whether the quick-build panel is currently open. */
    public boolean isQuickBuildOpen() {
        return this.quickBuildPanel.isOpen();
    }
    /** Opens or closes the quick-build panel. */
    public void setQuickBuildOpen(boolean open) {
        this.quickBuildPanel.setOpen(open);
    }

    /**
     * 将当前 quick build 模式的独立状态（fillMode/rotation/lineConnected）
     * 同步到 ScreenShapeController 的活跃字段中。
     * <p>应在 {@link #init()} 中的 {@code applyStoredUiState()} 完成后调用，
     * 确保持久化值正确反映到活跃状态。
     */
    public void syncQuickBuildActiveState() {
        if (this.quickBuildPanel.isRangeDestroyMode()) {
            this.shapeController.applyDestroyStateAsActive();
        } else {
            this.shapeController.applyBuildStateAsActive();
        }
        ensureFillModeForShape(this.controller.getBuildShape());
    }
    /** Returns the Minecraft client instance for access by sub-panels and utilities. */
    public net.minecraft.client.Minecraft getMinecraft() {
        return this.minecraft;
    }

    /**
     * Returns the resume placement panel, so external handlers can open it.
     */
    public RtsResumePlacementPanel getResumePlacementPanel() {
        return this.resumePlacementPanel;
    }
    /** Returns the blueprint resume panel, so external handlers can open it. */
    public RtsBlueprintResumePanel getBlueprintResumePanel() {
        return this.blueprintResumePanel;
    }
    /** Returns the last recorded mouse X position (updated each render frame). */
    public double getCurrentMouseX() {
        return this.lastMouseX;
    }
    /** Returns the last recorded mouse Y position (updated each render frame). */
    public double getCurrentMouseY() {
        return this.lastMouseY;
    }
    /** Returns the storage search box (for filtering items in the storage grid). */
    public EditBox getSearchBox() {
        return this.searchBox;
    }
    /** Returns the craftable-items search box (for filtering in the crafting panel). */
    public EditBox getCraftSearchBox() {
        return this.craftSearchBox;
    }
    /** Initialises the screen: creates search boxes, applies persisted UI state, and requests craftables. */
    @Override
    protected void init() {
        super.init();
        // Enter the RTS scale frame so that clamps in applyStoredUiState use the virtual coordinate space (rather than the GUI-scaled width)
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        try {
            this.uiStateManager.applyStoredUiState();
        } finally {
            if (frame != null) {
                frame.close();
            }
        }
        // 持久化加载后，将当前模式的独立状态同步到活跃字段
        syncQuickBuildActiveState();
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
    /** Prevents the game from pausing when the RTS screen is open (since it is an overlay, not a menu). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    /** Pressing Escape closes this screen and returns to normal gameplay. */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    /**
     * Called when the screen is closed. Cleans up UI state, persists state,
     * resets input handlers, disables funnel mode, toggles camera if needed, and restores cursor.
     */
    @Override
    public void onClose() {
        this.shapeController.clearShapeBuildSession();
        this.controller.clearAreaMineSession();
        persistUiState();
        this.uiStateManager.flush();
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
        this.craftQuantityWindowPanel.close();
        this.overlayRenderer.updateNativeCursorVisibility(false);
    }
    /* Called when the screen is fully removed from the display stack. Resets camera vertical input and cursor. */
    @Override
    public void removed() {
        super.removed();
        this.cameraInput.resetCameraVerticalHeld();
        this.overlayRenderer.updateNativeCursorVisibility(false);
    }
    /*
      Called every client tick. Updates shape state,
      updates funnel target position, syncs craftables panel state, and checks if
      active mining input is still held (stopping if released).
     */
    @Override
    public void tick() {
        super.tick();
        // 每 tick 写入脏状态（无脏时零开销）
        this.uiStateManager.flush();
        enforceBlueprintPlacementModeLock();
        if (this.rtsFlightToggleCooldownTicks > 0) {
            this.rtsFlightToggleCooldownTicks--;
        }
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
      dialogs, blueprint capture, home selection, floating windows, area mine,
      left-click panels, and world click actions.

      @return true if the click was consumed by this screen, false otherwise
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            try {
                return mouseClicked(mouseX / frame.scale(), mouseY / frame.scale(), button);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        endFixedRtsScaleInput(frame);
        if (handleOverlayClicks(mouseX, mouseY, button)) return true;
        if (handleBlueprintCaptureClicks(mouseX, mouseY, button)) return true;
        if (handleHomeSelectionClicks(mouseX, mouseY, button)) return true;
        if (handleAreaMineClickBlock(mouseX, mouseY, button)) return true;
        if (handleLeftClickInteractions(mouseX, mouseY, button)) return true;
        if (handleWorldClickActions(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Handles left/right click in blueprint capture mode. */
    private boolean handleBlueprintCaptureClicks(double mouseX, double mouseY, int button) {
        if (!BlueprintPanel.isCaptureModeActive()) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.cameraInput.stopActiveMining();
            if (isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    if (!BlueprintPanel.isCaptureSelectionComplete()) {
                        BlueprintPanel.acceptCapturePoint(hit.getBlockPos());
                        return true;
                    }
                    if (BlueprintPanel.toggleCaptureBlockExclusion(hit.getBlockPos())) {
                        return true;
                    }
                }
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return false;
        }
        return true;
    }

    /** Handles click in home selection mode. */
    private boolean handleHomeSelectionClicks(double mouseX, double mouseY, int button) {
        if (!this.controller.isHomeSelectionMode()) {
            return false;
        }
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

    /** Handles click on floating windows through the shared window layer. */
    private boolean handleOverlayClicks(double mouseX, double mouseY, int button) {
        if (handleFloatingWindowClick(mouseX, mouseY, button)) {
            submitCraftQuantityWindowIfReady();
            return true;
        }
        return false;
    }

    /** Blocks non-break clicks in world area during area mine selection. */
    private boolean handleAreaMineClickBlock(double mouseX, double mouseY, int button) {
        if (BlueprintPanel.isCaptureModeActive()) {
            return false;
        }
        if (this.controller.getAreaMinePhase() == MiningOperationService.AREA_MINE_PHASE_NONE) {
            return false;
        }
        if (isWorldArea(mouseX, mouseY) && !CameraInputHandler.isBreakActionMouse(button)) {
            return true;
        }
        return false;
    }

    /** Handles left-click interactions: blueprint placement HUD, storage link, top bar, panels, gui bind, storage linking. */
    private boolean handleLeftClickInteractions(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        enforceBlueprintPlacementModeLock();
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
        return false;
    }

    /**
     * Handles world click actions: break action, primary/rotate mouse,
     * and pan/pick mouse actions.
     */
    private boolean handleWorldClickActions(double mouseX, double mouseY, int button) {
        if (CameraInputHandler.isBreakActionMouse(button)
                && CameraInputHandler.canStartBreakActionOnMouse(button)
                && this.cameraInput.startMiningAt(mouseX, mouseY, button, false)) {
            return true;
        }
        boolean primaryMouse = CameraInputHandler.isPrimaryActionMouse(button);
        boolean rotateMouse = CameraInputHandler.isRotateDragActionMouse(button);
        boolean panMouse = CameraInputHandler.isPanDragActionMouse(button);
        boolean pickMouse = CameraInputHandler.isPickBlockActionMouse(button);
        /*
         * After key binding swap:
         *   Right button → primary action + camera pan (movement)
         *   Middle button → camera rotation + pick block
         */
        if (primaryMouse || rotateMouse) {
            if (isSearchFocused()) {
                blurSearchFocus();
            }
            if (primaryMouse && this.pendingGuiBindSlot >= 0 && isWorldArea(mouseX, mouseY)) {
                return true;
            }
            if (primaryMouse && !panMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.LINK_STORAGE) {
                return true;
            }
            if (primaryMouse && isInsideBottomPanel(mouseX, mouseY)) {
                return this.bottomPanel.handleRightClick(mouseX, mouseY);
            }
            if (primaryMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.ROTATE && !panMouse) {
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
        if (panMouse || pickMouse) {
            this.cameraInput.beginMiddlePress(isWorldArea(mouseX, mouseY), button, panMouse, pickMouse);
            return true;
        }
        return false;
    }
    @Override
    /**
     * Handles mouse release with RTS GUI scale remapping. Routes release events to
     * open dialogs, dragging state, floating windows, and camera input handlers.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            try {
                return mouseReleased(mouseX / frame.scale(), mouseY / frame.scale(), button);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        endFixedRtsScaleInput(frame);
        if (this.draggingInputSensitivity) {
            this.draggingInputSensitivity = false;
            return true;
        }
        if (handleFloatingWindowRelease(mouseX, mouseY, button)) {
            return true;
        }
        if (this.cameraInput.isLeftMiningActive() && !this.cameraInput.isKeyboardMining() && button == this.cameraInput.getActiveMiningMouseButton()) {
            this.cameraInput.stopActiveMining();
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
     * open dialogs, sensitivity slider dragging, floating windows, camera drag handlers,
     * and search box focus logic.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            try {
                return mouseDragged(mouseX / frame.scale(), mouseY / frame.scale(), button, dragX / frame.scale(), dragY / frame.scale());
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        endFixedRtsScaleInput(frame);
        if (this.draggingInputSensitivity) {
            this.cameraInput.updateInputSensitivityFromMouse(mouseX);
            return true;
        }
        if (handleFloatingWindowDrag(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }

        // Block all mouse drag operations while area mine selection is active
        if (!BlueprintPanel.isCaptureModeActive()
                && this.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
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
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            try {
                mouseMoved(mouseX / frame.scale(), mouseY / frame.scale());
                return;
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        endFixedRtsScaleInput(frame);
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
     * shape placement confirmation, blueprint placement,
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
                    && !BlueprintPanel.isCaptureSelectionComplete()
                    && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlueprintPanel.acceptCapturePoint(hit.getBlockPos());
                }
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
        if (!rangeDestroyMode && this.shapeController.tryConfirmPendingShapeBuild(forcePlace)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.hasSelectedBlueprint()) {
            if (BlueprintPanel.hasPinnedPreview()) {
                BlueprintPanel.confirmPinnedPreview();
                return true;
            }
            BlockHitResult blueprintHit = this.cursorPicker.pickBlueprintPlacementHit();
            if (blueprintHit != null) {
                BlockPos anchor = BlueprintPanel.anchorForCursorTarget(
                        this.cursorPicker.resolveBlueprintAnchor(blueprintHit));
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
                && this.controller.getBuildShape() != BuildShape.BLOCK
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
                this.controller.placeSelected(target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
                this.shapeController.recordSinglePlacementForUndo(
                        target.blockHit(),
                        InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                        "",
                        getSelectedToolSlot());
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
                && this.controller.getBuildShape() == BuildShape.BLOCK;
    }
    @Override
    /**
     * Handles mouse scroll with RTS GUI scale remapping. Routes scroll to open
     * dialogs, gear menu, wheel panels, guide panel, bottom panel, shape height
     * previews, rotation mode, and item slot scrolling.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            try {
                return mouseScrolled(mouseX / frame.scale(), mouseY / frame.scale(), scrollX, scrollY);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        endFixedRtsScaleInput(frame);
        if (handleFloatingWindowScroll(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (BlueprintPanel.mouseScrolledCaptureHeight(scrollY)) {
            return true;
        }
        if (isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleMouseScrolled(mouseX, mouseY, scrollY);
        }
        if (!isSearchFocused() && this.shapeController.handleShapeHeightMouseScrolled(scrollY)) {
            return true;
        }
        // While area mine selection is active: scroll wheel adjusts height in NEED_HEIGHT phase, blocked otherwise
        if (!BlueprintPanel.isCaptureModeActive()
                && this.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
            if (this.controller.getAreaMinePhase() == MiningOperationService.AREA_MINE_PHASE_NEED_HEIGHT) {
                int delta = scrollY > 0.0D ? 1 : -1;
                if (isAltDown()) {
                    delta *= 4;
                }
                this.controller.adjustAreaMineHeightOffset(delta);
            }
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
     * Handles key press events. Dispatches to dialogs, blueprint, overlay, world interaction,
     * search box, tool slot, and sensitivity handlers in priority order.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleOverlayKeys(keyCode, scanCode, modifiers)) return true;
        if (handleBlueprintKeys(keyCode, scanCode, modifiers)) return true;
        if (handleHomeSelectionKey(keyCode)) return true;
        if (handleWorldInteractionKeys(keyCode, scanCode, modifiers)) return true;
        if (handleSearchFocusKeys(keyCode, scanCode, modifiers)) return true;
        if (handleToolSlotKeys(keyCode, scanCode, modifiers)) return true;
        if (handleSensitivityKeys(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Dispatches key to blueprint capture mode and blueprint panel. */
    private boolean handleBlueprintKeys(int keyCode, int scanCode, int modifiers) {
        if (BlueprintPanel.isCaptureModeActive() && BlueprintPanel.keyPressed(keyCode, scanCode, this.controller)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                && BlueprintPanel.keyPressed(keyCode, scanCode, this.controller)) {
            return true;
        }
        return false;
    }

    /** Handles Esc in home selection mode. */
    private boolean handleHomeSelectionKey(int keyCode) {
        if (!this.controller.isHomeSelectionMode()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
        }
        return true;
    }

    /** Dispatches key to floating windows. */
    private boolean handleOverlayKeys(int keyCode, int scanCode, int modifiers) {
        if (this.floatingWindowLayer.keyPressed(keyCode, scanCode, modifiers)) {
            submitCraftQuantityWindowIfReady();
            return true;
        }
        return false;
    }

    /**
     * Dispatches key to world interaction: area mine, gui bind, undo, camera vertical,
     * mining, pick block, primary action, mode switch, funnel hotkey, quick drop,
     * shape rotation, and craft terminal.
     */
    private boolean handleWorldInteractionKeys(int keyCode, int scanCode, int modifiers) {
        // While area mine selection is active, block all world interaction keys except the break key
        if (!BlueprintPanel.isCaptureModeActive()
                && this.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
            if (!ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
                return true;
            }
        }
        if (this.pendingGuiBindSlot >= 0 && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.pendingGuiBindSlot = -1;
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            return this.shapeController.undoLastPlacementBatch();
        }
        // Alt+Space: toggle creative flight for the player entity in RTS mode
        if (!isSearchFocused() && (modifiers & GLFW.GLFW_MOD_ALT) != 0 && keyCode == GLFW.GLFW_KEY_SPACE) {
            if (this.rtsFlightToggleCooldownTicks <= 0) {
                this.rtsFlightToggleCooldownTicks = 10;
                handleRtsFlightToggle();
            }
            return true;
        }
        if (!isSearchFocused() && this.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, true)) {
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
                return false; // let super handle it for recipe viewer keybinds
            }
            this.shapeController.rotateShapeByStep(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (!isSearchFocused()
                && ClientKeyMappings.OPEN_CRAFT_TERMINAL.matches(keyCode, scanCode)
                && !hasControlDown()) {
            persistUiState();
            this.controller.openCraftTerminal();
            return true;
        }
        return false;
    }

    /** Handles search box key events: clear on Escape, typing, and Enter submission. */
    private boolean handleSearchFocusKeys(int keyCode, int scanCode, int modifiers) {
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
        return false;
    }

    /** Handles tool slot selection (1-9) and pin-quick-slot keybinds. */
    private boolean handleToolSlotKeys(int keyCode, int scanCode, int modifiers) {
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
        return false;
    }

    /** Handles input sensitivity adjustment keys. */
    private boolean handleSensitivityKeys(int keyCode, int scanCode) {
        if (ClientKeyMappings.DECREASE_SENSITIVITY.matches(keyCode, scanCode)) {
            this.controller.decreaseRotateSensitivity();
            return true;
        }
        if (ClientKeyMappings.INCREASE_SENSITIVITY.matches(keyCode, scanCode)) {
            this.controller.increaseRotateSensitivity();
            return true;
        }
        return false;
    }
    @Override
    /** Handles key release for funnel hotkey and camera vertical movement states. */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
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
     * Toggles the player's creative flight state while in RTS mode.
     * Only works if the player has the {@code mayfly} ability (creative/spectator mode).
     * When enabling flight while on ground, triggers a jump first to lift off,
     * since vanilla Minecraft requires the player to be airborne to enter
     * flying state even with {@code abilities.flying = true}.
     * Called when Alt+Space is pressed in the RTS builder screen.
     */
    private void handleRtsFlightToggle() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!this.minecraft.player.getAbilities().mayfly) return;

        boolean wasFlying = this.minecraft.player.getAbilities().flying;
        this.minecraft.player.getAbilities().flying = !wasFlying;

        // When enabling flight while on ground, apply a jump impulse to lift off.
        // Vanilla MC won't actually start flying if the player stays on ground.
        if (!wasFlying && this.minecraft.player.onGround()) {
            this.minecraft.player.jumpFromGround();
        }

        this.minecraft.player.onUpdateAbilities();
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
        this.controller.setMode(mode);
        this.controller.setFunnelEnabled(funnelEnabled);
        this.funnelHotkeyHeld = false;
        return true;
    }
    @Override
    /** Handles character-typed input, routing to quantity dialog, blueprint name dialog, search boxes, and ultimine limit input. */
    public boolean charTyped(char codePoint, int modifiers) {
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
        resetHoverStates();
        guiGraphics.fill(0, 0, this.width, TOP_H, 0xC0101116);
        if (this.controller.isHomeSelectionMode()) {
            this.overlayRenderer.renderHomeSelectionOverlay(guiGraphics, mouseX, mouseY);
            this.overlayRenderer.renderDamageFlash(guiGraphics);
            return;
        }
        this.topBarPanel.render(guiGraphics, mouseX, mouseY);
        if (this.controller.isPlayerStatusOverlayEnabled()) {
            this.playerStatusRenderer.render(guiGraphics);
        }
        this.storageLinkDetailHandler.updateVisibility(mouseX, mouseY);
        this.bottomPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        this.funnelBufferPanel.render(guiGraphics, mouseX, mouseY);
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            BlockHitResult hit = isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
            BlueprintPanel.updateCaptureHoverPoint(hit == null ? null : hit.getBlockPos());
        }
        this.blueprintWindowPanel.syncWithBlueprintState();
        this.blueprintMaterialWindowPanel.syncWithBlueprintState();
        this.blueprintNameWindowPanel.syncWithBlueprintState();
        this.floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        this.floatingWindowLayer.renderFloatingWindowOverlays(guiGraphics, mouseX, mouseY);
        this.overlayRenderer.renderQuestDetectPopup(guiGraphics);
        this.overlayRenderer.renderStorageScanPopup(guiGraphics);
        renderHoveredItemTooltips(guiGraphics, mouseX, mouseY);
        this.overlayRenderer.updateNativeCursor(this.floatingWindowLayer.resizeCursorAt(mouseX, mouseY));
        this.bottomPanel.renderCraftFeedback(guiGraphics);
        this.overlayRenderer.renderDamageFlash(guiGraphics);
    }

    /** Resets all panel hover states before each render frame. */
    private void resetHoverStates() {
        this.shapeController.setShapeCursorY(this.lastMouseY);
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
    }

    /**
     * Renders tooltips and cursor overlay for hovered items when no modal is open.
     * Handles creative entries, storage entries, recent items, fluids, craftables,
     * funnel buffer, GUI bind slots, empty hand slot, and discoverability hints.
     */
    private void renderHoveredItemTooltips(GuiGraphics g, int mouseX, int mouseY) {
        boolean modalOpen = isMouseOverFloatingWindow(mouseX, mouseY);
        boolean placementSelectionActive = this.controller.hasSelectedItem() || this.controller.hasSelectedFluid();
        if (!modalOpen) {
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredCreativeEntry >= 0) {
                var entry = this.bottomPanel.getCreativeEntryForTooltip(this.bottomPanel.hoveredCreativeEntry);
                if (entry != null) {
                    renderLeftDockedTooltip(g, entry.stack());
                }
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredEntry >= 0
                    && this.bottomPanel.hoveredEntry < this.controller.getStorageEntries().size()) {
                var entry = this.controller.getStorageEntries().get(this.bottomPanel.hoveredEntry);
                renderLeftDockedTooltip(g, entry.stack());
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredRecentEntry >= 0
                    && this.bottomPanel.hoveredRecentEntry < this.controller.getRecentEntries().size()) {
                var entry = this.controller.getRecentEntries().get(this.bottomPanel.hoveredRecentEntry);
                if (!entry.preview().isEmpty()) {
                    renderLeftDockedTooltip(g, entry.preview());
                } else {
                    renderLeftDockedTooltip(g, Component.literal(entry.label()));
                }
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredFluidEntry >= 0
                    && this.bottomPanel.hoveredFluidEntry < this.controller.getFluidEntries().size()) {
                var fluid = this.controller.getFluidEntries().get(this.bottomPanel.hoveredFluidEntry);
                if (!fluid.preview().isEmpty()) {
                    renderLeftDockedTooltip(g, fluid.preview());
                } else {
                    renderLeftDockedTooltip(g, Component.literal(fluid.label()));
                }
            }
            if (this.bottomPanel.hoveredCraftableEntry >= 0 && this.bottomPanel.hoveredCraftableEntry < this.controller.getCraftableEntries().size()) {
                var entry = this.controller.getCraftableEntries().get(this.bottomPanel.hoveredCraftableEntry);
                renderLeftDockedTooltip(g, entry.stack());
                String detail = entry.craftable()
                        ? text("screen.rtsbuilding.tooltip.craft_choose")
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    renderLeftDockedTooltipDetail(g, detail, entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
                }
            }
            if (this.funnelBufferPanel.getHoveredEntry() >= 0 && this.funnelBufferPanel.getHoveredEntry() < this.controller.getFunnelBufferEntries().size()) {
                var entry = this.controller.getFunnelBufferEntries().get(this.funnelBufferPanel.getHoveredEntry());
                renderLeftDockedTooltip(g, entry.stack());
                renderLeftDockedTooltipDetail(g, text("screen.rtsbuilding.tooltip.buffered", entry.count()), 0xFFD8B8);
            }
            if (this.bottomPanel.hoveredGuiBindingSlot >= 0 && this.bottomPanel.hoveredGuiBindingSlot < this.controller.getGuiBindingCount()) {
                String detail = this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                        ? this.controller.getGuiBindingLabel(this.bottomPanel.hoveredGuiBindingSlot)
                        : text("screen.rtsbuilding.tooltip.gui_empty");
                renderLeftDockedTooltip(g, Component.literal(detail));
                renderLeftDockedTooltipDetail(
                        g,
                        this.pendingGuiBindSlot == this.bottomPanel.hoveredGuiBindingSlot
                                ? text("screen.rtsbuilding.tooltip.gui_cancel_bind")
                                : (this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                                        ? text("screen.rtsbuilding.tooltip.gui_bound")
                                        : text("screen.rtsbuilding.tooltip.gui_unbound")),
                        0xFFCFE3F7);
            }
            if (this.bottomPanel.hoveredEmptyHandSlot) {
                renderLeftDockedTooltip(g, Component.translatable("screen.rtsbuilding.tooltip.empty_hand"));
                renderLeftDockedTooltipDetail(g, text("screen.rtsbuilding.tooltip.empty_hand_detail"), 0xFFD8B8);
            }
            renderDiscoverabilityTooltips(g, mouseX, mouseY);
            boolean funnelCursor = shouldRenderFunnelCursor();
            this.overlayRenderer.updateNativeCursorVisibility(funnelCursor);
            if (funnelCursor) {
                g.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
            } else if (this.pendingGuiBindSlot >= 0) {
                drawGuiBindCursor(g, mouseX, mouseY);
            } else {
                ItemStack cursorPreview = resolveCursorPreview();
                if (!cursorPreview.isEmpty() && !isSearchFocused()
                        && !isMouseOverFloatingWindow(mouseX, mouseY)) {
                    g.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
                }
            }
        } else {
            this.overlayRenderer.updateNativeCursorVisibility(false);
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
     * Begins a fixed RTS GUI scale input frame. Returns the scale frame if scaling
     * is needed (caller must call {@link #endFixedRtsScaleInput}), or null/unit-scale
     * frame if no remapping is required.
     */
    private RtsUiScaleFrame beginFixedRtsScaleInput() {
        if (this.fixedRtsScaleInputPass) return null;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
        }
        return frame;
    }

    /**
     * Ends a fixed RTS GUI scale input frame, resetting the pass flag and restoring
     * original window dimensions.
     */
    private void endFixedRtsScaleInput(RtsUiScaleFrame frame) {
        if (frame == null) return;
        this.fixedRtsScaleInputPass = false;
        frame.close();
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
        double renderScale = this.uiStateManager.fixedRtsGuiScale() / currentScale;
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
    /** Returns true if any recipe viewer mod (JEI, EMI, REI) is loaded. */
    private static boolean hasRecipeViewerLoaded() {
        return ModList.get().isLoaded("jei")
                || ModList.get().isLoaded("emi")
                || ModList.get().isLoaded("roughlyenoughitems");
    }

    /**
     * Persists the current UI state (shape, fill mode, rotation, panel toggles,
     * camera preferences, debug visibility) to {@link RtsClientUiStateStore}.
     */
    public void persistUiState() {
        this.uiStateManager.persistUiState();
    }
    /** Adjusts the fixed RTS GUI scale by a delta and persists the change. */
    public void adjustRtsGuiScale(double delta) {
        this.uiStateManager.adjustRtsGuiScale(delta);
    }
    /** Returns the current fixed RTS GUI scale value (e.g. 2.0 for 2x). */
    public double getRtsGuiScale() {
        return this.uiStateManager.fixedRtsGuiScale();
    }
    /** Returns the current RTS GUI scale as a human-readable label (e.g. "1.0x", "1.5x"). */
    public String rtsGuiScaleLabel() {
        return this.uiStateManager.rtsGuiScaleLabel();
    }
    /** Resolves the layout metadata for the quick-build panel (used for positioning and hit-testing). */
    public PanelLayouts.QuickBuildPanelLayout resolveQuickBuildPanelLayout() {
        return this.quickBuildPanel.resolveLayout();
    }

    /** Returns the floating window layer (for snap/positioning). */
    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    /** Returns true when quick-build is showing the range-destroy workflow. */
    public boolean isQuickBuildRangeDestroyMode() {
        return this.quickBuildPanel.isOpen() && this.quickBuildPanel.isRangeDestroyMode();
    }
    /** Returns true when Quick Build range-destroy is using the connected-chain shape. */
    public boolean isQuickBuildRangeDestroyChainMode() {
        return this.quickBuildPanel.isOpen() && this.quickBuildPanel.isRangeDestroyChainMode();
    }
    /** Player-facing shape label for the top status row. */
    public String activeQuickBuildShapeLabel() {
        if (isQuickBuildRangeDestroyChainMode()) {
            return text("screen.rtsbuilding.shape.chain");
        }
        return shapeLabel(this.controller.getBuildShape());
    }
    /** Handles the left-click shape selection flow for Quick Build range destroy. */
    public boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY) {
        if (!isQuickBuildRangeDestroyMode() || isQuickBuildRangeDestroyChainMode() || !isWorldArea(mouseX, mouseY)) {
            return false;
        }
        if (this.shapeController.tryConfirmPendingRangeDestroy()) {
            return true;
        }
        InteractionTypes.InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
        if (target != null && target.blockHit() != null) {
            this.shapeController.selectRangeDestroyShape(target.blockHit(), mouseY, target.rayDir());
            return true;
        }
        return true;
    }
    /** Sets the quick-build window mode and persists the UI state. */
    public void setQuickBuildMode(QuickBuildMode mode) {
        this.quickBuildPanel.setMode(mode);
    }
    /** Returns the current ultimine block limit. */
    public int getUltimineLimit() {
        return this.quickBuildPanel.getChainDestroyLimit();
    }
    /** Returns true if currently in area mine height selection phase (NEED_HEIGHT). */
    public boolean isAreaMineHeightPreview() {
        if (!isQuickBuildRangeDestroyMode()) {
            return false;
        }
        int phase = this.controller.getAreaMinePhase();
        return phase == MiningOperationService.AREA_MINE_PHASE_NEED_SECOND
                || phase == MiningOperationService.AREA_MINE_PHASE_NEED_HEIGHT;
    }
    /** Returns the number of available undo steps for shape placement. */
    public int getShapeUndoSize() {
        return this.shapeController.getShapeUndoSize();
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
        this.quickBuildPanel.toggleOpen();
    }

    /** Opens the window-layer craft quantity picker for a craftable entry. */
    public void openCraftQuantityWindow(CraftableEntry entry) {
        this.craftQuantityWindowPanel.open(entry);
    }

    /** Submits any craft request confirmed through the window-layer picker. */
    public void submitCraftQuantityWindowIfReady() {
        RtsCraftQuantityWindowPanel.Request request = this.craftQuantityWindowPanel.consumePendingRequest();
        if (request != null) {
            this.controller.craftRecipeToLinked(request.recipeId(), request.craftCount());
        }
    }

    private boolean handleFloatingWindowClick(double mouseX, double mouseY, int button) {
        return this.floatingWindowLayer.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleFloatingWindowDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.floatingWindowLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean handleFloatingWindowRelease(double mouseX, double mouseY, int button) {
        boolean handled = this.floatingWindowLayer.mouseReleased(mouseX, mouseY, button);
        if (this.floatingWindowLayer.consumeAnyBoundsDirty()) {
            persistUiState();
            return true;
        }
        submitCraftQuantityWindowIfReady();
        return handled;
    }

    private boolean handleFloatingWindowScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.floatingWindowLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean isMouseOverFloatingWindow(double mouseX, double mouseY) {
        return this.floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
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
        return this.craftQuantityWindowPanel.isOpen();
    }
    /**
     * Activates the funnel hotkey: stops mining, clears shape preview,
     * saves the current mode, and switches to funnel mode with funnel enabled.
     */
    private void activateFunnelHotkey() {
        if (isBlueprintPlacementModeLocked()) {
            enforceBlueprintPlacementModeLock();
            return;
        }
        this.cameraInput.stopActiveMining();
        this.shapeController.clearShapeBuildSession();
        if (this.controller.getMode() != BuilderMode.FUNNEL) {
            this.modeBeforeFunnelHotkey = this.controller.getMode();
        }
        this.controller.setMode(BuilderMode.FUNNEL);
        this.controller.setFunnelEnabled(true);
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
        this.controller.setFunnelEnabled(false);
        this.controller.setMode(BuilderMode.INTERACT);
        this.funnelHotkeyHeld = false;
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
    /** Copies a debug snapshot string to the system clipboard and shows a confirmation message. */
    public void copyDebugSnapshotToClipboard() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(buildDebugSnapshot());
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("screen.rtsbuilding.debug.copied"), true);
        }
    }
    /**
     * Builds a multi-line debug snapshot string containing the current screen state,
     * controller mode, storage info, shape settings, camera preferences, and player data.
     */
    private String buildDebugSnapshot() {
        StringBuilder out = new StringBuilder(512);
        out.append("RTSBuilding debug snapshot\n");
        out.append("screen=").append(this.width).append('x').append(this.height)
                .append(" uiScale=").append(rtsGuiScaleLabel()).append('\n');
        out.append("mode=").append(this.controller.getMode())
                .append(" topAction=").append(this.topBarPanel.topActionForMode())
                .append(" quickBuild=").append(this.quickBuildPanel.isOpen())
                .append(" quickDestroy=").append(isQuickBuildRangeDestroyMode())
                .append(" debugButton=").append(this.uiStateManager.isDebugButtonVisible())
                .append(" invertPanDragX=").append(this.controller.isInvertPanDragX())
                .append(" invertPanDragY=").append(this.controller.isInvertPanDragY())
                .append(" smoothCamera=").append(this.controller.isSmoothCamera())
                .append('\n');
        out.append("storageLinked=").append(this.controller.isStorageLinked())
                .append(" name=").append(this.controller.getLinkedStorageName())
                .append(" page=").append(this.controller.getStoragePage() + 1)
                .append('/').append(Math.max(1, this.controller.getStorageTotalPages()))
                .append(" entries=").append(this.controller.getStorageEntries().size())
                .append('/').append(this.controller.getStorageTotalEntries())
                .append(" revision=").append(this.controller.getStorageRevision())
                .append('\n');
        out.append("storageSearch=\"").append(this.controller.getStorageSearch())
                .append("\" category=").append(this.controller.getStorageCategory())
                .append(" sort=").append(this.controller.getStorageSort())
                .append(this.controller.isStorageSortAscending() ? ":asc" : ":desc")
                .append('\n');
        out.append("selectedItem=").append(this.controller.getSelectedItemId())
                .append(" label=\"").append(this.controller.getSelectedItemLabel())
                .append("\" selectedFluid=").append(this.controller.getSelectedFluidId())
                .append(" fluidLabel=\"").append(this.controller.getSelectedFluidLabel()).append("\"\n");
        out.append("shape=").append(this.controller.getBuildShape())
                .append(" fill=").append(this.shapeController.getShapeFillMode())
                .append(" rotation=").append(this.shapeController.getShapeRotateDegrees())
                .append(" pending=").append(this.shapeController.pendingShapeStatusText())
                .append('\n');
        out.append("cameraHeadStart=").append(this.controller.isStartCameraAtPlayerHead())
                .append(" allowPlacedRecovery=").append(this.controller.isAllowPlacedBlockRecovery())
                .append(" chunkCurtain=").append(this.controller.isChunkCurtainVisible())
                .append(" funnel=").append(this.controller.isFunnelEnabled())
                .append('\n');
        if (this.minecraft != null && this.minecraft.player != null) {
            BlockPos pos = this.minecraft.player.blockPosition();
            out.append("player=").append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ())
                    .append(" creative=").append(this.minecraft.player.isCreative())
                    .append('\n');
        }
        return out.toString();
    }
    /**
     * Renders discoverability tooltips for various UI elements when hovered:
     * undo key hint, quick-build toggle, quick-build cancel area.
     */
    private void renderDiscoverabilityTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (isMouseOverFloatingWindow(mouseX, mouseY)) {
            return;
        }
        if (this.storageLinkDetailHandler.renderStatusTooltip(g, mouseX, mouseY)) {
            return;
        }
        if (mouseY >= 42 && mouseY <= 56) {
            g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.undo_redo_keys"), mouseX, mouseY);
            return;
        }
        for (TopBarTypes.TopBarButtonLayout button : this.topBarPanel.buildTopBarButtonLayouts()) {
            if (button.id() == TopBarTypes.TopBarButtonId.QUICK_BUILD
                    && inside(mouseX, mouseY, button.x(), 4, button.width(), TOP_BUTTON_H)) {
                g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.quick_build_toggle"), mouseX, mouseY);
                return;
            }
        }
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
     * Returns whether the given screen coordinates are within the "world area" —
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
            return RtsAe2IconResolver.resolveGuiBindingIconItemId(this.minecraft.level, pos, hit.getDirection(), "");
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
            anchor = BlueprintPanel.anchorForCursorTarget(
                    this.cursorPicker.resolveBlueprintAnchor(this.cursorPicker.pickBlueprintPlacementHit()));
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
        List<BlockPos> raw = RtsUltimineCollector.collect(
                this.minecraft.level,
                seed,
                getUltimineLimit(),
                (pos, state, originalState) -> {
                    if (state.isAir()
                            || !state.getFluidState().isEmpty()
                            || (!creative && state.getDestroySpeed(this.minecraft.level, pos) < 0.0F)) {
                        return false;
                    }
                    return state.getBlock() == originalState.getBlock();
                });
        return filterToBounds(raw);
    }

    private List<BlockPos> filterToBounds(List<BlockPos> blocks) {
        if (!this.controller.hasBounds() || blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        return RenderingUtil.filterBlocksWithinBounds(blocks,
                this.controller.getAnchorX(), this.controller.getAnchorZ(), this.controller.getMaxRadius());
    }

    private double currentRtsGuiRenderScale() {
        if (this.minecraft == null || this.minecraft.getWindow() == null || this.width <= 0) {
            return 1.0D;
        }
        double currentScale = this.minecraft.getWindow().getScreenWidth() / (double) Math.max(1, this.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return 1.0D;
        }
        double renderScale = this.uiStateManager.fixedRtsGuiScale() / currentScale;
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
        // 移动玩家键位默认是 Ctrl+右键；双击仍保留“飞到目标上方”的精确落点。
        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - this.lastCtrlRightClickTime) < CTRL_DOUBLE_CLICK_THRESHOLD_MS;
        this.lastCtrlRightClickTime = now;

        BlockHitResult hit = this.cursorPicker.pickBlockHit();
        if (hit != null) {
            if (isDoubleClick) {
                this.lastCtrlRightClickTime = 0;
                RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
            } else {
                RtsClientPathfinding.goTo(hit.getBlockPos());
            }
        }
        return true;
    }
    /** Retired interaction-wheel hook kept so older extracted callers remain harmless. */
    public void closeInteractionWheel() {
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
     * Returns whether the funnel cursor (a hopper/funnel icon) should be rendered
     * at the mouse position instead of the normal cursor preview.
     */
    private boolean shouldRenderFunnelCursor() {
        return this.controller.isEnabled()
                && this.controller.getMode() == BuilderMode.FUNNEL
                && this.controller.isFunnelEnabled()
                && !isSearchFocused()
                && !isMouseOverFloatingWindow(currentMouseX(), currentMouseY());
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
    /** Exposes the shape controller for direct access by sub-panels. */
    public ScreenShapeController getShapeController() {
        return this.shapeController;
    }
    /** Returns the localized label for the given shape fill mode. */
    public String fillModeLabel(ShapeFillMode mode) {
        return this.shapeController.fillModeLabel(mode);
    }
    /** Returns the localized dimension label (e.g. "3x3x3") for the given build shape. */
    public static String shapeDimensionLabel(BuildShape shape) {
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
    public String shapeLabel(BuildShape shape) {
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

