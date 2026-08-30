package com.rtsbuilding.rtsbuilding.client.screen.shape;

/**
 * 范围建造与范围破坏在一次选点后是否应立即提交的纯策略。
 *
 * <p>它只解释“键盘最终确认”设置，不负责读取配置、生成方块或发送网络请求。
 * 当设置关闭，并且这次选点已经把会话推进到 {@link ShapeBuildTypes.Phase#READY_CONFIRM}
 * 时，应当使用同一次点击完成提交；不能再暗中要求第三次点击。</p>
 */
public final class ShapeConfirmationPolicy {
    private ShapeConfirmationPolicy() {
    }

    public static boolean shouldSubmitAfterSelection(
            boolean keyboardFinalConfirmEnabled,
            ShapeBuildTypes.Phase phase) {
        return !keyboardFinalConfirmEnabled
                && phase == ShapeBuildTypes.Phase.READY_CONFIRM;
    }
}
