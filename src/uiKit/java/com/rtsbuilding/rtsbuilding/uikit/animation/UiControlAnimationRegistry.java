package com.rtsbuilding.rtsbuilding.uikit.animation;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 为一组由稳定 ID 标识的控件保存有界动画状态。
 *
 * <p>它只拥有视觉插值，不拥有按钮命中、业务 Action 或可见布局。调用方必须给出
 * 稳定且有上限的 ID；达到容量时淘汰最久未使用的视觉状态，避免动态列表在长时间
 * 游戏中积累，同时绝不因为纯视觉缓存让客户端崩溃。窗口关闭或数据源整体替换时应
 * 调用 {@link #clear()}。</p>
 */
public final class UiControlAnimationRegistry<K> {
    private final UiClock clock;
    private final int maximumEntries;
    private final Map<K, UiControlAnimationState> animations =
            new LinkedHashMap<K, UiControlAnimationState>(16, 0.75F, true);

    public UiControlAnimationRegistry(UiClock clock, int maximumEntries) {
        if (clock == null || maximumEntries <= 0) {
            throw new IllegalArgumentException("clock and maximumEntries must be valid");
        }
        this.clock = clock;
        this.maximumEntries = maximumEntries;
    }

    public UiControlAnimationState.Snapshot update(
            K stableId,
            UiControlState state,
            boolean animationsEnabled) {
        if (stableId == null || state == null) {
            throw new IllegalArgumentException("stableId and state must not be null");
        }
        UiControlAnimationState animation = animations.get(stableId);
        if (animation == null) {
            if (animations.size() >= maximumEntries) {
                K eldest = animations.keySet().iterator().next();
                animations.remove(eldest);
            }
            animation = new UiControlAnimationState(clock);
            animations.put(stableId, animation);
        }
        return animation.update(state, animationsEnabled);
    }

    public int size() {
        return animations.size();
    }

    public boolean contains(K stableId) {
        return animations.containsKey(stableId);
    }

    public void clear() {
        animations.clear();
    }
}
