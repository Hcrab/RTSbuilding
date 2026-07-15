package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

/**
 * 高级破坏子模式枚举。
 * <ul>
 *   <li>{@link #RECTANGLE} — 矩形，6向滑条控制形状大小</li>
 *   <li>{@link #CYLINDER} — 圆柱，半径+高度滑条</li>
 *   <li>{@link #STAIRS} — 楼梯，沿斜面向下的破坏路径</li>
 *   <li>{@link #LUMBER} — 伐木，智能识别整棵树并砍伐</li>
 * </ul>
 */
public enum AdvancedDestroySubMode {
    RECTANGLE,
    CYLINDER,
    STAIRS,
    LUMBER
}
