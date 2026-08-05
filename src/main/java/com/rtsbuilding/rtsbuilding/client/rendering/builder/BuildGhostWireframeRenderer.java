package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.List;

/**
 * 1.12 建造预览线框顶点生成器。
 *
 * <p>本类只向世界渲染总入口已经开始的私有线缓冲追加顶点，不拥有、不提交也不重置
 * 缓冲。这样批量建造线框与主线一样，和范围选择框在同一个世界线框阶段统一绘制，
 * 不会在形状遍历中额外生成一层立即上传的逐格蓝线。</p>
 */
public final class BuildGhostWireframeRenderer {
    private BuildGhostWireframeRenderer() {
    }

    public static void renderWireframes(List<BlockPos> blocks, BufferBuilder callerBuffer, boolean readyConfirm) {
        if (blocks == null || blocks.isEmpty() || callerBuffer == null) {
            return;
        }
        float lineR = 0.30F;
        float lineG = 0.75F;
        float lineB = 1.00F;
        for (BlockPos pos : blocks) {
            appendLineBox(
                    callerBuffer,
                    pos.getX() + 0.03D,
                    pos.getY() + 0.03D,
                    pos.getZ() + 0.03D,
                    pos.getX() + 0.97D,
                    pos.getY() + 0.97D,
                    pos.getZ() + 0.97D,
                    lineR,
                    lineG,
                    lineB,
                    0.70F);
        }
    }

    /**
     * 向 {@code GL_LINES} 缓冲追加盒子的十二条独立边。
     *
     * <p>不能使用 1.12 的 {@code RenderGlobal.drawBoundingBox}：那个帮助方法输出的是
     * 18 个 {@code GL_LINE_STRIP} 顶点。把它放进世界总入口的 {@code GL_LINES} 缓冲会
     * 把相邻顶点错误配对，视觉上就会变成断裂的逐格梯状线框。</p>
     */
    private static void appendLineBox(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        appendEdge(buffer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        appendEdge(buffer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);

        appendEdge(buffer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        appendEdge(buffer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);

        appendEdge(buffer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        appendEdge(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        appendEdge(buffer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void appendEdge(BufferBuilder buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
    }
}
