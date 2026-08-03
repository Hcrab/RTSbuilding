package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 使用 1.12 烘焙方块模型管线绘制半透明蓝图虚影。
 *
 * <p>本类拥有独立的 {@link BufferBuilder}，不会 begin、finish 或清空 Minecraft/Tessellator
 * 的共享缓冲。蓝图数据目前只携带方块状态而不携带 TileEntity NBT，因此动态 TileEntity
 * 渲染明确不在此入口伪造；普通方块的 {@link IBakedModel} 仍完整保留。</p>
 */
public final class BlueprintGhostBlockModelRenderer {
    public static final float GHOST_ALPHA = 0.30F;

    private static final int INITIAL_BUFFER_BYTES = 2 * 1024 * 1024;
    private static final BufferBuilder MODEL_BUFFER = new BufferBuilder(INITIAL_BUFFER_BYTES);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private BlueprintGhostBlockModelRenderer() {
    }

    public static boolean renderModels(Minecraft minecraft, List<BlueprintGhostBlock> blocks,
            double cameraX, double cameraY, double cameraZ,
            int[] outMinX, int[] outMinY, int[] outMinZ,
            int[] outMaxX, int[] outMaxY, int[] outMaxZ) {
        if (minecraft == null || minecraft.world == null || blocks == null || blocks.isEmpty()) {
            return false;
        }

        updateBounds(blocks, outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);

        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        BlockModelRenderer modelRenderer = dispatcher.getBlockModelRenderer();
        boolean rendered = false;
        boolean bufferClosed = false;
        MODEL_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        MODEL_BUFFER.setTranslation(-cameraX, -cameraY, -cameraZ);
        try {
            for (BlueprintGhostBlock block : blocks) {
                if (!isModelBlock(block)) {
                    continue;
                }
                IBlockState state = block.state();
                BlockPos pos = block.pos();
                IBakedModel model = dispatcher.getModelForState(state);
                for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                    if (!state.getBlock().canRenderInLayer(state, layer)) {
                        continue;
                    }
                    ForgeHooksClient.setRenderLayer(layer);
                    rendered |= modelRenderer.renderModel(minecraft.world, model, state, pos,
                            MODEL_BUFFER, false, MathHelper.getPositionRandom(pos));
                }
            }

            if (!rendered || MODEL_BUFFER.getVertexCount() == 0) {
                MODEL_BUFFER.finishDrawing();
                MODEL_BUFFER.reset();
                bufferClosed = true;
                return false;
            }

            forceVertexAlpha(MODEL_BUFFER, GHOST_ALPHA);
            drawModelBuffer(minecraft);
            bufferClosed = true;
            return true;
        } finally {
            ForgeHooksClient.setRenderLayer(null);
            MODEL_BUFFER.setTranslation(0.0D, 0.0D, 0.0D);
            if (!bufferClosed) {
                discardModelBuffer();
            }
        }
    }

    public static boolean renderModels(Minecraft minecraft, List<BlueprintGhostBlock> blocks,
            double cameraX, double cameraY, double cameraZ) {
        int[] minX = {Integer.MAX_VALUE};
        int[] minY = {Integer.MAX_VALUE};
        int[] minZ = {Integer.MAX_VALUE};
        int[] maxX = {Integer.MIN_VALUE};
        int[] maxY = {Integer.MIN_VALUE};
        int[] maxZ = {Integer.MIN_VALUE};
        return renderModels(minecraft, blocks, cameraX, cameraY, cameraZ,
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void updateBounds(List<BlueprintGhostBlock> blocks,
            int[] minX, int[] minY, int[] minZ, int[] maxX, int[] maxY, int[] maxZ) {
        for (BlueprintGhostBlock block : blocks) {
            if (block == null || block.pos() == null) {
                continue;
            }
            BlockPos pos = block.pos();
            minX[0] = Math.min(minX[0], pos.getX());
            minY[0] = Math.min(minY[0], pos.getY());
            minZ[0] = Math.min(minZ[0], pos.getZ());
            maxX[0] = Math.max(maxX[0], pos.getX() + 1);
            maxY[0] = Math.max(maxY[0], pos.getY() + 1);
            maxZ[0] = Math.max(maxZ[0], pos.getZ() + 1);
        }
    }

    private static boolean isModelBlock(BlueprintGhostBlock block) {
        if (block == null || block.missing() || block.pos() == null || block.state() == null) {
            return false;
        }
        IBlockState state = block.state();
        return state.getRenderType() == EnumBlockRenderType.MODEL;
    }

    /** 将模型写入的原始顶点颜色 alpha 统一压到虚影透明度，同时保留模型自身 RGB。 */
    private static void forceVertexAlpha(BufferBuilder buffer, float alpha) {
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        int stride = buffer.getVertexFormat().getSize();
        int colorOffset = buffer.getVertexFormat().getColorOffset();
        ByteBuffer bytes = buffer.getByteBuffer();
        for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
            bytes.put(vertex * stride + colorOffset + 3, (byte) alphaByte);
        }
    }

    private static void drawModelBuffer(Minecraft minecraft) {
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        try {
            RtsOwnedBufferUploader.draw(MODEL_BUFFER);
        } finally {
            GlStateManager.enableCull();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.resetColor();
        }
    }

    private static void discardModelBuffer() {
        try {
            MODEL_BUFFER.finishDrawing();
        } catch (IllegalStateException ignored) {
            // 上传器可能已经 finish，只是尚未来得及 reset。
        }
        MODEL_BUFFER.reset();
    }
}
