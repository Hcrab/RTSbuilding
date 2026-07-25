package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;

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
