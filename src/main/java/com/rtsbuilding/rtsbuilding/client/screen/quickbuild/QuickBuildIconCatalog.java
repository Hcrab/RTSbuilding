package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Quick Build 正式形状图标与选择指示器的唯一生产目录。
 *
 * <p>这里只描述资源位置和精灵图尺寸，不创建控件、不判断选中状态，也不执行绘制。
 * 将资源映射从控件 owner 中独立出来后，新增形状时不会再在面板、输入和 renderer
 * 各维护一份顺序数组。</p>
 */
final class QuickBuildIconCatalog {
    static final ResourceLocation SELECTION_DOT =
            ResourceLocation.tryParse("rtsbuilding:textures/gui/general/mode_button.png");
    static final int SHAPE_SHEET_W = 450;
    static final int SHAPE_SHEET_H = 900;
    static final int SHAPE_STATE_H = 450;
    static final int MODE_SHEET_W = 512;
    static final int MODE_STATE_H = 512;
    static final int MODE_SHEET_H = MODE_STATE_H * 3;

    private QuickBuildIconCatalog() {}

    static ResourceLocation shapeTexture(QuickBuildUiShape shape) {
        return switch (shape) {
            case CHAIN -> QUICK_BUILD_CHAIN_BLOCK;
            case BLOCK -> QUICK_BUILD_SINGLE_BLOCK;
            case LINE -> QUICK_BUILD_LINE_BLOCK;
            case SQUARE -> QUICK_BUILD_SQUARE_BLOCK;
            case WALL -> QUICK_BUILD_WALL_BLOCK;
            case CIRCLE -> QUICK_BUILD_CIRCLE_BLOCK;
            case CYLINDER -> QUICK_BUILD_CYLINDER_BLOCK;
            case BALL -> QUICK_BUILD_BALL_BLOCK;
            case BOX -> QUICK_BUILD_BOX_BLOCK;
        };
    }

    static String tooltipKey(QuickBuildUiShape shape) {
        return "screen.rtsbuilding.tooltip.shape_"
                + shape.name().toLowerCase(Locale.ROOT);
    }
}
