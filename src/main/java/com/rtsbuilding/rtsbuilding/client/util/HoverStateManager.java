package com.rtsbuilding.rtsbuilding.client.util;

/**
 * 悬浮状态管理器——集中管理 UI 组件的鼠标悬浮状态检测和动画过渡。
 *
 * <p><b>解决的问题：</b></p>
 * <p>此前每个需要悬浮高亮效果的组件（{@code RtsPanel}、{@code RtsButton}、
 * {@code ThemeSwitchComponent}、{@code CollapsibleSection} 等）都各自维护
 * 一套相同的字段和逻辑：</p>
 * <ul>
 *   <li>{@code SmoothAnimator hoverAnim} —— 悬浮动画器</li>
 *   <li>{@code boolean lastHovered} —— 上一帧悬浮状态</li>
 *   <li>渲染帧中检测状态变化 → 启动动画 → tick → 取值</li>
 * </ul>
 *
 * <p>此类将以上模式统一封装，组件只需持有本管理器实例，
 * 在每帧渲染时调用 {@link #update(boolean)} 即可。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 在组件中声明
 * private final HoverStateManager hoverState = new HoverStateManager();
 *
 * // 在渲染帧中（通常每帧调用一次）
 * hoverState.update(isMouseOver(mouseX, mouseY));
 *
 * // 获取动画进度
 * float t = hoverState.getValue();
 *
 * // 交叉淡入淡出渲染
 * hoverState.renderCrossFade(normalRenderer, hoverRenderer);
 * }</pre>
 */
public class HoverStateManager {

    // ======================== 全局抑制（被上层面板遮挡时使用） ========================

    /** 当为 true 时，所有 HoverStateManager 实例的悬浮效果会被抑制 */
    private static boolean globallySuppressed;

    /**
     * 设置全局悬浮抑制状态。
     * <p>当某个 UI 组件被上层面板（如浮动窗口）遮挡时，应调用此方法传入 true，
     * 使所有下层组件的悬浮高亮效果被抑制，避免被遮挡区域误亮起。</p>
     *
     * @param suppressed true=抑制所有悬浮效果，false=恢复正常
     */
    public static void setGloballySuppressed(boolean suppressed) {
        globallySuppressed = suppressed;
    }

    /**
     * 当前是否处于全局悬浮抑制状态。
     */
    public static boolean isGloballySuppressed() {
        return globallySuppressed;
    }

    // ======================== 实例状态 ========================

    private final SmoothAnimator animator;
    private boolean lastHovered;

    public HoverStateManager() {
        this.animator = AnimationFactory.createHoverAnim();
    }

    /**
     * 更新悬浮状态并推进动画。
     * <p>每次渲染帧调用一次，传入当前帧的原始悬浮检测结果。
     * 内部自动检测状态变化并启动/停止动画过渡。</p>
     *
     * <p><b>注意：</b>如果全局处于抑制状态（{@link #setGloballySuppressed(boolean)}），
     * 传入 true 也不会触发悬浮动画效果，等效于传入 false。</p>
     *
     * @param hovered 当前帧的原始悬浮检测结果（不受全局抑制影响的值）
     * @return 当前动画进度值 [0, 1]（0=完全普通态，1=完全悬浮态）
     */
    public float update(boolean hovered) {
        boolean effective = hovered && !globallySuppressed;
        if (effective != this.lastHovered) {
            this.lastHovered = effective;
            this.animator.start(effective ? 1.0f : 0.0f);
        }
        this.animator.tick();
        return this.animator.getValue();
    }

    /**
     * 获取当前动画进度值 [0, 1]。
     * <p>0 = 完全普通态，1 = 完全悬浮态。</p>
     */
    public float getValue() {
        return this.animator.getValue();
    }

    /**
     * 当前组件是否处于"被悬浮"状态（即最近一次 {@link #update} 传入 {@code true}）。
     * <p>注意：这与动画进度无关，即使动画还未完全过渡到 1.0，
     * 只要当前帧检测到悬浮，此方法就返回 {@code true}。</p>
     */
    public boolean isActive() {
        return this.lastHovered;
    }

    /**
     * 动画是否仍在进行中。
     */
    public boolean isAnimating() {
        return this.animator.isRunning();
    }

    /**
     * 强制跳转到指定悬浮状态（不播放动画）。
     *
     * @param hovered true=完全悬浮态（值=1.0），false=完全普通态（值=0.0）
     */
    public void snapTo(boolean hovered) {
        this.lastHovered = hovered;
        this.animator.snapTo(hovered ? 1.0f : 0.0f);
    }

    /**
     * 使用当前动画进度进行交叉淡入淡出渲染。
     * <p>委托 {@link RtsClientUiUtil#renderCrossFade} 实现，
     * 自动使用管理器的当前值作为过渡进度 t。</p>
     *
     * @param normal    普通态渲染器
     * @param hovered   悬浮态渲染器
     */
    public void renderCrossFade(Runnable normal, Runnable hovered) {
        RtsClientUiUtil.renderCrossFade(this.animator.getValue(), normal, hovered);
    }
}
