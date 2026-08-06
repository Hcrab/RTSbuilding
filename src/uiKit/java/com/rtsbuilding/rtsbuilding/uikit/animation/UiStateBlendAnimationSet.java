package com.rtsbuilding.rtsbuilding.uikit.animation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为固定控件和固定视觉状态保存有界的交叉淡入权重。
 *
 * <p>本类只负责“旧状态逐渐淡出、新状态逐渐淡入”的连续权重，不拥有业务状态、
 * 命中矩形、纹理或绘制 API。控件 ID 与视觉状态在构造时固定，因此渲染期间不会
 * 因动态键产生无界缓存，也不会为每一帧创建新的动画对象。</p>
 *
 * <p>第一次观察控件时会直接对齐当前状态，避免刚打开界面时从错误默认态闪入。
 * 后续快速反向或连续切换会从每个状态的当前权重继续，不会机械跳回过渡起点。</p>
 */
public final class UiStateBlendAnimationSet<K, S> {
    private final Map<K, Entry<S>> entries = new LinkedHashMap<K, Entry<S>>();
    private final Collection<S> states;
    private final long durationMillis;
    private final UiEasing easing;

    public UiStateBlendAnimationSet(UiClock clock, Collection<K> stableIds,
                                    Collection<S> stableStates, long durationMillis,
                                    UiEasing easing) {
        if (clock == null || stableIds == null || stableIds.isEmpty()
                || stableStates == null || stableStates.size() < 2
                || durationMillis < 0L || easing == null) {
            throw new IllegalArgumentException("blend animation arguments must be valid");
        }
        LinkedHashMap<S, Boolean> uniqueStates = new LinkedHashMap<S, Boolean>();
        for (S state : stableStates) {
            if (state == null || uniqueStates.put(state, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("stable states must be non-null and unique");
            }
        }
        this.states = uniqueStates.keySet();
        for (K id : stableIds) {
            if (id == null || entries.containsKey(id)) {
                throw new IllegalArgumentException("stable ids must be non-null and unique");
            }
            entries.put(id, new Entry<S>(clock, this.states));
        }
        this.durationMillis = durationMillis;
        this.easing = easing;
    }

    /**
     * 更新一个控件的目标视觉状态。
     *
     * <p>业务状态应在调用本方法前已经生效；这里仅启动或重定向视觉权重。</p>
     */
    public void update(K id, S target, boolean animationsEnabled) {
        Entry<S> entry = entry(id);
        if (!entry.animations.containsKey(target)) {
            throw new IllegalArgumentException("unknown stable visual state: " + target);
        }
        if (!entry.observed) {
            entry.snapTo(target);
            return;
        }
        if (!target.equals(entry.target)) {
            entry.target = target;
            entry.animateTo(target, animationsEnabled ? durationMillis : 0L, easing);
        } else if (!animationsEnabled && !entry.isSettled(target)) {
            entry.snapTo(target);
        }
    }

    /** 返回指定状态当前的 0..1 权重。 */
    public double weight(K id, S state) {
        UiFloatAnimation animation = entry(id).animations.get(state);
        if (animation == null) {
            throw new IllegalArgumentException("unknown stable visual state: " + state);
        }
        return animation.value();
    }

    public int controlCount() {
        return entries.size();
    }

    public int stateCount() {
        return states.size();
    }

    private Entry<S> entry(K id) {
        Entry<S> entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("unknown stable control id: " + id);
        }
        return entry;
    }

    private static final class Entry<S> {
        private final Map<S, UiFloatAnimation> animations =
                new LinkedHashMap<S, UiFloatAnimation>();
        private S target;
        private boolean observed;

        private Entry(UiClock clock, Collection<S> states) {
            for (S state : states) {
                animations.put(state, new UiFloatAnimation(clock, 0.0D));
            }
        }

        private void animateTo(S next, long durationMillis, UiEasing easing) {
            for (Map.Entry<S, UiFloatAnimation> animation : animations.entrySet()) {
                animation.getValue().animateTo(
                        animation.getKey().equals(next) ? 1.0D : 0.0D,
                        durationMillis, easing);
            }
        }

        private void snapTo(S next) {
            target = next;
            observed = true;
            for (Map.Entry<S, UiFloatAnimation> animation : animations.entrySet()) {
                animation.getValue().snapTo(
                        animation.getKey().equals(next) ? 1.0D : 0.0D);
            }
        }

        private boolean isSettled(S expected) {
            for (Map.Entry<S, UiFloatAnimation> animation : animations.entrySet()) {
                double targetWeight = animation.getKey().equals(expected) ? 1.0D : 0.0D;
                if (Math.abs(animation.getValue().value() - targetWeight) > 0.000001D) {
                    return false;
                }
            }
            return true;
        }
    }
}
