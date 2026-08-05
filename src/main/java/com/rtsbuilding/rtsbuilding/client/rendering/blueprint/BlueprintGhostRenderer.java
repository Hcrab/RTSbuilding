package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 1.12 蓝图虚影总渲染入口。
 *
 * <p>模型批次与线框批次均使用本包私有缓冲，调用方不需要也不得把 Minecraft 正在使用的
 * Tessellator 缓冲交给这里。入口依次执行边界裁剪、方块模型、fallback 与整体包络框。</p>
 */
public final class BlueprintGhostRenderer {
    private static final float TRUNCATED_BOX_ALPHA = 0.22F;
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(512 * 1024);
    private static final WorldVertexBufferUploader LINE_UPLOADER = new WorldVertexBufferUploader();

    private BlueprintGhostRenderer() {
    }

    public static void renderBlueprintGhostPreview(Minecraft minecraft) {
        if (minecraft == null || !(minecraft.currentScreen instanceof BuilderScreen)) {
            return;
        }

        BuilderScreen screen = (BuilderScreen) minecraft.currentScreen;
        BlueprintGhostPreview preview = screen.getBlueprintGhostPreview();
        if (preview == null || preview.blocks() == null || preview.blocks().isEmpty()) {
            return;
        }

        List<BlueprintGhostBlock> blocks = BlueprintGhostBoundsFilter.filter(preview.blocks());
        if (blocks.isEmpty()) {
            return;
        }

        RenderManager renderManager = net.minecraft.client.renderer.entity.RenderManager.instance;
        double cameraX = renderManager.viewerPosX;
        double cameraY = renderManager.viewerPosY;
        double cameraZ = renderManager.viewerPosZ;
        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        int[] minX = {Integer.MAX_VALUE};
        int[] minY = {Integer.MAX_VALUE};
        int[] minZ = {Integer.MAX_VALUE};
        int[] maxX = {Integer.MIN_VALUE};
        int[] maxY = {Integer.MIN_VALUE};
        int[] maxZ = {Integer.MIN_VALUE};

        BlueprintGhostBlockModelRenderer.renderModels(minecraft, blocks, cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ);

        LINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        LINE_BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            BlueprintGhostFallbackRenderer.renderFallbacks(blocks, LINE_BUFFER, lineR, lineG, lineB);
            float envelopeAlpha = preview.truncated()
                    ? TRUNCATED_BOX_ALPHA : BlueprintGhostBlockModelRenderer.GHOST_ALPHA;
            BlueprintGhostEnvelopeRenderer.render(LINE_BUFFER,
                    minX[0], minY[0], minZ[0], maxX[0], maxY[0], maxZ[0],
                    lineR, lineG, lineB, envelopeAlpha);
            drawLines();
        } catch (RuntimeException exception) {
            discardLineBuffer();
            throw exception;
        }
    }

    /**
     * 迁移期兼容入口。两个参数只用于标明旧总渲染器已有的缓冲所有权；本类不会读写或结束它们。
     */
    public static void renderBlueprintGhostPreview(Minecraft minecraft,
            BufferBuilder callerLineBuffer, BufferBuilder callerFillBuffer) {
        renderBlueprintGhostPreview(minecraft);
    }

    private static void drawLines() {
        if (LINE_BUFFER.getVertexCount() == 0) {
            LINE_BUFFER.finishDrawing();
            LINE_BUFFER.reset();
            LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.5F);
        try {
            RtsOwnedBufferUploader.draw(LINE_BUFFER);
        } finally {
            LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
            GlStateManager.glLineWidth(1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.resetColor();
        }
    }

    private static void discardLineBuffer() {
        try {
            LINE_BUFFER.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 上传器可能已经 finish，只是尚未来得及 reset。
        }
        LINE_BUFFER.reset();
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }
}
