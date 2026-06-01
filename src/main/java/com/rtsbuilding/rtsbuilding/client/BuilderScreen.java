package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuideContext;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionOption;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTarget;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionWheelPanel;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.PlacementReplayKind;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayout;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelTab;
import com.rtsbuilding.rtsbuilding.client.screen.layout.QuickBuildPanelLayout;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.*;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarButtonId;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarButtonLayout;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.UltiminePanel;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
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
import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;
/**
 * RTS 寤洪€犳ā寮忎富灞忓箷锛圲I 鍏ュ彛锛夈€?
 * <p>
 * 璇ュ睆骞曟槸 RTS 寤洪€犳ā缁勭殑鏍稿績鐣岄潰锛岃鐩栧湪 Minecraft 娓告垙鐢婚潰涓婃柟锛?
 * 鎻愪緵蹇€熷缓閫犮€佽繛閿佹寲鎺樸€佺墿鍝佸瓨鍌ㄦ祻瑙堛€佷氦浜掕疆鐩樸€佸舰鐘跺缓閫犮€?
 * 钃濆浘鏀剧疆銆佹寚鍗楅潰鏉裤€佽缃彍鍗曠瓑鍏ㄩ儴 RTS 鍔熻兘鐨?UI 浜や簰銆?
 * <p>
 * 灞忓箷甯冨眬鍒嗕负涓変釜鍖哄煙锛?
 * <ul>
 *   <li><b>椤堕儴鏍?/b>锛圱op Bar锛夛細妯″紡鍒囨崲銆佸姛鑳芥寜閽€佸舰鐘堕€夋嫨銆佹寚鍗楀叆鍙?/li>
 *   <li><b>搴曢儴闈㈡澘</b>锛圔ottom Panel锛夛細鐗╁搧瀛樺偍缃戞牸銆佸悎鎴愰潰鏉裤€佽摑鍥鹃潰鏉?/li>
 *   <li><b>瑕嗙洊灞?/b>锛圤verlays锛夛細浜や簰杞洏銆佸舰鐘惰疆鐩樸€侀娇杞彍鍗曘€佹寚鍗楅潰鏉裤€佸脊绐楃瓑</li>
 * </ul>
 * <p>
 * 璇ョ被閫氳繃 {@link ClientRtsController} 涓庢父鎴忛€昏緫浜や簰锛屾墍鏈夊疄闄呭缓閫犳搷浣?
 * 濮旀墭缁欐湇鍔＄澶勭悊銆俇I 鐘舵€侀€氳繃 {@link RtsClientUiStateStore} 鎸佷箙鍖栥€?
 * <p>
 * <b>璁捐璇存槑锛?/b>姝ょ被涓哄睆骞曚富绫伙紝闆嗕腑绠＄悊鎵€鏈?UI 浜や簰銆備负淇濇寔浠ｇ爜鍙淮鎶ゆ€э紝
 * 甯冨眬甯搁噺宸叉彁鍙栬嚦 {@link BuilderScreenConstants}锛屽嚑浣曡绠楀凡鎻愬彇鑷?
 * {@link ShapeGeometryUtil}锛?
 * 褰㈢姸/钃濆浘棰勮妯″瀷宸叉彁鍙栦负鐙珛 records銆?
 *
 * @see ClientRtsController
 * @see BuilderScreenConstants
 * @see ShapeGeometryUtil
 */
