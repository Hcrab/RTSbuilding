package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入流水线——统一管理所有输入层。
 *
 * <p>替代原分散在 {@code RtsClientInputGate}、{@code ClientInputHandler}、
 * {@code BuilderScreen} 三处的输入处理逻辑。</p>
 *
 * <p>每帧按注册顺序遍历 {@link InputLayer}，已消费的事件不会继续传递。</p>
 */
public final class InputPipeline {

    private final List<InputLayer> layers = new ArrayList<>();

    /** 注册输入层。按注册顺序决定优先级。 */
    public void registerLayer(InputLayer layer) {
        this.layers.add(layer);
    }

    /** 每 tick 调用 Pre 事件。 */
    public void onTickPre() {
        Minecraft mc = Minecraft.getInstance();
        for (InputLayer layer : layers) {
            if (layer.isActive()) {
                layer.onTickPre(mc);
            }
        }
    }

    /** 每 tick 调用 Post 事件。 */
    public void onTickPost() {
        Minecraft mc = Minecraft.getInstance();
        for (InputLayer layer : layers) {
            if (layer.isActive()) {
                layer.onTickPost(mc);
            }
        }
    }

    /** 鼠标按下事件。返回 true 表示已消费。 */
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /** 鼠标释放事件。 */
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /** 鼠标拖拽事件。 */
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    /** 鼠标滚动事件。 */
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onMouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    /** 键盘按下事件。 */
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    /** 字符输入事件。 */
    public boolean onCharTyped(char codePoint, int modifiers) {
        for (InputLayer layer : layers) {
            if (layer.isActive() && layer.onCharTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
