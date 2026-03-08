package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

public final class BuilderScreen extends Screen {
    private static final int TOP_H = 52;
    private static final int DEFAULT_BOTTOM_H = 110;
    private static final int MIN_BOTTOM_H = 72;
    private static final int MAX_BOTTOM_H = 320;
    private static final int MIN_STORAGE_GRID_ROWS = 2;
    private static final int GRID_BOTTOM_PADDING = 4;
    private static final int SLOT = 22;
    private static final int HOTBAR_SLOT = 18;
    private static final int HOTBAR_PITCH = 20;
    private static final int TOOL_AREA_H = HOTBAR_SLOT;
    private static final int SEARCH_CLEAR_SIZE = 12;
    private static final int SORT_BUTTON_SIZE = 16;
    private static final int CRAFT_BUTTON_H = 14;
    private static final int CRAFT_PANEL_W = 106;
    private static final int CRAFT_PANEL_GAP = 6;
    private static final int CRAFT_PANEL_COLS = 4;
    private static final int CRAFT_PANEL_SLOT = 18;
    private static final int CRAFT_PANEL_PITCH = 20;
    private static final int CRAFT_PANEL_SEARCH_H = 12;
    private static final int CRAFT_PANEL_TOGGLE_W = 30;
    private static final long CRAFT_REPEAT_START_MS = 320L;
    private static final long CRAFT_REPEAT_MIN_MS = 75L;
    private static final long CRAFT_REPEAT_STEP_MS = 35L;
    private static final int CATEGORY_W = 124;
    private static final int CATEGORY_ROW_H = 11;
    private static final float CATEGORY_TEXT_SCALE = 0.84F;
    private static final int INTERACT_WHEEL_PAGE_SIZE = 10;
    private static final int INTERACT_WHEEL_RADIUS = 68;
    private static final int INTERACT_WHEEL_SLOT = 18;
    private static final int INTERACT_WHEEL_SLOT_HALF = INTERACT_WHEEL_SLOT / 2;
    private static final int TOP_BUTTON_GAP = 5;
    private static final int MIN_TOP_BUTTON_W = 28;
    private static final int TOP_ACTION_BUTTON_W = 74;
    private static final int TOP_SENS_BUTTON_W = 96;
    private static final int TOP_AUTOSTORE_BUTTON_W = 116;
    private static final int SHAPE_BUTTON_W = 112;
    private static final int SHAPE_ROTATE_BUTTON_W = 84;
    private static final int TOP_GUIDE_BUTTON_W = 78;
    private static final int QUEST_DETECT_BUTTON_W = 104;
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
    private static final int LEFT_SIDEBAR_TAB_W = 14;
    private static final int LEFT_SIDEBAR_TAB_H = 36;
    private static final int LEFT_SIDEBAR_PANEL_W = 132;
    private static final int LEFT_SIDEBAR_PANEL_H = 66;
    private static final int LEFT_SIDEBAR_BUTTON_W = 112;
    private static final int LEFT_SIDEBAR_BUTTON_H = 16;
    private static final int LEFT_SIDEBAR_Y = TOP_H + 8;
    private static final ItemStack FUNNEL_CURSOR_STACK = new ItemStack(Items.HOPPER);
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";

    private final ClientRtsController controller;

    private EditBox searchBox;
    private EditBox craftSearchBox;
    private int hoveredEntry = -1;
    private int hoveredFluidEntry = -1;
    private int hoveredCraftableEntry = -1;
    private int hoveredFunnelBufferEntry = -1;
    private int hoveredToolSlot = -1;
    private int hoveredPinIndex = -1;
    private boolean hoveredPinPageButton = false;
    private int categoryScroll = 0;
    private final Set<String> expandedCategoryMods = new HashSet<>();
    private int bottomPanelHeight = DEFAULT_BOTTOM_H;
    private boolean rightPressActive = false;
    private boolean rightDragRotated = false;
    private double rightDragDistance = 0.0D;
    private boolean leftMiningActive = false;
    private boolean guideOpen = false;
    private int pinPage = 0;
    private int craftScroll = 0;
    private int lastCraftablesStorageRevision = -1;
    private String heldCraftRecipeId = "";
    private long nextCraftRepeatMs;
    private long craftRepeatDelayMs = CRAFT_REPEAT_START_MS;
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
    private ShapeBuildSession shapeBuildSession;
    private int shapeFootprintNudgeA = 0;
    private int shapeFootprintNudgeB = 0;
    private ShapeFillMode shapeFillMode = ShapeFillMode.FILL;
    private int shapeRotateDegrees = 0;
    private boolean shapeWheelOpenedByAlt = false;
    private boolean altShapeMenuHeld = false;
    private boolean leftSidebarExpanded = false;
    private final List<ShapeHistoryBatch> shapeUndoStack = new ArrayList<>();
    private final List<ShapeHistoryBatch> shapeRedoStack = new ArrayList<>();

