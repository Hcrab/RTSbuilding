package com.rtsbuilding.rtsbuilding.client.screen.panel.container;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

/**
 * 容器屏幕输入转发器——封装对容器屏幕的输入事件转发与空安全检查。
 *
 * <p>将 {@link ContainerScreenPanel} 中反复出现的"空检查 + 坐标转换 + 方法调用"模式
 * 提取到此辅助类中，每个方法只做一件事：检查屏幕有效性后转发。</p>
 *
 * <p>本类不持有容器屏幕的生命周期管理逻辑，仅负责事件转发。</p>
 */
public final class ContainerInputForwarder {

    @Nullable
    private AbstractContainerScreen<?> screen;

    public ContainerInputForwarder(AbstractContainerScreen<?> screen) {
        this.screen = screen;
    }

    /** 清空内部引用并调用屏幕的 {@code removed()} 方法。 */
    public void clear() {
        if (screen != null) {
            screen.removed();
            screen = null;
        }
    }

    /** 是否持有有效的容器屏幕引用。 */
    public boolean hasScreen() {
        return screen != null;
    }

    /** 获取容器屏幕（可能为 null）。 */
    @Nullable
    public AbstractContainerScreen<?> getScreen() {
        return screen;
    }

    /** 初始化容器屏幕的渲染尺寸。 */
    public void init(int width, int height) {
        if (screen != null) {
            screen.init(net.minecraft.client.Minecraft.getInstance(), width, height);
        }
    }

    /** 推进容器屏幕的 tick。 */
    public void tick() {
        if (screen != null) screen.tick();
    }

    // ======================== 鼠标事件转发 ========================

    public void mouseClicked(double mx, double my, int button) {
        if (screen != null) screen.mouseClicked(mx, my, button);
    }

    /**
     * 转发鼠标拖拽事件。
     *
     * @return true 表示事件已转发（屏幕非空）
     */
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (screen != null) {
            screen.mouseDragged(mx, my, button, dx, dy);
            return true;
        }
        return false;
    }

    /**
     * 转发鼠标释放事件。
     *
     * @return true 表示事件已转发（屏幕非空）
     */
    public boolean mouseReleased(double mx, double my, int button) {
        if (screen != null) {
            screen.mouseReleased(mx, my, button);
            return true;
        }
        return false;
    }

    /**
     * 转发鼠标滚轮事件。
     *
     * @return 容器屏幕是否消费了该事件
     */
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (screen != null) return screen.mouseScrolled(mx, my, sx, sy);
        return false;
    }

    public void mouseMoved(double mx, double my) {
        if (screen != null) screen.mouseMoved(mx, my);
    }

    // ======================== 键盘事件转发 ========================

    /**
     * 转发按键事件。
     *
     * @return 容器屏幕是否消费了该事件
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (screen != null) return screen.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    /**
     * 转发字符输入事件。
     *
     * @return 容器屏幕是否消费了该事件
     */
    public boolean charTyped(char codePoint, int modifiers) {
        if (screen != null) return screen.charTyped(codePoint, modifiers);
        return false;
    }
}
