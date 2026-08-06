package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;

import java.util.EnumMap;

/**
 * Quick Build 的可持久化选择状态。
 *
 * <p>本类只保存模式、两侧形状、连锁上限、进阶开关和垂直开关；不执行持久化、
 * 插件权限、控制器同步、窗口绘制或网络副作用。这样面板不再用八个并行 boolean
 * 表达同一类“按形状索引的偏好”。</p>
 */
final class QuickBuildPreferenceState {
    private QuickBuildMode mode = QuickBuildMode.BUILD;
    private BuildShape buildShape = BuildShape.BLOCK;
    private AreaMineShape destroyShape = AreaMineShape.CHAIN;
    private int chainLimit = 64;
    private QuickBuildUiCatalogPage catalogPage = QuickBuildUiCatalogPage.SHAPES;
    private QuickBuildUiConvenienceTool convenienceTool = QuickBuildUiConvenienceTool.REPEAT_BOX;
    private QuickBuildUiConvenienceSettings convenienceSettings =
            QuickBuildUiConvenienceSettings.DEFAULT;
    private boolean overwrite;
    private int smartFillMaxBlocks = SmartFillLimits.DEFAULT_BLOCKS;
    private int smartFillDiameter = SmartFillLimits.DEFAULT_DIAMETER;
    private final EnumMap<BuildShape, Boolean> advanced =
            new EnumMap<BuildShape, Boolean>(BuildShape.class);
    private final EnumMap<BuildShape, Boolean> vertical =
            new EnumMap<BuildShape, Boolean>(BuildShape.class);

    QuickBuildMode mode() {
        return mode;
    }

    void mode(QuickBuildMode value) {
        mode = value == null ? QuickBuildMode.BUILD : value;
    }

    BuildShape buildShape() {
        return buildShape;
    }

    void buildShape(BuildShape value) {
        buildShape = value == null ? BuildShape.BLOCK : value;
    }

    AreaMineShape destroyShape() {
        return destroyShape;
    }

    void destroyShape(AreaMineShape value) {
        destroyShape = value == null ? AreaMineShape.CHAIN : value;
    }

    int chainLimit() {
        return chainLimit;
    }

    void chainLimit(int value) {
        chainLimit = value;
    }

    /**
     * 目录和工具参数是客户端 UI 偏好，不参与服务端破坏或放置的业务判定。
     * 1.12 当前状态文件尚无对应字段，因此只保证同一客户端会话内稳定保留。
     */
    QuickBuildUiCatalogPage catalogPage() {
        return catalogPage;
    }

    void catalogPage(QuickBuildUiCatalogPage value) {
        catalogPage = value == null ? QuickBuildUiCatalogPage.SHAPES : value;
    }

    QuickBuildUiConvenienceTool convenienceTool() {
        return convenienceTool;
    }

    void convenienceTool(QuickBuildUiConvenienceTool value) {
        convenienceTool = value == null ? QuickBuildUiConvenienceTool.REPEAT_BOX : value;
    }

    QuickBuildUiConvenienceSettings convenienceSettings() {
        return convenienceSettings;
    }

    void convenienceParameter(QuickBuildUiConvenienceParameter parameter, int value) {
        if (parameter != null) {
            convenienceSettings = convenienceSettings.with(parameter, value);
        }
    }

    int smartFillMaxBlocks() {
        return smartFillMaxBlocks;
    }

    void smartFillMaxBlocks(int value) {
        smartFillMaxBlocks = Math.max(SmartFillLimits.MIN_BLOCKS,
                Math.min(SmartFillLimits.MAX_BLOCKS, value));
    }

    int smartFillDiameter() {
        return smartFillDiameter;
    }

    void smartFillDiameter(int value) {
        smartFillDiameter = Math.max(SmartFillLimits.MIN_DIAMETER,
                Math.min(SmartFillLimits.MAX_DIAMETER, value));
    }

    boolean overwrite() {
        return overwrite;
    }

    void overwrite(boolean value) {
        overwrite = value;
    }

    boolean advanced(BuildShape shape) {
        return Boolean.TRUE.equals(advanced.get(normalize(shape)));
    }

    void advanced(BuildShape shape, boolean value) {
        BuildShape normalized = normalize(shape);
        if (supportsAdvanced(normalized)) {
            advanced.put(normalized, value);
        }
    }

    boolean vertical(BuildShape shape) {
        return Boolean.TRUE.equals(vertical.get(normalize(shape)));
    }

    void vertical(BuildShape shape, boolean value) {
        BuildShape normalized = normalize(shape);
        if (normalized == BuildShape.LINE
                || normalized == BuildShape.CIRCLE
                || normalized == BuildShape.CYLINDER) {
            vertical.put(normalized, value);
        }
    }

    private static boolean supportsAdvanced(BuildShape shape) {
        switch (shape) {
            case SQUARE:
            case WALL:
            case CIRCLE:
            case CYLINDER:
            case BALL:
            case BOX:
                return true;
            default:
                return false;
        }
    }

    private static BuildShape normalize(BuildShape shape) {
        return shape == null ? BuildShape.BLOCK : shape;
    }
}
