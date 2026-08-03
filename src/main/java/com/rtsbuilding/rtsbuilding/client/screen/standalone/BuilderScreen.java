package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.RtsAiChatPanel;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
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
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.TooltipStyle;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
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
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * RTS 主屏幕的顶层编排壳。
 *
 * <p>这里只保留 Screen 边界、构造装配、缩放入口和稳定公共门面；具体输入、绘制、窗口、
 * 世界动作与查询分别由独立 owner 承担。</p>
 */
public final class BuilderScreen extends BuilderScreenComponentState {

    private final BuilderScreenLifecycleOwner lifecycleOwner = new BuilderScreenLifecycleOwner(this);
    private final BuilderScreenPointerActionOwner pointerActionOwner = new BuilderScreenPointerActionOwner(this);
    private final BuilderScreenPointerGestureOwner pointerGestureOwner = new BuilderScreenPointerGestureOwner(this);
    private final BuilderScreenKeyboardActionOwner keyboardActionOwner = new BuilderScreenKeyboardActionOwner(this);
    private final BuilderScreenKeyboardSessionOwner keyboardSessionOwner = new BuilderScreenKeyboardSessionOwner(this);
    private final BuilderScreenRenderOwner renderOwner = new BuilderScreenRenderOwner(this);
    private final BuilderScreenWindowActionOwner windowActionOwner = new BuilderScreenWindowActionOwner(this);
    private final BuilderScreenModeSessionOwner modeSessionOwner = new BuilderScreenModeSessionOwner(this);
    private final BuilderScreenWorldQueryOwner worldQueryOwner = new BuilderScreenWorldQueryOwner(this);
    private final BuilderScreenPreviewQueryOwner previewQueryOwner = new BuilderScreenPreviewQueryOwner(this);

public BuilderScreen(ClientRtsController controller) {
        super(Component.literal("RTS Builder"));
        this.controller = controller;
        BuilderScreenInputHost inputHost =
                new BuilderScreenInputHost(this);
        this.pointerClickRouter = new BuilderScreenPointerClickRouter(
                inputHost, this.placementStateWheel, this.modeWheel);
        this.primaryActionRouter = new BuilderScreenPrimaryActionRouter(
                new BuilderScreenPrimaryActionHost(this),
                this.controller,
                this.bottomPanel,
                this.cullingManager,
                this.shapeController,
                this.cursorPicker);
        this.keyPressRouter = new BuilderScreenKeyPressRouter(
                inputHost, this.placementStateWheel, this.modeWheel);
        this.leftDockedTooltipRenderer =
                new LeftDockedTooltipRenderer(this, this.bottomPanel);
        this.uiStateManager = new RtsScreenUiStateManager(this.controller, this.shapeController, this.quickBuildPanel);
        this.guiScaleCoordinator = new RtsGuiScaleCoordinator(
                () -> this.minecraft,
                () -> this.width,
                () -> this.height,
                value -> this.width = value,
                value -> this.height = value,
                this.uiStateManager::fixedRtsGuiScale);
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
                this.aiChatPanel,
                this.guidePanel,
                this.quickBuildPanel,
                this.cullingPanel,
                this.workflowPanel,
                this.resumePlacementPanel,
                this.blueprintResumePanel);
        this.scrollRouter = new BuilderScreenScrollRouter(
                inputHost,
                this.controller,
                this.placementStateWheel,
                this.modeWheel,
                this.floatingWindowLayer,
                this.cullingManager,
                this.shapeController,
                this.bottomPanel);
        this.uiStateManager.registerWindowPanel("settings", this.gearMenuPanel);
        this.uiStateManager.registerWindowPanel("blueprints", this.blueprintWindowPanel);
        this.uiStateManager.registerWindowPanel("guide", this.guidePanel);
        this.uiStateManager.registerWindowPanel("ai_chat", this.aiChatPanel);
        this.uiStateManager.registerWindowPanel("linked_storage", this.linkedStoragePanel);
        this.uiStateManager.registerWindowPanel("craft_quantity", this.craftQuantityWindowPanel);
        this.uiStateManager.registerWindowPanel("blueprint_name", this.blueprintNameWindowPanel);
        this.uiStateManager.registerWindowPanel("blueprint_materials", this.blueprintMaterialWindowPanel);
        this.uiStateManager.registerWindowPanel("range_culling", this.cullingPanel);
        this.uiStateManager.registerWindowPanel("workflow", this.workflowPanel);
        this.uiStateManager.registerWindowPanel("resume_placement", this.resumePlacementPanel);
        this.uiStateManager.registerWindowPanel("blueprint_resume", this.blueprintResumePanel);
        // QuickBuildPanel 初始化时会通过 UI Core 快照读取形状填充文案和尺寸状态。
        // 形状控制器因此是面板初始化的前置依赖，必须先绑定 screen/controller。
        this.shapeController.init(this, this.controller);
        this.storageLinkDetailHandler.init(this, this.controller);
        this.guidePanel.init(this, this.controller);
        this.aiChatPanel.init(this, this.controller);
        this.gearMenuPanel.init(this, this.controller);
        this.blueprintWindowPanel.init(this, this.controller);
        this.blueprintNameWindowPanel.init(this, this.controller);
        this.blueprintMaterialWindowPanel.init(this, this.controller);
        this.craftQuantityWindowPanel.init(this, this.controller);
        this.funnelBufferPanel.init(this, this.controller);
        // Quick Build 初始化会生成一次正式状态快照，其中的成本与尺寸文本可能读取鼠标射线。
        // 因此光标拾取器必须先完成绑定，不能等到所有面板初始化结束后再挂载。
        this.cursorPicker.init(this, this.controller, this.shapeController);
        this.quickBuildPanel.init(this, this.controller);
        this.cullingPanel.init(this, this.controller);
        this.workflowPanel.init(this, this.controller);
        this.resumePlacementPanel.init(this, this.controller);
        this.blueprintResumePanel.init(this, this.controller);
        this.linkedStoragePanel.init(this, this.controller);
        this.topBarPanel.init(this, this.controller);
        this.bottomPanel.init(this, this.controller);
        this.cameraInput.init(this, this.controller);
        RtsCullingClientState.setActiveManager(this.cullingManager);
        RtsCullingClientState.requestCurrentWorldState();
    }

