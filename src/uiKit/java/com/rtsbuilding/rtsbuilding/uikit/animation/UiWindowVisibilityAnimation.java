package com.rtsbuilding.rtsbuilding.uikit.animation;

/**
 * 浮动窗口打开与关闭过渡的纯视觉状态机。
 *
 * <p>本类只拥有透明度和“仍在渐隐”状态，不拥有窗口的逻辑 open 值、输入路由、
 * 业务清理或 Minecraft 绘制。调用方必须在关闭请求到达时立即停止输入；本类只让
 * 已关闭窗口保留最多 {@link UiMotionSpec#WINDOW_DISMISS_MS} 毫秒的最后绘制帧。</p>
 */
public final class UiWindowVisibilityAnimation {
    private final UiFloatAnimation opacity;
    private boolean dismissing;

    public UiWindowVisibilityAnimation(UiClock clock, boolean initiallyVisible) {
        this.opacity = new UiFloatAnimation(clock, initiallyVisible ? 1.0D : 0.0D);
    }

    /** 从全透明开始浮现；若正在渐隐，则从当前透明度平滑反向。 */
    public void reveal(boolean animationsEnabled) {
        boolean resumeDismissal = this.dismissing;
        this.dismissing = false;
        if (!resumeDismissal) {
            this.opacity.snapTo(animationsEnabled ? 0.0D : 1.0D);
        }
        this.opacity.animateTo(
                1.0D,
                animationsEnabled ? UiMotionSpec.WINDOW_REVEAL_MS : 0L,
                UiEasing.EASE_OUT_CUBIC);
    }

    /** 开始渐隐；关闭动画被禁用时立即归零，不保留视觉尾帧。 */
    public void dismiss(boolean animationsEnabled) {
        this.dismissing = animationsEnabled;
        if (animationsEnabled) {
            this.opacity.animateTo(
                    0.0D, UiMotionSpec.WINDOW_DISMISS_MS, UiEasing.EASE_OUT_CUBIC);
        } else {
            this.opacity.snapTo(0.0D);
        }
    }

    public boolean shouldRender(boolean logicallyVisible) {
        return logicallyVisible || this.dismissing;
    }

    public boolean isDismissing() {
        return this.dismissing;
    }

    public double opacity() {
        return this.opacity.value();
    }

    /** 返回父窗口及全部子内容共同使用的透明度，进入和退出都不得拆成两套进度。 */
    public double subtreeTintOpacity() {
        return this.opacity.value();
    }

    /**
     * 返回窗口相对最终位置向下漂移的距离。打开时由 4px 浮到原位，关闭时反向退出；
     * 该值只服务绘制，不参与命中、拖拽或持久化坐标。
     */
    public double offsetY() {
        return (1.0D - this.opacity.value()) * UiMotionSpec.WINDOW_FLOAT_PX;
    }

    /** 返回 true 表示最后一帧结束，调用方现在可以清理仅供视觉使用的缓存。 */
    public boolean finishDismissalIfNeeded(boolean animationsEnabled) {
        if (!this.dismissing
                || animationsEnabled && !this.opacity.isFinished()) {
            return false;
        }
        this.dismissing = false;
        this.opacity.snapTo(0.0D);
        return true;
    }
}
