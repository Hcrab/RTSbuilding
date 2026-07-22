package com.rtsbuilding.rtsbuilding.uikit.skin;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将一个源贴图矩形映射为固定九块目标矩形。
 *
 * <p>该模型只计算坐标，不决定贴图过滤或批次。返回值永远恰好九块，因此渲染器
 * 不可能退化成按面积无限平铺的旧实现。</p>
 */
public final class UiNineSliceLayout {
    public enum Part {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, CENTER, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    private UiNineSliceLayout() {
    }

    public static List<Slice> calculate(UiRect source, UiRect target,
                                        double left, double top, double right, double bottom) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source and target must not be null");
        }
        requireBorder(left, "left");
        requireBorder(top, "top");
        requireBorder(right, "right");
        requireBorder(bottom, "bottom");
        if (left + right > source.getWidth() || top + bottom > source.getHeight()) {
            throw new IllegalArgumentException("borders exceed source rectangle");
        }
        if (left + right > target.getWidth() || top + bottom > target.getHeight()) {
            throw new IllegalArgumentException("target is too small for fixed corners");
        }

        double[] sourceX = new double[] {source.getX(), source.getX() + left, source.right() - right, source.right()};
        double[] sourceY = new double[] {source.getY(), source.getY() + top, source.bottom() - bottom, source.bottom()};
        double[] targetX = new double[] {target.getX(), target.getX() + left, target.right() - right, target.right()};
        double[] targetY = new double[] {target.getY(), target.getY() + top, target.bottom() - bottom, target.bottom()};
        Part[] parts = Part.values();
        List<Slice> slices = new ArrayList<Slice>(9);
        int part = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slices.add(new Slice(parts[part++],
                        rect(sourceX, sourceY, column, row),
                        rect(targetX, targetY, column, row)));
            }
        }
        return Collections.unmodifiableList(slices);
    }

    private static UiRect rect(double[] x, double[] y, int column, int row) {
        return new UiRect(x[column], y[row], x[column + 1] - x[column], y[row + 1] - y[row]);
    }

    private static void requireBorder(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " border must be finite and non-negative");
        }
    }

    public static final class Slice {
        private final Part part;
        private final UiRect source;
        private final UiRect target;

        private Slice(Part part, UiRect source, UiRect target) {
            this.part = part;
            this.source = source;
            this.target = target;
        }

        public Part getPart() {
            return part;
        }

        public UiRect getSource() {
            return source;
        }

        public UiRect getTarget() {
            return target;
        }
    }
}
