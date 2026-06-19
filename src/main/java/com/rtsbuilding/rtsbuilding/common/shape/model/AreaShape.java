package com.rtsbuilding.rtsbuilding.common.shape.model;

/**
 * 区域形状类型枚举，建造和破坏操作共用此枚举。
 * <p>
 * 统一了之前分离的 {@code BuildShape}（放置）和 {@code AreaMineShape}（破坏）枚举，
 * 使得系统的两侧使用相同的序数值进行网络通信和形状生成。
 * <p>
 * 注意：枚举序数必须保持稳定，因为它们作为字节（byte）通过网络传输。
 */
public enum AreaShape {
    /** 单方块模式 —— 单个方块的放置或破坏 */
    BLOCK,
    /** 线形模式 —— 沿任意轴的一条直线 */
    LINE,
    /** 正方形/矩形模式 —— 二维平面区域 */
    SQUARE,
    /** 墙体模式 —— 垂直墙面（基线沿 XZ 平面拉伸） */
    WALL,
    /** 圆形模式 —— 在 XZ 平面上的圆形或圆柱 */
    CIRCLE,
    /** 长方体模式 —— 三维立方体（实心方块） */
    BOX
}
