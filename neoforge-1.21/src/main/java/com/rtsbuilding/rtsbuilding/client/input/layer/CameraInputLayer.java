package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CameraInputLayer implements InputLayer {

    private final RtsClientKernel kernel;

    
    private int pressedButton = -1;

    
    private boolean pressedWithShift;

    
    private double accumulatedDragDistance = 0.0D;

    
    private boolean draggedPastThreshold = false;

    
    private static final double DRAG_THRESHOLD = 5.0D;

    public CameraInputLayer(RtsClientKernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public boolean isActive() {
        CameraModule cam = kernel.module(CameraModule.class);
        return cam != null && cam.getState().isEnabled();
    }

    
    public boolean wasDragged(int button) {
        return button == this.pressedButton && this.draggedPastThreshold;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.pressedButton = button;
            this.pressedWithShift = isShiftDown();
            this.accumulatedDragDistance = 0.0D;
            this.draggedPastThreshold = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.pressedButton == button) {
            boolean wasDragged = this.draggedPastThreshold;
            this.pressedButton = -1;
            this.pressedWithShift = false;
            this.accumulatedDragDistance = 0.0D;
            this.draggedPastThreshold = false;
            
            return wasDragged;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isActive()) return false;
        
        if (button != this.pressedButton) return false;

        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null) return false;

        
        this.accumulatedDragDistance += Math.sqrt(dragX * dragX + dragY * dragY);
        if (this.accumulatedDragDistance >= DRAG_THRESHOLD) {
            this.draggedPastThreshold = true;
        }

        if (this.accumulatedDragDistance < DRAG_THRESHOLD) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cam.queueRotateDrag(dragX, dragY);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.pressedWithShift) {
            cam.queueDragMove(dragX, dragY);
            return true;
        }

        
        return true;
    }

    
    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null) return false;

        cam.queueScroll(scrollY);
        return true;
    }
}
