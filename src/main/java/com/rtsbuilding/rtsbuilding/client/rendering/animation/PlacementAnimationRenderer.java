package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.rtsbuilding.rtsbuilding.Config;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 放置等待、确认放置和确认破坏动画的 1.12 渲染总入口。
 *
 * <p>三个缓冲区全部由本类独占。此类绝不读取、结束或重置 Minecraft/Tessellator 的共享
 * BufferBuilder，也不结束调用方传入的兼容参数。方块模型使用 1.12 BlockModelRenderer 烘焙到
 * 私有 BLOCK 缓冲区；透明填充和线框分别写入私有 POSITION_COLOR 缓冲区。</p>
 */
public final class PlacementAnimationRenderer {
    private static final BufferBuilder MODEL_BUFFER = new BufferBuilder(2 * 1024 * 1024);
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(512 * 1024);
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(512 * 1024);
    private static final WorldVertexBufferUploader MODEL_UPLOADER = new WorldVertexBufferUploader();
    private static final WorldVertexBufferUploader COLOR_UPLOADER = new WorldVertexBufferUploader();

    private PlacementAnimationRenderer() {
    }

    public static void addPendingBatch(List<BlockPos> positions, IBlockState blockState) {
        PendingGhostRenderer.addPendingBatch(positions, blockState);
    }

