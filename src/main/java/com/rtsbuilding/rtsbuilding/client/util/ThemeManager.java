package com.rtsbuilding.rtsbuilding.client.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局主题管理器——统一管理亮暗主题状态，支持监听器通知。
 *
 * <p>所有与亮暗主题相关的状态查询、切换、偏移量计算都应通过此管理器完成。
 * 新增 UI 元素时，可通过以下方式适配主题：</p>
 * <ul>
 *   <li><b>直接查询</b>：每帧通过 {@link #isLightMode()} 或 {@link #themeU(int)} 获取偏移</li>
 *   <li><b>监听通知</b>：注册 {@link ThemeListener} 在主题切换时刷新缓存或触发重绘</li>
 * </ul>
 *
 * <p>示例——双主题贴图渲染：</p>
 * <pre>{@code
 * int srcX = ThemeManager.getInstance().themeU(frameWidth);
 * g.blit(texture, dx, dy, w, h, srcX, srcY, w, h, texW, texH);
 * }</pre>
 */
public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private volatile boolean lightMode;
    private final List<ThemeListener> listeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {
    }

    // ======================== 获取实例 ========================

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    // ======================== 状态查询与切换 ========================

    public boolean isLightMode() {
        return lightMode;
    }

    public void setLightMode(boolean mode) {
        if (this.lightMode != mode) {
            this.lightMode = mode;
            notifyListeners();
        }
    }

    /** 切换亮暗主题。 */
    public void toggle() {
        setLightMode(!this.lightMode);
    }

    // ======================== 监听器管理 ========================

    public void addListener(ThemeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ThemeListener l : listeners) {
            l.onThemeChanged(this.lightMode);
        }
    }

    // ======================== 双主题贴图工具 ========================

    /**
     * 根据当前主题计算精灵图的水平像素偏移。
     *
     * <p>双主题精灵图横向翻倍，左半（u=0）为暗色，右半（u={@code frameWidth}）为明亮。
     * 亮色主题时 srcX 需右移一帧宽度，暗色时保持在 0。</p>
     *
     * @param frameWidth 单帧/单主题的像素宽度
     * @return 亮色主题返回 {@code frameWidth}，暗色返回 0
     */
    public int themeU(int frameWidth) {
        return lightMode ? frameWidth : 0;
    }

    // ======================== 主题色文字颜色 ========================

    /** 亮色主题文字颜色（深灰色，浅色背景需高对比度） */
    private static final int LIGHT_TEXT_COLOR = 0xFF333333;
    /** 暗色主题文字颜色（亮灰色） */
    private static final int DARK_TEXT_COLOR = 0xFFCCCCCC;
    /** 亮色主题悬浮文字颜色（中灰色） */
    private static final int LIGHT_HOVER_TEXT_COLOR = 0xFF555555;
    /** 暗色主题悬浮文字颜色（更亮的灰色） */
    private static final int DARK_HOVER_TEXT_COLOR = 0xFFE8E8E8;

    /**
     * 根据当前主题返回适合的基本文字颜色。
     *
     * @return 亮色主题返回深灰色 {@code 0xFF333333}，暗色主题返回亮灰色 {@code 0xFFCCCCCC}
     */
    public static int getTextColor() {
        return getInstance().isLightMode() ? LIGHT_TEXT_COLOR : DARK_TEXT_COLOR;
    }

    /**
     * 根据当前主题返回适合的悬浮态文字颜色。
     *
     * @return 亮色主题返回中灰色 {@code 0xFF555555}，暗色主题返回更亮的灰色 {@code 0xFFE8E8E8}
     */
    public static int getHoverTextColor() {
        return getInstance().isLightMode() ? LIGHT_HOVER_TEXT_COLOR : DARK_HOVER_TEXT_COLOR;
    }
}
