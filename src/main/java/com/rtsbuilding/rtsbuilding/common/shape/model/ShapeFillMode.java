package com.rtsbuilding.rtsbuilding.common.shape.model;

/**
 * 形状填充模式枚举，定义多方块形状在生成方块位置时的填充策略。
 * <p>
 * 不同的填充模式决定了形状生成器输出哪些位置的方块坐标：
 * <ul>
 *   <li>{@link #FILL} —— 实心填充，包含形状内的所有位置</li>
 *   <li>{@link #HOLLOW} —— 空心模式，仅包含外壳（墙面/表面）</li>
 *   <li>{@link #SKELETON} —— 骨架模式，仅包含边缘线框（仅 BOX 形状适用，显示 12 条棱线）</li>
 * </ul>
 */
public enum ShapeFillMode {

    /** 实心填充 —— 包含形状内部所有方块位置 */
    FILL,

    /** 空心模式 —— 仅包含形状的外壳/表面层 */
    HOLLOW,

    /** 骨架模式 —— 仅包含形状的边缘线框（仅适用于 BOX 形状） */
    SKELETON
}
