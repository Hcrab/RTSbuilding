package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

/**
 * Manages and renders destroy ghosts — a brief shrink-out line-box skeleton
 * shown when the server confirms a block has been destroyed.
 */
public final class DestroyGhostRenderer {

    /** Duration of the shrink-out animation (ms). */
    private static final long DESTROY_DURATION_MS = 220L;

    private DestroyGhostRenderer() {
    }

    /** pos.asLong() -> entry */
    private static final Map<Long, DestroyGhostEntry> GHOSTS = new LinkedHashMap<>();

    // ===== Public API =====

    /**
     * Registers a block position for destroy animation.
     * Called when the server confirms a block was destroyed.
     */
    public static void add(BlockPos pos) {
        if (pos == null) return;
        GHOSTS.put(pos.asLong(), new DestroyGhostEntry(pos.immutable(), System.currentTimeMillis()));
    }

    // ===== Rendering (called from facade) =====

    /** Renders all active destroy ghost skeletons (auto-removes expired entries). */
    static void render(PoseStack poseStack, VertexConsumer lineBuffer) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = GHOSTS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, DestroyGhostEntry> entry = iterator.next();
            DestroyGhostEntry ghost = entry.getValue();
            long elapsed = now - ghost.addedAtMs;

            if (elapsed > DESTROY_DURATION_MS) {
                iterator.remove();
                continue;
            }

            float scale = computeShrinkScale(elapsed);
            if (!isWithinBounds(ghost.pos)) continue;
            double half = (scale * 0.5D) + (0.02D * scale);
            double minX = ghost.pos.getX() + 0.5D - half;
            double minY = ghost.pos.getY() + 0.5D - half;
            double minZ = ghost.pos.getZ() + 0.5D - half;
            double maxX = ghost.pos.getX() + 0.5D + half;
            double maxY = ghost.pos.getY() + 0.5D + half;
            double maxZ = ghost.pos.getZ() + 0.5D + half;
            float alpha = Math.max(0.0F, Math.min(0.95F, scale * 0.95F));
            LevelRenderer.renderLineBox(poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    0.38F, 1.00F, 0.42F, alpha);
        }
    }

    // ===== Animation helpers =====

    /**
     * Computes the animated shrink scale for destroy ghosts (reverse of grow-in).
     * Starts at full size and eases out to 0 over DESTROY_DURATION_MS.
     */
    private static float computeShrinkScale(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        float progress = Math.min(1.0F, elapsedMs / (float) DESTROY_DURATION_MS);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress); // quadratic ease-out on reverse
        return Math.max(0.0F, 1.0F - eased);
    }

    // ===== Internal record =====

    private record DestroyGhostEntry(BlockPos pos, long addedAtMs) {
    }

    /**
     * 判断方块位置是否在 RTS 边界范围内。
     */
    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return true;
        return RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }
}
