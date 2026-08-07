package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/**
 * 建造与范围破坏共享的正式形状目录。
 * CHAIN 只属于破坏模式，其余名称与生产 BuildShape/AreaMineShape 同义。
 */
public enum QuickBuildUiShape {
    CHAIN("chain_block", "chain", "screen.rtsbuilding.tooltip.shape_chain"),
    BLOCK("single_block", "single", "screen.rtsbuilding.tooltip.shape_block"),
    LINE("line_block", "line", "screen.rtsbuilding.tooltip.shape_line"),
    SQUARE("square_block", "surface", "screen.rtsbuilding.tooltip.shape_square"),
    WALL("wall_block", "wall", "screen.rtsbuilding.tooltip.shape_wall"),
    CIRCLE("circle_block", "round", "screen.rtsbuilding.tooltip.shape_circle"),
    CYLINDER("cylinder_block", "cylinder", "screen.rtsbuilding.tooltip.shape_cylinder"),
    BALL("ball_block", "ball", "screen.rtsbuilding.tooltip.shape_ball"),
    BOX("box_block", "cube", "screen.rtsbuilding.tooltip.shape_box");

    /** 旧版 450×1350 图集名称；仅供兼容仍需识别历史资源的代码使用。 */
    public final String textureName;
    /** PR #133 双轨图标的稳定资源键；生产与无头预览必须共用。 */
    public final String contributorIconKey;
    public final String tooltipKey;
    QuickBuildUiShape(String textureName, String contributorIconKey, String tooltipKey) {
        this.textureName = textureName;
        this.contributorIconKey = contributorIconKey;
        this.tooltipKey = tooltipKey;
    }

    public boolean supportsAdvanced() {
        return this == SQUARE || this == WALL || this == CIRCLE
                || this == CYLINDER || this == BALL || this == BOX;
    }

    public boolean supportsVertical() { return this == CIRCLE || this == CYLINDER; }
}
