package com.rtsbuilding.rtsbuilding.client.screen.interaction;

/**
 * 放置重放类型枚举。
 * <p>
 * 指示形状历史记录中放置操作的来源类型：
 * <ul>
 *   <li>{@link #PIN_ITEM} - 从快捷栏 Pin 物品放置</li>
 *   <li>{@link #TOOL_SLOT} - 从工具槽物品放置</li>
 * </ul>
 */
public enum PlacementReplayKind {
    PIN_ITEM,
    TOOL_SLOT
}
