package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 形状虚影预览数据。
 * <p>
 * 用于在世界上渲染建造形状的预览方块，
 * 包含预览方块位置列表和是否处于"确认放置"状态。
 */
public record ShapeGhostPreview(List<BlockPos> blocks, boolean readyConfirm) {
    /** 空预览常量 */
    public static final ShapeGhostPreview EMPTY = new ShapeGhostPreview(List.of(), false);
}
