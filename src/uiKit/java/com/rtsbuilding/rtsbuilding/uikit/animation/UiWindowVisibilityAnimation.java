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
    public UiWindowVisibilityAnimation(UiClock clock, boolean initiallyVisible) { opacity = new UiFloatAnimation(clock, initiallyVisible ? 1.0D : 0.0D); }
    public void reveal(boolean animationsEnabled) {
        boolean resumeDismissal = dismissing;
        dismissing = false;
        if (!resumeDismissal) opacity.snapTo(animationsEnabled ? 0.0D : 1.0D);
        opacity.animateTo(1.0D, animationsEnabled ? UiMotionSpec.WINDOW_REVEAL_MS : 0L, UiEasing.EASE_OUT_CUBIC);
    }
    public void dismiss(boolean animationsEnabled) {
        dismissing = animationsEnabled;
        if (animationsEnabled) opacity.animateTo(0.0D, UiMotionSpec.WINDOW_DISMISS_MS, UiEasing.EASE_OUT_CUBIC);
        else opacity.snapTo(0.0D);
    }
    public boolean shouldRender(boolean logicallyVisible) { return logicallyVisible || dismissing; }
    public boolean isDismissing() { return dismissing; }
    public double opacity() { return opacity.value(); }
    public double subtreeTintOpacity() { return opacity.value(); }
    public double offsetY() { return (1.0D - opacity.value()) * UiMotionSpec.WINDOW_FLOAT_PX; }
    public boolean finishDismissalIfNeeded(boolean animationsEnabled) {
        if (!dismissing || animationsEnabled && !opacity.isFinished()) return false;
        dismissing = false;
        opacity.snapTo(0.0D);
        return true;
    }
}
