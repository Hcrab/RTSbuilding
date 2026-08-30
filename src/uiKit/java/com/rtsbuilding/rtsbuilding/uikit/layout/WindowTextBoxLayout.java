package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * RTS 浮窗文本框外框与内部 EditBox 的共享几何。
 *
 * <p>外框始终保留四像素水平文字内边距；居中值只在文字未填满内部宽度时平移
 * 内部 EditBox。这样占位文本、活动文字、光标和选区使用同一视觉起点。</p>
 */
public final class WindowTextBoxLayout {
    public static final int TEXT_PADDING_X = 4;
    public static final int BORDER_W = 1;
    public static final int MIN_INNER_H = 8;
    public static final int MAX_INNER_H = 20;
    public static final int DEFAULT_H = 20;
    public static final int DEFAULT_MAX_LENGTH = 256;

    private WindowTextBoxLayout() {
    }

    public static Geometry geometry(UiRect bounds, int fontLineHeight,
                                    int textWidth, boolean centered, boolean hasValue) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds");
        }
        int width = (int) bounds.getWidth();
        int height = (int) bounds.getHeight();
        int innerWidth = Math.max(1, width - TEXT_PADDING_X * 2);
        int innerHeight = Math.max(MIN_INNER_H,
                Math.min(MAX_INNER_H, height - BORDER_W * 2));
        double innerX = bounds.getX() + TEXT_PADDING_X;
        if (centered && hasValue && textWidth < innerWidth) {
            innerX += (innerWidth - Math.max(0, textWidth)) / 2;
        }
        double innerY = bounds.getY() + Math.max(BORDER_W, (height - innerHeight) / 2);
        double placeholderX = centered
                ? bounds.getX() + Math.max(TEXT_PADDING_X,
                        (width - Math.max(0, textWidth)) / 2)
                : bounds.getX() + TEXT_PADDING_X;
        double textY = bounds.getY() + (height - Math.max(0, fontLineHeight)) / 2;
        return new Geometry(
                bounds,
                new UiRect(bounds.getX(), bounds.getY(), bounds.getWidth(), BORDER_W),
                new UiRect(bounds.getX(), bounds.bottom() - BORDER_W,
                        bounds.getWidth(), BORDER_W),
                new UiRect(bounds.getX(), bounds.getY(), BORDER_W, bounds.getHeight()),
                new UiRect(bounds.right() - BORDER_W, bounds.getY(),
                        BORDER_W, bounds.getHeight()),
                new UiRect(innerX, innerY, innerWidth, innerHeight),
                placeholderX,
                textY);
    }

    public static final class Geometry {
        public final UiRect bounds;
        public final UiRect topBorder;
        public final UiRect bottomBorder;
        public final UiRect leftBorder;
        public final UiRect rightBorder;
        public final UiRect inner;
        public final double placeholderX;
        public final double textY;

        private Geometry(UiRect bounds, UiRect topBorder, UiRect bottomBorder,
                         UiRect leftBorder, UiRect rightBorder, UiRect inner,
                         double placeholderX, double textY) {
            this.bounds = bounds;
            this.topBorder = topBorder;
            this.bottomBorder = bottomBorder;
            this.leftBorder = leftBorder;
            this.rightBorder = rightBorder;
            this.inner = inner;
            this.placeholderX = placeholderX;
            this.textY = textY;
        }
    }
}
