package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGlStateRestorer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** 以穿透填充和线框显示镜头周围 3x3 区块边缘。 */
public final class ChunkGuideRenderer {
    private static final int RADIUS = 1;
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(2 * 1024 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(512 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private ChunkGuideRenderer() {
    }

    public static void renderChunkGuides(Minecraft minecraft, Vec3d cameraPosition) {
        if (minecraft == null || minecraft.world == null || cameraPosition == null) return;
        int centerChunkX = MathHelper.floor(cameraPosition.x) >> 4;
        int centerChunkZ = MathHelper.floor(cameraPosition.z) >> 4;
        int sourceY = minecraft.player == null
                ? MathHelper.floor(cameraPosition.y) : MathHelper.floor(minecraft.player.posY);
        int guideY = MathHelper.clamp(sourceY, 0, minecraft.world.getActualHeight() - 1);
        RenderManager manager = minecraft.getRenderManager();

        beginBuffers(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        try {
            for (int cx = centerChunkX - RADIUS; cx <= centerChunkX + RADIUS; cx++) {
                for (int cz = centerChunkZ - RADIUS; cz <= centerChunkZ + RADIUS; cz++) {
                    appendChunk(minecraft, cx, cz, guideY);
                }
            }
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        } finally {
            resetTranslations();
        }
    }

    /** 迁移期兼容入口：两个调用方缓冲不会被触碰。 */
    public static void renderChunkGuides(Minecraft minecraft, Vec3d cameraPosition,
            BufferBuilder callerFillBuffer, BufferBuilder callerLineBuffer) {
        renderChunkGuides(minecraft, cameraPosition);
    }

    private static void appendChunk(Minecraft minecraft, int chunkX, int chunkZ, int guideY) {
        int startX = chunkX << 4, startZ = chunkZ << 4;
        int endX = startX + 15, endZ = startZ + 15;
        if (!minecraft.world.isBlockLoaded(new BlockPos(startX, guideY, startZ), false)) return;
        Color color = colorFor(chunkX, chunkZ);
        for (int x = startX; x <= endX; x++) {
            appendCell(x, startZ, guideY, color);
            appendCell(x, endZ, guideY, color);
        }
        for (int z = startZ + 1; z < endZ; z++) {
            appendCell(startX, z, guideY, color);
            appendCell(endX, z, guideY, color);
        }
    }

    private static void appendCell(int x, int z, int y, Color color) {
        double inset = 0.04D;
        double x1 = x + inset, y1 = y + inset, z1 = z + inset;
        double x2 = x + 1.0D - inset, y2 = y + 1.0D - inset, z2 = z + 1.0D - inset;
        RenderGlobal.addChainedFilledBoxVertices(FILL_BUFFER,
                x1, y1, z1, x2, y2, z2, color.r, color.g, color.b, color.a);
        RenderGlobal.drawBoundingBox(LINE_BUFFER,
                x1, y1, z1, x2, y2, z2,
                Math.min(1.0F, color.r + 0.18F), Math.min(1.0F, color.g + 0.18F),
                Math.min(1.0F, color.b + 0.18F), 0.92F);
    }

    private static Color colorFor(int chunkX, int chunkZ) {
        return ((chunkX ^ chunkZ) & 1) == 0
                ? new Color(0.16F, 0.78F, 1.0F, 0.24F)
                : new Color(1.0F, 0.88F, 0.16F, 0.22F);
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
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            uploadOrReset(FILL_BUFFER);
            GlStateManager.glLineWidth(1.5F);
            uploadOrReset(LINE_BUFFER);
        } finally {
            resetTranslations();
            state.restore();
        }
    }

    private static void beginBuffers(double x, double y, double z) {
        FILL_BUFFER.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        FILL_BUFFER.setTranslation(x, y, z);
        try {
            LINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            LINE_BUFFER.setTranslation(x, y, z);
        } catch (RuntimeException exception) {
            discard(FILL_BUFFER);
            throw exception;
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
        try { buffer.finishDrawing(); } catch (IllegalStateException ignored) { }
        buffer.reset();
    }

    private static void resetTranslations() {
        FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static final class Color {
        final float r, g, b, a;
        Color(float r, float g, float b, float a) {
            this.r = r; this.g = g; this.b = b; this.a = a;
        }
    }

    private static final class GlSnapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        static GlSnapshot capture() { return new GlSnapshot(); }
        void restore() {
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            set(GL11.GL_BLEND, blend); set(GL11.GL_TEXTURE_2D, texture);
            set(GL11.GL_CULL_FACE, cull); set(GL11.GL_DEPTH_TEST, depth);
            GlStateManager.depthMask(depthMask); GlStateManager.glLineWidth(lineWidth);
            GlStateManager.resetColor();
        }
        private static void set(int cap, boolean enabled) {
            RtsGlStateRestorer.restoreCapability(cap, enabled);
        }
    }
}
