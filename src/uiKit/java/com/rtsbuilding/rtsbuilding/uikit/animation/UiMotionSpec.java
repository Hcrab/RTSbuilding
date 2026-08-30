package com.rtsbuilding.rtsbuilding.uikit.animation;

/**
 * RTS UI 的统一短动效节奏。
 *
 * <p>这里只声明视觉持续时间和浮现距离，不拥有任何业务状态，也不决定控件是否可点击。
 * 生产客户端、离屏预览和测试共用这些常量，避免同一种交互在不同面板里忽快忽慢。</p>
 */
public final class UiMotionSpec {
    public static final long HOVER_MS = 90L;
    public static final long SELECTION_MS = 140L;
    public static final long PRESS_MS = 65L;
    public static final long ENABLED_MS = 100L;
    public static final long STATE_BLEND_MS = 110L;
    public static final long SLIDER_MS = 90L;
    public static final long WINDOW_REVEAL_MS = 180L;
    public static final long WINDOW_DISMISS_MS = 140L;
    /** 窗口显隐时的短距离垂直漂移；命中矩形始终留在最终位置。 */
    public static final double WINDOW_FLOAT_PX = 4.0D;

    private UiMotionSpec() {
    }
}
