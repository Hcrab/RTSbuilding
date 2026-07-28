package com.uiexperiment.uiexperiment.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.uiexperiment.uiexperiment.UIExperimentMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

public final class SdfRenderer {

    private SdfRenderer() {}

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                        float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        // flush pending GUI draws
        g.flush();

        ShaderInstance shader = UIExperimentMod.roundedRectShader;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));
        float cx = x + halfW;
        float cy = y + halfH;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        shader.safeGetUniform("u_Size");
        shader.safeGetUniform("u_Size").set(halfW, halfH);
        shader.safeGetUniform("u_Radius");
        shader.safeGetUniform("u_Radius").set(clampedRadius);

        var matrix = g.pose().last().pose();

        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) {
            BufferUploader.drawWithShader(data);
        }
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                        float radius, int color) {
        drawRoundedRect(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawTriangle(GuiGraphics g, int x, int y, int w, int h,
                                     int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = UIExperimentMod.triangleShader;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f * alpha;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        // equilateral triangle vertices in UV space (centered)
        float h3 = 0.57735f; // height/2 for equilateral with side ~1.1547
        if (shader.safeGetUniform("u_TriA") != null)
            shader.safeGetUniform("u_TriA").set(-halfW * 0.85f, halfH * 0.7f);
        if (shader.safeGetUniform("u_TriB") != null)
            shader.safeGetUniform("u_TriB").set(halfW * 0.85f, halfH * 0.7f);
        if (shader.safeGetUniform("u_TriC") != null)
            shader.safeGetUniform("u_TriC").set(0f, -halfH * 0.7f);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + h, 0).setUv(-halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y + h, 0).setUv(halfW, halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + w, y, 0).setUv(halfW, -halfH).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-halfW, -halfH).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawTriangle(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawTriangle(g, x, y, w, h, color, 1f);
    }

    public static void drawBorderedRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                float radius, int fillColor, int borderColor,
                                                float borderWidth) {
        drawRoundedRect(g, x, y, w, h, radius, fillColor);
        drawRoundedRect(g, x, y, w, h, radius, borderColor, borderWidth);
    }
}
