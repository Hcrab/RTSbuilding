package com.rtsbuilding.rtsbuilding.client.util;

/**
 * 动画工厂——集中管理 UI 动画器的创建和时长配置。
 *
 * <p>所有 UI 组件应通过此工厂创建 {@link SmoothAnimator} 实例，
 * 避免将魔法数值的动画时长散落在各个组件中。
 * 如需全局调整动画速度，只需修改本类的常量或工厂方法。</p>
 *
 * <p><b>支持的动画类型：</b></p>
 * <ul>
 *   <li>{@link #createHoverAnim()} —— 悬浮态过渡（120ms）</li>
 *   <li>{@link #createSlideAnim()} —— 滑块位移（200ms）</li>
 *   <li>{@link #createExpandAnim()} —— 展开/收起（300ms）</li>
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

    private AnimationFactory() {}

    // ======================== 工厂方法 ========================

    /**
     * 创建悬浮态过渡动画器（120ms）。
     * <p>适用于按钮、面板背景、折叠条标题栏等 UI 组件的
     * 鼠标悬浮视觉效果切换。</p>
     */
    public static SmoothAnimator createHoverAnim() {
        return new SmoothAnimator(HOVER_DURATION_MS);
    }

    /**
     * 创建滑块位移动画器（200ms）。
     * <p>适用于主题开关、滑条等组件的滑块位置过渡。</p>
     */
    public static SmoothAnimator createSlideAnim() {
        return new SmoothAnimator(SLIDE_DURATION_MS);
    }

    /**
     * 创建展开/收起动画器（300ms）。
     * <p>适用于折叠面板内容区的展开和收起过渡。</p>
     */
    public static SmoothAnimator createExpandAnim() {
        return new SmoothAnimator(EXPAND_DURATION_MS);
    }
}
