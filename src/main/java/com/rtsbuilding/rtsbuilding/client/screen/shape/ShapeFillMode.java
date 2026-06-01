package com.rtsbuilding.rtsbuilding.client.screen.shape;

/**
 * 形状填充模式枚举。
 * <p>
 * 定义建造形状的三种填充方式：
 * <ul>
 *   <li>{@link #FILL} - 实心填充，所有内部位置都被填充</li>
 *   <li>{@link #HOLLOW} - 空心填充，仅保留外轮廓</li>
 *   <li>{@link #SKELETON} - 骨架填充，仅保留边缘骨架（适用于立方体）</li>
 * </ul>
 */
public enum ShapeFillMode {
    FILL,
    HOLLOW,
    SKELETON
}
