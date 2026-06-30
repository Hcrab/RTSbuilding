package com.rtsbuilding.rtsbuilding.client.input.layer;

import com.rtsbuilding.rtsbuilding.client.input.InputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 相机输入层——处理鼠标拖拽和滚轮控制 RTS 摄像机。
 *
 * <p>当 RTS 模式激活时监听：
 * <ul>
 *   <li><b>Shift + 鼠标右键拖拽</b> → 前后左右平移摄像机（{@link CameraModule#queueDragMove(double, double)}）</li>
 *   <li>鼠标中键拖拽 → 旋转摄像机视角（{@link CameraModule#queueRotateDrag(double, double)}）</li>
 *   <li>鼠标滚轮 → 推拉摄像机距离（{@link CameraModule#queueScroll(double)}）</li>
 * </ul>
 *
 * <p>右键拖拽需要按住 Shift 键，避免与框选等右键操作冲突。
 * 中键为固定绑定，不暴露给按键设置界面。</p>
 *
 * <p>本层同时消费右键/中键的按下和释放事件，确保这些事件不泄漏到
 * {@code BuilderScreen.super.mouseClicked()} 等下游，并维护按钮状态
 * 供拖拽/释放时做正确的状态匹配。</p>
 */
public final class CameraInputLayer implements InputLayer {

    private final RtsClientKernel kernel;

    /** 当前按下的按钮（-1 表示无），用于检测单击/拖拽/释放的状态匹配 */
    private int pressedButton = -1;

    /** 从按下开始累积的拖拽距离，用于区分「点击抖动」与「真实拖拽」 */
    private double accumulatedDragDistance = 0.0D;

    /** 拖拽触发阈值（像素），低于此值视为点击抖动而非拖拽 */
    private static final double DRAG_THRESHOLD = 5.0D;

    public CameraInputLayer(RtsClientKernel kernel) {
        this.kernel = kernel;
    }

    @Override
    public boolean isActive() {
        CameraModule cam = kernel.module(CameraModule.class);
        return cam != null && cam.getState().isEnabled();
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        // 中键始终消费；右键仅在按住 Shift 时消费（避免与框选冲突）
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isShiftDown())) {
            this.pressedButton = button;
            this.accumulatedDragDistance = 0.0D;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (this.pressedButton == button) {
            this.pressedButton = -1;
            this.accumulatedDragDistance = 0.0D;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isActive()) return false;
        // 状态匹配检查：只处理由本层消费了按下事件的按钮的拖拽
        if (button != this.pressedButton) return false;

        CameraModule cam = kernel.module(CameraModule.class);
        if (cam == null) return false;

        // 累积拖拽距离，低于阈值时视为点击抖动，不触发相机操作
        this.accumulatedDragDistance += Math.sqrt(dragX * dragX + dragY * dragY);
        if (this.accumulatedDragDistance < DRAG_THRESHOLD) {
            return true; // 仍消费事件，但不下发到相机
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            cam.queueRotateDrag(dragX, dragY);
            return true;
        }
        // 右键拖拽同样需要 Shift 按下才生效
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isShiftDown()) {
            cam.queueDragMove(dragX, dragY);
            return true;
        }
        return false;
    }

    /** 检测 Shift 键是否按下 */
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
