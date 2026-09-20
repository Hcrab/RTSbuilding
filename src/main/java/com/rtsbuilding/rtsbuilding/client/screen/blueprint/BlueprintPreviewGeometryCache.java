package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.AbstractList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

/**
 * 放置会话专用的单条目几何缓存，不读取客户端、配置或网络。
 * 内容边界只在蓝图身份/显示上限变化时扫描；旋转只变换边界八个角，方块状态按调色板惰性旋转。
 * 渲染器分批取局部方块，因此选择大型蓝图不会因鼠标移动重新分配全量世界坐标。
 */
final class BlueprintPreviewGeometryCache {
    private RtsBlueprint source;
    private AABB sourceContentBounds;
    private AABB sourcePreviewBounds;
    private int limit;
    private int y = -1, x = -1, z = -1;
    private List<BlueprintGhostBlock> localBlocks = List.of();
    private AABB localBounds;
    private BlockPos cursorOffset = BlockPos.ZERO;
    private BlueprintGhostPreview preview;

    void clear() {
        source = null;
        localBlocks = List.of();
        preview = null;
        sourceContentBounds = sourcePreviewBounds = localBounds = null;
        y = x = z = -1;
    }

    void prepare(RtsBlueprint blueprint, int requestedY, int requestedX, int requestedZ,
            int requestedLimit) {
        if (blueprint == null) {
            clear();
            return;
        }
        int safeLimit = Math.max(1, requestedLimit);
        if (source != blueprint || limit != safeLimit) {
            source = blueprint;
            limit = safeLimit;
            BoundsAccumulator contentBounds = new BoundsAccumulator();
            BoundsAccumulator previewBounds = new BoundsAccumulator();
            for (int i = 0; i < blueprint.blocks().size(); i++) {
                RtsBlueprintBlock block = blueprint.blocks().get(i);
                if (block == null) continue;
                if (i < safeLimit) previewBounds.include(block.relativePos());
                if (block.isMissingBlock()
                        || (block.state() != null && !block.state().isAir())) {
                    contentBounds.include(block.relativePos());
                }
            }
            sourceContentBounds = contentBounds.build();
            sourcePreviewBounds = previewBounds.build();
            y = x = z = -1;
        }
        int nextY = BlueprintTransform.normalizeSteps(requestedY);
        int nextX = BlueprintTransform.normalizeSteps(requestedX);
        int nextZ = BlueprintTransform.normalizeSteps(requestedZ);
        if (y == nextY && x == nextX && z == nextZ) return;

        y = nextY;
        x = nextX;
        z = nextZ;
        BlockPos center = BlueprintTransform.centerRotationOffset(source.size(), y, x, z);
        localBounds = rotateBounds(sourcePreviewBounds, y, x, z, center);
        AABB content = rotateBounds(sourceContentBounds, y, x, z, center);
        cursorOffset = content == null ? BlockPos.ZERO : new BlockPos(
                -(int) (content.minX + ((int) (content.maxX - content.minX - 1) / 2)),
                -(int) content.minY,
                -(int) (content.minZ + ((int) (content.maxZ - content.minZ - 1) / 2)));
        localBlocks = new RotatedBlocks(source.blocks(),
                Math.min(limit, source.blockCount()), y, x, z, center);
        preview = null;
    }

    BlockPos anchorForCursorTarget(BlockPos target) {
        return target == null ? null : target.offset(cursorOffset);
    }

    BlueprintGhostPreview preview(BlockPos anchor, boolean materialsReady) {
        if (preview == null || !preview.anchor().equals(anchor)
                || preview.materialsReady() != materialsReady) {
            preview = BlueprintGhostPreview.at(localBlocks, anchor, localBounds,
                    materialsReady, source != null && source.blockCount() > localBlocks.size());
        }
        return preview;
    }

    private static AABB union(AABB bounds, AABB cell) {
        return bounds == null ? cell : bounds.minmax(cell);
    }

    private static final class BoundsAccumulator {
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void include(BlockPos pos) {
            if (pos == null) return;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        AABB build() {
            return minX == Integer.MAX_VALUE ? null
                    : new AABB(minX, minY, minZ, maxX + 1D, maxY + 1D, maxZ + 1D);
        }
    }

    /** 直角旋转为轴置换，八角边界足以覆盖所有方块坐标。 */
    private static AABB rotateBounds(AABB bounds, int y, int x, int z, BlockPos center) {
        if (bounds == null) return null;
        AABB rotated = null;
        for (int corner = 0; corner < 8; corner++) {
            BlockPos pos = new BlockPos(
                    (int) ((corner & 1) == 0 ? bounds.minX : bounds.maxX - 1),
                    (int) ((corner & 2) == 0 ? bounds.minY : bounds.maxY - 1),
                    (int) ((corner & 4) == 0 ? bounds.minZ : bounds.maxZ - 1));
            rotated = union(rotated,
                    new AABB(BlueprintTransform.rotateAroundCenter(pos, y, x, z, center)));
        }
        return rotated;
    }

    private static final class RotatedBlocks extends AbstractList<BlueprintGhostBlock>
            implements RandomAccess {
        private final List<RtsBlueprintBlock> source;
        private final int size, y, x, z;
        private final BlockPos center;
        private final Map<BlockState, BlockState> states = new IdentityHashMap<>();

        private RotatedBlocks(List<RtsBlueprintBlock> source, int size,
                int y, int x, int z, BlockPos center) {
            this.source = source;
            this.size = size;
            this.y = y;
            this.x = x;
            this.z = z;
            this.center = center;
        }

        @Override
        public BlueprintGhostBlock get(int index) {
            java.util.Objects.checkIndex(index, size);
            RtsBlueprintBlock block = source.get(index);
            BlockState state = block.isMissingBlock() ? Blocks.AIR.defaultBlockState()
                    : states.computeIfAbsent(block.state(),
                            value -> BlueprintTransform.rotateState(value, y, x, z));
            return new BlueprintGhostBlock(
                    BlueprintTransform.rotateAroundCenter(block.relativePos(), y, x, z, center),
                    state, block.isMissingBlock());
        }

        @Override
        public int size() {
            return size;
        }
    }
}
