package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 通用窗口按钮文字的共享内边距。
 *
 * <p>按钮 Chrome、纹理 UV 与点击仍由各自控件负责；这里仅保证纯色和纹理按钮使用同一
 * 文本宽度与垂直基线公式。</p>
 */
public final class WindowButtonLayout {
    public static final int TEXT_HORIZONTAL_INSET = 4;
    public static final int TEXT_LINE_HEIGHT = 8;

    private WindowButtonLayout() {
    }

    public static int textWidth(int width) {
        return Math.max(TEXT_HORIZONTAL_INSET,
                width - TEXT_HORIZONTAL_INSET * 2);
    }

    public static int textY(int y, int height) {
        return y + (height - TEXT_LINE_HEIGHT) / 2;
    }
}
