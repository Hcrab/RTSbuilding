package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** 服务端确认放置后短暂显示的放大进入动画。 */
final class ConfirmedPlacementRenderer {
    static final long PLACE_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.58F;
    private static final Map<Long, PlacementEntry> PLACEMENTS =
            new LinkedHashMap<Long, PlacementEntry>();

    private ConfirmedPlacementRenderer() {
    }

    static void add(BlockPos pos, IBlockState state) {
        addAt(pos, state, System.currentTimeMillis());
    }

    static void addAt(BlockPos pos, IBlockState state, long addedAtMs) {
        if (pos == null || state == null || state.getBlock() == Blocks.AIR) return;
        PLACEMENTS.put(pos.toLong(), new PlacementEntry(pos.toImmutable(), state, addedAtMs));
    }

    static int placementCount() {
        return PLACEMENTS.size();
    }

    static void clearAll() {
        PLACEMENTS.clear();
    }

    static void renderModels(Minecraft minecraft, BufferBuilder fillBuffer,
            double cameraX, double cameraY, double cameraZ, long now) {
        pruneExpired(now);
        if (minecraft == null || minecraft.world == null || PLACEMENTS.isEmpty()) return;
        for (PlacementEntry entry : PLACEMENTS.values()) {
            if (!isWithinBounds(entry.pos)) continue;
            float scale = computeGrowScale(now - entry.addedAtMs);
            if (entry.state.getRenderType() == EnumBlockRenderType.MODEL) {
                PlacementAnimationRenderer.renderBlockModel(minecraft, entry.state, entry.pos,
                        MODEL_ALPHA, scale, cameraX, cameraY, cameraZ);
            } else {
                double inset = 0.5D - scale * 0.46D;
                PlacementAnimationRenderer.renderFilledBox(fillBuffer,
                        entry.pos.getX() + inset, entry.pos.getY() + inset, entry.pos.getZ() + inset,
                        entry.pos.getX() + 1.0D - inset, entry.pos.getY() + 1.0D - inset,
                        entry.pos.getZ() + 1.0D - inset, 0.40F, 0.85F, 0.90F, 0.16F);
            }
        }
    }

    static void renderWireframes(BufferBuilder lineBuffer, long now) {
        pruneExpired(now);
        for (PlacementEntry entry : PLACEMENTS.values()) {
            if (!isWithinBounds(entry.pos)) continue;
            PlacementAnimationRenderer.renderLineBox(lineBuffer, entry.pos,
                    computeGrowScale(now - entry.addedAtMs), 0.30F, 0.85F, 1.00F, 0.82F);
        }
    }

    static void pruneExpired(long now) {
        Iterator<Map.Entry<Long, PlacementEntry>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().addedAtMs > PLACE_DURATION_MS) iterator.remove();
        }
    }

    static float computeGrowScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) PLACE_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return 0.12F + eased * 0.86F;
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        return !controller.hasBounds() || RenderingUtil.isWithinBounds(pos,
                controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private static final class PlacementEntry {
        private final BlockPos pos;
        private final IBlockState state;
        private final long addedAtMs;
        private PlacementEntry(BlockPos pos, IBlockState state, long addedAtMs) {
            this.pos = pos; this.state = state; this.addedAtMs = addedAtMs;
        }
    }
}
