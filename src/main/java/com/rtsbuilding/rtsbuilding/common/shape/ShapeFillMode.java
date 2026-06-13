package com.rtsbuilding.rtsbuilding.common.shape;

/**
 * 形状填充模式枚举。
 * <p>
 * 定义多方块形状在生成方块位置时的填充策略：
 * <ul>
 *   <li>{@link #FILL} — 实心填充，包含所有内部位置</li>
 *   <li>{@link #HOLLOW} — 空心，仅包含外壳（墙壁/表面）</li>
 *   <li>{@link #SKELETON} — 骨架，仅包含边缘线框（仅 BOX 形状适用，显示 12 条棱）</li>
 * </ul>
 */
public enum ShapeFillMode {
    FILL,
    HOLLOW,
    SKELETON
}
