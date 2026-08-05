package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.block.EnumBlockRenderType;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** 服务端确认挖掘后短暂显示的缩小消失动画。 */
public final class DestroyGhostRenderer {
    static final long DESTROY_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.56F;
    private static final Map<Long, DestroyGhostEntry> GHOSTS =
            new LinkedHashMap<Long, DestroyGhostEntry>();

    private DestroyGhostRenderer() {
    }

    public static void add(BlockPos pos, BlockState state) {
        addAt(pos, state, System.currentTimeMillis());
    }

    static void addAt(BlockPos pos, BlockState state, long addedAtMs) {
        if (pos == null || state == null || state.getBlock() == Blocks.air) return;
        GHOSTS.put(pos.toLong(), new DestroyGhostEntry(pos.toImmutable(), state, addedAtMs));
    }

    static int ghostCount() {
        return GHOSTS.size();
    }

    static void clearAll() {
        GHOSTS.clear();
    }

    static void renderModels(Minecraft minecraft, BufferBuilder fillBuffer,
            double cameraX, double cameraY, double cameraZ, long now) {
        pruneExpired(now);
        if (minecraft == null || minecraft.theWorld == null || GHOSTS.isEmpty()) return;
        for (DestroyGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            float scale = computeShrinkScale(now - ghost.addedAtMs);
            if (ghost.state.getRenderType() == EnumBlockRenderType.MODEL) {
                PlacementAnimationRenderer.renderBlockModel(minecraft, ghost.state, ghost.pos,
                        MODEL_ALPHA, scale, cameraX, cameraY, cameraZ);
            } else {
                double inset = 0.5D - scale * 0.46D;
                PlacementAnimationRenderer.renderFilledBox(fillBuffer,
                        ghost.pos.getX() + inset, ghost.pos.getY() + inset, ghost.pos.getZ() + inset,
                        ghost.pos.getX() + 1.0D - inset, ghost.pos.getY() + 1.0D - inset,
                        ghost.pos.getZ() + 1.0D - inset,
                        0.30F, 0.95F, 0.36F, Math.max(0.0F, scale * 0.14F));
            }
        }
    }

    static void renderWireframes(BufferBuilder lineBuffer, long now) {
        pruneExpired(now);
        for (DestroyGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            float scale = computeShrinkScale(now - ghost.addedAtMs);
            PlacementAnimationRenderer.renderLineBox(lineBuffer, ghost.pos, scale,
                    0.38F, 1.00F, 0.42F, Math.max(0.0F, scale * 0.95F));
        }
    }

    static void pruneExpired(long now) {
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = GHOSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().addedAtMs > DESTROY_DURATION_MS) iterator.remove();
        }
    }

    static float computeShrinkScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) DESTROY_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return Math.max(0.0F, 1.0F - eased);
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        return !controller.hasBounds() || RenderingUtil.isWithinBounds(pos,
                controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private static final class DestroyGhostEntry {
        private final BlockPos pos;
        private final BlockState state;
        private final long addedAtMs;
        private DestroyGhostEntry(BlockPos pos, BlockState state, long addedAtMs) {
            this.pos = pos; this.state = state; this.addedAtMs = addedAtMs;
        }
    }
}
