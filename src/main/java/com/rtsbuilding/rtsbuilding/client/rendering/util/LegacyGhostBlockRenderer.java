package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.opengl.GL11;

/**
 * 在 1.7.10 中绘制真实方块模型虚影。
 *
 * <p>本类只负责“把一个 Block + metadata 安全地画成半透明模型”，不拥有预览列表、
 * 动画时钟或线框状态。GTNHLib 会把 {@code RenderBlocks} 对全局 Tessellator 的访问
 * 重定向到当前线程的捕获缓冲，然后由本类把一次捕获转成短命 VBO 绘制；因此这里不会结束、
 * 清空或污染 Minecraft 正在使用的共享缓冲。普通 1.7.10 支持以后可以在这一层补一个无
 * GTNHLib 的后端，
 * 上层调用者不需要再次改写。</p>
 */
public final class LegacyGhostBlockRenderer {
    private static boolean loggedFailure;

    private LegacyGhostBlockRenderer() {
    }

    public static boolean renderAt(Minecraft minecraft, BlockState state, BlockPos pos,
            float alpha, float scale) {
        if (minecraft == null || minecraft.theWorld == null || state == null || pos == null
                || state.getBlock() == Blocks.air || scale <= 0.0F) {
            return false;
        }

        Tessellator tessellator = null;
        VertexBuffer vertexBuffer = null;
        boolean capturing = false;
        boolean matrixPushed = false;
        boolean attribPushed = false;
        try {
            // 0.7.10 是 GTNH 2.8.4 的实际 ABI。捕获模式会通过 GTNHLib 的转换器把
            // RenderBlocks 内部读取的 Tessellator.instance 重定向到当前线程缓冲。
            TessellatorManager.startCapturing();
            capturing = true;
            tessellator = TessellatorManager.get();
            tessellator.startDrawingQuads();
            // RenderBlocks 会反复写入不透明顶点色；关闭顶点色后使用 GL 当前色统一控制 alpha。
            tessellator.disableColor();

            GhostBlockAccess access = new GhostBlockAccess(minecraft.theWorld, state, pos);
            RenderBlocks renderer = new RenderBlocks(access);
            Block block = state.getBlock();
            block.setBlockBoundsBasedOnState(access, pos.getX(), pos.getY(), pos.getZ());
            renderer.setRenderBoundsFromBlock(block);

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
            attribPushed = true;
            GL11.glPushMatrix();
            matrixPushed = true;

            RenderManager manager = RenderManager.instance;
            GL11.glTranslated(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
            if (scale != 1.0F) {
                double centerX = pos.getX() + 0.5D;
                double centerY = pos.getY() + 0.5D;
                double centerZ = pos.getZ() + 0.5D;
                GL11.glTranslated(centerX, centerY, centerZ);
                GL11.glScalef(scale, scale, scale);
                GL11.glTranslated(-centerX, -centerY, -centerZ);
            }

            minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, clampAlpha(alpha));

            boolean rendered = renderer.renderBlockByRenderType(
                    block, pos.getX(), pos.getY(), pos.getZ());
            // 只上传位置和纹理坐标，颜色/透明度由下面的 GL 当前色统一控制。
            vertexBuffer = TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION_TEXTURE);
            capturing = false;
            if (rendered) vertexBuffer.render();
            return rendered;
        } catch (Throwable failure) {
            if (!loggedFailure) {
                loggedFailure = true;
                RtsbuildingMod.LOGGER.warn(
                        "GTNH 方块虚影模型渲染失败；本次预览将退回线框/几何填充", failure);
            }
            return false;
        } finally {
            if (matrixPushed) GL11.glPopMatrix();
            if (attribPushed) GL11.glPopAttrib();
            GL11.glDepthMask(true);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (capturing) {
                try {
                    VertexBuffer abandoned = TessellatorManager.stopCapturingToVBO(
                            DefaultVertexFormat.POSITION_TEXTURE);
                    abandoned.close();
                } catch (Throwable ignored) {
                    TessellatorManager.cleanup();
                }
            }
            if (vertexBuffer != null) vertexBuffer.close();
        }
    }

    private static float clampAlpha(float alpha) {
        return Math.max(0.0F, Math.min(1.0F, alpha));
    }

    /** 只在目标坐标覆盖预览方块，其余查询继续委托真实世界。 */
    private static final class GhostBlockAccess implements IBlockAccess {
        private final World world;
        private final BlockState state;
        private final BlockPos pos;

        private GhostBlockAccess(World world, BlockState state, BlockPos pos) {
            this.world = world;
            this.state = state;
            this.pos = pos;
        }

        private boolean isTarget(int x, int y, int z) {
            return x == pos.getX() && y == pos.getY() && z == pos.getZ();
        }

        @Override public Block getBlock(int x, int y, int z) {
            return isTarget(x, y, z) ? state.getBlock() : world.getBlock(x, y, z);
        }
        @Override public TileEntity getTileEntity(int x, int y, int z) {
            return isTarget(x, y, z) ? null : world.getTileEntity(x, y, z);
        }
        @Override public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
            return world.getLightBrightnessForSkyBlocks(x, y, z, minimum);
        }
        @Override public int getBlockMetadata(int x, int y, int z) {
            return isTarget(x, y, z) ? state.getMetadata() : world.getBlockMetadata(x, y, z);
        }
        @Override public int isBlockProvidingPowerTo(int x, int y, int z, int direction) {
            return world.isBlockProvidingPowerTo(x, y, z, direction);
        }
        @Override public boolean isAirBlock(int x, int y, int z) {
            return getBlock(x, y, z).isAir(this, x, y, z);
        }
        @Override public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return world.getBiomeGenForCoords(x, z);
        }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public boolean extendedLevelsInChunkCache() { return false; }
        @Override public boolean isSideSolid(int x, int y, int z, ForgeDirection side,
                boolean defaultValue) {
            return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
        }
    }
}
