package com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.PanelResizeHandler;

/**
 * 右边框左边框拖拽缩放处理器。
 *
 * <p>封装左边框拖拽缩放的完整状态机：鼠标按下→拖拽→释放。
 * 采用与 {@link PanelResizeHandler} 一致的 delta 算法 + 对边锚定模式：</p>
 * <ul>
 *   <li>起始状态（鼠标位置 + 宽度）在 {@link #tryBegin} 时记录</li>
 *   <li>拖拽过程中通过 {@link #computeWidth} 基于 delta 计算新宽度</li>
 *   <li>释放后调用 {@link #end} 重置状态</li>
 * </ul>
 *
 * <p>纯逻辑计算，无任何 Minecraft 依赖，可独立单元测试。</p>
 */
public final class RightSidebarResizeHandler {

    /** 左边框可点击区域的像素宽度 */
    public static final int LEFT_BORDER_WIDTH = 5;

    /** 右边框最小宽度 */
    private static final int MIN_WIDTH = 30;

    /** 是否正在拖拽缩放 */
    private boolean active;

    /** 拖拽起始鼠标 X（用于计算 delta） */
    private int startMouseX;

    /** 拖拽起始时的宽度 */
    private int startWidth;

    /** 拖拽起始时的屏幕宽度（用于限制最大宽度） */
    private int screenWidth;

    // ======================== 状态查询 ========================

    /** 是否正在拖拽缩放 */
    public boolean isActive() {
        return this.active;
    }

    // ======================== 状态机 ========================

    /**
     * 尝试在左边框区域开始缩放。
     *
     * @param mouseX       鼠标当前 X（屏幕坐标）
     * @param mouseY       鼠标当前 Y（屏幕坐标）
     * @param sidebarX     右边框左边缘 X（屏幕坐标）
     * @param sidebarTop   右边框顶部 Y（屏幕坐标）
     * @param sidebarH     右边框高度
     * @param currentWidth 当前右边框宽度
     * @param screenWidth  当前屏幕宽度（用于限制最大宽度）
     * @return true 如果鼠标位于左边框区域且缩放已启动
     */
    public boolean tryBegin(double mouseX, double mouseY,
                            int sidebarX, int sidebarTop, int sidebarH,
                            int currentWidth, int screenWidth) {
        if (mouseX < sidebarX || mouseX >= sidebarX + LEFT_BORDER_WIDTH
                || mouseY < sidebarTop || mouseY >= sidebarTop + sidebarH) {
            return false;
        }
        this.active = true;
        this.startMouseX = (int) mouseX;
        this.startWidth = currentWidth;
        this.screenWidth = screenWidth;
        return true;
    }

    /**
     * 根据鼠标当前位置计算新宽度（delta 算法，与 {@link PanelResizeHandler} 一致）。
     * <p>左边缘缩放语义：鼠标右移（dx 为正）→ 宽度减小，
     * 鼠标左移（dx 为负）→ 宽度增大。</p>
     *
     * @return 计算后的新宽度，或在未激活时返回 -1
     */
    public int computeWidth(double mouseX) {
        if (!this.active) return -1;
        int dx = (int) mouseX - this.startMouseX;
        int newWidth = this.startWidth - dx;
        int maxWidth = this.screenWidth / 4;
        return Math.max(MIN_WIDTH, Math.min(maxWidth, newWidth));
    }

    /** 结束缩放，重置状态 */
    public void end() {
        this.active = false;
    }

    // ======================== 命中检测 ========================

    /**
     * 检测坐标是否在左边框可点击区域上（纯检测，不修改状态）。
     * <p>如果正在拖拽缩放则始终返回 true（确保拖拽过程中光标保持缩放样式），
     * 否则检测鼠标是否位于左边框范围内。</p>
     *
     * @param mx         鼠标 X（屏幕坐标）
     * @param my         鼠标 Y（屏幕坐标）
     * @param sidebarX   右边框左边缘 X（屏幕坐标）
     * @param sidebarTop 右边框顶部 Y（屏幕坐标）
     * @param sidebarH   右边框高度
     */
    public boolean isOverLeftEdge(int mx, int my,
                                  int sidebarX, int sidebarTop, int sidebarH) {
        if (this.active) return true;
        return mx >= sidebarX && mx < sidebarX + LEFT_BORDER_WIDTH
                && my >= sidebarTop && my < sidebarTop + sidebarH;
    }
}
