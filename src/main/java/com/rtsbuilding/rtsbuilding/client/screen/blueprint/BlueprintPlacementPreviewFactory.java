package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.core.BlockPos;

/**
 * 蓝图放置预览的纯生成入口。缓存由放置会话持有，本类不读取配置、世界或网络。
 * 带缓存与单次调用共用同一套旋转/抓取算法，避免两条入口在坐标语义上分叉。
 */
final class BlueprintPlacementPreviewFactory {
    private BlueprintPlacementPreviewFactory() { }

    static BlockPos anchorForCursorTarget(BlueprintPreviewGeometryCache cache, BlueprintEntry entry,
            BlockPos target, int y, int x, int z, int limit) {
        if (target == null || entry == null || !entry.error().isBlank()) return target;
        cache.prepare(entry.blueprint(), y, x, z, limit);
        return cache.anchorForCursorTarget(target);
    }

    static BlueprintGhostPreview create(BlueprintPreviewGeometryCache cache, BlueprintEntry entry,
            BlockPos anchor, int y, int x, int z, int limit, boolean materialsReady) {
        if (anchor == null || entry == null || !entry.error().isBlank()) return BlueprintGhostPreview.EMPTY;
        cache.prepare(entry.blueprint(), y, x, z, limit);
        return cache.preview(anchor, materialsReady);
    }

    static BlockPos anchorForCursorTarget(BlueprintEntry entry, BlockPos target, int y, int x, int z) {
        return anchorForCursorTarget(new BlueprintPreviewGeometryCache(), entry, target, y, x, z,
                entry == null ? 1 : entry.blockCount());
    }

    static BlueprintGhostPreview create(BlueprintEntry entry, BlockPos anchor, int y, int x, int z,
            int limit, boolean materialsReady) {
        return create(new BlueprintPreviewGeometryCache(), entry, anchor, y, x, z, limit, materialsReady);
    }
}
