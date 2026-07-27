package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.application.service.ScreenCoordinator;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.*;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.PanelRegistry;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.RenderLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.*;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class BuilderScreen extends Screen {

    private final RtsClientKernel kernel;
    
    private final ScreenBackgroundPanel screenBackgroundPanel;
    private final RtsFloatingWindowLayer floatingWindowLayer;
    private final TopBarPanel topBarPanel;
    private final ColorPickerPanel colorPickerPanel;
    private final GearMenuPanel gearMenuPanel;
    private final RightSidebarPanel rightSidebarPanel;
    private final DownSidebarPanel downSidebarPanel;
    private final LeftSidebarPanel leftSidebarPanel;

    
    private final PanelRegistry panelRegistry = new PanelRegistry();

    private final ScreenCoordinator screenCoordinator;

    

    
    private final BuilderScreenScaleManager scaleManager;

    private final CursorStyleManager cursorStyleManager;
    private final CursorWrapHandler cursorWrapHandler;
    
    private final BuilderScreenMovementHandler movementHandler;
    
    private final BindModeMouseHandler bindModeHandler;
    
    private final SelectionHighlight selectionHighlight;
    
    private final EntityInteractionHandler entityInteractionHandler;
    
    private final BuildInteractionHandler buildInteractionHandler;
    
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    
    private final BuilderScreenEventRouter eventRouter;

    public BuilderScreen() {
        super(Component.literal("RTS Builder"));
        this.kernel = RtsClientKernel.get();
        this.screenBackgroundPanel = new ScreenBackgroundPanel();
        this.colorPickerPanel = new ColorPickerPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.rightSidebarPanel = new RightSidebarPanel();
        this.downSidebarPanel = new DownSidebarPanel();
        this.leftSidebarPanel = new LeftSidebarPanel();
        this.topBarPanel = new TopBarPanel();
        panelRegistry.register(topBarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(leftSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(rightSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(downSidebarPanel, RenderLayer.CONTENT_PANELS);
        this.floatingWindowLayer = new RtsFloatingWindowLayer();
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });

        this.selectionHighlight = new SelectionHighlight();
        this.movementHandler = new BuilderScreenMovementHandler();
        this.bindModeHandler = new BindModeMouseHandler();
        this.entityInteractionHandler = new EntityInteractionHandler(selectionHighlight);
        CameraInputLayer cameraInputLayer = kernel.inputPipeline().findLayer(CameraInputLayer.class);
        this.buildInteractionHandler = new BuildInteractionHandler(kernel, cameraInputLayer);
        this.cursorStyleManager = new CursorStyleManager((mx, my) -> {
            var fwCursor = floatingWindowLayer.resizeCursorAt(mx, my);
            if (fwCursor != RtsPanel.ResizeCursor.DEFAULT) return fwCursor;
            if (floatingWindowLayer.isMouseOverWindowOrResizableBorder(mx, my)) {
                return RtsPanel.ResizeCursor.DEFAULT;
            }
            if (rightSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            if (downSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (rightSidebarPanel.isMouseOverLeftEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (downSidebarPanel.isMouseOverTopEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            return RtsPanel.ResizeCursor.DEFAULT;
        });
        this.cursorWrapHandler = new CursorWrapHandler();
        this.scaleManager = new BuilderScreenScaleManager();
        this.screenCoordinator = new ScreenCoordinator();
        this.eventRouter = new BuilderScreenEventRouter(new BuilderScreenEventRouter.SuperScreen() {
            @Override public boolean mouseClicked(double x, double y, int b) { return BuilderScreen.super.mouseClicked(x, y, b); }
            @Override public boolean mouseReleased(double x, double y, int b) { return BuilderScreen.super.mouseReleased(x, y, b); }
            @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { return BuilderScreen.super.mouseDragged(x, y, b, dx, dy); }
            @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return BuilderScreen.super.mouseScrolled(x, y, sx, sy); }
            @Override public boolean keyPressed(int kc, int sc, int mod) { return BuilderScreen.super.keyPressed(kc, sc, mod); }
            @Override public boolean charTyped(char cp, int mod) { return BuilderScreen.super.charTyped(cp, mod); }
            @Override public void mouseMoved(double x, double y) { BuilderScreen.super.mouseMoved(x, y); }
        });
        eventRouter.registerAll(eventDispatcher, panelRegistry, this, kernel,
                floatingWindowLayer, topBarPanel, leftSidebarPanel, gearMenuPanel,
                movementHandler, bindModeHandler, entityInteractionHandler,
                buildInteractionHandler);
    }

    @Override
    protected void init() {
        super.init();
        
        this.screenBackgroundPanel.init(this);
        this.colorPickerPanel.init(this);
        this.floatingWindowLayer.frontToBackWindows().add(this.colorPickerPanel);
        this.gearMenuPanel.init(this);
        this.floatingWindowLayer.frontToBackWindows().add(this.gearMenuPanel);
        
        panelRegistry.initAll(this);
        
        var eshp = kernel.renderPipeline().entitySelectHighlightPass;
        if (eshp != null) {
            eshp.setHighlightSource(this.selectionHighlight);
        }
        
        var csp = screenCoordinator.getContainerScreenPanel();
        if (csp != null && csp.isOpen()) {
            csp.init(this);
        }
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
        screenCoordinator.closeContainerScreen();
        this.topBarPanel.onRtsExited();
        super.onClose();
        this.cursorStyleManager.restoreDefault();
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            cam.disableCamera();
        }
    }

    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    
    public ColorPickerPanel getColorPickerPanel() {
        return this.colorPickerPanel;
    }

    public int getRightSidebarWidth() {
        return this.rightSidebarPanel.getCurrentWidth();
    }

    
    public int getDownSidebarHeight() {
        return this.downSidebarPanel.getCurrentHeight();
    }

    
    public int getLeftSidebarWidth() {
        return this.leftSidebarPanel.getCurrentWidth();
    }

    public boolean isMouseOverUI(double mouseX, double mouseY) {
        return screenCoordinator.isMouseOverUI(mouseX, mouseY, floatingWindowLayer, topBarPanel);
    }

    
    public void unfocusGridSearch() {
        if (downSidebarPanel != null) {
            downSidebarPanel.getRightLayer().unfocusSearch();
        }
    }

    public boolean isMouseOverRtsPanelApi(double mouseX, double mouseY) {
        
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        
        if (topBarPanel != null && topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY)) {
            return true;
        }
        
        if (mouseY < TopBarLayoutHelper.TOP_BAR_HEIGHT) {
            return true;
        }
        
        int leftW = getLeftSidebarWidth();
        if (leftW > 0 && mouseX < leftW) {
            return true;
        }
        
        int rightW = getRightSidebarWidth();
        if (rightW > 0 && mouseX >= this.width - rightW) {
            return true;
        }
        
        int downH = getDownSidebarHeight();
        if (downH > 0 && mouseY >= this.height - downH) {
            return true;
        }
        return false;
    }

    
    public boolean isClickButtonSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected();
    }

    
    public boolean isInteractiveMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.INTERACTIVE;
    }

    
    public boolean isBlueprintMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.BLUEPRINT;
    }

    
    public boolean isBindModeActive() {
        return leftSidebarPanel != null && leftSidebarPanel.isBindModeActive();
    }

    
    public void clearBoxSelection() {
        kernel.renderPipeline().boxSelector.reset();
        var bsp = kernel.renderPipeline().boxSelectionPass;
        if (bsp != null) bsp.clearCache();
    }

    
    
    

    public void showContainerScreen(Screen screen) {
        screenCoordinator.showContainerScreen(screen, floatingWindowLayer, this);
    }

    public boolean hasContainerScreen() {
        return screenCoordinator.hasContainerScreen();
    }

    public void closeContainerScreen() {
        screenCoordinator.closeContainerScreen();
    }

    
    public double getRtsGuiScale() {
        return scaleManager.getRtsGuiScale();
    }

    
    public String rtsGuiScaleLabel() {
        return scaleManager.rtsGuiScaleLabel();
    }

    
    public void adjustRtsGuiScale(double delta) {
        scaleManager.adjustRtsGuiScale(delta);
    }

    
    public void setRtsGuiScale(double scale) {
        scaleManager.setRtsGuiScale(scale);
    }

    
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        scaleManager.enableRtsScissor(g, x1, y1, x2, y2);
    }

    
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        return scaleManager.renderWithFixedRtsGuiScale(this, g, mouseX, mouseY, partialTick);
    }

    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        return scaleManager.enterFixedRtsGuiScale(this);
    }

    @javax.annotation.Nullable
    private Boolean scaleMouseEvent(double mouseX, double mouseY,
            java.util.function.BiFunction<Double, Double, Boolean> handler) {
        return scaleManager.scaleMouseEvent(this, mouseX, mouseY, handler);
    }

    private boolean scaleMouseEventVoid(double mouseX, double mouseY,
            java.util.function.BiConsumer<Double, Double> handler) {
        return scaleManager.scaleMouseEventVoid(this, mouseX, mouseY, handler);
    }

    
    
    

    @Override
    public void tick() {
        super.tick();
        cursorWrapHandler.tick(kernel.module(CameraModule.class), scaleManager.getRtsGuiScale(),
                getRightSidebarWidth(), getDownSidebarHeight());
        screenCoordinator.tickContainerScreen();
    }

    
    
    

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        if (!scaleManager.isInRenderPass() && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            
            renderPostScaleTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        
        
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        
        
        int rightW = getRightSidebarWidth();
        int downH = getDownSidebarHeight();
        if (screenBackgroundPanel != null && ViewCaptureService.hasValidFrame()) {
            int contentX = 0;
            int contentY = ScreenBackgroundPanel.BACKGROUND_TOP_Y
                + (DownSidebarLayoutHelper.DOWN_BAR_HEIGHT - downH) / 2;
            int contentW = this.width - rightW;
            
            int refContentH = this.height - ScreenBackgroundPanel.BACKGROUND_TOP_Y - DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;
            if (contentW > 0 && refContentH > 0) {
                screenBackgroundPanel.renderCapturedFrameAt(guiGraphics,
                        contentX, contentY, contentW, refContentH);
            }
        }

        
        
        
        boolean mouseOverFloating = floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
        panelRegistry.renderContentPanels(guiGraphics, mouseX, mouseY, partialTick, mouseOverFloating);

        
        if (screenBackgroundPanel != null) {
            screenBackgroundPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (rightSidebarPanel != null) {
            rightSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (downSidebarPanel != null) {
            downSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        
        if (topBarPanel != null) {
            topBarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (leftSidebarPanel != null) {
            leftSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        
        
        RenderSystem.clear(256, false); 
        if (floatingWindowLayer != null) {
            floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        }

        
        if (entityInteractionHandler != null) {
            entityInteractionHandler.validatePanel(this);
        }

        
        if (leftSidebarPanel != null && !leftSidebarPanel.isClickButtonSelected()
                && mouseX >= getLeftSidebarWidth() && mouseX < this.width - rightW
                && mouseY >= ScreenBackgroundPanel.BACKGROUND_TOP_Y
                && mouseY < this.height - downH
                && !isMouseOverUI(mouseX, mouseY)) {
            var bs = kernel.renderPipeline().boxSelector;
            bs.updateHoverFromScreen(Minecraft.getInstance(), this, hasControlDown());
        }

        cursorStyleManager.update(mouseX, mouseY);
        cursorWrapHandler.applyWrapIfPending();

        
        if (downSidebarPanel != null && !isMouseOverRtsPanelApi(mouseX, mouseY)) {
            var selected = downSidebarPanel.getRightLayer().getCurrentSelectedItem();
            if (!selected.isEmpty()) {
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                var pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(mouseX - 12, mouseY - 12, 300);
                guiGraphics.renderItem(selected, 0, 0);
                guiGraphics.renderItemDecorations(Minecraft.getInstance().font, selected, 0, 0);
                pose.popPose();
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            }
        }

        if (Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) {
            Minecraft.getInstance().gui.getDebugOverlay().render(guiGraphics);
        }
    }

    
    private void renderPostScaleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (downSidebarPanel == null) return;
        var stack = downSidebarPanel.getRightLayer().getHoveredSlotStack();
        if (stack.isEmpty()) return;
        g.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
    }

    
    
    
    
    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseClicked(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseClickEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseReleased(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseReleaseEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scaleManager.scaleMouseEventQuad(this, mouseX, mouseY, button, dragX, dragY,
                (x, y, btn, dx, dy) -> mouseDragged(x, y, btn, dx, dy))) {
            return true;
        }
        return eventDispatcher.dispatch(new MouseDragEvent(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseScrolled(x, y, scrollX, scrollY));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseScrollEvent(mouseX, mouseY, scrollX, scrollY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (scaleMouseEventVoid(mouseX, mouseY, (x, y) -> mouseMoved(x, y))) return;
        eventDispatcher.dispatch(new MouseMoveEvent(mouseX, mouseY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return eventDispatcher.dispatch(new KeyPressEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return eventDispatcher.dispatch(new CharEvent(codePoint, modifiers));
    }

}
