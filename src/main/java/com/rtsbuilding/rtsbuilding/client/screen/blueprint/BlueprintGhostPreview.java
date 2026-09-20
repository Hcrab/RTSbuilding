package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * 蓝图预览的唯一数据模型。
 *
 * <p>局部几何在移动锚点时保持同一身份，世界坐标只通过只读惰性视图平移，
 * 从而不会因鼠标移动重新分配整栋建筑的坐标列表。</p>
 */
public record BlueprintGhostPreview(
        List<BlueprintGhostBlock> blocks,
        boolean materialsReady,
        boolean truncated,
        List<BlueprintGhostBlock> localBlocks,
        BlockPos anchor,
        AABB localBounds) {
    public static final BlueprintGhostPreview EMPTY =
            new BlueprintGhostPreview(List.of(), false, false);

    /** 兼容旧调用方：传入列表被视为局部坐标，锚点默认为原点。 */
    public BlueprintGhostPreview(List<BlueprintGhostBlock> blocks,
            boolean materialsReady, boolean truncated) {
        this(blocks, materialsReady, truncated, blocks, BlockPos.ZERO, boundsOf(blocks));
    }

    static BlueprintGhostPreview at(List<BlueprintGhostBlock> localBlocks, BlockPos anchor,
            AABB bounds, boolean materialsReady, boolean truncated) {
        BlockPos fixedAnchor = anchor == null ? BlockPos.ZERO : anchor.immutable();
        List<BlueprintGhostBlock> local = localBlocks == null ? List.of() : localBlocks;
        return new BlueprintGhostPreview(new TranslatedBlocks(local, fixedAnchor),
                materialsReady, truncated, local, fixedAnchor, bounds);
    }

    private static AABB boundsOf(List<BlueprintGhostBlock> blocks) {
        if (blocks == null) return null;
        AABB bounds = null;
        for (BlueprintGhostBlock block : blocks) {
            if (block == null || block.pos() == null) continue;
            AABB cell = new AABB(block.pos());
            bounds = bounds == null ? cell : bounds.minmax(cell);
        }
        return bounds;
    }

    private static final class TranslatedBlocks extends AbstractList<BlueprintGhostBlock>
            implements RandomAccess {
        private final List<BlueprintGhostBlock> local;
        private final BlockPos anchor;

        private TranslatedBlocks(List<BlueprintGhostBlock> local, BlockPos anchor) {
            this.local = local;
            this.anchor = anchor;
        }

        @Override
        public BlueprintGhostBlock get(int index) {
            BlueprintGhostBlock block = local.get(index);
            return new BlueprintGhostBlock(block.pos().offset(anchor), block.state(), block.missing());
        }

        @Override
        public int size() {
            return local.size();
        }
    }
}
