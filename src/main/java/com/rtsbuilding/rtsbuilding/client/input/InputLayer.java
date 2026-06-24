package com.rtsbuilding.rtsbuilding.client.input;

import net.minecraft.client.Minecraft;

/**
 * 输入层接口——每个输入层处理一个维度的输入。
 *
 * <p>通过 {@link #isActive()} 控制是否参与输入处理，
 * 默认返回 true。不活跃的层被 {@link InputPipeline} 跳过。</p>
 */
public interface InputLayer {

    /** 当前层是否活跃。返回 false 时所有事件都跳过。 */
    default boolean isActive() {
        return true;
    }

    /** 客户端 tick Pre 事件。 */
    default void onTickPre(Minecraft mc) {}

    /** 客户端 tick Post 事件。 */
    default void onTickPost(Minecraft mc) {}

    /** 鼠标按下。返回 true 表示已消费。 */
    default boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** 鼠标释放。返回 true 表示已消费。 */
    default boolean onMouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /** 鼠标拖拽。返回 true 表示已消费。 */
    default boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /** 鼠标滚动。返回 true 表示已消费。 */
    default boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /** 键盘按下。返回 true 表示已消费。 */
    default boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 字符输入。返回 true 表示已消费。 */
    default boolean onCharTyped(char codePoint, int modifiers) {
        return false;
    }
}
