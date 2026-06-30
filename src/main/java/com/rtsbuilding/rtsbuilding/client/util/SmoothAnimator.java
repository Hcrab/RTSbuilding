package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.Util;

/**
 * 平滑动画器——管理一个数值从起始值到目标值的过渡动画。
 *
 * <p>支持缓入缓出（smoothstep），可用于 UI 组件中的数值动画，
 * 如透明度、位移、旋转角度等。</p>
 */
public class SmoothAnimator {

    /** UI 平滑动画全局开关（默认开启），由渲染设置面板控制 */
    public static boolean enabled = true;

    private float currentValue;
    private float startValue;
    private float endValue;
    private long startTime = -1;
    private final long durationMs;

    /**
     * @param durationMs 动画持续时间（毫秒）
     */
    public SmoothAnimator(long durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * 启动动画：从当前值过渡到目标值。
     */
    public void start(float to) {
        if (!enabled) {
            snapTo(to);
            return;
        }
        start(this.currentValue, to);
    }

    /**
     * 启动动画：从指定起始值过渡到目标值。
     */
    public void start(float from, float to) {
        if (!enabled) {
            snapTo(to);
            return;
        }
        this.startValue = from;
        this.endValue = to;
        this.startTime = Util.getMillis();
    }

    /**
     * 推进动画帧，更新当前值。
     * <p>应在每帧渲染时调用。</p>
     */
    public void tick() {
        if (this.startTime < 0) return;
        long elapsed = Util.getMillis() - this.startTime;
        if (elapsed >= this.durationMs) {
            this.currentValue = this.endValue;
            this.startTime = -1;
            return;
        }
        float t = (float) elapsed / this.durationMs;
        t = smoothstep(t);
        this.currentValue = this.startValue + (this.endValue - this.startValue) * t;
    }

    /**
     * 获取当前动画值。
     */
    public float getValue() {
        return this.currentValue;
    }

    /**
     * 动画是否进行中。
     */
    public boolean isRunning() {
        return this.startTime >= 0;
    }

    /**
     * 强制跳转到指定值并停止动画。
     */
    public void snapTo(float value) {
        this.currentValue = value;
        this.startTime = -1;
    }

    /**
     * 使用当前动画进度进行交叉淡入淡出渲染。
     * <p>委托 {@link RtsClientUiUtil#renderCrossFade} 实现具体的渲染逻辑，
     * 自动使用动画器的当前值作为过渡进度 t。</p>
     *
     * @param normal      普通态渲染器
     * @param highlighted 高亮态渲染器
     */
    public void renderCrossFade(Runnable normal, Runnable highlighted) {
        com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil.renderCrossFade(this.currentValue, normal, highlighted);
    }

    /**
     * 平滑插值（smoothstep），缓入缓出效果。
     */
    public static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * 线性插值两个 ARGB 颜色。
     *
     * @param colorA 起始颜色（ARGB）
     * @param colorB 目标颜色（ARGB）
     * @param t      插值因子 [0, 1]
     * @return 插值后的 ARGB 颜色
     */
    public static int lerpColor(int colorA, int colorB, float t) {
        int a = (int)(((colorA >> 24 & 0xFF) * (1.0f - t) + (colorB >> 24 & 0xFF) * t));
        int r = (int)(((colorA >> 16 & 0xFF) * (1.0f - t) + (colorB >> 16 & 0xFF) * t));
        int g = (int)(((colorA >> 8 & 0xFF) * (1.0f - t) + (colorB >> 8 & 0xFF) * t));
        int b = (int)(((colorA & 0xFF) * (1.0f - t) + (colorB & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 对颜色各 RGB 通道进行统一缩放（暗化或亮化）。
     *
     * @param color 原始 ARGB 颜色
     * @param factor 缩放因子 (&lt;1 暗化, &gt;1 亮化)
     * @return 缩放后的 ARGB 颜色（Alpha 保持不变）
     */
    public static int scaleColor(int color, float factor) {
        int a = color >> 24 & 0xFF;
        int r = (int)((color >> 16 & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
