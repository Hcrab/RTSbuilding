package com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.ActionButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.BuildDestroyButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.SelectButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.group_button.UltimineButtonGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class LeftSidebarPanel implements RtsPanelApi {

    

    
    private static final int BTN_TOP_MARGIN = 32;
    
    private static final int CROSS_GAP = 16;

    

    
    private BuilderScreen screen;

    
    private int currentWidth = LeftSidebarLayoutHelper.SIDEBAR_WIDTH;

    
    private final LeftSidebarLayoutHelper layout = new LeftSidebarLayoutHelper();

    
    private final SelectButtonGroup selectGroup = new SelectButtonGroup();
    
    private final ActionButtonGroup actionGroup = new ActionButtonGroup();
    
    private final BuildDestroyButtonGroup buildDestroyGroup = new BuildDestroyButtonGroup();
    
    private final UltimineButtonGroup ultimineGroup = new UltimineButtonGroup();

    
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
        if (selectGroup.isDisabled()) {
            return;
        }
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
        syncItemPickupState();
    }

    /**
     * 把物品拾取（漏斗）开关状态同步到服务端：
     * 服务端需要知道漏斗已开启才会真正吸物，否则漏斗请求会被静默拒绝。
     */
    private void syncItemPickupState() {
        com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway
                .sendSetFunnelEnabled(isItemPickupActive());
    }

    
    public boolean isItemPickupActive() {
        return actionGroup.isSelected(2);
    }

    
    public boolean isUltimineActive() {
        return ultimineGroup.isSelected(0);
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

        
        boolean buildMode = screen != null && screen.isBuildMode();
        buildDestroyGroup.setShow(buildMode);
        ultimineGroup.setShow(buildMode);
        // 建造模式 + 连锁挖掘启用 → 禁用选择（点击/框选）模式按钮，并显示覆盖层
        selectGroup.setDisabled(buildMode && ultimineGroup.isSelected(0));
        // 连锁挖掘启用 → 直接禁用建造/破坏按钮组
        buildDestroyGroup.setDisabled(ultimineGroup.isSelected(0));

        
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        actionGroup.render(g, mouseX, mouseY, bx, actionY);

        
        int buildDestroyY = actionY + actionGroup.visibleHeight() + CROSS_GAP;
        buildDestroyGroup.render(g, mouseX, mouseY, bx, buildDestroyY);

        int ultimineY = buildDestroyY + buildDestroyGroup.visibleHeight() + CROSS_GAP;
        ultimineGroup.render(g, mouseX, mouseY, bx, ultimineY);

        
        selectGroup.tickTooltips(mouseX, mouseY, bx, baseY);
        actionGroup.tickTooltips(mouseX, mouseY, bx, actionY);
        buildDestroyGroup.tickTooltips(mouseX, mouseY, bx, buildDestroyY);
        ultimineGroup.tickTooltips(mouseX, mouseY, bx, ultimineY);
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
        int clicked = actionGroup.mouseClicked(mouseX, mouseY, bx, actionY);
        if (clicked >= 0) {
            // 物品拾取按钮（index 2）被点击时同步开关状态到服务端
            if (clicked == 2) {
                syncItemPickupState();
            }
            return true;
        }

        
        int buildDestroyY = actionY + actionGroup.visibleHeight() + CROSS_GAP;
        if (buildDestroyGroup.mouseClicked(mouseX, mouseY, bx, buildDestroyY) >= 0) {
            return true;
        }

        
        int ultimineY = buildDestroyY + buildDestroyGroup.visibleHeight() + CROSS_GAP;
        if (ultimineGroup.mouseClicked(mouseX, mouseY, bx, ultimineY) >= 0) {
            return true;
        }

        return false;
    }

    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        renderTooltipOverlays(g, mouseX, mouseY);
    }

    private void renderTooltipOverlays(GuiGraphics g, int mouseX, int mouseY) {
        int bx = btnX();
        int baseY = groupBaseY();
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        int buildDestroyY = actionY + actionGroup.visibleHeight() + CROSS_GAP;
        int ultimineY = buildDestroyY + buildDestroyGroup.visibleHeight() + CROSS_GAP;

        selectGroup.renderTooltipOverlay(g, bx, baseY,
                this.screen.width, this.screen.height);
        actionGroup.renderTooltipOverlay(g, bx, actionY,
                this.screen.width, this.screen.height);
        buildDestroyGroup.renderTooltipOverlay(g, bx, buildDestroyY,
                this.screen.width, this.screen.height);
        ultimineGroup.renderTooltipOverlay(g, bx, ultimineY,
                this.screen.width, this.screen.height);
    }
}
