package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理客户端已提交、但尚未收到服务端确认的放置虚影。 */
public final class PendingGhostRenderer {
    private static final float GHOST_ALPHA = 0.60F;
    static final long GROW_DURATION_MS = 220L;
    static final long MAX_PENDING_MS = 5000L;
    private static final float BASE_SCALE = 0.8F;
    private static final float PULSE_AMPLITUDE = 0.025F;
    private static final float PULSE_FREQUENCY = 0.008F;
    private static final Map<Long, PendingGhostEntry> GHOSTS =
            new LinkedHashMap<Long, PendingGhostEntry>();

    private PendingGhostRenderer() {
    }

    public static void addPendingBatch(List<BlockPos> positions, IBlockState blockState) {
        addPendingBatchAt(positions, blockState, System.currentTimeMillis());
    }

    static void addPendingBatchAt(List<BlockPos> positions, IBlockState blockState, long addedAtMs) {
        if (positions == null || positions.isEmpty()) return;
        for (BlockPos pos : positions) {
            if (pos != null) {
                GHOSTS.put(pos.toLong(), new PendingGhostEntry(pos.toImmutable(), blockState, addedAtMs));
            }
        }
    }

    public static void clearAll() {
        GHOSTS.clear();
    }

    public static void remove(BlockPos pos) {
        if (pos != null) GHOSTS.remove(pos.toLong());
    }

    static int pendingCount() {
        return GHOSTS.size();
    }

    static void renderModels(Minecraft minecraft, BufferBuilder fillBuffer,
            double cameraX, double cameraY, double cameraZ, long now) {
        pruneExpired(now);
        if (minecraft == null || minecraft.world == null || GHOSTS.isEmpty()) return;
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            float scale = computeGrowScale(now - ghost.addedAtMs);
            if (ghost.state != null && ghost.state.getRenderType() == EnumBlockRenderType.MODEL) {
                PlacementAnimationRenderer.renderBlockModel(minecraft, ghost.state, ghost.pos,
                        GHOST_ALPHA, scale, cameraX, cameraY, cameraZ);
            } else {
                renderFilledBox(fillBuffer, ghost.pos, scale, 0.40F, 0.85F, 0.90F, 0.12F);
            }
        }
    }

    static void renderWireframes(BufferBuilder lineBuffer, long now) {
        pruneExpired(now);
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            PlacementAnimationRenderer.renderLineBox(lineBuffer, ghost.pos,
                    computeGrowScale(now - ghost.addedAtMs),
                    0.30F, 0.75F, 1.00F, 0.75F);
        }
    }

    private static void renderFilledBox(BufferBuilder buffer, BlockPos pos, float scale,
            float red, float green, float blue, float alpha) {
        double inset = 0.5D - scale * 0.44D;
        PlacementAnimationRenderer.renderFilledBox(buffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                red, green, blue, alpha);
    }

    static void pruneExpired(long now) {
        Iterator<Map.Entry<Long, PendingGhostEntry>> iterator = GHOSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingGhostEntry ghost = iterator.next().getValue();
            if (now - ghost.addedAtMs > MAX_PENDING_MS) iterator.remove();
        }
    }

    static float computeGrowScale(long elapsedMs) {
        long elapsed = Math.max(0L, elapsedMs);
        float progress = Math.min(1.0F, elapsed / (float) GROW_DURATION_MS);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        float scale = progress * BASE_SCALE;
        if (progress >= 1.0F) {
            scale += PULSE_AMPLITUDE * (float) Math.sin(elapsed * PULSE_FREQUENCY);
        }
        return scale;
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        return !controller.hasBounds() || RenderingUtil.isWithinBounds(pos,
                controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private static final class PendingGhostEntry {
        private final BlockPos pos;
        private final IBlockState state;
        private final long addedAtMs;

        private PendingGhostEntry(BlockPos pos, IBlockState state, long addedAtMs) {
            this.pos = pos;
            this.state = state;
            this.addedAtMs = addedAtMs;
        }
    }
}
