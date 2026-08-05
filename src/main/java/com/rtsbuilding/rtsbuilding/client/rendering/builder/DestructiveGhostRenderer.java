package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

/** 1.12 范围破坏单元格和总包络渲染器。 */
public final class DestructiveGhostRenderer {
    private static final double PAD = .02D;
    private static final BufferBuilder LINES = new BufferBuilder(1024 * 1024);
    private static final BufferBuilder FILL = new BufferBuilder(1024 * 1024);
    private static final BufferBuilder NODEPTH = new BufferBuilder(256 * 1024);
    @SuppressWarnings("unused")
    private static final WorldVertexBufferUploader UP = new WorldVertexBufferUploader();

    private DestructiveGhostRenderer() { }

    static void render(ShapeDataRecords.GhostPreview preview, BufferBuilder lines,
            BufferBuilder fill, float progress, float alpha) {
        render(preview, lines, fill, progress, alpha, null);
    }

    static void render(ShapeDataRecords.GhostPreview preview, BufferBuilder lines,
            BufferBuilder fill, float progress, float alpha, AxisAlignedBB override) {
        render0(preview, progress, alpha, true, true, override);
    }

    static void render(ShapeDataRecords.GhostPreview preview, BufferBuilder lines,
            BufferBuilder fill, float progress, float alpha,
            boolean renderFill, boolean renderLines) {
        render0(preview, progress, alpha, renderFill, renderLines, null);
    }

    static void renderWireframe(ShapeDataRecords.GhostPreview preview,
            BufferBuilder lines, float progress) {
        render0(preview, progress, 1.0F, false, true, null);
    }

    private static void render0(ShapeDataRecords.GhostPreview preview,
            float progress, float alpha, boolean renderFill, boolean renderLines,
            AxisAlignedBB override) {
        if (preview == null || alpha <= 0.0F) return;

        RenderManager manager = net.minecraft.client.renderer.entity.RenderManager.instance;
        begin(LINES, GL11.GL_LINES, manager);
        begin(FILL, GL11.GL_QUADS, manager);
        begin(NODEPTH, GL11.GL_LINES, manager);

        List<BlockPos> blocks = preview.blocks();
        float lineRed = lerp(preview.readyConfirm() ? .95F : 1.0F, .38F, progress);
        float lineGreen = lerp(preview.readyConfirm() ? .38F : .25F, 1.0F, progress);
        float lineBlue = lerp(.20F, .42F, progress);
        if (blocks != null) {
            for (BlockPos pos : blocks) {
                double minX = pos.getX() + .03D;
                double minY = pos.getY() + .03D;
                double minZ = pos.getZ() + .03D;
                double maxX = pos.getX() + .97D;
                double maxY = pos.getY() + .97D;
                double maxZ = pos.getZ() + .97D;
                if (renderFill) {
                    com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyRenderGeometry.addChainedFilledBoxVertices(
                            FILL, minX, minY, minZ, maxX, maxY, maxZ,
                            lineRed, lineGreen, lineBlue, .16F * alpha);
                }
                if (renderLines) {
                    appendLineBox(LINES, minX, minY, minZ, maxX, maxY, maxZ,
                            lineRed, lineGreen, lineBlue, .88F * alpha);
                }
            }
        }

        AxisAlignedBB envelope = override != null
                ? override : bounds(blocks, preview.emptyBlocks());
        if (envelope != null) {
            envelope = envelope.grow(PAD);
            float red = lerp(1.0F, .3F, progress);
            float green = lerp(.86F, .95F, progress);
            float blue = lerp(.18F, .36F, progress);
            if (renderFill) {
                com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyRenderGeometry.addChainedFilledBoxVertices(
                        FILL,
                        envelope.minX, envelope.minY, envelope.minZ,
                        envelope.maxX, envelope.maxY, envelope.maxZ,
                        red, green, blue, .10F * alpha);
            }
            if (renderLines) {
                float borderRed = lerp(1.0F, .38F, progress);
                float borderGreen = lerp(.86F, 1.0F, progress);
                float borderBlue = lerp(.22F, .42F, progress);
                appendLineBox(
                        LINES,
                        envelope.minX, envelope.minY, envelope.minZ,
                        envelope.maxX, envelope.maxY, envelope.maxZ,
                        borderRed, borderGreen, borderBlue, .78F * alpha);
                appendLineBox(
                        NODEPTH,
                        envelope.minX, envelope.minY, envelope.minZ,
                        envelope.maxX, envelope.maxY, envelope.maxZ,
                        borderRed, borderGreen, borderBlue, .20F * alpha);
            }
        }
        draw(renderLines, renderFill);
    }

    /**
     * GL_LINES 必须为十二条边逐对提交二十四个顶点。原版 drawBoundingBox 的顶点顺序
     * 面向 line strip，直接混用会让部分顶点被错误配对，最终只剩下几条可见边。
     */
    static void appendLineBox(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha) {
        line(buffer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(buffer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(buffer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);

        line(buffer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);

        line(buffer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(buffer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void line(BufferBuilder buffer,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        vertex(buffer, x1, y1, z1, red, green, blue, alpha);
        vertex(buffer, x2, y2, z2, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer,
            double x, double y, double z,
            float red, float green, float blue, float alpha) {
        buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static void begin(BufferBuilder buffer, int mode, RenderManager manager) {
        buffer.begin(mode, DefaultVertexFormats.POSITION_COLOR);
        buffer.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
    }

    private static void draw(boolean lines, boolean fill) {
        UltimineGhostRenderer.GlSnapshot gl = UltimineGhostRenderer.GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            if (lines) RtsOwnedBufferUploader.draw(LINES);
            else discard(LINES);
            if (fill) RtsOwnedBufferUploader.draw(FILL);
            else discard(FILL);
            if (lines) {
                GlStateManager.disableDepth();
                RtsOwnedBufferUploader.draw(NODEPTH);
                GlStateManager.enableDepth();
            } else {
                discard(NODEPTH);
            }
        } finally {
            LINES.setTranslation(0.0D, 0.0D, 0.0D);
            FILL.setTranslation(0.0D, 0.0D, 0.0D);
            NODEPTH.setTranslation(0.0D, 0.0D, 0.0D);
            gl.restore();
        }
    }

    private static void discard(BufferBuilder buffer) {
        buffer.finishDrawing();
        buffer.reset();
    }

    private static AxisAlignedBB bounds(List<BlockPos> first, List<BlockPos> second) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (List<BlockPos> positions : Arrays.asList(first, second)) {
            if (positions == null) continue;
            for (BlockPos pos : positions) {
                any = true;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX() + 1);
                maxY = Math.max(maxY, pos.getY() + 1);
                maxZ = Math.max(maxZ, pos.getZ() + 1);
            }
        }
        return any ? new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private static float lerp(float start, float end, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return start + (end - start) * clamped;
    }
}
