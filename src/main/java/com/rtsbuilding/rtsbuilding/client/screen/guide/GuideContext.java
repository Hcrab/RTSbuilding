package com.rtsbuilding.rtsbuilding.client.screen.guide;

/**
 * 指南面板上下文枚举。
 * <p>
 * 标识指南面板的打开位置/上下文：
 * <ul>
 *   <li>{@link #TOP} - 从顶部栏的指南按钮打开</li>
 *   <li>{@link #BOTTOM} - 从底部面板的指南入口打开</li>
 *   <li>{@link #SETTINGS} - 从设置菜单中的指南入口打开</li>
 * </ul>
 */
public enum GuideContext {
    TOP,
    BOTTOM,
    SETTINGS
}
