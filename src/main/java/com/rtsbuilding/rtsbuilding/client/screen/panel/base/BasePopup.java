package com.rtsbuilding.rtsbuilding.client.screen.panel.base;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.SmoothAnimator;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 弹出菜单基类——提供通用的打开/关闭、悬浮动画、九宫格背景渲染框架。
 *
 * <p>子类只需实现：</p>
 * <ul>
 *   <li>{@link #getPopupWidth()} — 菜单宽度</li>
 *   <li>{@link #renderItem(GuiGraphics, int, int, float)} — 每个菜单项的绘制</li>
 *   <li>{@link #onItemClick(int)} — 菜单项点击回调</li>
 *   <li>{@link #getItemCount()} — 菜单项数量</li>
 * </ul>
 *
 * <p>基类统一处理：九宫格面板背景渲染、悬浮检测循环、动画推进与触发、
 * 尺寸计算与命中检测、打开/关闭状态管理。</p>
 */
public abstract class BasePopup {

    /** 当前是否打开 */
    protected boolean open;
    /** 弹出菜单左上角 X */
    protected int x;
    /** 弹出菜单左上角 Y */
    protected int y;

    /** 每个菜单项独立的悬浮状态管理器 */
    private HoverStateManager[] hoverStates;

    /** 每个菜单项的内容宽度（不含 padH），用于自动计算弹出菜单总宽度 */
    private int[] itemContentWidths;
    /** 弹出菜单最小宽度 */
    private final int minPopupWidth = 80;

    // ======================== 外观常量（子类可重写） ========================

    /** 菜单项高度 */
    protected int getItemHeight() { return 22; }
    /** 菜单左右边缘与内容的 padding */
    protected int getPadH() { return 6; }
    /** 菜单上下边框与内容区域的 padding */
    protected int getPadV() { return 4; }
    /** 悬浮背景起始色（完全透明） */
    protected int bgNormal() { return 0x00000000; }
    /** 悬浮背景终止色（半透明深色） */
    protected int bgHover() { return 0x442A3442; }

    // ======================== 子类必须实现的抽象方法 ========================

    /**
     * 菜单总宽度。
     * <p>默认根据 {@link #itemContentWidths} 自动计算：最大内容宽度 + 2 &times; padH。
     * 若未注册内容宽度或计算结果小于 {@link #minPopupWidth}，则返回最小值。
     * 子类可重写此方法以覆盖默认行为。</p>
     */
    protected int getPopupWidth() {
        if (itemContentWidths == null || itemContentWidths.length == 0) {
            return minPopupWidth;
        }
        int max = 0;
        for (int w : itemContentWidths) {
            if (w > max) max = w;
        }
        return Math.max(minPopupWidth, max + getPadH() * 2);
    }
    /** 菜单项数量 */
    protected abstract int getItemCount();
    /**
     * 渲染单个菜单项。
     *
     * @param g      GuiGraphics
     * @param index  菜单项索引
     * @param itemY  该菜单项的左上角 Y 坐标
     * @param hoverT 悬浮动画进度（0.0 ~ 1.0）
     */
    protected abstract void renderItem(GuiGraphics g, int index, int itemY, float hoverT);
    /**
     * 点击菜单项时的回调。
     *
     * @param index 被点击的菜单项索引
     * @return true 表示事件被消费
     */
    protected abstract boolean onItemClick(int index);

    // ======================== 内容宽度注册 ========================

    /**
     * 注册每个菜单项的内容宽度（不含左右 padding）。
     * <p>调用后 {@link #getPopupWidth()} 会自动取最大值 + 2 &times; padH 作为菜单总宽度。
     * 若某些项需要更宽的宽度（如带图标的项），子类在计算时需将图标宽度也计入。</p>
     *
     * @param widths 每个菜单项的内容宽度
     */
    protected void setItemContentWidths(int... widths) {
        this.itemContentWidths = widths;
    }

    /** 子类在构造完成后调用此方法以创建悬浮状态管理器数组 */
    protected void initAnims(int count) {
        hoverStates = new HoverStateManager[count];
        for (int i = 0; i < count; i++) {
            hoverStates[i] = new HoverStateManager();
        }
    }

    // ======================== 位置 ========================

    /** 设置弹出菜单左上角位置 */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 根据按钮位置自动定位弹出菜单。
     * <p>屏幕左侧的按钮 → 菜单从左向右展开（左边缘对齐按钮中心 X）。</p>
     * <p>屏幕右侧的按钮 → 菜单从右向左展开（右边缘对齐按钮中心 X）。</p>
     * <p>两种情况菜单顶部对齐按钮底部。</p>
     *
     * @param btnCenterX  按钮中心 X 坐标
     * @param btnBottomY  按钮底部 Y 坐标
     * @param screenWidth 屏幕宽度（用于判断按钮在左半还是右半）
     */
    public void positionFromButton(int btnCenterX, int btnBottomY, int screenWidth) {
        int pw = getPopupWidth();
        boolean isRightSide = btnCenterX > screenWidth / 2;
        if (isRightSide) {
            // 右侧按钮 → 菜单右边缘对齐按钮中心
            this.x = btnCenterX - pw;
        } else {
            // 左侧按钮 → 菜单左边缘对齐按钮中心
            this.x = btnCenterX;
        }
        this.y = btnBottomY;
    }

    // ======================== 打开/关闭 ========================

    /** 切换打开/关闭状态 */
    public void toggle() {
        this.open = !this.open;
        if (!this.open) {
            resetAllHoverAnims();
        }
    }

    /** 打开菜单 */
    public void open() {
        if (!this.open) toggle();
    }

    /** 关闭菜单 */
    public void close() {
        if (this.open) toggle();
    }

    /** 菜单是否处于打开状态 */
    public boolean isOpen() {
        return open;
    }

    /** 关闭时重置所有悬浮动画 */
    private void resetAllHoverAnims() {
        if (hoverStates != null) {
            for (HoverStateManager hs : hoverStates) {
                hs.snapTo(false);
            }
        }
    }

    // ======================== 尺寸与命中检测 ========================

    /** 菜单总高度 */
    private int menuHeight() {
        return getPadV() * 2 + getItemCount() * getItemHeight();
    }

    /** 检测像素坐标是否在弹出菜单区域内 */
    public boolean contains(int mx, int my) {
        if (!open) return false;
        return mx >= x && mx < x + getPopupWidth()
                && my >= y && my < y + menuHeight();
    }

    /** 获取指定菜单项的 Y 坐标 */
    protected int itemY(int index) {
        return y + getPadV() + index * getItemHeight();
    }

    // ======================== 渲染 ========================

    /**
     * 渲染弹出菜单。
     * <p>基类提供：九宫格面板背景 + 悬浮检测 + 动画管理。
     * 子类通过 {@link #renderItem} 钩子自定义每个菜单项的外观。</p>
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int pw = getPopupWidth();
        int ph = menuHeight();

        // 1) 九宫格面板背景
        RtsClientUiUtil.drawNineSlicePanel(g, x, y, pw, ph, false);

        // 2) 检测当前悬浮项并更新各菜单项的悬浮状态
        int hoveredIndex = -1;
        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            boolean inside = mouseX >= x + getPadH() && mouseX < x + pw - getPadH()
                    && mouseY >= iy && mouseY < iy + getItemHeight();
            if (inside) {
                hoveredIndex = i;
            }
        }

        // 3) 绘制每个菜单项（更新悬浮状态 + 渲染背景 + 子类内容）
        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            float t = hoverStates[i].update(i == hoveredIndex);

            // 悬浮背景（从完全透明到半透明深色）
            if (t > 0.001f) {
                int bgColor = SmoothAnimator.lerpColor(bgNormal(), bgHover(), t);
                g.fill(x + getPadH(), iy, x + pw - getPadH(), iy + getItemHeight(), bgColor);
            }

            renderItem(g, i, iy, t);
        }
    }

    // ======================== 点击处理 ========================

    /**
     * 处理鼠标点击。
     *
     * @return true 表示事件被消费（点击到了某个菜单项）
     */
    public boolean handleClick(int mx, int my) {
        if (!open) return false;

        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            if (mx >= x + getPadH() && mx < x + getPopupWidth() - getPadH()
                    && my >= iy && my < iy + getItemHeight()) {
                return onItemClick(i);
            }
        }
        return false;
    }
}
