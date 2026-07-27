package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** 1.12 建造幽灵私有线框层；兼容参数绝不读取或结束。 */
public final class BuildGhostWireframeRenderer {
    private static final BufferBuilder BUFFER = new BufferBuilder(256*1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();
    private BuildGhostWireframeRenderer() {}
    public static void renderWireframes(List<BlockPos> blocks, BufferBuilder callerBuffer, boolean readyConfirm) {
        if (blocks==null||blocks.isEmpty()) return;
        RenderManager m=Minecraft.getMinecraft().getRenderManager();
        BUFFER.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        BUFFER.setTranslation(-m.viewerPosX,-m.viewerPosY,-m.viewerPosZ);
        for(BlockPos p:blocks) RenderGlobal.drawBoundingBox(BUFFER,p.getX()+.03,p.getY()+.03,p.getZ()+.03,
                p.getX()+.97,p.getY()+.97,p.getZ()+.97,.30F,.75F,1F,.70F);
        UltimineGhostRenderer.GlSnapshot gl=UltimineGhostRenderer.GlSnapshot.capture();try { GlStateManager.enableBlend(); GlStateManager.disableTexture2D(); GlStateManager.depthMask(false); UPLOADER.draw(BUFFER); }
        finally { BUFFER.setTranslation(0,0,0); gl.restore(); }
    }
}
