package com.rtsbuilding.rtsbuilding.uikit.scroll;

/**
 * 平台无关的一维滚动状态。
 *
 * <p>它只维护内容长度、视口长度和偏移，不扫描列表、不渲染滚动条，也不决定
 * 鼠标滚轮速度。</p>
 */
public final class UiScrollModel {
    private double contentExtent;
    private double viewportExtent;
    private double offset;

    public UiScrollModel(double contentExtent, double viewportExtent) {
        setExtents(contentExtent, viewportExtent);
    }

    public void setExtents(double contentExtent, double viewportExtent) {
        requireExtent(contentExtent, "contentExtent");
        requireExtent(viewportExtent, "viewportExtent");
        this.contentExtent = contentExtent;
        this.viewportExtent = viewportExtent;
        this.offset = clamp(offset);
    }

    public double getOffset() {
        return offset;
    }

    public double getMaximumOffset() {
        return Math.max(0.0D, contentExtent - viewportExtent);
    }

    public boolean canScroll() {
        return getMaximumOffset() > 0.0D;
    }

    public boolean setOffset(double newOffset) {
        double clamped = clamp(newOffset);
        if (Double.compare(clamped, offset) == 0) {
            return false;
        }
        offset = clamped;
        return true;
    }

    public boolean scrollBy(double delta) {
        return setOffset(offset + delta);
    }

    public boolean page(int direction) {
        if (direction == 0) {
            return false;
        }
        return scrollBy((direction < 0 ? -1.0D : 1.0D) * viewportExtent * 0.9D);
    }

    public double thumbExtent(double trackExtent, double minimumThumbExtent) {
        requireExtent(trackExtent, "trackExtent");
        requireExtent(minimumThumbExtent, "minimumThumbExtent");
        if (contentExtent <= 0.0D || viewportExtent >= contentExtent) {
            return trackExtent;
        }
        return Math.min(trackExtent, Math.max(minimumThumbExtent, trackExtent * viewportExtent / contentExtent));
    }

    public double thumbOffset(double trackExtent, double thumbExtent) {
        requireExtent(trackExtent, "trackExtent");
        requireExtent(thumbExtent, "thumbExtent");
        double maximum = getMaximumOffset();
        return maximum <= 0.0D ? 0.0D : (trackExtent - thumbExtent) * offset / maximum;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("offset must be finite");
        }
        return Math.max(0.0D, Math.min(value, getMaximumOffset()));
    }

    private static void requireExtent(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
