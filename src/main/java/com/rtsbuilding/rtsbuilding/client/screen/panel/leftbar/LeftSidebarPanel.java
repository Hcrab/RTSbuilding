package com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.group_button.ActionButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.group_button.SelectButtonGroup;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Objects;

/**
 * 左边框——固定在屏幕左侧的装饰性边框。
 *
 * <p>默认 90px 宽，从屏幕顶部栏底部延伸到屏幕底部。
 * 按钮按组管理，每组独立处理渲染与交互。</p>
 */
public final class LeftSidebarPanel implements RtsPanelApi {

    // ======================== 布局常量 ========================

    /** 按钮顶部间距 */
    private static final int BTN_TOP_MARGIN = 32;
    /** 跨组按钮间距 */
    private static final int CROSS_GAP = 16;

    // ======================== 实例字段 ========================

    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    private BuilderScreen screen;

    /**
     * 当前左边框宽度（初始值使用 {@link LeftSidebarLayoutHelper#SIDEBAR_WIDTH}）。
     * <p>其他面板通过 {@link #getCurrentWidth()} 获取。</p>
     */
    private int currentWidth = LeftSidebarLayoutHelper.SIDEBAR_WIDTH;

    /** 选择组（click + select），位于上方 */
    private final SelectButtonGroup selectGroup = new SelectButtonGroup();
    /** 操作组（bind + rotate + pickup），位于选择组下方 */
    private final ActionButtonGroup actionGroup = new ActionButtonGroup();

    /**
     * 设置当前左边框宽度。
     */
    public void setCurrentWidth(int width) {
        this.currentWidth = Math.max(30, Math.min(width, this.screen != null ? this.screen.width / 4 : 2000));
    }

    /**
     * 返回当前左边框宽度，供其他组件（如 {@link TopBarPanel}）
     * 动态调整布局位置。
     */
    public int getCurrentWidth() {
        return currentWidth;
    }

    @Override
    public void init(BuilderScreen screen) {
        this.screen = Objects.requireNonNull(screen,
                "LeftSidebarPanel.init() called with null screen");
    }

    /**
     * click_button（索引 0）是否处于选中状态。
     * 外部组件（如 {@link com.rtsbuilding.rtsbuilding.client.render.pass.InteractionTargetPass}）
     * 通过此方法判断是否应渲染交互目标高亮。
     */
    public boolean isClickButtonSelected() {
        return selectGroup.isSelected(0);
    }

    /**
     * 切换选择模式（框选 ↔ 点击），由快捷键 B 触发。
     */
    public void toggleSelectMode() {
        selectGroup.toggleSelection();
    }

    /**
     * 切换绑定模式（bind_button 开/关），由快捷键 Ctrl+G 触发。
     */
    public void toggleBindMode() {
        actionGroup.toggleBindButton();
    }

    /**
     * 切换方向旋转模式（direction_rotationbutton 开/关），由快捷键 Ctrl+R 触发。
     */
    public void toggleDirectionRotateMode() {
        actionGroup.toggleDirectionRotateButton();
    }

    /**
     * 切换物品拾取模式（item_pickup_button 开/关），由快捷键 Ctrl+F 触发。
     */
    public void toggleItemPickupMode() {
        actionGroup.toggleItemPickupButton();
    }

    // ======================== 布局快捷方法 ========================

    /** {@link LeftSidebarLayoutHelper#sidebarRect} 的快捷调用，免去重复传参。 */
    private LeftSidebarLayoutHelper.Rect layoutRect() {
        return LeftSidebarLayoutHelper.sidebarRect(
                this.screen.width, this.screen.height, this.currentWidth);
    }

    /** 按钮在左边框中的 X 坐标（距左边缘 4px） */
    private int btnX() {
        LeftSidebarLayoutHelper.Rect sb = layoutRect();
        return sb.x() + 4;
    }

    /** 各组起始 Y 坐标（第一个按钮顶部） */
    private int groupBaseY() {
        return LeftSidebarLayoutHelper.SIDEBAR_TOP_Y + BTN_TOP_MARGIN;
    }

    // ======================== 渲染 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bx = btnX();
        int baseY = groupBaseY();

        // group 0: 选择组（click + select）
        selectGroup.render(g, mouseX, mouseY, bx, baseY);

        // group 1: 操作组（bind + rotate + pickup）
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        actionGroup.render(g, mouseX, mouseY, bx, actionY);

        // 刷新各组 tooltip 状态
        selectGroup.tickTooltips(mouseX, mouseY, bx, baseY);
        actionGroup.tickTooltips(mouseX, mouseY, bx, actionY);
    }

    // ======================== 交互 ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int bx = btnX();
        int baseY = groupBaseY();

        // 先检测上方选择组
        if (selectGroup.mouseClicked(mouseX, mouseY, bx, baseY) >= 0) {
            // 点击 click_button 切换到点击模式 → 清理框选状态
            if (isClickButtonSelected() && screen != null) {
                screen.clearBoxSelection();
            }
            return true;
        }

        // 再检测下方操作组
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;
        if (actionGroup.mouseClicked(mouseX, mouseY, bx, actionY) >= 0) return true;

        return false;
    }

    @Override
    public List<PersistableProperty> persistableProperties() {
        return List.of();
    }

    /**
     * 渲染各按钮的 tooltip 覆盖层（在步骤 4 中调用，确保在其他 UI 之上）。
     */
    public void renderTooltipOverlays(GuiGraphics g, int mouseX, int mouseY) {
        int bx = btnX();
        int baseY = groupBaseY();
        int actionY = baseY + selectGroup.totalHeight() + CROSS_GAP;

        selectGroup.renderTooltipOverlay(g, bx, baseY,
                this.screen.width, this.screen.height);
        actionGroup.renderTooltipOverlay(g, bx, actionY,
                this.screen.width, this.screen.height);
    }
}
