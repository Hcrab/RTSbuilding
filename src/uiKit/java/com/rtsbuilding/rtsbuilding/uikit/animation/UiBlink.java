package com.rtsbuilding.rtsbuilding.uikit.animation;

/**
 * 由注入时钟驱动的确定性闪烁相位。
 *
 * <p>本类只决定当前相位是否可见，不拥有焦点、光标几何、颜色或平台绘制。生产界面与
 * 离屏测试因此可以共享完全相同的节奏，而不在各自 renderer 中读取墙上时钟。</p>
 */
public final class UiBlink {
    public static final long CARET_PHASE_MILLIS = 300L;

    public static boolean caretVisible(UiClock clock) {
        return visible(clock, CARET_PHASE_MILLIS);
    }

    public static boolean visible(UiClock clock, long phaseMillis) {
        if (clock == null || phaseMillis <= 0L) {
            throw new IllegalArgumentException("clock must not be null and phase must be positive");
        }
        return Math.floorMod(Math.floorDiv(clock.nowMillis(), phaseMillis), 2L) == 0L;
    }

    private UiBlink() {
    }
}
