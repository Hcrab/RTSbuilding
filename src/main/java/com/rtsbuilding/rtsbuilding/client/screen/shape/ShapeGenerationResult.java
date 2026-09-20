package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 一次完整形状计划的不可变值结果。预览、材料统计、放置确认和缓存都消费此结果。
 */
public record ShapeGenerationResult(
        ShapeGenerationStatus status,
        List<BlockPos> positions,
        long discoveredTargets) {
    public ShapeGenerationResult {
        status = status == null ? ShapeGenerationStatus.EMPTY : status;
        positions = List.copyOf(positions == null ? List.of() : positions);
        discoveredTargets = Math.max(discoveredTargets, positions.size());
    }

    public boolean ready() {
        return status == ShapeGenerationStatus.READY && !positions.isEmpty();
    }
}
