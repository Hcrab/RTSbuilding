package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import java.util.List;
import java.util.AbstractList;
import java.util.RandomAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * 蓝图世界虚影的唯一数据模型。
 *
 * <p>局部几何在移动锚点时保持同一身份，世界坐标列表为只读平移视图；不读取面板选择、
 * 不裁剪 RTS 边界，也不执行渲染。生产屏幕与世界渲染器直接共享本记录，避免每帧在两种
 * 同名 Preview 之间重复包装。</p>
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

    /** 保留独立世界坐标快照的调用方式；缓存生成器使用下面的局部几何入口。 */
    public BlueprintGhostPreview(List<BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
        this(blocks, materialsReady, truncated, blocks, BlockPos.ZERO, boundsOf(blocks));
    }

    static BlueprintGhostPreview at(List<BlueprintGhostBlock> localBlocks, BlockPos anchor,
            AABB bounds, boolean materialsReady, boolean truncated) {
        BlockPos fixedAnchor = anchor.immutable();
        return new BlueprintGhostPreview(new TranslatedBlocks(localBlocks, fixedAnchor),
                materialsReady, truncated, localBlocks, fixedAnchor, bounds);
    }

    private static AABB boundsOf(List<BlueprintGhostBlock> blocks) {
        AABB bounds = null;
        for (BlueprintGhostBlock block : blocks) {
            AABB cell = new AABB(block.pos());
            bounds = bounds == null ? cell : bounds.minmax(cell);
        }
        return bounds;
    }

    /** 惰性平移保留 blocks() 的世界坐标语义，不在鼠标移动时分配整栋建筑。 */
    private static final class TranslatedBlocks extends AbstractList<BlueprintGhostBlock> implements RandomAccess {
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
