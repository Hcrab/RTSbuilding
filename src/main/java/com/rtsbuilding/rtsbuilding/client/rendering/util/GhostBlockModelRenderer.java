package com.rtsbuilding.rtsbuilding.client.rendering.util;

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
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * 使用真实客户端世界和坐标烘焙 1.12 半透明方块模型。
 *
 * <p>本类拥有完整的私有 BLOCK 缓冲，既不借用也不结束 Minecraft 的共享
 * Tessellator。位置缩放和 alpha 修改发生在烘焙后的私有字节缓冲中。</p>
 */
public final class GhostBlockModelRenderer {
    private static final BufferBuilder MODEL_BUFFER = new BufferBuilder(2 * 1024 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private GhostBlockModelRenderer() {
    }

    public static boolean renderAt(Minecraft minecraft, IBlockState state, BlockPos pos, float alpha) {
        return renderAt(minecraft, state, pos, alpha, 1.0F);
    }

    public static boolean renderAt(Minecraft minecraft, IBlockState state, BlockPos pos,
            float alpha, float scale) {
        if (minecraft == null || minecraft.world == null || state == null || pos == null
                || state.getRenderType() != EnumBlockRenderType.MODEL) return false;

        RenderManager manager = minecraft.getRenderManager();
        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        BlockModelRenderer renderer = dispatcher.getBlockModelRenderer();
        IBakedModel model = dispatcher.getModelForState(state);
        boolean rendered = false;
        boolean closed = false;

        MODEL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        MODEL_BUFFER.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (!state.getBlock().canRenderInLayer(state, layer)) continue;
                ForgeHooksClient.setRenderLayer(layer);
                rendered |= renderer.renderModel(minecraft.world, model, state, pos,
                        MODEL_BUFFER, false, MathHelper.getPositionRandom(pos));
            }
            if (!rendered || MODEL_BUFFER.getVertexCount() == 0) {
                discard();
                closed = true;
                return false;
            }

            transformPositions(MODEL_BUFFER, pos, scale,
                    manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ);
            GhostAlphaBufferSource.forceVertexAlpha(MODEL_BUFFER, alpha);
            draw(minecraft);
            closed = true;
            return true;
        } finally {
            ForgeHooksClient.setRenderLayer(null);
            MODEL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
            if (!closed) discard();
        }
    }

    private static void transformPositions(BufferBuilder buffer, BlockPos pos, float scale,
            double cameraX, double cameraY, double cameraZ) {
        float safeScale = Math.max(0.0F, scale);
        if (safeScale == 1.0F) return;
        int stride = buffer.getVertexFormat().getSize();
        ByteBuffer bytes = buffer.getByteBuffer();
        float centerX = (float) (pos.getX() + 0.5D - cameraX);
        float centerY = (float) (pos.getY() + 0.5D - cameraY);
        float centerZ = (float) (pos.getZ() + 0.5D - cameraZ);
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            int offset = vertex * stride;
            float x = bytes.getFloat(offset);
            float y = bytes.getFloat(offset + 4);
            float z = bytes.getFloat(offset + 8);
            bytes.putFloat(offset, centerX + (x - centerX) * safeScale);
            bytes.putFloat(offset + 4, centerY + (y - centerY) * safeScale);
            bytes.putFloat(offset + 8, centerZ + (z - centerZ) * safeScale);
        }
    }

    private static void draw(Minecraft minecraft) {
        GlSnapshot snapshot = GlSnapshot.capture();
        try {
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            UPLOADER.draw(MODEL_BUFFER);
        } finally {
            snapshot.restore();
        }
    }

    private static void discard() {
        try {
            MODEL_BUFFER.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 上传器可能已经结束私有缓冲。
        }
        MODEL_BUFFER.reset();
    }

    private static final class GlSnapshot {
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final float[] color = currentColor();

        private static GlSnapshot capture() { return new GlSnapshot(); }

        private void restore() {
            GlStateManager.tryBlendFuncSeparate(
                    this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
            set(GL11.GL_BLEND, this.blend);
            set(GL11.GL_TEXTURE_2D, this.texture);
            set(GL11.GL_CULL_FACE, this.cull);
            set(GL11.GL_DEPTH_TEST, this.depth);
            GlStateManager.depthMask(this.depthMask);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureBinding);
            GlStateManager.color(this.color[0], this.color[1], this.color[2], this.color[3]);
        }

        private static float[] currentColor() {
            FloatBuffer values = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, values);
            return new float[] {values.get(0), values.get(1), values.get(2), values.get(3)};
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }
}