public Font font() {
        return this.font;
    }

public int topBarBottomY() {
        return TOP_H;
    }

public boolean isStorageViewVisible() {
        return this.bottomPanel.isStorageBrowserVisible() || this.linkedStoragePanel.isOpen();
    }

public void triggerDamageFlash() {
        this.overlayRenderer.triggerDamageFlash();
    }

public void setHoveredFunnelBufferEntry(int index) {
        this.funnelBufferPanel.setHoveredEntry(index);
    }

public void toggleContainerOverlayEnabled() { this.uiStateManager.toggleContainerOverlayEnabled(); }

public void toggleOverlayShiftImportEnabled() { this.uiStateManager.toggleOverlayShiftImportEnabled(); }

public void toggleShowStorageReadyPopup() {
        this.uiStateManager.toggleShowStorageReadyPopup();
        if (!this.uiStateManager.isShowStorageReadyPopupEnabled()) {
            this.controller.clearStorageScanPopupState();
        }
    }

public void toggleShowWorkflowPanelEnabled() { this.uiStateManager.toggleShowWorkflowPanelEnabled(); }

public void toggleJadePanelTrackMouse() { this.uiStateManager.toggleJadePanelTrackMouse(); }

public void toggleJadePanelHidden() { this.uiStateManager.toggleJadePanelHidden(); }

public void toggleStorageRefreshQuietEnabled() { this.uiStateManager.toggleStorageRefreshQuietEnabled(); }

public void toggleStorageAutoRefreshEnabled() { this.uiStateManager.toggleStorageAutoRefreshEnabled(); }

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

public ShapeDataRecords.GhostPreview getShapeGhostPreview() {
        return this.shapeController.getShapeGhostPreview();
    }

public List<ShapeDataRecords.GhostPreview> getConfirmedRangeDestroyPreviews() {
        return this.shapeController.getConfirmedRangeDestroyPreviews();
    }

public void ensureFillModeForShape(BuildShape shape) {
        this.shapeController.ensureFillModeForShape(shape);
    }

public boolean isQuickBuildOpen() {
        return canUseQuickBuild() && this.quickBuildPanel.isOpen();
    }

public void setQuickBuildOpen(boolean open) {
        if (open && !canUseQuickBuild()) {
            showQuickBuildLockedMessage();
            this.quickBuildPanel.setOpen(false);
            return;
        }
        this.quickBuildPanel.setOpen(open);
    }

