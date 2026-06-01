package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;

import java.util.List;

/**
 * 蓝图虚影预览数据。
 * <p>
 * 用于在世界上渲染蓝图放置的预览方块，
 * 包含预览方块列表、材料是否就绪、是否被截断等信息。
 */
public record BlueprintGhostPreview(List<BlueprintPanel.BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
    /** 空预览常量 */
    public static final BlueprintGhostPreview EMPTY = new BlueprintGhostPreview(List.of(), false, false);
}
