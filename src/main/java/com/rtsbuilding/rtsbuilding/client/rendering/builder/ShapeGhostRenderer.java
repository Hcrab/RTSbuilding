package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

/**
 * Entry-point facade that coordinates all in-world ghost preview rendering
 * for shape building and destruction modes within the {@link BuilderScreen}.
 *
 * <p>Delegates to specialized renderers based on the
 * {@link ShapeDataRecords.GhostPreview} state:
 * <ul>
 *   <li>{@link BuildGhostRenderer} — translucent block models / fallback outlines for placement</li>
 *   <li>{@link DestructiveGhostRenderer} — per-block coloured outlines + envelope for range destroy</li>
 *   <li>{@link UltimineGhostRenderer} — FTB Ultimine style chain-mining ghost</li>
 *   <li>{@link MergedSkeletonRenderer} — merged outer-perimeter skeleton for confirmed work areas</li>
 * </ul>
 */
public final class ShapeGhostRenderer {

    // ── Smoothed destroy progress state (shared across all rendering modes) ──

    private static float smoothedDestroyProgress;
    private static long smoothedDestroyProgressMs;
    private static int smoothedDestroyProgressKey;
    private static final long DESTROY_COMPLETE_FADE_MS = 180L;
    private static int destroyVisualKey;
    private static int destroyCompletePreviewKey;
    private static long destroyCompleteFadeStartMs;
    private static boolean destroyVisualComplete;

    private ShapeGhostRenderer() {
    }

    private record DestroyVisualState(float progress, float alpha, boolean fading) {
        private boolean hidden() {
            return alpha <= 0.0F;
        }
    }

    // ===== Entry point =====

