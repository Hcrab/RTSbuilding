package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Shared rendering math kept deliberately small on Forge 1.20.1. It owns only
 * generic visual helpers, not gameplay selection or server authority.
 */
public final class RenderingUtil {
    private RenderingUtil() {
    }

    public static boolean isWithinBounds(BlockPos pos, double anchorX, double anchorZ, double maxRadius) {
        if (pos == null) {
            return false;
        }
        int minBlockX = Mth.floor(anchorX - maxRadius);
        int maxBlockX = Mth.ceil(anchorX + maxRadius) - 1;
        int minBlockZ = Mth.floor(anchorZ - maxRadius);
        int maxBlockZ = Mth.ceil(anchorZ + maxRadius) - 1;
        return pos.getX() >= minBlockX && pos.getX() <= maxBlockX
                && pos.getZ() >= minBlockZ && pos.getZ() <= maxBlockZ;
    }

    public static float getBreathFactor(float speed, float minFactor) {
        double timeSeconds = System.currentTimeMillis() / 1000.0D;
        double phase = timeSeconds * speed * 2.0D * Math.PI;
        double sin = Math.sin(phase);
        return (float) ((sin + 1.0D) * 0.5D * (1.0F - minFactor) + minFactor);
    }

    public static void quad(VertexConsumer consumer, PoseStack poseStack,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(r, g, b, a).endVertex();
    }
}
