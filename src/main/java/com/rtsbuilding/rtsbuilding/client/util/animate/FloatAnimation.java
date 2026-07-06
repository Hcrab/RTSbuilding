package com.rtsbuilding.rtsbuilding.client.util.animate;

import net.minecraft.Util;

/**
 * 单值平滑动画器——管理一个 float 值从起始值到目标值的过渡动画。
 *
 * <p><b>特性：</b></p>
 * <ul>
 *   <li>支持 12 种 {@link EasingFunctions} 缓动曲线</li>
 *   <li>Builder 模式构建，清晰可读</li>
 *   <li>支持动画完成回调</li>
 *   <li>与 {@link AnimationGroup} 组合使用</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * FloatAnimation anim = FloatAnimation.builder()
 *         .from(0.0f).to(1.0f)
 *         .duration(300)
 *         .easing(EasingFunctions.EASE_OUT_BACK)
 *         .onComplete(() -> System.out.println("done!"))
 *         .build();
 * anim.start();
 *
 * // 每帧 tick
 * anim.tick();
 * float value = anim.getValue();
 * }</pre>
 */
public class FloatAnimation {

    /** 全局动画开关（默认开启），由渲染设置面板控制 */
    private static boolean enabled = true;

    /**
     * 全局动画是否启用。
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置全局动画开关。
     */
    public static void setEnabled(boolean v) {
        enabled = v;
    }

    // ======================== Builder ========================

    /**
     * FloatAnimation 构建器。
     */
    public static final class Builder {
        private float fromValue;
        private float toValue;
        private long durationMs = 200L;
        private EasingFunctions easing = EasingFunctions.SMOOTHSTEP;
        private Runnable onComplete;
        private boolean startFromCurrent;

        private Builder() {}

        /** 起始值（默认 0） */
        public Builder from(float from) { this.fromValue = from; return this; }

        /** 目标值（默认 0） */
        public Builder to(float to) { this.toValue = to; return this; }

        /** 动画时长（毫秒，默认 200） */
        public Builder duration(long ms) { this.durationMs = ms; return this; }

        /** 缓动曲线（默认 SMOOTHSTEP） */
        public Builder easing(EasingFunctions easing) { this.easing = easing; return this; }

        /** 动画完成回调 */
        public Builder onComplete(Runnable onComplete) { this.onComplete = onComplete; return this; }

        /** 启动时从当前值开始（而非 from 值） */
        public Builder startFromCurrent(boolean startFromCurrent) { this.startFromCurrent = startFromCurrent; return this; }

        /** 构建 FloatAnimation 实例 */
        public FloatAnimation build() { return new FloatAnimation(this); }
    }

    /** 创建构建器 */
    public static Builder builder() { return new Builder(); }

    // ======================== 实例字段 ========================

    private final long durationMs;
    private final EasingFunctions easing;
    private final Runnable onComplete;
    private final boolean startFromCurrent;

    private float fromValue;
    private float toValue;
    private float currentValue;
    private long startTime = -1L;

    private FloatAnimation(Builder builder) {
        this.fromValue = builder.fromValue;
        this.toValue = builder.toValue;
        this.durationMs = builder.durationMs;
        this.easing = builder.easing;
        this.onComplete = builder.onComplete;
        this.startFromCurrent = builder.startFromCurrent;
        this.currentValue = builder.fromValue;
    }

    // ======================== 生命周期 ========================

    /**
     * 启动动画：从预设起始值或当前值过渡到目标值。
     */
    public void start() {
        if (!enabled) {
            snapTo(toValue);
            return;
        }
        if (startFromCurrent) {
            this.fromValue = this.currentValue;
        }
        this.startTime = Util.getMillis();
    }

    /**
     * 启动动画到指定目标值（覆盖预设目标值）。
     *
     * @param to 目标值
     */
    public void start(float to) {
        this.toValue = to;
        start();
    }

    /**
     * 从指定起始值到目标值启动动画。
     *
     * @param from 起始值
     * @param to   目标值
     */
    public void start(float from, float to) {
        this.fromValue = from;
        this.toValue = to;
        this.startTime = enabled ? Util.getMillis() : -1L;
        if (!enabled) {
            this.currentValue = to;
        }
    }

    /**
     * 推进动画帧，更新当前值。
     * <p>应在每帧渲染时调用。</p>
     */
    public void tick() {
        if (this.startTime < 0) return;
        long elapsed = Util.getMillis() - this.startTime;
        if (elapsed >= this.durationMs) {
            this.currentValue = this.toValue;
            this.startTime = -1L;
            if (onComplete != null) onComplete.run();
            return;
        }
        float t = (float) elapsed / this.durationMs;
        float easedT = easing.apply(t);
        this.currentValue = this.fromValue + (this.toValue - this.fromValue) * easedT;
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
        this.startTime = -1L;
    }

    /**
     * 获取目标值。
     */
    public float getToValue() {
        return toValue;
    }

    /**
     * 获取起始值。
     */
    public float getFromValue() {
        return fromValue;
    }
}
