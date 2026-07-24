package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.ActionButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.SelectButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Objects;


public final class LeftSidebarPanel implements RtsPanelApi {

    

    
    private static final int BTN_TOP_MARGIN = 32;
    
    private static final int CROSS_GAP = 16;

    

    
    private BuilderScreen screen;

    
    private int currentWidth = LeftSidebarLayoutHelper.SIDEBAR_WIDTH;

    
    private final LeftSidebarLayoutHelper layout = new LeftSidebarLayoutHelper();

    
    private final SelectButtonGroup selectGroup = new SelectButtonGroup();
    
    private final ActionButtonGroup actionGroup = new ActionButtonGroup();

    
    public void setCurrentWidth(int width) {
        this.currentWidth = Math.max(30, Math.min(width, this.screen != null ? this.screen.width / 4 : 2000));
    }

    
    public int getCurrentWidth() {
        return currentWidth;
    }

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "LeftSidebarPanel.init() called with null screen");
    }

    
    public boolean isClickButtonSelected() {
        return selectGroup.isSelected(0);
    }

    
    public void toggleSelectMode() {
        selectGroup.toggleSelection();
    }

    
    public boolean isBindModeActive() {
        return actionGroup.isSelected(0);
    }

    
    public void toggleBindMode() {
        actionGroup.toggleBindButton();
    }

    
    public void toggleDirectionRotateMode() {
        actionGroup.toggleDirectionRotateButton();
    }

    
    public void toggleItemPickupMode() {
        actionGroup.toggleItemPickupButton();
    }

    

    
    private LeftSidebarLayoutHelper.Rect layoutRect() {
        return layout.sidebarRect(
                this.screen.width, this.screen.height, this.currentWidth);
    }

    
    private int btnX() {
        LeftSidebarLayoutHelper.Rect sb = layoutRect();
        return sb.x() + 4;
    }

    
    private int groupBaseY() {
        return LeftSidebarLayoutHelper.SIDEBAR_TOP_Y + BTN_TOP_MARGIN;
    }

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bx = btnX();
        int baseY = groupBaseY();

        
        selectGroup.render(g, mouseX, mouseY, bx, baseY);

        
        boolean showBind = screen != null && screen.isInteractiveMode();
        actionGroup.setShowBindButton(showBind);

        
        boolean showRotate = screen == null || !screen.isInteractiveMode();
        actionGroup.setShowRotateButton(showRotate);

        
        boolean blueprint = screen != null && screen.isBlueprintMode();
        actionGroup.setBlueprintMode(blueprint);

        
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        actionGroup.render(g, mouseX, mouseY, bx, actionY);

        
        selectGroup.tickTooltips(mouseX, mouseY, bx, baseY);
        actionGroup.tickTooltips(mouseX, mouseY, bx, actionY);
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int bx = btnX();
        int baseY = groupBaseY();

        
        if (selectGroup.mouseClicked(mouseX, mouseY, bx, baseY) >= 0) {
            
            if (isClickButtonSelected() && screen != null) {
                screen.clearBoxSelection();
            }
            return true;
        }

        
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        if (actionGroup.mouseClicked(mouseX, mouseY, bx, actionY) >= 0) return true;

        return false;
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return List.of();
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        renderTooltipOverlays(g, mouseX, mouseY);
    }

    
    private void renderTooltipOverlays(GuiGraphics g, int mouseX, int mouseY) {
        int bx = btnX();
        int baseY = groupBaseY();
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;

        selectGroup.renderTooltipOverlay(g, bx, baseY,
                this.screen.width, this.screen.height);
        actionGroup.renderTooltipOverlay(g, bx, actionY,
                this.screen.width, this.screen.height);
    }
}
