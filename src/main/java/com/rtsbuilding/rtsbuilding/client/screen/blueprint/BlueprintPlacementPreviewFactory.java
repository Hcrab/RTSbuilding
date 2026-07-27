package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprintBlock;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成蓝图放置虚影和“鼠标抓住建筑底部中心”所需的纯几何结果。
 *
 * <p>本类不读取 BlueprintPanel 的静态选择、配置、Minecraft 实例或网络状态；调用方必须
 * 明确传入条目、旋转、数量上限和材料状态。它也不负责 RTS 边界裁剪或绘制，这些仍属于
 * 后续渲染适配器。这样同一套旋转/锚点算法可被未来版本的薄平台插头直接复用。</p>
 */
final class BlueprintPlacementPreviewFactory {
    private BlueprintPlacementPreviewFactory() {
    }

    static BlockPos anchorForCursorTarget(
            BlueprintEntry entry,
            BlockPos cursorTarget,
            int yRotationSteps,
            int xRotationSteps,
            int zRotationSteps) {
        if (cursorTarget == null || entry == null || !entry.error().trim().isEmpty()) {
            return cursorTarget;
        }
        int y = BlueprintTransform.normalizeSteps(yRotationSteps);
        int x = BlueprintTransform.normalizeSteps(xRotationSteps);
        int z = BlueprintTransform.normalizeSteps(zRotationSteps);
        ContentBounds bounds = transformedContentBounds(
                entry.blueprint(), y, x, z);
        if (bounds == null) {
            return cursorTarget;
        }
        return cursorTarget.add(
                -bounds.centerX(), -bounds.minY(), -bounds.centerZ());
    }

    static BlueprintGhostPreview create(
            BlueprintEntry entry,
            BlockPos anchor,
            int yRotationSteps,
            int xRotationSteps,
            int zRotationSteps,
            int previewLimit,
            boolean materialsReady) {
        if (entry == null || anchor == null || !entry.error().trim().isEmpty()) {
            return BlueprintGhostPreview.EMPTY;
        }
        int safeLimit = Math.max(1, previewLimit);
        List<BlueprintGhostBlock> blocks =
                new ArrayList<>(Math.min(entry.blockCount(), safeLimit));
        int y = BlueprintTransform.normalizeSteps(yRotationSteps);
        int x = BlueprintTransform.normalizeSteps(xRotationSteps);
        int z = BlueprintTransform.normalizeSteps(zRotationSteps);
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(
                entry.blueprint().size(), y, x, z);
        for (RtsBlueprintBlock block : entry.blueprint().blocks()) {
            BlockPos pos = anchor.add(BlueprintTransform.rotateAroundCenter(
                    block.relativePos(), y, x, z, centerOffset));
            IBlockState state = block.isMissingBlock()
                    ? Blocks.AIR.getDefaultState()
                    : BlueprintTransform.rotateState(block.state(), y, x, z);
            blocks.add(new BlueprintGhostBlock(
                    pos, state, block.isMissingBlock()));
            if (blocks.size() >= safeLimit) {
                break;
            }
        }
        return new BlueprintGhostPreview(
                java.util.Collections.unmodifiableList(new ArrayList<BlueprintGhostBlock>(blocks)),
                materialsReady,
                entry.blockCount() > blocks.size());
    }

    private static ContentBounds transformedContentBounds(
            RtsBlueprint blueprint, int y, int x, int z) {
        if (blueprint == null || blueprint.blocks().isEmpty()) {
            return null;
        }
        BlockPos centerOffset = BlueprintTransform.centerRotationOffset(
                blueprint.size(), y, x, z);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;
        for (RtsBlueprintBlock block : blueprint.blocks()) {
            if (block == null
                    || (!block.isMissingBlock()
                    && (block.state() == null || block.state().getBlock() == Blocks.AIR))) {
                continue;
            }
            BlockPos pos = BlueprintTransform.rotateAroundCenter(
                    block.relativePos(), y, x, z, centerOffset);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            found = true;
        }
        return found
                ? new ContentBounds(minX, minY, minZ, maxX, maxY, maxZ)
                : null;
    }

    private static final class ContentBounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private ContentBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        int minY() { return minY; }
        int centerX() {
            return this.minX + ((this.maxX - this.minX) / 2);
        }

        int centerZ() {
            return this.minZ + ((this.maxZ - this.minZ) / 2);
        }
    }
}
