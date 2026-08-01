package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

/**
 * 快速建造形状枚举。
 * <p>
 * 定义 RTS 模式中快速建造支持的几何形状：
 * <ul>
 *   <li>{@link #BLOCK} — 单方块放置</li>
 *   <li>{@link #LINE} — 直线</li>
 *   <li>{@link #SQUARE} — 平面矩形</li>
 *   <li>{@link #WALL} — 垂直墙壁</li>
 *   <li>{@link #CIRCLE} — 圆形</li>
 *   <li>{@link #CYLINDER} — 圆柱体</li>
 *   <li>{@link #BALL} — 球体</li>
 *   <li>{@link #BOX} — 立方体</li>
 * </ul>
 */
public enum BuildShape {
    BLOCK,
    LINE,
    SQUARE,
    WALL,
    CIRCLE,
    CYLINDER,
    BALL,
    BOX
}
