package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 形状建造预览渲染器
 * 负责在BuilderScreen中渲染快速建造形状的幽灵预览（如墙体、地板等）
 * <p>
 * 建造模式下使用实际方块模型以透明材质渲染，破坏模式下使用边界框+方块高亮渲染。
 */
public final class ShapeGhostRenderer {

    /** 建造幽灵透明度（包可见，供同包渲染器使用） */
    static final float BUILD_GHOST_ALPHA = 0.8F;

    /** 包围盒边框外扩距离 */
    private static final double BOUNDARY_PADDING = 0.02D;

    /**
     * 私有构造函数，防止实例化
     */
    private ShapeGhostRenderer() {
    }

    /**
     * 渲染形状建造的幽灵预览
     * <p>
     * 建造模式：尝试使用透明方块模型渲染，若方块状态不可用则回退到彩色方块高亮。
     * 破坏模式：使用边界框+方块高亮渲染（无指定方块可预览）。
     *
     * @param minecraft  Minecraft客户端实例
     * @param poseStack  姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     * @param fillBuffer 填充缓冲区
     */
    public static void renderShapeGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // 仅在BuilderScreen中渲染
        if (!(minecraft.screen instanceof BuilderScreen)) {
            return;
        }

        ShapeDataRecords.GhostPreview preview = ((BuilderScreen) minecraft.screen).getShapeGhostPreview();
        if (preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty()) {
            return;
        }

        if (com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled()) {
            renderWireframePreview(preview, poseStack, lineBuffer);
            return;
        }