public boolean canUseQuickBuild() {
        return !this.controller.isProgressionEnabled()
                || this.controller.hasInstalledPlugin(BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN.toString());
    }

public void showQuickBuildLockedMessage() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                    Component.translatable("message.rtsbuilding.quick_build.remote_place_locked"), true);
        }
    }

    public void syncQuickBuildActiveState() { this.lifecycleOwner.syncQuickBuildActiveState(); }
public net.minecraft.client.Minecraft getMinecraft() {
        return this.minecraft;
    }

public RtsResumePlacementPanel getResumePlacementPanel() {
        return this.resumePlacementPanel;
    }

public RtsBlueprintResumePanel getBlueprintResumePanel() {
        return this.blueprintResumePanel;
    }

public double getCurrentMouseX() {
        return this.lastMouseX;
    }

public double getCurrentMouseY() {
        return this.lastMouseY;
    }

public EditBox getSearchBox() {
        return this.searchBox;
    }

public EditBox getCraftSearchBox() {
        return this.craftSearchBox;
    }

    @Override
    protected void init() { super.init(); this.lifecycleOwner.init(); }
@Override
    public boolean isPauseScreen() {
        return false;
    }

@Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() { this.lifecycleOwner.onClose(); }
    @Override
    public void removed() { super.removed(); this.lifecycleOwner.removed(); }
    @Override
    public void tick() { super.tick(); this.lifecycleOwner.tick(); }
