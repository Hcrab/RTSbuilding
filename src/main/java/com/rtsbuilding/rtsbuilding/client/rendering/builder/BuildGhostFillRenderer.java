package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** 1.12 建造幽灵的私有半透明填充层。 */
public final class BuildGhostFillRenderer {
    private static final BufferBuilder BUFFER = new BufferBuilder(256 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private BuildGhostFillRenderer() {}

    public static void renderFill(List<BlockPos> blocks, BufferBuilder callerBuffer, boolean readyConfirm) {
        if (blocks == null || blocks.isEmpty()) return;
        RenderManager manager = net.minecraft.client.renderer.entity.RenderManager.instance;
        BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        BUFFER.setTranslation(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        float r = readyConfirm ? 0.24F : 0.16F;
        float g = readyConfirm ? 0.72F : 0.55F;
        float b = readyConfirm ? 0.24F : 0.90F;
        float a = readyConfirm ? 0.22F : 0.16F;
        for (BlockPos pos : blocks) com.rtsbuilding.rtsbuilding.client.rendering.util.LegacyRenderGeometry.addChainedFilledBoxVertices(BUFFER,
                pos.getX()+.03D,pos.getY()+.03D,pos.getZ()+.03D,
                pos.getX()+.97D,pos.getY()+.97D,pos.getZ()+.97D,r,g,b,a);
        UltimineGhostRenderer.GlSnapshot gl=UltimineGhostRenderer.GlSnapshot.capture(); try {
            GlStateManager.enableBlend(); GlStateManager.disableTexture2D(); GlStateManager.disableCull();
            GlStateManager.depthMask(false); RtsOwnedBufferUploader.draw(BUFFER);
        } finally {
            BUFFER.setTranslation(0,0,0); gl.restore();
        }
    }
}
