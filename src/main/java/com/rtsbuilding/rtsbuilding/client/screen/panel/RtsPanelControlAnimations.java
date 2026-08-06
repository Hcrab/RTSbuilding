package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;

/**
 * 浮动窗口内手绘控件的有界动画仓库。
 *
 * <p>它只把稳定控件 id 与通用交互状态转换为动画快照，不拥有业务值、命中区域、
 * 输入事件或绘制。窗口关闭后由所有者显式清空，避免动态行长期积累。</p>
 */
final class RtsPanelControlAnimations {
    private final UiControlAnimationRegistry<String> registry =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 256);

    UiControlAnimationState.Snapshot update(
            String stableId,
            boolean enabled,
            boolean hovered,
            boolean selected,
            boolean interactionSuppressed,
            boolean animationsEnabled) {
        return this.registry.update(
                stableId,
                new UiControlState(
                        true, enabled,
                        enabled && !interactionSuppressed && hovered,
                        false, false,
                        selected, false, false, enabled ? "" : "disabled"),
                animationsEnabled);
    }

    void clear() {
        this.registry.clear();
    }
}