@Override
    /*
      Handles mouse click input with RTS GUI scale remapping. Routes clicks through
      dialogs, blueprint capture, home selection, floating windows, area mine,
      left-click panels, and world click actions.

      @return true if the click was consumed by this screen, false otherwise
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try (RtsGuiScaleCoordinator.InputFrame frame =
                     this.guiScaleCoordinator.beginInput()) {
            if (frame.requiresRemap()) {
                return mouseClicked(mouseX / frame.scale(), mouseY / frame.scale(), button);
            }
        }
        return this.pointerClickRouter.mouseClicked(mouseX, mouseY, button);
    }

    void selectPlacementStateFromWheel( PlacementStateWheel.PlacementChoice choice, int button) { this.pointerActionOwner.selectPlacementStateFromWheel(choice, button); }
    void closePlacementStateWheelFromPointer(int button) { this.pointerActionOwner.closePlacementStateWheelFromPointer(button); }
    void selectModeFromWheelPointer(BuilderMode selectedMode, int button) { this.pointerActionOwner.selectModeFromWheelPointer(selectedMode, button); }
    void closeModeWheelFromPointer(int button) { this.pointerActionOwner.closeModeWheelFromPointer(button); }
boolean forwardUnhandledMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    boolean handleBlueprintCaptureClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleBlueprintCaptureClicks(mouseX, mouseY, button); }
    boolean handleHomeSelectionClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleHomeSelectionClicks(mouseX, mouseY, button); }
    boolean handleOverlayClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleOverlayClicks(mouseX, mouseY, button); }
    boolean handleAreaMineClickBlock(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleAreaMineClickBlock(mouseX, mouseY, button); }
    boolean handleLeftClickInteractions(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleLeftClickInteractions(mouseX, mouseY, button); }
    boolean handleWorldClickActions(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleWorldClickActions(mouseX, mouseY, button); }
    boolean handleAdvancedShapeHandleClick(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleAdvancedShapeHandleClick(mouseX, mouseY, button); }
    boolean handleBatchConfirmMouse(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleBatchConfirmMouse(mouseX, mouseY, button); }
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return this.pointerGestureOwner.mouseReleased(mouseX, mouseY, button); }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return this.pointerGestureOwner.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
    @Override
    public void mouseMoved(double mouseX, double mouseY) { this.pointerGestureOwner.mouseMoved(mouseX, mouseY); }
    public boolean isCameraUpActionHeld() { return this.pointerGestureOwner.isCameraUpActionHeld(); }
    public boolean isCameraDownActionHeld() { return this.pointerGestureOwner.isCameraDownActionHeld(); }
    boolean runPrimaryActionAt(double mouseX, double mouseY) { return this.pointerGestureOwner.runPrimaryActionAt(mouseX, mouseY); }
    boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton) { return this.pointerGestureOwner.runPrimaryActionAt(mouseX, mouseY, mouseButton); }
    boolean openPlacementStateWheel(double mouseX, double mouseY) { return this.pointerGestureOwner.openPlacementStateWheel(mouseX, mouseY); }
    void closePlacementStateWheel() { this.pointerGestureOwner.closePlacementStateWheel(); }
    void closePlacementStateWheelImmediately() { this.pointerGestureOwner.closePlacementStateWheelImmediately(); }
    void releasePlacementWheelPointer() { this.pointerGestureOwner.releasePlacementWheelPointer(); }
    boolean tryUseMainHandItemInAir() { return this.worldQueryOwner.tryUseMainHandItemInAir(); }
    boolean canUseMainHandItemInAir() { return this.pointerGestureOwner.canUseMainHandItemInAir(); }
@Override
    /**
     * Handles mouse scroll with RTS GUI scale remapping. Routes scroll to open
     * dialogs, gear menu, wheel panels, guide panel, bottom panel, shape height
     * previews, rotation mode, and item slot scrolling.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        try (RtsGuiScaleCoordinator.InputFrame frame =
                     this.guiScaleCoordinator.beginInput()) {
            if (frame.requiresRemap()) {
                return mouseScrolled(mouseX / frame.scale(), mouseY / frame.scale(), scrollX, scrollY);
            }
        }
        return this.scrollRouter.mouseScrolled(
                mouseX, mouseY, scrollX, scrollY);
    }

@Override
    /**
     * Handles key press events. Dispatches to dialogs, blueprint, overlay, world interaction,
     * search box, tool slot, and sensitivity handlers in priority order.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.keyPressRouter.keyPressed(keyCode, scanCode, modifiers);
    }

    void closePlacementStateWheelFromKey() { this.keyboardActionOwner.closePlacementStateWheelFromKey(); }
boolean forwardUnhandledKeyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean handleBlueprintKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleBlueprintKeys(keyCode, scanCode, modifiers); }
    boolean handleHomeSelectionKey(int keyCode) { return this.keyboardActionOwner.handleHomeSelectionKey(keyCode); }
    boolean handleOverlayKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleOverlayKeys(keyCode, scanCode, modifiers); }
    boolean handleWorldInteractionKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleWorldInteractionKeys(keyCode, scanCode, modifiers); }
    boolean handlePlacedBlockRotationKey(int keyCode) { return this.keyboardActionOwner.handlePlacedBlockRotationKey(keyCode); }
    boolean handleSelectionBoxKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleSelectionBoxKeys(keyCode, scanCode, modifiers); }
    boolean handleBatchConfirmKey(int keyCode, int scanCode) { return this.keyboardActionOwner.handleBatchConfirmKey(keyCode, scanCode); }
    boolean handleSearchFocusKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleSearchFocusKeys(keyCode, scanCode, modifiers); }
    boolean handleToolSlotKeys(int keyCode, int scanCode, int modifiers) { return this.keyboardActionOwner.handleToolSlotKeys(keyCode, scanCode, modifiers); }
    boolean handleSensitivityKeys(int keyCode, int scanCode) { return this.keyboardActionOwner.handleSensitivityKeys(keyCode, scanCode); }
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return this.keyboardSessionOwner.keyReleased(keyCode, scanCode, modifiers); }
    void handleRtsFlightToggle() { this.keyboardSessionOwner.handleRtsFlightToggle(); }
    boolean handleModeKeyPressed(int keyCode, int scanCode) { return this.keyboardSessionOwner.handleModeKeyPressed(keyCode, scanCode); }
    boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) { return this.keyboardSessionOwner.switchToModeFromKey(mode, funnelEnabled); }
    @Override
    public boolean charTyped(char codePoint, int modifiers) { return this.keyboardSessionOwner.charTyped(codePoint, modifiers); }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) { this.renderOwner.render(guiGraphics, mouseX, mouseY, partialTick); }
    void resetHoverStates() { this.renderOwner.resetHoverStates(); }
    void renderHoveredItemTooltips(GuiGraphics g, int mouseX, int mouseY) { this.renderOwner.renderHoveredItemTooltips(g, mouseX, mouseY); }
    public void renderTopGuideHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) { this.renderOwner.renderTopGuideHint(g, topButtons); }
    void drawGuiBindCursor(GuiGraphics g, int mouseX, int mouseY) { this.renderOwner.drawGuiBindCursor(g, mouseX, mouseY); }
static boolean hasRecipeViewerLoaded() {
        return ModList.get().isLoaded("jei")
                || ModList.get().isLoaded("emi")
                || ModList.get().isLoaded("roughlyenoughitems");
    }

    public void persistUiState() { this.windowActionOwner.persistUiState(); }
    public void adjustRtsGuiScale(double delta) { this.windowActionOwner.adjustRtsGuiScale(delta); }
    public double getRtsGuiScale() { return this.windowActionOwner.getRtsGuiScale(); }
    public String rtsGuiScaleLabel() { return this.windowActionOwner.rtsGuiScaleLabel(); }
    public RtsFloatingWindowLayer getFloatingWindowLayer() { return this.windowActionOwner.getFloatingWindowLayer(); }
    public ClientRtsController uiController() { return this.windowActionOwner.uiController(); }
    public boolean isQuickBuildRangeDestroyMode() { return this.windowActionOwner.isQuickBuildRangeDestroyMode(); }
    public boolean isQuickBuildRangeDestroyChainMode() { return this.windowActionOwner.isQuickBuildRangeDestroyChainMode(); }
    public boolean isQuickBuildCreativeOverwriteEnabled() { return this.windowActionOwner.isQuickBuildCreativeOverwriteEnabled(); }
    public boolean isAdvancedRangeDestroyBoxMode() { return this.windowActionOwner.isAdvancedRangeDestroyBoxMode(); }
    public boolean isAdvancedRangeDestroyShapeMode() { return this.windowActionOwner.isAdvancedRangeDestroyShapeMode(); }
    public boolean isAdvancedShapeMode() { return this.windowActionOwner.isAdvancedShapeMode(); }
    public boolean isRoundShapeVertical(BuildShape shape) { return this.windowActionOwner.isRoundShapeVertical(shape); }
    public String activeQuickBuildShapeLabel() { return this.windowActionOwner.activeQuickBuildShapeLabel(); }
    public boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY) { return this.windowActionOwner.handleQuickBuildRangeDestroyClick(mouseX, mouseY); }
    public boolean handleQuickBuildRangeDestroyClick(double mouseX, double mouseY,
            com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind inputKind) {
        return this.windowActionOwner.handleQuickBuildRangeDestroyClick(mouseX, mouseY, inputKind);
    }
    public void setQuickBuildMode(QuickBuildMode mode) { this.windowActionOwner.setQuickBuildMode(mode); }
    public int getUltimineLimit() { return this.windowActionOwner.getUltimineLimit(); }
    public boolean isAreaMineHeightPreview() { return this.windowActionOwner.isAreaMineHeightPreview(); }
    public int getShapeUndoSize() { return this.windowActionOwner.getShapeUndoSize(); }
    public int getPendingGuiBindSlot() { return this.windowActionOwner.getPendingGuiBindSlot(); }
    public void setPendingGuiBindSlot(int slot) { this.windowActionOwner.setPendingGuiBindSlot(slot); }
    public void clearPendingGuiBind() { this.windowActionOwner.clearPendingGuiBind(); }
    public void toggleQuickBuild() { this.windowActionOwner.toggleQuickBuild(); }
    public void openCraftQuantityWindow(CraftableEntry entry) { this.windowActionOwner.openCraftQuantityWindow(entry); }
    public void submitCraftQuantityWindowIfReady() { this.windowActionOwner.submitCraftQuantityWindowIfReady(); }
    boolean handleFloatingWindowClick(double mouseX, double mouseY, int button) { return this.windowActionOwner.handleFloatingWindowClick(mouseX, mouseY, button); }
    boolean handleFloatingWindowDrag(double mouseX, double mouseY, int button, double dragX, double dragY) { return this.windowActionOwner.handleFloatingWindowDrag(mouseX, mouseY, button, dragX, dragY); }
    boolean handleFloatingWindowRelease(double mouseX, double mouseY, int button) { return this.windowActionOwner.handleFloatingWindowRelease(mouseX, mouseY, button); }
    public boolean isMouseOverFloatingWindow(double mouseX, double mouseY) { return this.windowActionOwner.isMouseOverFloatingWindow(mouseX, mouseY); }
    public void closeGearMenu() { this.windowActionOwner.closeGearMenu(); }
    public void toggleGearMenu() { this.windowActionOwner.toggleGearMenu(); }
    public void toggleTopGuide(int x, int y) { this.windowActionOwner.toggleTopGuide(x, y); }
    public void openAiChat() { this.windowActionOwner.openAiChat(); }
    public boolean canUseRangeCulling() { return this.modeSessionOwner.canUseRangeCulling(); }
    public boolean isRangeCullingManagementActive() { return this.modeSessionOwner.isRangeCullingManagementActive(); }
    public void toggleRangeCullingManagement() { this.modeSessionOwner.toggleRangeCullingManagement(); }
    public void openBottomGuide(int x, int y) { this.modeSessionOwner.openBottomGuide(x, y); }
    public boolean isGuideOpen() { return this.modeSessionOwner.isGuideOpen(); }
    public boolean isGearMenuOpen() { return this.modeSessionOwner.isGearMenuOpen(); }
    public boolean isCraftQuantityDialogOpen() { return this.modeSessionOwner.isCraftQuantityDialogOpen(); }
    void activateFunnelHotkey() { this.modeSessionOwner.activateFunnelHotkey(); }
    void deactivateFunnelHotkey() { this.modeSessionOwner.deactivateFunnelHotkey(); }
    void beginFunnelMouseHold(int button) { this.modeSessionOwner.beginFunnelMouseHold(button); }
    void endFunnelMouseHold(int button) { this.modeSessionOwner.endFunnelMouseHold(button); }
    void syncFunnelHoldState() { this.modeSessionOwner.syncFunnelHoldState(); }
    void updateModeWheelAltState() { this.modeSessionOwner.updateModeWheelAltState(); }
    boolean canOpenModeWheel() { return this.modeSessionOwner.canOpenModeWheel(); }
    void selectModeFromWheel(BuilderMode mode) { this.modeSessionOwner.selectModeFromWheel(mode); }
    boolean handleRangeCullingSelectionClick(double mouseX, double mouseY, int button) { return this.worldQueryOwner.handleRangeCullingSelectionClick(mouseX, mouseY, button); }
    boolean handleRangeCullingWorldAction(double mouseX, double mouseY) { return this.worldQueryOwner.handleRangeCullingWorldAction(mouseX, mouseY); }
    boolean handleBoxHandleDrag(int button, double dragX, double dragY) { return this.modeSessionOwner.handleBoxHandleDrag(button, dragX, dragY); }
    double[] screenAxisForDirection(Direction direction) { return this.modeSessionOwner.screenAxisForDirection(direction); }
    void updateRangeCullingHover(double mouseX, double mouseY) { this.modeSessionOwner.updateRangeCullingHover(mouseX, mouseY); }
    void updateAdvancedRangeDestroyHover(double mouseX, double mouseY) { this.modeSessionOwner.updateAdvancedRangeDestroyHover(mouseX, mouseY); }
    public boolean isBlueprintPlacementModeLocked() { return this.modeSessionOwner.isBlueprintPlacementModeLocked(); }
    void enforceBlueprintPlacementModeLock() { this.modeSessionOwner.enforceBlueprintPlacementModeLock(); }
    void quickDropSelectedAtCursor() { this.modeSessionOwner.quickDropSelectedAtCursor(); }
    public void blurSearchFocus() { this.worldQueryOwner.blurSearchFocus(); }
    public void focusStorageSearchBox() { this.worldQueryOwner.focusStorageSearchBox(); }
    public void focusCraftSearchBox() { this.worldQueryOwner.focusCraftSearchBox(); }
    public boolean isWorldArea(double mouseX, double mouseY) { return this.worldQueryOwner.isWorldArea(mouseX, mouseY); }
    public int getBottomY() { return this.worldQueryOwner.getBottomY(); }
    public int getFloatingPanelAvailableHeight(int panelY) { return this.worldQueryOwner.getFloatingPanelAvailableHeight(panelY); }
    boolean isInsideBottomPanel(double mouseX, double mouseY) { return this.worldQueryOwner.isInsideBottomPanel(mouseX, mouseY); }
    public boolean isSearchFocused() { return this.worldQueryOwner.isSearchFocused(); }
    public int getSelectedToolSlot() { return this.worldQueryOwner.getSelectedToolSlot(); }
    ItemStack getSelectedToolStack() { return this.worldQueryOwner.getSelectedToolStack(); }
    String resolveGuiBindingItemId(BlockHitResult hit) { return this.worldQueryOwner.resolveGuiBindingItemId(hit); }
    public boolean canUseToolSlotShapeSource() { return this.worldQueryOwner.canUseToolSlotShapeSource(); }
    boolean tryAssignQuickSlotFromToolSelection(int pinIndex) { return this.worldQueryOwner.tryAssignQuickSlotFromToolSelection(pinIndex); }
    public void setSelectedToolSlot(int slot) { this.worldQueryOwner.setSelectedToolSlot(slot); }
    public BlueprintGhostPreview getBlueprintGhostPreview() { return this.previewQueryOwner.getBlueprintGhostPreview(); }
    public List<BlockPos> collectUltiminePreviewBlocks() { return this.previewQueryOwner.collectUltiminePreviewBlocks(); }
    List<BlockPos> filterToBounds(List<BlockPos> blocks) { return this.previewQueryOwner.filterToBounds(blocks); }
    boolean isMovePlayerActionMouse(int button) { return this.previewQueryOwner.isMovePlayerActionMouse(button); }
    boolean isMovePlayerActionKey(int keyCode, int scanCode) { return this.previewQueryOwner.isMovePlayerActionKey(keyCode, scanCode); }
    boolean handleMovePlayerActionAt(double mouseX, double mouseY) { return this.previewQueryOwner.handleMovePlayerActionAt(mouseX, mouseY); }
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) { this.previewQueryOwner.enableRtsScissor(g, x1, y1, x2, y2); }
    public String trimToWidth(String text, int maxWidth) { return this.previewQueryOwner.trimToWidth(text, maxWidth); }
    public String text(String key, Object... args) { return this.previewQueryOwner.text(key, args); }
    public String selectedItemStatusLabel() { return this.previewQueryOwner.selectedItemStatusLabel(); }
    boolean hasMainHandItem() { return this.worldQueryOwner.hasMainHandItem(); }
    ItemStack resolveCursorPreview() { return this.previewQueryOwner.resolveCursorPreview(); }
    boolean shouldRenderFunnelCursor() { return this.previewQueryOwner.shouldRenderFunnelCursor(); }
    public Vec3 computeCursorRayDirection() { return this.previewQueryOwner.computeCursorRayDirection(); }
    public Vec3 currentRayOrigin() { return this.previewQueryOwner.currentRayOrigin(); }
    public Direction currentCameraHorizontalDirection() { return this.previewQueryOwner.currentCameraHorizontalDirection(); }
    public PlacedBlockRotationHandles getRotationHandles() { return this.previewQueryOwner.getRotationHandles(); }
    public BlockHitResult pickBlockHit() { return this.previewQueryOwner.pickBlockHit(); }
    public InteractionTypes.InteractionTarget pickInteractionTarget(boolean includeFluidSource) { return this.previewQueryOwner.pickInteractionTarget(includeFluidSource); }
    public ScreenShapeController getShapeController() { return this.previewQueryOwner.getShapeController(); }
    public String fillModeLabel(ShapeFillMode mode) { return this.previewQueryOwner.fillModeLabel(mode); }
public static String shapeDimensionLabel(BuildShape shape) {
        return ScreenShapeController.shapeDimensionLabel(shape);
    }

    public String currentShapeSizeText() { return this.previewQueryOwner.currentShapeSizeText(); }
    public String currentShapeCostText() { return this.previewQueryOwner.currentShapeCostText(); }
    public String pendingShapeStatusText() { return this.previewQueryOwner.pendingShapeStatusText(); }
    public String shapeLabel(BuildShape shape) { return this.previewQueryOwner.shapeLabel(shape); }
    boolean isAltDown() { return this.previewQueryOwner.isAltDown(); }
    boolean isAltDownForInput() { return this.worldQueryOwner.isAltDownForInput(); }
    double currentMouseX() { return this.previewQueryOwner.currentMouseX(); }
    double currentMouseY() { return this.previewQueryOwner.currentMouseY(); }
    int uiWidth() { return this.width; }
    int uiHeight() { return this.height; }
    boolean forwardUnhandledMouseReleased(double x, double y, int button) { return super.mouseReleased(x, y, button); }
    boolean forwardUnhandledMouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged(x, y, button, dx, dy); }
    void forwardUnhandledMouseMoved(double x, double y) { super.mouseMoved(x, y); }
    boolean forwardUnhandledKeyReleased(int key, int scan, int modifiers) { return super.keyReleased(key, scan, modifiers); }
    boolean forwardUnhandledCharTyped(char codePoint, int modifiers) { return super.charTyped(codePoint, modifiers); }
}
