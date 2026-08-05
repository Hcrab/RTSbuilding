package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.presentation.event.model.EventResult.PASS;

public final class BindModeMouseHandler {

    private final RtsClientKernel kernel;
    private final BuilderScreenBindHandler bindHandler;

    public BindModeMouseHandler() {
        this.kernel = RtsClientKernel.get();
        this.bindHandler = new BuilderScreenBindHandler();
    }

    
    public BuilderScreenBindHandler getBindHandler() {
        return bindHandler;
    }

    

    
    public EventResult handleMouseClick(MouseClickEvent event, BuilderScreen screen,
                                         LeftSidebarPanel leftSidebarPanel) {
        
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            BuildingModule bm = kernel.module(BuildingModule.class);
            if (bm != null && bm.getMode() == BuilderMode.INTERACT) {
                if (bindHandler.handleClickModeUnbind(screen)) return CONSUMED;
            }
        }
        
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            var sel = kernel.renderPipeline().boxSelector;
            if (sel.getPhase() == BoxSelector.Phase.COMPLETE) {
                if (bindHandler.confirmBatchUnbind()) return CONSUMED;
            }
        }
        
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !isAltDown() && !isShiftDown()
                && leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            BuildingModule bm = kernel.module(BuildingModule.class);
            if (bm != null && bm.getMode() == BuilderMode.INTERACT) {
                if (bindHandler.handleClickModeBind(screen)) return CONSUMED;
            }
        }
        
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !isAltDown() && !isShiftDown()
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            if (bindHandler.confirmBatchBind()) return CONSUMED;
        }
        return PASS;
    }

    

    
    public EventResult handleKeyPress(KeyPressEvent event, LeftSidebarPanel leftSidebarPanel) {
        if ((event.keyCode() == GLFW.GLFW_KEY_ENTER || event.keyCode() == GLFW.GLFW_KEY_KP_ENTER)
                && !leftSidebarPanel.isClickButtonSelected()
                && leftSidebarPanel.isBindModeActive()) {
            if (bindHandler.confirmBatchBind()) return CONSUMED;
        }
        return PASS;
    }

    

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
