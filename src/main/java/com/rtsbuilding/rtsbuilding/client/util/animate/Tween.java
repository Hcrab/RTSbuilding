package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * 补间动画——驱动单个数值从起始值到目标值的平滑过渡。
 *
 * <p>这是 {@link FloatAnimation} 的现代化替代，核心改进：</p>
 * <ul>
 *   <li>由 {@link AnimationEngine} 统一管理生命周期，无需手动调用 {@link #tick()}</li>
 *   <li>支持 {@link #onUpdate(DoubleConsumer)} 回调，解耦动画与消费方</li>
 *   <li>支持 {@link #chain(Tween)} 顺序链接、{@link AnimationEngine#parallel(List)} 并行组合</li>
 *   <li>内部使用 double 精度，避免大值动画的精度损失</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>{@code
 * // 创建并启动（自动注册到引擎）
 * Tween tween = AnimationEngine.getInstance()
 *         .tween(0.0, 1.0, 200)
 *         .ease(EasingFunctions.EASE_OUT_BACK)
 *         .onUpdate(val -> mySlider.setValue(val))
 *         .onComplete(() -> System.out.println("done"))
 *         .start();
 *
 * // 链接动画（当前完成后自动启动下一个）
 * tween.chain(nextTween);
 *
 * // 在动画进行中修改目标值（自动从当前值过渡到新目标）
 * tween.retarget(2.0);
 * }</pre>
 */
public final class Tween {

    // ======================== 状态枚举 ========================

    public enum State {
        /** 未启动 */
        IDLE,
        /** 播放中 */
        PLAYING,
        /** 已暂停 */
        PAUSED,
        /** 已结束 */
        FINISHED
    }

    // ======================== 实例字段 ========================

    private final double from;
    private final double to;
    private final long durationMs;
    private final EasingFunctions easing;

    private double currentValue;
    private double startFrom;         // 实际起始值（可被 retarget 修改）
    private double endTo;            // 实际目标值（可被 retarget 修改）
    private State state = State.IDLE;
    private long startTime;
    private long pauseElapsed;       // 暂停时已消耗的时间
    private DoubleConsumer onUpdate;
    private Runnable onComplete;
    private boolean completed;       // 防止 onComplete 重复触发
    private Tween chainedTween;      // 完成后自动启动的下一个动画

    Tween(double from, double to, long durationMs, EasingFunctions easing) {
        this.from = from;
        this.to = to;
        this.durationMs = durationMs;
        this.easing = easing;
        this.currentValue = from;
        this.startFrom = from;
        this.endTo = to;
    }

    // ======================== 构建器风格 API ========================

    /** 设置更新回调（每帧动画值变化时调用） */
    public Tween onUpdate(DoubleConsumer callback) {
        this.onUpdate = callback;
        return this;
    }

    /** 设置完成回调 */
    public Tween onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    // ======================== 生命周期 ========================

    /** 启动动画，自动注册到全局动画引擎。 */
    public Tween start() {
        if (state == State.FINISHED) return this;
        this.state = State.PLAYING;
        this.startTime = System.currentTimeMillis();
        this.startFrom = this.currentValue;
        this.completed = false;
        this.durationRemaining = 0;
        AnimationEngine.getInstance().register(this);
        return this;
    }

    /** 暂停动画 */
    public void pause() {
        if (state != State.PLAYING) return;
        this.state = State.PAUSED;
        this.pauseElapsed = System.currentTimeMillis() - this.startTime;
    }

    /** 恢复动画 */
    public void resume() {
        if (state != State.PAUSED) return;
        this.state = State.PLAYING;
        this.startTime = System.currentTimeMillis() - this.pauseElapsed;
    }

    /**
     * 重新设定目标值——从当前值平滑过渡到新目标。
     * <p>动画时长按剩余时间比例缩放，保持相同的"完成速度感"。</p>
     */
    public void retarget(double newTo) {
        this.endTo = newTo;
        this.startFrom = this.currentValue;
        if (state == State.PLAYING) {
            long elapsed = System.currentTimeMillis() - this.startTime;
            double progress = Math.min(1.0, (double) elapsed / durationMs);
            long remaining = (long) ((1.0 - progress) * durationMs);
            this.startTime = System.currentTimeMillis();
            this.durationRemaining = remaining;
        }
    }
    // 运行时剩余时长（retarget 时修改）
    private long durationRemaining;

    /**
     * 链接动画——当前 Tween 完成后自动启动下一个。
     *
     * @param next 跟随执行的 Tween（需已配置好参数，start() 由系统自动调用）
     */
    public Tween chain(Tween next) {
        this.chainedTween = next;
        return next;
    }

    /**
     * 强制跳转到结束状态并触发完成回调。
     */
    public void finish() {
        if (state == State.FINISHED) return;
        this.currentValue = this.endTo;
        this.state = State.FINISHED;
        notifyUpdate();
        notifyComplete();
        if (chainedTween != null) {
            chainedTween.start();
        }
    }

    /** 强制跳转到指定值并结束动画。 */
    public void snapTo(double value) {
        this.currentValue = value;
        this.startFrom = value;
        this.state = State.IDLE;
    }

    // ======================== 引擎内部方法 ========================

    /**
     * 推进动画一帧（由 {@link AnimationEngine#tick()} 统一调用）。
     *
     * @return true 表示动画仍在进行中，false 表示已结束
     */
    boolean tick() {
        if (state != State.PLAYING) return state != State.FINISHED;

        long now = System.currentTimeMillis();
        long elapsed = now - this.startTime;
        long effectiveDuration = (this.durationRemaining > 0) ? this.durationRemaining : this.durationMs;

        if (elapsed >= effectiveDuration) {
            this.currentValue = this.endTo;
            this.state = State.FINISHED;
            notifyUpdate();
            notifyComplete();
            if (chainedTween != null) {
                chainedTween.start();
            }
            return false;
        }

        double t = (double) elapsed / effectiveDuration;
        float easedT = easing.apply((float) t);
        this.currentValue = this.startFrom + (this.endTo - this.startFrom) * easedT;
        notifyUpdate();
        return true;
    }

    /** 获取当前动画值 */
    public double getValue() {
        return currentValue;
    }

    /** 获取当前状态的浮点值（便利方法） */
    public float getFloat() {
        return (float) currentValue;
    }

    /** 动画是否正在进行 */
    public boolean isRunning() {
        return state == State.PLAYING;
    }

    /** 动画是否已结束 */
    public boolean isFinished() {
        return state == State.FINISHED;
    }

    // ======================== 内部 ========================

    private void notifyUpdate() {
        if (onUpdate != null) {
            onUpdate.accept(currentValue);
        }
    }

    private void notifyComplete() {
        if (!completed && onComplete != null) {
            completed = true;
            onComplete.run();
        }
    }
}
