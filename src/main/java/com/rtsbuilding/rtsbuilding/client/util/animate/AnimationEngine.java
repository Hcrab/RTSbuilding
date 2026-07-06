package com.rtsbuilding.rtsbuilding.client.util.animate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 全局动画引擎——统一管理所有活跃 {@link Tween} 的生命周期。
 *
 * <p><b>解决的问题：</b></p>
 * <ul>
 *   <li>此前每个 UI 组件各自维护 {\@link FloatAnimation}，需要手动调用 tick()</li>
 *   <li>分散的动画器导致帧管理碎片化，无法统一优化</li>
 *   <li>缺乏并行/顺序组合能力</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * // 在 BuilderScreen.tick() 中调用（仅需一次）
 * AnimationEngine.getInstance().tick();
 *
 * // 创建并启动补间动画
 * AnimationEngine.getInstance()
 *         .tween(0.0, 1.0, 200)
 *         .ease(EasingFunctions.EASE_OUT_BACK)
 *         .onUpdate(val -> someComponent.setValue(val))
 *         .start();
 *
 * // 并行执行多个动画
 * AnimationEngine.getInstance().parallel(
 *     engine.tween(0, 200, 200).onUpdate(w -> panel.setWidth(w)),
 *     engine.tween(0, 1f, 200).onUpdate(a -> panel.setAlpha(a))
 * ).start();
 * }</pre>
 *
 * <p><b>线程安全：</b>非线程安全。所有操作需在 Minecraft 渲染线程中调用。</p>
 */
public final class AnimationEngine {

    private static final AnimationEngine INSTANCE = new AnimationEngine();

    /** 当前帧活跃的补间动画列表 */
    private final List<Tween> activeTweens = new ArrayList<>();

    /** 下一帧待添加的补间（避免遍历中并发修改） */
    private final List<Tween> pendingAdd = new ArrayList<>();

    /** 是否已启用（默认开启） */
    private boolean enabled = true;

    private AnimationEngine() {}

    /** 获取全局唯一实例 */
    public static AnimationEngine getInstance() {
        return INSTANCE;
    }

    // ======================== 启用/禁用 ========================

    /** 全局动画是否启用。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置全局动画开关（与 FloatAnimation.setEnabled 同步）。 */
    public void setEnabled(boolean v) {
        this.enabled = v;
        if (!v) {
            // 禁用时立即结束所有动画
            for (Tween t : activeTweens) {
                t.finish();
            }
            activeTweens.clear();
            pendingAdd.clear();
        }
    }

    // ======================== 核心生命周期 ========================

    /**
     * 推进所有活跃动画一帧。
     * <p>必须在主渲染线程每帧调用一次，通常位于 {@code Screen.tick()} 中。</p>
     */
    public void tick() {
        if (!enabled) return;

        // 将待添加的补间移入活跃列表
        if (!pendingAdd.isEmpty()) {
            activeTweens.addAll(pendingAdd);
            pendingAdd.clear();
        }

        // 推进所有补间，移除已完成的
        Iterator<Tween> it = activeTweens.iterator();
        while (it.hasNext()) {
            Tween tween = it.next();
            boolean alive = tween.tick();
            if (!alive) {
                it.remove();
            }
        }
    }

    /**
     * 立即完成所有活跃动画。
     */
    public void flush() {
        for (Tween t : activeTweens) {
            t.finish();
        }
        activeTweens.clear();
        pendingAdd.clear();
    }

    /** 当前活跃的动画数量。 */
    public int activeCount() {
        return activeTweens.size();
    }

    // ======================== Tween 工厂 ========================

    /**
     * 创建从 from 到 to 的补间动画。
     *
     * @param from       起始值
     * @param to         目标值
     * @param durationMs 时长（毫秒）
     */
    public Tween tween(double from, double to, long durationMs) {
        return new Tween(from, to, durationMs, EasingFunctions.SMOOTHSTEP);
    }

    /**
     * 创建从 from 到 to 的补间动画，指定缓动函数。
     */
    public Tween tween(double from, double to, long durationMs, EasingFunctions easing) {
        return new Tween(from, to, durationMs, easing);
    }

    // ======================== 组合动画 ========================

    /**
     * 创建并行动画组——所有子补间同时启动。
     * <p>返回一个虚拟 {@link Tween}，其生命周期是所有子补间的并集。
     * 可在此虚拟 Tween 上设置 {@code onComplete} 监听全部完成。</p>
     */
    public ParallelTween parallel(Tween... tweens) {
        return new ParallelTween(this, tweens);
    }

    /**
     * 创建顺序动画链——子补间依次执行。
     * <p>等价于手动调用 {@code tween1.chain(tween2).chain(tween3)}。</p>
     */
    public Tween sequence(Tween first, Tween... rest) {
        Tween current = first;
        for (Tween next : rest) {
            current.chain(next);
            current = next;
        }
        return first;
    }

    // ======================== 包内方法 ========================

    /**
     * 注册一个补间到引擎（由 Tween.start() 自动调用）。
     */
    void register(Tween tween) {
        if (!enabled) {
            tween.snapTo(tween.getValue());
            tween.finish();
            return;
        }
        pendingAdd.add(tween);
    }

    // ======================== 并行补间组合 ========================

    /**
     * 并行动画组——管理一组同时运行的补间。
     * <p>所有子补间同时启动，当所有子补间完成后触发 onComplete。</p>
     */
    public static final class ParallelTween {

        private final List<Tween> children;
        private Runnable onComplete;

        ParallelTween(AnimationEngine engine, Tween... tweens) {
            this.children = new ArrayList<>(List.of(tweens));
            // 注册所有子补间到引擎
            for (Tween t : tweens) {
                engine.register(t);
            }
        }

        /** 启动所有子补间。 */
        public ParallelTween start() {
            for (Tween t : children) {
                t.start();
            }
            return this;
        }

        /** 全部完成时的回调。 */
        public ParallelTween onComplete(Runnable callback) {
            Runnable wrapped = () -> {
                // 检查所有子补间是否都已结束
                boolean allFinished = true;
                for (Tween t : children) {
                    if (!t.isFinished()) {
                        allFinished = false;
                        break;
                    }
                }
                if (allFinished && callback != null) {
                    callback.run();
                }
            };
            for (Tween t : children) {
                t.onComplete(wrapped);
            }
            return this;
        }
    }
}
