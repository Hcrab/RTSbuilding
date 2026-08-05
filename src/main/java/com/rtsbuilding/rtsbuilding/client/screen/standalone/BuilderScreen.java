package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import cpw.mods.fml.common.Loader;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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
        super(new ChatComponentText("RTS Builder"));
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
                () -> this.mc,
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

public FontRenderer font() {
        return this.fontRendererObj;
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
        if (this.mc != null && this.mc.thePlayer != null) {
            com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(this.mc.thePlayer,
                    new ChatComponentTranslation("message.rtsbuilding.quick_build.remote_place_locked"), true);
        }
    }

    public void syncQuickBuildActiveState() { this.lifecycleOwner.syncQuickBuildActiveState(); }
public net.minecraft.client.Minecraft getMinecraft() {
        return this.mc;
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

public GuiTextField getSearchBox() {
        return this.searchBox;
    }

public GuiTextField getCraftSearchBox() {
        return this.craftSearchBox;
    }

    @Override
    public void initGui() { super.initGui(); this.lifecycleOwner.init(); }
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    @Override
    public void onGuiClosed() {
        // 1.12 的 onGuiClosed 同时覆盖“玩家主动关闭”和“被容器 GUI 替换”。
        // 主线只在主动 onClose 时退出 RTS；远程箱子覆盖屏幕时只能执行 removed 清理，
        // 否则随后按 Esc 关闭箱子就已经收到了错误的相机关闭状态。
        this.lifecycleOwner.removed();
        super.onGuiClosed();
    }
    @Override
    public void updateScreen() { super.updateScreen(); this.lifecycleOwner.tick(); }
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
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.legacyDragButton = button;
        this.legacyDragLastX = mouseX;
        this.legacyDragLastY = mouseY;
        mouseClicked((double) mouseX, (double) mouseY, button);
    }

    void selectPlacementStateFromWheel( PlacementStateWheel.PlacementChoice choice, int button) { this.pointerActionOwner.selectPlacementStateFromWheel(choice, button); }
    void closePlacementStateWheelFromPointer(int button) { this.pointerActionOwner.closePlacementStateWheelFromPointer(button); }
    void selectModeFromWheelPointer(BuilderMode selectedMode, int button) { this.pointerActionOwner.selectModeFromWheelPointer(selectedMode, button); }
    void closeModeWheelFromPointer(int button) { this.pointerActionOwner.closeModeWheelFromPointer(button); }
