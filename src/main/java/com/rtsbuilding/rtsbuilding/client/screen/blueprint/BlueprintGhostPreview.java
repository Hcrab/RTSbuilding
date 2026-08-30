package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import java.util.List;

/**
 * 蓝图世界虚影的唯一数据模型。
 *
 * <p>它只保存已经生成的方块快照、材料是否齐备以及是否因上限截断，不读取面板选择、
 * 不裁剪 RTS 边界，也不执行渲染。生产屏幕与世界渲染器直接共享本记录，避免每帧在两种
 * 同名 Preview 之间重复包装。</p>
 */
public record BlueprintGhostPreview(
        List<BlueprintGhostBlock> blocks,
        boolean materialsReady,
        boolean truncated) {
    public static final BlueprintGhostPreview EMPTY =
            new BlueprintGhostPreview(List.of(), false, false);
}
