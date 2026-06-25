package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试选项弹出菜单——点击 button_right 后显示。
 *
 * <p>提供区块显示、碰撞箱显示、坐标轴彩色线条等开关选项，
 * 每个选项以复选框 + 标签形式展示，点击即切换状态并执行对应回调。</p>
 */
public final class DebugMenuPopup {

    /** 单个选项项：标签 + 切换回调 */
    public record DebugToggleItem(Component label, ToggleAction action) {}

    @FunctionalInterface
    public interface ToggleAction {
        void onToggle(boolean newState);
    }

    private final DebugToggleItem[] items;
    private final boolean[] states;
    private boolean open;
    private int x;
    private int y;

    // ======================== 外观常量 ========================

    /** 菜单项高度 */
    private static final int ITEM_HEIGHT = 22;
    /** 菜单左右边缘与内容的 padding */
    private static final int PAD_H = 6;
    /** 菜单上下边框与内容区域的 padding */
    private static final int PAD_V = 4;
    /** 菜单宽度（足够容纳最长的选项文字） */
    private static final int POPUP_WIDTH = 260;
    /** 复选框绘制尺寸 */
    private static final int CHECKBOX_SIZE = 12;
    /** 复选框与文字间距 */
    private static final int CHECKBOX_TEXT_GAP = 4;
    /** 勾选标记内边距 */
    private static final int CHECKMARK_INSET = 3;

    /** 复选框边框颜色 */
    private static final int CHECKBOX_COLOR = 0xFF888888;
    /** 勾选标记颜色 */
    private static final int CHECKMARK_COLOR = 0xFFFFFFFF;
    /** 悬浮背景色 */
    private static final int BG_HOVER = 0x442A3442;

    // ======================== 动画器 ========================

    /** 每个选项项独立的悬浮动画器 */
    private final List<SmoothAnimator> hoverAnims;
    /** 上一帧悬浮的选项索引 */
    private int lastHoveredIndex = -1;

    /**
     * @param items 选项列表（带默认状态）
     */
    public DebugMenuPopup(List<DebugToggleItem> items) {
        this(items, null);
    }

