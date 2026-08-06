package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/**
 * 建造与范围破坏共享的正式形状目录。
 * CHAIN 只属于破坏模式，其余名称与生产 BuildShape/AreaMineShape 同义。
 */
public enum QuickBuildUiShape {
    CHAIN("chain_block", "screen.rtsbuilding.tooltip.shape_chain"),
    BLOCK("single_block", "screen.rtsbuilding.tooltip.shape_block"),
    LINE("line_block", "screen.rtsbuilding.tooltip.shape_line"),
    SQUARE("square_block", "screen.rtsbuilding.tooltip.shape_square"),
    WALL("wall_block", "screen.rtsbuilding.tooltip.shape_wall"),
    CIRCLE("circle_block", "screen.rtsbuilding.tooltip.shape_circle"),
    CYLINDER("cylinder_block", "screen.rtsbuilding.tooltip.shape_cylinder"),
    BALL("ball_block", "screen.rtsbuilding.tooltip.shape_ball"),
    BOX("box_block", "screen.rtsbuilding.tooltip.shape_box");

    public final String textureName;
    public final String tooltipKey;
    QuickBuildUiShape(String textureName, String tooltipKey) {
        this.textureName = textureName;
        this.tooltipKey = tooltipKey;
    }

    public boolean supportsAdvanced() {
        return this == SQUARE || this == WALL || this == CIRCLE
                || this == CYLINDER || this == BALL || this == BOX;
    }

    public boolean supportsVertical() { return this == CIRCLE || this == CYLINDER; }
}
