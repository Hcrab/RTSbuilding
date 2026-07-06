package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 动画组合器——支持多个动画的并行或串行组合播放。
 *
 * <p><b>解决的问题：</b></p>
 * <p>UI 组件常需同时控制多个动画（如折叠条同时运行动画箭头旋转和内容展开），
 * 或按顺序执行链式动画（如浮窗：淡入 → 等待 → 向上滑动）。</p>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 并行：箭头旋转 + 内容展开同时进行
 * AnimationGroup parallel = AnimationGroup.parallel(
 *         arrowAnim, contentAnim
 * );
 * parallel.start();
 *
 * // 串行：先淡入，再向上滑动
 * AnimationGroup sequence = AnimationGroup.sequence(
 *         fadeInAnim,
 *         slideUpAnim
 * );
 * sequence.start();
 * }</pre>
 */
public final class AnimationGroup {

    /** 组合策略 */
    public enum Mode {
        /** 并行——所有动画同时启动 */
        PARALLEL,
        /** 串行——上一个动画完成后启动下一个 */
        SEQUENCE
    }

    /**
     * 创建并行动画组——所有动画同时启动。
     *
     * @param animations 参与的 FloatAnimation
     * @return 动画组
     */
    public static AnimationGroup parallel(FloatAnimation... animations) {
        return new AnimationGroup(Mode.PARALLEL, Arrays.asList(animations));
    }

    /**
     * 创建串行动画组——上一个完成后启动下一个。
     *
     * @param animations 参与的 FloatAnimation
     * @return 动画组
     */
    public static AnimationGroup sequence(FloatAnimation... animations) {
        return new AnimationGroup(Mode.SEQUENCE, Arrays.asList(animations));
    }

    // ======================== 实例 ========================

    private final Mode mode;
    private final List<FloatAnimation> animations;
    private int currentIndex;
    private boolean started;

    private AnimationGroup(Mode mode, List<FloatAnimation> animations) {
        this.mode = mode;
        this.animations = new ArrayList<>(animations);
    }

    /**
     * 启动动画组。
     */
    public void start() {
        if (animations.isEmpty()) return;
        this.started = true;
        switch (mode) {
            case PARALLEL -> animations.forEach(FloatAnimation::start);
            case SEQUENCE -> {
                currentIndex = 0;
                animations.get(0).start();
            }
        }
    }

    /**
     * 推进所有动画。每帧调用一次。
     */
    public void tick() {
        if (!started) return;

        switch (mode) {
            case PARALLEL -> animations.forEach(FloatAnimation::tick);
            case SEQUENCE -> {
                if (currentIndex >= animations.size()) return;
                FloatAnimation current = animations.get(currentIndex);
                current.tick();
                if (!current.isRunning()) {
                    currentIndex++;
                    if (currentIndex < animations.size()) {
                        animations.get(currentIndex).start();
                    }
                }
            }
        }
    }

    /**
     * 动画组是否全部执行完毕。
     */
    public boolean isFinished() {
        if (!started) return false;
        return switch (mode) {
            case PARALLEL -> animations.stream().noneMatch(FloatAnimation::isRunning);
            case SEQUENCE -> currentIndex >= animations.size();
        };
    }

    /**
     * 获取指定索引的动画值。
     *
     * @param index 动画索引
     * @return 当前值，索引越界返回 0
     */
    public float getValue(int index) {
        if (index < 0 || index >= animations.size()) return 0.0f;
        return animations.get(index).getValue();
    }

    /**
     * 强行停止所有动画，跳转到目标值。
     */
    public void snapToEnd() {
        switch (mode) {
            case PARALLEL -> animations.forEach(a -> a.snapTo(a.getToValue()));
            case SEQUENCE -> {
                for (FloatAnimation a : animations) {
                    a.snapTo(a.getToValue());
                }
                currentIndex = animations.size();
            }
        }
    }
}
