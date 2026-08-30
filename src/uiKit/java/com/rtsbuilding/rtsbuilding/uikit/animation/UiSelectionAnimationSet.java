package com.rtsbuilding.rtsbuilding.uikit.animation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为一组预先声明的稳定控件 ID 保存有界选中动画。
 *
 * <p>它只计算 0..1 的视觉强度，不拥有命中矩形、业务状态或 Action。ID 集合在构造时
 * 固定，渲染期间不会因临时列表扩张；动态贡献若消失，应由上层重新建立对应的有界集合。</p>
 */
public final class UiSelectionAnimationSet<K> {
    private final Map<K, UiFloatAnimation> animations = new LinkedHashMap<>();
    private final Map<K, Boolean> targets = new LinkedHashMap<>();
    private final long activationDurationMillis;
    private final long deactivationDurationMillis;
    private final UiEasing easing;

    public UiSelectionAnimationSet(UiClock clock, Collection<K> stableIds,
                                   long durationMillis, UiEasing easing) {
        this(clock, stableIds, durationMillis, durationMillis, easing);
    }

    /**
     * 分别声明进入与退出时长。退出设为 0 可用于模式互斥按钮：旧模式立即退回普通态，
     * 新模式只对自己的边框做短暂进入插值，从而避免两枚按钮同时残留选中光晕。
     */
    public UiSelectionAnimationSet(UiClock clock, Collection<K> stableIds,
                                   long activationDurationMillis, long deactivationDurationMillis,
                                   UiEasing easing) {
        if (clock == null || stableIds == null || stableIds.isEmpty()
                || activationDurationMillis < 0L || deactivationDurationMillis < 0L
                || easing == null) {
            throw new IllegalArgumentException("animation set arguments must be valid");
        }
        for (K id : stableIds) {
            if (id == null || animations.containsKey(id)) {
                throw new IllegalArgumentException("stable ids must be non-null and unique");
            }
            animations.put(id, new UiFloatAnimation(clock, 0.0D));
            targets.put(id, false);
        }
        this.activationDurationMillis = activationDurationMillis;
        this.deactivationDurationMillis = deactivationDurationMillis;
        this.easing = easing;
    }

    /** 更新目标并返回当前视觉强度；业务选中值仍由调用方立即使用。 */
    public double value(K id, boolean selected, boolean animationsEnabled) {
        UiFloatAnimation animation = animations.get(id);
        if (animation == null) {
            throw new IllegalArgumentException("unknown stable control id: " + id);
        }
        boolean previous = targets.get(id);
        if (previous != selected) {
            targets.put(id, selected);
            animation.animateTo(selected ? 1.0D : 0.0D,
                    animationsEnabled
                            ? (selected ? activationDurationMillis : deactivationDurationMillis)
                            : 0L,
                    easing);
        } else if (!animationsEnabled && animation.value() != (selected ? 1.0D : 0.0D)) {
            animation.animateTo(selected ? 1.0D : 0.0D, 0L, UiEasing.LINEAR);
        }
        return animation.value();
    }

    public int size() {
        return animations.size();
    }
}
