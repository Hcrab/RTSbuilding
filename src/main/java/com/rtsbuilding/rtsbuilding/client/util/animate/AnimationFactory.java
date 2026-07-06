package com.rtsbuilding.rtsbuilding.client.util.animate;

/**
 * 动画工厂——集中管理 UI 动画器的创建和时长配置。
 *
 * <p>所有 UI 组件应通过此工厂创建动画器，
 * 避免将魔法数值的动画时长散落在各个组件中。</p>
 *
 * <p><b>支持的动画类型：</b></p>
 * <ul>
 *   <li>{@link #newHoverAnim()} —— 悬浮态过渡 FloatAnimation（120ms, SMOOTHSTEP）</li>
 *   <li>{@link #newSlideAnim()} —— 滑块位移 FloatAnimation（200ms, EASE_OUT_BACK）</li>
 *   <li>{@link #newExpandAnim()} —— 展开/收起 FloatAnimation（300ms, EASE_OUT_QUART）</li>
 *   <li>{@link #newPopupAnim()} —— 弹窗弹出 FloatAnimation（250ms, EASE_OUT_BACK）</li>
 *   <li>{@link #newFadeAnim()} —— 淡入淡出 FloatAnimation（150ms, LINEAR）</li>
 * </ul>
 */
public final class AnimationFactory {

    // ======================== 时长常量 ========================

    /** 悬浮态过渡动画时长（毫秒） */
    public static final long HOVER_DURATION_MS = 120L;
    /** 滑块位移动画时长（毫秒） */
    public static final long SLIDE_DURATION_MS = 200L;
    /** 展开/收起动画时长（毫秒） */
    public static final long EXPAND_DURATION_MS = 300L;
    /** 弹窗弹出动画时长（毫秒） */
    public static final long POPUP_DURATION_MS = 250L;
    /** 淡入淡出动画时长（毫秒） */
    public static final long FADE_DURATION_MS = 150L;

    private AnimationFactory() {}

    // ======================== FloatAnimation 工厂方法 ========================

    /**
     * 创建悬浮态过渡 FloatAnimation（120ms, SMOOTHSTEP）。
     * <p>适用于按钮、面板背景、折叠条标题栏等 UI 组件的
     * 鼠标悬浮视觉效果切换。</p>
     */
    public static FloatAnimation newHoverAnim() {
        return FloatAnimation.builder()
                .duration(HOVER_DURATION_MS)
                .easing(EasingFunctions.SMOOTHSTEP)
                .startFromCurrent(true)
                .build();
    }

    /**
     * 创建滑块位移动画 FloatAnimation（200ms, EASE_OUT_BACK）。
     * <p>适用于主题开关、滑条等组件的滑块位置过渡。
     * EASE_OUT_BACK 会产生略过目标再弹回的效果，手感更自然。</p>
     */
    public static FloatAnimation newSlideAnim() {
        return FloatAnimation.builder()
                .duration(SLIDE_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_BACK)
                .startFromCurrent(true)
                .build();
    }

    /**
     * 创建展开/收起 FloatAnimation（300ms, EASE_OUT_QUART）。
     * <p>适用于折叠面板内容区的展开和收起过渡。</p>
     */
    public static FloatAnimation newExpandAnim() {
        return FloatAnimation.builder()
                .duration(EXPAND_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_QUART)
                .startFromCurrent(true)
                .build();
    }

    /**
     * 创建弹窗弹出 FloatAnimation（250ms, EASE_OUT_BACK）。
     * <p>适用于浮窗、提示框、弹出菜单等组件的弹入效果。</p>
     */
    public static FloatAnimation newPopupAnim() {
        return FloatAnimation.builder()
                .duration(POPUP_DURATION_MS)
                .easing(EasingFunctions.EASE_OUT_BACK)
                .startFromCurrent(true)
                .build();
    }

    /**
     * 创建淡入淡出 FloatAnimation（150ms, LINEAR）。
     * <p>适用于透明度过渡、渐隐渐现等场景。采用 LINEAR 缓动以确保透明度变化均匀，
     * 视觉上更自然，不会出现 S 形曲线在中间段加速的感觉。</p>
     */
    public static FloatAnimation newFadeAnim() {
        return FloatAnimation.builder()
                .duration(FADE_DURATION_MS)
                .easing(EasingFunctions.LINEAR)
                .startFromCurrent(true)
                .build();
    }
}