public final class BuilderScreen extends Screen {
    private final ClientRtsController controller;
    private EditBox searchBox;
    private EditBox craftSearchBox;
    private final FunnelBufferPanel funnelBufferPanel = new FunnelBufferPanel();
    private final QuickBuildPanel quickBuildPanel = new QuickBuildPanel();
    private final ShapeContextPanel shapeContextPanel = new ShapeContextPanel();
    private final UltiminePanel ultiminePanel = new UltiminePanel();
    private final TopBarPanel topBarPanel = new TopBarPanel();
    private final BottomPanel bottomPanel = new BottomPanel();
    private final ScreenShapeController shapeController = new ScreenShapeController();
    private final ScreenCursorPicker cursorPicker = new ScreenCursorPicker();
    private final CameraInputHandler cameraInput = new CameraInputHandler();
    private final GuidePanel guidePanel = new GuidePanel();
    private final GearMenuPanel gearMenuPanel = new GearMenuPanel();
    private final InteractionWheelPanel interactionWheelPanel = new InteractionWheelPanel();
    private final ShapeWheelPanel shapeWheelPanel = new ShapeWheelPanel();
    private boolean debugButtonVisible = false;
    private boolean draggingInputSensitivity = false;
    private long damageFlashStartMs = -1L;
    private boolean funnelHotkeyHeld = false;
    private BuilderMode modeBeforeFunnelHotkey = BuilderMode.INTERACT;
    private boolean nativeCursorHidden = false;
    private boolean fixedRtsScaleRenderPass = false;
    private boolean fixedRtsScaleInputPass = false;
    private double activeRtsGuiRenderScale = 1.0D;
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private int pendingGuiBindSlot = -1;
    public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
        this.guidePanel.init(this, this.controller);
        this.gearMenuPanel.init(this, this.controller);
        this.interactionWheelPanel.init(this, this.controller);
        this.shapeWheelPanel.init(this, this.controller);
        this.funnelBufferPanel.init(this, this.controller);
        this.quickBuildPanel.init(this, this.controller);
        this.ultiminePanel.init(this, this.controller);
        this.shapeContextPanel.init(this, this.controller);
        this.topBarPanel.init(this, this.controller);
        this.bottomPanel.init(this, this.controller);
        this.shapeController.init(this, this.controller);
        this.cursorPicker.init(this, this.controller, this.shapeController);
        this.cameraInput.init(this, this.controller);
    }
    public Font font() {
        return this.font;
    }
    public void triggerDamageFlash() {
        this.damageFlashStartMs = System.currentTimeMillis();
    }
    public void setHoveredFunnelBufferEntry(int index) {
        this.funnelBufferPanel.setHoveredEntry(index);
    }
    public void toggleDebugButton() {
        this.debugButtonVisible = !this.debugButtonVisible;
    }
    public boolean isDebugButtonVisible() {
        return this.debugButtonVisible;
    }
    public boolean isDraggingInputSensitivity() {
        return this.draggingInputSensitivity;
    }
    public ShapeFillMode getShapeFillMode() {
        return this.shapeController.getShapeFillMode();
    }
    public void setShapeFillMode(ShapeFillMode mode) {
        this.shapeController.setShapeFillMode(mode);
    }
    public int getShapeRotateDegrees() {
        return this.shapeController.getShapeRotateDegrees();
    }
    public void clearShapeBuildSession() {
        this.shapeController.clearShapeBuildSession();
    }
    public void rotateShapeByStep(int step) {
        this.shapeController.rotateShapeByStep(step);
    }
    public ShapeGhostPreview getShapeGhostPreview() {
        return this.shapeController.getShapeGhostPreview();
    }
    public void ensureFillModeForShape(ClientRtsController.BuildShape shape) {
        this.shapeController.ensureFillModeForShape(shape);
    }
    public boolean isQuickBuildOpen() {
        return this.quickBuildPanel.isQuickBuildOpen();
    }
    public void setQuickBuildOpen(boolean open) {
        this.quickBuildPanel.setQuickBuildOpen(open);
    }
    public net.minecraft.client.Minecraft getMinecraft() {
        return this.minecraft;
    }
    public double getCurrentMouseX() {
        return this.lastMouseX;
    }
    public double getCurrentMouseY() {
        return this.lastMouseY;
    }
    public boolean isShapeWheelOpen() {
        return this.shapeWheelPanel.isOpen();
    }
    public boolean isInteractionWheelOpen() {
        return this.interactionWheelPanel.isOpen();
    }
    public void handleShapeWheelAltRelease(double mouseX, double mouseY) {
        this.shapeWheelPanel.handleAltRelease(mouseX, mouseY);
    }
    public EditBox getSearchBox() {
        return this.searchBox;
    }
    public EditBox getCraftSearchBox() {
        return this.craftSearchBox;
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
        if (this.bottomPanel.craftSearchDraft == null) {
            this.bottomPanel.craftSearchDraft = this.controller.getCraftablesSearch();
        }
        this.craftSearchBox.setValue(this.bottomPanel.craftSearchDraft);
        this.craftSearchBox.setResponder(value -> this.bottomPanel.craftSearchDraft = value == null ? "" : value);
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
        updateNativeCursorVisibility(false);
    }
    @Override
    public void removed() {
        super.removed();
        this.cameraInput.resetCameraVerticalHeld();
        updateNativeCursorVisibility(false);
    }
    @Override
    public void tick() {
        super.tick();
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
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            boolean handled = this.bottomPanel.craftQuantityDialog.mouseClicked(mouseX, mouseY, button, this.width, this.height);
            this.bottomPanel.submitCraftQuantityDialogIfReady();
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
                this.cameraInput.stopActiveMining();
                if (!BlueprintPanel.mouseClickedCaptureOverlay(mouseX, mouseY, this.width, this.height, TOP_H + 8)) {
                    if (BlueprintPanel.isCaptureSelectionComplete() && isWorldArea(mouseX, mouseY)) {
                        BlockHitResult hit = this.cursorPicker.pickBlockHit();
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
                    BlockHitResult hit = this.cursorPicker.pickBlockHit();
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
        if (this.shapeWheelPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.interactionWheelPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.guidePanel.isOpen()) {
            return this.guidePanel.mouseClicked(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.gearMenuPanel.isOpen()) {
                return this.gearMenuPanel.mouseClicked(mouseX, mouseY, button);
            }
            if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS
                    && BlueprintPanel.mouseClickedPlacementHud(mouseX, mouseY, this.width, this.height, TOP_H + 8, this.bottomPanel.getBottomY())) {
                return true;
            }
            if (this.quickBuildPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.ultiminePanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.topBarPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.funnelBufferPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (this.shapeContextPanel.handleClick(mouseX, mouseY)) {
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
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return true;
        }
        if (this.draggingInputSensitivity) {
            this.draggingInputSensitivity = false;
            return true;
        }
        if (this.shapeWheelPanel.isOpen()) {
            return true;
        }
        if (this.interactionWheelPanel.isOpen()) {
            return true;
        }
        if (this.guidePanel.isOpen()) {
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
        if (this.shapeWheelPanel.isOpen()) {
            return true;
        }
        if (this.interactionWheelPanel.isOpen()) {
            return true;
        }
        if (this.guidePanel.isOpen()) {
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

    public boolean isCameraUpActionHeld() {
        return this.cameraInput.isCameraUpActionHeld();
    }
    public boolean isCameraDownActionHeld() {
        return this.cameraInput.isCameraDownActionHeld();
    }
    private boolean runPrimaryActionAt(double mouseX, double mouseY) {
        return runPrimaryActionAt(mouseX, mouseY, -1);
    }
    private boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton) {
        if (this.pendingGuiBindSlot >= 0) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            if (!BlueprintPanel.isCaptureSelectionComplete() && isWorldArea(mouseX, mouseY)) {
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
            InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
            if (target != null && target.blockHit() != null) {
                this.shapeController.clearShapeBuildSession();
                this.controller.rotateBlock(target.blockHit().getBlockPos());
            }
            return true;
        }
        if (isWheelModifierDown()) {
            openInteractionWheel(mouseX, mouseY);
            return true;
        }
        boolean forcePlace = hasShiftDown();
        if (this.shapeController.tryConfirmPendingShapeBuild(forcePlace)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.hasSelectedBlueprint()) {
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
        InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
        if (target == null) {
            return true;
        }
        if (this.controller.hasSelectedFluid()) {
            if (target.blockHit() != null) {
                this.shapeController.placeWithShape(
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
                this.shapeController.clearShapeBuildSession();
                this.controller.interactEntityWithPinnedItem(
                        target.entityId(),
                        target.hitLocation(),
                        this.controller.getSelectedItemId(),
                        target.rayOrigin(),
                        target.rayDir());
            } else if (target.blockHit() != null) {
                this.shapeController.placeWithShape(
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
            this.shapeController.placeWithShape(
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
        this.shapeController.clearShapeBuildSession();
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
                this.shapeController.recordSinglePlacementForUndo(
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
    @Override
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
        if (BlueprintPanel.isNameDialogOpen()) {
            return true;
        }
        if (BlueprintPanel.isMaterialDialogOpen()) {
            return BlueprintPanel.mouseScrolledMaterialDialog(scrollY, this.controller, this.width, this.height);
        }
        if (this.gearMenuPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (this.shapeWheelPanel.mouseScrolled(scrollY)) {
            return true;
        }
        if (this.interactionWheelPanel.mouseScrolled(scrollY)) {
            return true;
        }
        if (this.guidePanel.isOpen()) {
            return this.guidePanel.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleMouseScrolled(mouseX, mouseY, scrollY);
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
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            boolean handled = this.bottomPanel.craftQuantityDialog.keyPressed(keyCode, scanCode, modifiers);
            this.bottomPanel.submitCraftQuantityDialogIfReady();
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
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.keyPressed(keyCode)) {
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
        if (this.shapeWheelPanel.keyPressed(keyCode)) {
            return true;
        }
        if (this.interactionWheelPanel.keyPressed(keyCode)) {
            return true;
        }
        if (this.guidePanel.isOpen()) {
            return this.guidePanel.keyPressed(keyCode);
        }
        if (this.gearMenuPanel.isOpen()) {
            return this.gearMenuPanel.keyPressed(keyCode);
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
            this.shapeController.rotateShapeByStep(hasShiftDown() ? -1 : 1);
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
                this.controller.setStorageSearch(this.searchBox.getValue());
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
    @Override
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            return this.bottomPanel.craftQuantityDialog.charTyped(codePoint, modifiers);
        }
        if (BlueprintPanel.charTypedNameDialog(codePoint)) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.charTyped(codePoint)) {
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
        if (this.ultiminePanel.isLimitEditing()) {
            return this.ultiminePanel.handleCharTyped(codePoint);
        }
        return super.charTyped(codePoint, modifiers);
    }
    // ======================== 娓叉煋鏂规硶 ========================
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.fixedRtsScaleRenderPass && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            return;
        }
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.shapeController.setShapeCursorY(mouseY);
        this.funnelBufferPanel.resetHoveredEntry();
        this.bottomPanel.hoveredEntry = -1;
        this.bottomPanel.hoveredRecentEntry = -1;
        this.bottomPanel.hoveredFluidEntry = -1;
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
        this.bottomPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        this.quickBuildPanel.render(guiGraphics, mouseX, mouseY);
        this.ultiminePanel.render(guiGraphics, mouseX, mouseY);
        this.funnelBufferPanel.render(guiGraphics, mouseX, mouseY);
        this.shapeContextPanel.render(guiGraphics, mouseX, mouseY);
        renderQuestDetectPopup(guiGraphics);
        renderStorageScanPopup(guiGraphics);
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
            BlockHitResult hit = isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
            BlueprintPanel.updateCaptureHoverPoint(hit == null ? null : hit.getBlockPos());
        }
        BlueprintPanel.renderCaptureOverlay(guiGraphics, this.font, this.width, this.height, mouseX, mouseY, TOP_H + 8);
        if (this.bottomPanel.bottomPanelTab == BottomPanelTab.BLUEPRINTS) {
            BlueprintPanel.renderPlacementHud(guiGraphics, this.font, this.controller,
                    this.width, this.height, mouseX, mouseY, TOP_H + 8, this.bottomPanel.getBottomY());
        }
        boolean modalOpen = this.gearMenuPanel.isOpen()
                || this.guidePanel.isOpen()
                || this.interactionWheelPanel.isOpen()
                || this.shapeWheelPanel.isOpen()
                || this.bottomPanel.craftQuantityDialog.isOpen()
                || BlueprintPanel.isNameDialogOpen()
                || BlueprintPanel.isMaterialDialogOpen();
        boolean placementSelectionActive = this.controller.hasSelectedItem() || this.controller.hasSelectedFluid();
        if (!modalOpen) {
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredEntry >= 0
                    && this.bottomPanel.hoveredEntry < this.controller.getStorageEntries().size()) {
                var entry = this.controller.getStorageEntries().get(this.bottomPanel.hoveredEntry);
                guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredRecentEntry >= 0
                    && this.bottomPanel.hoveredRecentEntry < this.controller.getRecentEntries().size()) {
                var entry = this.controller.getRecentEntries().get(this.bottomPanel.hoveredRecentEntry);
                if (!entry.preview().isEmpty()) {
                    guiGraphics.renderTooltip(this.font, entry.preview(), mouseX, mouseY);
                } else {
                    guiGraphics.renderTooltip(this.font, Component.literal(entry.label()), mouseX, mouseY);
                }
            }
            if (!placementSelectionActive
                    && this.bottomPanel.hoveredFluidEntry >= 0
                    && this.bottomPanel.hoveredFluidEntry < this.controller.getFluidEntries().size()) {
                var fluid = this.controller.getFluidEntries().get(this.bottomPanel.hoveredFluidEntry);
                if (!fluid.preview().isEmpty()) {
                    guiGraphics.renderTooltip(this.font, fluid.preview(), mouseX, mouseY);
                } else {
                    guiGraphics.renderTooltip(this.font, Component.literal(fluid.label()), mouseX, mouseY);
                }
            }
            if (this.bottomPanel.hoveredCraftableEntry >= 0 && this.bottomPanel.hoveredCraftableEntry < this.controller.getCraftableEntries().size()) {
                var entry = this.controller.getCraftableEntries().get(this.bottomPanel.hoveredCraftableEntry);
                guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
                String detail = entry.craftable()
                        ? text("screen.rtsbuilding.tooltip.craft_choose")
                        : entry.missingSummary();
                if (detail != null && !detail.isBlank()) {
                    guiGraphics.drawString(this.font, detail, mouseX + 10, mouseY + 18, entry.craftable() ? 0xFFAEE8AE : 0xFFFFB0B0);
                }
            }
            if (this.funnelBufferPanel.getHoveredEntry() >= 0 && this.funnelBufferPanel.getHoveredEntry() < this.controller.getFunnelBufferEntries().size()) {
                var entry = this.controller.getFunnelBufferEntries().get(this.funnelBufferPanel.getHoveredEntry());
                guiGraphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
                guiGraphics.drawString(this.font, text("screen.rtsbuilding.tooltip.buffered", entry.count()), mouseX + 10, mouseY + 18, 0xFFD8B8);
            }
            if (this.bottomPanel.hoveredGuiBindingSlot >= 0 && this.bottomPanel.hoveredGuiBindingSlot < this.controller.getGuiBindingCount()) {
                String detail = this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                        ? this.controller.getGuiBindingLabel(this.bottomPanel.hoveredGuiBindingSlot)
                        : text("screen.rtsbuilding.tooltip.gui_empty");
                guiGraphics.renderTooltip(this.font, Component.literal(detail), mouseX, mouseY);
                guiGraphics.drawString(
                        this.font,
                        this.pendingGuiBindSlot == this.bottomPanel.hoveredGuiBindingSlot
                                ? text("screen.rtsbuilding.tooltip.gui_cancel_bind")
                                : (this.controller.hasGuiBinding(this.bottomPanel.hoveredGuiBindingSlot)
                                        ? text("screen.rtsbuilding.tooltip.gui_bound")
                                        : text("screen.rtsbuilding.tooltip.gui_unbound")),
                        mouseX + 10,
                        mouseY + 18,
                        0xFFCFE3F7);
            }
            if (this.bottomPanel.hoveredEmptyHandSlot) {
                guiGraphics.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.empty_hand"), mouseX, mouseY);
                guiGraphics.drawString(this.font, text("screen.rtsbuilding.tooltip.empty_hand_detail"), mouseX + 10, mouseY + 18, 0xFFD8B8);
            }
            renderDiscoverabilityTooltips(guiGraphics, mouseX, mouseY);
            boolean funnelCursor = shouldRenderFunnelCursor();
            updateNativeCursorVisibility(funnelCursor);
            if (funnelCursor) {
                guiGraphics.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
            } else if (this.pendingGuiBindSlot >= 0) {
                drawGuiBindCursor(guiGraphics, mouseX, mouseY);
            } else {
                ItemStack cursorPreview = resolveCursorPreview();
                if (!cursorPreview.isEmpty() && !isSearchFocused() && !this.guidePanel.isOpen() && !this.interactionWheelPanel.isOpen()
                        && !this.shapeWheelPanel.isOpen()) {
                    guiGraphics.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
                }
            }
        } else {
            updateNativeCursorVisibility(false);
        }
        this.bottomPanel.renderCraftFeedback(guiGraphics);
        if (this.interactionWheelPanel.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z, () -> renderInteractionWheel(guiGraphics, mouseX, mouseY));
        }
        if (this.shapeWheelPanel.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z, () -> renderShapeWheel(guiGraphics, mouseX, mouseY));
        }
        if (this.gearMenuPanel.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 20.0F, () -> this.gearMenuPanel.render(guiGraphics, mouseX, mouseY));
        }
        if (this.guidePanel.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 40.0F, () -> this.guidePanel.render(guiGraphics));
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
        if (this.bottomPanel.craftQuantityDialog.isOpen()) {
            renderAtGuiLayer(guiGraphics, RTS_MODAL_LAYER_Z + 60.0F,
                    () -> this.bottomPanel.craftQuantityDialog.render(guiGraphics, this.font, this.width, this.height, mouseX, mouseY));
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
        return new RtsUiScaleFrame(oldW, oldH, renderScale, () -> {
            this.width = oldW;
            this.height = oldH;
        });
    }
    private void renderHomeSelectionOverlay(GuiGraphics g, int mouseX, int mouseY) {
        updateNativeCursorVisibility(false);
        int panelW = Math.min(360, this.width - 24);
        int panelX = (this.width - panelW) / 2;
        int panelY = 12;
        RtsClientUiUtil.drawPanelFrame(g, panelX, panelY, panelW, 54, 0xCC101820, 0xFF6E8799, 0xFF0D1218);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.title"), panelX + panelW / 2, panelY + 8, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.area"), panelX + panelW / 2, panelY + 22, 0xD8E6F5);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.confirm"), panelX + panelW / 2, panelY + 34, 0xBFD2E6);
        BlockHitResult hit = isWorldArea(mouseX, mouseY) ? this.cursorPicker.pickBlockHit() : null;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.home_select.target", pos.getX(), pos.getY(), pos.getZ()), this.width / 2, panelY + 68, 0xFFE7C46A);
        }
    }
    public void renderTopGuideHint(GuiGraphics g, List<TopBarButtonLayout> topButtons) {
        this.guidePanel.renderTopHint(g, topButtons);
    }
    private void drawGuiBindCursor(GuiGraphics g, int mouseX, int mouseY) {
        int x = mouseX + 8;
        int y = mouseY + 8;
        RtsClientUiUtil.drawPanelFrame(g, x, y, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE, 0xCC2D6B47, 0xFF78B28C, 0xFF0F151C);
        g.drawCenteredString(this.font, "+", x + CRAFT_DOCK_SLOT_SIZE / 2, y + 1, 0xFFFFFF);
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
        BottomPanelLayout layout = this.bottomPanel.resolveBottomPanelLayout();
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
    public boolean hasProgressionNode(ResourceLocation nodeId) {
        return !this.controller.isProgressionEnabled()
                || nodeId == null
                || this.controller.getUnlockedProgressionNodes().contains(nodeId.toString());
    }
    private static boolean hasRecipeViewerLoaded() {
        return ModList.get().isLoaded("jei")
                || ModList.get().isLoaded("emi")
                || ModList.get().isLoaded("roughlyenoughitems");
    }

    private void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        this.quickBuildPanel.setQuickBuildOpen(state.quickBuildOpen);
        this.ultiminePanel.setOpen(state.ultimineOpen);
        this.ultiminePanel.setLimit(state.ultimineLimit);
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);
        this.debugButtonVisible = state.debugButtonVisible;
        int sensitivityPresetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
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
            this.shapeController.setShapeFillMode(ShapeFillMode.valueOf(state.fillMode));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setShapeFillMode(ShapeFillMode.FILL);
        }
        this.shapeController.rotateToDegrees(Math.floorMod(state.rotationDegrees, 360));
        this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
    }
    public void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.controller.getBuildShape().name();
        state.fillMode = this.shapeController.getShapeFillMode().name();
        state.rotationDegrees = this.shapeController.getShapeRotateDegrees();
        state.quickBuildOpen = this.quickBuildPanel.isQuickBuildOpen();
        state.ultimineOpen = this.ultiminePanel.isOpen();
        state.ultimineLimit = this.ultiminePanel.getLimit();
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.inputSensitivityIndex = this.controller.getInputSensitivityIndex();
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
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }
    public String rtsGuiScaleLabel() {
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
    public QuickBuildPanelLayout resolveQuickBuildPanelLayout() {
        return this.quickBuildPanel.resolveLayout();
    }

    private void adjustUltimineLimit(int delta) {
        this.ultiminePanel.adjustLimit(delta);
    }
    private boolean isInsideUltimineLimitInput(double mouseX, double mouseY) {
        return this.ultiminePanel.isInsideLimitInput(mouseX, mouseY);
    }

    public boolean isUltimineOpen() {
        return this.ultiminePanel.isOpen();
    }
    public int getUltimineLimit() {
        return this.ultiminePanel.getLimit();
    }
    public void setUltimineLastSentLimit(int limit) {
        this.ultiminePanel.setLastSentLimit(limit);
    }
    public int getShapeUndoSize() {
        return this.shapeController.getShapeUndoSize();
    }
    public int getShapeRedoSize() {
        return this.shapeController.getShapeRedoSize();
    }
    public int getPendingGuiBindSlot() {
        return this.pendingGuiBindSlot;
    }
    public void setPendingGuiBindSlot(int slot) {
        this.pendingGuiBindSlot = slot;
    }
    public void clearPendingGuiBind() {
        this.pendingGuiBindSlot = -1;
    }
    public void toggleQuickBuild() {
        this.quickBuildPanel.toggleOpen();
    }
    public void toggleUltimine() {
        this.ultiminePanel.setOpen(!this.ultiminePanel.isOpen());
    }
    public void closeGearMenu() {
        this.gearMenuPanel.close();
    }
    public void toggleGearMenu() {
        if (this.gearMenuPanel.isOpen()) {
            this.gearMenuPanel.close();
        } else {
            this.gearMenuPanel.open();
        }
    }
    public void toggleTopGuide(int x, int y) {
        if (this.guidePanel.isOpen() && this.guidePanel.getContext() == GuideContext.TOP) {
            this.guidePanel.close();
        } else {
            this.guidePanel.open(GuideContext.TOP, x, y);
        }
    }
    public void openBottomGuide(int x, int y) {
        this.guidePanel.open(GuideContext.BOTTOM, x, y);
    }
    public boolean isGuideOpen() {
        return this.guidePanel.isOpen();
    }
    public boolean isGearMenuOpen() {
        return this.gearMenuPanel.isOpen();
    }
    public boolean isCraftQuantityDialogOpen() {
        return this.bottomPanel.craftQuantityDialog.isOpen();
    }
    private void activateFunnelHotkey() {
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
    public void copyDebugSnapshotToClipboard() {
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
                .append(" topAction=").append(this.topBarPanel.topActionForMode())
                .append(" quickBuild=").append(this.quickBuildPanel.isQuickBuildOpen())
                .append(" ultimine=").append(this.ultiminePanel.isOpen())
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
    private void renderDiscoverabilityTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (this.guidePanel.isOpen() || this.interactionWheelPanel.isOpen() || this.shapeWheelPanel.isOpen()) {
            return;
        }
        if (mouseY >= 42 && mouseY <= 56) {
            g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.undo_redo_keys"), mouseX, mouseY);
            return;
        }
        for (TopBarButtonLayout button : this.topBarPanel.buildTopBarButtonLayouts()) {
            if (button.id() == TopBarButtonId.QUICK_BUILD
                    && inside(mouseX, mouseY, button.x(), 4, button.width(), TOP_BUTTON_H)) {
                g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.quick_build_toggle"), mouseX, mouseY);
                return;
            }
        }
        if (this.quickBuildPanel.isQuickBuildOpen() && hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            QuickBuildPanelLayout layout = resolveQuickBuildPanelLayout();
            if (layout != null && layout.contains(mouseX, mouseY)) {
                g.renderTooltip(this.font, Component.translatable("screen.rtsbuilding.tooltip.quick_build_cancel"), mouseX, mouseY);
            }
        }
    }

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
    public void focusStorageSearchBox() {
        if (this.craftSearchBox != null && this.craftSearchBox.isFocused()) {
            this.craftSearchBox.setFocused(false);
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
        }
    }
    public void focusCraftSearchBox() {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
        }
        if (this.craftSearchBox != null) {
            this.craftSearchBox.setFocused(true);
            this.setFocused(this.craftSearchBox);
        }
    }

    public boolean isWorldArea(double mouseX, double mouseY) {
        return mouseY > TOP_H && !this.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
    }

    public int getBottomY() {
        return this.bottomPanel.getBottomY();
    }
    public int getFloatingPanelAvailableHeight(int panelY) {
        return Math.max(0, getBottomY() - panelY - 6);
    }

    private boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return this.bottomPanel.isInsideBottomPanel(mouseX, mouseY);
    }


    public boolean isSearchFocused() {
        return (this.searchBox != null && this.searchBox.isFocused())
                || (this.craftSearchBox != null && this.craftSearchBox.isFocused());
    }
    public int getSelectedToolSlot() {
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
    public boolean canUseToolSlotShapeSource() {
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
        int slot = this.bottomPanel.hoveredToolSlot >= 0 ? this.bottomPanel.hoveredToolSlot : getSelectedToolSlot();
        slot = Mth.clamp(slot, 0, 8);
        ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        this.controller.assignQuickSlotFromToolItem(pinIndex, stack);
        return true;
    }
    public void setSelectedToolSlot(int slot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.getInventory().selected = Mth.clamp(slot, 0, 8);
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

    public BlueprintGhostPreview getBlueprintGhostPreview() {
        if (this.bottomPanel.bottomPanelTab != BottomPanelTab.BLUEPRINTS
                || BlueprintPanel.isCaptureModeActive()
                || !BlueprintPanel.hasSelectedBlueprint()) {
            return BlueprintGhostPreview.EMPTY;
        }
        BlockPos anchor = BlueprintPanel.getPinnedAnchor();
        if (anchor == null) {
            if (!isWorldArea(this.lastMouseX, this.lastMouseY)) {
                return BlueprintGhostPreview.EMPTY;
            }
            BlockHitResult hit = this.cursorPicker.pickBlueprintPlacementHit();
            if (hit == null) {
                return BlueprintGhostPreview.EMPTY;
            }
            anchor = this.cursorPicker.resolveBlueprintAnchor(hit);
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
    public List<BlockPos> collectUltiminePreviewBlocks() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return List.of();
        }
        BlockPos seed = this.controller.getMineProgressPos();
        if (seed == null) {
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
        int limit = this.ultiminePanel.getLimit();
        return RtsUltimineCollector.collect(
                this.minecraft.level,
                seed,
                limit,
                (pos, state, originalState) -> !state.isAir()
                        && state.getBlock() == originalState.getBlock()
                        && (creative || state.getDestroySpeed(this.minecraft.level, pos) >= 0.0F));
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
        InteractionTarget target = this.cursorPicker.pickInteractionTarget(false);
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
        return this.interactionWheelPanel.open(mouseX, mouseY);
    }
    public void closeInteractionWheel() {
        this.interactionWheelPanel.close();
    }
    public void openShapeWheel(double mouseX, double mouseY) {
        this.shapeWheelPanel.open(mouseX, mouseY, false);
        closeInteractionWheel();
    }
    private void closeShapeWheel() {
        this.shapeWheelPanel.close();
    }

    private ClientRtsController.BuildShape resolveShapeWheelOption(double mouseX, double mouseY) {
        return this.shapeWheelPanel.resolveOption(mouseX, mouseY);
    }
    private InteractionOption resolveInteractionWheelOption(double mouseX, double mouseY) {
        return this.interactionWheelPanel.resolveOption(mouseX, mouseY);
    }
    private void renderInteractionWheel(GuiGraphics g, int mouseX, int mouseY) {
        this.interactionWheelPanel.render(g, mouseX, mouseY);
    }
    private void renderShapeWheel(GuiGraphics g, int mouseX, int mouseY) {
        this.shapeWheelPanel.render(g, mouseX, mouseY);
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

    public String trimToWidth(String text, int maxWidth) {
        return RtsClientUiUtil.trimToWidth(this.font, text, maxWidth);
    }
    public String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

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
                && !this.guidePanel.isOpen()
                && !this.interactionWheelPanel.isOpen()
                && !this.shapeWheelPanel.isOpen();
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
    public Vec3 computeCursorRayDirection() {
        return this.cursorPicker.computeCursorRayDirection();
    }
    public BlockHitResult pickBlockHit() {
        return this.cursorPicker.pickBlockHit();
    }
    public InteractionTarget pickInteractionTarget(boolean includeFluidSource) {
        return this.cursorPicker.pickInteractionTarget(includeFluidSource);
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
    public String fillModeLabel(ShapeFillMode mode) {
        return this.shapeController.fillModeLabel(mode);
    }
    public static String shapeDimensionLabel(ClientRtsController.BuildShape shape) {
        return ScreenShapeController.shapeDimensionLabel(shape);
    }
    public String currentShapeSizeText() {
        return this.shapeController.currentShapeSizeText();
    }
    public String currentShapeCostText() {
        return this.shapeController.currentShapeCostText();
    }
    public String pendingShapeStatusText() {
        return this.shapeController.pendingShapeStatusText();
    }
    public String shapeLabel(ClientRtsController.BuildShape shape) {
        return this.shapeController.shapeLabel(shape);
    }

    private boolean isAltDown() {
        if (this.minecraft == null) return false;
        long window = this.minecraft.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private double currentMouseX() {
        return this.lastMouseX;
    }

    private double currentMouseY() {
        return this.lastMouseY;
    }

}

