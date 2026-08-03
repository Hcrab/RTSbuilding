package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 保留主线按钮构造语义，同时投影到 1.19.2 的构造器 API。
 *
 * <p>它只收口按钮实例的构造方式，不接管布局、焦点、旁白或点击路由；这些行为仍由
 * 原有 Screen 与控件代码负责。把版本差异留在这里可避免生产界面为旧版复制一套流程。</p>
 */
public final class RtsButtons {
    private RtsButtons() {
    }

    public static Builder builder(Component message, Button.OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static final class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private int x;
        private int y;
        private int width;
        private int height = 20;

        private Builder(Component message, Button.OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Button build() {
            return new Button(this.x, this.y, this.width, this.height, this.message, this.onPress);
        }
    }
}
