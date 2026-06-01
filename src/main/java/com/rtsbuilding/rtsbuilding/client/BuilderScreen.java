package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.blueprint.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;

public final class BuilderScreen extends Screen {
    private static final int TOP_H = 52;
    private static final int DEFAULT_BOTTOM_H = 110;
    private static final int MIN_BOTTOM_H = 72;
    private static final int MAX_BOTTOM_H = 320;
    private static final int BOTTOM_PANEL_PADDING = 8;
    private static final int BOTTOM_PANEL_HEADER_H = 18;
    private static final int MIN_STORAGE_GRID_ROWS = 2;
    private static final int GRID_BOTTOM_PADDING = 4;
    private static final int SLOT = 22;
    private static final int HOTBAR_SLOT = 18;
    private static final int HOTBAR_PITCH = 20;
    private static final int TOOL_HOTBAR_ITEM_SLOTS = 8;
    private static final int EMPTY_HAND_BUTTON_INDEX = 8;
    private static final int TOOL_AREA_H = HOTBAR_SLOT;
    private static final int SEARCH_CLEAR_SIZE = 12;
    private static final int SORT_BUTTON_SIZE = 16;
    private static final int CRAFT_PANEL_W = 126;
    private static final int CRAFT_PANEL_GAP = 6;
    private static final int CRAFT_PANEL_COLS = 4;
    private static final int CRAFT_PANEL_SLOT = 18;
    private static final int CRAFT_PANEL_PITCH = 20;
    private static final int CRAFT_PANEL_SEARCH_H = 12;
    private static final int CRAFT_PANEL_APPLY_W = 18;
    private static final int CRAFT_PANEL_TOGGLE_W = 38;
    private static final int CRAFT_DOCK_C_SIZE = 18;
    private static final int CRAFT_DOCK_SLOT_SIZE = 10;
    private static final int CRAFT_DOCK_GAP = 2;
    private static final int STORAGE_RECENT_GAP = 6;
    private static final int CATEGORY_W = 124;
    private static final int CATEGORY_ROW_H = 11;
    private static final float CATEGORY_TEXT_SCALE = 0.84F;
    private static final int INTERACT_WHEEL_PAGE_SIZE = 10;
    private static final int INTERACT_WHEEL_RADIUS = 68;
    private static final int INTERACT_WHEEL_SLOT = 18;
    private static final int INTERACT_WHEEL_SLOT_HALF = INTERACT_WHEEL_SLOT / 2;
    private static final int TOP_BUTTON_GAP = 5;
    private static final int TOP_BUTTON_H = 24;
    private static final int MIN_TOP_BUTTON_W = 28;
    private static final int TOP_MODE_BUTTON_W = 32;
    private static final int TOP_ICON_BUTTON_W = 32;
    private static final int TOP_QUICK_BUILD_BUTTON_W = 96;
    private static final int TOP_ULTIMINE_BUTTON_W = 74;
    private static final int TOP_ACTION_BUTTON_W = 74;
    private static final int TOP_SENS_BUTTON_W = 96;
    private static final int TOP_AUTOSTORE_BUTTON_W = 116;
    private static final int SHAPE_BUTTON_W = 112;
    private static final int SHAPE_ROTATE_BUTTON_W = 84;
    private static final int TOP_GUIDE_BUTTON_W = 78;
    private static final int QUEST_DETECT_BUTTON_W = 104;
    private static final int QUEST_DETECT_POPUP_W = 178;
    private static final int QUEST_DETECT_POPUP_H = 48;
    private static final int STORAGE_SCAN_POPUP_W = 150;
    private static final int STORAGE_SCAN_POPUP_H = 30;
    private static final int QUICK_BUILD_PANEL_W = 188;
    private static final int QUICK_BUILD_PANEL_H = 216;
    private static final int QUICK_BUILD_PANEL_MIN_H = 156;
    private static final int ULTIMINE_PANEL_W = 238;
    private static final int ULTIMINE_PANEL_H = 122;
    private static final int ULTIMINE_MIN_LIMIT = 1;
    private static final int ULTIMINE_MAX_LIMIT = 256;
    private static final int QUICK_BUILD_SHAPE_SLOT = 32;
    private static final int QUICK_BUILD_SHAPE_GAP = 8;
    private static final int QUICK_BUILD_GEAR_MENU_W = 148;
    private static final int QUICK_BUILD_GEAR_ROW_H = 18;
    private static final int SHAPE_WHEEL_RADIUS = 52;
    private static final int SHAPE_WHEEL_SLOT = 22;
    private static final int SHAPE_MAX_DIMENSION = 32;
    private static final int SHAPE_MAX_OFFSET = SHAPE_MAX_DIMENSION - 1;
    private static final int SHAPE_MAX_RADIUS = 32;
    private static final int SHAPE_ROTATE_STEP_DEGREES = 15;
    private static final int SHAPE_HISTORY_LIMIT = 24;
    private static final int SHAPE_CONTEXT_PANEL_W = 148;
    private static final int SHAPE_CONTEXT_PANEL_X_MARGIN = 10;
    private static final int SHAPE_CONTEXT_PANEL_Y = TOP_H + 10;
    private static final int SHAPE_CONTEXT_ROW_H = 14;
    private static final int FUNNEL_BUFFER_PANEL_W = 132;
    private static final int FUNNEL_BUFFER_ROW_H = 22;
    private static final int FUNNEL_BUFFER_TOGGLE_W = 60;
    private static final int FUNNEL_BUFFER_TOGGLE_H = 16;
    private static final int GEAR_MENU_H = 284;
    private static final int GEAR_MENU_MIN_H = 168;
    private static final int GEAR_MENU_CONTENT_H = 472;
    private static final double MIDDLE_CLICK_DRAG_THRESHOLD = 1.5D;
    private static final double DEFAULT_RTS_GUI_SCALE = 2.0D;
    private static final double MIN_RTS_GUI_SCALE = 1.0D;
    private static final double MAX_RTS_GUI_SCALE = 4.0D;
    private static final double RTS_GUI_SCALE_STEP = 0.5D;
    private static final float RTS_MODAL_LAYER_Z = 400.0F;
    private static final long DAMAGE_FLASH_DURATION_MS = 300L;
    private static final ItemStack FUNNEL_CURSOR_STACK = new ItemStack(Items.HOPPER);
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private final ClientRtsController controller;

    private EditBox searchBox;
    private EditBox craftSearchBox;
    private String craftSearchDraft;
    private int hoveredEntry = -1;
    private int hoveredRecentEntry = -1;
    private int hoveredFluidEntry = -1;
    private int hoveredCraftableEntry = -1;
    private int hoveredFunnelBufferEntry = -1;
    private int hoveredToolSlot = -1;
    private boolean hoveredEmptyHandSlot = false;
    private int hoveredPinIndex = -1;
    private int hoveredGuiBindingSlot = -1;
    private boolean hoveredPinPageButton = false;
    private int categoryScroll = 0;
    private final Set<String> expandedCategoryMods = new HashSet<>();
    private int bottomPanelHeight = DEFAULT_BOTTOM_H;
    private BottomPanelTab bottomPanelTab = BottomPanelTab.STORAGE;
    private boolean rightPressActive = false;
    private int rightPressButton = -1;
    private boolean rightPressCanPrimary = false;
    private boolean rightPressCanRotate = false;
    private boolean rightDragRotated = false;
    private double rightDragDistance = 0.0D;
    private boolean middlePressActive = false;
    private int middlePressButton = -1;
    private boolean middlePressCanPan = false;
    private boolean middlePressCanPick = false;
    private double middleDragDistance = 0.0D;
    private double keyboardPanLastMouseX = Double.NaN;
    private double keyboardPanLastMouseY = Double.NaN;
    private boolean leftMiningActive = false;
    private int activeMiningMouseButton = -1;
    private boolean activeMiningKeyboard = false;
    private boolean cameraUpActionHeld = false;
    private boolean cameraDownActionHeld = false;
    private boolean guideOpen = false;
    private GuideContext guideContext = GuideContext.TOP;
    private int guidePage = 0;
    private int guideTopicScroll = 0;
    private int guideTextScroll = 0;
    private int guideAnchorX = -1;
    private int guideAnchorY = -1;
    private boolean gearMenuOpen = false;
    private int gearMenuScroll = 0;
    private boolean debugButtonVisible = false;
    private boolean draggingHorizontalSensitivity = false;
    private boolean draggingVerticalSensitivity = false;
    private boolean draggingRotateSensitivity = false;
    private boolean quickBuildOpen = true;
    private boolean ultimineOpen = false;
    private int ultimineLimit = 64;
    private boolean ultimineLimitEditing = false;
    private boolean ultimineLimitSelectAll = false;
    private String ultimineLimitDraft = "";
    private int lastUltimineSentLimit = 0;
    private int pinPage = 0;
    private int craftScroll = 0;
    private int lastCraftablesStorageRevision = -1;
    private final RtsCraftQuantityDialog craftQuantityDialog = new RtsCraftQuantityDialog();
    private long damageFlashStartMs = -1L;
    private boolean interactionWheelOpen = false;
    private final List<InteractionOption> interactionWheelOptions = new ArrayList<>();
    private InteractionTarget interactionWheelTarget;
    private int interactionWheelPage = 0;
    private int interactionWheelCenterX = 0;
    private int interactionWheelCenterY = 0;
    private boolean shapeWheelOpen = false;
    private int shapeWheelCenterX = 0;
    private int shapeWheelCenterY = 0;
    private boolean funnelBufferVisible = true;
    private boolean funnelHotkeyHeld = false;
    private BuilderMode modeBeforeFunnelHotkey = BuilderMode.INTERACT;
    private boolean nativeCursorHidden = false;
    private boolean fixedRtsScaleRenderPass = false;
    private boolean fixedRtsScaleInputPass = false;
    private double activeRtsGuiRenderScale = 1.0D;
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;
    private ShapeBuildSession shapeBuildSession;
    private int shapeFootprintNudgeA = 0;
    private int shapeFootprintNudgeB = 0;
    private double shapeCursorY = 0.0D;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private ShapeFillMode shapeFillMode = ShapeFillMode.FILL;
    private int shapeRotateDegrees = 0;
    private boolean shapeWheelOpenedByAlt = false;
    private boolean altShapeMenuHeld = false;
    private int pendingGuiBindSlot = -1;
    private final List<ShapeHistoryBatch> shapeUndoStack = new ArrayList<>();
    private final List<ShapeHistoryBatch> shapeRedoStack = new ArrayList<>();
    private static final ResourceLocation SHAPE_BLOCK_INACTIVE = quickBuildTexture("shape_block_inactive");
    private static final ResourceLocation SHAPE_BLOCK_HOVER = quickBuildTexture("shape_block_hover");
    private static final ResourceLocation SHAPE_BLOCK_ACTIVE = quickBuildTexture("shape_block_active");
    private static final ResourceLocation SHAPE_LINE_INACTIVE = quickBuildTexture("shape_line_inactive");
    private static final ResourceLocation SHAPE_LINE_HOVER = quickBuildTexture("shape_line_hover");
    private static final ResourceLocation SHAPE_LINE_ACTIVE = quickBuildTexture("shape_line_active");
    private static final ResourceLocation SHAPE_SQUARE_INACTIVE = quickBuildTexture("shape_square_inactive");
    private static final ResourceLocation SHAPE_SQUARE_HOVER = quickBuildTexture("shape_square_hover");
    private static final ResourceLocation SHAPE_SQUARE_ACTIVE = quickBuildTexture("shape_square_active");
    private static final ResourceLocation SHAPE_WALL_INACTIVE = quickBuildTexture("shape_wall_inactive");
    private static final ResourceLocation SHAPE_WALL_HOVER = quickBuildTexture("shape_wall_hover");
    private static final ResourceLocation SHAPE_WALL_ACTIVE = quickBuildTexture("shape_wall_active");
    private static final ResourceLocation SHAPE_CIRCLE_INACTIVE = quickBuildTexture("shape_circle_inactive");
    private static final ResourceLocation SHAPE_CIRCLE_HOVER = quickBuildTexture("shape_circle_hover");
    private static final ResourceLocation SHAPE_CIRCLE_ACTIVE = quickBuildTexture("shape_circle_active");
    private static final ResourceLocation SHAPE_BOX_INACTIVE = quickBuildTexture("shape_box_inactive");
    private static final ResourceLocation SHAPE_BOX_HOVER = quickBuildTexture("shape_box_hover");
    private static final ResourceLocation SHAPE_BOX_ACTIVE = quickBuildTexture("shape_box_active");
    private static final ResourceLocation TOPBAR_INTERACT_INACTIVE = topbarTexture("mode_interact_inactive");
    private static final ResourceLocation TOPBAR_INTERACT_HOVER = topbarTexture("mode_interact_hover");
    private static final ResourceLocation TOPBAR_INTERACT_ACTIVE = topbarTexture("mode_interact_active");
    private static final ResourceLocation TOPBAR_INTERACT_PRESSED = topbarTexture("mode_interact_pressed");
    private static final ResourceLocation TOPBAR_LINK_INACTIVE = topbarTexture("mode_link_inactive");
    private static final ResourceLocation TOPBAR_LINK_HOVER = topbarTexture("mode_link_hover");
    private static final ResourceLocation TOPBAR_LINK_ACTIVE = topbarTexture("mode_link_active");
    private static final ResourceLocation TOPBAR_LINK_PRESSED = topbarTexture("mode_link_pressed");
    private static final ResourceLocation TOPBAR_FUNNEL_INACTIVE = topbarTexture("mode_funnel_inactive");
    private static final ResourceLocation TOPBAR_FUNNEL_HOVER = topbarTexture("mode_funnel_hover");
    private static final ResourceLocation TOPBAR_FUNNEL_ACTIVE = topbarTexture("mode_funnel_active");
    private static final ResourceLocation TOPBAR_FUNNEL_PRESSED = topbarTexture("mode_funnel_pressed");
    private static final ResourceLocation TOPBAR_ROTATE_INACTIVE = topbarTexture("mode_rotate_inactive");
    private static final ResourceLocation TOPBAR_ROTATE_HOVER = topbarTexture("mode_rotate_hover");
    private static final ResourceLocation TOPBAR_ROTATE_ACTIVE = topbarTexture("mode_rotate_active");
    private static final ResourceLocation TOPBAR_ROTATE_PRESSED = topbarTexture("mode_rotate_pressed");
    private static final ResourceLocation TOPBAR_QUICK_BUILD_INACTIVE = topbarTexture("quick_build_inactive");
    private static final ResourceLocation TOPBAR_QUICK_BUILD_HOVER = topbarTexture("quick_build_hover");
    private static final ResourceLocation TOPBAR_QUICK_BUILD_ACTIVE = topbarTexture("quick_build_active");
    private static final ResourceLocation TOPBAR_QUICK_BUILD_PRESSED = topbarTexture("quick_build_pressed");
    private static final ResourceLocation TOPBAR_ULTIMINE_INACTIVE = topbarTexture("ultimine_inactive");
    private static final ResourceLocation TOPBAR_ULTIMINE_HOVER = topbarTexture("ultimine_hover");
    private static final ResourceLocation TOPBAR_ULTIMINE_ACTIVE = topbarTexture("ultimine_active");
    private static final ResourceLocation TOPBAR_ULTIMINE_PRESSED = topbarTexture("ultimine_pressed");
    private static final ResourceLocation TOPBAR_CHUNK_VIEW_INACTIVE = topbarTexture("chunk_view_inactive");
    private static final ResourceLocation TOPBAR_CHUNK_VIEW_HOVER = topbarTexture("chunk_view_hover");
    private static final ResourceLocation TOPBAR_CHUNK_VIEW_ACTIVE = topbarTexture("chunk_view_active");
    private static final ResourceLocation TOPBAR_CHUNK_VIEW_PRESSED = topbarTexture("chunk_view_pressed");
    private static final ResourceLocation TOPBAR_GEAR_INACTIVE = topbarTexture("settings_gear_inactive");
    private static final ResourceLocation TOPBAR_GEAR_HOVER = topbarTexture("settings_gear_hover");
    private static final ResourceLocation TOPBAR_GEAR_ACTIVE = topbarTexture("settings_gear_active");
    private static final ResourceLocation TOPBAR_GEAR_PRESSED = topbarTexture("settings_gear_pressed");
    private static final ResourceLocation TOPBAR_QUEST_DETECT_INACTIVE = topbarTexture("quest_detect_inactive");
    private static final ResourceLocation TOPBAR_QUEST_DETECT_HOVER = topbarTexture("quest_detect_hover");
    private static final ResourceLocation TOPBAR_QUEST_DETECT_ACTIVE = topbarTexture("quest_detect_active");
    private static final ResourceLocation TOPBAR_QUEST_DETECT_PRESSED = topbarTexture("quest_detect_pressed");

