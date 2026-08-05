package com.rtsbuilding.rtsbuilding.client.presentation.panel.gear;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants.*;

public final class GearMenuPanel extends RtsPanel {
    private static final int LEGACY_DEFAULT_WINDOW_W = 200;
    private static final int LEGACY_DEFAULT_WINDOW_H = 284;
    private static final int DEFAULT_WINDOW_W = 253;
    private static final int MIN_WINDOW_W = 187;
    
    private static final int SCROLLBAR_RIGHT_GAP = 11;
    
    private static final int CONTENT_WIDTH_REDUCTION = 6;
    
    private static final int CONTENT_TOP_PAD = 8;
    private CameraModule cameraModule = null;
    private final RenderingSection renderingSection = new RenderingSection();
    private final PersonalizationSection personalizationSection = new PersonalizationSection();
    private final OperationSection operationSection = new OperationSection();

    
    private final ScrollBar scrollBar = new ScrollBar();

    @Override
    public void init(BuilderScreen screen) {
        super.init(screen);
        this.resizable = true;
        RtsClientKernel kernel = RtsClientKernel.get();
        this.cameraModule = kernel.module(CameraModule.class);
        this.operationSection.setCameraModule(this.cameraModule);
        this.renderingSection.setColorPickerPanel(
                ((BuilderScreen) screen).getColorPickerPanel());
        this.renderingSection.setColorPickerButtonParent(this);
    }

    public void open() {
        setOpen(true);
        markBroughtToFront();
    }

    

    
    private int totalSectionHeight(int cw) {
        return CONTENT_TOP_PAD
                + renderingSection.totalHeight(cw)
                + personalizationSection.totalHeight(cw)
                + operationSection.totalHeight(cw);
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        
        renderingSection.renderColorTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = contentX();
        int cy = contentY();
        int cw = contentWidth();
        int ch = contentHeight();

        
        int totalH = totalSectionHeight(cw);
        scrollBar.setContent(totalH, ch);

        int scroll = scrollBar.getScroll();

        
        int sectionRenderW = cw - CONTENT_WIDTH_REDUCTION;

        
        int scrolledCy = cy - scroll;
        int sectionY = scrolledCy;
        renderingSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);
        sectionY += renderingSection.totalHeight(cw);
        personalizationSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);
        sectionY += personalizationSection.totalHeight(cw);
        operationSection.render(g, mouseX, mouseY, cx, sectionY, sectionRenderW);

        
        if (scrollBar.isVisible()) {
            int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
            scrollBar.render(g, barX, cy, ch);
        }
    }

    

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = contentX();
            int cy = contentY();
            int cw = contentWidth();
            int ch = contentHeight();

            
            if (scrollBar.isVisible()) {
                int barX = cx + cw - SCROLLBAR_RIGHT_GAP;
                if (scrollBar.handleClick(mouseX, mouseY, barX, cy, ch)) {
                    return;
                }
            }

            
            int sectionClickW = cw - CONTENT_WIDTH_REDUCTION;

            int scroll = scrollBar.getScroll();
            int scrolledCy = cy - scroll;

            int sectionCY = scrolledCy;
            if (renderingSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW)) return;
            sectionCY += renderingSection.totalHeight(cw);
            if (personalizationSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW)) return;
            sectionCY += personalizationSection.totalHeight(cw);
            operationSection.handleClick(mouseX, mouseY, cx, sectionCY, sectionClickW);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            
            if (scrollBar.isDragging()) {
                int cy = contentY();
                int ch = contentHeight();
                scrollBar.handleDrag(mouseY, cy, ch);
                return true;
            }
            
            if (operationSection.isSliderDragging()) {
                operationSection.handleSliderDrag(mouseX);
                return true;
            }
            
            if (renderingSection.isSliderDragging()) {
                renderingSection.handleSliderDrag(mouseX);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.endDrag();
        operationSection.endSliderDrag();
        renderingSection.endSliderDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        
        if (operationSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        
        if (renderingSection.handleSliderScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.settings.title");
    }

    @Override
    protected int getDefaultWidth() {
        return DEFAULT_WINDOW_W;
    }

    @Override
    protected int getDefaultHeight() {
        return GEAR_MENU_H;
    }

    @Override
    public int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    public int getMinWindowHeight() {
        return GEAR_MENU_MIN_H;
    }

    @Override
    protected boolean shouldUseSdfBackground() {
        return true;
    }

    @Override
    protected int getPanelBgColor() { return DarkUiPalette.bg(); }

    @Override
    protected int getPanelBorderColor() { return DarkUiPalette.accent(); }

    @Override
    protected int getPanelHoverBgColor() { return DarkUiPalette.hoverBorder(); }

    @Override
    protected int getTitleBarBgColor() { return DarkUiPalette.border(); }

    @Override
    protected int contentY() { return bounds.getY() + getTitleBarHeight() + 2; }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean legacyDefaultBounds = width == LEGACY_DEFAULT_WINDOW_W && height == LEGACY_DEFAULT_WINDOW_H;
        int restoredWidth = legacyDefaultBounds ? DEFAULT_WINDOW_W : width;
        int restoredHeight = legacyDefaultBounds ? GEAR_MENU_H : height;
        super.setBounds(x, y, restoredWidth, restoredHeight);
    }

    @Override
    protected int getMaxWindowWidth() {
        if (this.screen == null) {
            return super.getMaxWindowWidth();
        }
        int viewportLimit = Math.max(getMinWindowWidth(), (this.screen.width * 2) / 3);
        return Math.min(super.getMaxWindowWidth(), viewportLimit);
    }

    @Override
    protected int getMaxWindowHeight() {
        if (this.screen == null) {
            return super.getMaxWindowHeight();
        }
        int viewportLimit = Math.max(getMinWindowHeight(), (this.screen.height * 2) / 3);
        return Math.min(super.getMaxWindowHeight(), viewportLimit);
    }

    @Override
    protected void computeDefaultPosition() {
        setWindowX(Math.max(8, (this.screen.width - getWindowWidth()) / 2));
        setWindowY(Mth.clamp((this.screen.height - getWindowHeight()) / 2,
                TOP_H + 6,
                Math.max(TOP_H + 6, this.screen.height - getWindowHeight() - 8)));
    }

    public CameraModule getCameraModule() {
        return cameraModule;
    }
}
