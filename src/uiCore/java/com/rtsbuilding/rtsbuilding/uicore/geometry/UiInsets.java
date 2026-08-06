package com.rtsbuilding.rtsbuilding.uicore.geometry;

/**
 * UI 矩形四边的不可变内边距。
 *
 * <p>此值对象只描述几何，不负责像素缩放、字体度量或任何 Minecraft
 * 平台行为，因此可以由 1.21.1、26.1 和 1.12.2 共用。</p>
 */
public final class UiInsets {
    public static final UiInsets ZERO = new UiInsets(0.0D, 0.0D, 0.0D, 0.0D);

    private final double left;
    private final double top;
    private final double right;
    private final double bottom;

    public UiInsets(double left, double top, double right, double bottom) {
        requireNonNegative(left, "left");
        requireNonNegative(top, "top");
        requireNonNegative(right, "right");
        requireNonNegative(bottom, "bottom");
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static UiInsets all(double value) {
        return new UiInsets(value, value, value, value);
    }

    public static UiInsets symmetric(double horizontal, double vertical) {
        return new UiInsets(horizontal, vertical, horizontal, vertical);
    }

    public double getLeft() {
        return left;
    }

    public double getTop() {
        return top;
    }

    public double getRight() {
        return right;
    }

    public double getBottom() {
        return bottom;
    }

    public double horizontal() {
        return left + right;
    }

    public double vertical() {
        return top + bottom;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UiInsets)) return false;
        UiInsets insets = (UiInsets) other;
        return Double.compare(left, insets.left) == 0
                && Double.compare(top, insets.top) == 0
                && Double.compare(right, insets.right) == 0
                && Double.compare(bottom, insets.bottom) == 0;
    }

    @Override
    public int hashCode() {
        long result = Double.doubleToLongBits(left);
        result = 31L * result + Double.doubleToLongBits(top);
        result = 31L * result + Double.doubleToLongBits(right);
        result = 31L * result + Double.doubleToLongBits(bottom);
        return (int) (result ^ (result >>> 32));
    }

    @Override
    public String toString() {
        return "UiInsets{" + left + "," + top + "," + right + "," + bottom + "}";
    }

    private static void requireNonNegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
