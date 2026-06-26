package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.PanelResizeHandler;

/**
 * 下边框上边缘拖拽缩放处理器。
 *
 * <p>封装上边缘拖拽缩放的完整状态机：鼠标按下→拖拽→释放。
 * 采用与 {@link PanelResizeHandler} 一致的 delta 算法 + 对边锚定模式：</p>
 * <ul>
 *   <li>起始状态（鼠标位置 + 高度）在 {@link #tryBegin} 时记录</li>
 *   <li>拖拽过程中通过 {@link #computeHeight} 基于 delta 计算新高度</li>
 *   <li>释放后调用 {@link #end} 重置状态</li>
 * </ul>
 *
 * <p>纯逻辑计算，无任何 Minecraft 依赖，可独立单元测试。</p>
 */
public final class DownSidebarResizeHandler {

    /** 上边缘可点击区域的像素高度 */
    public static final int TOP_BORDER_HEIGHT = 5;

    /** 下边框最小高度 */
    private static final int MIN_HEIGHT = 8;

    /** 是否正在拖拽缩放 */
    private boolean active;

    /** 拖拽起始鼠标 Y（用于计算 delta） */
    private int startMouseY;

    /** 拖拽起始时的高度 */
    private int startHeight;

    /** 拖拽起始时的屏幕高度（用于限制最大高度） */
    private int screenHeight;

    // ======================== 状态查询 ========================

    /** 是否正在拖拽缩放 */
    public boolean isActive() {
        return this.active;
    }

    // ======================== 状态机 ========================

    /**
     * 尝试在上边缘区域开始缩放。
     *
     * @param mouseX       鼠标当前 X（屏幕坐标）
     * @param mouseY       鼠标当前 Y（屏幕坐标）
     * @param barX         下边框左边缘 X（屏幕坐标）
     * @param barTop       下边框顶部 Y（屏幕坐标）
     * @param barW         下边框宽度
     * @param currentHeight 当前下边框高度
     * @param screenHeight  当前屏幕高度（用于限制最大高度）
     * @return true 如果鼠标位于上边缘区域且缩放已启动
     */
    public boolean tryBegin(double mouseX, double mouseY,
                            int barX, int barTop, int barW,
                            int currentHeight, int screenHeight) {
        if (mouseY < barTop || mouseY >= barTop + TOP_BORDER_HEIGHT
                || mouseX < barX || mouseX >= barX + barW) {
            return false;
        }
        this.active = true;
        this.startMouseY = (int) mouseY;
        this.startHeight = currentHeight;
        this.screenHeight = screenHeight;
        return true;
    }

    /**
     * 根据鼠标当前位置计算新高度（delta 算法，与 {@link PanelResizeHandler} 一致）。
     * <p>上边缘缩放语义：鼠标上移（dy 为负）→ 高度增大，
     * 鼠标下移（dy 为正）→ 高度减小。</p>
     *
     * @return 计算后的新高度，或在未激活时返回 -1
     */
    public int computeHeight(double mouseY) {
        if (!this.active) return -1;
        int dy = (int) mouseY - this.startMouseY;
        int newHeight = this.startHeight - dy;
        int maxHeight = this.screenHeight / 2;
        return Math.max(MIN_HEIGHT, Math.min(maxHeight, newHeight));
    }

    /** 结束缩放，重置状态 */
    public void end() {
        this.active = false;
    }

    // ======================== 命中检测 ========================

    /**
     * 检测坐标是否在上边缘可点击区域上（纯检测，不修改状态）。
     * <p>如果正在拖拽缩放则始终返回 true（确保拖拽过程中光标保持缩放样式），
     * 否则检测鼠标是否位于上边缘范围内。</p>
     *
     * @param mx      鼠标 X（屏幕坐标）
     * @param my      鼠标 Y（屏幕坐标）
     * @param barX    下边框左边缘 X（屏幕坐标）
     * @param barTop  下边框顶部 Y（屏幕坐标）
     * @param barW    下边框宽度
     */
    public boolean isOverTopEdge(int mx, int my,
                                 int barX, int barTop, int barW) {
        if (this.active) return true;
        return my >= barTop && my < barTop + TOP_BORDER_HEIGHT
                && mx >= barX && mx < barX + barW;
    }
}
