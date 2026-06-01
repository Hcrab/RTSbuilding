package com.rtsbuilding.rtsbuilding.client.screen.shape;

/**
 * 形状构建阶段枚举。
 * <p>
 * 表示用户构建形状时的交互阶段：
 * <ul>
 *   <li>{@link #NEED_SECOND_POINT} - 已点击第一个锚点，等待第二个锚点</li>
 *   <li>{@link #NEED_THIRD_POINT} - 已点击第二个锚点，等待第三个锚点（仅立方体需要高度）</li>
 *   <li>{@link #READY_CONFIRM} - 所有锚点已确定，等待用户确认放置</li>
 * </ul>
 */
public enum ShapeBuildPhase {
    NEED_SECOND_POINT,
    NEED_THIRD_POINT,
    READY_CONFIRM
}
