package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Quick Build 形状预览的总入口。
 *
 * <p>这个类只负责根据预览状态分发到具体 renderer：建造预览、
 * 未确认破坏预览、连锁破坏预览，以及确认后的合并骨架。确认后的骨架缓存、
 * 逐块侵蚀和 no-depth 兜底线统一由 {@link MergedSkeletonRenderer} 拥有。
 */
public final class ShapeGhostRenderer {
    private static final double BOUNDARY_PADDING = 0.02D;

    private static float smoothedDestroyProgress;
    private static long smoothedDestroyProgressMs;
    private static int smoothedDestroyProgressKey;

    private ShapeGhostRenderer() {
    }

    public static void renderShapeGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            return;
        }

        boolean sawConfirmedWorkArea = false;
        for (ShapeDataRecords.GhostPreview preview : screen.getConfirmedRangeDestroyPreviews()) {
            sawConfirmedWorkArea |= isConfirmedDestructiveWorkArea(preview);
            renderGhostPreview(minecraft, preview, poseStack, lineBuffer, fillBuffer);
        }
        ShapeDataRecords.GhostPreview currentPreview = screen.getShapeGhostPreview();
        if (!(sawConfirmedWorkArea && isUnconfirmedDestructivePreview(currentPreview))) {
            sawConfirmedWorkArea |= isConfirmedDestructiveWorkArea(currentPreview);
            renderGhostPreview(minecraft, currentPreview, poseStack, lineBuffer, fillBuffer);
        }
        if (!sawConfirmedWorkArea) {
            MergedSkeletonRenderer.clearCache();
        }
    }

    public static void markDestroyed(BlockPos pos) {
        MergedSkeletonRenderer.markDestroyed(pos);
    }

    public static void clearDeferredNoDepthPasses() {
        UltimineGhostRenderer.clearDeferredNoDepthPasses();
    }

    public static void flushDeferredNoDepthPasses() {
        UltimineGhostRenderer.flushDeferredNoDepthPasses();
    }

    private static void renderGhostPreview(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (preview == null) {
            return;
        }
        preview = clampPreviewToBounds(preview);
        if (preview == null) {
            return;
        }
        if (!preview.chainDestroyPreview() && isEmpty(preview.blocks()) && isEmpty(preview.emptyBlocks())) {
            return;
        }

        if (preview.destructive() && preview.confirmedWorkArea()) {
            float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
            if (preview.chainDestroyPreview()) {
                MergedSkeletonRenderer.renderConfirmedDestroyWorkArea(preview, poseStack, lineBuffer, fillBuffer,
                        progress);
            } else if (Config.isRangeDestroySkeletonEnabled()) {
                MergedSkeletonRenderer.renderConfirmedRangeDestroy(preview, poseStack, lineBuffer, fillBuffer,
                        progress);
            } else {
                DestructiveGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer, progress, 1.0F);
            }
            return;
        }

        if (preview.chainDestroyPreview()) {
            float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
            UltimineGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer, progress);
            return;
        }

        if (preview.destructive()) {
            DestructiveGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer, 0.0F, 1.0F);
            return;
        }

        BuildGhostRenderer.render(minecraft, preview, poseStack, lineBuffer, fillBuffer);
    }

    static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        float alpha = clamp01(alphaMultiplier);
        if (alpha <= 0.0F) {
            return;
        }

        if (!isEmpty(preview.blocks()) || !isEmpty(preview.emptyBlocks())) {
            float envLineR = lerp(1.00F, 0.38F, progress);
            float envLineG = lerp(0.86F, 1.00F, progress);
            float envLineB = lerp(0.22F, 0.42F, progress);
            float envFillR = lerp(1.00F, 0.30F, progress);
            float envFillG = lerp(0.86F, 0.95F, progress);
            float envFillB = lerp(0.18F, 0.36F, progress);
            renderGhostEnvelope(
                    poseStack, lineBuffer, fillBuffer,
                    preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, 0.78F * alpha,
                    envFillR, envFillG, envFillB, 0.10F * alpha);
        }

        DestructiveCellColors colors = DestructiveCellColors.forConfirmState(preview.readyConfirm());
        float lineR = lerp(colors.lineR(), 0.38F, progress);
        float lineG = lerp(colors.lineG(), 1.00F, progress);
        float lineB = lerp(colors.lineB(), 0.42F, progress);
        float fillR = lerp(colors.fillR(), 0.30F, progress);
        float fillG = lerp(colors.fillG(), 0.95F, progress);
        float fillB = lerp(colors.fillB(), 0.36F, progress);

        for (BlockPos pos : preview.blocks()) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, colors.fillA() * alpha);
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    lineR, lineG, lineB, colors.lineA() * alpha);
        }
    }

    private static boolean isConfirmedDestructiveWorkArea(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.destructive() && preview.confirmedWorkArea();
    }

    private static boolean isUnconfirmedDestructivePreview(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.destructive() && !preview.confirmedWorkArea();
    }

    private static void renderGhostEnvelope(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        Bounds bounds = Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) {
            return;
        }
        double minX = bounds.minX() - BOUNDARY_PADDING;
        double minY = bounds.minY() - BOUNDARY_PADDING;
        double minZ = bounds.minZ() - BOUNDARY_PADDING;
        double maxX = bounds.maxX() + 1.0D + BOUNDARY_PADDING;
        double maxY = bounds.maxY() + 1.0D + BOUNDARY_PADDING;
        double maxZ = bounds.maxZ() + 1.0D + BOUNDARY_PADDING;

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                fillR, fillG, fillB, fillA);
        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                lineR, lineG, lineB, lineA);
    }

    private static float smoothedDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        int key = previewKey(preview);
        float target = rawDestroyProgress(controller, preview);
        long now = System.currentTimeMillis();
        if (key != smoothedDestroyProgressKey || smoothedDestroyProgressMs <= 0L) {
            smoothedDestroyProgressKey = key;
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return clamp01(smoothedDestroyProgress);
        }
        float deltaSeconds = Math.min(0.10F, Math.max(0.0F, (now - smoothedDestroyProgressMs) / 1000.0F));
        smoothedDestroyProgressMs = now;
        float speed = target > smoothedDestroyProgress ? 4.5F : 1.8F;
        float maxDelta = speed * deltaSeconds;
        if (Math.abs(target - smoothedDestroyProgress) <= maxDelta) {
            smoothedDestroyProgress = target;
        } else {
            smoothedDestroyProgress += Math.signum(target - smoothedDestroyProgress) * maxDelta;
        }
        if (target <= 0.0F && smoothedDestroyProgress < 0.01F) {
            smoothedDestroyProgress = 0.0F;
        }
        return clamp01(smoothedDestroyProgress);
    }

    private static float rawDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null) {
            return 0.0F;
        }
        float miningStageProgress = miningStageProgress(controller, preview);
        if (preview != null && preview.chainDestroyPreview() && miningStageProgress > 0.0F) {
            return miningStageProgress;
        }
        RtsWorkflowStatus workflow = controller.findActiveDestroyWorkflow();
        if (workflow != null && workflow.totalBlocks() > 0) {
            return clamp01((float) workflow.completedBlocks() / (float) workflow.totalBlocks());
        }
        return miningStageProgress;
    }

    private static float miningStageProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null || preview == null) {
            return 0.0F;
        }
        BlockPos progressPos = controller.getMineProgressPos();
        if (progressPos == null || !previewContains(preview, progressPos)) {
            return 0.0F;
        }
        int stage = controller.getMineProgressStage();
        if (stage < 0) {
            return 0.0F;
        }
        return clamp01((Math.min(9, stage) + 1) / 10.0F);
    }
    private static boolean previewContains(ShapeDataRecords.GhostPreview preview, BlockPos pos) {
        return preview != null && pos != null && (contains(preview.blocks(), pos) || contains(preview.emptyBlocks(), pos));
    }

    private static boolean contains(List<BlockPos> blocks, BlockPos pos) {
        if (blocks == null || pos == null) {
            return false;
        }
        for (BlockPos block : blocks) {
            if (pos.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static int previewKey(ShapeDataRecords.GhostPreview preview) {
        Bounds bounds = preview == null ? null : Bounds.from(preview.blocks(), preview.emptyBlocks());
        if (bounds == null) {
            return 0;
        }
        int result = 17;
        result = 31 * result + bounds.minX();
        result = 31 * result + bounds.minY();
        result = 31 * result + bounds.minZ();
        result = 31 * result + bounds.maxX();
        result = 31 * result + bounds.maxY();
        result = 31 * result + bounds.maxZ();
        result = 31 * result + (preview.chainDestroyPreview() ? 1 : 0);
        result = 31 * result + (preview.confirmedWorkArea() ? 1 : 0);
        return result;
    }

    private static ShapeDataRecords.GhostPreview clampPreviewToBounds(ShapeDataRecords.GhostPreview preview) {
        if (preview == null) {
            return null;
        }
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return preview;
        }
        List<BlockPos> filteredBlocks = RenderingUtil.filterBlocksWithinBounds(
                preview.blocks(), controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
        List<BlockPos> filteredEmptyBlocks = RenderingUtil.filterBlocksWithinBounds(
                preview.emptyBlocks(), controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
        if (filteredBlocks == preview.blocks() && filteredEmptyBlocks == preview.emptyBlocks()) {
            return preview;
        }
        if (isEmpty(filteredBlocks) && isEmpty(filteredEmptyBlocks)) {
            return null;
        }
        return new ShapeDataRecords.GhostPreview(
                filteredBlocks,
                preview.readyConfirm(),
                preview.destructive(),
                filteredEmptyBlocks,
                preview.chainDestroyPreview(),
                preview.confirmedWorkArea());
    }

    private static boolean isEmpty(List<BlockPos> blocks) {
        return blocks == null || blocks.isEmpty();
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
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
            if (blocks == null) {
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
            return this.hasAny ? new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ) : null;
        }
    }

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
                    readyConfirm ? 0.22F : 0.07F);
        }
    }
}
