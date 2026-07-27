package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;

/** 向蓝图私有线缓冲写入整体包络框。 */
public final class BlueprintGhostEnvelopeRenderer {
    private static final double ENVELOPE_PADDING = 0.02D;

    private BlueprintGhostEnvelopeRenderer() {
    }

    public static void render(BufferBuilder lineBuffer,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            float r, float g, float b, float alpha) {
        if (lineBuffer == null || minX == Integer.MAX_VALUE) {
            return;
        }
        RenderGlobal.drawBoundingBox(lineBuffer,
                minX - ENVELOPE_PADDING, minY - ENVELOPE_PADDING, minZ - ENVELOPE_PADDING,
                maxX + ENVELOPE_PADDING, maxY + ENVELOPE_PADDING, maxZ + ENVELOPE_PADDING,
                r, g, b, alpha);
    }
}
