package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGlStateRestorer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 1.12 的 RTS 边界力场渲染器。
 *
 * <p>本类完整拥有自己的缓冲区；兼容重载中的调用方缓冲仅用于维持迁移期签名，
 * 不会被 begin、finish、reset 或上传。</p>
 */
public final class BoundaryLineRenderer {
    private static final float TILE_SIZE = 2.0F;
    private static final float ALPHA = 0.80F;
    private static final BufferBuilder BUFFER = new BufferBuilder(256 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private static final ResourceLocation FORCEFIELD =
            new ResourceLocation("minecraft", "textures/misc/forcefield.png");

    private BoundaryLineRenderer() {
    }

    public static void renderBarrierBoundary(double minX, double minZ, double maxX, double maxZ,
            double defaultY, World world) {
        if (world == null) return;
        int highest = findHighestBoundaryBlock(world, minX, minZ, maxX, maxZ);
        double yMax = highest == Integer.MIN_VALUE ? defaultY + 3.0D : highest + 5.0D;
        double yMin = 0.0D;
        float scroll = (float) (System.nanoTime() / 1.0e9D * 0.5D);
        double cameraX = Minecraft.getMinecraft().getRenderManager().viewerPosX;
        double cameraY = Minecraft.getMinecraft().getRenderManager().viewerPosY;
        double cameraZ = Minecraft.getMinecraft().getRenderManager().viewerPosZ;

        BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            double heightTiles = (yMax - yMin) / TILE_SIZE;
            addWall(minX, yMin, minZ, maxX, yMax, minZ,
                    (maxX - minX) / TILE_SIZE, heightTiles, scroll);
            addWall(maxX, yMin, maxZ, minX, yMax, maxZ,
                    (maxX - minX) / TILE_SIZE, heightTiles, scroll);
            addWall(minX, yMin, minZ, minX, yMax, maxZ,
                    (maxZ - minZ) / TILE_SIZE, heightTiles, scroll);
            addWall(maxX, yMin, maxZ, maxX, yMax, minZ,
                    (maxZ - minZ) / TILE_SIZE, heightTiles, scroll);
            drawOwnedBuffer();
        } catch (RuntimeException exception) {
            discard();
            throw exception;
        } finally {
            BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        }
    }

    /** 迁移期兼容入口：调用方缓冲绝不会被触碰。 */
    public static void renderBarrierBoundary(BufferBuilder callerBuffer,
            double minX, double minZ, double maxX, double maxZ, double defaultY, World world) {
        renderBarrierBoundary(minX, minZ, maxX, maxZ, defaultY, world);
    }

    private static int findHighestBoundaryBlock(World world,
            double minX, double minZ, double maxX, double maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX), x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ), z2 = (int) Math.floor(maxZ);
        for (int x = x1; x <= x2; x++) {
            highest = heightIfLoaded(world, x, z1, highest);
            highest = heightIfLoaded(world, x, z2, highest);
        }
        for (int z = z1 + 1; z < z2; z++) {
            highest = heightIfLoaded(world, x1, z, highest);
            highest = heightIfLoaded(world, x2, z, highest);
        }
        return highest;
    }

    private static int heightIfLoaded(World world, int x, int z, int current) {
        BlockPos probe = new BlockPos(x, 64, z);
        return world.isBlockLoaded(probe, false) ? Math.max(current, world.getHeight(x, z)) : current;
    }

    private static void addWall(double x1, double y1, double z1,
            double x2, double y2, double z2, double tileU, double tileV, float scroll) {
        vertex(x1, y1, z1, scroll, scroll);
        vertex(x2, y1, z2, tileU + scroll, scroll);
        vertex(x2, y2, z2, tileU + scroll, tileV + scroll);
        vertex(x1, y2, z1, scroll, tileV + scroll);
    }

    private static void vertex(double x, double y, double z, double u, double v) {
        BUFFER.pos(x, y, z).tex(u, v).color(1.0F, 1.0F, 1.0F, ALPHA).endVertex();
    }

    private static void drawOwnedBuffer() {
        GlSnapshot state = GlSnapshot.capture();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            Minecraft.getMinecraft().getTextureManager().bindTexture(FORCEFIELD);
            RtsOwnedBufferUploader.draw(BUFFER);
        } finally {
            state.restore();
        }
    }

    private static void discard() {
        try { BUFFER.finishDrawing(); } catch (IllegalStateException ignored) { }
        BUFFER.reset();
    }

    private static final class GlSnapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        private final int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        static GlSnapshot capture() { return new GlSnapshot(); }

        void restore() {
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_TEXTURE_2D, texture);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_DEPTH_TEST, depth);
            GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);
            RtsGlStateRestorer.restoreTextureBinding(textureBinding);
            GlStateManager.resetColor();
        }

        private static void set(int capability, boolean enabled) {
            RtsGlStateRestorer.restoreCapability(capability, enabled);
        }
    }
}