boolean forwardUnhandledMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean handleBlueprintCaptureClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleBlueprintCaptureClicks(mouseX, mouseY, button); }
    boolean handleHomeSelectionClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleHomeSelectionClicks(mouseX, mouseY, button); }
    boolean handleOverlayClicks(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleOverlayClicks(mouseX, mouseY, button); }
    boolean handleAreaMineClickBlock(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleAreaMineClickBlock(mouseX, mouseY, button); }
    boolean handleLeftClickInteractions(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleLeftClickInteractions(mouseX, mouseY, button); }
    boolean handleWorldClickActions(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleWorldClickActions(mouseX, mouseY, button); }
    boolean handleAdvancedShapeHandleClick(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleAdvancedShapeHandleClick(mouseX, mouseY, button); }
    boolean handleBatchConfirmMouse(double mouseX, double mouseY, int button) { return this.pointerActionOwner.handleBatchConfirmMouse(mouseX, mouseY, button); }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return this.pointerGestureOwner.mouseReleased(mouseX, mouseY, button); }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return this.pointerGestureOwner.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
    public void mouseMoved(double mouseX, double mouseY) { this.pointerGestureOwner.mouseMoved(mouseX, mouseY); }
    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        mouseReleased((double) mouseX, (double) mouseY, button);
        if (button == this.legacyDragButton) {
            this.legacyDragButton = -1;
            this.legacyDragLastX = Double.NaN;
            this.legacyDragLastY = Double.NaN;
        }
    }
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        double dragX = button == this.legacyDragButton && Double.isFinite(this.legacyDragLastX)
                ? mouseX - this.legacyDragLastX : 0.0D;
        double dragY = button == this.legacyDragButton && Double.isFinite(this.legacyDragLastY)
                ? mouseY - this.legacyDragLastY : 0.0D;
        this.legacyDragButton = button;
        this.legacyDragLastX = mouseX;
        this.legacyDragLastY = mouseY;
        mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
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
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * this.width / Math.max(1, this.mc.displayWidth);
            int y = this.height - Mouse.getEventY() * this.height / Math.max(1, this.mc.displayHeight) - 1;
            mouseScrolled(x, y, 0.0D, wheel > 0 ? 1.0D : -1.0D);
        }
    }

    /**
     * Handles key press events. Dispatches to dialogs, blueprint, overlay, world interaction,
     * search box, tool slot, and sensitivity handlers in priority order.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.keyPressRouter.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 1.12 把物理按键和字符输入合并在同一次 keyTyped 回调里。
        // 即使文本框已经消费了按键阶段，也必须继续投递可打印字符；否则搜索、AI 和插件输入框只能获得空字符。
        boolean keyHandled = keyPressed(keyCode, 0, 0);
        boolean charHandled = !Character.isISOControl(typedChar) && charTyped(typedChar, 0);
        if (keyHandled || charHandled) return;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // 旧版没有独立 Screen#onClose；只在未被子面板消费的 Esc 上补主线主动关闭语义。
            this.lifecycleOwner.onClose();
        }
        super.keyTyped(typedChar, keyCode);
    }
    @Override
    public void handleKeyboardInput() {
        int keyCode = Keyboard.getEventKey() == Keyboard.KEY_NONE
                ? Keyboard.getEventCharacter() + 256
                : Keyboard.getEventKey();
        if (Keyboard.getEventKeyState()) {
            super.handleKeyboardInput();
        } else {
            keyReleased(keyCode, 0, 0);
        }
    }

    void closePlacementStateWheelFromKey() { this.keyboardActionOwner.closePlacementStateWheelFromKey(); }
boolean forwardUnhandledKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
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
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return this.keyboardSessionOwner.keyReleased(keyCode, scanCode, modifiers); }
    void handleRtsFlightToggle() { this.keyboardSessionOwner.handleRtsFlightToggle(); }
    boolean handleModeKeyPressed(int keyCode, int scanCode) { return this.keyboardSessionOwner.handleModeKeyPressed(keyCode, scanCode); }
    boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) { return this.keyboardSessionOwner.switchToModeFromKey(mode, funnelEnabled); }
    public boolean charTyped(char codePoint, int modifiers) { return this.keyboardSessionOwner.charTyped(codePoint, modifiers); }
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        this.renderOwner.render(new LegacyGuiGraphics(this.mc, this.width, this.height), mouseX, mouseY, partialTick);
    }
    public void render(LegacyGuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) { this.renderOwner.render(guiGraphics, mouseX, mouseY, partialTick); }
    void resetHoverStates() { this.renderOwner.resetHoverStates(); }
    void renderHoveredItemTooltips(LegacyGuiGraphics g, int mouseX, int mouseY) { this.renderOwner.renderHoveredItemTooltips(g, mouseX, mouseY); }
    public void renderTopGuideHint(LegacyGuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) { this.renderOwner.renderTopGuideHint(g, topButtons); }
    void drawGuiBindCursor(LegacyGuiGraphics g, int mouseX, int mouseY) { this.renderOwner.drawGuiBindCursor(g, mouseX, mouseY); }
static boolean hasRecipeViewerLoaded() {
        return Loader.isModLoaded("jei")
                || Loader.isModLoaded("emi")
                || Loader.isModLoaded("roughlyenoughitems");
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
    double[] screenAxisForDirection(EnumFacing direction) { return this.modeSessionOwner.screenAxisForDirection(direction); }
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
    String resolveGuiBindingItemId(RayTraceResult hit) { return this.worldQueryOwner.resolveGuiBindingItemId(hit); }
    public boolean canUseToolSlotShapeSource() { return this.worldQueryOwner.canUseToolSlotShapeSource(); }
    boolean tryAssignQuickSlotFromToolSelection(int pinIndex) { return this.worldQueryOwner.tryAssignQuickSlotFromToolSelection(pinIndex); }
    public void setSelectedToolSlot(int slot) { this.worldQueryOwner.setSelectedToolSlot(slot); }
    public BlueprintGhostPreview getBlueprintGhostPreview() { return this.previewQueryOwner.getBlueprintGhostPreview(); }
    public List<BlockPos> collectUltiminePreviewBlocks() { return this.previewQueryOwner.collectUltiminePreviewBlocks(); }
    List<BlockPos> filterToBounds(List<BlockPos> blocks) { return this.previewQueryOwner.filterToBounds(blocks); }
    boolean isMovePlayerActionMouse(int button) { return this.previewQueryOwner.isMovePlayerActionMouse(button); }
    boolean isMovePlayerActionKey(int keyCode, int scanCode) { return this.previewQueryOwner.isMovePlayerActionKey(keyCode, scanCode); }
    boolean handleMovePlayerActionAt(double mouseX, double mouseY) { return this.previewQueryOwner.handleMovePlayerActionAt(mouseX, mouseY); }
    public void enableRtsScissor(LegacyGuiGraphics g, int x1, int y1, int x2, int y2) { this.previewQueryOwner.enableRtsScissor(g, x1, y1, x2, y2); }
    public String trimToWidth(String text, int maxWidth) { return this.previewQueryOwner.trimToWidth(text, maxWidth); }
    public String text(String key, Object... args) { return this.previewQueryOwner.text(key, args); }
    public String selectedItemStatusLabel() { return this.previewQueryOwner.selectedItemStatusLabel(); }
    boolean hasMainHandItem() { return this.worldQueryOwner.hasMainHandItem(); }
    ItemStack resolveCursorPreview() { return this.previewQueryOwner.resolveCursorPreview(); }
    boolean shouldRenderFunnelCursor() { return this.previewQueryOwner.shouldRenderFunnelCursor(); }
    public Vec3d computeCursorRayDirection() { return this.previewQueryOwner.computeCursorRayDirection(); }
    public Vec3d currentRayOrigin() { return this.previewQueryOwner.currentRayOrigin(); }
    public EnumFacing currentCameraHorizontalDirection() { return this.previewQueryOwner.currentCameraHorizontalDirection(); }
    public PlacedBlockRotationHandles getRotationHandles() { return this.previewQueryOwner.getRotationHandles(); }
    public RayTraceResult pickBlockHit() { return this.previewQueryOwner.pickBlockHit(); }
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
    boolean forwardUnhandledMouseReleased(double x, double y, int button) { return false; }
    boolean forwardUnhandledMouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    void forwardUnhandledMouseMoved(double x, double y) { }
    boolean forwardUnhandledKeyReleased(int key, int scan, int modifiers) { return false; }
    boolean forwardUnhandledCharTyped(char codePoint, int modifiers) { return false; }
}