    /**
     * @param items       选项列表（带默认状态）
     * @param defaultStates 各选项的默认勾选状态，null 表示全部不勾选
     */
    public DebugMenuPopup(List<DebugToggleItem> items, boolean[] defaultStates) {
        this.items = items.toArray(new DebugToggleItem[0]);
        this.states = new boolean[items.size()];
        if (defaultStates != null && defaultStates.length == items.size()) {
            System.arraycopy(defaultStates, 0, this.states, 0, items.size());
        }
        this.hoverAnims = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            hoverAnims.add(AnimationFactory.createHoverAnim());
        }
    }

    // ======================== 位置 ========================

    /** 设置弹出菜单左上角位置 */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // ======================== 打开/关闭 ========================

    /** 切换打开/关闭状态 */
    public void toggle() {
        this.open = !this.open;
        if (!this.open) {
            // 关闭时重置所有悬浮动画
            for (SmoothAnimator anim : hoverAnims) {
                anim.snapTo(0.0f);
            }
            lastHoveredIndex = -1;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        if (this.open) toggle();
    }

    /**
     * 从外部设置指定索引的选项状态（用于持久化恢复后同步显示状态）。
     *
     * @param index 选项索引
     * @param state 新的勾选状态
     */
    public void setItemState(int index, boolean state) {
        if (index >= 0 && index < states.length) {
            states[index] = state;
        }
    }

    // ======================== 尺寸与命中检测 ========================

    private int menuWidth() {
        return POPUP_WIDTH;
    }

    private int menuHeight() {
        return PAD_V * 2 + items.length * ITEM_HEIGHT;
    }

    /** 检测像素坐标是否在弹出菜单区域内 */
    public boolean contains(int mx, int my) {
        if (!open) return false;
        return mx >= x && mx < x + menuWidth()
                && my >= y && my < y + menuHeight();
    }

    /** 获取指定选项项的 Y 坐标 */
    private int itemY(int index) {
        return y + PAD_V + index * ITEM_HEIGHT;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染弹出菜单。
     * <p>在 TopBarPanel 的 render() 末尾调用。</p>
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int pw = menuWidth();
        int ph = menuHeight();

        // 1) 九宫格面板背景
        RtsClientUiUtil.drawNineSlicePanel(g, x, y, pw, ph, false);

        // 2) 检测当前悬浮项
        int hoveredIndex = -1;
        for (int i = 0; i < items.length; i++) {
            int iy = itemY(i);
            if (mouseX >= x + PAD_H && mouseX < x + pw - PAD_H
                    && mouseY >= iy && mouseY < iy + ITEM_HEIGHT) {
                hoveredIndex = i;
                break;
            }
        }

        // 3) 悬浮状态变更 → 启动动画
        if (hoveredIndex != lastHoveredIndex) {
            if (lastHoveredIndex >= 0 && lastHoveredIndex < hoverAnims.size()) {
                hoverAnims.get(lastHoveredIndex).start(0.0f);
            }
            if (hoveredIndex >= 0 && hoveredIndex < hoverAnims.size()) {
                hoverAnims.get(hoveredIndex).start(1.0f);
            }
            lastHoveredIndex = hoveredIndex;
        }

        // 4) 推进所有动画
        for (SmoothAnimator anim : hoverAnims) {
            anim.tick();
        }

        // 5) 绘制每个选项
        for (int i = 0; i < items.length; i++) {
            int iy = itemY(i);
            float t = hoverAnims.get(i).getValue();

            // 悬浮背景（从完全透明到半透明深色）
            if (t > 0.001f) {
                int bgColor = SmoothAnimator.lerpColor(0x00000000, BG_HOVER, t);
                g.fill(x + PAD_H, iy, x + pw - PAD_H, iy + ITEM_HEIGHT, bgColor);
            }

            // 复选框
            int checkX = x + PAD_H;
            int checkY = iy + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;
            drawCheckbox(g, checkX, checkY, states[i]);

            // 文字
            int textColor = t > 0.5f ? ThemeManager.getHoverTextColor() : ThemeManager.getTextColor();
            String label = items[i].label().getString();
            int textX = checkX + CHECKBOX_SIZE + CHECKBOX_TEXT_GAP;
            int textY = iy + (ITEM_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2 + 1;
            RtsClientUiUtil.drawUiText(g, label, textX, textY, textColor);
        }
    }

    /** 绘制复选框（边框 + 勾选标记） */
    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        // 边框
        g.fill(x, y, x + CHECKBOX_SIZE, y + 1, CHECKBOX_COLOR);                                // 上
        g.fill(x, y + CHECKBOX_SIZE - 1, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, CHECKBOX_COLOR); // 下
        g.fill(x, y, x + 1, y + CHECKBOX_SIZE, CHECKBOX_COLOR);                                // 左
        g.fill(x + CHECKBOX_SIZE - 1, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, CHECKBOX_COLOR); // 右

        if (checked) {
            // 勾选标记：填充内部矩形
            g.fill(x + CHECKMARK_INSET, y + CHECKMARK_INSET,
                    x + CHECKBOX_SIZE - CHECKMARK_INSET, y + CHECKBOX_SIZE - CHECKMARK_INSET,
                    CHECKMARK_COLOR);
        }
    }

    // ======================== 点击处理 ========================

    /**
     * 处理鼠标点击。
     *
     * @return true 表示事件被消费（点击到了某个选项项）
     */
    public boolean handleClick(int mx, int my) {
        if (!open) return false;

        for (int i = 0; i < items.length; i++) {
            int iy = itemY(i);
            if (mx >= x + PAD_H && mx < x + menuWidth() - PAD_H
                    && my >= iy && my < iy + ITEM_HEIGHT) {
                // 切换状态
                states[i] = !states[i];
                // 执行回调
                if (items[i].action() != null) {
                    items[i].action().onToggle(states[i]);
                }
                return true;
            }
        }
        return false;
    }
}
