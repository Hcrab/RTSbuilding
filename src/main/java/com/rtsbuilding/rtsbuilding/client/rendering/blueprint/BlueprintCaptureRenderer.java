package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGlStateQueries;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.List;

/**
 * 蓝图捕获区域的 1.12 世界空间渲染器。
 *
 * <p>本类保留主线的包含块、排除块、捕获边界和六向手柄语义。所有顶点都写入本类独占的
 * {@link BufferBuilder}；兼容入口收到的缓冲仅用于维持迁移期调用协议，绝不会结束、上传或重置
 * Minecraft/Tessellator 或调用方的共享缓冲。渲染修改过的 GL 状态会在 {@code finally} 中精确恢复。</p>
 */
public final class BlueprintCaptureRenderer {
    private static final int CAPTURE_BLOCK_HIGHLIGHT_LIMIT = 8192;
    private static final int CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT = 1024;
    // 8192 个包含块加 1024 个排除块的双层填充约需 4.7 MiB，预留余量避免录制大区域时扩容。
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(8 * 1024 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(512 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private static final float INCLUDED_BLOCK_R = 0.12F;
    private static final float INCLUDED_BLOCK_G = 0.56F;
    private static final float INCLUDED_BLOCK_B = 1.0F;
    private static final float INCLUDED_BLOCK_A = 0.11F;
    private static final float EXCLUDED_BLOCK_R = 1.0F;
    private static final float EXCLUDED_BLOCK_G = 0.36F;
    private static final float EXCLUDED_BLOCK_B = 0.12F;
    private static final float EXCLUDED_BLOCK_LINE_A = 0.95F;
    private static final float EXCLUDED_BLOCK_FILL_A = 0.24F;
    private static final float EXCLUDED_BLOCK_MARK_A = 0.72F;
    private static final float BOUNDARY_BOX_R = 0.35F;
    private static final float BOUNDARY_BOX_G = 0.78F;
    private static final float BOUNDARY_BOX_B = 1.0F;
    private static final float BOUNDARY_BOX_A = 0.95F;

    private BlueprintCaptureRenderer() {
    }

    public static void renderBlueprintCaptureBox() {
        RtsCullingBox captureBox = BlueprintPanel.getCapturePreviewBoxForRender();
        AxisAlignedBB renderBox = BlueprintPanel.getCapturePreviewAabbForRender();
        if (captureBox == null || renderBox == null) {
            return;
        }

        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        beginOwnedBuffers(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        try {
            appendCaptureGeometry(FILL_BUFFER, LINE_BUFFER, renderBox,
                    BlueprintPanel.getCaptureIncludedBlocksForRender(CAPTURE_BLOCK_HIGHLIGHT_LIMIT),
                    BlueprintPanel.getCaptureExcludedBlocksForRender(CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT));
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        }

        if (BlueprintPanel.isCaptureSelectionComplete()) {
            RtsBoxHandleRenderer.renderAxisHandles(renderBox,
                    BlueprintPanel.getCaptureHoveredHandleDirection(),
                    BlueprintPanel.getCaptureActiveHandleDirection());
        }
    }

    /**
     * 迁移期兼容入口。四个参数均归调用方所有，本类有意忽略它们并使用自己的缓冲。
     */
    public static void renderBlueprintCaptureBox(BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer,
            BufferBuilder callerHandleLineBuffer, BufferBuilder callerHandleFillBuffer) {
        renderBlueprintCaptureBox();
    }

    static void appendCaptureGeometry(BufferBuilder fillBuffer, BufferBuilder lineBuffer,
            AxisAlignedBB renderBox, List<BlockPos> includedBlocks, List<BlockPos> excludedBlocks) {
        if (includedBlocks != null) {
            for (BlockPos pos : includedBlocks) {
                if (pos == null) continue;
                RenderGlobal.addChainedFilledBoxVertices(fillBuffer,
                        pos.getX() + 0.04D, pos.getY() + 0.04D, pos.getZ() + 0.04D,
                        pos.getX() + 0.96D, pos.getY() + 0.96D, pos.getZ() + 0.96D,
                        INCLUDED_BLOCK_R, INCLUDED_BLOCK_G, INCLUDED_BLOCK_B, INCLUDED_BLOCK_A);
            }
        }
        if (excludedBlocks != null) {
            for (BlockPos pos : excludedBlocks) {
                if (pos == null) continue;
                RenderGlobal.addChainedFilledBoxVertices(fillBuffer,
                        pos.getX() + 0.07D, pos.getY() + 0.07D, pos.getZ() + 0.07D,
                        pos.getX() + 0.93D, pos.getY() + 0.93D, pos.getZ() + 0.93D,
                        EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_FILL_A);
                RenderGlobal.addChainedFilledBoxVertices(fillBuffer,
                        pos.getX() + 0.18D, pos.getY() + 0.91D, pos.getZ() + 0.18D,
                        pos.getX() + 0.82D, pos.getY() + 0.99D, pos.getZ() + 0.82D,
                        EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_MARK_A);
                RenderGlobal.drawBoundingBox(lineBuffer,
                        pos.getX() + 0.06D, pos.getY() + 0.06D, pos.getZ() + 0.06D,
                        pos.getX() + 0.94D, pos.getY() + 0.94D, pos.getZ() + 0.94D,
                        EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_LINE_A);
            }
        }

        appendBoundary(lineBuffer, renderBox);
    }

    private static void appendBoundary(BufferBuilder lineBuffer, AxisAlignedBB box) {
        RenderGlobal.drawBoundingBox(lineBuffer,
                box.minX - 0.01D, box.minY - 0.01D, box.minZ - 0.01D,
                box.maxX + 0.01D, box.maxY + 0.01D, box.maxZ + 0.01D,
                BOUNDARY_BOX_R, BOUNDARY_BOX_G, BOUNDARY_BOX_B, BOUNDARY_BOX_A);
    }

    private static void beginOwnedBuffers(double translateX, double translateY, double translateZ) {
        FILL_BUFFER.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        FILL_BUFFER.setTranslation(translateX, translateY, translateZ);
        try {
            // RenderGlobal#drawBoundingBox 输出一条带透明断点的连续线带。
            LINE_BUFFER.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            LINE_BUFFER.setTranslation(translateX, translateY, translateZ);
        } catch (RuntimeException exception) {
            discard(FILL_BUFFER);
            throw exception;
        }
    }

    private static void drawOwnedBuffers() {
        GlSnapshot state = GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            uploadOrReset(FILL_BUFFER);
            GlStateManager.glLineWidth(1.5F);
            uploadOrReset(LINE_BUFFER);
        } finally {
            resetTranslations();
            state.restore();
        }
    }

    private static void uploadOrReset(BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) RtsOwnedBufferUploader.draw(buffer); else discard(buffer);
    }

    private static void discardOwnedBuffers() {
        discard(FILL_BUFFER);
        discard(LINE_BUFFER);
        resetTranslations();
    }

    private static void discard(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 缓冲可能已经由本类上传完成；这里只确保它回到可复用状态。
        }
        buffer.reset();
    }

    private static void resetTranslations() {
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static final class GlSnapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float[] color = RtsGlStateQueries.currentColor();

        private static GlSnapshot capture() { return new GlSnapshot(); }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            setBlend(blend); setTexture(texture); setCull(cull); setDepth(depth);
            GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);
            GlStateManager.color(color[0], color[1], color[2], color[3]);
        }

        private static void setBlend(boolean enabled) {
            if (enabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        }
        private static void setTexture(boolean enabled) {
            if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
        private static void setCull(boolean enabled) {
            if (enabled) GlStateManager.enableCull(); else GlStateManager.disableCull();
        }
        private static void setDepth(boolean enabled) {
            if (enabled) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }
}