    public static void confirmPlacement(BlockPos pos, IBlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderPlaceAnimationLayers()) ConfirmedPlacementRenderer.add(pos, state);
    }

    public static void addDestroy(BlockPos pos, IBlockState state) {
        PendingGhostRenderer.remove(pos);
        if (shouldRenderDestroyLayers()) DestroyGhostRenderer.add(pos, state);
    }

    public static void clearAll() {
        PendingGhostRenderer.clearAll();
        ConfirmedPlacementRenderer.clearAll();
        DestroyGhostRenderer.clearAll();
    }

    /** 使用本类私有缓冲区绘制当前全部动画。 */
    public static void render(Minecraft minecraft) {
        if (minecraft == null || minecraft.world == null) return;
        RenderManager manager = minecraft.getRenderManager();
        double cameraX = manager.viewerPosX;
        double cameraY = manager.viewerPosY;
        double cameraZ = manager.viewerPosZ;
        long now = System.currentTimeMillis();

        boolean previewModel = Config.isPlacementBlockGhostPreviewEnabled();
        boolean placeModel = Config.isPlaceBlockGhostAnimationEnabled();
        boolean destroyModel = Config.isDestroyBlockGhostAnimationEnabled();
        boolean previewLines = Config.isPlacementWireframePreviewEnabled();
        boolean placeLines = Config.isPlaceWireframeAnimationEnabled();
        boolean destroyLines = Config.isDestroyWireframeAnimationEnabled();

        FILL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        FILL_BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            if (previewModel) PendingGhostRenderer.renderModels(minecraft, FILL_BUFFER,
                    cameraX, cameraY, cameraZ, now);
            if (placeModel) ConfirmedPlacementRenderer.renderModels(minecraft, FILL_BUFFER,
                    cameraX, cameraY, cameraZ, now);
            if (destroyModel) DestroyGhostRenderer.renderModels(minecraft, FILL_BUFFER,
                    cameraX, cameraY, cameraZ, now);
            drawColorBuffer(FILL_BUFFER, false);
        } catch (RuntimeException exception) {
            discardBuffer(FILL_BUFFER);
            throw exception;
        } finally {
            FILL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        }

        LINE_BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        LINE_BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            if (previewLines) PendingGhostRenderer.renderWireframes(LINE_BUFFER, now);
            if (placeLines) ConfirmedPlacementRenderer.renderWireframes(LINE_BUFFER, now);
            if (destroyLines) DestroyGhostRenderer.renderWireframes(LINE_BUFFER, now);
            drawColorBuffer(LINE_BUFFER, true);
        } catch (RuntimeException exception) {
            discardBuffer(LINE_BUFFER);
            throw exception;
        } finally {
            LINE_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 迁移期兼容入口。两个参数仅声明调用方仍拥有自己的缓冲区；本方法不会读写或结束它们。
     */
    public static void render(Minecraft minecraft, BufferBuilder callerLineBuffer,
            BufferBuilder callerFillBuffer) {
        render(minecraft);
    }

    static boolean renderBlockModel(Minecraft minecraft, IBlockState state, BlockPos pos,
            float alpha, float scale, double cameraX, double cameraY, double cameraZ) {
        if (minecraft == null || minecraft.world == null || state == null || pos == null) return false;
        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        BlockModelRenderer renderer = dispatcher.getBlockModelRenderer();
        IBakedModel model = dispatcher.getModelForState(state);
        boolean rendered = false;
        boolean closed = false;
        MODEL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        MODEL_BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (!state.getBlock().canRenderInLayer(state, layer)) continue;
                ForgeHooksClient.setRenderLayer(layer);
                rendered |= renderer.renderModel(minecraft.world, model, state, pos,
                        MODEL_BUFFER, false, MathHelper.getPositionRandom(pos));
            }
            if (!rendered || MODEL_BUFFER.getVertexCount() == 0) {
                MODEL_BUFFER.finishDrawing();
                MODEL_BUFFER.reset();
                closed = true;
                return false;
            }
            transformModelVertices(MODEL_BUFFER, pos, scale, cameraX, cameraY, cameraZ, alpha);
            drawModelBuffer(minecraft);
            closed = true;
            return true;
        } finally {
            ForgeHooksClient.setRenderLayer(null);
            MODEL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
            if (!closed) discardBuffer(MODEL_BUFFER);
        }
    }

    /** 将私有模型缓冲区中的位置围绕方块中心缩放，并统一压入动画透明度。 */
    private static void transformModelVertices(BufferBuilder buffer, BlockPos pos, float scale,
            double cameraX, double cameraY, double cameraZ, float alpha) {
        float safeScale = Math.max(0.0F, scale);
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int stride = buffer.getVertexFormat().getSize();
        int colorOffset = buffer.getVertexFormat().getColorOffset();
        float centerX = (float) (pos.getX() + 0.5D - cameraX);
        float centerY = (float) (pos.getY() + 0.5D - cameraY);
        float centerZ = (float) (pos.getZ() + 0.5D - cameraZ);
        ByteBuffer bytes = buffer.getByteBuffer();
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            int offset = vertex * stride;
            float x = bytes.getFloat(offset);
            float y = bytes.getFloat(offset + 4);
            float z = bytes.getFloat(offset + 8);
            bytes.putFloat(offset, centerX + (x - centerX) * safeScale);
            bytes.putFloat(offset + 4, centerY + (y - centerY) * safeScale);
            bytes.putFloat(offset + 8, centerZ + (z - centerZ) * safeScale);
            bytes.put(offset + colorOffset + 3, (byte) alphaByte);
        }
    }

    private static void drawModelBuffer(Minecraft minecraft) {
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        try {
            MODEL_UPLOADER.draw(MODEL_BUFFER);
        } finally {
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.resetColor();
        }
    }

    private static void drawColorBuffer(BufferBuilder buffer, boolean lines) {
        if (buffer.getVertexCount() == 0) {
            buffer.finishDrawing();
            buffer.reset();
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (lines) GlStateManager.glLineWidth(1.5F);
        try {
            COLOR_UPLOADER.draw(buffer);
        } finally {
            if (lines) GlStateManager.glLineWidth(1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.resetColor();
        }
    }

    private static void discardBuffer(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 上传器可能已结束绘制；reset 仍由本类对自己的缓冲区执行。
        }
        buffer.reset();
    }

    static void renderLineBox(BufferBuilder buffer, BlockPos pos, float scale,
            float red, float green, float blue, float alpha) {
        double inset = 0.5D - scale * 0.46D;
        double x1 = pos.getX() + inset, y1 = pos.getY() + inset, z1 = pos.getZ() + inset;
        double x2 = pos.getX() + 1.0D - inset, y2 = pos.getY() + 1.0D - inset;
        double z2 = pos.getZ() + 1.0D - inset;
        line(buffer,x1,y1,z1,x2,y1,z1,red,green,blue,alpha);
        line(buffer,x2,y1,z1,x2,y1,z2,red,green,blue,alpha);
        line(buffer,x2,y1,z2,x1,y1,z2,red,green,blue,alpha);
        line(buffer,x1,y1,z2,x1,y1,z1,red,green,blue,alpha);
        line(buffer,x1,y2,z1,x2,y2,z1,red,green,blue,alpha);
        line(buffer,x2,y2,z1,x2,y2,z2,red,green,blue,alpha);
        line(buffer,x2,y2,z2,x1,y2,z2,red,green,blue,alpha);
        line(buffer,x1,y2,z2,x1,y2,z1,red,green,blue,alpha);
        line(buffer,x1,y1,z1,x1,y2,z1,red,green,blue,alpha);
        line(buffer,x2,y1,z1,x2,y2,z1,red,green,blue,alpha);
        line(buffer,x2,y1,z2,x2,y2,z2,red,green,blue,alpha);
        line(buffer,x1,y1,z2,x1,y2,z2,red,green,blue,alpha);
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1,
            double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        buffer.pos(x1,y1,z1).color(red,green,blue,alpha).endVertex();
        buffer.pos(x2,y2,z2).color(red,green,blue,alpha).endVertex();
    }

    static void renderFilledBox(BufferBuilder buffer, double x1, double y1, double z1,
            double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        quad(buffer,x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,red,green,blue,alpha);
        quad(buffer,x1,y2,z1,x1,y2,z2,x2,y2,z2,x2,y2,z1,red,green,blue,alpha);
        quad(buffer,x1,y1,z1,x1,y2,z1,x2,y2,z1,x2,y1,z1,red,green,blue,alpha);
        quad(buffer,x2,y1,z2,x2,y2,z2,x1,y2,z2,x1,y1,z2,red,green,blue,alpha);
        quad(buffer,x1,y1,z2,x1,y2,z2,x1,y2,z1,x1,y1,z1,red,green,blue,alpha);
        quad(buffer,x2,y1,z1,x2,y2,z1,x2,y2,z2,x2,y1,z2,red,green,blue,alpha);
    }

    private static void quad(BufferBuilder buffer,
            double ax,double ay,double az,double bx,double by,double bz,
            double cx,double cy,double cz,double dx,double dy,double dz,
            float red,float green,float blue,float alpha) {
        buffer.pos(ax,ay,az).color(red,green,blue,alpha).endVertex();
        buffer.pos(bx,by,bz).color(red,green,blue,alpha).endVertex();
        buffer.pos(cx,cy,cz).color(red,green,blue,alpha).endVertex();
        buffer.pos(dx,dy,dz).color(red,green,blue,alpha).endVertex();
    }

    private static boolean shouldRenderPlaceAnimationLayers() {
        return Config.isPlaceBlockGhostAnimationEnabled() || Config.isPlaceWireframeAnimationEnabled();
    }

    private static boolean shouldRenderDestroyLayers() {
        return Config.isDestroyBlockGhostAnimationEnabled() || Config.isDestroyWireframeAnimationEnabled();
    }
}