    public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
    }

    public void triggerDamageFlash() {
        this.damageFlashStartMs = System.currentTimeMillis();
    }

    @Override
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
        if (this.craftSearchDraft == null) {
            this.craftSearchDraft = this.controller.getCraftablesSearch();
        }
        this.craftSearchBox.setValue(this.craftSearchDraft);
        this.craftSearchBox.setResponder(value -> this.craftSearchDraft = value == null ? "" : value);
        this.controller.requestCraftables();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        closeInteractionWheel();
        closeShapeWheel();
        clearShapeBuildSession();
        persistUiState();
        this.pendingGuiBindSlot = -1;
        this.altShapeMenuHeld = false;
        this.funnelHotkeyHeld = false;
        this.cameraUpActionHeld = false;
        this.cameraDownActionHeld = false;
        stopActiveMining();
        if (this.controller.isFunnelEnabled()) {
            this.controller.setFunnelEnabled(false);
        }
        if (this.controller.isEnabled()) {
            RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
        }
        this.craftQuantityDialog.close();
        updateNativeCursorVisibility(false);
    }

    @Override
    public void removed() {
        super.removed();
        this.cameraUpActionHeld = false;
        this.cameraDownActionHeld = false;
        updateNativeCursorVisibility(false);
    }

    @Override
    public void tick() {
        super.tick();
        updateAltShapeWheelLifecycle();
        if (this.controller.getMode() == BuilderMode.FUNNEL && this.controller.isFunnelEnabled()) {
            BlockHitResult hit = pickBlockHit();
            if (hit != null) {
                this.controller.updateFunnelTarget(hit.getBlockPos());
            }
        }
        syncCraftablesPanelState();
        if (!this.leftMiningActive) {
            return;
        }
        if (this.minecraft == null || !this.controller.isEnabled()) {
            stopActiveMining();
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        boolean miningInputDown = this.activeMiningKeyboard
                ? ClientKeyMappings.ACTION_BREAK.isDown()
                : this.activeMiningMouseButton >= 0
                        && GLFW.glfwGetMouseButton(window, this.activeMiningMouseButton) == GLFW.GLFW_PRESS;
        if (!miningInputDown) {
            stopActiveMining();
            return;
        }
    }

    @Override
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
        if (this.craftQuantityDialog.isOpen()) {
            boolean handled = this.craftQuantityDialog.mouseClicked(mouseX, mouseY, button, this.width, this.height);
            submitCraftQuantityDialogIfReady();
            return handled;
        }
        if (BlueprintPanel.isNameDialogOpen()) {
            return BlueprintPanel.mouseClickedNameDialog(mouseX, mouseY, button, this.width, this.height);
        }
        if (BlueprintPanel.isMaterialDialogOpen()) {
            return BlueprintPanel.mouseClickedMaterialDialog(mouseX, mouseY, button, this.width, this.height);
        }
        if (BlueprintPanel.isCaptureModeActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                stopActiveMining();
                if (!BlueprintPanel.mouseClickedCaptureOverlay(mouseX, mouseY, this.width, this.height, TOP_H + 8)) {
                    if (BlueprintPanel.isCaptureSelectionComplete() && isWorldArea(mouseX, mouseY)) {
                        BlockHitResult hit = pickBlockHit();
                        if (hit != null
                                && hit.getType() == HitResult.Type.BLOCK
                                && BlueprintPanel.toggleCaptureBlockExclusion(hit.getBlockPos())) {
                            return true;
                        }
                    }
                    BlueprintPanel.cancelCaptureFromClick();
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (!BlueprintPanel.isCaptureSelectionComplete() && isWorldArea(mouseX, mouseY)) {
                    BlockHitResult hit = pickBlockHit();
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        BlueprintPanel.acceptCapturePoint(hit.getBlockPos());
                    }
                    return true;
                }
                if (!BlueprintPanel.isCaptureSelectionComplete()) {
                    return true;
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && this.ultimineLimitEditing
                && !isInsideUltimineLimitInput(mouseX, mouseY)) {
            commitUltimineLimitEdit();
        }

        if (this.controller.isHomeSelectionMode()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = pickBlockHit();
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

        if (this.shapeWheelOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ClientRtsController.BuildShape picked = resolveShapeWheelOption(mouseX, mouseY);
                if (picked != null) {
                    this.controller.setBuildShape(picked);
                    ensureFillModeForShape(picked);
                    clearShapeBuildSession();
                    persistUiState();
                }
                closeShapeWheel();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                closeShapeWheel();
                return true;
            }
            return true;
        }

        if (this.interactionWheelOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                InteractionOption picked = resolveInteractionWheelOption(mouseX, mouseY);
                if (picked != null) {
                    runInteractionOption(picked);
                }
                closeInteractionWheel();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                closeInteractionWheel();
                return true;
            }
            return true;
        }

        if (this.guideOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int topic = resolveGuideTopicClick(mouseX, mouseY);
                if (topic >= 0) {
                    this.guidePage = topic;
                    this.guideTextScroll = 0;
                    return true;
                }
                if (isInsideGuidePrev(mouseX, mouseY)) {
                    this.guidePage = Math.max(0, this.guidePage - 1);
                    this.guideTextScroll = 0;
                    return true;
                }
                if (isInsideGuideNext(mouseX, mouseY)) {
                    this.guidePage = Math.min(getGuidePageCount() - 1, this.guidePage + 1);
                    this.guideTextScroll = 0;
                    return true;
                }
                if (!isInsideGuidePanel(mouseX, mouseY) || isInsideGuideClose(mouseX, mouseY)) {
                    this.guideOpen = false;
                }
                return true;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.gearMenuOpen) {
                if (handleGearMenuClick(mouseX, mouseY)) {
                    return true;
                }
                this.gearMenuOpen = false;
            }
            if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS
                    && BlueprintPanel.mouseClickedPlacementHud(mouseX, mouseY, this.width, this.height, TOP_H + 8, getBottomY())) {
                return true;
            }
            if (handleQuickBuildPanelClick(mouseX, mouseY)) {
                return true;
            }
            if (handleUltiminePanelClick(mouseX, mouseY)) {
                return true;
            }
            if (handleTopBarClick(mouseX, mouseY)) {
                return true;
            }
            if (handleFunnelBufferPanelClick(mouseX, mouseY)) {
                return true;
            }
            if (handleBottomPanelClick(mouseX, mouseY)) {
                return true;
            }

            if (this.pendingGuiBindSlot >= 0 && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = pickBlockHit();
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
                BlockHitResult hit = pickBlockHit();
                if (hit != null) {
                    this.controller.linkStorage(hit.getBlockPos());
                    return true;
                }
            }

        }

        if (isBreakActionMouse(button)
                && canStartBreakActionOnMouse(button)
                && startMiningAt(mouseX, mouseY, button, false)) {
            return true;
        }

        boolean primaryMouse = isPrimaryActionMouse(button);
        boolean rotateMouse = isRotateDragActionMouse(button);
        if (primaryMouse || rotateMouse) {
            if (isSearchFocused()) {
                blurSearchFocus();
            }
            if (primaryMouse && this.pendingGuiBindSlot >= 0 && isWorldArea(mouseX, mouseY)) {
                return true;
            }
            if (primaryMouse && !rotateMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.LINK_STORAGE) {
                BlockHitResult hit = pickBlockHit();
                if (hit != null) {
                    this.controller.linkStorage(hit.getBlockPos(), false);
                }
                return true;
            }
            if (primaryMouse && isInsideBottomPanel(mouseX, mouseY)) {
                return handleBottomPanelRightClick(mouseX, mouseY);
            }
            if (primaryMouse && isWorldArea(mouseX, mouseY) && this.controller.getMode() == BuilderMode.ROTATE && !rotateMouse) {
                BlockHitResult hit = pickBlockHit();
                if (hit != null) {
                    clearShapeBuildSession();
                    this.controller.rotateBlock(hit.getBlockPos());
                }
                return true;
            }
            if (isWorldArea(mouseX, mouseY)) {
                this.rightPressActive = true;
                this.rightPressButton = button;
                this.rightPressCanPrimary = primaryMouse;
                this.rightPressCanRotate = rotateMouse;
                this.rightDragRotated = false;
                this.rightDragDistance = 0.0D;
                return true;
            }
            return true;
        }

        boolean panMouse = isPanDragActionMouse(button);
        boolean pickMouse = isPickBlockActionMouse(button);
        if (panMouse || pickMouse) {
            this.middlePressActive = isWorldArea(mouseX, mouseY);
            this.middlePressButton = button;
            this.middlePressCanPan = panMouse;
            this.middlePressCanPick = pickMouse;
            this.middleDragDistance = 0.0D;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
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
        if (this.craftQuantityDialog.isOpen()) {
            return true;
        }

        if (this.draggingHorizontalSensitivity || this.draggingVerticalSensitivity || this.draggingRotateSensitivity) {
            this.draggingHorizontalSensitivity = false;
            this.draggingVerticalSensitivity = false;
            this.draggingRotateSensitivity = false;
            saveSensitivityState();
            return true;
        }

        if (this.shapeWheelOpen) {
            return true;
        }

        if (this.interactionWheelOpen) {
            return true;
        }

        if (this.guideOpen) {
            return true;
        }

        if (this.leftMiningActive && !this.activeMiningKeyboard && button == this.activeMiningMouseButton) {
            stopActiveMining();
            return true;
        }

        if (this.rightPressActive && button == this.rightPressButton) {
            boolean canPrimary = this.rightPressCanPrimary;

            this.rightPressActive = false;
            this.rightPressButton = -1;
            this.rightPressCanPrimary = false;
            this.rightPressCanRotate = false;
            if (this.rightDragRotated) {
                this.rightDragRotated = false;
                this.rightDragDistance = 0.0D;
                return true;
            }

            if (!isWorldArea(mouseX, mouseY)) {
                return true;
            }

            if (!canPrimary) {
                return true;
            }

            return runPrimaryActionAt(mouseX, mouseY, button);
        }

        if (this.middlePressActive && button == this.middlePressButton) {
            if (this.middlePressCanPick
                    && this.middleDragDistance <= MIDDLE_CLICK_DRAG_THRESHOLD
                    && isWorldArea(mouseX, mouseY)) {
                tryPickHoveredBlockForPlacement();
            }
            this.middlePressActive = false;
            this.middlePressButton = -1;
            this.middlePressCanPan = false;
            this.middlePressCanPick = false;
            this.middleDragDistance = 0.0D;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
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
        if (this.craftQuantityDialog.isOpen()) {
            return true;
        }

        if (this.draggingHorizontalSensitivity) {
            updateHorizontalSensitivityFromMouse(mouseX);
            saveSensitivityState();
            return true;
        }
        if (this.draggingVerticalSensitivity) {
            updateVerticalSensitivityFromMouse(mouseX);
            saveSensitivityState();
            return true;
        }
        if (this.draggingRotateSensitivity) {
            updateRotateSensitivityFromMouse(mouseX);
            saveSensitivityState();
            return true;
        }

        if (this.shapeWheelOpen) {
            return true;
        }

        if (this.interactionWheelOpen) {
            return true;
        }

        if (this.guideOpen) {
            return true;
        }

        if (this.rightPressActive
                && button == this.rightPressButton
                && this.rightPressCanRotate
                && isWorldArea(mouseX, mouseY)
                && !isAltDown()) {
            this.rightDragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (this.rightDragDistance > 1.5D) {
                this.rightDragRotated = true;
            }
            this.controller.queueRotateDrag(dragX, dragY);
            return true;
        }

        if (this.middlePressActive
                && button == this.middlePressButton
                && this.middlePressCanPan
                && isWorldArea(mouseX, mouseY)) {
            this.middleDragDistance += Math.abs(dragX) + Math.abs(dragY);
            this.controller.queuePanDrag(dragX, dragY);
            return true;
        }

        if (canUseKeyboardPanDrag(mouseX, mouseY)) {
            this.controller.queuePanDrag(dragX, dragY);
            this.keyboardPanLastMouseX = mouseX;
            this.keyboardPanLastMouseY = mouseY;
            return true;
        }

        if (isSearchFocused()) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
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

        if (canUseKeyboardPanDrag(mouseX, mouseY)) {
            if (!Double.isNaN(this.keyboardPanLastMouseX) && !Double.isNaN(this.keyboardPanLastMouseY)) {
                double dragX = mouseX - this.keyboardPanLastMouseX;
                double dragY = mouseY - this.keyboardPanLastMouseY;
                if (Math.abs(dragX) > 0.0D || Math.abs(dragY) > 0.0D) {
                    this.controller.queuePanDrag(dragX, dragY);
                }
            }
            this.keyboardPanLastMouseX = mouseX;
            this.keyboardPanLastMouseY = mouseY;
        } else {
            this.keyboardPanLastMouseX = Double.NaN;
            this.keyboardPanLastMouseY = Double.NaN;
        }

        super.mouseMoved(mouseX, mouseY);
    }

    private static boolean isPrimaryActionMouse(int button) {
        return ClientKeyMappings.ACTION_PRIMARY.matchesMouse(button);
    }

    private static boolean isBreakActionMouse(int button) {
        return ClientKeyMappings.ACTION_BREAK.matchesMouse(button);
    }

    private static boolean isRotateDragActionMouse(int button) {
        return ClientKeyMappings.CAMERA_ROTATE_DRAG.matchesMouse(button);
    }

    private static boolean isPanDragActionMouse(int button) {
        return ClientKeyMappings.CAMERA_PAN_DRAG.matchesMouse(button);
    }

    private static boolean isKeyboardPanDragActionHeld() {
        InputConstants.Key key = ClientKeyMappings.CAMERA_PAN_DRAG.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && ClientKeyMappings.CAMERA_PAN_DRAG.isDown();
    }

    private static boolean isPickBlockActionMouse(int button) {
        return ClientKeyMappings.PICK_BLOCK.matchesMouse(button);
    }

    private boolean canUseKeyboardPanDrag(double mouseX, double mouseY) {
        return isKeyboardPanDragActionHeld()
                && isWorldArea(mouseX, mouseY)
                && !this.craftQuantityDialog.isOpen()
                && !this.draggingHorizontalSensitivity
                && !this.shapeWheelOpen
                && !this.interactionWheelOpen
                && !this.guideOpen
                && !this.gearMenuOpen
                && !BlueprintPanel.isNameDialogOpen()
                && !BlueprintPanel.isMaterialDialogOpen()
                && !isSearchFocused();
    }

    public boolean isCameraUpActionHeld() {
        return this.cameraUpActionHeld || ClientKeyMappings.CAMERA_UP.isDown();
    }

    public boolean isCameraDownActionHeld() {
        return this.cameraDownActionHeld || ClientKeyMappings.CAMERA_DOWN.isDown();
    }

    private boolean updateCameraVerticalHeldState(int keyCode, int scanCode, boolean down) {
        boolean handled = false;
        if (ClientKeyMappings.CAMERA_UP.matches(keyCode, scanCode)) {
            this.cameraUpActionHeld = down;
            handled = true;
        }
        if (ClientKeyMappings.CAMERA_DOWN.matches(keyCode, scanCode)) {
            this.cameraDownActionHeld = down;
            handled = true;
        }
        return handled;
    }

    private boolean canStartBreakActionOnMouse(int button) {
        return !isPrimaryActionMouse(button)
                && !isRotateDragActionMouse(button)
                && !isPanDragActionMouse(button)
                && !isPickBlockActionMouse(button);
    }

    private boolean startMiningAt(double mouseX, double mouseY, int mouseButton, boolean keyboard) {
        if (this.pendingGuiBindSlot >= 0
                || BlueprintPanel.isCaptureModeActive()
                || !isWorldArea(mouseX, mouseY)
                || this.controller.getMode() == BuilderMode.LINK_STORAGE
                || this.controller.getMode() == BuilderMode.FUNNEL) {
            return false;
        }
        BlockHitResult hit = pickBlockHit();
        if (hit == null) {
            return false;
        }
        if (this.ultimineOpen) {
            this.controller.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(), getSelectedToolSlot(), this.ultimineLimit);
            this.lastUltimineSentLimit = this.ultimineLimit;
        } else {
            this.controller.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(), getSelectedToolSlot());
            this.lastUltimineSentLimit = 1;
        }
        this.leftMiningActive = true;
        this.activeMiningMouseButton = keyboard ? -1 : mouseButton;
        this.activeMiningKeyboard = keyboard;
        return true;
    }

    private void stopActiveMining() {
        if (!this.leftMiningActive && this.activeMiningMouseButton < 0 && !this.activeMiningKeyboard) {
            return;
        }
        this.leftMiningActive = false;
        this.activeMiningMouseButton = -1;
        this.activeMiningKeyboard = false;
        this.controller.abortMining(getSelectedToolSlot());
    }

    private boolean runPrimaryActionAt(double mouseX, double mouseY) {
        return runPrimaryActionAt(mouseX, mouseY, -1);
    }

    private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton) {
        if (this.pendingGuiBindSlot >= 0) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            if (!BlueprintPanel.isCaptureSelectionComplete() && isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = pickBlockHit();
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlueprintPanel.acceptCapturePoint(hit.getBlockPos());
                }
            }
            return true;
        }
        if (isInsideBottomPanel(mouseX, mouseY)) {
            return handleBottomPanelRightClick(mouseX, mouseY);
        }
        if (!isWorldArea(mouseX, mouseY)) {
            return true;
        }
        if (this.controller.getMode() == BuilderMode.LINK_STORAGE) {
            clearShapeBuildSession();
            BlockHitResult hit = pickBlockHit();
            if (hit != null) {
                this.controller.linkStorage(hit.getBlockPos(), mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            return true;
        }
        if (this.controller.getMode() == BuilderMode.FUNNEL) {
            clearShapeBuildSession();
            return true;
        }
        if (this.controller.getMode() == BuilderMode.ROTATE) {
            InteractionTarget target = pickInteractionTarget(false);
            if (target != null && target.blockHit() != null) {
                clearShapeBuildSession();
                this.controller.rotateBlock(target.blockHit().getBlockPos());
            }
            return true;
        }

        if (isWheelModifierDown()) {
            openInteractionWheel(mouseX, mouseY);
            return true;
        }
        boolean forcePlace = hasShiftDown();
        if (tryConfirmPendingShapeBuild(forcePlace)) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.hasSelectedBlueprint()) {
            if (BlueprintPanel.hasPinnedPreview()) {
                BlueprintPanel.confirmPinnedPreview();
                return true;
            }
            BlockHitResult blueprintHit = pickBlueprintPlacementHit();
            if (blueprintHit != null) {
                BlockPos anchor = resolveBlueprintAnchor(blueprintHit);
                if (anchor != null) {
                    BlueprintPanel.pinSelected(anchor);
                }
            }
            return true;
        }
        InteractionTarget target = pickInteractionTarget(false);
        if (target == null) {
            return true;
        }

        if (this.controller.hasSelectedFluid()) {
            if (target.blockHit() != null) {
                placeWithShape(
                        target.blockHit(),
                        forcePlace,
                        target.rayOrigin(),
                        target.rayDir(),
                        mouseY,
                        true,
                        PlacementReplayKind.TOOL_SLOT,
                        "",
                        -1);
            }
            return true;
        }

        if (this.controller.hasSelectedItem()) {
            if (target.isEntityTarget()) {
                clearShapeBuildSession();
                this.controller.interactEntityWithPinnedItem(
                        target.entityId(),
                        target.hitLocation(),
                        this.controller.getSelectedItemId(),
                        target.rayOrigin(),
                        target.rayDir());
            } else if (target.blockHit() != null) {
                placeWithShape(
                        target.blockHit(),
                        forcePlace,
                        target.rayOrigin(),
                        target.rayDir(),
                        mouseY,
                        false,
                        PlacementReplayKind.PIN_ITEM,
                        this.controller.getSelectedItemId(),
                        -1);
            }
            return true;
        }

        if (target.blockHit() != null
                && this.controller.getBuildShape() != ClientRtsController.BuildShape.BLOCK
                && canUseToolSlotShapeSource()) {
            placeWithShape(
                    target.blockHit(),
                    forcePlace,
                    target.rayOrigin(),
                    target.rayDir(),
                    mouseY,
                    false,
                    PlacementReplayKind.TOOL_SLOT,
                    "",
                    getSelectedToolSlot());
            return true;
        }

        clearShapeBuildSession();
        if (this.controller.isEmptyHandSelected()) {
            if (!target.isEntityTarget() && target.blockHit() != null) {
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
                recordSinglePlacementForUndo(
                        target.blockHit(),
                        PlacementReplayKind.TOOL_SLOT,
                        "",
                        getSelectedToolSlot());
            } else {
                this.controller.interactEmpty(target.blockHit(), target.rayOrigin(), target.rayDir());
            }
        }
        return true;
    }

    private BlockPos resolveBlueprintAnchor(BlockHitResult hit) {
        if (hit == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockPos clicked = hit.getBlockPos();
        return BlueprintReplaceRules.canBlueprintReplace(this.minecraft.level.getBlockState(clicked))
                ? clicked
                : clicked.relative(hit.getDirection());
    }

    private BlockHitResult pickBlueprintPlacementHit() {
        InteractionTarget target = pickInteractionTarget(false);
        if (target != null && target.blockHit() != null) {
            return target.blockHit();
        }
        return tryCreateBlueprintAirHit();
    }

    private boolean tryPickHoveredBlockForPlacement() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return false;
        }
        BlockHitResult hit = pickBlockHit();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockState state = this.minecraft.level.getBlockState(hit.getBlockPos());
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return false;
        }
        ItemStack preview = new ItemStack(item);
        if (preview.isEmpty()) {
            return false;
        }
        clearShapeBuildSession();
        this.controller.selectItemForPlacement(itemId.toString(), preview.getHoverName().getString(), preview);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    return mouseScrolled(mouseX / frame.scale(), mouseY / frame.scale(), scrollY);
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) {
                frame.close();
            }
        }
        if (this.craftQuantityDialog.isOpen()) {
            return this.craftQuantityDialog.mouseScrolled(scrollY);
        }
        if (BlueprintPanel.isNameDialogOpen()) {
            return true;
        }
        if (BlueprintPanel.isMaterialDialogOpen()) {
            return BlueprintPanel.mouseScrolledMaterialDialog(scrollY, this.controller, this.width, this.height);
        }

        if (this.gearMenuOpen && isInsideGearMenu(mouseX, mouseY)) {
            return scrollGearMenu(scrollY);
        }

        if (this.shapeWheelOpen) {
            if (scrollY > 0.0D) {
                this.controller.cycleBuildShape(-1);
            } else if (scrollY < 0.0D) {
                this.controller.cycleBuildShape(1);
            }
            ensureFillModeForShape(this.controller.getBuildShape());
            clearShapeBuildSession();
            persistUiState();
            return true;
        }

        if (this.interactionWheelOpen) {
            int pageCount = getInteractionWheelPageCount();
            if (pageCount > 1) {
                if (scrollY > 0.0D) {
                    this.interactionWheelPage = (this.interactionWheelPage + pageCount - 1) % pageCount;
                } else if (scrollY < 0.0D) {
                    this.interactionWheelPage = (this.interactionWheelPage + 1) % pageCount;
                }
            }
            return true;
        }

        if (this.guideOpen) {
            return scrollGuidePanel(mouseX, mouseY, scrollY);
        }

        if (isInsideBottomPanel(mouseX, mouseY)) {
            BottomPanelLayout layout = resolveBottomPanelLayout();
            if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
                int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
                int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
                int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
                int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
                BlueprintPanel.mouseScrolled(mouseX, mouseY, scrollY, contentX, contentY, contentW, contentH);
                return true;
            }
            if (inside(mouseX, mouseY, layout.craftPanelX(), layout.craftPanelY(), CRAFT_PANEL_W, layout.craftPanelH())) {
                int visibleRows = layout.storageRows();
                int totalRows = Math.max(1, (int) Math.ceil(this.controller.getCraftableEntries().size() / (double) CRAFT_PANEL_COLS));
                int maxScroll = Math.max(0, totalRows - visibleRows);
                int delta = scrollY > 0.0D ? -1 : 1;
                this.craftScroll = Mth.clamp(this.craftScroll + delta, 0, maxScroll);
                if (delta > 0 && this.craftScroll >= maxScroll && this.controller.hasMoreCraftables()) {
                    this.controller.requestMoreCraftables();
                }
                return true;
            }
            if (isInsideCategoryList(mouseX, mouseY)) {
                shiftCategoryScroll(scrollY > 0.0D ? -1 : 1);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.craftQuantityDialog.isOpen()) {
            boolean handled = this.craftQuantityDialog.keyPressed(keyCode, scanCode, modifiers);
            submitCraftQuantityDialogIfReady();
            return handled;
        }
        if (BlueprintPanel.keyPressedNameDialog(keyCode)) {
            return true;
        }
        if (BlueprintPanel.keyPressedMaterialDialog(keyCode)) {
            return true;
        }
        if (BlueprintPanel.isCaptureModeActive() && BlueprintPanel.keyPressed(keyCode)) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.keyPressed(keyCode)) {
            return true;
        }

        if (this.controller.isHomeSelectionMode()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                RtsClientPacketGateway.sendToggleCamera(this.controller.isStartCameraAtPlayerHead());
            }
            return true;
        }
        if (this.ultimineLimitEditing) {
            return handleUltimineLimitKeyPressed(keyCode);
        }

        if (this.shapeWheelOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeShapeWheel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_1) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_2) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.LINE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_3) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.SQUARE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_4) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.WALL);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_5) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.CIRCLE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_6) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.BOX);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                persistUiState();
                return true;
            }
            return true;
        }

        if (this.interactionWheelOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeInteractionWheel();
                return true;
            }
            int pageCount = getInteractionWheelPageCount();
            if (pageCount > 1 && (keyCode == GLFW.GLFW_KEY_Q || keyCode == GLFW.GLFW_KEY_LEFT)) {
                this.interactionWheelPage = (this.interactionWheelPage + pageCount - 1) % pageCount;
                return true;
            }
            if (pageCount > 1 && (keyCode == GLFW.GLFW_KEY_E || keyCode == GLFW.GLFW_KEY_RIGHT)) {
                this.interactionWheelPage = (this.interactionWheelPage + 1) % pageCount;
                return true;
            }
            return true;
        }

        if (this.guideOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.guideOpen = false;
            }
            return true;
        }

        if (this.gearMenuOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.gearMenuOpen = false;
                return true;
            }
        }

        if (this.pendingGuiBindSlot >= 0 && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.pendingGuiBindSlot = -1;
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            return undoLastPlacementBatch();
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            return redoLastPlacementBatch();
        }

        if (!isSearchFocused()
                && canAdjustCurrentShapeHeight()
                && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int delta = keyCode == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1;
            if (isAltDown()) {
                delta *= 4;
            }
            if (adjustShapeHeightNudge(delta)) {
                return true;
            }
        }

        if (!isSearchFocused() && updateCameraVerticalHeldState(keyCode, scanCode, true)) {
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
            if (startMiningAt(currentMouseX(), currentMouseY(), -1, true)) {
                return true;
            }
        }
        if (!isSearchFocused() && ClientKeyMappings.PICK_BLOCK.matches(keyCode, scanCode)) {
            if (isWorldArea(currentMouseX(), currentMouseY())) {
                tryPickHoveredBlockForPlacement();
            }
            return true;
        }
        if (!isSearchFocused() && ClientKeyMappings.ACTION_PRIMARY.matches(keyCode, scanCode)) {
            return runPrimaryActionAt(currentMouseX(), currentMouseY());
        }

        if (!isSearchFocused() && handleModeKeyPressed(keyCode, scanCode)) {
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
            rotateShapeByStep(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (!isSearchFocused()
                && ClientKeyMappings.OPEN_CRAFT_TERMINAL.matches(keyCode, scanCode)
                && !hasControlDown()
                && hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)) {
            this.controller.openCraftTerminal();
            return true;
        }

        if (isSearchFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchBox != null && this.searchBox.isFocused()) {
                this.searchBox.setValue("");
                this.controller.setStorageSearch("");
                blurSearchFocus();
                return true;
            }
            if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
                this.craftSearchDraft = "";
                this.craftSearchBox.setValue("");
                this.controller.setCraftablesSearch("");
                blurSearchFocus();
                return true;
            }
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                this.controller.setStorageSearch(this.searchBox.getValue());
            }
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyCraftSearchDraft();
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
            if (this.hoveredPinPageButton) {
                return true;
            }
            if (this.hoveredPinIndex >= 0) {
                if (this.controller.hasSelectedItem()) {
                    this.controller.assignQuickSlotFromSelected(this.hoveredPinIndex);
                    return true;
                }
                if (tryAssignQuickSlotFromToolSelection(this.hoveredPinIndex)) {
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

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode) && this.funnelHotkeyHeld) {
            this.funnelHotkeyHeld = false;
            deactivateFunnelHotkey();
            return true;
        }
        if (updateCameraVerticalHeldState(keyCode, scanCode, false)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean handleModeKeyPressed(int keyCode, int scanCode) {
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

    private boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) {
        if (mode == null || (this.controller.getMode() == mode && this.controller.isFunnelEnabled() == funnelEnabled)) {
            return false;
        }
        stopActiveMining();
        clearShapeBuildSession();
        closeInteractionWheel();
        closeShapeWheel();
        this.controller.setMode(mode);
        this.controller.setFunnelEnabled(funnelEnabled);
        this.funnelHotkeyHeld = false;
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.craftQuantityDialog.isOpen()) {
            return this.craftQuantityDialog.charTyped(codePoint, modifiers);
        }
        if (BlueprintPanel.charTypedNameDialog(codePoint)) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.charTyped(codePoint)) {
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.charTyped(codePoint, modifiers)) {
                this.controller.setStorageSearch(this.searchBox.getValue());
            }
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.charTyped(codePoint, modifiers);
            return true;
        }
        if (this.ultimineLimitEditing) {
            return handleUltimineLimitCharTyped(codePoint);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.fixedRtsScaleRenderPass && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            return;
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.shapeCursorY = mouseY;
        this.hoveredEntry = -1;
        this.hoveredRecentEntry = -1;
        this.hoveredFluidEntry = -1;
        this.hoveredCraftableEntry = -1;
        this.hoveredFunnelBufferEntry = -1;
        this.hoveredToolSlot = -1;
        this.hoveredEmptyHandSlot = false;
        this.hoveredPinIndex = -1;
        this.hoveredGuiBindingSlot = -1;
        this.hoveredPinPageButton = false;

        guiGraphics.fill(0, 0, this.width, TOP_H, 0xC0101116);

        if (this.controller.isHomeSelectionMode()) {
            renderHomeSelectionOverlay(guiGraphics, mouseX, mouseY);
            renderDamageFlash(guiGraphics);
            return;
        }

        renderTopBar(guiGraphics, mouseX, mouseY);
        renderBottomPanel(guiGraphics, mouseX, mouseY, partialTick);
        renderQuickBuildPanel(guiGraphics, mouseX, mouseY);
        renderUltiminePanel(guiGraphics, mouseX, mouseY);
        renderFunnelBufferPanel(guiGraphics, mouseX, mouseY);
        renderQuestDetectPopup(guiGraphics);
        renderStorageScanPopup(guiGraphics);
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            BlockHitResult hit = isWorldArea(mouseX, mouseY) ? pickBlockHit() : null;
            BlueprintPanel.updateCaptureHoverPoint(hit == null ? null : hit.getBlockPos());
        }
        BlueprintPanel.renderCaptureOverlay(guiGraphics, this.font, this.width, this.height, mouseX, mouseY, TOP_H + 8);
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
            BlueprintPanel.renderPlacementHud(guiGraphics, this.font, this.controller,
                    this.width, this.height, mouseX, mouseY, TOP_H + 8, getBottomY());
        }

        boolean modalOpen = this.gearMenuOpen
                || this.guideOpen
                || this.interactionWheelOpen
                || this.shapeWheelOpen
                || this.craftQuantityDialog.isOpen()
                || BlueprintPanel.isNameDialogOpen()
                || BlueprintPanel.isMaterialDialogOpen();
        if (!modalOpen) {
            if (this.hoveredGuiBindingSlot >= 0 && this.hoveredGuiBindingSlot < this.controller.getGuiBindingCount()) {
                String detail = this.controller.hasGuiBinding(this.hoveredGuiBindingSlot)
                        ? this.controller.getGuiBindingLabel(this.hoveredGuiBindingSlot)
                        : text("screen.rtsbuilding.tooltip.gui_empty");
                guiGraphics.renderTooltip(this.font, Component.literal(detail), mouseX, mouseY);
                guiGraphics.drawString(
                        this.font,
                        this.pendingGuiBindSlot == this.hoveredGuiBindingSlot
                                ? text("screen.rtsbuilding.tooltip.gui_cancel_bind")
                                : (this.controller.hasGuiBinding(this.hoveredGuiBindingSlot)
                                        ? text("screen.rtsbuilding.tooltip.gui_bound")
                                        : text("screen.rtsbuilding.tooltip.gui_unbound")),
                        mouseX + 10,
                        mouseY + 18,
                        0xFFCFE3F7);
            }

            renderBottomHoverInfoStrip(guiGraphics);

            renderDiscoverabilityTooltips(guiGraphics, mouseX, mouseY);

            boolean funnelCursor = shouldRenderFunnelCursor();
            updateNativeCursorVisibility(funnelCursor);
            if (funnelCursor) {
                guiGraphics.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
            } else if (this.pendingGuiBindSlot >= 0) {
                drawGuiBindCursor(guiGraphics, mouseX, mouseY);
            } else {
                ItemStack cursorPreview = resolveCursorPreview();
                if (!cursorPreview.isEmpty() && !isSearchFocused() && !this.guideOpen && !this.interactionWheelOpen
                        && !this.shapeWheelOpen) {
                    guiGraphics.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
                }
            }

        } else {
            updateNativeCursorVisibility(false);
        }

        renderCraftFeedback(guiGraphics);

        if (this.interactionWheelOpen) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z, () -> renderInteractionWheel(guiGraphics, mouseX, mouseY));
        }

        if (this.shapeWheelOpen) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z, () -> renderShapeWheel(guiGraphics, mouseX, mouseY));
        }

        if (this.gearMenuOpen) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 20.0F, () -> renderGearMenu(guiGraphics, mouseX, mouseY));
        }

        if (this.guideOpen) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 40.0F, () -> renderGuidePanel(guiGraphics));
        }

        if (BlueprintPanel.isMaterialDialogOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 50.0F,
                    () -> BlueprintPanel.renderMaterialDialog(guiGraphics, this.font, this.controller,
                            this.width, this.height, mouseX, mouseY));
        }

        if (BlueprintPanel.isNameDialogOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 55.0F,
                    () -> BlueprintPanel.renderNameDialog(guiGraphics, this.font, this.width, this.height, mouseX, mouseY));
        }

        if (this.craftQuantityDialog.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 60.0F,
                    () -> this.craftQuantityDialog.render(guiGraphics, this.font, this.width, this.height, mouseX, mouseY));
        }

        renderDamageFlash(guiGraphics);
    }

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

    private void renderAtGuiLayer(GuiGraphics g, float z, Runnable renderer) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, z);
        try {
            renderer.run();
        } finally {
            g.pose().popPose();
        }
    }

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
        return new RtsUiScaleFrame(this, oldW, oldH, renderScale);
    }

    private void renderHomeSelectionOverlay(GuiGraphics g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.width - 24);
        int panelX = (this.width - panelW) / 2;
        int panelY = 12;
        drawPanelFrame(g, panelX, panelY, panelW, 54, 0xCC101820, 0xFF6E8799, 0xFF0D1218);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.title"), panelX + panelW / 2, panelY + 8, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.area"), panelX + panelW / 2, panelY + 22, 0xD8E6F5);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.confirm"), panelX + panelW / 2, panelY + 34, 0xBFD2E6);

        BlockHitResult hit = isWorldArea(mouseX, mouseY) ? pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()), this.width / 2, panelY + 68, 0xFFE7C46A);
        }
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        ensureFillModeForShape(this.controller.getBuildShape());
        List<TopBarButtonLayout> topButtons = buildTopBarButtonLayouts();
        for (TopBarButtonLayout button : topButtons) {
            drawTopButton(g, mouseX, mouseY, button);
        }
        renderTopGuideHint(g, topButtons);

        String modeText = switch (this.controller.getMode()) {
            case INTERACT -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.interact"));
            case LINK_STORAGE -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.link_storage"));
            case FUNNEL -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.funnel"));
            case SELECT_PAN -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.camera"));
            case ROTATE -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.rotate"));
            default -> text("screen.rtsbuilding.status.mode", text("screen.rtsbuilding.mode.idle"));
        };

        String linked = this.controller.isStorageLinked()
                ? text("screen.rtsbuilding.status.storage_linked", this.controller.getLinkedStorageName())
                : text("screen.rtsbuilding.status.storage_not_linked");
        String selected;
        if (this.controller.hasSelectedFluid()) {
            selected = text("screen.rtsbuilding.status.selected_fluid", this.controller.getSelectedFluidLabel());
        } else if (!this.controller.getSelectedItemLabel().isEmpty()) {
            selected = text("screen.rtsbuilding.status.selected_item", selectedItemStatusLabel());
        } else if (this.controller.isEmptyHandSelected()) {
            selected = text("screen.rtsbuilding.status.selected_empty_hand");
        } else {
            selected = text("screen.rtsbuilding.status.selected_none");
        }
        String row1 = modeText + "    " + selected;
        String row2 = linked + (this.controller.isAutoStoreMinedDrops()
                ? "    " + text("screen.rtsbuilding.status.auto_store_on")
                : "    " + text("screen.rtsbuilding.status.auto_store_off"))
                + (hasProgressionNode(RtsProgressionNodes.FUNNEL) ? "    " + text("screen.rtsbuilding.status.funnel", text(this.controller.isFunnelEnabled() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off")) : "")
                + (hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE) ? "    " + text("screen.rtsbuilding.status.shape", shapeLabel(this.controller.getBuildShape())) : "")
                + (hasProgressionNode(RtsProgressionNodes.ULTIMINE) && this.ultimineOpen ? "    " + text("screen.rtsbuilding.status.ultimine", this.ultimineLimit) : "")
                + "    " + text("screen.rtsbuilding.status.fill", fillModeLabel(this.shapeFillMode))
                + "    " + text("screen.rtsbuilding.status.rotation", this.shapeRotateDegrees)
                + "    " + text("screen.rtsbuilding.status.undo_redo", this.shapeUndoStack.size(), this.shapeRedoStack.size())
                + "    " + pendingShapeStatusText()
                + (this.pendingGuiBindSlot >= 0 ? "    " + text("screen.rtsbuilding.status.gui_bind_armed", this.pendingGuiBindSlot + 1) : "");

        int statusX = 8;
        int statusW = Math.max(40, this.width - 16);
        g.drawString(this.font, trimToWidth(row1, statusW), statusX, 33, 0xF0F0F0);
        g.drawString(this.font, trimToWidth(row2, statusW), statusX, 44,
                this.controller.isStorageLinked() ? 0xB8FFB8 : 0xFFD8AE);
    }

    private void renderTopGuideHint(GuiGraphics g, List<TopBarButtonLayout> topButtons) {
        if (this.guideOpen && this.guideContext == GuideContext.TOP) {
            return;
        }
        TopBarButtonLayout guide = null;
        int nextX = this.width - 8;
        for (TopBarButtonLayout button : topButtons) {
            if (button.id() == TopBarButtonId.GUIDE) {
                guide = button;
                continue;
            }
            if (guide != null && button.x() > guide.x()) {
                nextX = Math.min(nextX, button.x());
            }
        }
        if (guide == null) {
            return;
        }
        int hintX = guide.x() + guide.width() + 4;
        int maxW = nextX - hintX - 4;
        if (maxW < 42) {
            return;
        }
        String hint = trimToWidth(text("screen.rtsbuilding.top_hint.guide"), maxW - 8);
        if (hint.isBlank()) {
            return;
        }
        int y = 12;
        int color = 0xFFE7C46A;
        g.drawString(this.font, ">", hintX, y, color);
        g.drawString(this.font, hint, hintX + 8, y, color);
    }

    private void drawCraftDock(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        CraftDockLayout dock = resolveCraftDockLayout(x, y);
        if (hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)) {
            boolean craftHovered = inside(mouseX, mouseY, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE);
            int craftFill = craftHovered ? 0xCC385465 : 0xAA24303A;
            drawPanelFrame(g, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE, craftFill, 0xFF6E8799, 0xFF111821);
            g.drawCenteredString(this.font, "C", dock.cX() + CRAFT_DOCK_C_SIZE / 2, dock.cY() + 5, 0xFFFFFF);
        }

        if (!hasProgressionNode(RtsProgressionNodes.REMOTE_GUI)) {
            return;
        }
        for (int slot = 0; slot < this.controller.getGuiBindingCount(); slot++) {
            int slotX = dock.slotX(slot);
            int slotY = dock.slotY(slot);
            boolean hovered = inside(mouseX, mouseY, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE);
            boolean pending = this.pendingGuiBindSlot == slot;
            boolean bound = this.controller.hasGuiBinding(slot);
            int fill = pending ? 0xCC2D6B47 : (bound ? 0xAA23384A : 0xAA202731);
            if (hovered) {
                fill = pending ? 0xDD377F53 : (bound ? 0xBB2C4760 : 0xBB29323D);
                this.hoveredGuiBindingSlot = slot;
            }
            drawPanelFrame(g, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE, fill, 0xFF698097, 0xFF0F151C);
            ItemStack preview = this.controller.getGuiBindingPreview(slot);
            if (bound && !pending && !preview.isEmpty()) {
                drawMiniItem(g, preview, slotX, slotY, CRAFT_DOCK_SLOT_SIZE);
            } else {
                String text = (!bound || pending) ? "+" : Integer.toString(slot + 1);
                g.drawCenteredString(this.font, text, slotX + CRAFT_DOCK_SLOT_SIZE / 2, slotY + 2, 0xFFFFFF);
            }
        }
    }

    private void drawMiniItem(GuiGraphics g, ItemStack stack, int slotX, int slotY, int slotSize) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float scale = Math.min(1.0F, slotSize / 16.0F);
        float inset = Math.max(0.0F, (slotSize - (16.0F * scale)) * 0.5F);
        g.pose().pushPose();
        g.pose().translate(slotX + inset, slotY + inset, 150.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private void drawGuiBindCursor(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX + 8;
        int y = mouseY + 8;
        drawPanelFrame(g, x, y, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE, 0xCC2D6B47, 0xFF78B28C, 0xFF0F151C);
        g.drawCenteredString(this.font, "+", x + CRAFT_DOCK_SLOT_SIZE / 2, y + 1, 0xFFFFFF);
    }

    private boolean handleCraftDockClick(double mouseX, double mouseY, int button, int x, int y) {
        CraftDockLayout dock = resolveCraftDockLayout(x, y);
        if (hasProgressionNode(RtsProgressionNodes.CRAFT_TERMINAL)
                && inside(mouseX, mouseY, dock.cX(), dock.cY(), CRAFT_DOCK_C_SIZE, CRAFT_DOCK_C_SIZE)) {
            this.controller.openCraftTerminal();
            return true;
        }

        if (!hasProgressionNode(RtsProgressionNodes.REMOTE_GUI)) {
            return false;
        }
        for (int slot = 0; slot < this.controller.getGuiBindingCount(); slot++) {
            int slotX = dock.slotX(slot);
            int slotY = dock.slotY(slot);
            if (!inside(mouseX, mouseY, slotX, slotY, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE)) {
                continue;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (this.pendingGuiBindSlot == slot) {
                    this.pendingGuiBindSlot = -1;
                } else if (this.controller.hasGuiBinding(slot)) {
                    this.pendingGuiBindSlot = -1;
                    this.controller.openGuiBinding(slot);
                } else {
                    this.pendingGuiBindSlot = slot;
                }
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (hasShiftDown()) {
                    if (this.pendingGuiBindSlot == slot) {
                        this.pendingGuiBindSlot = -1;
                    }
                    this.controller.clearGuiBinding(slot);
                    return true;
                }
                this.pendingGuiBindSlot = this.pendingGuiBindSlot == slot ? -1 : slot;
                return true;
            }

            return true;
        }
        return false;
    }

    private void renderFunnelBufferPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (this.controller.getMode() != BuilderMode.FUNNEL && this.controller.getFunnelBufferEntries().isEmpty()) {
            return;
        }

        int toggleX = this.width - FUNNEL_BUFFER_TOGGLE_W - 8;
        int toggleY = TOP_H + 6;
        int toggleBg = this.funnelBufferVisible ? 0xAA2C4E3D : 0xAA2A2D36;
        g.fill(toggleX, toggleY, toggleX + FUNNEL_BUFFER_TOGGLE_W, toggleY + FUNNEL_BUFFER_TOGGLE_H, toggleBg);
        g.drawCenteredString(this.font, "BUFFER", toggleX + FUNNEL_BUFFER_TOGGLE_W / 2, toggleY + 4, 0xFFFFFF);

        if (!this.funnelBufferVisible) {
            return;
        }

        int panelX = this.width - FUNNEL_BUFFER_PANEL_W - 8;
        int panelY = TOP_H + 26;
        int panelH = getFloatingPanelAvailableHeight(panelY);
        if (panelH < 20) {
            return;
        }
        g.fill(panelX, panelY, panelX + FUNNEL_BUFFER_PANEL_W, panelY + panelH, 0xAA17191F);
        g.drawString(this.font, "Funnel Buffer", panelX + 6, panelY + 4, 0xF0F0F0);

        List<ClientRtsController.FunnelBufferEntry> entries = this.controller.getFunnelBufferEntries();
        int listY = panelY + 16;
        int rows = Math.max(1, (panelH - 20) / FUNNEL_BUFFER_ROW_H);
        for (int i = 0; i < rows; i++) {
            int entryIndex = i;
            int rowY = listY + i * FUNNEL_BUFFER_ROW_H;
            if (entryIndex >= entries.size()) {
                break;
            }
            var entry = entries.get(entryIndex);
            int rowX = panelX + 4;
            int rowW = FUNNEL_BUFFER_PANEL_W - 8;
            g.fill(rowX, rowY, rowX + rowW, rowY + FUNNEL_BUFFER_ROW_H - 2, 0x88303845);

            int slotX = rowX + 2;
            int slotY = rowY + 2;
            g.fill(slotX, slotY, slotX + 18, slotY + 18, 0xAA1E222A);
            g.renderItem(entry.stack(), slotX + 1, slotY + 1);
            g.drawString(this.font, trimToWidth(entry.stack().getHoverName().getString(), rowW - 30), rowX + 24, rowY + 3, 0xFFFFFF);
            g.drawString(this.font, "x" + compactCount(entry.count()), rowX + 24, rowY + 12, 0xFFDFAE);

            if (inside(mouseX, mouseY, rowX, rowY, rowW, FUNNEL_BUFFER_ROW_H - 2)) {
                this.hoveredFunnelBufferEntry = entryIndex;
                g.fill(rowX, rowY, rowX + rowW, rowY + FUNNEL_BUFFER_ROW_H - 2, 0x33FFFFFF);
            }
        }

        if (entries.isEmpty()) {
            g.drawString(this.font, "empty", panelX + 6, panelY + 20, 0x99B4BCC8);
        }
    }

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

    private void renderStorageScanPopup(GuiGraphics g) {
        if (!this.controller.isStorageScanPopupVisible()) {
            return;
        }
        BottomPanelLayout layout = resolveBottomPanelLayout();
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

    private void drawTopButton(GuiGraphics g, int mouseX, int mouseY, TopBarButtonLayout button) {
        if (button.iconOnly()) {
            drawTopIconButton(g, mouseX, mouseY, button);
            return;
        }
        drawTopButtonSized(g, button.x(), button.label(), button.active(), button.width());
    }

    private void drawTopButtonSized(GuiGraphics g, int x, String label, boolean active, int w) {
        int y = 4;
        int h = TOP_BUTTON_H;
        int bg = active ? 0xFF2E6A50 : 0xAA1F2329;
        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, 0xFF5B6673);
        g.hLine(x, x + w, y + h, 0xFF0D0E10);
        g.vLine(x, y, y + h, 0xFF5B6673);
        g.vLine(x + w, y, y + h, 0xFF0D0E10);
        g.drawCenteredString(this.font, trimToWidth(label, Math.max(6, w - 8)), x + w / 2, y + 8, 0xFFFFFF);
    }

    private void drawTopIconButton(GuiGraphics g, int mouseX, int mouseY, TopBarButtonLayout button) {
        int x = button.x();
        int y = 4;
        int w = button.width();
        int h = TOP_BUTTON_H;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        boolean pressed = hovered && isPrimaryMouseDown();

        int bg = 0xAA1F2329;
        int light = 0xFF5B6673;
        int dark = 0xFF0D0E10;
        int icon = 0xFFBDC9D6;
        if (button.active()) {
            bg = 0xFF2D6B47;
            light = 0xFF9AD2AE;
            icon = 0xFFF4FBF5;
        } else if (pressed) {
            bg = 0xFF1F5037;
            light = 0xFF6AA784;
            icon = 0xFFD9E3EF;
        } else if (hovered) {
            bg = 0xFF1D2530;
            light = 0xFF7A90AA;
            icon = 0xFFD9E3EF;
        }

        ResourceLocation textureIcon = topbarModeTexture(button.id(), button.active(), hovered, pressed);
        if (textureIcon != null) {
            g.blit(textureIcon, x + (w - TOP_BUTTON_H) / 2, y, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
            return;
        }

        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, light);
        g.hLine(x, x + w, y + h, dark);
        g.vLine(x, y, y + h, light);
        g.vLine(x + w, y, y + h, dark);
        if (pressed) {
            g.hLine(x + 1, x + w - 1, y + 1, dark);
            g.vLine(x + 1, y + 1, y + h - 1, dark);
        }

        int cx = x + (w / 2);
        int cy = y + (h / 2);
        switch (button.id()) {
            case INTERACT -> drawInteractModeIcon(g, cx, cy, icon);
            case LINK -> drawLinkModeIcon(g, cx, cy, icon, button.active());
            case FUNNEL -> drawFunnelModeIcon(g, cx, cy, icon, button.active());
            case ROTATE -> drawRotateModeIcon(g, cx, cy, icon);
            case QUICK_BUILD -> drawQuickBuildIcon(g, cx, cy, icon, button.active());
            case ULTIMINE -> drawUltimineIcon(g, cx, cy, icon, button.active());
            case QUEST_DETECT -> drawQuestCheckIcon(g, cx, cy, icon);
            case CHUNK_VIEW -> drawChunkCurtainIcon(g, cx, cy, icon, button.active());
            case DEBUG -> drawDebugIcon(g, cx, cy, icon);
            case GEAR -> drawGearIcon(g, cx, cy, icon);
            case GUIDE -> g.drawCenteredString(this.font, "i", cx, y + 7, icon);
            default -> g.drawCenteredString(this.font, "?", cx, y + 6, icon);
        }
    }

    private ResourceLocation topbarModeTexture(TopBarButtonId id, boolean active, boolean hovered, boolean pressed) {
        String state = active ? "active" : pressed ? "pressed" : hovered ? "hover" : "inactive";
        return switch (id) {
            case INTERACT -> switch (state) {
                case "active" -> TOPBAR_INTERACT_ACTIVE;
                case "pressed" -> TOPBAR_INTERACT_PRESSED;
                case "hover" -> TOPBAR_INTERACT_HOVER;
                default -> TOPBAR_INTERACT_INACTIVE;
            };
            case LINK -> switch (state) {
                case "active" -> TOPBAR_LINK_ACTIVE;
                case "pressed" -> TOPBAR_LINK_PRESSED;
                case "hover" -> TOPBAR_LINK_HOVER;
                default -> TOPBAR_LINK_INACTIVE;
            };
            case FUNNEL -> switch (state) {
                case "active" -> TOPBAR_FUNNEL_ACTIVE;
                case "pressed" -> TOPBAR_FUNNEL_PRESSED;
                case "hover" -> TOPBAR_FUNNEL_HOVER;
                default -> TOPBAR_FUNNEL_INACTIVE;
            };
            case ROTATE -> switch (state) {
                case "active" -> TOPBAR_ROTATE_ACTIVE;
                case "pressed" -> TOPBAR_ROTATE_PRESSED;
                case "hover" -> TOPBAR_ROTATE_HOVER;
                default -> TOPBAR_ROTATE_INACTIVE;
            };
            case QUICK_BUILD -> switch (state) {
                case "active" -> TOPBAR_QUICK_BUILD_ACTIVE;
                case "pressed" -> TOPBAR_QUICK_BUILD_PRESSED;
                case "hover" -> TOPBAR_QUICK_BUILD_HOVER;
                default -> TOPBAR_QUICK_BUILD_INACTIVE;
            };
            case ULTIMINE -> switch (state) {
                case "active" -> TOPBAR_ULTIMINE_ACTIVE;
                case "pressed" -> TOPBAR_ULTIMINE_PRESSED;
                case "hover" -> TOPBAR_ULTIMINE_HOVER;
                default -> TOPBAR_ULTIMINE_INACTIVE;
            };
            case QUEST_DETECT -> switch (state) {
                case "active" -> TOPBAR_QUEST_DETECT_ACTIVE;
                case "pressed" -> TOPBAR_QUEST_DETECT_PRESSED;
                case "hover" -> TOPBAR_QUEST_DETECT_HOVER;
                default -> TOPBAR_QUEST_DETECT_INACTIVE;
            };
            case CHUNK_VIEW -> switch (state) {
                case "active" -> TOPBAR_CHUNK_VIEW_ACTIVE;
                case "pressed" -> TOPBAR_CHUNK_VIEW_PRESSED;
                case "hover" -> TOPBAR_CHUNK_VIEW_HOVER;
                default -> TOPBAR_CHUNK_VIEW_INACTIVE;
            };
            case GEAR -> switch (state) {
                case "active" -> TOPBAR_GEAR_ACTIVE;
                case "pressed" -> TOPBAR_GEAR_PRESSED;
                case "hover" -> TOPBAR_GEAR_HOVER;
                default -> TOPBAR_GEAR_INACTIVE;
            };
            default -> null;
        };
    }

    private boolean isPrimaryMouseDown() {
        return this.minecraft != null
                && this.minecraft.getWindow() != null
                && GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void drawInteractModeIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy - 8, cx - 5, cy + 7, color);
        g.fill(cx - 5, cy - 6, cx - 3, cy + 5, color);
        g.fill(cx - 3, cy - 4, cx - 1, cy + 3, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, color);
        g.fill(cx + 2, cy, cx + 5, cy + 3, color);
        g.fill(cx - 2, cy + 3, cx + 1, cy + 8, color);
        g.fill(cx + 1, cy + 6, cx + 4, cy + 8, color);
        g.fill(cx + 4, cy - 7, cx + 7, cy - 4, 0x6688BEF4);
        g.fill(cx + 5, cy - 6, cx + 6, cy - 5, color);
    }

    private void drawLinkModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int left = active ? 0xFF88BEF4 : color;
        int right = active ? 0xFF78B28C : color;
        drawMiniChainLoop(g, cx - 5, cy + 1, left);
        drawMiniChainLoop(g, cx + 5, cy - 1, right);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 1, color);
        g.fill(cx - 2, cy, cx + 3, cy + 2, color);
    }

    private void drawFunnelModeIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int top = active ? 0xFFFFC472 : color;
        int mid = active ? 0xFF78B28C : color;
        int tip = active ? 0xFF88BEF4 : color;
        g.fill(cx - 8, cy - 7, cx + 8, cy - 5, top);
        g.fill(cx - 7, cy - 5, cx + 7, cy - 3, top);
        g.fill(cx - 5, cy - 3, cx + 5, cy - 1, mid);
        g.fill(cx - 3, cy - 1, cx + 3, cy + 1, mid);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 7, tip);
        g.fill(cx + 1, cy + 5, cx + 4, cy + 7, tip);
    }

    private void drawRotateModeIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 5, cy - 7, cx + 5, cy - 5, color);
        g.fill(cx + 4, cy - 6, cx + 7, cy - 2, color);
        g.fill(cx + 6, cy - 2, cx + 8, cy + 1, color);
        g.fill(cx + 3, cy - 8, cx + 8, cy - 5, color);
        g.fill(cx + 5, cy - 4, cx + 8, cy - 1, color);
        g.fill(cx - 8, cy - 1, cx - 6, cy + 3, color);
        g.fill(cx - 7, cy + 3, cx - 4, cy + 6, color);
        g.fill(cx - 5, cy + 5, cx + 5, cy + 7, color);
        g.fill(cx - 8, cy + 4, cx - 3, cy + 7, color);
        g.fill(cx - 8, cy + 1, cx - 5, cy + 4, color);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF1B222C);
        g.hLine(cx - 3, cx + 3, cy - 3, color);
        g.hLine(cx - 3, cx + 3, cy + 3, color);
        g.vLine(cx - 3, cy - 3, cy + 3, color);
        g.vLine(cx + 3, cy - 3, cy + 3, color);
    }

    private void drawQuickBuildIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int accent = active ? 0xFFFFC96B : color;
        g.fill(cx - 8, cy - 6, cx + 6, cy - 4, accent);
        g.fill(cx - 8, cy - 6, cx - 6, cy + 6, accent);
        g.fill(cx - 8, cy + 4, cx + 6, cy + 6, accent);
        g.fill(cx + 4, cy - 6, cx + 6, cy + 6, accent);
        g.fill(cx - 4, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.fill(cx + 3, cy - 9, cx + 8, cy - 4, color);
        g.fill(cx + 5, cy - 7, cx + 10, cy - 2, color);
    }

    private void drawUltimineIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int head = active ? 0xFF78B28C : color;
        g.fill(cx - 8, cy - 7, cx - 1, cy - 5, head);
        g.fill(cx - 6, cy - 5, cx + 1, cy - 3, head);
        g.fill(cx + 1, cy - 3, cx + 3, cy - 1, color);
        g.fill(cx + 2, cy - 1, cx + 5, cy + 7, color);
        g.fill(cx + 5, cy - 8, cx + 7, cy - 3, 0xFFFFC96B);
        g.fill(cx + 3, cy - 6, cx + 9, cy - 5, 0xFFFFC96B);
    }

    private void drawMiniChainLoop(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 5, cy - 4, cx + 5, cy - 2, color);
        g.fill(cx - 5, cy + 2, cx + 5, cy + 4, color);
        g.fill(cx - 5, cy - 3, cx - 3, cy + 3, color);
        g.fill(cx + 3, cy - 3, cx + 5, cy + 3, color);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
    }

    private void drawQuestCheckIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy + 1, cx - 3, cy + 5, color);
        g.fill(cx - 4, cy + 4, cx, cy + 8, color);
        g.fill(cx - 1, cy + 1, cx + 3, cy + 5, color);
        g.fill(cx + 2, cy - 2, cx + 6, cy + 2, color);
        g.fill(cx + 5, cy - 5, cx + 9, cy - 1, color);
    }

    private void drawChunkCurtainIcon(GuiGraphics g, int cx, int cy, int color, boolean active) {
        int glow = active ? 0x4488BEF4 : 0x221D2530;
        g.fill(cx - 8, cy - 7, cx + 8, cy + 7, glow);
        g.fill(cx - 7, cy - 6, cx - 6, cy + 6, color);
        g.fill(cx - 1, cy - 6, cx, cy + 6, color);
        g.fill(cx + 5, cy - 6, cx + 6, cy + 6, color);
        g.fill(cx - 7, cy - 6, cx + 6, cy - 5, color);
        g.fill(cx - 7, cy, cx + 6, cy + 1, color);
        g.fill(cx - 7, cy + 6, cx + 6, cy + 7, color);
    }

    private void drawGearIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 2, cy - 8, cx + 2, cy - 5, color);
        g.fill(cx - 2, cy + 5, cx + 2, cy + 8, color);
        g.fill(cx - 8, cy - 2, cx - 5, cy + 2, color);
        g.fill(cx + 5, cy - 2, cx + 8, cy + 2, color);
        g.fill(cx - 6, cy - 6, cx - 3, cy - 3, color);
        g.fill(cx + 3, cy - 6, cx + 6, cy - 3, color);
        g.fill(cx - 6, cy + 3, cx - 3, cy + 6, color);
        g.fill(cx + 3, cy + 3, cx + 6, cy + 6, color);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, color);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1B222C);
    }

    private void drawDebugIcon(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0x3328D4FF);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, color);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF1B222C);
        g.drawCenteredString(this.font, "D", cx, cy - 4, 0xFF1B222C);
    }

    private List<TopBarButtonLayout> buildTopBarButtonLayouts() {
        List<TopBarButtonLayout> layouts = new ArrayList<>();
        int x = 8;
        layouts.add(new TopBarButtonLayout(TopBarButtonId.INTERACT, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.INTERACT));
        x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        if (hasProgressionNode(RtsProgressionNodes.STORAGE_LINK)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.LINK, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.LINK));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (hasProgressionNode(RtsProgressionNodes.FUNNEL)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.FUNNEL, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.FUNNEL));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (hasProgressionNode(RtsProgressionNodes.ROTATE_BLOCK)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.ROTATE, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.ROTATE));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        x += 8;
        if (hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.QUICK_BUILD, x, TOP_ICON_BUTTON_W, "", true, this.quickBuildOpen));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (hasProgressionNode(RtsProgressionNodes.ULTIMINE)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.ULTIMINE, x, TOP_ICON_BUTTON_W, "", true, this.ultimineOpen));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (isFtbQuestIntegrationLoaded()) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.QUEST_DETECT, x, TOP_ICON_BUTTON_W, "", true, this.controller.isQuestDetectPopupVisible()));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        layouts.add(new TopBarButtonLayout(TopBarButtonId.CHUNK_VIEW, x, TOP_ICON_BUTTON_W, "", true, this.controller.isChunkCurtainVisible()));
        x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        layouts.add(new TopBarButtonLayout(TopBarButtonId.GUIDE, x, TOP_ICON_BUTTON_W, "", true, this.guideOpen));
        int gearX = Math.max(x + TOP_BUTTON_GAP, this.width - TOP_ICON_BUTTON_W - 8);
        if (this.debugButtonVisible) {
            int debugX = gearX - TOP_ICON_BUTTON_W - TOP_BUTTON_GAP;
            layouts.add(new TopBarButtonLayout(TopBarButtonId.DEBUG, debugX, TOP_ICON_BUTTON_W, "", true, false));
        }
        layouts.add(new TopBarButtonLayout(TopBarButtonId.GEAR, gearX, TOP_ICON_BUTTON_W, "", true, this.gearMenuOpen));
        return layouts;
    }

    private boolean hasProgressionNode(ResourceLocation nodeId) {
        return !this.controller.isProgressionEnabled()
                || nodeId == null
                || this.controller.getUnlockedProgressionNodes().contains(nodeId.toString());
    }

    private boolean isFtbQuestIntegrationLoaded() {
        return ModList.get().isLoaded("ftbquests")
                || ModList.get().isLoaded("ftb_quests")
                || ModList.get().isLoaded("ftblibrary");
    }

    private static boolean hasRecipeViewerLoaded() {
        return ModList.get().isLoaded("jei")
                || ModList.get().isLoaded("emi")
                || ModList.get().isLoaded("roughlyenoughitems");
    }

    private static ResourceLocation quickBuildTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/quickbuild/" + key + ".png");
        return id == null ? new ResourceLocation("minecraft", "missingno") : id;
    }

    private static ResourceLocation topbarTexture(String key) {
        ResourceLocation id = ResourceLocation.tryParse("rtsbuilding:textures/gui/topbar/" + key + ".png");
        return id == null ? new ResourceLocation("minecraft", "missingno") : id;
    }

    private void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        this.quickBuildOpen = state.quickBuildOpen;
        this.ultimineOpen = state.ultimineOpen;
        this.ultimineLimit = clampUltimineLimit(state.ultimineLimit);
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);
        this.debugButtonVisible = state.debugButtonVisible;
        this.controller.setHorizontalSensitivityByFraction(
                (state.horizontalSensitivityScale - 0.25F) / (3.00F - 0.25F));
        this.controller.setVerticalSensitivityByFraction(
                (state.verticalSensitivityScale - 0.25F) / (3.00F - 0.25F));
        this.controller.setRotateSensitivityByFraction(
                (state.rotateSensitivityScale - 0.25F) / (3.00F - 0.25F));
        this.controller.setChunkCurtainVisible(state.chunkCurtainVisible);
        try {
            this.controller.setBuildShape(ClientRtsController.BuildShape.valueOf(state.buildShape));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
        }
        try {
            this.shapeFillMode = ShapeFillMode.valueOf(state.fillMode);
        } catch (IllegalArgumentException ignored) {
            this.shapeFillMode = ShapeFillMode.FILL;
        }
        this.shapeRotateDegrees = Math.floorMod(state.rotationDegrees, 360);
        ensureFillModeForShape(this.controller.getBuildShape());
    }

    private void saveSensitivityState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.horizontalSensitivityScale = this.controller.getHorizontalSensitivityScale();
        state.verticalSensitivityScale = this.controller.getVerticalSensitivityScale();
        state.rotateSensitivityScale = this.controller.getRotateSensitivityScale();
        RtsClientUiStateStore.save(state);
    }

    private void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.controller.getBuildShape().name();
        state.fillMode = this.shapeFillMode.name();
        state.rotationDegrees = this.shapeRotateDegrees;
        state.quickBuildOpen = this.quickBuildOpen;
        state.ultimineOpen = this.ultimineOpen;
        state.ultimineLimit = this.ultimineLimit;
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.horizontalSensitivityScale = this.controller.getHorizontalSensitivityScale();
        state.verticalSensitivityScale = this.controller.getVerticalSensitivityScale();
        state.rotateSensitivityScale = this.controller.getRotateSensitivityScale();
        state.startCameraAtPlayerHead = this.controller.isStartCameraAtPlayerHead();
        state.allowPlacedBlockRecovery = this.controller.isAllowPlacedBlockRecovery();
        state.invertPanDragX = this.controller.isInvertPanDragX();
        state.invertPanDragY = this.controller.isInvertPanDragY();
        state.smoothCamera = this.controller.isSmoothCamera();
        state.damageSoundEnabled = this.controller.isDamageSoundEnabled();
        state.damageAutoReturnEnabled = this.controller.isDamageAutoReturnEnabled();
        state.debugButtonVisible = this.debugButtonVisible;
        RtsClientUiStateStore.save(state);
    }

    private void renderGearMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.gearMenuOpen) {
            return;
        }
        int w = Math.min(300, this.width - 24);
        int h = gearMenuHeight();
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        this.gearMenuScroll = Mth.clamp(this.gearMenuScroll, 0, maxGearMenuScroll(h));
        drawPanelFrame(g, x, y, w, h, 0xF0181D25, 0xFF6D7C90, 0xFF0A0D12);
        g.fill(x + 3, y + 3, x + w - 3, y + 24, 0xFF2A303A);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.title"), x + 12, y + 10, 0xF4F7FF);
        drawPanelFrame(g, x + w - 44, y + 6, 16, 16, 0xCC3D516D, 0xFF8FA4BF, 0xFF0D1218);
        g.drawCenteredString(this.font, "i", x + w - 36, y + 10, 0xDDE8F4);
        drawPanelFrame(g, x + w - 24, y + 6, 16, 16, 0xCC3D516D, 0xFF8FA4BF, 0xFF0D1218);
        g.drawCenteredString(this.font, "x", x + w - 16, y + 10, 0xDDE8F4);

        int viewportTop = gearMenuViewportTop(y);
        int viewportBottom = gearMenuViewportBottom(y, h);
        enableRtsScissor(g, x + 8, viewportTop, x + w - 8, viewportBottom);
        renderGearMenuControls(g, mouseX, mouseY, x, y, w);
        g.disableScissor();
        renderGearMenuScrollbar(g, x, y, w, h);
    }

    private void renderGearMenuControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        int controlsY = gearMenuContentY(y);
        drawSettingsSection(g, x + 8, controlsY, w - 16, GEAR_MENU_CONTENT_H, Component.translatable("screen.rtsbuilding.settings.controls").getString());

        int trackX = x + 16;
        int trackW = w - 32;

        // Horizontal speed slider
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.horizontal_speed"), x + 16, controlsY + 20, 0xC8D3DF);
        g.drawString(this.font, this.controller.getHorizontalSensitivityLabel(), x + w - 60, controlsY + 20, 0xEAF4FF);
        int horzTrackY = controlsY + 38;
        drawSensitivityTrack(g, trackX, horzTrackY, trackW);
        int horzKnobX = trackX + (int) Math.round(this.controller.getHorizontalSensitivityFraction() * trackW);
        g.fill(horzKnobX - 3, horzTrackY - 5, horzKnobX + 4, horzTrackY + 8, 0xFF5FE36C);

        // Vertical speed slider
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.vertical_speed"), x + 16, controlsY + 60, 0xC8D3DF);
        g.drawString(this.font, this.controller.getVerticalSensitivityLabel(), x + w - 60, controlsY + 60, 0xEAF4FF);
        int vertTrackY = controlsY + 78;
        drawSensitivityTrack(g, trackX, vertTrackY, trackW);
        int vertKnobX = trackX + (int) Math.round(this.controller.getVerticalSensitivityFraction() * trackW);
        g.fill(vertKnobX - 3, vertTrackY - 5, vertKnobX + 4, vertTrackY + 8, 0xFF5FE36C);

        // Rotate speed slider
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.rotate_speed"), x + 16, controlsY + 100, 0xC8D3DF);
        g.drawString(this.font, this.controller.getRotateSensitivityLabel(), x + w - 60, controlsY + 100, 0xEAF4FF);
        int rotTrackY = controlsY + 118;
        drawSensitivityTrack(g, trackX, rotTrackY, trackW);
        int rotKnobX = trackX + (int) Math.round(this.controller.getRotateSensitivityFraction() * trackW);
        g.fill(rotKnobX - 3, rotTrackY - 5, rotKnobX + 4, rotTrackY + 8, 0xFF5FE36C);

        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.slow"), trackX, rotTrackY + 10, 0xB5C1CE);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.fast"), trackX + trackW - 24, rotTrackY + 10, 0xB5C1CE);

        int scaleButtonY = controlsY + 150;
        int minusX = x + w - 124;
        int valueX = minusX + 26;
        int plusX = valueX + 60;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.ui_scale"), x + 16, scaleButtonY + 7, 0xC8D3DF);
        drawGearMenuRow(g, mouseX, mouseY, minusX, scaleButtonY, 22, 22, "-", false);
        drawPanelFrame(g, valueX, scaleButtonY, 56, 22, 0xCC1A232E, 0xFF566B80, 0xFF0D1218);
        g.drawCenteredString(this.font, rtsGuiScaleLabel(), valueX + 28, scaleButtonY + 7, 0xEAF4FF);
        drawGearMenuRow(g, mouseX, mouseY, plusX, scaleButtonY, 22, 22, "+", false);

        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.auto_store"), x + 16, controlsY + 198, 0xC8D3DF);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 192, 76, 22, this.controller.isAutoStoreMinedDrops(),
                text(this.controller.isAutoStoreMinedDrops() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        g.drawString(this.font, Component.translatable("screen.rtsbuilding.settings.head_start"), x + 16, controlsY + 226, 0xC8D3DF);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, controlsY + 220, 76, 22, this.controller.isStartCameraAtPlayerHead(),
                text(this.controller.isStartCameraAtPlayerHead() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));

        int placedRecoveryY = controlsY + 248;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, placedRecoveryY,
                "screen.rtsbuilding.settings.placed_recovery",
                "screen.rtsbuilding.settings.placed_recovery.hint",
                this.controller.isAllowPlacedBlockRecovery());

        int debugButtonY = controlsY + 284;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, debugButtonY,
                "screen.rtsbuilding.settings.debug_button",
                "screen.rtsbuilding.settings.debug_button.hint",
                this.debugButtonVisible);

        int overlayToggleY = controlsY + 320;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, overlayToggleY,
                "screen.rtsbuilding.settings.container_overlay",
                "screen.rtsbuilding.settings.container_overlay.hint",
                RtsClientUiStateStore.isContainerOverlayEnabled());

        int panDragXToggleY = controlsY + 276;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, panDragXToggleY,
                "screen.rtsbuilding.settings.pan_drag_x_invert",
                "screen.rtsbuilding.settings.pan_drag_x_invert.hint",
                this.controller.isInvertPanDragX());

        int panDragYToggleY = controlsY + 312;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, panDragYToggleY,
                "screen.rtsbuilding.settings.pan_drag_y_invert",
                "screen.rtsbuilding.settings.pan_drag_y_invert.hint",
                this.controller.isInvertPanDragY());

        int smoothCameraToggleY = controlsY + 348;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, smoothCameraToggleY,
                "screen.rtsbuilding.settings.smooth_camera",
                "screen.rtsbuilding.settings.smooth_camera.hint",
                this.controller.isSmoothCamera());

        int damageSoundToggleY = controlsY + 384;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, damageSoundToggleY,
                "screen.rtsbuilding.settings.damage_sound",
                "screen.rtsbuilding.settings.damage_sound.hint",
                this.controller.isDamageSoundEnabled());

        int damageAutoReturnToggleY = controlsY + 420;
        drawSettingsToggleWithHint(g, mouseX, mouseY, x, w, damageAutoReturnToggleY,
                "screen.rtsbuilding.settings.damage_auto_return",
                "screen.rtsbuilding.settings.damage_auto_return.hint",
                this.controller.isDamageAutoReturnEnabled());
    }

    private void drawSensitivityTrack(GuiGraphics g, int trackX, int trackY, int trackW) {
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF07090D);
        g.fill(trackX + 1, trackY + 1, trackX + trackW - 1, trackY + 3, 0xFF313946);
    }

    private void drawSettingsToggleWithHint(GuiGraphics g, int mouseX, int mouseY, int x, int w, int rowY,
            String labelKey, String hintKey, boolean active) {
        g.drawString(this.font, trimToWidth(text(labelKey), w - 126), x + 16, rowY + 2, 0xC8D3DF);
        g.drawString(this.font, trimToWidth(text(hintKey), w - 126), x + 16, rowY + 13, 0x9FB0C2);
        drawToggleButton(g, mouseX, mouseY, x + w - 92, rowY + 4, 76, 22, active,
                text(active ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off"));
    }

    private void renderGearMenuScrollbar(GuiGraphics g, int x, int y, int w, int h) {
        int maxScroll = maxGearMenuScroll(h);
        if (maxScroll <= 0) {
            return;
        }
        int viewportTop = gearMenuViewportTop(y);
        int viewportBottom = gearMenuViewportBottom(y, h);
        int trackX = x + w - 7;
        int trackH = Math.max(1, viewportBottom - viewportTop);
        g.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0x88313A46);
        int thumbH = Math.max(18, (int) Math.round(trackH * (trackH / (double) (GEAR_MENU_CONTENT_H + 12))));
        int thumbY = viewportTop + (int) Math.round((trackH - thumbH) * (this.gearMenuScroll / (double) maxScroll));
        g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xCC8AA0B8);
    }

    private int gearMenuHeight() {
        return Mth.clamp(Math.min(GEAR_MENU_H, this.height - 24), GEAR_MENU_MIN_H, GEAR_MENU_H);
    }

    private int maxGearMenuScroll(int menuH) {
        int viewportH = Math.max(1, gearMenuViewportBottom(0, menuH) - gearMenuViewportTop(0));
        return Math.max(0, GEAR_MENU_CONTENT_H + 8 - viewportH);
    }

    private int gearMenuViewportTop(int menuY) {
        return menuY + 30;
    }

    private int gearMenuViewportBottom(int menuY, int menuH) {
        return menuY + menuH - 8;
    }

    private int gearMenuContentY(int menuY) {
        return gearMenuViewportTop(menuY) - this.gearMenuScroll;
    }

    private boolean isInsideGearMenu(double mouseX, double mouseY) {
        int w = Math.min(300, this.width - 24);
        int h = gearMenuHeight();
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        return inside(mouseX, mouseY, x, y, w, h);
    }

    private boolean scrollGearMenu(double scrollY) {
        int maxScroll = maxGearMenuScroll(gearMenuHeight());
        if (maxScroll <= 0) {
            return true;
        }
        int delta = scrollY > 0.0D ? -18 : 18;
        this.gearMenuScroll = Mth.clamp(this.gearMenuScroll + delta, 0, maxScroll);
        return true;
    }

    private void drawSettingsSection(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.drawString(this.font, title, x + 2, y, 0xF4F7FF);
        drawPanelFrame(g, x, y + 12, w, h - 12, 0xDD111720, 0xFF384351, 0xFF080B10);
    }

    private void drawToggleButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, boolean active, String label) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? (hover ? 0xDD45BA53 : 0xDD329A42) : (hover ? 0xDD3D4957 : 0xDD28313C);
        drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF8EF19A : 0xFF68788A, 0xFF10151B);
        int switchX = active ? x + w - 26 : x + 6;
        g.fill(switchX, y + 4, switchX + 18, y + h - 4, active ? 0xFF72F07A : 0xFF788696);
        g.drawCenteredString(this.font, label, x + w / 2, y + 7, 0xF7FBFF);
    }

    private void drawGearMenuRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean active) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = active ? 0xCC2D7C4B : (hover ? 0xCC334054 : 0xCC26303D);
        drawPanelFrame(g, x, y, w, h, bg, 0xFF6A8299, 0xFF0E1116);
        g.drawCenteredString(this.font, trimToWidth(label, w - 10), x + w / 2, y + 7, 0xF2F6FB);
    }

    private boolean handleGearMenuClick(double mouseX, double mouseY) {
        if (!this.gearMenuOpen) {
            return false;
        }
        int w = Math.min(300, this.width - 24);
        int h = gearMenuHeight();
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        if (!inside(mouseX, mouseY, x, y, w, h)) {
            return false;
        }

        if (inside(mouseX, mouseY, x + w - 24, y + 6, 16, 16)) {
            this.gearMenuOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, x + w - 44, y + 6, 16, 16)) {
            this.gearMenuOpen = false;
            openGuide(GuideContext.SETTINGS);
            return true;
        }
        int viewportTop = gearMenuViewportTop(y);
        int viewportBottom = gearMenuViewportBottom(y, h);
        if (!inside(mouseX, mouseY, x + 8, viewportTop, w - 16, viewportBottom - viewportTop)) {
            return true;
        }
        double contentMouseY = mouseY + this.gearMenuScroll;
        int controlsY = y + 30;
        if (inside(mouseX, contentMouseY, x + 16, controlsY + 30, w - 32, 24)) {
            this.draggingHorizontalSensitivity = true;
            updateHorizontalSensitivityFromMouse(mouseX);
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 16, controlsY + 70, w - 32, 24)) {
            this.draggingVerticalSensitivity = true;
            updateVerticalSensitivityFromMouse(mouseX);
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 16, controlsY + 110, w - 32, 24)) {
            this.draggingRotateSensitivity = true;
            updateRotateSensitivityFromMouse(mouseX);
            return true;
        }
        int scaleButtonY = controlsY + 150;
        int minusX = x + w - 124;
        int plusX = minusX + 86;
        if (inside(mouseX, contentMouseY, minusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(-RTS_GUI_SCALE_STEP);
            return true;
        }
        if (inside(mouseX, contentMouseY, plusX, scaleButtonY, 22, 22)) {
            adjustRtsGuiScale(RTS_GUI_SCALE_STEP);
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 144, w - 24, 42)) {
            this.controller.toggleAutoStoreMinedDrops();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 172, w - 24, 36)) {
            this.controller.toggleStartCameraAtPlayerHead();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 204, w - 24, 34)) {
            this.controller.toggleAllowPlacedBlockRecovery();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 240, w - 24, 34)) {
            this.debugButtonVisible = !this.debugButtonVisible;
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 276, w - 24, 34)) {
            RtsClientUiStateStore.setContainerOverlayEnabled(!RtsClientUiStateStore.isContainerOverlayEnabled());
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 272, w - 24, 34)) {
            this.controller.toggleInvertPanDragX();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 308, w - 24, 34)) {
            this.controller.toggleInvertPanDragY();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 344, w - 24, 34)) {
            this.controller.toggleSmoothCamera();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 380, w - 24, 34)) {
            this.controller.toggleDamageSoundEnabled();
            persistUiState();
            return true;
        }
        if (inside(mouseX, contentMouseY, x + 12, controlsY + 416, w - 24, 34)) {
            this.controller.toggleDamageAutoReturnEnabled();
            persistUiState();
            return true;
        }
        return true;
    }

    private void updateHorizontalSensitivityFromMouse(double mouseX) {
        int w = Math.min(300, this.width - 24);
        int x = (this.width - w) / 2;
        int trackX = x + 16;
        int trackW = w - 32;
        double fraction = (mouseX - trackX) / Math.max(1.0D, trackW);
        this.controller.setHorizontalSensitivityByFraction(fraction);
    }

    private void updateVerticalSensitivityFromMouse(double mouseX) {
        int w = Math.min(300, this.width - 24);
        int x = (this.width - w) / 2;
        int trackX = x + 16;
        int trackW = w - 32;
        double fraction = (mouseX - trackX) / Math.max(1.0D, trackW);
        this.controller.setVerticalSensitivityByFraction(fraction);
    }

    private void updateRotateSensitivityFromMouse(double mouseX) {
        int w = Math.min(300, this.width - 24);
        int x = (this.width - w) / 2;
        int trackX = x + 16;
        int trackW = w - 32;
        double fraction = (mouseX - trackX) / Math.max(1.0D, trackW);
        this.controller.setRotateSensitivityByFraction(fraction);
    }

    private void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }

    private String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }

    private void renderQuickBuildPanel(GuiGraphics g, int mouseX, int mouseY) {
        QuickBuildPanelLayout layout = resolveQuickBuildPanelLayout();
        if (layout == null) {
            return;
        }
        int x = layout.x();
        int y = layout.y();
        int panelH = layout.h();
        drawPanelFrame(g, x, y, QUICK_BUILD_PANEL_W, panelH, 0xEE161C24, 0xFF6C839A, 0xFF0D1117);
        g.fill(x + 1, y + 1, x + QUICK_BUILD_PANEL_W - 1, y + 20, 0xCC233345);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.quick_build.title"), x + 8, y + 6, 0xF2F7FF);

        int shapeTitleY = y + 30;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.quick_build.shape"), x + 8, shapeTitleY, 0xD8E3EE);
        ClientRtsController.BuildShape[] shapes = new ClientRtsController.BuildShape[] {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = y + 40 + (row * (QUICK_BUILD_SHAPE_SLOT + 6));
            boolean hover = inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT);
            boolean selected = shapes[i] == this.controller.getBuildShape();
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            drawPanelFrame(g, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT, bg, 0xFF647B92, 0xFF0D1117);
            drawShapeTexture(g, shapes[i], selected ? "active" : (hover ? "hover" : "inactive"), slotX, slotY);
        }

        int rightX = x + 88;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.quick_build.fill"), rightX, shapeTitleY, 0xD8E3EE);
        List<ShapeFillMode> modes = availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = y + 42 + (i * 20);
            ShapeFillMode mode = modes.get(i);
            boolean selected = this.shapeFillMode == mode;
            boolean hover = inside(mouseX, mouseY, rightX, rowY, 84, 16);
            int bg = selected ? 0xAA2D6B47 : (hover ? 0xAA243547 : 0xAA1C232D);
            drawPanelFrame(g, rightX, rowY, 84, 16, bg, 0xFF647B92, 0xFF0D1117);
            g.fill(rightX + 4, rowY + 4, rightX + 12, rowY + 12, 0xAA111820);
            if (selected) {
                g.fill(rightX + 6, rowY + 6, rightX + 10, rowY + 10, 0xFF78B28C);
            }
            g.drawString(this.font, fillModeLabel(mode), rightX + 18, rowY + 4, 0xF2F7FF);
        }

        int rotY = y + 120;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.quick_build.rotation"), rightX, rotY, 0xD8E3EE);
        drawPanelFrame(g, rightX, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "-", rightX + 10, rotY + 15, 0xFFFFFF);
        drawPanelFrame(g, rightX + 24, rotY + 10, 56, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, this.shapeRotateDegrees + "deg", rightX + 52, rotY + 15, 0xF2F7FF);
        drawPanelFrame(g, rightX + 84, rotY + 10, 20, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "+", rightX + 94, rotY + 15, 0xFFFFFF);

        if (panelH >= QUICK_BUILD_PANEL_H - 20) {
            String materialCost = text("screen.rtsbuilding.quick_build.materials", currentShapeCostText());
            g.drawString(this.font, materialCost, x + 8, y + QUICK_BUILD_PANEL_H - 34, 0xB8FFB8);
            g.drawString(this.font, "Selection persists automatically", x + 8, y + QUICK_BUILD_PANEL_H - 18, 0xAFC0D3);
        }
    }

    private void drawShapeTexture(GuiGraphics g, ClientRtsController.BuildShape shape, String state, int x, int y) {
        ResourceLocation texture = switch (shape) {
            case BLOCK -> "active".equals(state) ? SHAPE_BLOCK_ACTIVE : ("hover".equals(state) ? SHAPE_BLOCK_HOVER : SHAPE_BLOCK_INACTIVE);
            case LINE -> "active".equals(state) ? SHAPE_LINE_ACTIVE : ("hover".equals(state) ? SHAPE_LINE_HOVER : SHAPE_LINE_INACTIVE);
            case SQUARE -> "active".equals(state) ? SHAPE_SQUARE_ACTIVE : ("hover".equals(state) ? SHAPE_SQUARE_HOVER : SHAPE_SQUARE_INACTIVE);
            case WALL -> "active".equals(state) ? SHAPE_WALL_ACTIVE : ("hover".equals(state) ? SHAPE_WALL_HOVER : SHAPE_WALL_INACTIVE);
            case CIRCLE -> "active".equals(state) ? SHAPE_CIRCLE_ACTIVE : ("hover".equals(state) ? SHAPE_CIRCLE_HOVER : SHAPE_CIRCLE_INACTIVE);
            case BOX -> "active".equals(state) ? SHAPE_BOX_ACTIVE : ("hover".equals(state) ? SHAPE_BOX_HOVER : SHAPE_BOX_INACTIVE);
        };
        g.blit(texture, x + 2, y + 2, 0, 0, 28, 28, 32, 32);
    }

    private boolean handleQuickBuildPanelClick(double mouseX, double mouseY) {
        QuickBuildPanelLayout layout = resolveQuickBuildPanelLayout();
        if (layout == null || !layout.contains(mouseX, mouseY)) {
            return false;
        }
        int x = layout.x();
        int y = layout.y();
        ClientRtsController.BuildShape[] shapes = new ClientRtsController.BuildShape[] {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int slotX = x + 8 + (col * (QUICK_BUILD_SHAPE_SLOT + QUICK_BUILD_SHAPE_GAP));
            int slotY = y + 40 + (row * (QUICK_BUILD_SHAPE_SLOT + 6));
            if (inside(mouseX, mouseY, slotX, slotY, QUICK_BUILD_SHAPE_SLOT, QUICK_BUILD_SHAPE_SLOT)) {
                this.controller.setBuildShape(shapes[i]);
                ensureFillModeForShape(shapes[i]);
                clearShapeBuildSession();
                persistUiState();
                return true;
            }
        }

        int rightX = x + 88;
        List<ShapeFillMode> modes = availableFillModes(this.controller.getBuildShape());
        for (int i = 0; i < modes.size(); i++) {
            int rowY = y + 42 + (i * 20);
            if (inside(mouseX, mouseY, rightX, rowY, 84, 16)) {
                this.shapeFillMode = modes.get(i);
                persistUiState();
                return true;
            }
        }

        int rotY = y + 120;
        if (inside(mouseX, mouseY, rightX, rotY + 10, 20, 18)) {
            rotateShapeByStep(-1);
            return true;
        }
        if (inside(mouseX, mouseY, rightX + 84, rotY + 10, 20, 18)) {
            rotateShapeByStep(1);
            return true;
        }
        return true;
    }

    private QuickBuildPanelLayout resolveQuickBuildPanelLayout() {
        if (!this.quickBuildOpen || !hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            return null;
        }
        int y = TOP_H + 10;
        int availableH = getFloatingPanelAvailableHeight(y);
        if (availableH < QUICK_BUILD_PANEL_MIN_H) {
            return null;
        }
        int panelH = Math.min(QUICK_BUILD_PANEL_H, availableH);
        int maxX = Math.max(4, this.width - QUICK_BUILD_PANEL_W - 4);
        int x = Mth.clamp(this.width - QUICK_BUILD_PANEL_W - 10, 4, maxX);
        return new QuickBuildPanelLayout(x, y, QUICK_BUILD_PANEL_W, panelH);
    }

    private void renderUltiminePanel(GuiGraphics g, int mouseX, int mouseY) {
        if (!this.ultimineOpen || !hasProgressionNode(RtsProgressionNodes.ULTIMINE)) {
            return;
        }
        int x = ultiminePanelX();
        int y = ultiminePanelY();
        if (getFloatingPanelAvailableHeight(y) < ULTIMINE_PANEL_H) {
            return;
        }

        drawPanelFrame(g, x, y, ULTIMINE_PANEL_W, ULTIMINE_PANEL_H, 0xEE161C24, 0xFF6C839A, 0xFF0D1117);
        g.fill(x + 1, y + 1, x + ULTIMINE_PANEL_W - 1, y + 20, 0xCC233345);
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.ultimine.title"), x + 8, y + 6, 0xF2F7FF);

        int rowY = y + 32;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.ultimine.blocks"), x + 8, rowY, 0xD8E3EE);
        drawPanelFrame(g, x + 8, rowY + 12, 24, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "-", x + 20, rowY + 17, 0xFFFFFF);
        drawPanelFrame(g, x + 38, rowY + 12, 58, 18, 0xAA243547, 0xFF647B92, 0xFF0D1117);
        if (this.ultimineLimitEditing) {
            g.fill(x + 40, rowY + 14, x + 94, rowY + 28, 0x552D82C8);
        }
        String limitText = this.ultimineLimitEditing ? this.ultimineLimitDraft : Integer.toString(this.ultimineLimit);
        if (limitText.isEmpty()) {
            limitText = "_";
        }
        g.drawCenteredString(this.font, limitText, x + 67, rowY + 17, this.ultimineLimitEditing ? 0xFFFFFF : 0xF2F7FF);
        drawPanelFrame(g, x + 102, rowY + 12, 24, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "+", x + 114, rowY + 17, 0xFFFFFF);
        drawPanelFrame(g, x + 132, rowY + 12, 42, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "MIN", x + 153, rowY + 17, 0xFFFFFF);
        drawPanelFrame(g, x + 180, rowY + 12, 48, 18, 0xAA1C232D, 0xFF647B92, 0xFF0D1117);
        g.drawCenteredString(this.font, "MAX", x + 204, rowY + 17, 0xFFFFFF);

        int stage = this.controller.getMineProgressStage();
        int progressY = y + 82;
        String progressLabel = stage >= 0
                ? text("screen.rtsbuilding.ultimine.breaking", Math.max(1, this.lastUltimineSentLimit))
                : text("screen.rtsbuilding.ultimine.ready");
        g.drawString(this.font, progressLabel, x + 8, progressY - 12, stage >= 0 ? 0xB8FFB8 : 0xAFC0D3);
        drawPanelFrame(g, x + 8, progressY, ULTIMINE_PANEL_W - 16, 12, 0xAA101820, 0xFF647B92, 0xFF0D1117);
        int fillW = stage < 0 ? 0 : Math.min(ULTIMINE_PANEL_W - 20, Math.max(1, (int) (((stage + 1) / 10.0F) * (ULTIMINE_PANEL_W - 20))));
        if (fillW > 0) {
            g.fill(x + 10, progressY + 2, x + 10 + fillW, progressY + 10, 0xFF78B28C);
        }
    }

    private boolean handleUltiminePanelClick(double mouseX, double mouseY) {
        if (!this.ultimineOpen || !hasProgressionNode(RtsProgressionNodes.ULTIMINE)) {
            return false;
        }
        int x = ultiminePanelX();
        int y = ultiminePanelY();
        if (getFloatingPanelAvailableHeight(y) < ULTIMINE_PANEL_H || !inside(mouseX, mouseY, x, y, ULTIMINE_PANEL_W, ULTIMINE_PANEL_H)) {
            return false;
        }

        int rowY = y + 32;
        if (inside(mouseX, mouseY, x + 8, rowY + 12, 24, 18)) {
            adjustUltimineLimit(hasShiftDown() ? -16 : -1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 102, rowY + 12, 24, 18)) {
            adjustUltimineLimit(hasShiftDown() ? 16 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, x + 38, rowY + 12, 58, 18)) {
            beginUltimineLimitEdit();
            return true;
        }
        if (inside(mouseX, mouseY, x + 132, rowY + 12, 42, 18)) {
            this.ultimineLimit = ULTIMINE_MIN_LIMIT;
            cancelUltimineLimitEdit();
            persistUiState();
            return true;
        }
        if (inside(mouseX, mouseY, x + 180, rowY + 12, 48, 18)) {
            this.ultimineLimit = ULTIMINE_MAX_LIMIT;
            cancelUltimineLimitEdit();
            persistUiState();
            return true;
        }
        return true;
    }

    private void adjustUltimineLimit(int delta) {
        this.ultimineLimit = clampUltimineLimit(this.ultimineLimit + delta);
        cancelUltimineLimitEdit();
        persistUiState();
    }

    private int ultiminePanelX() {
        return this.width - ULTIMINE_PANEL_W - 10;
    }

    private int ultiminePanelY() {
        QuickBuildPanelLayout quickBuildLayout = resolveQuickBuildPanelLayout();
        return TOP_H + 10 + (quickBuildLayout == null ? 0 : quickBuildLayout.h() + 8);
    }

    private boolean isInsideUltimineLimitInput(double mouseX, double mouseY) {
        if (!this.ultimineOpen || !hasProgressionNode(RtsProgressionNodes.ULTIMINE)) {
            return false;
        }
        int x = ultiminePanelX();
        int y = ultiminePanelY();
        if (getFloatingPanelAvailableHeight(y) < ULTIMINE_PANEL_H) {
            return false;
        }
        int rowY = y + 32;
        return inside(mouseX, mouseY, x + 38, rowY + 12, 58, 18);
    }

    private void beginUltimineLimitEdit() {
        this.ultimineLimitDraft = Integer.toString(this.ultimineLimit);
        this.ultimineLimitEditing = true;
        this.ultimineLimitSelectAll = true;
        blurSearchFocus();
    }

    private void commitUltimineLimitEdit() {
        if (!this.ultimineLimitEditing) {
            return;
        }
        try {
            if (!this.ultimineLimitDraft.isBlank()) {
                this.ultimineLimit = clampUltimineLimit(Integer.parseInt(this.ultimineLimitDraft));
            }
        } catch (NumberFormatException ignored) {
        }
        cancelUltimineLimitEdit();
        persistUiState();
    }

    private void cancelUltimineLimitEdit() {
        this.ultimineLimitEditing = false;
        this.ultimineLimitSelectAll = false;
        this.ultimineLimitDraft = "";
    }

    private boolean handleUltimineLimitKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitUltimineLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelUltimineLimitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.ultimineLimitSelectAll) {
                this.ultimineLimitDraft = "";
                this.ultimineLimitSelectAll = false;
            } else if (!this.ultimineLimitDraft.isEmpty()) {
                this.ultimineLimitDraft = this.ultimineLimitDraft.substring(0, this.ultimineLimitDraft.length() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            this.ultimineLimitDraft = "";
            this.ultimineLimitSelectAll = false;
            return true;
        }
        return true;
    }

    private boolean handleUltimineLimitCharTyped(char codePoint) {
        if (!Character.isDigit(codePoint)) {
            return true;
        }
        if (this.ultimineLimitSelectAll) {
            this.ultimineLimitDraft = "";
            this.ultimineLimitSelectAll = false;
        }
        if (this.ultimineLimitDraft.length() < 3) {
            this.ultimineLimitDraft += codePoint;
        }
        return true;
    }

    private int clampUltimineLimit(int value) {
        return Math.max(ULTIMINE_MIN_LIMIT, Math.min(ULTIMINE_MAX_LIMIT, value));
    }

    private void renderShapeContextPanel(GuiGraphics g, int mouseX, int mouseY) {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return;
        }
        ensureFillModeForShape(shape);

        int panelX = this.width - SHAPE_CONTEXT_PANEL_W - SHAPE_CONTEXT_PANEL_X_MARGIN;
        int panelY = SHAPE_CONTEXT_PANEL_Y;
        int panelW = SHAPE_CONTEXT_PANEL_W;
        int panelH = 122;
        if (getFloatingPanelAvailableHeight(panelY) < panelH) {
            return;
        }

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA111820);
        g.hLine(panelX, panelX + panelW, panelY, 0xFF5B7085);
        g.hLine(panelX, panelX + panelW, panelY + panelH, 0xFF0C0F13);
        g.vLine(panelX, panelY, panelY + panelH, 0xFF5B7085);
        g.vLine(panelX + panelW, panelY, panelY + panelH, 0xFF0C0F13);

        g.drawString(this.font, "Shape Context", panelX + 8, panelY + 6, 0xEAF5FF);
        g.drawString(this.font, shapeDimensionLabel(shape) + " Shape", panelX + 8, panelY + 18, 0xA9C7E8);
        g.drawString(this.font, "Fill Attribute", panelX + 8, panelY + 30, 0xA9C7E8);

        int rowY = panelY + 42;
        List<ShapeFillMode> modes = availableFillModes(shape);
        for (ShapeFillMode mode : modes) {
            boolean selected = mode == this.shapeFillMode;
            boolean hover = inside(mouseX, mouseY, panelX + 8, rowY, panelW - 16, SHAPE_CONTEXT_ROW_H);
            int bg = selected ? 0xAA2E6A50 : (hover ? 0x88334A5F : 0x66303A45);
            g.fill(panelX + 8, rowY, panelX + panelW - 8, rowY + SHAPE_CONTEXT_ROW_H, bg);
            g.drawString(this.font, fillModeLabel(mode), panelX + 12, rowY + 3, selected ? 0xFFFFFF : 0xDCE7F3);
            rowY += SHAPE_CONTEXT_ROW_H + 3;
        }

        g.drawString(this.font, "Size: " + currentShapeSizeText(), panelX + 8, panelY + panelH - 48, 0xB8FFB8);
        g.drawString(this.font, "Cost: " + currentShapeCostText() + " blocks", panelX + 8, panelY + panelH - 36, 0xB8FFB8);
        g.drawString(this.font, "ALT Hold: Shape Radial", panelX + 8, panelY + panelH - 24, 0xB7CDE2);
        g.drawString(this.font, "Ctrl+Z / Ctrl+Y", panelX + 8, panelY + panelH - 13, 0xB7CDE2);

        int leftX = SHAPE_CONTEXT_PANEL_X_MARGIN;
        int leftW = 180;
        int leftH = 66;
        g.fill(leftX, panelY, leftX + leftW, panelY + leftH, 0x99101822);
        g.hLine(leftX, leftX + leftW, panelY, 0xFF4E6075);
        g.hLine(leftX, leftX + leftW, panelY + leftH, 0xFF0C0F13);
        g.vLine(leftX, panelY, panelY + leftH, 0xFF4E6075);
        g.vLine(leftX + leftW, panelY, panelY + leftH, 0xFF0C0F13);
        g.drawString(this.font, "Build Flow", leftX + 8, panelY + 6, 0xEAF5FF);
        g.drawString(this.font, "A -> B -> (C for Cube) -> Confirm", leftX + 8, panelY + 20, 0xC7D8EA);
        g.drawString(this.font, pendingShapeStatusText(), leftX + 8, panelY + 34, 0xB8FFB8);
        g.drawString(this.font, "Rot/Fill updates preview live", leftX + 8, panelY + 48, 0xB7CDE2);
    }

    private boolean handleShapeContextPanelClick(double mouseX, double mouseY) {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return false;
        }
        ensureFillModeForShape(shape);
        int panelX = this.width - SHAPE_CONTEXT_PANEL_W - SHAPE_CONTEXT_PANEL_X_MARGIN;
        int panelY = SHAPE_CONTEXT_PANEL_Y;
        int panelW = SHAPE_CONTEXT_PANEL_W;
        int panelH = 122;
        if (getFloatingPanelAvailableHeight(panelY) < panelH) {
            return false;
        }
        if (!inside(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            return false;
        }

        int rowY = panelY + 42;
        List<ShapeFillMode> modes = availableFillModes(shape);
        for (ShapeFillMode mode : modes) {
            if (inside(mouseX, mouseY, panelX + 8, rowY, panelW - 16, SHAPE_CONTEXT_ROW_H)) {
                this.shapeFillMode = mode;
                return true;
            }
            rowY += SHAPE_CONTEXT_ROW_H + 3;
        }
        return true;
    }

    private void renderBottomPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BottomPanelLayout layout = resolveBottomPanelLayout();
        int bottomH = layout.panelH();
        int bottomY = layout.panelY();
        int sortX = layout.sortX();
        int sortY = layout.sortY();

        drawPanelFrame(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(), 0xD014151A, 0xFF64788E, 0xFF0D1015);
        g.fill(layout.panelX() + 1, layout.panelY() + 1, layout.panelX() + layout.panelW() - 1, layout.panelY() + BOTTOM_PANEL_HEADER_H, 0xCC1C242F);
        renderBottomPanelTabs(g, layout, mouseX, mouseY);
        int refreshX = bottomRefreshButtonX(layout);
        int refreshY = bottomGuideButtonY(layout);
        boolean refreshHover = inside(mouseX, mouseY, refreshX, refreshY, 12, 12);
        int refreshBg = this.controller.isStorageScanRunning()
                ? 0xCC3F627E
                : refreshHover ? 0xCC41576F : 0xAA2B3542;
        g.fill(refreshX, refreshY, refreshX + 12, refreshY + 12, refreshBg);
        g.drawCenteredString(this.font, "R", refreshX + 6, refreshY + 2, 0xEAF4FF);
        int guideX = bottomGuideButtonX(layout);
        int guideY = bottomGuideButtonY(layout);
        boolean guideHover = inside(mouseX, mouseY, guideX, guideY, 12, 12);
        g.fill(guideX, guideY, guideX + 12, guideY + 12, guideHover ? 0xCC41576F : 0xAA2B3542);
        g.drawCenteredString(this.font, "i", guideX + 6, guideY + 2, 0xEAF4FF);

        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
            int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
            int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
            int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
            int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
            BlueprintPanel.render(g, this.font, this.controller, contentX, contentY, contentW, contentH, mouseX, mouseY);
            return;
        }

        drawSortButton(g, sortX, sortY, "S");
        drawSortButton(g, sortX, sortY + SORT_BUTTON_SIZE + 4, this.controller.isStorageSortAscending() ? "A" : "D");
        g.drawString(this.font, sortLabel(this.controller.getStorageSort()), sortX + SORT_BUTTON_SIZE + 4, sortY + 6, 0xFFFFFF);
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY, "+");
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY + SORT_BUTTON_SIZE + 4, "-");
        drawCraftDock(g, mouseX, mouseY, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2);

        int categoryX = layout.categoryX();
        int categoryY = layout.categoryY();
        int categoryH = layout.categoryH();
        drawCategoryPanel(g, mouseX, mouseY, categoryX, categoryY, CATEGORY_W, categoryH);

        int storageX = layout.storageX();
        int storageY = layout.storageY();
        int storageW = layout.storageW();
        int craftPanelX = layout.craftPanelX();
        int mainStorageW = layout.mainStorageW();
        int searchW = layout.searchW();
        int searchFieldW = computeSearchFieldWidth(searchW);

        if (this.searchBox != null) {
            this.searchBox.setX(storageX);
            this.searchBox.setY(storageY);
            this.searchBox.setWidth(searchFieldW);
            this.searchBox.setHeight(14);
            this.searchBox.render(g, mouseX, mouseY, partialTick);
            drawSearchClearButton(g, storageX, storageY, searchW);
        }

        int pagerX = layout.pagerX();
        drawPager(g, pagerX, storageY);

        int toolY = layout.toolY();
        renderToolArea(g, mouseX, mouseY, storageX, toolY, mainStorageW);

        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int storageRows = layout.storageRows();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();
        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = storageX;
        int itemGridW = mainStorageW;
        if (fluidW > 0) {
            drawFluidGrid(g, mouseX, mouseY, storageX, gridY, fluidW, gridH);
            itemGridX = storageX + fluidW + 4;
            itemGridW = Math.max(SLOT, mainStorageW - fluidW - 4);
        }
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);
        int recentGridX = itemGridX + storageGridW + STORAGE_RECENT_GAP;
        int recentGridW = Math.max(SLOT, itemGridW - storageGridW - STORAGE_RECENT_GAP);
        drawStorageGrid(g, mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        drawRecentGrid(g, mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
        renderCraftablesPanel(g, mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH, partialTick);
    }

    private void renderBottomPanelTabs(GuiGraphics g, BottomPanelLayout layout, int mouseX, int mouseY) {
        int labelX = layout.panelX() + 8;
        int labelY = layout.panelY() + 5;
        g.drawString(this.font, "RTS", labelX, labelY, 0xF2F6FB);
        drawBottomPanelTab(
                g,
                layout,
                BottomPanelTab.STORAGE,
                Component.translatable("screen.rtsbuilding.storage.tab").getString(),
                mouseX,
                mouseY);
        drawBottomPanelTab(
                g,
                layout,
                BottomPanelTab.BLUEPRINTS,
                Component.translatable("screen.rtsbuilding.blueprints.tab").getString(),
                mouseX,
                mouseY);
    }

    private void drawBottomPanelTab(
            GuiGraphics g,
            BottomPanelLayout layout,
            BottomPanelTab tab,
            String label,
            int mouseX,
            int mouseY) {
        int x = bottomPanelTabX(layout, tab);
        int y = layout.panelY() + 2;
        int w = bottomPanelTabW(tab);
        boolean active = this.bottomPanelTab == tab;
        boolean hover = inside(mouseX, mouseY, x, y, w, BOTTOM_PANEL_HEADER_H - 3);
        int fill = active ? 0xCC355B4C : hover ? 0xAA334052 : 0x8826303B;
        drawPanelFrame(g, x, y, w, BOTTOM_PANEL_HEADER_H - 3, fill, active ? 0xFF7CCB93 : 0xFF536679, 0xFF0D1015);
        g.drawCenteredString(this.font, trimToWidth(label, w - 8), x + w / 2, y + 4, active ? 0xFFFFFFFF : 0xFFD8E2EE);
    }

    private int bottomPanelTabX(BottomPanelLayout layout, BottomPanelTab tab) {
        int storageX = layout.panelX() + 38;
        if (tab == BottomPanelTab.STORAGE) {
            return storageX;
        }
        return storageX + bottomPanelTabW(BottomPanelTab.STORAGE) + 4;
    }

    private int bottomPanelTabW(BottomPanelTab tab) {
        return tab == BottomPanelTab.STORAGE ? 76 : 86;
    }

    private BottomPanelTab resolveBottomPanelTabClick(BottomPanelLayout layout, double mouseX, double mouseY) {
        for (BottomPanelTab tab : BottomPanelTab.values()) {
            if (inside(mouseX, mouseY, bottomPanelTabX(layout, tab), layout.panelY() + 2, bottomPanelTabW(tab), BOTTOM_PANEL_HEADER_H - 3)) {
                return tab;
            }
        }
        return null;
    }

    private void renderToolArea(GuiGraphics g, int mouseX, int mouseY, int storageX, int rowY, int storageW) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        int selectedToolSlot = getSelectedToolSlot();
        int selected = (this.controller.hasSelectedItem()
                || this.controller.hasSelectedFluid()
                || this.controller.isEmptyHandSelected()
                || selectedToolSlot >= TOOL_HOTBAR_ITEM_SLOTS) ? -1 : selectedToolSlot;

        for (int i = 0; i < 9; i++) {
            int cx = hotbarX + i * HOTBAR_PITCH;
            int cy = rowY;
            boolean emptyHandButton = i == EMPTY_HAND_BUTTON_INDEX;
            int bg = emptyHandButton
                    ? (this.controller.isEmptyHandSelected() ? 0xCC9B604B : 0xB06F5146)
                    : (i == selected ? 0xCC3A6E57 : 0xAA1B1E25);
            g.fill(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            g.hLine(cx, cx + HOTBAR_SLOT, cy, emptyHandButton ? 0xFFFFD0B0 : 0xFF5E6874);
            g.hLine(cx, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            g.vLine(cx, cy, cy + HOTBAR_SLOT, emptyHandButton ? 0xFFFFD0B0 : 0xFF5E6874);
            g.vLine(cx + HOTBAR_SLOT, cy, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (emptyHandButton) {
                drawEmptyHandButton(g, cx, cy);
            } else {
                ItemStack stack = this.minecraft.player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, cx + 1, cy + 1);
                    g.renderItemDecorations(this.font, stack, cx + 1, cy + 1);
                }
            }
            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                if (emptyHandButton) {
                    this.hoveredEmptyHandSlot = true;
                } else {
                    this.hoveredToolSlot = i;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0) {
            return;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);
        int pinStartIndex = this.pinPage * slotsPerPage;

        for (int cell = 0; cell < pinVisibleCells; cell++) {
            int cx = pinStartX + cell * HOTBAR_PITCH;
            int cy = rowY;
            boolean pageButton = usePager && cell == pinVisibleCells - 1;
            int pinIndex = pinStartIndex + cell;
            boolean filled = !pageButton
                    && pinIndex < totalPins
                    && !this.controller.getQuickSlotItemId(pinIndex).isBlank();
            int bg = filled ? 0xAA253043 : 0xAA1A1A1A;
            g.fill(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            g.hLine(cx, cx + HOTBAR_SLOT, cy, 0xFF67758A);
            g.hLine(cx, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            g.vLine(cx, cy, cy + HOTBAR_SLOT, 0xFF67758A);
            g.vLine(cx + HOTBAR_SLOT, cy, cy + HOTBAR_SLOT, 0xFF0C0D10);

            if (pageButton) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0xAA2C3A26);
                g.drawCenteredString(this.font, "+", cx + HOTBAR_SLOT / 2, cy + 5, 0xE9F7DA);
            } else if (pinIndex < totalPins) {
                ItemStack preview = this.controller.getQuickSlotPreview(pinIndex);
                if (!preview.isEmpty()) {
                    g.renderItem(preview, cx + 1, cy + 1);
                    if (this.controller.getQuickSlotItemId(pinIndex).equals(this.controller.getSelectedItemId())) {
                        g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x3340FF80);
                    }
                    long count = resolvePinnedItemCount(this.controller.getQuickSlotItemId(pinIndex));
                    drawSlotCountOverlay(
                            g,
                            cx,
                            cy,
                            HOTBAR_SLOT,
                            compactCount(count),
                            count > 0 ? 0xFFF7E6A8 : 0xFFB4B9C3);
                } else {
                    g.drawCenteredString(this.font, Integer.toString(pinIndex + 1), cx + HOTBAR_SLOT / 2, cy + 5, 0x88D0D8E4);
                }
            }
            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                if (pageButton) {
                    this.hoveredPinPageButton = true;
                } else if (pinIndex < totalPins) {
                    this.hoveredPinIndex = pinIndex;
                }
            }
        }
    }

    private void drawEmptyHandButton(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y + 2, x + HOTBAR_SLOT - 2, y + HOTBAR_SLOT - 2, 0xDDC66A3D);
        g.fill(x + 3, y + 3, x + HOTBAR_SLOT - 3, y + 5, 0x33FFFFFF);
        g.drawCenteredString(this.font,
                trimToWidth(text("screen.rtsbuilding.empty_hand.button"), HOTBAR_SLOT - 4),
                x + HOTBAR_SLOT / 2,
                y + 5,
                0xFFFFE4C7);
    }

    private void renderBottomHoverInfoStrip(GuiGraphics g) {
        BottomHoverInfo info = resolveBottomHoverInfo();
        if (info == null || info.label().isBlank()) {
            return;
        }

        BottomPanelLayout layout = resolveBottomPanelLayout();
        int x = layout.panelX() + 8;
        int y = Math.max(TOP_H + 2, layout.panelY() - 15);
        int maxW = Math.max(120, layout.panelW() - 16);
        String message = info.detail() == null || info.detail().isBlank()
                ? info.label()
                : info.label() + "  " + info.detail();
        int w = Math.min(maxW, Math.max(120, this.font.width(message) + 14));
        drawPanelFrame(g, x, y, w, 14, 0xC8141A22, 0xFF5F7185, 0xFF0D1118);
        g.drawString(this.font, trimToWidth(message, w - 10), x + 5, y + 3, info.color(), false);
    }

    private BottomHoverInfo resolveBottomHoverInfo() {
        if (this.hoveredEmptyHandSlot) {
            return new BottomHoverInfo(text("screen.rtsbuilding.tooltip.empty_hand"), "", 0xFFFFC38A);
        }
        if (this.minecraft != null && this.minecraft.player != null && this.hoveredToolSlot >= 0) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(this.hoveredToolSlot);
            return hoverInfoFromStack(stack, countDetail(stack.getCount()), 0xFFEAF2FF);
        }
        if (this.hoveredEntry >= 0 && this.hoveredEntry < this.controller.getStorageEntries().size()) {
            ClientRtsController.StorageEntry entry = this.controller.getStorageEntries().get(this.hoveredEntry);
            return hoverInfoFromStack(entry.stack(), countDetail(entry.count()), 0xFFEAF2FF);
        }
        if (this.hoveredRecentEntry >= 0 && this.hoveredRecentEntry < this.controller.getRecentEntries().size()) {
            ClientRtsController.RecentEntry entry = this.controller.getRecentEntries().get(this.hoveredRecentEntry);
            String label = !entry.preview().isEmpty() ? entry.preview().getHoverName().getString() : entry.label();
            return new BottomHoverInfo(label, recentDetail(entry), entry.fluid() ? 0xFFBEE6FF : 0xFFE8F4C0);
        }
        if (this.hoveredFluidEntry >= 0 && this.hoveredFluidEntry < this.controller.getFluidEntries().size()) {
            ClientRtsController.FluidEntry fluid = this.controller.getFluidEntries().get(this.hoveredFluidEntry);
            String label = !fluid.preview().isEmpty() ? fluid.preview().getHoverName().getString() : fluid.label();
            return new BottomHoverInfo(label, compactFluidAmount(fluid.amount()), 0xFFFCCB8A);
        }
        if (this.hoveredCraftableEntry >= 0 && this.hoveredCraftableEntry < this.controller.getCraftableEntries().size()) {
            ClientRtsController.CraftableEntry entry = this.controller.getCraftableEntries().get(this.hoveredCraftableEntry);
            String detail = entry.craftable() ? text("screen.rtsbuilding.tooltip.craft_choose") : entry.missingSummary();
            return hoverInfoFromStack(entry.stack(), detail, entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
        }
        if (this.hoveredFunnelBufferEntry >= 0 && this.hoveredFunnelBufferEntry < this.controller.getFunnelBufferEntries().size()) {
            ClientRtsController.FunnelBufferEntry entry = this.controller.getFunnelBufferEntries().get(this.hoveredFunnelBufferEntry);
            return hoverInfoFromStack(entry.stack(), text("screen.rtsbuilding.tooltip.buffered", entry.count()), 0xFFD8B8);
        }
        if (this.hoveredPinIndex >= 0 && this.hoveredPinIndex < this.controller.getQuickSlotCount()) {
            ItemStack preview = this.controller.getQuickSlotPreview(this.hoveredPinIndex);
            String itemId = this.controller.getQuickSlotItemId(this.hoveredPinIndex);
            String detail = itemId == null || itemId.isBlank() ? "" : countDetail(resolvePinnedItemCount(itemId));
            return hoverInfoFromStack(preview, detail, 0xFFEAF2FF);
        }
        return null;
    }

    private BottomHoverInfo hoverInfoFromStack(ItemStack stack, String detail, int color) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return new BottomHoverInfo(stack.getHoverName().getString(), detail, color);
    }

    private String recentDetail(ClientRtsController.RecentEntry entry) {
        String amount = formatRecentAmount(entry);
        return entry.fluid() ? amount : "x" + amount;
    }

    private static String countDetail(long count) {
        return count > 0 ? "x" + compactCount(count) : "";
    }

    private void drawSortButton(GuiGraphics g, int x, int y, String label) {
        g.fill(x, y, x + SORT_BUTTON_SIZE, y + SORT_BUTTON_SIZE, 0xAA29323D);
        g.drawCenteredString(this.font, label, x + SORT_BUTTON_SIZE / 2, y + 4, 0xFFFFFF);
    }

    private void drawCategoryPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0x8820222A);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.storage.category"), x + width / 2, y + 2, 0xFFFFFF);

        int upX0 = x + width - 24;
        int upX1 = x + width - 13;
        int downX0 = x + width - 12;
        int downX1 = x + width - 2;
        int arrowY0 = y + 1;
        int arrowY1 = y + 11;
        g.fill(upX0, arrowY0, upX1, arrowY1, 0xAA2A2A2A);
        g.fill(downX0, arrowY0, downX1, arrowY1, 0xAA2A2A2A);
        g.drawCenteredString(this.font, "^", upX0 + 5, y + 2, 0xFFFFFF);
        g.drawCenteredString(this.font, "v", downX0 + 5, y + 2, 0xFFFFFF);

        int listY = y + 13;
        int listH = height - 15;
        int visible = Math.max(1, listH / CATEGORY_ROW_H);
        List<CategoryRow> rows = buildCategoryRows();
        int maxScroll = Math.max(0, rows.size() - visible);
        this.categoryScroll = Mth.clamp(this.categoryScroll, 0, maxScroll);

        for (int row = 0; row < visible; row++) {
            int index = this.categoryScroll + row;
            if (index >= rows.size()) {
                break;
            }
            CategoryRow category = rows.get(index);
            int rowY = listY + row * CATEGORY_ROW_H;
            boolean selected = category.token().equals(this.controller.getStorageCategory());
            int bg = selected ? 0xFF335E4C : 0x66343A47;
            g.fill(x + 2, rowY, x + width - 2, rowY + CATEGORY_ROW_H - 2, bg);
            int textColor = selected ? 0xFFFFFF : 0xE0E0E0;
            int labelX = x + 6 + (category.depth() * 10);
            int labelRight = x + width - 6;

            if (category.expandable()) {
                int toggleX = x + width - 12;
                int toggleY = rowY + 1;
                g.fill(toggleX, toggleY, toggleX + 9, toggleY + CATEGORY_ROW_H - 3, 0xAA2A313B);
                g.drawCenteredString(this.font, category.expanded() ? "-" : "+", toggleX + 4, rowY + 3, 0xFFFFFF);
                labelRight = toggleX - 3;
            }

            int availableWidth = Math.max(8, labelRight - labelX);
            int maxLabelWidth = Math.max(8, (int) Math.floor(availableWidth / CATEGORY_TEXT_SCALE));
            String label = trimToWidth(category.label(), maxLabelWidth);
            int scaledTextWidth = (int) Math.ceil(this.font.width(label) * CATEGORY_TEXT_SCALE);
            int centeredX = labelX + Math.max(0, (availableWidth - scaledTextWidth) / 2);
            int textAreaHeight = CATEGORY_ROW_H - 2;
            int scaledTextHeight = Math.max(1, (int) Math.ceil(this.font.lineHeight * CATEGORY_TEXT_SCALE));
            int centeredY = rowY + 1 + Math.max(0, (textAreaHeight - scaledTextHeight) / 2);
            drawScaledText(g, label, centeredX, centeredY, textColor, CATEGORY_TEXT_SCALE);
        }
    }

    private void drawPager(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 16, y + 14, 0xAA2A2A2A);
        g.drawString(this.font, "<", x + 5, y + 3, 0xFFFFFF);
        g.fill(x + 58, y, x + 74, y + 14, 0xAA2A2A2A);
        g.drawString(this.font, ">", x + 63, y + 3, 0xFFFFFF);
        g.drawString(this.font,
                (this.controller.getStoragePage() + 1) + "/" + this.controller.getStorageTotalPages(),
                x + 19, y + 3, 0xFFFFFF);
    }

    private int computeSearchFieldWidth(int searchAreaWidth) {
        return Math.max(56, searchAreaWidth - (SEARCH_CLEAR_SIZE + 2));
    }

    private int getSearchClearButtonX(int searchX, int searchAreaWidth) {
        return searchX + computeSearchFieldWidth(searchAreaWidth) + 2;
    }

    private void drawSearchClearButton(GuiGraphics g, int searchX, int searchY, int searchAreaWidth) {
        int x = getSearchClearButtonX(searchX, searchAreaWidth);
        int y = searchY + 1;
        int bg = isSearchFocused() ? 0xAA3B4755 : 0xAA2A313B;
        g.fill(x, y, x + SEARCH_CLEAR_SIZE, y + SEARCH_CLEAR_SIZE, bg);
        g.hLine(x, x + SEARCH_CLEAR_SIZE, y, 0xFF637283);
        g.hLine(x, x + SEARCH_CLEAR_SIZE, y + SEARCH_CLEAR_SIZE, 0xFF101318);
        g.vLine(x, y, y + SEARCH_CLEAR_SIZE, 0xFF637283);
        g.vLine(x + SEARCH_CLEAR_SIZE, y, y + SEARCH_CLEAR_SIZE, 0xFF101318);
        int textColor = this.searchBox != null && !this.searchBox.getValue().isEmpty() ? 0xFFFFFF : 0x99A6B5;
        g.drawCenteredString(this.font, "x", x + SEARCH_CLEAR_SIZE / 2, y + 3, textColor);
    }

    private static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        RtsClientUiUtil.drawPanelFrame(guiGraphics, x, y, w, h, fillColor, light, dark);
    }

    private void drawStorageGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        List<ClientRtsController.StorageEntry> entries = this.controller.getStorageEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            int box = SLOT - 2;
            g.fill(cx, cy, cx + box, cy + box, 0xAA111111);
            g.hLine(cx, cx + box, cy, 0xFF4A4A4A);
            g.hLine(cx, cx + box, cy + box, 0xFF1B1B1B);
            g.vLine(cx, cy, cy + box, 0xFF4A4A4A);
            g.vLine(cx + box, cy, cy + box, 0xFF1B1B1B);

            if (i < entries.size()) {
                var entry = entries.get(i);
                boolean selected = !this.controller.getSelectedItemPreview().isEmpty()
                        && ItemStack.isSameItemSameTags(entry.stack(), this.controller.getSelectedItemPreview());
                if (selected) {
                    g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3326C56D);
                }
                g.renderItem(entry.stack(), cx + 2, cy + 2);
                drawSlotCountOverlay(g, cx, cy, box, compactCount(entry.count()), 0xFFF7E6A8);

                if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                    this.hoveredEntry = i;
                    if (selected) {
                        g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3340FF80);
                    }
                }
            }
        }
    }

    private void drawRecentGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        List<ClientRtsController.RecentEntry> entries = this.controller.getRecentEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            int box = SLOT - 2;
            g.fill(cx, cy, cx + box, cy + box, 0xAA161C24);
            g.hLine(cx, cx + box, cy, 0xFF526171);
            g.hLine(cx, cx + box, cy + box, 0xFF10151B);
            g.vLine(cx, cy, cy + box, 0xFF526171);
            g.vLine(cx + box, cy, cy + box, 0xFF10151B);

            if (i >= entries.size()) {
                continue;
            }

            ClientRtsController.RecentEntry entry = entries.get(i);
            if (!entry.preview().isEmpty()) {
                g.renderItem(entry.preview(), cx + 2, cy + 2);
            }
            drawSlotCountOverlay(
                    g,
                    cx,
                    cy,
                    box,
                    formatRecentAmount(entry),
                    entry.fluid() ? 0xFFBEE6FF : 0xFFE8F4C0);

            if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x22FFFFFF);
                this.hoveredRecentEntry = i;
            }
        }
    }

    private void renderCraftablesPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, float partialTick) {
        syncCraftSearchValueFromController();

        drawPanelFrame(g, x, y, width, height, 0xAA141922, 0xFF637993, 0xFF0D1218);
        g.drawString(this.font, "Craft", x + 5, y + 4, 0xEAF2FF);

        int searchX = x + 4;
        int searchY = y + 15;
        int searchW = Math.max(24, width - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;
        int toggleY = searchY;
        boolean craftSearchDirty = hasPendingCraftSearchDraft();
        int applyBg = craftSearchDirty ? 0xAA4C6E39 : 0xAA24303A;
        int toggleBg = this.controller.isCraftablesShowUnavailable() ? 0xAA5A3D2A : 0xAA2C5A41;

        drawPanelFrame(g, searchX, searchY, searchW, CRAFT_PANEL_SEARCH_H, 0xAA1E2731, 0xFF5E738A, 0xFF111921);
        if (this.craftSearchBox != null) {
            this.craftSearchBox.setX(searchX + 2);
            this.craftSearchBox.setY(searchY + 2);
            this.craftSearchBox.setWidth(Math.max(22, searchW - 4));
            this.craftSearchBox.setHeight(8);
            this.craftSearchBox.render(g, mouseX, mouseY, partialTick);
        }

        drawPanelFrame(g, applyX, toggleY, CRAFT_PANEL_APPLY_W, CRAFT_PANEL_SEARCH_H, applyBg, 0xFF6E8799, 0xFF111821);
        g.drawCenteredString(this.font,
                "OK",
                applyX + CRAFT_PANEL_APPLY_W / 2,
                toggleY + 2,
                craftSearchDirty ? 0xFFFFFF : 0xFFB8C7D6);

        drawPanelFrame(g, toggleX, toggleY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H, toggleBg, 0xFF667D95, 0xFF111821);
        g.drawCenteredString(this.font,
                this.controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                toggleX + CRAFT_PANEL_TOGGLE_W / 2,
                toggleY + 2,
                0xFFFFFF);

        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int clampedRows = Math.max(1, (height - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<ClientRtsController.CraftableEntry> entries = this.controller.getCraftableEntries();
        int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - clampedRows);
        this.craftScroll = Mth.clamp(this.craftScroll, 0, maxScroll);
        int startIndex = this.craftScroll * CRAFT_PANEL_COLS;

        for (int row = 0; row < clampedRows; row++) {
            for (int col = 0; col < CRAFT_PANEL_COLS; col++) {
                int index = startIndex + row * CRAFT_PANEL_COLS + col;
                int slotX = x + 4 + col * CRAFT_PANEL_PITCH;
                int slotY = gridY + row * CRAFT_PANEL_PITCH;
                int fill = 0xAA1A212B;
                if (index < entries.size()) {
                    ClientRtsController.CraftableEntry entry = entries.get(index);
                    fill = entry.craftable() ? 0xAA214131 : 0xAA3F2323;
                }
                drawPanelFrame(g, slotX, slotY, CRAFT_PANEL_SLOT, CRAFT_PANEL_SLOT, fill, 0xFF596D84, 0xFF11171E);
                if (index >= entries.size()) {
                    continue;
                }

                ClientRtsController.CraftableEntry entry = entries.get(index);
                g.renderItem(entry.stack(), slotX + 1, slotY + 1);
                if (entry.resultCount() > 1) {
                    drawSlotCountOverlay(g, slotX, slotY, CRAFT_PANEL_SLOT, compactCount(entry.resultCount()), 0xFFE8F4FF);
                }
                if (!entry.craftable()) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_PANEL_SLOT - 1, slotY + CRAFT_PANEL_SLOT - 1, 0x44220000);
                }
                if (mouseX >= slotX && mouseX <= slotX + CRAFT_PANEL_SLOT && mouseY >= slotY && mouseY <= slotY + CRAFT_PANEL_SLOT) {
                    g.fill(slotX + 1, slotY + 1, slotX + CRAFT_PANEL_SLOT - 1, slotY + CRAFT_PANEL_SLOT - 1, 0x22FFFFFF);
                    this.hoveredCraftableEntry = index;
                }
            }
        }
    }

    private void syncCraftablesPanelState() {
        if (this.lastCraftablesStorageRevision != this.controller.getStorageRevision()) {
            this.lastCraftablesStorageRevision = this.controller.getStorageRevision();
            this.controller.requestCraftables();
        }
        syncCraftSearchValueFromController();
    }

    private void syncCraftSearchValueFromController() {
        if (this.craftSearchBox == null || this.craftSearchBox.isFocused()) {
            return;
        }
        String expected = this.craftSearchDraft == null ? "" : this.craftSearchDraft;
        if (expected == null) {
            expected = "";
        }
        if (!expected.equals(this.craftSearchBox.getValue())) {
            this.craftSearchBox.setValue(expected);
        }
    }

    private void openCraftQuantityDialog(ClientRtsController.CraftableEntry entry) {
        blurSearchFocus();
        RtsCraftablesUiHelper.openCraftQuantityDialog(this.craftQuantityDialog, entry);
    }

    private void submitCraftQuantityDialogIfReady() {
        RtsCraftablesUiHelper.submitPendingCraftRequest(this.craftQuantityDialog, this.controller);
    }

    private void renderCraftFeedback(GuiGraphics g) {
        RtsCraftFeedbackPopup.render(g, this.font, this.width, this.controller);
    }

    private void drawFluidGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        int cols = 2;
        int rows = Math.max(1, height / SLOT);
        int maxSlots = cols * rows;
        int box = SLOT - 2;
        List<ClientRtsController.FluidEntry> entries = this.controller.getFluidEntries();

        for (int i = 0; i < maxSlots; i++) {
            int cx = x + (i % cols) * SLOT;
            int cy = y + (i / cols) * SLOT;
            g.fill(cx, cy, cx + box, cy + box, 0xAA2E1E12);
            g.hLine(cx, cx + box, cy, 0xFFFFA553);
            g.hLine(cx, cx + box, cy + box, 0xFF23140A);
            g.vLine(cx, cy, cy + box, 0xFFFFA553);
            g.vLine(cx + box, cy, cy + box, 0xFF23140A);

            if (i < entries.size()) {
                var entry = entries.get(i);
                boolean selected = entry.fluidId().equals(this.controller.getSelectedFluidId());
                if (selected) {
                    g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3367D8FF);
                }
                if (!entry.preview().isEmpty()) {
                    g.renderItem(entry.preview(), cx + 2, cy + 2);
                }
                drawSlotCountOverlay(g, cx, cy, box, compactFluidAmount(entry.amount()), 0xFFFCCB8A);

                if (mouseX >= cx && mouseX <= cx + box && mouseY >= cy && mouseY <= cy + box) {
                    this.hoveredFluidEntry = i;
                    if (selected) {
                        g.fill(cx + 1, cy + 1, cx + box - 1, cy + box - 1, 0x3340FF80);
                    }
                }
            }
        }
    }

    private boolean handleFunnelBufferPanelClick(double mouseX, double mouseY) {
        if (this.controller.getMode() != BuilderMode.FUNNEL && this.controller.getFunnelBufferEntries().isEmpty()) {
            return false;
        }

        int toggleX = this.width - FUNNEL_BUFFER_TOGGLE_W - 8;
        int toggleY = TOP_H + 6;
        if (inside(mouseX, mouseY, toggleX, toggleY, FUNNEL_BUFFER_TOGGLE_W, FUNNEL_BUFFER_TOGGLE_H)) {
            this.funnelBufferVisible = !this.funnelBufferVisible;
            return true;
        }
        if (!this.funnelBufferVisible) {
            return false;
        }

        int panelX = this.width - FUNNEL_BUFFER_PANEL_W - 8;
        int panelY = TOP_H + 26;
        int panelH = getFloatingPanelAvailableHeight(panelY);
        if (panelH < 20) {
            return false;
        }
        return inside(mouseX, mouseY, panelX, panelY, FUNNEL_BUFFER_PANEL_W, panelH);
    }

    private void activateFunnelHotkey() {
        stopActiveMining();
        clearShapeBuildSession();
        closeInteractionWheel();
        closeShapeWheel();
        if (this.controller.getMode() != BuilderMode.FUNNEL) {
            this.modeBeforeFunnelHotkey = this.controller.getMode();
        }
        this.controller.setMode(BuilderMode.FUNNEL);
        this.controller.setFunnelEnabled(true);
    }

    private void deactivateFunnelHotkey() {
        if (this.controller.getMode() == BuilderMode.FUNNEL || this.controller.isFunnelEnabled()) {
            this.controller.setFunnelEnabled(false);
            this.controller.setMode(this.modeBeforeFunnelHotkey == BuilderMode.FUNNEL
                    ? BuilderMode.INTERACT
                    : this.modeBeforeFunnelHotkey);
        }
    }

    private void quickDropSelectedAtCursor() {
        if (this.minecraft == null || this.minecraft.gameRenderer == null || this.minecraft.getCameraEntity() == null) {
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
            net.minecraft.resources.ResourceLocation id = BuiltInRegistries.ITEM.getKey(toolStack.getItem());
            if (id == null) {
                return;
            }
            dropItemId = id.toString();
        }

        Vec3 origin = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        Vec3 dropPos = origin.add(dir.scale(3.25D));
        BlockHitResult hit = pickBlockHit(true);
        if (hit != null) {
            dropPos = Vec3.atCenterOf(hit.getBlockPos()).add(0.0D, 1.05D, 0.0D);
        }
        this.controller.quickDropSelectedItem(dropItemId, 1, dropPos);
    }

    private boolean handleTopBarClick(double mouseX, double mouseY) {
        if (mouseY < 4 || mouseY > 4 + TOP_BUTTON_H) {
            return false;
        }

        for (TopBarButtonLayout button : buildTopBarButtonLayouts()) {
            if (!inside(mouseX, mouseY, button.x(), 4, button.width(), TOP_BUTTON_H)) {
                continue;
            }
            switch (button.id()) {
                case INTERACT -> {
                    this.controller.setMode(BuilderMode.INTERACT);
                    this.controller.setFunnelEnabled(false);
                    clearShapeBuildSession();
                }
                case LINK -> {
                    this.controller.setMode(BuilderMode.LINK_STORAGE);
                    this.controller.setFunnelEnabled(false);
                    clearShapeBuildSession();
                }
                case FUNNEL -> {
                    this.controller.setMode(BuilderMode.FUNNEL);
                    this.controller.setFunnelEnabled(true);
                    clearShapeBuildSession();
                }
                case ROTATE -> {
                    this.controller.setMode(BuilderMode.ROTATE);
                    this.controller.setFunnelEnabled(false);
                    clearShapeBuildSession();
                }
                case QUICK_BUILD -> {
                    this.quickBuildOpen = !this.quickBuildOpen;
                    this.gearMenuOpen = false;
                    persistUiState();
                }
                case ULTIMINE -> {
                    this.ultimineOpen = !this.ultimineOpen;
                    this.gearMenuOpen = false;
                    persistUiState();
                }
                case QUEST_DETECT -> {
                    this.gearMenuOpen = false;
                    this.controller.detectQuestsNow();
                }
                case CHUNK_VIEW -> {
                    this.controller.setChunkCurtainVisible(!this.controller.isChunkCurtainVisible());
                    persistUiState();
                }
                case GUIDE -> {
                    if (this.guideOpen && this.guideContext == GuideContext.TOP) {
                        this.guideOpen = false;
                    } else {
                        openGuide(GuideContext.TOP, button.x() + button.width() / 2, 4 + TOP_BUTTON_H);
                    }
                    this.gearMenuOpen = false;
                }
                case DEBUG -> {
                    this.gearMenuOpen = false;
                    copyDebugSnapshotToClipboard();
                }
                case GEAR -> {
                    this.gearMenuOpen = !this.gearMenuOpen;
                    if (this.gearMenuOpen) {
                        this.gearMenuScroll = 0;
                    }
                }
                default -> {
                }
            }
            return true;
        }
        this.gearMenuOpen = false;
        return false;
    }

    private void copyDebugSnapshotToClipboard() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(buildDebugSnapshot());
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("screen.rtsbuilding.debug.copied"), true);
        }
    }

    private String buildDebugSnapshot() {
        StringBuilder out = new StringBuilder(512);
        out.append("RTSBuilding debug snapshot\n");
        out.append("screen=").append(this.width).append('x').append(this.height)
                .append(" uiScale=").append(rtsGuiScaleLabel()).append('\n');
        out.append("mode=").append(this.controller.getMode())
                .append(" topAction=").append(topActionForMode())
                .append(" quickBuild=").append(this.quickBuildOpen)
                .append(" ultimine=").append(this.ultimineOpen)
                .append(" debugButton=").append(this.debugButtonVisible)
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
                .append(" fill=").append(this.shapeFillMode)
                .append(" rotation=").append(this.shapeRotateDegrees)
                .append(" pending=").append(pendingShapeStatusText())
                .append('\n');
        out.append("allowPlacedRecovery=").append(this.controller.isAllowPlacedBlockRecovery())
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

    private void renderDiscoverabilityTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (this.guideOpen || this.interactionWheelOpen || this.shapeWheelOpen) {
            return;
        }
        if (mouseY >= 42 && mouseY <= 56) {
            g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.undo_redo_keys"), mouseX, mouseY);
            return;
        }
        for (TopBarButtonLayout button : buildTopBarButtonLayouts()) {
            if (button.id() == TopBarButtonId.QUICK_BUILD
                    && inside(mouseX, mouseY, button.x(), 4, button.width(), TOP_BUTTON_H)) {
                g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.quick_build_toggle"), mouseX, mouseY);
                return;
            }
        }
        if (this.quickBuildOpen && hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            QuickBuildPanelLayout layout = resolveQuickBuildPanelLayout();
            if (layout != null && layout.contains(mouseX, mouseY)) {
                g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.quick_build_cancel"), mouseX, mouseY);
            }
        }
    }

    private boolean handleBottomPanelClick(double mouseX, double mouseY) {
        BottomPanelLayout layout = resolveBottomPanelLayout();
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }

        BottomPanelTab clickedTab = resolveBottomPanelTabClick(layout, mouseX, mouseY);
        if (clickedTab != null) {
            this.bottomPanelTab = clickedTab;
            blurSearchFocus();
            this.gearMenuOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, bottomRefreshButtonX(layout), bottomGuideButtonY(layout), 12, 12)) {
            if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
                BlueprintPanel.reload();
            } else {
                this.controller.refreshStoragePage();
            }
            this.gearMenuOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, bottomGuideButtonX(layout), bottomGuideButtonY(layout), 12, 12)) {
            openGuide(GuideContext.BOTTOM, bottomGuideButtonX(layout) + 6, bottomGuideButtonY(layout));
            this.gearMenuOpen = false;
            return true;
        }
        if (layout.isInsideHeader(mouseX, mouseY)) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
            int contentX = layout.panelX() + BOTTOM_PANEL_PADDING;
            int contentY = layout.panelY() + BOTTOM_PANEL_HEADER_H + 4;
            int contentW = Math.max(80, layout.panelW() - BOTTOM_PANEL_PADDING * 2);
            int contentH = Math.max(24, layout.panelH() - BOTTOM_PANEL_HEADER_H - 8);
            return BlueprintPanel.mouseClicked(mouseX, mouseY, contentX, contentY, contentW, contentH);
        }

        int sortX = layout.sortX();
        int sortY = layout.sortY();
        int categoryX = layout.categoryX();
        int storageX = layout.storageX();
        int mainStorageW = layout.mainStorageW();
        int searchW = layout.searchW();
        int pagerX = layout.pagerX();
        int toolY = layout.toolY();
        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();

        if (handleSearchClearClick(mouseX, mouseY, storageX, layout.storageY(), searchW)) {
            return true;
        }

        if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            focusStorageSearchBox();
            return true;
        }

        if (handleCraftablesPanelLeftClick(mouseX, mouseY, layout.craftPanelX(), craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
            return true;
        }

        blurSearchFocus();

        if (inside(mouseX, mouseY, sortX, sortY, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            this.controller.cycleSort();
            return true;
        }
        if (inside(mouseX, mouseY, sortX, sortY + SORT_BUTTON_SIZE + 4, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            this.controller.toggleSortDirection();
            return true;
        }
        int heightBtnX = sortX + SORT_BUTTON_SIZE + 26;
        if (inside(mouseX, mouseY, heightBtnX, sortY, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            adjustBottomPanelSize(1);
            return true;
        }
        if (inside(mouseX, mouseY, heightBtnX, sortY + SORT_BUTTON_SIZE + 4, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            adjustBottomPanelSize(-1);
            return true;
        }
        if (handleCraftDockClick(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2)) {
            return true;
        }

        int categoryY = layout.categoryY();
        int upX0 = categoryX + CATEGORY_W - 24;
        int downX0 = categoryX + CATEGORY_W - 12;
        if (inside(mouseX, mouseY, upX0, categoryY + 1, 11, 10)) {
            shiftCategoryScroll(-1);
            return true;
        }
        if (inside(mouseX, mouseY, downX0, categoryY + 1, 10, 10)) {
            shiftCategoryScroll(1);
            return true;
        }

        CategoryClick categoryClick = resolveClickedCategoryAction(mouseX, mouseY);
        if (categoryClick != null) {
            if (categoryClick.toggleExpandOnly()) {
                toggleCategoryExpansion(categoryClick.modNamespace());
                return true;
            }
            this.controller.setStorageCategory(categoryClick.categoryToken());
            if (categoryClick.modNamespace() != null && !categoryClick.modNamespace().isBlank()) {
                this.expandedCategoryMods.add(categoryClick.modNamespace());
            }
            return true;
        }

        if (handleToolRowClick(mouseX, mouseY, storageX, toolY, mainStorageW)) {
            return true;
        }

        if (inside(mouseX, mouseY, pagerX, layout.storageY(), 16, 14)) {
            this.controller.prevPage();
            return true;
        }
        if (inside(mouseX, mouseY, pagerX + 58, layout.storageY(), 16, 14)) {
            this.controller.nextPage();
            return true;
        }

        int fluidW = getFluidStripWidth(mainStorageW);
        if (fluidW > 0) {
            int fluidIndex = resolveClickedFluid(mouseX, mouseY, storageX, gridY, fluidW, gridH);
            if (fluidIndex >= 0) {
                this.controller.selectFluidEntry(fluidIndex);
                return true;
            }
        }

        int itemGridX = fluidW > 0 ? storageX + fluidW + 4 : storageX;
        int itemGridW = fluidW > 0 ? Math.max(SLOT, mainStorageW - fluidW - 4) : mainStorageW;
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);
        int recentGridX = itemGridX + storageGridW + STORAGE_RECENT_GAP;
        int recentGridW = Math.max(SLOT, itemGridW - storageGridW - STORAGE_RECENT_GAP);
        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        if (entryIndex >= 0) {
            this.controller.selectStorageEntry(entryIndex);
            return true;
        }
        int recentIndex = resolveClickedRecentEntry(mouseX, mouseY, recentGridX, gridY, recentGridW, gridH);
        if (recentIndex >= 0) {
            this.controller.selectRecentEntry(recentIndex);
            return true;
        }
        return true;
    }

    private boolean handleBottomPanelRightClick(double mouseX, double mouseY) {
        BottomPanelLayout layout = resolveBottomPanelLayout();
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (layout.isInsideHeader(mouseX, mouseY)) {
            return true;
        }
        if (this.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
            return true;
        }

        int storageX = layout.storageX();
        int sortX = layout.sortX();
        int sortY = layout.sortY();
        int mainStorageW = layout.mainStorageW();
        int toolY = layout.toolY();
        int gridY = layout.gridY();
        int gridH = layout.gridH();
        int craftPanelY = layout.craftPanelY();
        int craftPanelH = layout.craftPanelH();

        if (handleCraftDockClick(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_RIGHT, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2)) {
            return true;
        }

        if (handleToolRowRightClick(mouseX, mouseY, storageX, toolY, mainStorageW)) {
            return true;
        }

        if (handleCraftablesPanelRightClick(mouseX, mouseY, layout.craftPanelX(), craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
            return true;
        }

        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = fluidW > 0 ? storageX + fluidW + 4 : storageX;
        int itemGridW = fluidW > 0 ? Math.max(SLOT, mainStorageW - fluidW - 4) : mainStorageW;
        int storageGridW = Math.max(SLOT, (itemGridW - STORAGE_RECENT_GAP) / 2);

        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, storageGridW, gridH);
        if (entryIndex >= 0 && entryIndex < this.controller.getStorageEntries().size()) {
            this.controller.storeFluidFromStorageItem(this.controller.getStorageEntries().get(entryIndex).itemId());
            return true;
        }
        return true;
    }

    private int bottomGuideButtonX(BottomPanelLayout layout) {
        return layout.panelX() + layout.panelW() - 20;
    }

    private int bottomRefreshButtonX(BottomPanelLayout layout) {
        return bottomGuideButtonX(layout) - 16;
    }

    private int bottomGuideButtonY(BottomPanelLayout layout) {
        return layout.panelY() + 3;
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY, int searchX, int searchY, int searchAreaWidth) {
        if (this.searchBox == null) {
            return false;
        }
        int clearX = getSearchClearButtonX(searchX, searchAreaWidth);
        int clearY = searchY + 1;
        if (!inside(mouseX, mouseY, clearX, clearY, SEARCH_CLEAR_SIZE, SEARCH_CLEAR_SIZE)) {
            return false;
        }
        this.searchBox.setValue("");
        this.controller.setStorageSearch("");
        blurSearchFocus();
        return true;
    }

    private void blurSearchFocus() {
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

    private void focusStorageSearchBox() {
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.setFocused(false);
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
        }
    }

    private void focusCraftSearchBox() {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        if (this.craftSearchBox != null) {
            this.craftSearchBox.setFocused(true);
            this.setFocused(this.craftSearchBox);
        }
    }

    private boolean handleToolRowClick(double mouseX, double mouseY, int storageX, int rowY, int storageW) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (!inside(mouseX, mouseY, storageX, rowY, storageW, TOOL_AREA_H)) {
            return false;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        if (inside(mouseX, mouseY, hotbarX, rowY, hotbarW, HOTBAR_SLOT)) {
            int index = (int) ((mouseX - hotbarX) / HOTBAR_PITCH);
            if (index >= 0 && index < 9) {
                int slotX = hotbarX + index * HOTBAR_PITCH;
                if (mouseX <= slotX + HOTBAR_SLOT) {
                    if (index == EMPTY_HAND_BUTTON_INDEX) {
                        this.controller.selectEmptyHand();
                        return true;
                    }
                    ItemStack stack = this.minecraft.player.getInventory().getItem(index);
                    if (hasShiftDown() && !stack.isEmpty()) {
                        this.controller.storeHotbarSlotToLinked(index);
                        return true;
                    }
                    setSelectedToolSlot(index);
                    this.controller.clearPlacementSelectionPreserveMode();
                    return true;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0 || !inside(mouseX, mouseY, pinStartX, rowY, pinVisibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) {
            return true;
        }

        int cell = (int) ((mouseX - pinStartX) / HOTBAR_PITCH);
        if (cell < 0 || cell >= pinVisibleCells) {
            return true;
        }
        int slotX = pinStartX + cell * HOTBAR_PITCH;
        if (mouseX > slotX + HOTBAR_SLOT) {
            return true;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == pinVisibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIndex = this.pinPage * slotsPerPage + cell;
        if (pinIndex < 0 || pinIndex >= totalPins) {
            return true;
        }

        if (hasShiftDown()) {
            this.controller.clearQuickSlot(pinIndex);
            return true;
        }
        this.controller.selectQuickSlot(pinIndex);
        return true;
    }

    private boolean handleToolRowRightClick(double mouseX, double mouseY, int storageX, int rowY, int storageW) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (!inside(mouseX, mouseY, storageX, rowY, storageW, TOOL_AREA_H)) {
            return false;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        if (inside(mouseX, mouseY, hotbarX, rowY, hotbarW, HOTBAR_SLOT)) {
            int index = (int) ((mouseX - hotbarX) / HOTBAR_PITCH);
            if (index >= 0 && index < 9) {
                int slotX = hotbarX + index * HOTBAR_PITCH;
                if (mouseX <= slotX + HOTBAR_SLOT) {
                    if (index == EMPTY_HAND_BUTTON_INDEX) {
                        this.controller.selectEmptyHand();
                        return true;
                    }
                    this.controller.storeFluidFromToolSlot(index);
                    return true;
                }
            }
        }

        int pinStartX = hotbarX + hotbarW + 12;
        int pinVisibleCells = computeVisiblePinCells(pinStartX, storageX + storageW);
        if (pinVisibleCells <= 0 || !inside(mouseX, mouseY, pinStartX, rowY, pinVisibleCells * HOTBAR_PITCH, HOTBAR_SLOT)) {
            return true;
        }

        int cell = (int) ((mouseX - pinStartX) / HOTBAR_PITCH);
        if (cell < 0 || cell >= pinVisibleCells) {
            return true;
        }

        int slotX = pinStartX + cell * HOTBAR_PITCH;
        if (mouseX > slotX + HOTBAR_SLOT) {
            return true;
        }

        int totalPins = this.controller.getQuickSlotCount();
        boolean usePager = shouldUsePinPager(pinVisibleCells, totalPins);
        int slotsPerPage = computePinSlotsPerPage(pinVisibleCells, totalPins);
        int pageCount = Math.max(1, (int) Math.ceil(totalPins / (double) slotsPerPage));
        this.pinPage = Mth.clamp(this.pinPage, 0, pageCount - 1);

        if (usePager && cell == pinVisibleCells - 1) {
            this.pinPage = (this.pinPage + 1) % pageCount;
            return true;
        }

        int pinIndex = this.pinPage * slotsPerPage + cell;
        if (pinIndex < 0 || pinIndex >= totalPins) {
            return true;
        }

        String itemId = this.controller.getQuickSlotItemId(pinIndex);
        if (itemId != null && !itemId.isBlank()) {
            this.controller.storeFluidFromPinnedItem(itemId);
        }
        return true;
    }

    private boolean handleCraftablesPanelLeftClick(double mouseX, double mouseY, int x, int y, int width, int height) {
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }

        int searchX = x + 4;
        int searchY = y + 15;
        int searchW = Math.max(24, width - CRAFT_PANEL_APPLY_W - CRAFT_PANEL_TOGGLE_W - 16);
        int applyX = searchX + searchW + 4;
        int toggleX = applyX + CRAFT_PANEL_APPLY_W + 4;

        if (this.craftSearchBox != null && this.craftSearchBox.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            focusCraftSearchBox();
            return true;
        }
        if (inside(mouseX, mouseY, applyX, searchY, CRAFT_PANEL_APPLY_W, CRAFT_PANEL_SEARCH_H)) {
            applyCraftSearchDraft();
            blurSearchFocus();
            return true;
        }
        if (inside(mouseX, mouseY, toggleX, searchY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H)) {
            this.controller.toggleCraftablesShowUnavailable();
            return true;
        }
        return true;
    }

    private void applyCraftSearchDraft() {
        String next = normalizeCraftSearchDraft(this.craftSearchBox == null ? this.craftSearchDraft : this.craftSearchBox.getValue());
        this.craftSearchDraft = next;
        if (this.craftSearchBox != null && !next.equals(this.craftSearchBox.getValue())) {
            this.craftSearchBox.setValue(next);
        }
        this.craftScroll = 0;
        this.controller.setCraftablesSearch(next);
    }

    private boolean hasPendingCraftSearchDraft() {
        return !normalizeCraftSearchDraft(this.craftSearchDraft).equals(normalizeCraftSearchDraft(this.controller.getCraftablesSearch()));
    }

    private static String normalizeCraftSearchDraft(String value) {
        return RtsCraftablesUiHelper.normalizeSearchDraft(value);
    }

    private boolean handleCraftablesPanelRightClick(double mouseX, double mouseY, int x, int y, int width, int height) {
        int entryIndex = resolveCraftableEntryIndex(mouseX, mouseY, x, y, width, height);
        if (entryIndex < 0 || entryIndex >= this.controller.getCraftableEntries().size()) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
        ClientRtsController.CraftableEntry entry = this.controller.getCraftableEntries().get(entryIndex);
        if (!entry.craftable()) {
            return true;
        }
        openCraftQuantityDialog(entry);
        return true;
    }

    private int resolveCraftableEntryIndex(double mouseX, double mouseY, int x, int y, int width, int height) {
        int searchY = y + 15;
        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int visibleRows = Math.max(1, (height - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<ClientRtsController.CraftableEntry> entries = this.controller.getCraftableEntries();
        int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.craftScroll = Mth.clamp(this.craftScroll, 0, maxScroll);

        if (!inside(mouseX, mouseY, x + 4, gridY, CRAFT_PANEL_COLS * CRAFT_PANEL_PITCH, visibleRows * CRAFT_PANEL_PITCH)) {
            return -1;
        }

        int col = (int) ((mouseX - (x + 4)) / CRAFT_PANEL_PITCH);
        int row = (int) ((mouseY - gridY) / CRAFT_PANEL_PITCH);
        if (col < 0 || col >= CRAFT_PANEL_COLS || row < 0 || row >= visibleRows) {
            return -1;
        }
        int slotX = x + 4 + col * CRAFT_PANEL_PITCH;
        int slotY = gridY + row * CRAFT_PANEL_PITCH;
        if (!inside(mouseX, mouseY, slotX, slotY, CRAFT_PANEL_SLOT, CRAFT_PANEL_SLOT)) {
            return -1;
        }

        int index = this.craftScroll * CRAFT_PANEL_COLS + row * CRAFT_PANEL_COLS + col;
        return index < entries.size() ? index : -1;
    }

    private long resolvePinnedItemCount(String itemId) {
        return this.controller.getStorageTotalCount(itemId);
    }

    private CategoryClick resolveClickedCategoryAction(double mouseX, double mouseY) {
        BottomPanelLayout layout = resolveBottomPanelLayout();
        int categoryX = layout.categoryX();
        int categoryY = layout.categoryY();
        int listY = categoryY + 13;
        int listH = layout.categoryH() - 15;

        if (!inside(mouseX, mouseY, categoryX + 2, listY, CATEGORY_W - 4, listH)) {
            return null;
        }

        int visible = Math.max(1, listH / CATEGORY_ROW_H);
        int row = (int) ((mouseY - listY) / CATEGORY_ROW_H);
        if (row < 0 || row >= visible) {
            return null;
        }

        List<CategoryRow> rows = buildCategoryRows();
        int index = this.categoryScroll + row;
        if (index < 0 || index >= rows.size()) {
            return null;
        }

        CategoryRow clicked = rows.get(index);
        if (clicked.expandable()) {
            int rowY = listY + row * CATEGORY_ROW_H;
            int toggleX = categoryX + CATEGORY_W - 12;
            if (inside(mouseX, mouseY, toggleX, rowY + 1, 9, CATEGORY_ROW_H - 3)) {
                return new CategoryClick(clicked.token(), clicked.modNamespace(), true);
            }
        }
        return new CategoryClick(clicked.token(), clicked.modNamespace(), false);
    }

    private List<CategoryRow> buildCategoryRows() {
        List<CategoryRow> rows = new ArrayList<>();
        rows.add(new CategoryRow(CATEGORY_ALL, "All", 0, false, false, ""));

        Map<String, Set<String>> modToTabs = new HashMap<>();
        Set<String> mods = new HashSet<>();

        for (String raw : this.controller.getStorageCategories()) {
            String category = normalizeCategoryToken(raw);
            if (category.isEmpty() || CATEGORY_ALL.equals(category)) {
                continue;
            }
            if (category.startsWith(CATEGORY_MOD_PREFIX)) {
                String mod = category.substring(CATEGORY_MOD_PREFIX.length());
                if (!mod.isBlank()) {
                    mods.add(mod);
                    modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>());
                }
                continue;
            }
            if (category.startsWith(CATEGORY_TAB_PREFIX)) {
                String payload = category.substring(CATEGORY_TAB_PREFIX.length());
                int split = payload.indexOf('|');
                if (split <= 0 || split >= payload.length() - 1) {
                    continue;
                }
                String mod = payload.substring(0, split);
                String tab = payload.substring(split + 1);
                if (mod.isBlank() || tab.isBlank()) {
                    continue;
                }
                mods.add(mod);
                modToTabs.computeIfAbsent(mod, ignored -> new HashSet<>()).add(tab);
                continue;
            }

            // Legacy category payloads used bare namespace tokens.
            mods.add(category);
            modToTabs.computeIfAbsent(category, ignored -> new HashSet<>());
        }

        String selected = normalizeCategoryToken(this.controller.getStorageCategory());
        if (selected.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = selected.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split > 0) {
                this.expandedCategoryMods.add(payload.substring(0, split));
            }
        }

        List<String> orderedMods = new ArrayList<>(mods);
        orderedMods.sort(BuilderScreen::compareNamespace);

        for (String mod : orderedMods) {
            List<String> tabs = new ArrayList<>(modToTabs.getOrDefault(mod, Set.of()));
            tabs.sort(BuilderScreen::compareTabKey);
            boolean expandable = !tabs.isEmpty();
            boolean expanded = expandable && this.expandedCategoryMods.contains(mod);
            rows.add(new CategoryRow(encodeModCategory(mod), formatModLabel(mod), 0, expandable, expanded, mod));
            if (!expanded) {
                continue;
            }
            for (String tab : tabs) {
                rows.add(new CategoryRow(encodeTabCategory(mod, tab), formatTabLabel(tab), 1, false, false, mod));
            }
        }

        return rows;
    }

    private void toggleCategoryExpansion(String modNamespace) {
        if (modNamespace == null || modNamespace.isBlank()) {
            return;
        }
        if (this.expandedCategoryMods.contains(modNamespace)) {
            this.expandedCategoryMods.remove(modNamespace);
        } else {
            this.expandedCategoryMods.add(modNamespace);
        }
    }

    private static String normalizeCategoryToken(String token) {
        if (token == null) {
            return CATEGORY_ALL;
        }
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return CATEGORY_ALL;
        }
        return value;
    }

    private static String encodeModCategory(String modNamespace) {
        return CATEGORY_MOD_PREFIX + modNamespace;
    }

    private static String encodeTabCategory(String modNamespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + modNamespace + "|" + tabKey;
    }

    private static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static int compareTabKey(String a, String b) {
        String aName = formatTabLabel(a);
        String bName = formatTabLabel(b);
        int byLabel = aName.compareToIgnoreCase(bName);
        return byLabel != 0 ? byLabel : a.compareToIgnoreCase(b);
    }

    private int resolveClickedEntry(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getStorageEntries().size() ? index : -1;
    }

    private int resolveClickedRecentEntry(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = Math.max(1, width / SLOT);
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getRecentEntries().size() ? index : -1;
    }

    private int resolveClickedFluid(double mouseX, double mouseY, int x, int y, int width, int height) {
        int cols = 2;
        int rows = Math.max(1, height / SLOT);
        if (!inside(mouseX, mouseY, x, y, cols * SLOT, rows * SLOT)) {
            return -1;
        }
        int col = (int) ((mouseX - x) / SLOT);
        int row = (int) ((mouseY - y) / SLOT);
        int index = row * cols + col;
        return index < this.controller.getFluidEntries().size() ? index : -1;
    }

    private boolean isWorldArea(double mouseX, double mouseY) {
        return mouseY > TOP_H && !isInsideBottomPanel(mouseX, mouseY);
    }

    private boolean isInsideCategoryList(double mouseX, double mouseY) {
        BottomPanelLayout layout = resolveBottomPanelLayout();
        int listY = layout.categoryY() + 13;
        int listH = layout.categoryH() - 15;
        return inside(mouseX, mouseY, layout.categoryX() + 2, listY, CATEGORY_W - 4, listH);
    }

    private void shiftCategoryScroll(int delta) {
        int visible = Math.max(1, (getBottomHeight() - 15) / CATEGORY_ROW_H);
        int maxScroll = Math.max(0, buildCategoryRows().size() - visible);
        this.categoryScroll = Mth.clamp(this.categoryScroll + delta, 0, maxScroll);
    }

    private int getBottomY() {
        return resolveBottomPanelLayout().panelY();
    }

    private int getFloatingPanelAvailableHeight(int panelY) {
        return Math.max(0, getBottomY() - panelY - 6);
    }

    private int getBottomHeight() {
        return resolveBottomPanelLayout().panelH();
    }

    private void adjustBottomPanelSize(int direction) {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, this.height - TOP_H - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.bottomPanelHeight = Mth.clamp(this.bottomPanelHeight + (direction * SLOT), minH, dynamicMaxH);
    }

    private boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return resolveBottomPanelLayout().contains(mouseX, mouseY);
    }

    private BottomPanelLayout resolveBottomPanelLayout() {
        int dynamicMaxH = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, this.height - TOP_H - 16));
        int minH = Math.min(dynamicMaxH, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        int maxH = Math.max(minH, dynamicMaxH);

        this.bottomPanelHeight = Mth.clamp(this.bottomPanelHeight, minH, maxH);

        int panelX = 0;
        int panelY = this.height - this.bottomPanelHeight;
        int panelW = this.width;
        int panelH = this.bottomPanelHeight;
        int contentX = BOTTOM_PANEL_PADDING;
        int contentY = panelY + BOTTOM_PANEL_HEADER_H + 4;
        int sortX = contentX;
        int sortY = contentY + 2;
        int categoryX = sortX + 58;
        int categoryY = contentY;
        int categoryH = Math.max(24, panelY + panelH - BOTTOM_PANEL_PADDING - categoryY);
        int storageX = categoryX + CATEGORY_W + 10;
        int storageY = contentY;
        int storageW = Math.max(120, panelW - BOTTOM_PANEL_PADDING - storageX);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(72, mainStorageW - 82);
        int pagerX = Math.min(storageX + searchW + 4, craftPanelX - 80);
        searchW = Math.max(56, pagerX - storageX - 4);
        int toolY = storageY + 17;
        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, panelY + panelH - BOTTOM_PANEL_PADDING - gridY);
        int storageRows = Math.max(1, gridH / SLOT);
        int craftPanelY = storageY;
        int craftPanelH = Math.max(CRAFT_PANEL_SEARCH_H + CRAFT_PANEL_SLOT + 27, panelY + panelH - BOTTOM_PANEL_PADDING - craftPanelY);

        return new BottomPanelLayout(
                panelX,
                panelY,
                panelW,
                panelH,
                sortX,
                sortY,
                categoryX,
                categoryY,
                categoryH,
                storageX,
                storageY,
                storageW,
                craftPanelX,
                mainStorageW,
                searchW,
                pagerX,
                toolY,
                gridY,
                gridH,
                storageRows,
                craftPanelY,
                craftPanelH);
    }

    private record BottomPanelLayout(
            int panelX,
            int panelY,
            int panelW,
            int panelH,
            int sortX,
            int sortY,
            int categoryX,
            int categoryY,
            int categoryH,
            int storageX,
            int storageY,
            int storageW,
            int craftPanelX,
            int mainStorageW,
            int searchW,
            int pagerX,
            int toolY,
            int gridY,
            int gridH,
            int storageRows,
            int craftPanelY,
            int craftPanelH) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + this.panelH;
        }

        private boolean isInsideHeader(double mouseX, double mouseY) {
            return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                    && mouseY >= this.panelY && mouseY <= this.panelY + BOTTOM_PANEL_HEADER_H;
        }
    }

    private record BottomHoverInfo(String label, String detail, int color) {
    }

    private enum BottomPanelTab {
        STORAGE,
        BLUEPRINTS
    }

    private record QuickBuildPanelLayout(int x, int y, int w, int h) {
        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, this.x, this.y, this.w, this.h);
        }
    }

    private record CraftDockLayout(int cX, int cY) {
        private int slotX(int slot) {
            return switch (slot) {
                case 0, 5 -> this.cX - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 1, 6 -> this.cX + (CRAFT_DOCK_C_SIZE - CRAFT_DOCK_SLOT_SIZE) / 2;
                case 2, 7 -> this.cX + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                case 3 -> this.cX - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 4 -> this.cX + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                default -> this.cX;
            };
        }

        private int slotY(int slot) {
            return switch (slot) {
                case 0, 1, 2 -> this.cY - CRAFT_DOCK_SLOT_SIZE - CRAFT_DOCK_GAP;
                case 3, 4 -> this.cY + (CRAFT_DOCK_C_SIZE - CRAFT_DOCK_SLOT_SIZE) / 2;
                case 5, 6, 7 -> this.cY + CRAFT_DOCK_C_SIZE + CRAFT_DOCK_GAP;
                default -> this.cY;
            };
        }
    }

    private record RtsUiScaleFrame(BuilderScreen screen, int oldW, int oldH, double scale) implements AutoCloseable {
        @Override
        public void close() {
            this.screen.width = this.oldW;
            this.screen.height = this.oldH;
        }
    }

    private int minimumBottomHeightForGridRows(int rows) {
        int gridTopOffset = BOTTOM_PANEL_HEADER_H + 4 + 17 + TOOL_AREA_H + 4;
        return gridTopOffset + BOTTOM_PANEL_PADDING + (Math.max(1, rows) * SLOT);
    }

    private CraftDockLayout resolveCraftDockLayout(int x, int y) {
        int cX = x + 14;
        int cY = y + CRAFT_DOCK_SLOT_SIZE + CRAFT_DOCK_GAP;
        return new CraftDockLayout(cX, cY);
    }

    public boolean isSearchFocused() {
        return (this.searchBox != null && this.searchBox.isFocused())
                || (this.craftSearchBox != null && this.craftSearchBox.isFocused());
    }

    private int getSelectedToolSlot() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 0;
        }
        return Mth.clamp(this.minecraft.player.getInventory().selected, 0, 8);
    }

    private ItemStack getSelectedToolStack() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return this.minecraft.player.getInventory().getItem(getSelectedToolSlot());
    }

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

    private boolean canUseToolSlotShapeSource() {
        if (this.controller.hasSelectedItem() || this.controller.hasSelectedFluid() || this.controller.isEmptyHandSelected()) {
            return false;
        }
        ItemStack stack = getSelectedToolStack();
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    private boolean tryAssignQuickSlotFromToolSelection(int pinIndex) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.controller.isEmptyHandSelected()) {
            return false;
        }
        int slot = this.hoveredToolSlot >= 0 ? this.hoveredToolSlot : getSelectedToolSlot();
        slot = Mth.clamp(slot, 0, 8);
        ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        this.controller.assignQuickSlotFromToolItem(pinIndex, stack);
        return true;
    }

    private void setSelectedToolSlot(int slot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.getInventory().selected = Mth.clamp(slot, 0, 8);
    }

    private int getHotbarSlotsWidth() {
        return HOTBAR_PITCH * 9 - (HOTBAR_PITCH - HOTBAR_SLOT);
    }

    private int getFluidStripWidth(int storageWidth) {
        int wanted = SLOT * 2;
        if (storageWidth < wanted + SLOT * 3) {
            return 0;
        }
        return wanted;
    }

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

    private boolean shouldUsePinPager(int visibleCells, int totalPins) {
        return visibleCells >= 2 && totalPins > visibleCells;
    }

    private int computePinSlotsPerPage(int visibleCells, int totalPins) {
        if (visibleCells <= 0) {
            return 1;
        }
        if (shouldUsePinPager(visibleCells, totalPins)) {
            return Math.max(1, visibleCells - 1);
        }
        return visibleCells;
    }

    private void placeWithShape(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir, double mouseY,
            boolean fluidPlacement, PlacementReplayKind replayKind, String replayItemId, int replayToolSlot) {
        if (hit == null) {
            return;
        }
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            clearShapeBuildSession();
            if (fluidPlacement) {
                this.controller.placeSelectedFluid(hit, forcePlace, rayOrigin, rayDir);
            } else {
                this.controller.placeSelected(hit, forcePlace, rayOrigin, rayDir);
                recordSinglePlacementForUndo(
                        hit,
                        replayKind,
                        replayItemId,
                        replayToolSlot);
            }
            return;
        }

        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != shape) {
            this.shapeFootprintNudgeA = 0;
            this.shapeFootprintNudgeB = 0;
            this.shapeBuildSession = new ShapeBuildSession(
                    shape,
                    resolveShapeBuildFace(shape, hit.getDirection(), rayDir),
                    resolveShapePlacementFace(shape, hit.getDirection(), rayDir),
                    hit.getBlockPos(),
                    null,
                    ShapeBuildPhase.NEED_SECOND_POINT,
                    0,
                    mouseY);
            return;
        }

        ShapeBuildSession session = this.shapeBuildSession;
        if (session.phase() == ShapeBuildPhase.NEED_SECOND_POINT) {
            BlockPos pointB = resolveShapePlanePoint(session, hit);
            this.shapeBuildSession = new ShapeBuildSession(
                    shape,
                    session.planeFace(),
                    session.placementFace(),
                    session.pointA(),
                    pointB,
                    ShapeBuildPhase.READY_CONFIRM,
                    0,
                    requiresThirdPoint(shape) ? mouseY : session.boxHeightMouseBaseY());
            return;
        }

        if (session.phase() == ShapeBuildPhase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildSession(
                    shape,
                    session.planeFace(),
                    session.placementFace(),
                    session.pointA(),
                    session.pointB(),
                    ShapeBuildPhase.READY_CONFIRM,
                    session.boxHeightOffset(),
                    session.boxHeightMouseBaseY());
        }
    }

    private boolean tryConfirmPendingShapeBuild(boolean forcePlace) {
        if (this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK) {
            return false;
        }
        boolean useFluid = this.controller.hasSelectedFluid();
        boolean usePinnedItem = this.controller.hasSelectedItem();
        if (!useFluid && !usePinnedItem && !canUseToolSlotShapeSource()) {
            return false;
        }
        ShapeBuildInput input = resolveCurrentShapeBuildInput(null, true);
        if (input == null || this.minecraft == null) {
            return false;
        }
        Vec3 rayOrigin = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = computeCursorRayDirection();
        List<BlockHitResult> hits = buildShapePlacementHits(input, this.shapeFillMode);
        clearShapeBuildSession();
        if (useFluid) {
            for (BlockHitResult shapedHit : hits) {
                this.controller.placeSelectedFluid(shapedHit, forcePlace, rayOrigin, rayDir);
            }
        } else {
            this.controller.placeSelectedBatch(hits, forcePlace, rayOrigin, rayDir, true);
        }
        if (!useFluid) {
            List<BlockPos> positions = new ArrayList<>(hits.size());
            for (BlockHitResult shapedHit : hits) {
                positions.add(shapedHit.getBlockPos().immutable());
            }
            recordPlacementBatchForUndo(
                    usePinnedItem ? PlacementReplayKind.PIN_ITEM : PlacementReplayKind.TOOL_SLOT,
                    usePinnedItem ? this.controller.getSelectedItemId() : "",
                    usePinnedItem ? -1 : getSelectedToolSlot(),
                    input.placementFace(),
                    positions);
        }
        return true;
    }

    private void clearShapeBuildSession() {
        this.shapeBuildSession = null;
        this.shapeFootprintNudgeA = 0;
        this.shapeFootprintNudgeB = 0;
    }

    private void rotateShapeByStep(int step) {
        int raw = this.shapeRotateDegrees + (step * SHAPE_ROTATE_STEP_DEGREES);
        this.shapeRotateDegrees = Math.floorMod(raw, 360);
        persistUiState();
    }

    public ShapeGhostPreview getShapeGhostPreview() {
        if (this.leftMiningActive && this.ultimineOpen) {
            List<BlockPos> preview = collectUltiminePreviewBlocks();
            if (!preview.isEmpty()) {
                return new ShapeGhostPreview(preview, true);
            }
        }
        if (this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK) {
            return ShapeGhostPreview.EMPTY;
        }
        if (!this.controller.hasSelectedItem() && !this.controller.hasSelectedFluid() && !canUseToolSlotShapeSource()) {
            return ShapeGhostPreview.EMPTY;
        }
        ShapeBuildInput input = resolveCurrentShapeBuildInput(pickBlockHit(), false);
        if (input == null) {
            return ShapeGhostPreview.EMPTY;
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, buildShapePositions(input, this.shapeFillMode));
        if (blocks.isEmpty()) {
            return ShapeGhostPreview.EMPTY;
        }
        boolean ready = this.shapeBuildSession != null && this.shapeBuildSession.phase() == ShapeBuildPhase.READY_CONFIRM;
        return new ShapeGhostPreview(blocks, ready);
    }

    public BlueprintGhostPreview getBlueprintGhostPreview() {
        if (this.bottomPanelTab != BottomPanelTab.BLUEPRINTS
                || BlueprintPanel.isCaptureModeActive()
                || !BlueprintPanel.hasSelectedBlueprint()) {
            return BlueprintGhostPreview.EMPTY;
        }
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        if (anchor == null) {
            if (!isWorldArea(this.lastMouseX, this.lastMouseY)) {
                return BlueprintGhostPreview.EMPTY;
            }
            BlockHitResult hit = pickBlueprintPlacementHit();
            if (hit == null) {
                return BlueprintGhostPreview.EMPTY;
            }
            anchor = resolveBlueprintAnchor(hit);
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

    private List<BlockPos> collectUltiminePreviewBlocks() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        BlockPos seed = this.controller.getMineProgressPos();
        if (seed == null) {
            BlockHitResult hit = pickBlockHit();
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
        int limit = clampUltimineLimit(this.ultimineLimit);
        return RtsUltimineCollector.collect(
                this.minecraft.level,
                seed,
                limit,
                (pos, state, originalState) -> !state.isAir()
                        && state.getBlock() == originalState.getBlock()
                        && (creative || state.getDestroySpeed(this.minecraft.level, pos) >= 0.0F));
    }

    private ShapeBuildInput resolveCurrentShapeBuildInput(BlockHitResult cursorHit, boolean requireReady) {
        ShapeBuildSession session = this.shapeBuildSession;
        if (session == null || session.shape() != this.controller.getBuildShape()) {
            return null;
        }

        if (requireReady && session.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return null;
        }

        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return null;
        }

        if (session.phase() == ShapeBuildPhase.NEED_SECOND_POINT) {
            if (requireReady) {
                return null;
            }
            BlockPos pointB = resolveShapePlanePoint(session, cursorHit);
            pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
            return new ShapeBuildInput(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, 0);
        }

        BlockPos pointB = session.pointB();
        if (pointB == null) {
            return null;
        }

        if (session.phase() == ShapeBuildPhase.NEED_THIRD_POINT) {
            if (requireReady) {
                return null;
            }
            pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
            return new ShapeBuildInput(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session));
        }

        pointB = applyShapeFootprintNudges(session.shape(), session.planeFace(), pointA, pointB);
        return new ShapeBuildInput(session.shape(), session.planeFace(), session.placementFace(), pointA, pointB, resolveBoxHeightOffset(session));
    }

    private int resolveBoxHeightOffset(ShapeBuildSession session) {
        if (session == null) {
            return 0;
        }
        if (session.shape() != ClientRtsController.BuildShape.BOX
                || (session.phase() != ShapeBuildPhase.READY_CONFIRM && session.phase() != ShapeBuildPhase.NEED_THIRD_POINT)) {
            return session.boxHeightOffset();
        }
        int mouseOffset = (int) Math.round((session.boxHeightMouseBaseY() - this.shapeCursorY) / 10.0D);
        return clampShapeOffset(session.boxHeightOffset() + mouseOffset);
    }

    private BlockPos resolveShapePlanePoint(ShapeBuildSession session, BlockHitResult cursorHit) {
        if (session == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : null;
        }
        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : null;
        }

        ClientRtsController.BuildShape shape = session.shape();
        if (shape == null
                || shape == ClientRtsController.BuildShape.BLOCK) {
            return cursorHit != null ? cursorHit.getBlockPos() : pointA;
        }

        Direction planeFace = session.planeFace();
        if (shape == ClientRtsController.BuildShape.LINE
                || shape == ClientRtsController.BuildShape.SQUARE
                || shape == ClientRtsController.BuildShape.WALL
                || shape == ClientRtsController.BuildShape.BOX) {
            planeFace = Direction.UP;
        }
        if (planeFace == null) {
            return cursorHit != null ? cursorHit.getBlockPos() : pointA;
        }

        Vec3 planeHit = intersectCursorRayWithShapePlane(pointA, planeFace);
        if (planeHit == null && cursorHit != null) {
            planeHit = cursorHit.getLocation();
        }
        if (planeHit == null) {
            return pointA;
        }
        return blockPosFromPlaneHit(pointA, planeFace, planeHit);
    }

    private Vec3 intersectCursorRayWithShapePlane(BlockPos anchor, Direction face) {
        if (anchor == null || face == null || this.minecraft == null || this.minecraft.gameRenderer == null) {
            return null;
        }
        Vec3 rayOrigin = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = computeCursorRayDirection();
        if (rayOrigin == null || rayDir == null) {
            return null;
        }

        Vec3 planeAnchor = Vec3.atCenterOf(anchor);
        double planeCoord = switch (face.getAxis()) {
            case X -> planeAnchor.x;
            case Y -> planeAnchor.y;
            case Z -> planeAnchor.z;
        };
        double originCoord = switch (face.getAxis()) {
            case X -> rayOrigin.x;
            case Y -> rayOrigin.y;
            case Z -> rayOrigin.z;
        };
        double dirCoord = switch (face.getAxis()) {
            case X -> rayDir.x;
            case Y -> rayDir.y;
            case Z -> rayDir.z;
        };
        if (Math.abs(dirCoord) < 1.0E-5D) {
            return null;
        }

        double t = (planeCoord - originCoord) / dirCoord;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }
        return rayOrigin.add(rayDir.scale(t));
    }

    private static BlockPos blockPosFromPlaneHit(BlockPos anchor, Direction face, Vec3 hitVec) {
        if (anchor == null || face == null || hitVec == null) {
            return anchor;
        }
        return switch (face.getAxis()) {
            case X -> new BlockPos(anchor.getX(), Mth.floor(hitVec.y), Mth.floor(hitVec.z));
            case Y -> new BlockPos(Mth.floor(hitVec.x), anchor.getY(), Mth.floor(hitVec.z));
            case Z -> new BlockPos(Mth.floor(hitVec.x), Mth.floor(hitVec.y), anchor.getZ());
        };
    }

    private BlockPos applyShapeFootprintNudges(ClientRtsController.BuildShape shape, Direction face, BlockPos pointA, BlockPos pointB) {
        if (pointA == null || pointB == null) {
            return pointB;
        }
        if (this.shapeFootprintNudgeA == 0 && this.shapeFootprintNudgeB == 0) {
            return pointB;
        }
        if (shape == null || shape == ClientRtsController.BuildShape.BLOCK) {
            return pointB;
        }

        Direction axisA;
        Direction axisB;
        if (shape == ClientRtsController.BuildShape.BOX) {
            axisA = Direction.EAST;
            axisB = Direction.SOUTH;
        } else {
            Direction[] axes = resolveShapePlaneAxes(shape, face);
            if (axes.length < 2) {
                return pointB;
            }
            axisA = axes[0];
            axisB = axes[1];
        }

        int dx = pointB.getX() - pointA.getX();
        int dy = pointB.getY() - pointA.getY();
        int dz = pointB.getZ() - pointA.getZ();
        int nextA = clampShapeOffset(dotDelta(dx, dy, dz, axisA) + this.shapeFootprintNudgeA);
        int nextB = clampShapeOffset(dotDelta(dx, dy, dz, axisB) + this.shapeFootprintNudgeB);
        return offsetPos(pointA, axisA, nextA, axisB, nextB);
    }

    private List<BlockHitResult> buildShapePlacementHits(ShapeBuildInput input, ShapeFillMode fillMode) {
        List<BlockPos> positions = filterOccupiedReadyShapeTargets(input, buildShapePositions(input, fillMode));
        List<BlockHitResult> hits = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            hits.add(createShapePlacementHit(pos, input.placementFace()));
        }
        return hits;
    }

    private List<BlockPos> buildShapePositions(ShapeBuildInput input, ShapeFillMode fillMode) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        BlockPos start = input.pointA();
        BlockPos end = input.pointB();
        switch (input.shape()) {
            case LINE -> addLineTargets(targets, start, end);
            case SQUARE -> addSquareTargets(targets, start, end, input.planeFace(), fillMode);
            case WALL -> addWallTargets(targets, start, end, input.boxHeightOffset(), fillMode);
            case CIRCLE -> addCircleTargets(targets, start, end, input.planeFace(), fillMode);
            case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), fillMode);
            default -> targets.add(start);
        }
        return new ArrayList<>(targets);
    }

    private List<BlockPos> filterOccupiedReadyShapeTargets(ShapeBuildInput input, List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (input == null || input.placementFace() == null) {
            return targets;
        }

        boolean strictEmptyLock = shouldSkipOccupiedReadyShapeTargets(input);
        boolean uniformPlacement = shouldUseUniformShapePlanePlacement(input);
        LinkedHashSet<BlockPos> resolved = new LinkedHashSet<>(targets.size());
        if (this.minecraft == null || this.minecraft.level == null) {
            for (BlockPos clickedPos : targets) {
                if (clickedPos == null) {
                    continue;
                }
                BlockPos placePos = uniformPlacement
                        ? resolveUniformShapePlacementTargetPos(input, clickedPos)
                        : resolvePlacementTargetPos(clickedPos, input.placementFace());
                if (placePos != null) {
                    resolved.add(placePos.immutable());
                }
            }
            return new ArrayList<>(resolved);
        }

        for (BlockPos clickedPos : targets) {
            if (clickedPos == null) {
                continue;
            }
            BlockPos placePos = uniformPlacement
                    ? resolveUniformShapePlacementTargetPos(input, clickedPos)
                    : resolvePlacementTargetPos(clickedPos, input.placementFace());
            if (placePos == null) {
                continue;
            }

            if (strictEmptyLock
                    && this.minecraft.level.hasChunkAt(placePos)
                    && !this.minecraft.level.getBlockState(placePos).canBeReplaced()) {
                continue;
            }
            resolved.add(placePos.immutable());
        }
        return new ArrayList<>(resolved);
    }

    private boolean shouldUseUniformShapePlanePlacement(ShapeBuildInput input) {
        if (input == null || input.placementFace() == null) {
            return false;
        }
        return switch (input.shape()) {
            case LINE, SQUARE, WALL, BOX -> true;
            default -> false;
        };
    }

    private BlockPos resolvePlacementTargetPos(BlockPos clickedPos, Direction face) {
        if (clickedPos == null || face == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        if (!this.minecraft.level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return this.minecraft.level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    private BlockPos resolveUniformShapePlacementTargetPos(ShapeBuildInput input, BlockPos clickedPos) {
        if (input == null || clickedPos == null) {
            return null;
        }
        BlockPos anchor = input.pointA();
        Direction face = input.placementFace();
        if (anchor == null || face == null) {
            return clickedPos;
        }
        BlockPos anchorPlaced = resolvePlacementTargetPos(anchor, face);
        if (anchorPlaced == null) {
            return clickedPos;
        }
        return clickedPos.offset(
                anchorPlaced.getX() - anchor.getX(),
                anchorPlaced.getY() - anchor.getY(),
                anchorPlaced.getZ() - anchor.getZ());
    }

    private boolean shouldSkipOccupiedReadyShapeTargets(ShapeBuildInput input) {
        if (input == null || input.shape() == ClientRtsController.BuildShape.BLOCK) {
            return false;
        }
        if (this.shapeBuildSession == null || this.shapeBuildSession.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return false;
        }
        if (this.controller.hasSelectedFluid()) {
            return false;
        }
        if (this.controller.hasSelectedItem()) {
            String itemId = this.controller.getSelectedItemId();
            if (itemId == null || itemId.isBlank()) {
                return false;
            }
            net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return false;
            }
            return BuiltInRegistries.ITEM.get(key) instanceof BlockItem;
        }
        return canUseToolSlotShapeSource();
    }

    private BlockHitResult createShapePlacementHit(BlockPos pos, Direction face) {
        Vec3 faceNormal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 hitVec = Vec3.atCenterOf(pos).add(faceNormal.scale(0.5D));
        return new BlockHitResult(hitVec, face, pos, false);
    }

    private void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 0) {
            targets.add(start);
            return;
        }

        if (steps > SHAPE_MAX_OFFSET) {
            double scale = SHAPE_MAX_OFFSET / (double) steps;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
            dz = (int) Math.round(dz * scale);
            steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        }

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = start.getX() + (int) Math.round(dx * t);
            int y = start.getY() + (int) Math.round(dy * t);
            int z = start.getZ() + (int) Math.round(dz * t);
            targets.add(new BlockPos(x, y, z));
        }
    }

    private void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.SQUARE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]));
        addRotatedPlaneRectangleTargets(targets, start, axes[0], axes[1], aOffset, bOffset, fillMode);
    }

    private void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        LinkedHashSet<BlockPos> baseLine = new LinkedHashSet<>();
        addLineTargets(baseLine, start, new BlockPos(end.getX(), start.getY(), end.getZ()));
        if (baseLine.isEmpty()) {
            baseLine.add(start);
        }

        int yOffset = clampShapeOffset(heightOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        List<BlockPos> base = new ArrayList<>(baseLine);
        for (int i = 0; i < base.size(); i++) {
            BlockPos basePos = base.get(i);
            boolean endColumn = i == 0 || i == base.size() - 1;
            for (int iy = minY; iy <= maxY; iy++) {
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                targets.add(basePos.above(iy));
            }
        }
    }

    private void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.CIRCLE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = Mth.clamp((int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b))), 0, SHAPE_MAX_RADIUS);
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        Set<PlaneCell> rotatedCells = new HashSet<>();

        for (int ia = -radius; ia <= radius; ia++) {
            for (int ib = -radius; ib <= radius; ib++) {
                int dist2 = (ia * ia) + (ib * ib);
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                if (!inOuter || ((fillMode != ShapeFillMode.FILL) && inInner)) {
                    continue;
                }
                RotatedOffset rotated = rotatePlaneOffset(ia, ib, 0.0D, 0.0D, this.shapeRotateDegrees);
                rotatedCells.add(new PlaneCell(rotated.a(), rotated.b()));
            }
        }

        if (fillMode == ShapeFillMode.FILL) {
            rotatedCells = fillPlaneInteriorHoles(rotatedCells);
        }

        for (PlaneCell cell : rotatedCells) {
            targets.add(offsetPos(start, axes[0], cell.a(), axes[1], cell.b()));
        }
    }

    private void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        int xOffset = clampShapeOffset(end.getX() - start.getX());
        int zOffset = clampShapeOffset(end.getZ() - start.getZ());
        int yOffset = clampShapeOffset(heightOffset);

        int minX = Math.min(0, xOffset);
        int maxX = Math.max(0, xOffset);
        int minZ = Math.min(0, zOffset);
        int maxZ = Math.max(0, zOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        Set<PlaneCell> rotatedFootprint = buildRotatedRectangleFillCells(minX, maxX, minZ, maxZ, this.shapeRotateDegrees);
        if (rotatedFootprint.isEmpty()) {
            return;
        }

        if (fillMode == ShapeFillMode.FILL) {
            for (PlaneCell cell : rotatedFootprint) {
                for (int iy = minY; iy <= maxY; iy++) {
                    targets.add(start.offset(cell.a(), iy, cell.b()));
                }
            }
            return;
        }

        Set<BlockPos> fullVolume = new HashSet<>(rotatedFootprint.size() * Math.max(1, (maxY - minY) + 1));
        for (PlaneCell cell : rotatedFootprint) {
            for (int iy = minY; iy <= maxY; iy++) {
                fullVolume.add(start.offset(cell.a(), iy, cell.b()));
            }
        }

        for (BlockPos pos : fullVolume) {
            boolean xBoundary = !fullVolume.contains(pos.east()) || !fullVolume.contains(pos.west());
            boolean yBoundary = !fullVolume.contains(pos.above()) || !fullVolume.contains(pos.below());
            boolean zBoundary = !fullVolume.contains(pos.north()) || !fullVolume.contains(pos.south());
            int boundaryAxes = (xBoundary ? 1 : 0) + (yBoundary ? 1 : 0) + (zBoundary ? 1 : 0);
            if (fillMode == ShapeFillMode.HOLLOW) {
                if (boundaryAxes >= 1) {
                    targets.add(pos);
                }
                continue;
            }
            if (boundaryAxes >= 2) {
                targets.add(pos);
            }
        }
    }

    private void addRotatedPlaneRectangleTargets(Set<BlockPos> targets, BlockPos start, Direction axisA, Direction axisB,
            int aOffset, int bOffset, ShapeFillMode fillMode) {
        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);
        Set<PlaneCell> filledCells = buildRotatedRectangleFillCells(minA, maxA, minB, maxB, this.shapeRotateDegrees);
        for (PlaneCell cell : filledCells) {
            if (fillMode != ShapeFillMode.FILL && isPlaneBoundaryCell(filledCells, cell)) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
                continue;
            }
            if (fillMode == ShapeFillMode.FILL) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
            }
        }
    }

    private static boolean isPlaneBoundaryCell(Set<PlaneCell> filledCells, PlaneCell cell) {
        return !filledCells.contains(new PlaneCell(cell.a() + 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a() - 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() + 1))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() - 1));
    }

    private static Set<PlaneCell> buildRotatedRectangleFillCells(int minA, int maxA, int minB, int maxB, int degrees) {
        Set<PlaneCell> filled = new HashSet<>();
        int normalized = Math.floorMod(degrees, 360);
        if (normalized == 0) {
            for (int a = minA; a <= maxA; a++) {
                for (int b = minB; b <= maxB; b++) {
                    filled.add(new PlaneCell(a, b));
                }
            }
            return fillPlaneInteriorHoles(filled);
        }

        double centerA = (minA + maxA) * 0.5D;
        double centerB = (minB + maxB) * 0.5D;
        double rad = Math.toRadians(normalized);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double[][] corners = new double[][] {
                { minA, minB },
                { minA, maxB },
                { maxA, minB },
                { maxA, maxB }
        };
        double minRotA = Double.POSITIVE_INFINITY;
        double maxRotA = Double.NEGATIVE_INFINITY;
        double minRotB = Double.POSITIVE_INFINITY;
        double maxRotB = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double da = corner[0] - centerA;
            double db = corner[1] - centerB;
            double ra = (da * cos) - (db * sin) + centerA;
            double rb = (da * sin) + (db * cos) + centerB;
            minRotA = Math.min(minRotA, ra);
            maxRotA = Math.max(maxRotA, ra);
            minRotB = Math.min(minRotB, rb);
            maxRotB = Math.max(maxRotB, rb);
        }

        int scanMinA = (int) Math.floor(minRotA) - 1;
        int scanMaxA = (int) Math.ceil(maxRotA) + 1;
        int scanMinB = (int) Math.floor(minRotB) - 1;
        int scanMaxB = (int) Math.ceil(maxRotB) + 1;

        for (int a = scanMinA; a <= scanMaxA; a++) {
            for (int b = scanMinB; b <= scanMaxB; b++) {
                if (isInverseRotatedInsideCellBounds(a, b, minA, maxA, minB, maxB, centerA, centerB, cos, sin)) {
                    filled.add(new PlaneCell(a, b));
                }
            }
        }
        return fillPlaneInteriorHoles(filled);
    }

    private static boolean isInverseRotatedInsideCellBounds(
            int targetA, int targetB,
            int minA, int maxA, int minB, int maxB,
            double centerA, double centerB,
            double cos, double sin) {
        double[][] sampleOffsets = new double[][] {
                { 0.0D, 0.0D },
                { -0.35D, 0.0D },
                { 0.35D, 0.0D },
                { 0.0D, -0.35D },
                { 0.0D, 0.35D },
                { -0.3D, -0.3D },
                { -0.3D, 0.3D },
                { 0.3D, -0.3D },
                { 0.3D, 0.3D }
        };
        for (double[] sample : sampleOffsets) {
            double da = (targetA + sample[0]) - centerA;
            double db = (targetB + sample[1]) - centerB;
            double sourceA = (da * cos) + (db * sin) + centerA;
            double sourceB = (-da * sin) + (db * cos) + centerB;
            if (sourceA >= minA - 0.5D
                    && sourceA <= maxA + 0.5D
                    && sourceB >= minB - 0.5D
                    && sourceB <= maxB + 0.5D) {
                return true;
            }
        }
        return false;
    }

    private static Set<PlaneCell> fillPlaneInteriorHoles(Set<PlaneCell> filledCells) {
        if (filledCells == null || filledCells.isEmpty()) {
            return filledCells == null ? Set.of() : filledCells;
        }

        int minA = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE;
        int maxB = Integer.MIN_VALUE;
        for (PlaneCell cell : filledCells) {
            minA = Math.min(minA, cell.a());
            maxA = Math.max(maxA, cell.a());
            minB = Math.min(minB, cell.b());
            maxB = Math.max(maxB, cell.b());
        }

        int extMinA = minA - 1;
        int extMaxA = maxA + 1;
        int extMinB = minB - 1;
        int extMaxB = maxB + 1;

        Set<PlaneCell> outside = new HashSet<>();
        ArrayDeque<PlaneCell> queue = new ArrayDeque<>();
        for (int a = extMinA; a <= extMaxA; a++) {
            queueOutsidePlaneCell(new PlaneCell(a, extMinB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(a, extMaxB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }
        for (int b = extMinB + 1; b <= extMaxB - 1; b++) {
            queueOutsidePlaneCell(new PlaneCell(extMinA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(extMaxA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }

        while (!queue.isEmpty()) {
            PlaneCell cell = queue.removeFirst();
            queueOutsidePlaneCell(new PlaneCell(cell.a() + 1, cell.b()), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a() - 1, cell.b()), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a(), cell.b() + 1), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a(), cell.b() - 1), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }

        Set<PlaneCell> dense = new HashSet<>(filledCells);
        for (int a = minA; a <= maxA; a++) {
            for (int b = minB; b <= maxB; b++) {
                PlaneCell cell = new PlaneCell(a, b);
                if (dense.contains(cell)) {
                    continue;
                }
                if (!outside.contains(cell)) {
                    dense.add(cell);
                }
            }
        }
        return dense;
    }

    private static void queueOutsidePlaneCell(
            PlaneCell cell,
            Set<PlaneCell> filledCells,
            Set<PlaneCell> outside,
            ArrayDeque<PlaneCell> queue,
            int minA, int maxA, int minB, int maxB) {
        if (cell.a() < minA || cell.a() > maxA || cell.b() < minB || cell.b() > maxB) {
            return;
        }
        if (filledCells.contains(cell) || outside.contains(cell)) {
            return;
        }
        outside.add(cell);
        queue.addLast(cell);
    }

    private static int clampShapeOffset(int value) {
        return Mth.clamp(value, -SHAPE_MAX_OFFSET, SHAPE_MAX_OFFSET);
    }

    private static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    private static BlockPos offsetPos(BlockPos origin, Direction axisA, int stepA, Direction axisB, int stepB) {
        int dx = (axisA.getStepX() * stepA) + (axisB.getStepX() * stepB);
        int dy = (axisA.getStepY() * stepA) + (axisB.getStepY() * stepB);
        int dz = (axisA.getStepZ() * stepA) + (axisB.getStepZ() * stepB);
        return origin.offset(dx, dy, dz);
    }

    private static RotatedOffset rotatePlaneOffset(int a, int b, double centerA, double centerB, int degrees) {
        int normalized = Math.floorMod(degrees, 360);
        if (normalized == 0) {
            return new RotatedOffset(a, b);
        }
        double rad = Math.toRadians(normalized);
        double da = a - centerA;
        double db = b - centerB;
        int ra = (int) Math.round((da * Math.cos(rad)) - (db * Math.sin(rad)) + centerA);
        int rb = (int) Math.round((da * Math.sin(rad)) + (db * Math.cos(rad)) + centerB);
        return new RotatedOffset(ra, rb);
    }

    private Direction resolveShapeBuildFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape == null) {
            return clickedFace == null ? Direction.UP : clickedFace;
        }
        return switch (shape) {
            case LINE, SQUARE, WALL, BOX -> Direction.UP;
            default -> clickedFace == null ? Direction.UP : clickedFace;
        };
    }

    private Direction resolveShapePlacementFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (clickedFace != null) {
            return clickedFace;
        }
        return resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    private Direction[] resolveShapePlaneAxes(ClientRtsController.BuildShape shape, Direction face) {
        if (shape == ClientRtsController.BuildShape.SQUARE || shape == ClientRtsController.BuildShape.BOX) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (shape == ClientRtsController.BuildShape.WALL) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (face == null) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        return switch (face.getAxis()) {
            case Y -> new Direction[] { Direction.EAST, Direction.SOUTH };
            case X -> new Direction[] { Direction.UP, Direction.SOUTH };
            case Z -> new Direction[] { Direction.EAST, Direction.UP };
        };
    }

    private static boolean requiresThirdPoint(ClientRtsController.BuildShape shape) {
        return shape == ClientRtsController.BuildShape.BOX;
    }

    private void cycleShapeFillModeForCurrentShape(int step) {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        List<ShapeFillMode> modes = availableFillModes(shape);
        if (modes.isEmpty()) {
            return;
        }
        int currentIndex = modes.indexOf(this.shapeFillMode);
        if (currentIndex < 0) {
            this.shapeFillMode = modes.get(0);
            persistUiState();
            return;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.shapeFillMode = modes.get(next);
        persistUiState();
    }

    private void ensureFillModeForShape(ClientRtsController.BuildShape shape) {
        List<ShapeFillMode> modes = availableFillModes(shape);
        if (modes.isEmpty()) {
            this.shapeFillMode = ShapeFillMode.FILL;
            persistUiState();
            return;
        }
        if (!modes.contains(this.shapeFillMode)) {
            this.shapeFillMode = modes.get(0);
            persistUiState();
        }
    }

    private static List<ShapeFillMode> availableFillModes(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return List.of(ShapeFillMode.FILL);
        }
        return switch (shape) {
            case LINE -> List.of(ShapeFillMode.FILL);
            case SQUARE, WALL, CIRCLE -> List.of(ShapeFillMode.FILL, ShapeFillMode.HOLLOW);
            case BOX -> List.of(ShapeFillMode.FILL, ShapeFillMode.HOLLOW, ShapeFillMode.SKELETON);
            default -> List.of(ShapeFillMode.FILL);
        };
    }

    private boolean adjustShapeDimensionNudge(int delta, boolean adjustSecondaryAxis, boolean adjustHeight) {
        if (delta == 0 || this.shapeBuildSession == null) {
            return false;
        }
        if (adjustHeight && canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return adjustShapeHeightNudge(delta);
        }
        return adjustShapeFootprintNudge(delta, adjustSecondaryAxis);
    }

    private boolean adjustShapeFootprintNudge(int delta, boolean secondaryAxis) {
        if (delta == 0 || this.shapeBuildSession == null) {
            return false;
        }
        if (this.shapeBuildSession.shape() == ClientRtsController.BuildShape.BLOCK) {
            return false;
        }
        if (this.shapeBuildSession.phase() != ShapeBuildPhase.NEED_SECOND_POINT
                && this.shapeBuildSession.phase() != ShapeBuildPhase.NEED_THIRD_POINT
                && this.shapeBuildSession.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return false;
        }
        if (secondaryAxis) {
            this.shapeFootprintNudgeB = clampShapeOffset(this.shapeFootprintNudgeB + delta);
        } else {
            this.shapeFootprintNudgeA = clampShapeOffset(this.shapeFootprintNudgeA + delta);
        }
        return true;
    }

    private boolean canAdjustCurrentShapeHeight() {
        return this.shapeBuildSession != null
                && this.shapeBuildSession.shape() == this.controller.getBuildShape()
                && canAdjustShapeHeight(this.shapeBuildSession.shape());
    }

    private static boolean canAdjustShapeHeight(ClientRtsController.BuildShape shape) {
        return shape == ClientRtsController.BuildShape.WALL || shape == ClientRtsController.BuildShape.BOX;
    }

    private boolean adjustShapeHeightNudge(int delta) {
        if (delta == 0 || this.shapeBuildSession == null || !canAdjustShapeHeight(this.shapeBuildSession.shape())) {
            return false;
        }
        if (this.shapeBuildSession.shape() == ClientRtsController.BuildShape.BOX
                && this.shapeBuildSession.phase() != ShapeBuildPhase.NEED_THIRD_POINT
                && this.shapeBuildSession.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return false;
        }
        if (this.shapeBuildSession.shape() == ClientRtsController.BuildShape.WALL
                && this.shapeBuildSession.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return false;
        }
        int nextOffset = clampShapeOffset(this.shapeBuildSession.boxHeightOffset() + delta);
        this.shapeBuildSession = new ShapeBuildSession(
                this.shapeBuildSession.shape(),
                this.shapeBuildSession.planeFace(),
                this.shapeBuildSession.placementFace(),
                this.shapeBuildSession.pointA(),
                this.shapeBuildSession.pointB(),
                this.shapeBuildSession.phase(),
                nextOffset,
                this.shapeBuildSession.boxHeightMouseBaseY());
        return true;
    }

    private boolean undoLastPlacementBatch() {
        if (this.shapeUndoStack.isEmpty()) {
            return false;
        }
        ShapeHistoryBatch batch = this.shapeUndoStack.remove(this.shapeUndoStack.size() - 1);
        List<BlockPos> positions = batch.positions();
        for (int i = positions.size() - 1; i >= 0; i--) {
            this.controller.breakPlaced(positions.get(i), batch.face(), true);
        }
        this.shapeRedoStack.add(batch);
        if (this.shapeRedoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeRedoStack.remove(0);
        }
        return true;
    }

    private boolean redoLastPlacementBatch() {
        if (this.shapeRedoStack.isEmpty() || this.minecraft == null) {
            return false;
        }
        int idx = this.shapeRedoStack.size() - 1;
        ShapeHistoryBatch batch = this.shapeRedoStack.get(idx);
        if (batch.replayKind() == PlacementReplayKind.PIN_ITEM) {
            if (!this.controller.hasSelectedItem() || !batch.itemId().equals(this.controller.getSelectedItemId())) {
                return false;
            }
        } else {
            if (this.minecraft.player == null) {
                return false;
            }
            this.controller.clearPlacementSelectionPreserveMode();
            setSelectedToolSlot(batch.toolSlot());
        }
        this.shapeRedoStack.remove(idx);
        Vec3 rayOrigin = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 rayDir = computeCursorRayDirection();
        List<BlockHitResult> hits = new ArrayList<>(batch.positions().size());
        for (BlockPos pos : batch.positions()) {
            hits.add(createShapePlacementHit(pos, batch.face()));
        }
        this.controller.placeSelectedBatch(hits, false, rayOrigin, rayDir, false);
        this.shapeUndoStack.add(batch);
        if (this.shapeUndoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeUndoStack.remove(0);
        }
        return true;
    }

    private void recordSinglePlacementForUndo(
            BlockHitResult hit,
            PlacementReplayKind replayKind,
            String itemId,
            int toolSlot) {
        if (hit == null) {
            return;
        }
        recordPlacementBatchForUndo(
                replayKind,
                itemId,
                toolSlot,
                hit.getDirection(),
                List.of(hit.getBlockPos().immutable()));
    }

    private void recordPlacementBatchForUndo(
            PlacementReplayKind replayKind,
            String itemId,
            int toolSlot,
            Direction face,
            List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        if (replayKind == PlacementReplayKind.PIN_ITEM && (itemId == null || itemId.isBlank())) {
            return;
        }
        ShapeHistoryBatch batch = new ShapeHistoryBatch(
                replayKind,
                itemId == null ? "" : itemId,
                Mth.clamp(toolSlot, 0, 8),
                face,
                List.copyOf(positions));
        this.shapeUndoStack.add(batch);
        if (this.shapeUndoStack.size() > SHAPE_HISTORY_LIMIT) {
            this.shapeUndoStack.remove(0);
        }
        this.shapeRedoStack.clear();
    }

    private void updateAltShapeWheelLifecycle() {
        boolean altDown = isAltDown();
        double mouseX = currentMouseX();
        double mouseY = currentMouseY();

        if (altDown && !this.altShapeMenuHeld && shouldOpenAltShapeWheel(mouseY)) {
            this.altShapeMenuHeld = true;
            openShapeWheel(mouseX, mouseY);
            this.shapeWheelOpenedByAlt = true;
        }

        if (!altDown && this.altShapeMenuHeld) {
            this.altShapeMenuHeld = false;
            if (this.shapeWheelOpenedByAlt && this.shapeWheelOpen) {
                ClientRtsController.BuildShape picked = resolveShapeWheelOption(mouseX, mouseY);
                if (picked != null) {
                    this.controller.setBuildShape(picked);
                    ensureFillModeForShape(picked);
                    clearShapeBuildSession();
                }
                closeShapeWheel();
            }
            this.shapeWheelOpenedByAlt = false;
        }
    }

    private boolean shouldOpenAltShapeWheel(double mouseY) {
        return this.controller.isEnabled()
                && !this.shapeWheelOpen
                && !this.interactionWheelOpen
                && !this.guideOpen
                && isWorldArea(currentMouseX(), mouseY);
    }

    private boolean isAltDown() {
        if (this.minecraft == null) {
            return false;
        }
        long window = this.minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private double currentMouseX() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) {
            return 0.0D;
        }
        double guiX = this.minecraft.mouseHandler.xpos()
                * this.minecraft.getWindow().getGuiScaledWidth()
                / this.minecraft.getWindow().getScreenWidth();
        return guiX / currentRtsGuiRenderScale();
    }

    private double currentMouseY() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) {
            return 0.0D;
        }
        double guiY = this.minecraft.mouseHandler.ypos()
                * this.minecraft.getWindow().getGuiScaledHeight()
                / this.minecraft.getWindow().getScreenHeight();
        return guiY / currentRtsGuiRenderScale();
    }

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

    private boolean tryDirectToolInteraction() {
        InteractionTarget target = pickInteractionTarget(false);
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

    private boolean openInteractionWheel(double mouseX, double mouseY) {
        InteractionTarget target = pickInteractionTarget(false);
        if (target == null) {
            return false;
        }

        List<InteractionOption> options = buildInteractionOptions();
        if (options.isEmpty()) {
            return false;
        }

        this.interactionWheelOpen = true;
        this.interactionWheelTarget = target;
        this.interactionWheelOptions.clear();
        this.interactionWheelOptions.addAll(options);
        this.interactionWheelPage = 0;

        int minX = INTERACT_WHEEL_RADIUS + INTERACT_WHEEL_SLOT;
        int maxX = Math.max(minX, this.width - INTERACT_WHEEL_RADIUS - INTERACT_WHEEL_SLOT);
        int minY = TOP_H + INTERACT_WHEEL_RADIUS + INTERACT_WHEEL_SLOT;
        int maxY = Math.max(minY, getBottomY() - INTERACT_WHEEL_RADIUS - INTERACT_WHEEL_SLOT);
        this.interactionWheelCenterX = Mth.clamp((int) Math.round(mouseX), minX, maxX);
        this.interactionWheelCenterY = Mth.clamp((int) Math.round(mouseY), minY, maxY);
        return true;
    }

    private void closeInteractionWheel() {
        this.interactionWheelOpen = false;
        this.interactionWheelTarget = null;
        this.interactionWheelOptions.clear();
        this.interactionWheelPage = 0;
    }

    private void openShapeWheel(double mouseX, double mouseY) {
        this.shapeWheelOpen = true;
        closeInteractionWheel();
        int minX = SHAPE_WHEEL_RADIUS + SHAPE_WHEEL_SLOT;
        int maxX = Math.max(minX, this.width - SHAPE_WHEEL_RADIUS - SHAPE_WHEEL_SLOT);
        int minY = TOP_H + SHAPE_WHEEL_RADIUS + SHAPE_WHEEL_SLOT;
        int maxY = Math.max(minY, getBottomY() - SHAPE_WHEEL_RADIUS - SHAPE_WHEEL_SLOT);
        this.shapeWheelCenterX = Mth.clamp((int) Math.round(mouseX), minX, maxX);
        this.shapeWheelCenterY = Mth.clamp((int) Math.round(mouseY), minY, maxY);
    }

    private void closeShapeWheel() {
        this.shapeWheelOpen = false;
        this.shapeWheelOpenedByAlt = false;
    }

    private List<ShapeWheelSlot> collectShapeWheelSlots() {
        List<ShapeWheelSlot> slots = new ArrayList<>(6);
        ClientRtsController.BuildShape[] shapes = new ClientRtsController.BuildShape[] {
                ClientRtsController.BuildShape.BLOCK,
                ClientRtsController.BuildShape.LINE,
                ClientRtsController.BuildShape.SQUARE,
                ClientRtsController.BuildShape.WALL,
                ClientRtsController.BuildShape.CIRCLE,
                ClientRtsController.BuildShape.BOX
        };
        for (int i = 0; i < shapes.length; i++) {
            double angle = (-Math.PI / 2.0D) + ((Math.PI * 2.0D) * (i / (double) shapes.length));
            int cx = this.shapeWheelCenterX + (int) Math.round(Math.cos(angle) * SHAPE_WHEEL_RADIUS);
            int cy = this.shapeWheelCenterY + (int) Math.round(Math.sin(angle) * SHAPE_WHEEL_RADIUS);
            slots.add(new ShapeWheelSlot(
                    shapes[i],
                    cx - (SHAPE_WHEEL_SLOT / 2),
                    cy - (SHAPE_WHEEL_SLOT / 2)));
        }
        return slots;
    }

    private ClientRtsController.BuildShape resolveShapeWheelOption(double mouseX, double mouseY) {
        for (ShapeWheelSlot slot : collectShapeWheelSlots()) {
            if (inside(mouseX, mouseY, slot.x(), slot.y(), SHAPE_WHEEL_SLOT, SHAPE_WHEEL_SLOT)) {
                return slot.shape();
            }
        }
        return null;
    }

    private List<InteractionOption> buildInteractionOptions() {
        List<InteractionOption> options = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.player == null) {
            return options;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            options.add(new InteractionOption(
                    InteractionSource.TOOL_SLOT,
                    slot,
                    -1,
                    "",
                    stack.copy()));
        }

        int pinCount = this.controller.getQuickSlotCount();
        for (int pin = 0; pin < pinCount; pin++) {
            String itemId = this.controller.getQuickSlotItemId(pin);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ItemStack preview = this.controller.getQuickSlotPreview(pin);
            if (preview.isEmpty()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            options.add(new InteractionOption(
                    InteractionSource.PIN_ITEM,
                    -1,
                    pin,
                    itemId,
                    preview.copy()));
        }
        return options;
    }

    private InteractionTarget pickInteractionTarget(boolean includeFluidSource) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.getCameraEntity() == null) {
            return null;
        }

        Vec3 camPos = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        Vec3 to = camPos.add(dir.scale(128.0D));
        boolean includeFluid = includeFluidSource;

        HitResult blockRaw = this.minecraft.level.clip(new ClipContext(
                camPos,
                to,
                ClipContext.Block.OUTLINE,
                includeFluid ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE,
                this.minecraft.getCameraEntity()));
        BlockHitResult blockHit = blockRaw instanceof BlockHitResult bhr && blockRaw.getType() == HitResult.Type.BLOCK
                ? bhr
                : null;

        EntityHitResult entityHit = pickEntityHit(camPos, to, dir);
        double blockDist = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (entityHit != null && entityDist <= blockDist) {
            Entity entity = entityHit.getEntity();
            return new InteractionTarget(
                    entity.getId(),
                    entityHit.getLocation(),
                    null,
                    camPos,
                    dir);
        }
        if (blockHit != null) {
            return new InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    blockHit.getLocation(),
                    blockHit,
                    camPos,
                    dir);
        }
        BlockHitResult airShapeHit = tryCreateAirShapeHit(camPos, dir);
        if (airShapeHit != null) {
            return new InteractionTarget(
                    C2SRtsInteractPayload.NO_ENTITY,
                    airShapeHit.getLocation(),
                    airShapeHit,
                    camPos,
                    dir);
        }
        return null;
    }

    private EntityHitResult pickEntityHit(Vec3 camPos, Vec3 to, Vec3 dir) {
        Entity cameraEntity = this.minecraft != null ? this.minecraft.getCameraEntity() : null;
        if (cameraEntity == null || this.minecraft == null || this.minecraft.player == null) {
            return null;
        }
        AABB search = cameraEntity.getBoundingBox().expandTowards(dir.scale(128.0D)).inflate(1.0D);
        return ProjectileUtil.getEntityHitResult(
                cameraEntity,
                camPos,
                to,
                search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != this.minecraft.player,
                128.0D * 128.0D);
    }

    private void runInteractionOption(InteractionOption option) {
        if (option == null || this.interactionWheelTarget == null) {
            return;
        }
        if (option.source() == InteractionSource.TOOL_SLOT) {
            if (this.interactionWheelTarget.isEntityTarget()) {
                this.controller.interactEntityWithToolSlot(
                        this.interactionWheelTarget.entityId(),
                        this.interactionWheelTarget.hitLocation(),
                        option.toolSlot(),
                        this.interactionWheelTarget.rayOrigin(),
                        this.interactionWheelTarget.rayDir());
            } else if (this.interactionWheelTarget.blockHit() != null) {
                this.controller.interactBlockWithToolSlot(
                        this.interactionWheelTarget.blockHit(),
                        option.toolSlot(),
                        this.interactionWheelTarget.rayOrigin(),
                        this.interactionWheelTarget.rayDir());
            }
            return;
        }

        if (option.source() == InteractionSource.PIN_ITEM) {
            if (this.interactionWheelTarget.isEntityTarget()) {
                this.controller.interactEntityWithPinnedItem(
                        this.interactionWheelTarget.entityId(),
                        this.interactionWheelTarget.hitLocation(),
                        option.itemId(),
                        this.interactionWheelTarget.rayOrigin(),
                        this.interactionWheelTarget.rayDir());
            } else if (this.interactionWheelTarget.blockHit() != null) {
                this.controller.interactBlockWithPinnedItem(
                        this.interactionWheelTarget.blockHit(),
                        option.itemId(),
                        this.interactionWheelTarget.rayOrigin(),
                        this.interactionWheelTarget.rayDir());
            }
        }
    }

    private int getInteractionWheelPageCount() {
        if (this.interactionWheelOptions.isEmpty()) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(this.interactionWheelOptions.size() / (double) INTERACT_WHEEL_PAGE_SIZE));
    }

    private List<InteractionWheelSlot> collectInteractionWheelSlots() {
        List<InteractionWheelSlot> slots = new ArrayList<>();
        if (!this.interactionWheelOpen || this.interactionWheelOptions.isEmpty()) {
            return slots;
        }

        int pageCount = getInteractionWheelPageCount();
        this.interactionWheelPage = Mth.clamp(this.interactionWheelPage, 0, pageCount - 1);
        int from = this.interactionWheelPage * INTERACT_WHEEL_PAGE_SIZE;
        int to = Math.min(this.interactionWheelOptions.size(), from + INTERACT_WHEEL_PAGE_SIZE);
        int count = Math.max(0, to - from);
        if (count <= 0) {
            return slots;
        }

        for (int i = 0; i < count; i++) {
            double angle = (-Math.PI / 2.0D) + ((Math.PI * 2.0D) * (i / (double) count));
            int cx = this.interactionWheelCenterX + (int) Math.round(Math.cos(angle) * INTERACT_WHEEL_RADIUS);
            int cy = this.interactionWheelCenterY + (int) Math.round(Math.sin(angle) * INTERACT_WHEEL_RADIUS);
            slots.add(new InteractionWheelSlot(
                    this.interactionWheelOptions.get(from + i),
                    cx - INTERACT_WHEEL_SLOT_HALF,
                    cy - INTERACT_WHEEL_SLOT_HALF));
        }
        return slots;
    }

    private InteractionOption resolveInteractionWheelOption(double mouseX, double mouseY) {
        for (InteractionWheelSlot slot : collectInteractionWheelSlots()) {
            if (inside(mouseX, mouseY, slot.x(), slot.y(), INTERACT_WHEEL_SLOT, INTERACT_WHEEL_SLOT)) {
                return slot.option();
            }
        }
        return null;
    }

    private void renderInteractionWheel(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, TOP_H, this.width, getBottomY(), 0x66000000);

        int ringR = INTERACT_WHEEL_RADIUS + 16;
        g.fill(
                this.interactionWheelCenterX - ringR,
                this.interactionWheelCenterY - ringR,
                this.interactionWheelCenterX + ringR,
                this.interactionWheelCenterY + ringR,
                0x77151A22);
        g.fill(
                this.interactionWheelCenterX - 24,
                this.interactionWheelCenterY - 24,
                this.interactionWheelCenterX + 24,
                this.interactionWheelCenterY + 24,
                0xAA0C1118);

        List<InteractionWheelSlot> slots = collectInteractionWheelSlots();
        for (InteractionWheelSlot slot : slots) {
            int x = slot.x();
            int y = slot.y();
            boolean hover = inside(mouseX, mouseY, x, y, INTERACT_WHEEL_SLOT, INTERACT_WHEEL_SLOT);
            g.fill(x, y, x + INTERACT_WHEEL_SLOT, y + INTERACT_WHEEL_SLOT, hover ? 0xCC335369 : 0xAA1B232E);
            g.hLine(x, x + INTERACT_WHEEL_SLOT, y, 0xFF5B7085);
            g.hLine(x, x + INTERACT_WHEEL_SLOT, y + INTERACT_WHEEL_SLOT, 0xFF0C0F13);
            g.vLine(x, y, y + INTERACT_WHEEL_SLOT, 0xFF5B7085);
            g.vLine(x + INTERACT_WHEEL_SLOT, y, y + INTERACT_WHEEL_SLOT, 0xFF0C0F13);
            g.renderItem(slot.option().preview(), x + 1, y + 1);
            if (hover) {
                g.fill(x + 1, y + 1, x + INTERACT_WHEEL_SLOT - 1, y + INTERACT_WHEEL_SLOT - 1, 0x22FFFFFF);
            }
        }

        int pageCount = getInteractionWheelPageCount();
        String title = this.interactionWheelTarget != null && this.interactionWheelTarget.isEntityTarget()
                ? "Entity Interact"
                : "Block Interact";
        g.drawCenteredString(this.font, title, this.interactionWheelCenterX, this.interactionWheelCenterY - 10, 0xEAF5FF);
        g.drawCenteredString(
                this.font,
                (this.interactionWheelPage + 1) + "/" + pageCount,
                this.interactionWheelCenterX,
                this.interactionWheelCenterY + 2,
                0xA9C7E8);
        g.drawCenteredString(
                this.font,
                "LMB: use   RMB/Esc: cancel   Wheel: page",
                this.interactionWheelCenterX,
                this.interactionWheelCenterY + 30,
                0xB7CDE2);

        InteractionOption hover = resolveInteractionWheelOption(mouseX, mouseY);
        if (hover != null) {
            String sourceLabel = hover.source() == InteractionSource.TOOL_SLOT
                    ? "Tool " + (hover.toolSlot() + 1)
                    : "Pin " + (hover.pinIndex() + 1);
            g.drawCenteredString(
                    this.font,
                    trimToWidth(sourceLabel + " - " + hover.preview().getHoverName().getString(), 260),
                    this.interactionWheelCenterX,
                    this.interactionWheelCenterY + 42,
                    0xFFFFFF);
        }
    }

    private void renderShapeWheel(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, TOP_H, this.width, getBottomY(), 0x55000000);
        int ringR = SHAPE_WHEEL_RADIUS + 16;
        g.fill(
                this.shapeWheelCenterX - ringR,
                this.shapeWheelCenterY - ringR,
                this.shapeWheelCenterX + ringR,
                this.shapeWheelCenterY + ringR,
                0x77141920);
        g.fill(
                this.shapeWheelCenterX - 22,
                this.shapeWheelCenterY - 22,
                this.shapeWheelCenterX + 22,
                this.shapeWheelCenterY + 22,
                0xAA0B1016);

        for (ShapeWheelSlot slot : collectShapeWheelSlots()) {
            int x = slot.x();
            int y = slot.y();
            boolean hover = inside(mouseX, mouseY, x, y, SHAPE_WHEEL_SLOT, SHAPE_WHEEL_SLOT);
            boolean selected = slot.shape() == this.controller.getBuildShape();
            int bg = selected ? 0xCC3A6E57 : (hover ? 0xCC385065 : 0xAA1B232E);
            g.fill(x, y, x + SHAPE_WHEEL_SLOT, y + SHAPE_WHEEL_SLOT, bg);
            g.hLine(x, x + SHAPE_WHEEL_SLOT, y, 0xFF5B7085);
            g.hLine(x, x + SHAPE_WHEEL_SLOT, y + SHAPE_WHEEL_SLOT, 0xFF0C0F13);
            g.vLine(x, y, y + SHAPE_WHEEL_SLOT, 0xFF5B7085);
            g.vLine(x + SHAPE_WHEEL_SLOT, y, y + SHAPE_WHEEL_SLOT, 0xFF0C0F13);
            g.drawCenteredString(this.font, shapeShortLabel(slot.shape()), x + SHAPE_WHEEL_SLOT / 2, y + 7, 0xFFFFFF);
        }

        g.drawCenteredString(this.font, "Build Shape", this.shapeWheelCenterX, this.shapeWheelCenterY - 10, 0xEAF5FF);
        g.drawCenteredString(this.font, "ALT release/LMB: select   Wheel/1-5: cycle", this.shapeWheelCenterX, this.shapeWheelCenterY + 30, 0xB7CDE2);

        ClientRtsController.BuildShape hover = resolveShapeWheelOption(mouseX, mouseY);
        if (hover != null) {
            g.drawCenteredString(this.font, shapeLabel(hover), this.shapeWheelCenterX, this.shapeWheelCenterY + 42, 0xFFFFFF);
        }
    }

    private void renderGuidePanel(GuiGraphics g) {
        GuidePanelRect rect = guidePanelRect();
        int panelW = rect.w();
        int panelH = rect.h();
        int x = rect.x();
        int y = rect.y();
        int closeX = x + panelW - 20;

        g.fill(x, y, x + panelW, y + panelH, 0xEE151A21);
        g.hLine(x, x + panelW, y, 0xFF6E7C90);
        g.hLine(x, x + panelW, y + panelH, 0xFF0E1014);
        g.vLine(x, y, y + panelH, 0xFF6E7C90);
        g.vLine(x + panelW, y, y + panelH, 0xFF0E1014);

        g.drawString(this.font, guideTitle(), x + 12, y + 10, 0xFFFFFF);
        g.fill(closeX, y + 8, closeX + 12, y + 20, 0xAA2C3442);
        g.drawCenteredString(this.font, "X", closeX + 6, y + 11, 0xFFFFFF);

        GuideTopic[] topics = guideTopics();
        this.guidePage = Mth.clamp(this.guidePage, 0, Math.max(0, topics.length - 1));
        int tabX = x + 8;
        int tabY = y + 30;
        int tabW = guideTopicTabWidth();
        int topicAreaH = guideTopicAreaHeight(panelH);
        int visibleTopics = guideVisibleTopicRows(panelH);
        this.guideTopicScroll = Mth.clamp(this.guideTopicScroll, 0, Math.max(0, topics.length - visibleTopics));
        if (this.guidePage < this.guideTopicScroll) {
            this.guideTopicScroll = this.guidePage;
        } else if (this.guidePage >= this.guideTopicScroll + visibleTopics) {
            this.guideTopicScroll = Math.max(0, this.guidePage - visibleTopics + 1);
        }
        int topicEnd = Math.min(topics.length, this.guideTopicScroll + visibleTopics);
        for (int i = this.guideTopicScroll; i < topicEnd; i++) {
            int ty = tabY + (i - this.guideTopicScroll) * 22;
            boolean active = i == this.guidePage;
            int bg = active ? 0xCC355A71 : 0x88303A45;
            drawPanelFrame(g, tabX, ty, tabW, 18, bg, active ? 0xFF8FB4D0 : 0xFF4A5665, 0xFF0D1218);
            if (this.guideContext == GuideContext.BOTTOM) {
                String label = trimToWidth(Component.translatable(topics[i].titleKey()).getString(), tabW - 8);
                g.drawString(this.font, label, tabX + 4, ty + 5, active ? 0xFFF4FBFF : 0xFFB9C7D5, false);
            } else {
                drawGuideTopicIcon(g, topics[i].icon(), tabX + 10, ty + 9, active ? 0xFFF4FBFF : 0xFFB9C7D5);
            }
        }
        drawVerticalScrollbar(g, tabX + tabW + 3, tabY, topicAreaH, this.guideTopicScroll, topics.length, visibleTopics);

        int textX = x + tabW + 18;
        int lineY = y + 32;
        int maxTextW = guideTextMaxWidth(panelW, tabW);
        GuideTopic topic = topics[this.guidePage];
        g.drawString(this.font, trimToWidth(Component.translatable(topic.titleKey()).getString(), maxTextW), textX, lineY, 0xFFE7C46A);
        int bodyTop = lineY + 16;
        int bodyAreaH = guideTextAreaHeight(panelH);
        int visibleTextLines = guideVisibleTextLines(panelH);
        List<FormattedCharSequence> bodyLines = collectGuideTextLines(topic, maxTextW);
        this.guideTextScroll = Mth.clamp(this.guideTextScroll, 0, Math.max(0, bodyLines.size() - visibleTextLines));
        int lineEnd = Math.min(bodyLines.size(), this.guideTextScroll + visibleTextLines);
        enableRtsScissor(g, textX, bodyTop, textX + maxTextW, bodyTop + bodyAreaH);
        try {
            for (int i = this.guideTextScroll; i < lineEnd; i++) {
                g.drawString(this.font, bodyLines.get(i), textX, bodyTop + (i - this.guideTextScroll) * 12, 0xE6EDF8);
            }
        } finally {
            g.disableScissor();
        }
        drawVerticalScrollbar(g, x + panelW - 8, bodyTop, bodyAreaH, this.guideTextScroll, bodyLines.size(), visibleTextLines);
    }

    private boolean scrollGuidePanel(double mouseX, double mouseY, double scrollY) {
        if (scrollY == 0.0D) {
            return true;
        }
        GuidePanelRect rect = guidePanelRect();
        if (!isInsideGuidePanel(mouseX, mouseY)) {
            return true;
        }
        GuideTopic[] topics = guideTopics();
        int delta = scrollY > 0.0D ? -1 : 1;
        int tabX = rect.x() + 8;
        int tabY = rect.y() + 30;
        int tabW = guideTopicTabWidth();
        if (inside(mouseX, mouseY, tabX, tabY, tabW + 8, guideTopicAreaHeight(rect.h()))) {
            int visible = guideVisibleTopicRows(rect.h());
            this.guideTopicScroll = Mth.clamp(this.guideTopicScroll + delta, 0, Math.max(0, topics.length - visible));
            return true;
        }

        int maxTextW = guideTextMaxWidth(rect.w(), tabW);
        this.guidePage = Mth.clamp(this.guidePage, 0, Math.max(0, topics.length - 1));
        GuideTopic topic = topics[this.guidePage];
        int visible = guideVisibleTextLines(rect.h());
        int maxScroll = Math.max(0, collectGuideTextLines(topic, maxTextW).size() - visible);
        this.guideTextScroll = Mth.clamp(this.guideTextScroll + delta, 0, maxScroll);
        return true;
    }

    private List<FormattedCharSequence> collectGuideTextLines(GuideTopic topic, int maxTextW) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String key : topic.lineKeys()) {
            lines.addAll(this.font.split(Component.translatable(key), maxTextW));
        }
        return lines;
    }

    private int guideTopicAreaHeight(int panelH) {
        return Math.max(18, panelH - 42);
    }

    private int guideVisibleTopicRows(int panelH) {
        return Math.max(1, guideTopicAreaHeight(panelH) / 22);
    }

    private int guideTextAreaHeight(int panelH) {
        return Math.max(24, panelH - 62);
    }

    private int guideTextMaxWidth(int panelW, int tabW) {
        return Math.max(48, panelW - tabW - 42);
    }

    private int guideVisibleTextLines(int panelH) {
        return Math.max(1, guideTextAreaHeight(panelH) / 12);
    }

    private void drawVerticalScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int total, int visible) {
        if (total <= visible || h <= 0) {
            return;
        }
        int trackW = 3;
        int knobH = Math.max(10, h * visible / Math.max(visible + 1, total));
        int maxScroll = Math.max(1, total - visible);
        int knobY = y + (h - knobH) * Mth.clamp(scroll, 0, maxScroll) / maxScroll;
        g.fill(x, y, x + trackW, y + h, 0x55303A45);
        g.fill(x, knobY, x + trackW, knobY + knobH, 0xCC8FB4D0);
    }

    private void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
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

    private boolean isInsideGuidePanel(double mouseX, double mouseY) {
        GuidePanelRect rect = guidePanelRect();
        return inside(mouseX, mouseY, rect.x(), rect.y(), rect.w(), rect.h());
    }

    private boolean isInsideGuideClose(double mouseX, double mouseY) {
        GuidePanelRect rect = guidePanelRect();
        int closeX = rect.x() + rect.w() - 20;
        return inside(mouseX, mouseY, closeX, rect.y() + 8, 12, 12);
    }

    private boolean isInsideGuidePrev(double mouseX, double mouseY) {
        return false;
    }

    private boolean isInsideGuideNext(double mouseX, double mouseY) {
        return false;
    }

    private void drawButtonLike(GuiGraphics g, int x, int y, int w, int h, String label, boolean active) {
        int bg = active ? 0xCC334E63 : 0x66303A45;
        drawPanelFrame(g, x, y, w, h, bg, active ? 0xFF7B91A8 : 0xFF4A5665, 0xFF0D1218);
        g.drawCenteredString(this.font, label, x + w / 2, y + 4, active ? 0xF2F7FF : 0x8895A4);
    }

    private void openGuide(GuideContext context) {
        openGuide(context, -1, -1);
    }

    private void openGuide(GuideContext context, int anchorX, int anchorY) {
        this.guideOpen = true;
        this.guideContext = context;
        this.guidePage = 0;
        this.guideTopicScroll = 0;
        this.guideTextScroll = 0;
        this.guideAnchorX = anchorX;
        this.guideAnchorY = anchorY;
    }

    private Component guideTitle() {
        return switch (this.guideContext) {
            case BOTTOM -> Component.translatable("screen.rtsbuilding.guide.bottom.title");
            case SETTINGS -> Component.translatable("screen.rtsbuilding.guide.settings.title");
            default -> Component.translatable("screen.rtsbuilding.guide.top.title");
        };
    }

    private GuideTopic[] guideTopics() {
        return switch (this.guideContext) {
            case BOTTOM -> new GuideTopic[] {
                    topic(GuideIcon.SORT, "screen.rtsbuilding.guide.bottom.sort.title", "screen.rtsbuilding.guide.bottom.sort.1", "screen.rtsbuilding.guide.bottom.sort.2", "screen.rtsbuilding.guide.bottom.sort.3", "screen.rtsbuilding.guide.bottom.sort.4"),
                    topic(GuideIcon.CRAFT, "screen.rtsbuilding.guide.bottom.remote.title", "screen.rtsbuilding.guide.bottom.remote.1", "screen.rtsbuilding.guide.bottom.remote.2", "screen.rtsbuilding.guide.bottom.remote.3"),
                    topic(GuideIcon.GRID, "screen.rtsbuilding.guide.bottom.main.title", "screen.rtsbuilding.guide.bottom.main.1", "screen.rtsbuilding.guide.bottom.main.2", "screen.rtsbuilding.guide.bottom.main.3", "screen.rtsbuilding.guide.bottom.main.4"),
                    topic(GuideIcon.PIN, "screen.rtsbuilding.guide.bottom.recent_pins.title", "screen.rtsbuilding.guide.bottom.recent_pins.1", "screen.rtsbuilding.guide.bottom.recent_pins.2", "screen.rtsbuilding.guide.bottom.recent_pins.3"),
                    topic(GuideIcon.SEARCH, "screen.rtsbuilding.guide.bottom.craft_panel.title", "screen.rtsbuilding.guide.bottom.craft_panel.1", "screen.rtsbuilding.guide.bottom.craft_panel.2")
            };
            case SETTINGS -> new GuideTopic[] {
                    topic(GuideIcon.SLIDER, "screen.rtsbuilding.guide.settings.sensitivity.title", "screen.rtsbuilding.guide.settings.sensitivity.1", "screen.rtsbuilding.guide.settings.sensitivity.2"),
                    topic(GuideIcon.GRID, "screen.rtsbuilding.guide.settings.ui_scale.title", "screen.rtsbuilding.guide.settings.ui_scale.1", "screen.rtsbuilding.guide.settings.ui_scale.2"),
                    topic(GuideIcon.TOGGLE, "screen.rtsbuilding.guide.settings.autostore.title", "screen.rtsbuilding.guide.settings.autostore.1", "screen.rtsbuilding.guide.settings.autostore.2"),
                    topic(GuideIcon.TOGGLE, "screen.rtsbuilding.guide.settings.placed_recovery.title", "screen.rtsbuilding.guide.settings.placed_recovery.1", "screen.rtsbuilding.guide.settings.placed_recovery.2"),
                    topic(GuideIcon.GEAR, "screen.rtsbuilding.guide.settings.config.title", "screen.rtsbuilding.guide.settings.config.1", "screen.rtsbuilding.guide.settings.config.2")
            };
            default -> new GuideTopic[] {
                    topic(GuideIcon.HAND, "screen.rtsbuilding.guide.top.interact.title", "screen.rtsbuilding.guide.top.interact.1", "screen.rtsbuilding.guide.top.interact.2", "screen.rtsbuilding.guide.top.interact.3", "screen.rtsbuilding.guide.top.interact.4"),
                    topic(GuideIcon.GRID, "screen.rtsbuilding.guide.top.camera.title", "screen.rtsbuilding.guide.top.camera.1", "screen.rtsbuilding.guide.top.camera.2", "screen.rtsbuilding.guide.top.camera.3", "screen.rtsbuilding.guide.top.camera.4"),
                    topic(GuideIcon.LINK, "screen.rtsbuilding.guide.top.link.title", "screen.rtsbuilding.guide.top.link.1", "screen.rtsbuilding.guide.top.link.2"),
                    topic(GuideIcon.FUNNEL, "screen.rtsbuilding.guide.top.funnel.title", "screen.rtsbuilding.guide.top.funnel.1", "screen.rtsbuilding.guide.top.funnel.2"),
                    topic(GuideIcon.ROTATE, "screen.rtsbuilding.guide.top.rotate.title", "screen.rtsbuilding.guide.top.rotate.1"),
                    topic(GuideIcon.BUILD, "screen.rtsbuilding.guide.top.build.title", "screen.rtsbuilding.guide.top.build.1", "screen.rtsbuilding.guide.top.build.2", "screen.rtsbuilding.guide.top.build.3"),
                    topic(GuideIcon.PICKAXE, "screen.rtsbuilding.guide.top.ultimine.title", "screen.rtsbuilding.guide.top.ultimine.1", "screen.rtsbuilding.guide.top.ultimine.2"),
                    topic(GuideIcon.GRID, "screen.rtsbuilding.guide.top.chunk.title", "screen.rtsbuilding.guide.top.chunk.1")
            };
        };
    }

    private GuideTopic topic(GuideIcon icon, String titleKey, String... lineKeys) {
        return new GuideTopic(icon, titleKey, lineKeys);
    }

    private int getGuidePageCount() {
        return guideTopics().length;
    }

    private GuidePanelRect guidePanelRect() {
        int panelW = Math.min(330, Math.max(250, this.width - 28));
        int panelH = Math.min(178, Math.max(138, this.height - 90));
        int x;
        int y;
        if (this.guideContext == GuideContext.BOTTOM) {
            if (hasGuideAnchor()) {
                x = clampGuidePanelX(this.guideAnchorX - panelW + 20, panelW);
                y = clampGuidePanelY(this.guideAnchorY - panelH - 8, panelH);
            } else {
                x = Math.max(8, this.width - panelW - 8);
                y = Math.max(TOP_H + 6, getBottomY() - panelH - 6);
            }
        } else if (this.guideContext == GuideContext.SETTINGS) {
            int settingsW = Math.min(300, this.width - 24);
            int settingsX = (this.width - settingsW) / 2;
            int settingsY = (this.height - GEAR_MENU_H) / 2;
            int gap = 6;
            int leftSpace = Math.max(0, settingsX - 8 - gap);
            int rightSpace = Math.max(0, this.width - (settingsX + settingsW) - 8 - gap);
            if (leftSpace >= 230 || rightSpace >= 230) {
                boolean useLeft = leftSpace >= rightSpace;
                panelW = Math.min(330, useLeft ? leftSpace : rightSpace);
                x = useLeft ? settingsX - gap - panelW : settingsX + settingsW + gap;
                y = Mth.clamp(settingsY, 8, Math.max(8, this.height - panelH - 8));
            } else {
                panelW = Math.min(330, Math.max(220, this.width - 16));
                x = Math.max(8, (this.width - panelW) / 2);
                int belowY = settingsY + GEAR_MENU_H + gap;
                y = belowY + panelH <= this.height - 8
                        ? belowY
                        : Math.max(8, settingsY - panelH - gap);
            }
        } else {
            if (hasGuideAnchor()) {
                x = clampGuidePanelX(this.guideAnchorX - panelW / 2, panelW);
                y = clampGuidePanelY(this.guideAnchorY + 8, panelH);
            } else {
                x = 8;
                y = TOP_H + 6;
            }
        }
        return new GuidePanelRect(x, y, panelW, panelH);
    }

    private boolean hasGuideAnchor() {
        return this.guideAnchorX >= 0 && this.guideAnchorY >= 0;
    }

    private int clampGuidePanelX(int x, int panelW) {
        return Mth.clamp(x, 8, Math.max(8, this.width - panelW - 8));
    }

    private int clampGuidePanelY(int y, int panelH) {
        int minY = TOP_H + 6;
        return Mth.clamp(y, minY, Math.max(minY, this.height - panelH - 8));
    }

    private int resolveGuideTopicClick(double mouseX, double mouseY) {
        GuidePanelRect rect = guidePanelRect();
        GuideTopic[] topics = guideTopics();
        int tabX = rect.x() + 8;
        int tabY = rect.y() + 30;
        int tabW = guideTopicTabWidth();
        for (int i = 0; i < topics.length; i++) {
            if (inside(mouseX, mouseY, tabX, tabY + i * 22, tabW, 18)) {
                return i;
            }
        }
        return -1;
    }

    private int guideTopicTabWidth() {
        return this.guideContext == GuideContext.BOTTOM ? 92 : 20;
    }

    private void drawGuideTopicIcon(GuiGraphics g, GuideIcon icon, int cx, int cy, int color) {
        switch (icon) {
            case HAND -> drawGuideTextureIcon(g, TOPBAR_INTERACT_ACTIVE, cx, cy);
            case LINK -> drawGuideTextureIcon(g, TOPBAR_LINK_ACTIVE, cx, cy);
            case FUNNEL -> drawGuideTextureIcon(g, TOPBAR_FUNNEL_ACTIVE, cx, cy);
            case ROTATE -> drawGuideTextureIcon(g, TOPBAR_ROTATE_ACTIVE, cx, cy);
            case BUILD -> drawGuideTextureIcon(g, TOPBAR_QUICK_BUILD_ACTIVE, cx, cy);
            case PICKAXE -> drawGuideTextureIcon(g, TOPBAR_ULTIMINE_ACTIVE, cx, cy);
            case GRID -> drawGuideTextureIcon(g, TOPBAR_CHUNK_VIEW_ACTIVE, cx, cy);
            case SEARCH -> {
                g.fill(cx - 6, cy - 6, cx + 2, cy - 4, color);
                g.fill(cx - 6, cy + 1, cx + 2, cy + 3, color);
                g.fill(cx - 6, cy - 6, cx - 4, cy + 3, color);
                g.fill(cx + 1, cy - 6, cx + 3, cy + 3, color);
                g.fill(cx + 3, cy + 3, cx + 7, cy + 7, color);
            }
            case SORT -> {
                g.fill(cx - 7, cy - 7, cx - 2, cy - 5, color);
                g.fill(cx - 7, cy - 1, cx + 2, cy + 1, color);
                g.fill(cx - 7, cy + 5, cx + 7, cy + 7, color);
                g.fill(cx + 5, cy - 7, cx + 7, cy - 3, color);
                g.fill(cx + 3, cy - 4, cx + 9, cy - 2, color);
                g.fill(cx + 5, cy + 2, cx + 7, cy + 7, color);
                g.fill(cx + 3, cy + 1, cx + 9, cy + 3, color);
            }
            case CLOCK -> {
                g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0x331B222C);
                g.hLine(cx - 4, cx + 4, cy - 6, color);
                g.hLine(cx - 4, cx + 4, cy + 6, color);
                g.vLine(cx - 6, cy - 4, cy + 4, color);
                g.vLine(cx + 6, cy - 4, cy + 4, color);
                g.fill(cx, cy - 4, cx + 2, cy + 1, color);
                g.fill(cx, cy, cx + 5, cy + 2, color);
            }
            case DROPLET -> {
                g.fill(cx - 2, cy - 7, cx + 2, cy - 4, color);
                g.fill(cx - 5, cy - 3, cx + 5, cy + 5, color);
                g.fill(cx - 3, cy + 5, cx + 3, cy + 8, color);
            }
            case PIN -> {
                g.fill(cx - 4, cy - 7, cx + 4, cy - 5, color);
                g.fill(cx - 2, cy - 5, cx + 2, cy + 2, color);
                g.fill(cx - 5, cy + 1, cx + 5, cy + 3, color);
                g.fill(cx, cy + 3, cx + 1, cy + 8, color);
            }
            case CRAFT -> {
                g.fill(cx - 7, cy - 7, cx + 7, cy + 7, color);
                g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF1B222C);
                g.fill(cx - 1, cy - 7, cx + 1, cy + 7, 0xFF1B222C);
                g.fill(cx - 7, cy - 1, cx + 7, cy + 1, 0xFF1B222C);
            }
            case SLIDER -> {
                g.fill(cx - 7, cy - 4, cx + 7, cy - 2, color);
                g.fill(cx - 7, cy + 4, cx + 7, cy + 6, color);
                g.fill(cx - 2, cy - 7, cx + 2, cy + 1, color);
                g.fill(cx + 3, cy + 1, cx + 7, cy + 8, color);
            }
            case TOGGLE -> {
                g.fill(cx - 8, cy - 4, cx + 8, cy + 4, color);
                g.fill(cx + 1, cy - 7, cx + 7, cy + 7, 0xFF1B222C);
            }
            case GEAR -> drawGearIcon(g, cx, cy, color);
        }
    }

    private void drawGuideTextureIcon(GuiGraphics g, ResourceLocation texture, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(cx - 9, cy - 9, 0.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.blit(texture, 0, 0, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
        g.pose().popPose();
    }

    private String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(this.font, text, maxWidth);
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String storageCountDetail(long count) {
        return text(
                this.controller.isStorageLinked()
                        ? "screen.rtsbuilding.tooltip.count_storage"
                        : "screen.rtsbuilding.tooltip.count_inventory",
                compactCount(count));
    }

    private String selectedItemStatusLabel() {
        ItemStack preview = this.controller.getSelectedItemPreview();
        String label = this.controller.getSelectedItemLabel();
        if (preview != null && !preview.isEmpty() && preview.isDamageableItem()) {
            int max = preview.getMaxDamage();
            int durability = Math.max(0, max - preview.getDamageValue());
            return label + " " + durability + "/" + max;
        }
        return label;
    }

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

    private boolean hasMainHandItem() {
        return this.minecraft != null
                && this.minecraft.player != null
                && !this.minecraft.player.getMainHandItem().isEmpty();
    }

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

    private BlockHitResult pickBlockHit() {
        return pickBlockHit(false);
    }

    private BlockHitResult pickBlockHit(boolean includeFluidSource) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        Vec3 to = camPos.add(dir.scale(128.0D));
        ClipContext.Fluid fluidMode = includeFluidSource ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        HitResult hit = this.minecraft.level.clip(new ClipContext(camPos, to, ClipContext.Block.OUTLINE, fluidMode,
                this.minecraft.getCameraEntity()));
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        return tryCreateAirShapeHit(camPos, dir);
    }

    private BlockHitResult tryCreateAirShapeHit(Vec3 camPos, Vec3 dir) {
        if (camPos == null || dir == null) {
            return null;
        }
        if (this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK
                && (this.shapeBuildSession == null || this.shapeBuildSession.shape() == ClientRtsController.BuildShape.BLOCK)) {
            return null;
        }

        Direction face = resolveAirShapeFace(dir);
        Vec3 planeAnchor = resolveAirShapePlaneAnchor(face);
        if (face == null || planeAnchor == null) {
            return null;
        }

        double dirComponent = switch (face.getAxis()) {
            case X -> dir.x;
            case Y -> dir.y;
            case Z -> dir.z;
        };
        if (Math.abs(dirComponent) < 1.0E-5D) {
            return null;
        }

        double planeCoord = switch (face.getAxis()) {
            case X -> planeAnchor.x;
            case Y -> planeAnchor.y;
            case Z -> planeAnchor.z;
        };
        double originCoord = switch (face.getAxis()) {
            case X -> camPos.x;
            case Y -> camPos.y;
            case Z -> camPos.z;
        };
        double t = (planeCoord - originCoord) / dirComponent;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }

        Vec3 hitVec = camPos.add(dir.scale(t));
        return new BlockHitResult(hitVec, face, BlockPos.containing(hitVec), false);
    }

    private BlockHitResult tryCreateBlueprintAirHit() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null
                || this.minecraft.getCameraEntity() == null) {
            return null;
        }
        Vec3 camPos = this.minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 dir = computeCursorRayDirection();
        if (Math.abs(dir.y) < 1.0E-5D) {
            return null;
        }
        double planeY = this.minecraft.player.blockPosition().getY();
        double t = (planeY - camPos.y) / dir.y;
        if (t <= 0.0D || t > 128.0D) {
            return null;
        }
        Vec3 hitVec = camPos.add(dir.scale(t));
        return new BlockHitResult(hitVec, Direction.UP, BlockPos.containing(hitVec), false);
    }

    private Direction resolveAirShapeFace(Vec3 dir) {
        if (this.shapeBuildSession != null && this.shapeBuildSession.planeFace() != null) {
            return this.shapeBuildSession.planeFace();
        }
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.LINE
                || shape == ClientRtsController.BuildShape.SQUARE
                || shape == ClientRtsController.BuildShape.BOX) {
            return Direction.UP;
        }
        if (shape == ClientRtsController.BuildShape.WALL) {
            return Direction.UP;
        }
        return Direction.getNearest(-dir.x, -dir.y, -dir.z);
    }

    private Vec3 resolveAirShapePlaneAnchor(Direction face) {
        if (face == null || this.minecraft == null || this.minecraft.player == null) {
            return null;
        }
        if (this.shapeBuildSession != null) {
            if (this.shapeBuildSession.pointA() != null) {
                return Vec3.atCenterOf(this.shapeBuildSession.pointA());
            }
            if (this.shapeBuildSession.pointB() != null) {
                return Vec3.atCenterOf(this.shapeBuildSession.pointB());
            }
        }
        return Vec3.atCenterOf(this.minecraft.player.blockPosition());
    }

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

    private boolean shouldRenderFunnelCursor() {
        return this.controller.isEnabled()
                && this.controller.getMode() == BuilderMode.FUNNEL
                && this.controller.isFunnelEnabled()
                && !isSearchFocused()
                && !this.guideOpen
                && !this.interactionWheelOpen
                && !this.shapeWheelOpen;
    }

    private void updateNativeCursorVisibility(boolean hide) {
        if (this.minecraft == null) {
            this.nativeCursorHidden = false;
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        if (hide == this.nativeCursorHidden) {
            return;
        }
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, hide ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
        this.nativeCursorHidden = hide;
    }

    private Vec3 computeCursorRayDirection() {
        double mouseX = this.minecraft.mouseHandler.xpos();
        double mouseY = this.minecraft.mouseHandler.ypos();
        double width = Math.max(1.0D, this.minecraft.getWindow().getScreenWidth());
        double height = Math.max(1.0D, this.minecraft.getWindow().getScreenHeight());

        double nx = (mouseX / width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / height) * 2.0D;

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

        double fovY = Math.toRadians(this.minecraft.options.fov().get());
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / height);
        // Current yaw basis yields a left-vector here; invert X NDC to keep screen-right -> ray-right.
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static String sortLabel(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Qty";
            case MOD -> "Mod";
            case NAME -> "Name";
        };
    }

    private String fillModeLabel(ShapeFillMode mode) {
        if (mode == null) {
            return text("screen.rtsbuilding.fill.fill");
        }
        return switch (mode) {
            case FILL -> text("screen.rtsbuilding.fill.fill");
            case HOLLOW -> text("screen.rtsbuilding.fill.hollow");
            case SKELETON -> text("screen.rtsbuilding.fill.skeleton");
        };
    }

    private static String shapeDimensionLabel(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return "2D";
        }
        return switch (shape) {
            case LINE -> "1D";
            case BOX -> "3D";
            default -> "2D";
        };
    }

    private String currentShapeSizeText() {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return "1*1*1";
        }
        ShapeBuildInput input = resolveCurrentShapeBuildInput(pickBlockHit(), false);
        if (input == null) {
            return "0*0*0";
        }
        List<BlockPos> blocks = buildShapePositions(input, this.shapeFillMode);
        if (blocks.isEmpty()) {
            return "0*0*0";
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int sx = (maxX - minX) + 1;
        int sy = (maxY - minY) + 1;
        int sz = (maxZ - minZ) + 1;
        return sx + "*" + sy + "*" + sz;
    }

    private String currentShapeCostText() {
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            return "1";
        }
        ShapeBuildInput input = resolveCurrentShapeBuildInput(pickBlockHit(), false);
        if (input == null) {
            return "0";
        }
        List<BlockPos> blocks = filterOccupiedReadyShapeTargets(input, buildShapePositions(input, this.shapeFillMode));
        return Integer.toString(blocks.size());
    }

    private String pendingShapeStatusText() {
        ClientRtsController.BuildShape currentShape = this.controller.getBuildShape();
        if (currentShape == ClientRtsController.BuildShape.BLOCK) {
            return text("screen.rtsbuilding.shape_status.place");
        }
        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != currentShape) {
            return text("screen.rtsbuilding.shape_status.step_a");
        }
        return switch (this.shapeBuildSession.phase()) {
            case NEED_SECOND_POINT -> {
                BlockPos a = this.shapeBuildSession.pointA();
                yield text("screen.rtsbuilding.shape_status.step_b", a.getX(), a.getY(), a.getZ());
            }
            case NEED_THIRD_POINT -> text("screen.rtsbuilding.shape_status.step_height");
            case READY_CONFIRM -> currentShape == ClientRtsController.BuildShape.WALL
                    ? text("screen.rtsbuilding.shape_status.confirm_wall")
                    : text("screen.rtsbuilding.shape_status.confirm");
        };
    }

    private String shapeLabel(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return text("screen.rtsbuilding.shape.block");
        }
        return switch (shape) {
            case BLOCK -> text("screen.rtsbuilding.shape.block");
            case LINE -> text("screen.rtsbuilding.shape.line");
            case SQUARE -> text("screen.rtsbuilding.shape.square");
            case WALL -> text("screen.rtsbuilding.shape.wall");
            case CIRCLE -> text("screen.rtsbuilding.shape.circle");
            case BOX -> text("screen.rtsbuilding.shape.box");
        };
    }

    private static String shapeShortLabel(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return "B";
        }
        return switch (shape) {
            case BLOCK -> "B";
            case LINE -> "L";
            case SQUARE -> "SQ";
            case WALL -> "W";
            case CIRCLE -> "C";
            case BOX -> "CU";
        };
    }

    private static String formatModLabel(String modNamespace) {
        if ("minecraft".equals(modNamespace)) {
            return "Vanilla";
        }
        return humanizeToken(modNamespace);
    }

    private static String formatTabLabel(String tabKey) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(tabKey);
        String path = key == null ? tabKey : key.getPath();
        return humanizeToken(path);
    }

    private static String humanizeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String normalized = token.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == ' ') {
                sb.append(c);
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String compactCount(long value) {
        return RtsClientUiUtil.compactCount(value);
    }

    private static String compactFluidAmount(long milliBuckets) {
        return RtsClientUiUtil.compactFluidAmount(milliBuckets);
    }

    private String formatRecentAmount(ClientRtsController.RecentEntry entry) {
        if (entry == null) {
            return "";
        }
        long amount = this.controller.getRecentDisplayAmount(entry);
        return entry.fluid() ? compactFluidAmount(amount) : compactCount(amount);
    }

    private void drawSlotCountOverlay(GuiGraphics g, int slotX, int slotY, int box, String countText, int color) {
        RtsClientUiUtil.drawSlotCountOverlay(g, this.font, slotX, slotY, box, countText, color);
    }

    private TopAction topActionForMode() {
        return switch (this.controller.getMode()) {
            case INTERACT -> TopAction.INTERACT;
            case LINK_STORAGE -> TopAction.LINK;
            case FUNNEL -> TopAction.FUNNEL;
            case ROTATE -> TopAction.ROTATE;
            default -> TopAction.INTERACT;
        };
    }

    private record CategoryRow(
            String token,
            String label,
            int depth,
            boolean expandable,
            boolean expanded,
            String modNamespace) {
    }

    private record CategoryClick(
            String categoryToken,
            String modNamespace,
            boolean toggleExpandOnly) {
    }

    private enum InteractionSource {
        TOOL_SLOT,
        PIN_ITEM
    }

    private record InteractionOption(
            InteractionSource source,
            int toolSlot,
            int pinIndex,
            String itemId,
            ItemStack preview) {
    }

    private record InteractionWheelSlot(InteractionOption option, int x, int y) {
    }

    private record ShapeWheelSlot(ClientRtsController.BuildShape shape, int x, int y) {
    }

    private enum ShapeFillMode {
        FILL,
        HOLLOW,
        SKELETON
    }

    private enum GuideContext {
        TOP,
        BOTTOM,
        SETTINGS
    }

    private enum GuideIcon {
        HAND,
        LINK,
        FUNNEL,
        ROTATE,
        BUILD,
        PICKAXE,
        GRID,
        SEARCH,
        SORT,
        CLOCK,
        DROPLET,
        PIN,
        CRAFT,
        SLIDER,
        TOGGLE,
        GEAR
    }

    private record GuideTopic(GuideIcon icon, String titleKey, String[] lineKeys) {
    }

    private record GuidePanelRect(int x, int y, int w, int h) {
    }

    private record ShapeHistoryBatch(
            PlacementReplayKind replayKind,
            String itemId,
            int toolSlot,
            Direction face,
            List<BlockPos> positions) {
    }

    private enum PlacementReplayKind {
        PIN_ITEM,
        TOOL_SLOT
    }

    private enum ShapeBuildPhase {
        NEED_SECOND_POINT,
        NEED_THIRD_POINT,
        READY_CONFIRM
    }

    private record ShapeBuildSession(
            ClientRtsController.BuildShape shape,
            Direction planeFace,
            Direction placementFace,
            BlockPos pointA,
            BlockPos pointB,
            ShapeBuildPhase phase,
            int boxHeightOffset,
            double boxHeightMouseBaseY) {
    }

    private record ShapeBuildInput(
            ClientRtsController.BuildShape shape,
            Direction planeFace,
            Direction placementFace,
            BlockPos pointA,
            BlockPos pointB,
            int boxHeightOffset) {
    }

    private record RotatedOffset(int a, int b) {
    }

    private record PlaneCell(int a, int b) {
    }

    public record ShapeGhostPreview(List<BlockPos> blocks, boolean readyConfirm) {
        public static final ShapeGhostPreview EMPTY = new ShapeGhostPreview(List.of(), false);
    }

    public record BlueprintGhostPreview(List<BlueprintPanel.BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
        public static final BlueprintGhostPreview EMPTY = new BlueprintGhostPreview(List.of(), false, false);
    }

    private record InteractionTarget(
            int entityId,
            Vec3 hitLocation,
            BlockHitResult blockHit,
            Vec3 rayOrigin,
            Vec3 rayDir) {
        private boolean isEntityTarget() {
            return this.entityId >= 0;
        }
    }

    private record TopBarButtonSpec(
            TopBarButtonId id,
            String label,
            boolean iconOnly,
            boolean active,
            int baseWidth) {
    }

    private record TopBarButtonLayout(
            TopBarButtonId id,
            int x,
            int width,
            String label,
            boolean iconOnly,
            boolean active) {
    }

    private enum TopBarButtonId {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE,
        QUICK_BUILD,
        ULTIMINE,
        CHUNK_VIEW,
        DEBUG,
        GEAR,
        SENSITIVITY,
        AUTO_STORE,
        SHAPE,
        SHAPE_ROTATE,
        GUIDE,
        QUEST_DETECT
    }

    private enum TopAction {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE
    }
}