        if (preview.destructive()) {
            // 破坏模式：使用边界框+方块高亮（无特定方块可预览）
            renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer);
            return;
        }

        // 建造模式：尝试解析方块状态并使用透明方块模型渲染
        BlockState blockState = resolveBuildBlockState(minecraft);
        if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
            renderBuildGhostModels(minecraft, preview, poseStack, blockState, lineBuffer);
        } else {
            // 无法解析方块状态时回退到彩色方块高亮
            renderBuildGhostFallback(preview, poseStack, lineBuffer, fillBuffer);
        }
    }

    /**
     * 解析当前用于形状建造的方块状态。
     * 优先使用RTS存储中选择的物品，其次回退到玩家主手物品。
     */
    private static BlockState resolveBuildBlockState(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    /**
     * 使用透明方块模型渲染建造幽灵预览。
     * 每个方块渲染实际模型，并覆盖半透明alpha，同时绘制整体包围盒边框。
     */
    private static void renderBuildGhostModels(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, BlockState blockState, VertexConsumer lineBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        // 计算包围盒边界
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }

        // 渲染每个方块的透明模型（使用该位置的实际光照）
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, BUILD_GHOST_ALPHA);

        for (BlockPos pos : blocks) {
            int light = LevelRenderer.getLightColor(minecraft.level, pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(
                    blockState,
                    poseStack,
                    translucentBuffer,
                    light,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        blockBuffer.endBatch();

        // 渲染整体包围盒边框（readyConfirm=true 绿色系，false 青色系）
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX - BOUNDARY_PADDING, minY - BOUNDARY_PADDING, minZ - BOUNDARY_PADDING,
                maxX + BOUNDARY_PADDING, maxY + BOUNDARY_PADDING, maxZ + BOUNDARY_PADDING,
                lineR, lineG, lineB,
                0.95F);
    }

    /**
     * 建造模式回退渲染：当无法解析方块状态时，使用彩色方块高亮。
     */
    private static void renderBuildGhostFallback(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
        float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
        float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
        float fillA = preview.readyConfirm() ? 0.22F : 0.16F;
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;

        for (BlockPos pos : blocks) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fillR, fillG, fillB, fillA);

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    lineR, lineG, lineB,
                    0.95F);
        }
    }

    /**
     * 渲染破坏模式的幽灵预览：边界框 + 方块彩色高亮。
     */
    private static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        boolean readyConfirm = preview.readyConfirm();

        // 边界框渲染（emptyBlocks 区域使用黄色标记不可破坏方块）
        if (!preview.emptyBlocks().isEmpty()) {
            float envLineR = 1.00F, envLineG = 0.86F, envLineB = 0.22F, envLineA = 0.72F;
            float envFillR = 1.00F, envFillG = 0.86F, envFillB = 0.18F, envFillA = 0.18F;
            renderGhostEnvelope(poseStack, lineBuffer, fillBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, envLineA,
                    envFillR, envFillG, envFillB, envFillA);
        }

        // 方块高亮渲染
        DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
        float lineR = dcc.lineR, lineG = dcc.lineG, lineB = dcc.lineB, lineA = dcc.lineA;
        float fillR = dcc.fillR, fillG = dcc.fillG, fillB = dcc.fillB, fillA = dcc.fillA;

        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (BlockPos pos : blocks) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fillR, fillG, fillB, fillA);

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    lineR, lineG, lineB,
                    lineA);
        }
    }

    private static void renderGhostEnvelope(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        Bounds bounds = Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) {
            return;
        }

        double padding = BOUNDARY_PADDING;
        double minX = bounds.minX() - padding;
        double minY = bounds.minY() - padding;
        double minZ = bounds.minZ() - padding;
        double maxX = bounds.maxX() + 1.0D + padding;
        double maxY = bounds.maxY() + 1.0D + padding;
        double maxZ = bounds.maxZ() + 1.0D + padding;

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                fillR, fillG, fillB, fillA);

        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                lineR, lineG, lineB,
                lineA);
    }

    private static void renderWireframePreview(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        boolean destructive = preview.destructive();
        boolean readyConfirm = preview.readyConfirm();

        // Envelope for non-breakable blocks in destruct mode
        if (destructive && !preview.emptyBlocks().isEmpty()) {
            float envLineR = 1.00F, envLineG = 0.86F, envLineB = 0.22F, envLineA = 0.72F;
            renderWireframeEnvelope(poseStack, lineBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, envLineA);
        }

        if (preview.blocks() == null || preview.blocks().isEmpty()) {
            return;
        }

        // Cell line boxes
        for (BlockPos pos : preview.blocks()) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;
            if (destructive) {
                DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        dcc.lineR(), dcc.lineG(), dcc.lineB(), dcc.lineA());
            } else {
                float lineR = readyConfirm ? 0.45F : 0.30F;
                float lineG = readyConfirm ? 0.95F : 0.75F;
                float lineB = readyConfirm ? 0.45F : 1.00F;
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        lineR, lineG, lineB, 0.95F);
            }
        }
    }

    private static void renderWireframeEnvelope(PoseStack poseStack, VertexConsumer lineBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA) {
        Bounds bounds = Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) return;
        double padding = BOUNDARY_PADDING;
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                bounds.minX() - padding, bounds.minY() - padding, bounds.minZ() - padding,
                bounds.maxX() + 1.0D + padding, bounds.maxY() + 1.0D + padding, bounds.maxZ() + 1.0D + padding,
                lineR, lineG, lineB, lineA);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
        }
    }

    /** Cell rendering colors for destructive ghost preview — grouped by confirm state. */
    private record DestructiveCellColors(
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        private static DestructiveCellColors forConfirmState(boolean readyConfirm) {
            return new DestructiveCellColors(
                    1.00F,
                    readyConfirm ? 0.95F : 0.46F,
                    readyConfirm ? 0.45F : 0.64F,
                    readyConfirm ? 0.95F : 0.62F,
                    1.00F,
                    readyConfirm ? 0.72F : 0.25F,
                    readyConfirm ? 0.24F : 0.44F,
                    readyConfirm ? 0.22F : 0.07F
            );
        }
    }

    private static final class MutableBounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private boolean hasAny;

        private void include(List<BlockPos> blocks) {
            if (blocks == null || blocks.isEmpty()) {
                return;
            }
            for (BlockPos pos : blocks) {
                if (pos == null) {
                    continue;
                }
                this.minX = Math.min(this.minX, pos.getX());
                this.minY = Math.min(this.minY, pos.getY());
                this.minZ = Math.min(this.minZ, pos.getZ());
                this.maxX = Math.max(this.maxX, pos.getX());
                this.maxY = Math.max(this.maxY, pos.getY());
                this.maxZ = Math.max(this.maxZ, pos.getZ());
                this.hasAny = true;
            }
        }

        private Bounds toBounds() {
            if (!this.hasAny) {
                return null;
            }
            return new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    /**
     * Applies a fixed alpha multiplier to block model rendering.
     * Routes all render types through the translucent layer with an alpha override.
     */
    record GhostAlphaBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new GhostAlphaVertexConsumer(delegate.getBuffer(RenderType.translucent()), alpha);
        }
    }

    record GhostAlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
