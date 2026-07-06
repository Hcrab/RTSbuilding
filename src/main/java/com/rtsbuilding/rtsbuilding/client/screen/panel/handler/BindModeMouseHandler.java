package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.screen.event.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.screen.event.EventResult;
import com.rtsbuilding.rtsbuilding.client.screen.event.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.event.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.screen.event.EventResult.PASS;

/**
 * 绑定模式交互处理器——封装容器绑定相关的鼠标/键盘事件处理。
 *
 * <p>从 {@link BuilderScreen#registerEventHandlers()} 中提取，
 * 消除屏幕类中的业务逻辑 lambda，使事件注册更加清晰。</p>
 *
 * <p>注册到 {@link EventDispatcher} 的 {@link EventDispatcher#P_BIND_LOGIC} 优先级：</p>
 * <ul>
 *   <li>鼠标点击：左键解绑（点击模式/框选模式）、右键绑定（点击模式/框选模式）</li>
 *   <li>键盘按键：Enter 确认框选批量绑定</li>
 * </ul>
 */
public final class BindModeMouseHandler {

    private final RtsClientKernel kernel;
    private final BuilderScreenBindHandler bindHandler;

    public BindModeMouseHandler() {
        this.kernel = RtsClientKernel.get();
        this.bindHandler = new BuilderScreenBindHandler();
    }

    /** 获取底层 BindHandler（供 BuilderScreen 对框选状态做额外处理时复用）。 */
    public BuilderScreenBindHandler getBindHandler() {
        return bindHandler;
    }

    // ======================== 鼠标点击 ========================

    /**
     * 处理绑定模式的鼠标点击事件。
     * <p>在 P_BIND_LOGIC 优先级注册。</p>
     */
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel) {
        // 左键解绑：点击模式
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            BuildingModule bm = kernel.module(BuildingModule.class);
            if (bm != null && bm.getMode() == BuilderMode.INTERACT) {
                if (bindHandler.handleClickModeUnbind(screen)) return CONSUMED;
            }
        }
        // 左键解绑：框选批量
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            var sel = kernel.renderPipeline().boxSelector;
            if (sel.getPhase() == BoxSelector.Phase.COMPLETE) {
                if (bindHandler.confirmBatchUnbind()) return CONSUMED;
            }
        }
        // 右键绑定：点击模式（含模式循环）
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !isAltDown() && !isShiftDown()
                && leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            BuildingModule bm = kernel.module(BuildingModule.class);
            if (bm != null && bm.getMode() == BuilderMode.INTERACT) {
                if (bindHandler.handleClickModeBind(screen)) return CONSUMED;
            }
        }
        // 右键绑定：框选批量
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !isAltDown() && !isShiftDown()
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            if (bindHandler.confirmBatchBind()) return CONSUMED;
        }
        return PASS;
    }

    // ======================== 键盘按键 ========================

    /**
     * 处理绑定模式的键盘事件（Enter 确认批量绑定）。
     * <p>在 P_BIND_LOGIC 优先级注册。</p>
     */
    public EventResult handleKeyPress(KeyPressEvent event, LeftSidebarPanel leftSidebarPanel) {
        if ((event.keyCode() == GLFW.GLFW_KEY_ENTER || event.keyCode() == GLFW.GLFW_KEY_KP_ENTER)
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            if (bindHandler.confirmBatchBind()) return CONSUMED;
        }
        return PASS;
    }

    // ======================== 辅助 ========================

    private static boolean isAltDown() {
        if (Minecraft.getInstance().getWindow() == null) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        if (Minecraft.getInstance().getWindow() == null) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
