package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 把一棵完整浮窗组件树先画入私有缓冲，再以统一透明度合成回主画面。
 *
 * <p>它只在窗口进入/退出动画期间工作；稳定帧仍走原有直接绘制路径。这样文字、纯色矩形、
 * 贴图和物品不会各自解释透明度，也不会结束 Minecraft 的共享缓冲。缓冲在所有浮窗之间串行
 * 复用，不持有窗口状态，不参与命中与生命周期判断。</p>
 */
final class RtsWindowSubtreeCompositor {
    private static TextureTarget target;

    static void render(GuiGraphics graphics, int logicalWidth, int logicalHeight,
                       double opacity, Runnable subtreeRenderer) {
        double clampedOpacity = Math.max(0.0D, Math.min(1.0D, opacity));
        if (clampedOpacity >= 0.999D) {
            subtreeRenderer.run();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureTarget(mainTarget.width, mainTarget.height);
        graphics.flush();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        try {
            subtreeRenderer.run();
            graphics.flush();
        } finally {
            mainTarget.bindWrite(true);
        }
        composite(graphics, logicalWidth, logicalHeight, (float) clampedOpacity);
    }

    private static void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_NEAREST);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_NEAREST);
        }
    }

    private static void composite(GuiGraphics graphics, int width, int height, float opacity) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());

        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = graphics.pose().last().pose();
        int alpha = Math.round(opacity * 255.0F);
        builder.addVertex(matrix, 0.0F, height, 0.0F)
                .setUv(0.0F, 0.0F).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, width, height, 0.0F)
                .setUv(1.0F, 0.0F).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, width, 0.0F, 0.0F)
                .setUv(1.0F, 1.0F).setColor(255, 255, 255, alpha);
        builder.addVertex(matrix, 0.0F, 0.0F, 0.0F)
                .setUv(0.0F, 1.0F).setColor(255, 255, 255, alpha);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private RtsWindowSubtreeCompositor() {
    }
}