    /**
     * Entry-point called each frame. Iterates over confirmed range-destroy
     * previews and the current shape ghost preview, dispatching each to
     * {@link #renderGhostPreview}.
     */
    public static void renderShapeGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            return;
        }

        boolean sawConfirmedDestructiveWorkArea = false;
        for (ShapeDataRecords.GhostPreview preview : screen.getConfirmedRangeDestroyPreviews()) {
            sawConfirmedDestructiveWorkArea |= isConfirmedDestructiveWorkArea(preview);
            renderGhostPreview(minecraft, preview, poseStack, lineBuffer, fillBuffer);
        }
        ShapeDataRecords.GhostPreview currentPreview = screen.getShapeGhostPreview();
        sawConfirmedDestructiveWorkArea |= isConfirmedDestructiveWorkArea(currentPreview);
        renderGhostPreview(minecraft, currentPreview, poseStack, lineBuffer, fillBuffer);
        if (!sawConfirmedDestructiveWorkArea) {
            MergedSkeletonRenderer.clearCache();
        }
    }

    /**
     * Records a block as destroyed for incremental skeleton updates.
     * Delegates to {@link MergedSkeletonRenderer#markDestroyed(BlockPos)}.
     */
    public static void markDestroyed(BlockPos pos) {
        MergedSkeletonRenderer.markDestroyed(pos);
    }

    // ===== Ghost preview dispatch =====

    private static void renderGhostPreview(Minecraft minecraft, ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (preview == null) return;

        // 将预览方块裁剪到 RTS 边界范围内，超出边界的方块不渲染
        preview = clampPreviewToBounds(preview);
        if (preview == null) return;

        // Ultimine ghost always tries to render; skip early-exit for ultimine
        if (!preview.chainDestroyPreview() && preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty()) {
            return;
        }

        // ── Confirmed destructive work area ──
        if (preview.destructive() && preview.confirmedWorkArea()) {
            DestroyVisualState visual = destroyVisualState(ClientRtsController.get(), preview);
            if (visual.hidden()) {
                return;
            }
            if (com.rtsbuilding.rtsbuilding.Config.isRangeDestroySkeletonEnabled()) {
                if (preview.chainDestroyPreview()) {
                    if (visual.fading()) {
                        MergedSkeletonRenderer.renderMergedSkeletonSnapshot(preview, poseStack, lineBuffer, fillBuffer,
                                1.0F, 0.30F, 0.035F, visual.alpha());
                    } else {
                        MergedSkeletonRenderer.renderConfirmedDestroyWorkArea(preview, poseStack, lineBuffer,
                                fillBuffer, visual.progress(), visual.alpha());
                    }
                } else {
                    renderConfirmedRangeDestroyWorkArea(preview, poseStack, lineBuffer, fillBuffer, visual);
                }
                return;
            }
            DestructiveGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer,
                    visual.progress(), visual.alpha());
            return;
        }

        // ── Wireframe mode (debug/config toggle) ──
        // ── Ultimine (chain-mining) ghost ──
        if (preview.chainDestroyPreview() && com.rtsbuilding.rtsbuilding.Config.isRangeDestroySkeletonEnabled()) {
            float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
            UltimineGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer, progress);
            return;
        }

        // ── Destructive (range-destroy) ghost ──
        if (preview.destructive()) {
            float progress = preview.confirmedWorkArea() ? smoothedDestroyProgress(ClientRtsController.get(), preview) : 0.0F;
            DestructiveGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer, progress, 1.0F);
            return;
        }

        // ── Build mode (placement ghost) ──
        boolean usePlacementLayerSettings = shouldUsePlacementPreviewSettings(preview);
        BuildGhostRenderer.render(minecraft, preview, poseStack, lineBuffer, fillBuffer,
                usePlacementLayerSettings ? com.rtsbuilding.rtsbuilding.Config.isBlockGhostPreviewEnabled() : true,
                usePlacementLayerSettings ? com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled() : true);
    }

    // ===== Range-destroy confirmed work area handling =====

    private static void renderConfirmedRangeDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, DestroyVisualState visual) {
        ClientRtsController controller = ClientRtsController.get();

        if (visual.fading()) {
            MergedSkeletonRenderer.renderMergedSkeletonSnapshot(preview, poseStack, lineBuffer, fillBuffer,
                    1.0F, 0.30F, 0.030F, visual.alpha());
            return;
        }
        if (hasStartedDestroyBatch(controller, preview)) {
            MergedSkeletonRenderer.renderMergedSkeletonFast(preview, poseStack, lineBuffer, fillBuffer,
                    visual.progress(), 0.30F, 0.030F, visual.alpha());
            return;
        }
        if (MergedSkeletonRenderer.hasCachedSkeleton(preview)) {
            if (MergedSkeletonRenderer.renderCachedSkeleton(preview, poseStack, lineBuffer, fillBuffer,
                    visual.progress(), 0.30F, 0.030F, visual.alpha())) {
                return;
            }
        }
        if (MergedSkeletonRenderer.hasSkeletonCacheForPreview(preview)) {
            return;
        }
        DestructiveGhostRenderer.render(preview, poseStack, lineBuffer, fillBuffer,
                visual.progress(), visual.alpha());
    }

    // ===== Smoothed destroy progress =====

    private static DestroyVisualState destroyVisualState(ClientRtsController controller,
            ShapeDataRecords.GhostPreview preview) {
        int previewKey = previewKey(preview);
        if (previewKey != destroyVisualKey) {
            destroyVisualKey = previewKey;
        }

        long now = System.currentTimeMillis();
        boolean active = hasActiveDestroyProgress(controller, preview);
        boolean complete = hasCompletedDestroyProgress(controller, preview);
        if (active && !complete) {
            if (previewKey == destroyCompletePreviewKey) {
                destroyVisualComplete = false;
                destroyCompletePreviewKey = 0;
                destroyCompleteFadeStartMs = 0L;
            }
            return new DestroyVisualState(smoothedDestroyProgress(controller, preview), 1.0F, false);
        }

        boolean recentComplete = hasRecentDestroyCompletion(controller, preview, now);
        boolean completedPreview = destroyVisualComplete && previewKey == destroyCompletePreviewKey;
        if (complete || recentComplete || completedPreview) {
            if (!completedPreview) {
                destroyVisualComplete = true;
                destroyCompletePreviewKey = previewKey;
                long completedAt = controller == null ? 0L : controller.getMineProgressCompletedAtMs();
                destroyCompleteFadeStartMs = completedAt > 0L ? completedAt : now;
            }
            long elapsed = Math.max(0L, now - destroyCompleteFadeStartMs);
            float alpha = RenderingUtil.clamp01(1.0F - elapsed / (float) DESTROY_COMPLETE_FADE_MS);
            return new DestroyVisualState(1.0F, alpha, true);
        }

        return new DestroyVisualState(smoothedDestroyProgress(controller, preview), 1.0F, false);
    }

    static float smoothedDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        int previewKey = previewKey(preview);
        float target = rawDestroyProgress(controller, preview);
        long now = System.currentTimeMillis();
        if (previewKey != smoothedDestroyProgressKey) {
            smoothedDestroyProgressKey = previewKey;
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return smoothedDestroyProgress;
        }
        if (smoothedDestroyProgressMs <= 0L) {
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return smoothedDestroyProgress;
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
        return RenderingUtil.clamp01(smoothedDestroyProgress);
    }

    private static float rawDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null) return 0.0F;
        BlockPos progressPos = controller.getMineProgressPos();
        if (progressPos == null || !previewContains(preview, progressPos)) return 0.0F;
        int processed = controller.getUltimineProgressProcessed();
        int total = controller.getUltimineProgressTotal();
        int stage = controller.getMineProgressStage();
        if (processed >= 0 && total > 0) {
            if (processed >= total) return 1.0F;
            float currentBlockProgress = stage < 0 ? 0.0F : RenderingUtil.clamp01((Math.min(9, stage) + 1) / 10.0F);
            return RenderingUtil.clamp01((processed + currentBlockProgress) / (float) total);
        }
        return stage < 0 ? 0.0F : RenderingUtil.clamp01((Math.min(9, stage) + 1) / 10.0F);
    }

    // ===== Utility methods =====

    private static boolean isConfirmedDestructiveWorkArea(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.destructive() && preview.confirmedWorkArea();
    }

    static boolean hasStartedDestroyBatch(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null || preview == null) return false;
        BlockPos progressPos = controller.getMineProgressPos();
        return progressPos != null
                && previewContains(preview, progressPos)
                && controller.getUltimineProgressProcessed() >= 0
                && controller.getUltimineProgressTotal() > 0;
    }

    private static boolean hasActiveDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null || preview == null) return false;
        BlockPos progressPos = controller.getMineProgressPos();
        if (progressPos == null || !previewContains(preview, progressPos)) return false;
        if (controller.getUltimineProgressProcessed() >= 0 && controller.getUltimineProgressTotal() > 0) {
            return true;
        }
        return controller.getMineProgressStage() >= 0;
    }

    private static boolean hasCompletedDestroyProgress(ClientRtsController controller,
            ShapeDataRecords.GhostPreview preview) {
        if (controller == null || preview == null) return false;
        BlockPos progressPos = controller.getMineProgressPos();
        return progressPos != null
                && previewContains(preview, progressPos)
                && controller.getUltimineProgressTotal() > 0
                && controller.getUltimineProgressProcessed() >= controller.getUltimineProgressTotal();
    }

    private static boolean hasRecentDestroyCompletion(ClientRtsController controller,
            ShapeDataRecords.GhostPreview preview, long now) {
        if (controller == null || preview == null) return false;
        long completedAt = controller.getMineProgressCompletedAtMs();
        return completedAt > 0L
                && now - completedAt <= DESTROY_COMPLETE_FADE_MS
                && previewContains(preview, controller.getMineProgressCompletedPos());
    }

    static boolean previewContains(ShapeDataRecords.GhostPreview preview, BlockPos pos) {
        if (preview == null || pos == null) return false;
        return RenderingUtil.contains(preview.blocks(), pos) || RenderingUtil.contains(preview.emptyBlocks(), pos);
    }

    private static boolean shouldUsePlacementPreviewSettings(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.readyConfirm();
    }

    private static int previewKey(ShapeDataRecords.GhostPreview preview) {
        RenderingUtil.Bounds bounds = preview == null ? null : RenderingUtil.Bounds.from(preview.blocks(), preview.emptyBlocks());
        if (bounds == null) return 0;
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

    /**
     * 将 GhostPreview 中的方块位置裁剪到 RTS 边界范围内。
     * <p>
     * 如果所有方块都在边界外，返回 {@code null} 以跳过渲染。
     */
    private static ShapeDataRecords.GhostPreview clampPreviewToBounds(ShapeDataRecords.GhostPreview preview) {
        if (preview == null) return null;
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return preview;

        double ax = controller.getAnchorX();
        double az = controller.getAnchorZ();
        double r = controller.getMaxRadius();

        List<BlockPos> filteredBlocks = RenderingUtil.filterBlocksWithinBounds(preview.blocks(), ax, az, r);
        List<BlockPos> filteredEmptyBlocks = RenderingUtil.filterBlocksWithinBounds(preview.emptyBlocks(), ax, az, r);

        // 如果两个列表都是原对象，说明没有方块被过滤掉
        if (filteredBlocks == preview.blocks() && filteredEmptyBlocks == preview.emptyBlocks()) {
            return preview;
        }

        // 如果所有方块都不在边界内，返回 null 跳过渲染
        if (filteredBlocks.isEmpty() && filteredEmptyBlocks.isEmpty()) {
            return null;
        }

        return new ShapeDataRecords.GhostPreview(
                filteredBlocks, preview.readyConfirm(), preview.destructive(),
                filteredEmptyBlocks, preview.chainDestroyPreview(), preview.confirmedWorkArea());
    }


}