    public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
    }

    @Override
    protected void init() {
        super.init();
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
        this.craftSearchBox.setValue(this.controller.getCraftablesSearch());
        this.craftSearchBox.setResponder(this.controller::setCraftablesSearch);
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
        this.altShapeMenuHeld = false;
        this.funnelHotkeyHeld = false;
        this.controller.abortMining(getSelectedToolSlot());
        this.leftMiningActive = false;
        if (this.controller.isFunnelEnabled()) {
            this.controller.setFunnelEnabled(false);
        }
        if (this.controller.isEnabled()) {
            PacketDistributor.sendToServer(new C2SRtsToggleCameraPayload());
        }
        this.heldCraftRecipeId = "";
        updateNativeCursorVisibility(false);
    }

    @Override
    public void removed() {
        super.removed();
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
        tickCraftRepeat();
        if (!this.leftMiningActive) {
            return;
        }
        if (this.minecraft == null || !this.controller.isEnabled()) {
            this.leftMiningActive = false;
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            this.leftMiningActive = false;
            this.controller.abortMining(getSelectedToolSlot());
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.shapeWheelOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ClientRtsController.BuildShape picked = resolveShapeWheelOption(mouseX, mouseY);
                if (picked != null) {
                    this.controller.setBuildShape(picked);
                    ensureFillModeForShape(picked);
                    clearShapeBuildSession();
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
                if (!isInsideGuidePanel(mouseX, mouseY) || isInsideGuideClose(mouseX, mouseY)) {
                    this.guideOpen = false;
                }
                return true;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleShapeContextPanelClick(mouseX, mouseY)) {
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

            if (isWorldArea(mouseY) && this.controller.getMode() == BuilderMode.LINK_STORAGE) {
                BlockHitResult hit = pickBlockHit();
                if (hit != null) {
                    this.controller.linkStorage(hit.getBlockPos());
                    return true;
                }
            }

            if (isWorldArea(mouseY)
                    && this.controller.getMode() != BuilderMode.LINK_STORAGE
                    && this.controller.getMode() != BuilderMode.FUNNEL) {
                BlockHitResult hit = pickBlockHit();
                if (hit != null) {
                    this.controller.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(), getSelectedToolSlot());
                    this.leftMiningActive = true;
                    return true;
                }
            }

        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (isSearchFocused()) {
                blurSearchFocus();
            }
            if (mouseY >= getBottomY()) {
                return handleBottomPanelRightClick(mouseX, mouseY);
            }
            if (isWorldArea(mouseY)) {
                this.rightPressActive = true;
                this.rightDragRotated = false;
                this.rightDragDistance = 0.0D;
                return true;
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            clearShapeBuildSession();
            this.controller.clearSelectedItem();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.shapeWheelOpen) {
            return true;
        }

        if (this.interactionWheelOpen) {
            return true;
        }

        if (this.guideOpen) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.leftMiningActive) {
                this.leftMiningActive = false;
                this.controller.abortMining(getSelectedToolSlot());
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            stopCraftHold();
            if (!this.rightPressActive) {
                return true;
            }

            this.rightPressActive = false;
            if (this.rightDragRotated) {
                this.rightDragRotated = false;
                this.rightDragDistance = 0.0D;
                return true;
            }

            if (!isWorldArea(mouseY)) {
                return true;
            }

            if (this.controller.getMode() == BuilderMode.LINK_STORAGE || this.controller.getMode() == BuilderMode.FUNNEL) {
                clearShapeBuildSession();
                return true;
            }

            boolean wheelRequested = isWheelModifierDown();
            if (wheelRequested) {
                openInteractionWheel(mouseX, mouseY);
                return true;
            }
            boolean forcePlace = hasShiftDown();
            if (tryConfirmPendingShapeBuild(forcePlace)) {
                return true;
            }
            InteractionTarget target = pickInteractionTarget(false);
            if (target == null) {
                return true;
            }

            if (this.controller.hasSelectedFluid()) {
                clearShapeBuildSession();
                if (target.blockHit() != null) {
                    this.controller.placeSelectedFluid(target.blockHit(), forcePlace, target.rayOrigin(), target.rayDir());
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
                        PlacementReplayKind.TOOL_SLOT,
                        "",
                        getSelectedToolSlot());
                return true;
            }

            clearShapeBuildSession();
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

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.shapeWheelOpen) {
            return true;
        }

        if (this.interactionWheelOpen) {
            return true;
        }

        if (this.guideOpen) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.rightPressActive && isWorldArea(mouseY) && !isAltDown()) {
            this.rightDragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (this.rightDragDistance > 1.5D) {
                this.rightDragRotated = true;
            }
            this.controller.queueRotateDrag(dragX, dragY);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && isWorldArea(mouseY)) {
            this.controller.queuePanDrag(dragX, dragY);
            return true;
        }

        if (isSearchFocused()) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.shapeWheelOpen) {
            if (scrollY > 0.0D) {
                this.controller.cycleBuildShape(-1);
            } else if (scrollY < 0.0D) {
                this.controller.cycleBuildShape(1);
            }
            ensureFillModeForShape(this.controller.getBuildShape());
            clearShapeBuildSession();
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
            return true;
        }

        if (mouseY >= getBottomY()) {
            int bottomY = getBottomY();
            int bottomH = getBottomHeight();
            int sortX = 8;
            int categoryX = sortX + 58;
            int storageX = categoryX + CATEGORY_W + 10;
            int storageW = Math.max(120, this.width - storageX - 8);
            int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
            int craftPanelY = bottomY + 21 + TOOL_AREA_H + 4;
            int craftPanelH = Math.max(SLOT, bottomH - (craftPanelY - bottomY) - 4);
            if (inside(mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
                int visibleRows = Math.max(1, (craftPanelH - (CRAFT_PANEL_SEARCH_H + 21)) / CRAFT_PANEL_PITCH);
                int totalRows = Math.max(1, (int) Math.ceil(this.controller.getCraftableEntries().size() / (double) CRAFT_PANEL_COLS));
                int maxScroll = Math.max(0, totalRows - visibleRows);
                int delta = scrollY > 0.0D ? -1 : 1;
                this.craftScroll = Mth.clamp(this.craftScroll + delta, 0, maxScroll);
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
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_2) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.LINE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_3) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.SQUARE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_4) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.WALL);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_5) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.CIRCLE);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_6) {
                this.controller.setBuildShape(ClientRtsController.BuildShape.BOX);
                ensureFillModeForShape(this.controller.getBuildShape());
                clearShapeBuildSession();
                closeShapeWheel();
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

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            return undoLastPlacementBatch();
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            return redoLastPlacementBatch();
        }

        if (!isSearchFocused()
                && this.controller.getBuildShape() == ClientRtsController.BuildShape.BOX
                && (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int delta = keyCode == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1;
            if (isAltDown()) {
                delta *= 4;
            }
            if (adjustBoxHeightNudge(delta)) {
                return true;
            }
        }

        if (!isSearchFocused() && keyCode == GLFW.GLFW_KEY_F) {
            activateFunnelHotkey();
            this.funnelHotkeyHeld = true;
            return true;
        }
        if (!isSearchFocused() && keyCode == GLFW.GLFW_KEY_Q) {
            quickDropSelectedAtCursor();
            return true;
        }
        if (!isSearchFocused() && keyCode == GLFW.GLFW_KEY_R && !hasControlDown()) {
            rotateShapeByStep(hasShiftDown() ? -1 : 1);
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
                this.craftSearchBox.setValue("");
                this.controller.setCraftablesSearch("");
                blurSearchFocus();
                return true;
            }
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused() && this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            this.controller.setStorageSearch(this.searchBox.getValue());
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused() && this.craftSearchBox.keyPressed(keyCode, scanCode, modifiers)) {
            this.controller.setCraftablesSearch(this.craftSearchBox.getValue());
            return true;
        }

        if (!isSearchFocused() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int slot = keyCode - GLFW.GLFW_KEY_1;
            setSelectedToolSlot(slot);
            this.controller.clearPlacementSelectionPreserveMode();
            return true;
        }

        if (!isSearchFocused() && keyCode == GLFW.GLFW_KEY_A) {
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

        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            this.controller.decreaseRotateSensitivity();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            this.controller.increaseRotateSensitivity();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && this.funnelHotkeyHeld) {
            this.funnelHotkeyHeld = false;
            deactivateFunnelHotkey();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused() && this.searchBox.charTyped(codePoint, modifiers)) {
            this.controller.setStorageSearch(this.searchBox.getValue());
            return true;
        }
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused() && this.craftSearchBox.charTyped(codePoint, modifiers)) {
            this.controller.setCraftablesSearch(this.craftSearchBox.getValue());
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.hoveredEntry = -1;
        this.hoveredFluidEntry = -1;
        this.hoveredCraftableEntry = -1;
        this.hoveredFunnelBufferEntry = -1;
        this.hoveredToolSlot = -1;
        this.hoveredPinIndex = -1;
        this.hoveredPinPageButton = false;

        guiGraphics.fill(0, 0, this.width, TOP_H, 0xC0101116);
        guiGraphics.fill(0, getBottomY(), this.width, this.height, 0xD014151A);

        renderTopBar(guiGraphics);
        renderBottomPanel(guiGraphics, mouseX, mouseY, partialTick);
        renderShapeContextPanel(guiGraphics, mouseX, mouseY);
        renderFunnelBufferPanel(guiGraphics, mouseX, mouseY);

        if (this.hoveredEntry >= 0 && this.hoveredEntry < this.controller.getStorageEntries().size()) {
            var entry = this.controller.getStorageEntries().get(this.hoveredEntry);
            guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            guiGraphics.drawString(this.font, "x" + entry.count(), mouseX + 10, mouseY + 18, 0xFFFFAA);
        }

        if (this.hoveredFluidEntry >= 0 && this.hoveredFluidEntry < this.controller.getFluidEntries().size()) {
            var fluid = this.controller.getFluidEntries().get(this.hoveredFluidEntry);
            if (!fluid.preview().isEmpty()) {
                guiGraphics.renderTooltip(this.font, fluid.preview(), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal(fluid.label()), mouseX, mouseY);
            }
            guiGraphics.drawString(
                    this.font,
                    compactFluidAmount(fluid.amount()) + " / " + compactFluidAmount(fluid.capacity()),
                    mouseX + 10,
                    mouseY + 18,
                    0xFFDFAE);
        }

        if (this.hoveredCraftableEntry >= 0 && this.hoveredCraftableEntry < this.controller.getCraftableEntries().size()) {
            var entry = this.controller.getCraftableEntries().get(this.hoveredCraftableEntry);
            guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            String detail = entry.craftable()
                    ? "Right click: craft to linked storage"
                    : entry.missingSummary();
            if (detail != null && !detail.isBlank()) {
                guiGraphics.drawString(this.font, detail, mouseX + 10, mouseY + 18, entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
            }
        }

        if (this.hoveredFunnelBufferEntry >= 0 && this.hoveredFunnelBufferEntry < this.controller.getFunnelBufferEntries().size()) {
            var entry = this.controller.getFunnelBufferEntries().get(this.hoveredFunnelBufferEntry);
            guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            guiGraphics.drawString(this.font, "Buffered x" + entry.count(), mouseX + 10, mouseY + 18, 0xFFD8B8);
        }

        boolean funnelCursor = shouldRenderFunnelCursor();
        updateNativeCursorVisibility(funnelCursor);
        if (funnelCursor) {
            guiGraphics.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
        } else {
            ItemStack cursorPreview = resolveCursorPreview();
            if (!cursorPreview.isEmpty() && !isSearchFocused() && !this.guideOpen && !this.interactionWheelOpen
                    && !this.shapeWheelOpen) {
                guiGraphics.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
            }
        }

        renderCraftFeedback(guiGraphics, mouseX, mouseY);

        if (this.interactionWheelOpen) {
            renderInteractionWheel(guiGraphics, mouseX, mouseY);
        }

        if (this.shapeWheelOpen) {
            renderShapeWheel(guiGraphics, mouseX, mouseY);
        }

        if (this.guideOpen) {
            renderGuidePanel(guiGraphics);
        }
    }

    private void renderTopBar(GuiGraphics g) {
        ensureFillModeForShape(this.controller.getBuildShape());
        for (TopBarButtonLayout button : buildTopBarButtonLayouts()) {
            drawTopButtonSized(g, button.x(), button.label(), button.active(), button.width());
        }

        String modeText = switch (this.controller.getMode()) {
            case INTERACT -> "Mode: Interact";
            case LINK_STORAGE -> "Mode: Link Storage";
            case FUNNEL -> "Mode: Funnel";
            case SELECT_PAN -> "Mode: Camera";
            case ROTATE -> "Mode: Rotate";
            default -> "Mode: Idle";
        };

        String linked = this.controller.isStorageLinked()
                ? "Linked Storage: " + this.controller.getLinkedStorageName()
                : "Linked Storage: Not linked";
        String selected;
        if (this.controller.hasSelectedFluid()) {
            selected = "Selected Fluid: " + this.controller.getSelectedFluidLabel();
        } else if (!this.controller.getSelectedItemLabel().isEmpty()) {
            selected = "Selected Placement Item: " + this.controller.getSelectedItemLabel();
        } else {
            selected = "Selected Placement Item: None";
        }
        String row1 = modeText + "    " + selected;
        String row2 = linked + (this.controller.isAutoStoreMinedDrops()
                ? "    Mined drops are auto-stored"
                : "    Mined drops are not auto-stored")
                + (this.controller.isFunnelEnabled() ? "    Funnel: ON" : "    Funnel: OFF")
                + "    Shape: " + shapeLabel(this.controller.getBuildShape())
                + "    Fill: " + fillModeLabel(this.shapeFillMode)
                + "    Rot: " + this.shapeRotateDegrees + "deg"
                + "    Undo/Redo: " + this.shapeUndoStack.size() + "/" + this.shapeRedoStack.size()
                + "    " + pendingShapeStatusText();

        int statusX = 8;
        int statusW = Math.max(40, this.width - 16);
        g.drawString(this.font, trimToWidth(row1, statusW), statusX, 29, 0xF0F0F0);
        g.drawString(this.font, trimToWidth(row2, statusW), statusX, 40,
                this.controller.isStorageLinked() ? 0xB8FFB8 : 0xFFD8AE);
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
    private void drawTopButtonSized(GuiGraphics g, int x, String label, boolean active, int w) {
        int y = 4;
        int h = 20;
        int bg = active ? 0xFF2E6A50 : 0xAA1F2329;
        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, 0xFF5B6673);
        g.hLine(x, x + w, y + h, 0xFF0D0E10);
        g.vLine(x, y, y + h, 0xFF5B6673);
        g.vLine(x + w, y, y + h, 0xFF0D0E10);
        g.drawCenteredString(this.font, trimToWidth(label, Math.max(6, w - 8)), x + w / 2, y + 6, 0xFFFFFF);
    }

    private List<TopBarButtonLayout> buildTopBarButtonLayouts() {
        List<TopBarButtonSpec> specs = List.of(
                new TopBarButtonSpec(TopBarButtonId.INTERACT, "INTERACT", topActionForMode() == TopAction.INTERACT, TOP_ACTION_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.LINK, "LINK", topActionForMode() == TopAction.LINK, TOP_ACTION_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.FUNNEL, "FUNNEL", topActionForMode() == TopAction.FUNNEL, TOP_ACTION_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.ROTATE, "ROTATE", topActionForMode() == TopAction.ROTATE, TOP_ACTION_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.SENSITIVITY, "SENS " + this.controller.getInputSensitivityLabel(), false, TOP_SENS_BUTTON_W),
                new TopBarButtonSpec(
                        TopBarButtonId.AUTO_STORE,
                        this.controller.isAutoStoreMinedDrops() ? "AUTO STORE: ON" : "AUTO STORE: OFF",
                        this.controller.isAutoStoreMinedDrops(),
                        TOP_AUTOSTORE_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.SHAPE, "SHAPE: " + shapeLabel(this.controller.getBuildShape()), this.shapeWheelOpen, SHAPE_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.SHAPE_ROTATE, "ROT: " + this.shapeRotateDegrees + "deg", false, SHAPE_ROTATE_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.GUIDE, "GUIDE", this.guideOpen, TOP_GUIDE_BUTTON_W),
                new TopBarButtonSpec(TopBarButtonId.QUEST_DETECT, "QUEST DETECT", false, QUEST_DETECT_BUTTON_W));

        int gapsTotal = Math.max(0, specs.size() - 1) * TOP_BUTTON_GAP;
        int buttonsBaseWidth = 0;
        for (TopBarButtonSpec spec : specs) {
            buttonsBaseWidth += spec.baseWidth();
        }

        int availableWidth = Math.max(80, this.width - 16);
        int availableForButtons = Math.max(MIN_TOP_BUTTON_W, availableWidth - gapsTotal);
        int minButtonWidth = Math.max(12, Math.min(MIN_TOP_BUTTON_W, availableForButtons / Math.max(1, specs.size())));
        double widthScale = Math.min(1.0D, availableForButtons / (double) Math.max(1, buttonsBaseWidth));

        List<TopBarButtonLayout> layouts = new ArrayList<>(specs.size());
        int x = 8;
        int remainingWidth = availableForButtons;
        for (int i = 0; i < specs.size(); i++) {
            TopBarButtonSpec spec = specs.get(i);
            int remainingButtons = specs.size() - i - 1;
            int minReserved = remainingButtons * minButtonWidth;
            int width = i == specs.size() - 1
                    ? Math.max(minButtonWidth, remainingWidth)
                    : Math.max(minButtonWidth, (int) Math.round(spec.baseWidth() * widthScale));
            if (remainingButtons > 0) {
                width = Math.min(width, Math.max(minButtonWidth, remainingWidth - minReserved));
            }
            layouts.add(new TopBarButtonLayout(spec.id(), x, width, spec.label(), spec.active()));
            remainingWidth -= width;
            x += width + TOP_BUTTON_GAP;
        }
        return layouts;
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
        int bottomH = getBottomHeight();
        int bottomY = getBottomY();

        int sortX = 8;
        int sortY = bottomY + 6;

        drawSortButton(g, sortX, sortY, "S");
        drawSortButton(g, sortX, sortY + SORT_BUTTON_SIZE + 4, this.controller.isStorageSortAscending() ? "A" : "D");
        g.drawString(this.font, sortLabel(this.controller.getStorageSort()), sortX + SORT_BUTTON_SIZE + 4, sortY + 6, 0xFFFFFF);
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY, "+");
        drawSortButton(g, sortX + SORT_BUTTON_SIZE + 26, sortY + SORT_BUTTON_SIZE + 4, "-");
        drawCraftButton(g, sortX, sortY + (SORT_BUTTON_SIZE + 4) * 2 + 2, SORT_BUTTON_SIZE * 2 + 26, CRAFT_BUTTON_H);

        int categoryX = sortX + 58;
        int categoryY = bottomY + 4;
        int categoryH = bottomH - 16;
        drawCategoryPanel(g, mouseX, mouseY, categoryX, categoryY, CATEGORY_W, categoryH);

        int storageX = categoryX + CATEGORY_W + 10;
        int storageY = bottomY + 4;
        int storageW = Math.max(120, this.width - storageX - 8);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int craftPanelY = storageY + 17 + TOOL_AREA_H + 4;
        int craftPanelH = Math.max(SLOT, bottomH - (craftPanelY - bottomY) - 4);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(80, storageW - 102);
        int searchFieldW = computeSearchFieldWidth(searchW);

        if (this.searchBox != null) {
            this.searchBox.setX(storageX);
            this.searchBox.setY(storageY);
            this.searchBox.setWidth(searchFieldW);
            this.searchBox.setHeight(14);
            this.searchBox.render(g, mouseX, mouseY, partialTick);
            drawSearchClearButton(g, storageX, storageY, searchW);
        }

        int pagerX = storageX + searchW + 4;
        drawPager(g, pagerX, storageY);

        int toolY = storageY + 17;
        renderToolArea(g, mouseX, mouseY, storageX, toolY, mainStorageW);

        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, bottomH - (gridY - bottomY) - 4);
        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = storageX;
        int itemGridW = mainStorageW;
        if (fluidW > 0) {
            drawFluidGrid(g, mouseX, mouseY, storageX, gridY, fluidW, gridH);
            itemGridX = storageX + fluidW + 4;
            itemGridW = Math.max(SLOT, mainStorageW - fluidW - 4);
        }
        drawStorageGrid(g, mouseX, mouseY, itemGridX, gridY, itemGridW, gridH);
        renderCraftablesPanel(g, mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH, partialTick);
    }

    private void renderToolArea(GuiGraphics g, int mouseX, int mouseY, int storageX, int rowY, int storageW) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int hotbarX = storageX;
        int hotbarW = getHotbarSlotsWidth();
        int selected = (this.controller.hasSelectedItem() || this.controller.hasSelectedFluid()) ? -1 : getSelectedToolSlot();

        for (int i = 0; i < 9; i++) {
            int cx = hotbarX + i * HOTBAR_PITCH;
            int cy = rowY;
            int bg = i == selected ? 0xCC3A6E57 : 0xAA1B1E25;
            g.fill(cx, cy, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, bg);
            g.hLine(cx, cx + HOTBAR_SLOT, cy, 0xFF5E6874);
            g.hLine(cx, cx + HOTBAR_SLOT, cy + HOTBAR_SLOT, 0xFF0C0D10);
            g.vLine(cx, cy, cy + HOTBAR_SLOT, 0xFF5E6874);
            g.vLine(cx + HOTBAR_SLOT, cy, cy + HOTBAR_SLOT, 0xFF0C0D10);

            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, cx + 1, cy + 1);
                g.renderItemDecorations(this.font, stack, cx + 1, cy + 1);
            }
            if (mouseX >= cx && mouseX <= cx + HOTBAR_SLOT && mouseY >= cy && mouseY <= cy + HOTBAR_SLOT) {
                g.fill(cx + 1, cy + 1, cx + HOTBAR_SLOT - 1, cy + HOTBAR_SLOT - 1, 0x22FFFFFF);
                this.hoveredToolSlot = i;
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

    private void drawSortButton(GuiGraphics g, int x, int y, String label) {
        g.fill(x, y, x + SORT_BUTTON_SIZE, y + SORT_BUTTON_SIZE, 0xAA29323D);
        g.drawCenteredString(this.font, label, x + SORT_BUTTON_SIZE / 2, y + 4, 0xFFFFFF);
    }

    private void drawCraftButton(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xAA2A3340);
        g.hLine(x, x + w, y, 0xFF5F748A);
        g.hLine(x, x + w, y + h, 0xFF11151B);
        g.vLine(x, y, y + h, 0xFF5F748A);
        g.vLine(x + w, y, y + h, 0xFF11151B);
        g.drawCenteredString(this.font, "CRAFT", x + w / 2, y + 3, 0xFFFFFF);
    }

    private void drawCategoryPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0x8820222A);
        g.drawCenteredString(this.font, "Category", x + width / 2, y + 2, 0xFFFFFF);

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
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.hLine(x, x + w, y, light);
        guiGraphics.hLine(x, x + w, y + h, dark);
        guiGraphics.vLine(x, y, y + h, light);
        guiGraphics.vLine(x + w, y, y + h, dark);
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
                boolean selected = entry.itemId().equals(this.controller.getSelectedItemId());
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

    private void renderCraftablesPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height, float partialTick) {
        syncCraftSearchValueFromController();

        drawPanelFrame(g, x, y, width, height, 0xAA141922, 0xFF637993, 0xFF0D1218);
        g.drawString(this.font, "Craft", x + 5, y + 4, 0xEAF2FF);

        int searchX = x + 4;
        int searchY = y + 15;
        int searchW = Math.max(34, width - CRAFT_PANEL_TOGGLE_W - 12);
        int toggleX = searchX + searchW + 4;
        int toggleY = searchY;
        int toggleBg = this.controller.isCraftablesShowUnavailable() ? 0xAA5A3D2A : 0xAA2C5A41;

        drawPanelFrame(g, searchX, searchY, searchW, CRAFT_PANEL_SEARCH_H, 0xAA1E2731, 0xFF5E738A, 0xFF111921);
        if (this.craftSearchBox != null) {
            this.craftSearchBox.setX(searchX + 2);
            this.craftSearchBox.setY(searchY + 2);
            this.craftSearchBox.setWidth(Math.max(22, searchW - 4));
            this.craftSearchBox.setHeight(8);
            this.craftSearchBox.render(g, mouseX, mouseY, partialTick);
        }

        drawPanelFrame(g, toggleX, toggleY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H, toggleBg, 0xFF667D95, 0xFF111821);
        g.drawCenteredString(this.font,
                this.controller.isCraftablesShowUnavailable() ? "ALL" : "MAKE",
                toggleX + CRAFT_PANEL_TOGGLE_W / 2,
                toggleY + 2,
                0xFFFFFF);

        int gridY = searchY + CRAFT_PANEL_SEARCH_H + 6;
        int visibleRows = Math.max(1, (height - (gridY - y) - 6) / CRAFT_PANEL_PITCH);
        List<ClientRtsController.CraftableEntry> entries = this.controller.getCraftableEntries();
        int totalRows = Math.max(1, (int) Math.ceil(entries.size() / (double) CRAFT_PANEL_COLS));
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.craftScroll = Mth.clamp(this.craftScroll, 0, maxScroll);
        int startIndex = this.craftScroll * CRAFT_PANEL_COLS;

        for (int row = 0; row < visibleRows; row++) {
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
        String expected = this.controller.getCraftablesSearch();
        if (expected == null) {
            expected = "";
        }
        if (!expected.equals(this.craftSearchBox.getValue())) {
            this.craftSearchBox.setValue(expected);
        }
    }

    private void tickCraftRepeat() {
        if (this.heldCraftRecipeId.isBlank() || this.minecraft == null) {
            return;
        }
        long window = this.minecraft.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            stopCraftHold();
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.nextCraftRepeatMs) {
            return;
        }
        this.controller.craftRecipeToLinked(this.heldCraftRecipeId);
        this.craftRepeatDelayMs = Math.max(CRAFT_REPEAT_MIN_MS, this.craftRepeatDelayMs - CRAFT_REPEAT_STEP_MS);
        this.nextCraftRepeatMs = now + this.craftRepeatDelayMs;
    }

    private void beginCraftHold(String recipeId) {
        this.heldCraftRecipeId = recipeId == null ? "" : recipeId;
        this.craftRepeatDelayMs = CRAFT_REPEAT_START_MS;
        this.nextCraftRepeatMs = System.currentTimeMillis() + this.craftRepeatDelayMs;
    }

    private void stopCraftHold() {
        this.heldCraftRecipeId = "";
        this.craftRepeatDelayMs = CRAFT_REPEAT_START_MS;
        this.nextCraftRepeatMs = 0L;
    }

    private void renderCraftFeedback(GuiGraphics g, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();
        if (now >= this.controller.getCraftFeedbackExpiryMs() || this.controller.getCraftFeedbackCount() <= 0) {
            return;
        }
        float progress = (this.controller.getCraftFeedbackExpiryMs() - now) / 900.0F;
        int alpha = Mth.clamp((int) (255.0F * progress), 48, 255);
        int color = (alpha << 24) | 0xAEE8AE;
        g.drawString(this.font, "+" + this.controller.getCraftFeedbackCount(), mouseX + 18, mouseY - 12, color, true);
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
        if (this.leftMiningActive) {
            this.leftMiningActive = false;
            this.controller.abortMining(getSelectedToolSlot());
        }
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
            dropPos = hit.getLocation().add(dir.scale(0.12D));
        }
        this.controller.quickDropSelectedItem(dropItemId, 1, dropPos);
    }

    private boolean handleTopBarClick(double mouseX, double mouseY) {
        if (mouseY < 4 || mouseY > 24) {
            return false;
        }

        for (TopBarButtonLayout button : buildTopBarButtonLayouts()) {
            if (!inside(mouseX, mouseY, button.x(), 4, button.width(), 20)) {
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
                case SENSITIVITY -> this.controller.cycleInputSensitivity();
                case AUTO_STORE -> this.controller.toggleAutoStoreMinedDrops();
                case SHAPE -> {
                    this.shapeWheelOpenedByAlt = false;
                    openShapeWheel(mouseX, mouseY);
                }
                case SHAPE_ROTATE -> rotateShapeByStep(1);
                case GUIDE -> this.guideOpen = !this.guideOpen;
                case QUEST_DETECT -> this.controller.detectQuestsNow();
            }
            return true;
        }
        return false;
    }

    private boolean handleBottomPanelClick(double mouseX, double mouseY) {
        int bottomH = getBottomHeight();
        int bottomY = getBottomY();
        if (mouseY < bottomY) {
            return false;
        }

        int sortX = 8;
        int sortY = bottomY + 6;
        int categoryX = sortX + 58;
        int storageX = categoryX + CATEGORY_W + 10;
        int storageW = Math.max(120, this.width - storageX - 8);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int craftPanelY = bottomY + 21 + TOOL_AREA_H + 4;
        int craftPanelH = Math.max(SLOT, bottomH - (craftPanelY - bottomY) - 4);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int searchW = Math.max(80, storageW - 102);
        int pagerX = storageX + searchW + 4;
        int toolY = bottomY + 21;

        if (handleSearchClearClick(mouseX, mouseY, storageX, bottomY + 4, searchW)) {
            return true;
        }

        if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            focusStorageSearchBox();
            return true;
        }

        if (handleCraftablesPanelLeftClick(mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
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
            adjustBottomPanelHeight(SLOT);
            return true;
        }
        if (inside(mouseX, mouseY, heightBtnX, sortY + SORT_BUTTON_SIZE + 4, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)) {
            adjustBottomPanelHeight(-SLOT);
            return true;
        }
        int craftBtnY = sortY + (SORT_BUTTON_SIZE + 4) * 2 + 2;
        int craftBtnW = SORT_BUTTON_SIZE * 2 + 26;
        if (inside(mouseX, mouseY, sortX, craftBtnY, craftBtnW, CRAFT_BUTTON_H)) {
            this.controller.openCraftTerminal();
            return true;
        }

        int categoryY = bottomY + 4;
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

        if (inside(mouseX, mouseY, pagerX, bottomY + 4, 16, 14)) {
            this.controller.prevPage();
            return true;
        }
        if (inside(mouseX, mouseY, pagerX + 58, bottomY + 4, 16, 14)) {
            this.controller.nextPage();
            return true;
        }

        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, bottomH - (gridY - bottomY) - 4);
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
        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, itemGridW, gridH);
        if (entryIndex >= 0) {
            this.controller.selectStorageEntry(entryIndex);
            return true;
        }
        return true;
    }

    private boolean handleBottomPanelRightClick(double mouseX, double mouseY) {
        int bottomH = getBottomHeight();
        int bottomY = getBottomY();
        if (mouseY < bottomY) {
            return false;
        }

        int sortX = 8;
        int categoryX = sortX + 58;
        int storageX = categoryX + CATEGORY_W + 10;
        int storageW = Math.max(120, this.width - storageX - 8);
        int craftPanelX = storageX + Math.max(120, storageW - CRAFT_PANEL_W);
        int craftPanelY = bottomY + 21 + TOOL_AREA_H + 4;
        int craftPanelH = Math.max(SLOT, bottomH - (craftPanelY - bottomY) - 4);
        int mainStorageW = Math.max(120, craftPanelX - storageX - CRAFT_PANEL_GAP);
        int toolY = bottomY + 21;

        if (handleToolRowRightClick(mouseX, mouseY, storageX, toolY, mainStorageW)) {
            stopCraftHold();
            return true;
        }

        if (handleCraftablesPanelRightClick(mouseX, mouseY, craftPanelX, craftPanelY, CRAFT_PANEL_W, craftPanelH)) {
            return true;
        }

        int gridY = toolY + TOOL_AREA_H + 4;
        int gridH = Math.max(SLOT, bottomH - (gridY - bottomY) - 4);
        int fluidW = getFluidStripWidth(mainStorageW);
        int itemGridX = fluidW > 0 ? storageX + fluidW + 4 : storageX;
        int itemGridW = fluidW > 0 ? Math.max(SLOT, mainStorageW - fluidW - 4) : mainStorageW;

        int entryIndex = resolveClickedEntry(mouseX, mouseY, itemGridX, gridY, itemGridW, gridH);
        if (entryIndex >= 0 && entryIndex < this.controller.getStorageEntries().size()) {
            this.controller.storeFluidFromStorageItem(this.controller.getStorageEntries().get(entryIndex).itemId());
            return true;
        }
        return true;
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
        int searchW = Math.max(34, width - CRAFT_PANEL_TOGGLE_W - 12);
        int toggleX = searchX + searchW + 4;

        if (this.craftSearchBox != null && this.craftSearchBox.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            focusCraftSearchBox();
            return true;
        }
        if (inside(mouseX, mouseY, toggleX, searchY, CRAFT_PANEL_TOGGLE_W, CRAFT_PANEL_SEARCH_H)) {
            this.controller.toggleCraftablesShowUnavailable();
            return true;
        }
        return resolveCraftableEntryIndex(mouseX, mouseY, x, y, width, height) >= 0;
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
        this.controller.craftRecipeToLinked(entry.recipeId());
        beginCraftHold(entry.recipeId());
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
        if (itemId == null || itemId.isBlank()) {
            return 0L;
        }
        for (var entry : this.controller.getStorageEntries()) {
            if (itemId.equals(entry.itemId())) {
                return entry.count();
            }
        }
        return 0L;
    }

    private CategoryClick resolveClickedCategoryAction(double mouseX, double mouseY) {
        int bottomH = getBottomHeight();
        int sortX = 8;
        int bottomY = getBottomY();
        int categoryX = sortX + 58;
        int categoryY = bottomY + 4;
        int listY = categoryY + 13;
        int listH = bottomH - 15;

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

    private boolean isWorldArea(double mouseY) {
        return mouseY > TOP_H && mouseY < getBottomY();
    }

    private boolean isInsideCategoryList(double mouseX, double mouseY) {
        int bottomH = getBottomHeight();
        int sortX = 8;
        int bottomY = getBottomY();
        int categoryX = sortX + 58;
        int categoryY = bottomY + 4;
        int listY = categoryY + 13;
        int listH = bottomH - 15;
        return inside(mouseX, mouseY, categoryX + 2, listY, CATEGORY_W - 4, listH);
    }

    private void shiftCategoryScroll(int delta) {
        int visible = Math.max(1, (getBottomHeight() - 15) / CATEGORY_ROW_H);
        int maxScroll = Math.max(0, buildCategoryRows().size() - visible);
        this.categoryScroll = Mth.clamp(this.categoryScroll + delta, 0, maxScroll);
    }

    private int getBottomY() {
        return this.height - getBottomHeight();
    }

    private int getFloatingPanelAvailableHeight(int panelY) {
        return Math.max(0, getBottomY() - panelY - 6);
    }

    private int getBottomHeight() {
        int dynamicMax = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, this.height - TOP_H - 16));
        int minAllowed = Math.min(dynamicMax, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.bottomPanelHeight = Mth.clamp(this.bottomPanelHeight, minAllowed, dynamicMax);
        return this.bottomPanelHeight;
    }

    private void adjustBottomPanelHeight(int delta) {
        int dynamicMax = Math.max(MIN_BOTTOM_H, Math.min(MAX_BOTTOM_H, this.height - TOP_H - 16));
        int minAllowed = Math.min(dynamicMax, Math.max(MIN_BOTTOM_H, minimumBottomHeightForGridRows(MIN_STORAGE_GRID_ROWS)));
        this.bottomPanelHeight = Mth.clamp(this.bottomPanelHeight + delta, minAllowed, dynamicMax);
    }

    private int minimumBottomHeightForGridRows(int rows) {
        int gridStartOffset = 21 + TOOL_AREA_H + 4; // bottomY -> tool row + tool row -> grid top gap
        return gridStartOffset + (Math.max(1, rows) * SLOT) + GRID_BOTTOM_PADDING;
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

    private boolean canUseToolSlotShapeSource() {
        if (this.controller.hasSelectedItem() || this.controller.hasSelectedFluid()) {
            return false;
        }
        ItemStack stack = getSelectedToolStack();
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    private boolean tryAssignQuickSlotFromToolSelection(int pinIndex) {
        if (this.minecraft == null || this.minecraft.player == null) {
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
            PlacementReplayKind replayKind, String replayItemId, int replayToolSlot) {
        if (hit == null) {
            return;
        }
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.BLOCK) {
            clearShapeBuildSession();
            this.controller.placeSelected(hit, forcePlace, rayOrigin, rayDir);
            recordSinglePlacementForUndo(
                    hit,
                    replayKind,
                    replayItemId,
                    replayToolSlot);
            return;
        }

        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != shape) {
            this.shapeFootprintNudgeA = 0;
            this.shapeFootprintNudgeB = 0;
            this.shapeBuildSession = new ShapeBuildSession(
                    shape,
                    resolveShapeBuildFace(shape, hit.getDirection(), rayDir),
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
            if (requiresThirdPoint(shape)) {
                this.shapeBuildSession = new ShapeBuildSession(
                        shape,
                        session.face(),
                        session.pointA(),
                        pointB,
                        ShapeBuildPhase.NEED_THIRD_POINT,
                        0,
                        mouseY);
            } else {
                this.shapeBuildSession = new ShapeBuildSession(
                        shape,
                        session.face(),
                        session.pointA(),
                        pointB,
                        ShapeBuildPhase.READY_CONFIRM,
                        0,
                        session.boxHeightMouseBaseY());
            }
            return;
        }

        if (session.phase() == ShapeBuildPhase.NEED_THIRD_POINT) {
            this.shapeBuildSession = new ShapeBuildSession(
                    shape,
                    session.face(),
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
        boolean usePinnedItem = this.controller.hasSelectedItem();
        if (!usePinnedItem && !canUseToolSlotShapeSource()) {
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
        for (BlockHitResult shapedHit : hits) {
            this.controller.placeSelected(shapedHit, forcePlace, rayOrigin, rayDir, true);
        }
        List<BlockPos> positions = new ArrayList<>(hits.size());
        for (BlockHitResult shapedHit : hits) {
            positions.add(shapedHit.getBlockPos().immutable());
        }
        recordPlacementBatchForUndo(
                usePinnedItem ? PlacementReplayKind.PIN_ITEM : PlacementReplayKind.TOOL_SLOT,
                usePinnedItem ? this.controller.getSelectedItemId() : "",
                usePinnedItem ? -1 : getSelectedToolSlot(),
                input.face(),
                positions);
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
    }

    public ShapeGhostPreview getShapeGhostPreview() {
        if (this.controller.hasSelectedFluid()
                || this.controller.getBuildShape() == ClientRtsController.BuildShape.BLOCK) {
            return ShapeGhostPreview.EMPTY;
        }
        if (!this.controller.hasSelectedItem() && !canUseToolSlotShapeSource()) {
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
            pointB = applyShapeFootprintNudges(session.shape(), session.face(), pointA, pointB);
            return new ShapeBuildInput(session.shape(), session.face(), pointA, pointB, 0);
        }

        BlockPos pointB = session.pointB();
        if (pointB == null) {
            return null;
        }

        if (session.phase() == ShapeBuildPhase.NEED_THIRD_POINT) {
            if (requireReady) {
                return null;
            }
            pointB = applyShapeFootprintNudges(session.shape(), session.face(), pointA, pointB);
            return new ShapeBuildInput(session.shape(), session.face(), pointA, pointB, session.boxHeightOffset());
        }

        pointB = applyShapeFootprintNudges(session.shape(), session.face(), pointA, pointB);
        return new ShapeBuildInput(session.shape(), session.face(), pointA, pointB, session.boxHeightOffset());
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
                || shape == ClientRtsController.BuildShape.BLOCK
                || shape == ClientRtsController.BuildShape.LINE) {
            return cursorHit != null ? cursorHit.getBlockPos() : pointA;
        }

        Direction planeFace = session.face();
        if (shape == ClientRtsController.BuildShape.SQUARE || shape == ClientRtsController.BuildShape.BOX) {
            planeFace = Direction.UP;
        } else if (shape == ClientRtsController.BuildShape.WALL && planeFace == null) {
            planeFace = resolveWallFace(cursorHit == null ? null : cursorHit.getDirection(), computeCursorRayDirection());
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
            hits.add(createShapePlacementHit(pos, input.face()));
        }
        return hits;
    }

    private List<BlockPos> buildShapePositions(ShapeBuildInput input, ShapeFillMode fillMode) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        BlockPos start = input.pointA();
        BlockPos end = input.pointB();
        switch (input.shape()) {
            case LINE -> addLineTargets(targets, start, end);
            case SQUARE -> addSquareTargets(targets, start, end, input.face(), fillMode);
            case WALL -> addWallTargets(targets, start, end, input.face(), fillMode);
            case CIRCLE -> addCircleTargets(targets, start, end, input.face(), fillMode);
            case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), fillMode);
            default -> targets.add(start);
        }
        return new ArrayList<>(targets);
    }

    private List<BlockPos> filterOccupiedReadyShapeTargets(ShapeBuildInput input, List<BlockPos> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (input == null || input.face() == null) {
            return targets;
        }

        boolean strictEmptyLock = shouldSkipOccupiedReadyShapeTargets(input);
        LinkedHashSet<BlockPos> resolved = new LinkedHashSet<>(targets.size());
        if (this.minecraft == null || this.minecraft.level == null) {
            for (BlockPos clickedPos : targets) {
                if (clickedPos == null) {
                    continue;
                }
                BlockPos placePos = resolvePlacementTargetPos(clickedPos, input.face());
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
            BlockPos placePos = resolvePlacementTargetPos(clickedPos, input.face());
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

    private BlockPos resolvePlacementTargetPos(BlockPos clickedPos, Direction face) {
        if (clickedPos == null || face == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        if (!this.minecraft.level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return this.minecraft.level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
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

    private void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.WALL, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]));
        addRotatedPlaneRectangleTargets(targets, start, axes[0], axes[1], aOffset, bOffset, fillMode);
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
            case SQUARE, BOX -> Direction.UP;
            case WALL -> resolveWallFace(clickedFace, rayDir);
            default -> clickedFace == null ? Direction.UP : clickedFace;
        };
    }

    private Direction resolveWallFace(Direction clickedFace, Vec3 rayDir) {
        if (clickedFace != null && clickedFace.getAxis().isHorizontal()) {
            return clickedFace;
        }
        if (rayDir == null) {
            return Direction.SOUTH;
        }
        if (Math.abs(rayDir.x) >= Math.abs(rayDir.z)) {
            return rayDir.x >= 0.0D ? Direction.WEST : Direction.EAST;
        }
        return rayDir.z >= 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private Direction[] resolveShapePlaneAxes(ClientRtsController.BuildShape shape, Direction face) {
        if (shape == ClientRtsController.BuildShape.SQUARE || shape == ClientRtsController.BuildShape.BOX) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (shape == ClientRtsController.BuildShape.WALL) {
            if (face == null) {
                return new Direction[] { Direction.EAST, Direction.UP };
            }
            return switch (face.getAxis()) {
                case X -> new Direction[] { Direction.SOUTH, Direction.UP };
                case Z -> new Direction[] { Direction.EAST, Direction.UP };
                case Y -> new Direction[] { Direction.EAST, Direction.UP };
            };
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
            return;
        }
        int next = Math.floorMod(currentIndex + step, modes.size());
        this.shapeFillMode = modes.get(next);
    }

    private void ensureFillModeForShape(ClientRtsController.BuildShape shape) {
        List<ShapeFillMode> modes = availableFillModes(shape);
        if (modes.isEmpty()) {
            this.shapeFillMode = ShapeFillMode.FILL;
            return;
        }
        if (!modes.contains(this.shapeFillMode)) {
            this.shapeFillMode = modes.get(0);
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
        if (adjustHeight && this.shapeBuildSession.shape() == ClientRtsController.BuildShape.BOX) {
            return adjustBoxHeightNudge(delta);
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

    private boolean adjustBoxHeightNudge(int delta) {
        if (delta == 0 || this.shapeBuildSession == null || this.shapeBuildSession.shape() != ClientRtsController.BuildShape.BOX) {
            return false;
        }
        if (this.shapeBuildSession.phase() != ShapeBuildPhase.NEED_THIRD_POINT
                && this.shapeBuildSession.phase() != ShapeBuildPhase.READY_CONFIRM) {
            return false;
        }
        int nextOffset = clampShapeOffset(this.shapeBuildSession.boxHeightOffset() + delta);
        this.shapeBuildSession = new ShapeBuildSession(
                this.shapeBuildSession.shape(),
                this.shapeBuildSession.face(),
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
        for (BlockPos pos : batch.positions()) {
            this.controller.placeSelected(createShapePlacementHit(pos, batch.face()), false, rayOrigin, rayDir);
        }
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
                && isWorldArea(mouseY);
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
        return this.minecraft == null ? 0.0D : this.minecraft.mouseHandler.xpos();
    }

    private double currentMouseY() {
        return this.minecraft == null ? 0.0D : this.minecraft.mouseHandler.ypos();
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
        int panelW = Math.min(620, Math.max(360, this.width - 48));
        int panelH = Math.min(320, Math.max(220, this.height - 72));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int closeX = x + panelW - 20;

        g.fill(0, 0, this.width, this.height, 0x88000000);
        g.fill(x, y, x + panelW, y + panelH, 0xEE151A21);
        g.hLine(x, x + panelW, y, 0xFF6E7C90);
        g.hLine(x, x + panelW, y + panelH, 0xFF0E1014);
        g.vLine(x, y, y + panelH, 0xFF6E7C90);
        g.vLine(x + panelW, y, y + panelH, 0xFF0E1014);

        g.drawString(this.font, "How To Use RTS Builder", x + 12, y + 10, 0xFFFFFF);
        g.fill(closeX, y + 8, closeX + 12, y + 20, 0xAA2C3442);
        g.drawCenteredString(this.font, "X", closeX + 6, y + 11, 0xFFFFFF);

        String[] lines = new String[] {
                "1. Choose a top mode: Interact, Link Storage, or Rotate.",
                "2. Press F to jump into Funnel mode quickly (Shift+F cycles Fill/Hollow/Skeleton).",
                "3. Mine blocks by holding the left mouse button in Interact mode.",
                "4. Right-click normally: place/build first, then fallback to interaction if nothing can be placed.",
                "5. Hold Shift and right-click to force item placement/use on interactable blocks.",
                "6. Right-click a bucket/container in Storage, Tools, or RTS Pin to transfer 1 bucket into RTS Fluid.",
                "7. Click a fluid slot (orange strip) to select fluid placement mode.",
                "8. Hold ALT to open radial, hover shape sector, release ALT to pick shape.",
                "9. Cube context (right panel): Fill / Hollow / Skeleton.",
                "10. Non-BLOCK staged flow: set points first, then RMB confirm.",
                "11. 2D shapes use 2 clicks + confirm; CUBE uses 3 clicks + confirm.",
                "12. After A-point: PgUp/PgDn adjusts length; hold Shift for width.",
                "13. For CUBE height stage: hold Ctrl + PgUp/PgDn (Alt for x4 step).",
                "14. ROT button rotates around build center by 15deg per step.",
                "15. Shape dimensions/radius are capped at 32 for this phase.",
                "16. Ctrl+Z undo last batch, Ctrl+Y redo (same selected item required).",
                "17. Alt+Ctrl+RMB opens Tool/Pin interaction wheel.",
                "18. In Link Storage mode, right-click is disabled; left-click containers to link only."
        };

        int lineY = y + 30;
        int maxTextW = panelW - 24;
        for (String line : lines) {
            g.drawString(this.font, trimToWidth(line, maxTextW), x + 12, lineY, 0xE6EDF8);
            lineY += 14;
            if (lineY > y + panelH - 14) {
                break;
            }
        }
    }

    private boolean isInsideGuidePanel(double mouseX, double mouseY) {
        int panelW = Math.min(560, Math.max(320, this.width - 48));
        int panelH = Math.min(280, Math.max(210, this.height - 72));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        return inside(mouseX, mouseY, x, y, panelW, panelH);
    }

    private boolean isInsideGuideClose(double mouseX, double mouseY) {
        int panelW = Math.min(560, Math.max(320, this.width - 48));
        int panelH = Math.min(280, Math.max(210, this.height - 72));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int closeX = x + panelW - 20;
        return inside(mouseX, mouseY, closeX, y + 8, 12, 12);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - this.font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && this.font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
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

    private Direction resolveAirShapeFace(Vec3 dir) {
        if (this.shapeBuildSession != null && this.shapeBuildSession.face() != null) {
            return this.shapeBuildSession.face();
        }
        ClientRtsController.BuildShape shape = this.controller.getBuildShape();
        if (shape == ClientRtsController.BuildShape.SQUARE || shape == ClientRtsController.BuildShape.BOX) {
            return Direction.UP;
        }
        if (shape == ClientRtsController.BuildShape.WALL) {
            return resolveWallFace(null, dir);
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

    private static String fillModeLabel(ShapeFillMode mode) {
        if (mode == null) {
            return "Fill";
        }
        return switch (mode) {
            case FILL -> "Fill";
            case HOLLOW -> "Hollow";
            case SKELETON -> "Skeleton";
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
            return "RMB: place";
        }
        if (this.shapeBuildSession == null || this.shapeBuildSession.shape() != currentShape) {
            return "Step1: RMB set A";
        }
        return switch (this.shapeBuildSession.phase()) {
            case NEED_SECOND_POINT -> {
                BlockPos a = this.shapeBuildSession.pointA();
                yield "A: " + a.getX() + "," + a.getY() + "," + a.getZ()
                        + "  Step2: RMB set B";
            }
            case NEED_THIRD_POINT -> "Step3: Ctrl+PgUp/PgDn adjust height, RMB lock height";
            case READY_CONFIRM -> "RMB: confirm build";
        };
    }

    private static String shapeLabel(ClientRtsController.BuildShape shape) {
        if (shape == null) {
            return "BLOCK";
        }
        return switch (shape) {
            case BLOCK -> "BLOCK";
            case LINE -> "LINE";
            case SQUARE -> "SQUARE";
            case WALL -> "WALL";
            case CIRCLE -> "CIRCLE";
            case BOX -> "CUBE";
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
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) {
            return String.format("%.1fM B", buckets / 1_000_000.0);
        }
        if (buckets >= 1_000L) {
            return String.format("%.1fK B", buckets / 1_000.0);
        }
        return buckets + " B";
    }

    private void drawSlotCountOverlay(GuiGraphics g, int slotX, int slotY, int box, String countText, int color) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(slotX + 1, slotY + box - 9, slotX + box - 1, slotY + box - 1, 0xB0000000);
        g.drawString(this.font, countText, slotX + 2, slotY + box - 8, color, true);
        g.pose().popPose();
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
            Direction face,
            BlockPos pointA,
            BlockPos pointB,
            ShapeBuildPhase phase,
            int boxHeightOffset,
            double boxHeightMouseBaseY) {
    }

    private record ShapeBuildInput(
            ClientRtsController.BuildShape shape,
            Direction face,
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
            boolean active,
            int baseWidth) {
    }

    private record TopBarButtonLayout(
            TopBarButtonId id,
            int x,
            int width,
            String label,
            boolean active) {
    }

    private enum TopBarButtonId {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE,
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



