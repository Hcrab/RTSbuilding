package com.rtsbuilding.rtsbuilding.uicore.geometry;

/**
 * 平台无关的不可变 UI 矩形。
 *
 * <p>右边和下边采用半开区间，避免相邻控件共享边界时同时命中。该类不拥有
 * 布局策略，也不接触渲染 API。</p>
 */
public final class UiRect {
    public static final UiRect EMPTY = new UiRect(0.0D, 0.0D, 0.0D, 0.0D);

    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public UiRect(double x, double y, double width, double height) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(width, "width");
        requireFinite(height, "height");
        if (width < 0.0D || height < 0.0D) {
            throw new IllegalArgumentException("width and height must be non-negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean isEmpty() {
        return width == 0.0D || height == 0.0D;
    }

    public boolean contains(double pointX, double pointY) {
        return !isEmpty()
                && pointX >= x && pointX < right()
                && pointY >= y && pointY < bottom();
    }

    public boolean contains(UiRect other) {
        return other != null
                && other.x >= x && other.y >= y
                && other.right() <= right() && other.bottom() <= bottom();
    }

    public boolean intersects(UiRect other) {
        return other != null && !isEmpty() && !other.isEmpty()
                && x < other.right() && right() > other.x
                && y < other.bottom() && bottom() > other.y;
    }

    public UiRect intersection(UiRect other) {
        if (!intersects(other)) {
            return EMPTY;
        }
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        return new UiRect(left, top, right - left, bottom - top);
    }

    public UiRect inset(UiInsets insets) {
        if (insets == null) {
            throw new IllegalArgumentException("insets must not be null");
        }
        double newWidth = Math.max(0.0D, width - insets.horizontal());
        double newHeight = Math.max(0.0D, height - insets.vertical());
        return new UiRect(x + insets.getLeft(), y + insets.getTop(), newWidth, newHeight);
    }

    public UiRect translate(double deltaX, double deltaY) {
        return new UiRect(x + deltaX, y + deltaY, width, height);
    }

    /** 将矩形平移进容器；尺寸大于容器时缩到容器尺寸。 */
    public UiRect clampWithin(UiRect container) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        double clampedWidth = Math.min(width, container.width);
        double clampedHeight = Math.min(height, container.height);
        double clampedX = Math.max(container.x, Math.min(x, container.right() - clampedWidth));
        double clampedY = Math.max(container.y, Math.min(y, container.bottom() - clampedHeight));
        return new UiRect(clampedX, clampedY, clampedWidth, clampedHeight);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UiRect)) return false;
        UiRect rect = (UiRect) other;
        return Double.compare(x, rect.x) == 0
                && Double.compare(y, rect.y) == 0
                && Double.compare(width, rect.width) == 0
                && Double.compare(height, rect.height) == 0;
    }

    @Override
    public int hashCode() {
        long result = Double.doubleToLongBits(x);
        result = 31L * result + Double.doubleToLongBits(y);
        result = 31L * result + Double.doubleToLongBits(width);
        result = 31L * result + Double.doubleToLongBits(height);
        return (int) (result ^ (result >>> 32));
    }

    @Override
    public String toString() {
        return "UiRect{" + x + "," + y + "," + width + "," + height + "}";
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
