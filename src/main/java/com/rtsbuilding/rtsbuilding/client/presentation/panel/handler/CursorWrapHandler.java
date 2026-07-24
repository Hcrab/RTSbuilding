package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;


public final class CursorWrapHandler {

    private static final double WRAP_MARGIN = 2.0D;

    
    private double[] pendingWrap;

    
    private boolean wasInsideContent = false;

    
    public void tick(CameraModule cam, double guiScale, int rightSidebarWidth, int downSidebarHeight) {
        if (cam == null || !cam.getState().isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        var window = mc != null ? mc.getWindow() : null;
        if (window == null) return;
        long h = window.getWindow();

        boolean rightDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1;
        boolean middleDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == 1;
        
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
        
        
        double left = WRAP_MARGIN * gs;
        double top = (ScreenBackgroundPanel.BACKGROUND_TOP_Y + WRAP_MARGIN) * gs;
        double right = virtualW - rightSidebarWidth * gs - WRAP_MARGIN * gs;
        double bottom = virtualH - downSidebarHeight * gs - WRAP_MARGIN * gs;

        if (right <= left || bottom <= top) return;

        boolean isInside = vx >= left && vx < right && vy >= top && vy < bottom;

        
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

        
        this.wasInsideContent = isInside;
    }

    
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
