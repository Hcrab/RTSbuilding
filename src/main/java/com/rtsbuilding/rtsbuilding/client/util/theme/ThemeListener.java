package com.rtsbuilding.rtsbuilding.client.util.theme;

/**
 * 主题切换监听器——当全局亮暗主题切换时回调。
 *
 * <p>实现该接口并注册到 {@link ThemeManager#addListener(ThemeListener)}，
 * 可在主题切换时执行刷新操作（如重设缓存、触发重绘等）。</p>
 */
@FunctionalInterface
public interface ThemeListener {
    /**
     * 主题已切换。
     *
     * @param lightMode 当前是否为明亮主题
     */
    void onThemeChanged(boolean lightMode);
}
