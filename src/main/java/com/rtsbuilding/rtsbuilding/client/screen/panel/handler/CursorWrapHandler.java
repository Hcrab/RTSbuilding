package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/**
 * 光标环绕处理器——在摄像机拖拽时将光标限制在 RTS 内容区域内。
 *
 * <p>当玩家按住右键/中键拖拽相机时，光标移动到内容区边缘会自动环绕到对侧，
 * 实现无限拖拽交互。环绕坐标延迟到渲染帧末尾执行，避免在事件回调中嵌套修改光标。</p>
 *
 * <p>完全无状态（除 {@link #pendingWrap} 待执行队列外），不持有 BuilderScreen 引用，
 * 所有布局参数通过 {@link #tick} 方法参数注入。</p>
 */
public final class CursorWrapHandler {

    private static final double WRAP_MARGIN = 2.0D;

    /** 待应用的鼠标环绕坐标（GLFW 窗口内容区坐标），由 tick() 设置，render() 末尾执行 */
    private double[] pendingWrap;

    /** 上一次 tick 时光标是否在内容区内部。
     * 仅当光标从内部过渡到外部时才触发环绕，
     * 防止在侧边栏/顶栏/底栏点击时误环绕。 */
    private boolean wasInsideContent = false;

    /**
     * 每 tick 检查拖拽场景下光标是否超出内容区边界，若是则计算环绕目标。
     * <p>采用两阶段设计：tick() 仅计算坐标并缓存，不直接修改光标位置，
     * 由 {@link #applyWrapIfPending()} 在渲染帧末尾安全执行。</p>
     *
     * <p>边界以 {@link ScreenBackgroundPanel} 的九宫格背景贴图内容区为准：
     * 从屏幕左边缘（x=0）、顶部栏底部（BACKGROUND_TOP_Y）开始，
     * 到右边栏左侧、下边栏顶部结束。</p>
     *
     * <p>环绕仅在光标<b>从内容区内部→外部</b>过渡时触发。
     * 若光标一开始就在边界外（如侧边栏上），则不触发环绕。
     * 每次按键释放后内部状态重置，确保跨拖拽会话间不残留。</p>
     *
     * @param cam               摄像机模块（用于判断 RTS 模式是否活跃）
     * @param guiScale          当前 RTS GUI 缩放倍率
     * @param rightSidebarWidth 右边栏实际宽度（像素）
     * @param downSidebarHeight 下边栏实际高度（像素）
     */
    public void tick(CameraModule cam, double guiScale, int rightSidebarWidth, int downSidebarHeight) {
        if (cam == null || !cam.getState().isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        var window = mc != null ? mc.getWindow() : null;
        if (window == null) return;
        long h = window.getWindow();

        boolean rightDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1;
        boolean middleDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == 1;
        // 无按键按下时重置内部追踪状态，避免跨拖拽会话残留
        if (!rightDown && !middleDown) {
            this.wasInsideContent = false;
            return;
        }

        double glfwX, glfwY;
        try (var stack = MemoryStack.stackPush()) {
            var xBuf = stack.mallocDouble(1);
            var yBuf = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(h, xBuf, yBuf);
            glfwX = xBuf.get(0);
            glfwY = yBuf.get(0);
        }

        double virtualW = window.getScreenWidth();
        double virtualH = window.getScreenHeight();
        double vx = glfwX * virtualW / window.getWidth();
        double vy = glfwY * virtualH / window.getHeight();

        double gs = guiScale;
        // 内容区边界与 ScreenBackgroundPanel.renderNineSliceFallback 保持一致：
        // screen_ui.png 从 (0, BACKGROUND_TOP_Y) 开始，延伸到右边栏左侧、下边栏顶部
        double left = WRAP_MARGIN * gs;
        double top = (ScreenBackgroundPanel.BACKGROUND_TOP_Y + WRAP_MARGIN) * gs;
        double right = virtualW - rightSidebarWidth * gs - WRAP_MARGIN * gs;
        double bottom = virtualH - downSidebarHeight * gs - WRAP_MARGIN * gs;

        if (right <= left || bottom <= top) return;

        boolean isInside = vx >= left && vx < right && vy >= top && vy < bottom;

        // 仅在光标从内容区内部过渡到外部时触发环绕
        if (this.wasInsideContent && !isInside) {
            double wrapX = vx, wrapY = vy;
            boolean wrap = false;

            if (vx < left) { wrapX = right - gs; wrap = true; }
            else if (vx >= right) { wrapX = left + gs; wrap = true; }
            if (vy < top) { wrapY = bottom - gs; wrap = true; }
            else if (vy >= bottom) { wrapY = top + gs; wrap = true; }

            if (wrap) {
                this.pendingWrap = new double[]{
                        wrapX * window.getWidth() / virtualW,
                        wrapY * window.getHeight() / virtualH
                };
            }
        }

        // 更新内部状态供下一帧判断
        this.wasInsideContent = isInside;
    }

    /**
     * 在渲染帧末尾应用待处理的光标环绕。
     * <p>延迟执行避免在事件回调中设置光标位置导致递归。</p>
     */
    public void applyWrapIfPending() {
        if (this.pendingWrap == null) return;
        Minecraft mc = Minecraft.getInstance();
        var w = mc != null ? mc.getWindow() : null;
        if (w != null) {
            GLFW.glfwSetCursorPos(w.getWindow(), this.pendingWrap[0], this.pendingWrap[1]);
        }
        this.pendingWrap = null;
    }
}
