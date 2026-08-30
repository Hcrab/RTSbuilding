package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;

/** 世界目标高亮的可见 UI 遮挡规则；不执行射线或绘制。 */
final class InteractionTargetOcclusionPolicy {
    static boolean shapeSelectionBlocks(
            boolean quickBuildOpen,
            boolean rangeDestroyMode,
            ShapeBuildTypes.Phase phase) {
        return quickBuildOpen
                && rangeDestroyMode
                && phase == ShapeBuildTypes.Phase.READY_CONFIRM;
    }

    private InteractionTargetOcclusionPolicy() {
    }
}
