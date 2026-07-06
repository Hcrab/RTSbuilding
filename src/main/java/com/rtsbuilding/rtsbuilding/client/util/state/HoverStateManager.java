package com.rtsbuilding.rtsbuilding.client.util.state;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;

/**
 * 悬浮状态管理器——集中管理 UI 组件的鼠标悬浮状态检测和动画过渡。
 *
 * <p><b>解决的问题：</b></p>
 * <p>此前每个需要悬浮高亮效果的组件（{@code RtsPanel}、{@code RtsButton}、
 * {@code ThemeSwitchComponent}、{@code CollapsibleSection} 等）都各自维护
 * 一套相同的字段和逻辑，此类将以上模式统一封装。</p>
 *
 * <p><b>悬浮抑制机制：</b></p>
 * <p>通过 {@link #floatingWindowSuppression()} 全局抑制上下文实现。
 * 当弹窗/浮窗打开时，上层面板主动设为 true，下层组件的悬浮高亮自动抑制。
 * 调用方无需额外处理，直接在渲染帧传 {@code isMouseOver} 即可。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 在组件中声明
 * private final HoverStateManager hoverState = new HoverStateManager();
 *
 * // 在渲染帧中
 * float t = hoverState.update(isMouseOver(mouseX, mouseY));
 * }</pre>
 */
public class HoverStateManager {

    /** 悬浮动画器 */
    private final FloatAnimation animator;

    private boolean lastHovered;

    public HoverStateManager() {
        this.animator = AnimationFactory.newHoverAnim();
    }

    // ======================== 全局悬浮抑制（用于浮动窗口遮挡场景）====================

    /** 浮动窗口抑制上下文——当浮动窗口/弹窗覆盖在下层面板上方时，
     * 由 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel}
     * 和 {@link com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen}
     * 设置为 true 以抑制下层组件的悬浮高亮效果。
     * 下层组件的 {@link #update(boolean)} 自动检查此上下文。 */
    private static final HoverSuppression FLOATING_WINDOW_SUPPRESSION
            = new HoverSuppression();

    /**
     * 获取浮动窗口抑制上下文——当浮动窗口打开时设为 true，
     * 下层组件的悬浮高亮效果将被抑制。
     * <p>实例级抑制通过 {@link #setSuppression(HoverSuppression)} 为每个组件单独设置。
     * 此全局抑制适用于整个 UI 树的批量遮挡场景。</p>
     */
    public static HoverSuppression floatingWindowSuppression() {
        return FLOATING_WINDOW_SUPPRESSION;
    }

    // ======================== 核心 API ========================

    /**
     * 更新悬浮状态并推进动画。
     *
     * @param hovered 当前帧的原始悬浮检测结果（会被全局抑制自动过滤）
     * @return 当前动画进度值 [0, 1]
     */
    public float update(boolean hovered) {
        boolean effective = hovered && !FLOATING_WINDOW_SUPPRESSION.isSuppressed();
        if (effective != this.lastHovered) {
            this.lastHovered = effective;
            this.animator.start(effective ? 1.0f : 0.0f);
        }
        this.animator.tick();
        return this.animator.getValue();
    }

    /** 获取当前动画进度值 [0, 1]。 */
    public float getValue() {
        return this.animator.getValue();
    }

    /** 当前组件是否处于"被悬浮"状态。 */
    public boolean isActive() {
        return this.lastHovered;
    }

    /** 动画是否仍在进行中。 */
    public boolean isAnimating() {
        return this.animator.isRunning();
    }

    /** 强制跳转到指定悬浮状态（不播放动画）。 */
    public void snapTo(boolean hovered) {
        this.lastHovered = hovered;
        this.animator.snapTo(hovered ? 1.0f : 0.0f);
    }

    /**
     * 使用当前动画进度进行交叉淡入淡出渲染。
     */
    public void renderCrossFade(Runnable normal, Runnable hovered) {
        CrossFadeRenderer.render(this.animator.getValue(), normal, hovered);
    }
}
