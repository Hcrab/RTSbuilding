package com.rtsbuilding.rtsbuilding.uikit.section;

/**
 * 折叠分组的纯逻辑状态。
 *
 * <p>模型只拥有展开目标和高度计算；动画时间由 {@code UiFloatAnimation} 驱动，
 * renderer 决定是否 scissor 内容，因此不会把平台绘制细节带进 Kit。</p>
 */
public final class UiCollapsibleSectionModel {
    private final double headerHeight;
    private double contentHeight;
    private boolean expanded;

    public UiCollapsibleSectionModel(double headerHeight, double contentHeight, boolean expanded) {
        this.headerHeight = requireNonNegative(headerHeight, "headerHeight");
        this.contentHeight = requireNonNegative(contentHeight, "contentHeight");
        this.expanded = expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggle() {
        this.expanded = !this.expanded;
    }

    public double targetProgress() {
        return expanded ? 1.0D : 0.0D;
    }

    public double getHeaderHeight() {
        return headerHeight;
    }

    public double getContentHeight() {
        return contentHeight;
    }

    public void setContentHeight(double contentHeight) {
        this.contentHeight = requireNonNegative(contentHeight, "contentHeight");
    }

    public double visibleContentHeight(double animationProgress) {
        return contentHeight * clampProgress(animationProgress);
    }

    public double totalHeight(double animationProgress) {
        return headerHeight + visibleContentHeight(animationProgress);
    }

    private static double clampProgress(double progress) {
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            throw new IllegalArgumentException("animationProgress must be finite");
        }
        return Math.max(0.0D, Math.min(1.0D, progress));
    }

    private static double requireNonNegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }
}
