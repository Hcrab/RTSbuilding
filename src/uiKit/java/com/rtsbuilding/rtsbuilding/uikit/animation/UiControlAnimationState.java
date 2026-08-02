package com.rtsbuilding.rtsbuilding.uikit.animation;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;

/**
 * 单个通用控件拥有的有界交互动画。
 *
 * <p>每个实例固定持有悬停、选中、按下和禁用四条标量动画，不创建按帧缓存，
 * 不改变命中区域，也不延迟业务 action。生产 Minecraft 控件与离屏测试台可以
 * 共享同一状态推进规则。</p>
 */
public final class UiControlAnimationState {
    public static final long HOVER_DURATION_MS = 90L;
    public static final long SELECTION_DURATION_MS = 140L;
    public static final long PRESS_DURATION_MS = 65L;
    public static final long ENABLED_DURATION_MS = 100L;

    private final UiFloatAnimation hover;
    private final UiFloatAnimation selection;
    private final UiFloatAnimation press;
    private final UiFloatAnimation disabled;
    private boolean initialized;
    private boolean hoverTarget;
    private boolean selectionTarget;
    private boolean pressTarget;
    private boolean disabledTarget;

    public UiControlAnimationState(UiClock clock) {
        this.hover = new UiFloatAnimation(clock, 0.0D);
        this.selection = new UiFloatAnimation(clock, 0.0D);
        this.press = new UiFloatAnimation(clock, 0.0D);
        this.disabled = new UiFloatAnimation(clock, 0.0D);
    }

    public Snapshot update(UiControlState state, boolean animationsEnabled) {
        if (state == null) {
            throw new IllegalArgumentException("state");
        }
        boolean nextHover = state.isHovered() || state.isFocused();
        boolean nextSelection = state.isSelected();
        boolean nextPress = state.isPressed();
        boolean nextDisabled = !state.isEnabled();
        if (!this.initialized || !animationsEnabled) {
            this.hover.snapTo(nextHover ? 1.0D : 0.0D);
            this.selection.snapTo(nextSelection ? 1.0D : 0.0D);
            this.press.snapTo(nextPress ? 1.0D : 0.0D);
            this.disabled.snapTo(nextDisabled ? 1.0D : 0.0D);
            this.initialized = true;
        } else {
            animateChanged(this.hover, this.hoverTarget, nextHover,
                    HOVER_DURATION_MS);
            animateChanged(this.selection, this.selectionTarget, nextSelection,
                    SELECTION_DURATION_MS);
            animateChanged(this.press, this.pressTarget, nextPress,
                    PRESS_DURATION_MS);
            animateChanged(this.disabled, this.disabledTarget, nextDisabled,
                    ENABLED_DURATION_MS);
        }
        this.hoverTarget = nextHover;
        this.selectionTarget = nextSelection;
        this.pressTarget = nextPress;
        this.disabledTarget = nextDisabled;
        return snapshot();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                this.hover.value(),
                this.selection.value(),
                this.press.value(),
                this.disabled.value());
    }

    private static void animateChanged(
            UiFloatAnimation animation,
            boolean oldTarget,
            boolean newTarget,
            long duration) {
        if (oldTarget != newTarget) {
            animation.animateTo(
                    newTarget ? 1.0D : 0.0D,
                    duration,
                    UiEasing.EASE_OUT_CUBIC);
        }
    }

    /** 不可变的当前视觉强度；所有值都位于 0..1。 */
    public static final class Snapshot {
        private final double hover;
        private final double selection;
        private final double press;
        private final double disabled;

        private Snapshot(
                double hover,
                double selection,
                double press,
                double disabled) {
            this.hover = hover;
            this.selection = selection;
            this.press = press;
            this.disabled = disabled;
        }

        public double hover() {
            return this.hover;
        }

        public double selection() {
            return this.selection;
        }

        public double press() {
            return this.press;
        }

        public double disabled() {
            return this.disabled;
        }
    }
}
