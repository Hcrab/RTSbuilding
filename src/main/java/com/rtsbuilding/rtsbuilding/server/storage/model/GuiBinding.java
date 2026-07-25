package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 玩家自定义的外部 GUI 快捷绑定。
 *
 * <p>存储一个目标方块和显示元数据，允许从 RTS 模式一键打开容器的 GUI。
 * @param pos       目标方块坐标
 * @param dimension 目标方块所在维度
 * @param label     玩家自定义的显示标签
 * @param itemId    用于图标的物品 ID
 * @param face      与方块交互的朝向
 */
public record GuiBinding(BlockPos pos, ResourceKey<Level> dimension, String label, String itemId, Direction face) {
}
