package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class SdfRenderer {

    private SdfRenderer() {}

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                        float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = RtsShaders.roundedRect;
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

    public static void drawRoundedRectTopOnly(GuiGraphics g, int x, int y, int w, int h,
                                               float radius, int color, float alpha) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = RtsShaders.roundedRectTop;
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

    public static void drawPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawRoundedRect(g, x, y, w, h, Math.min(w, h) / 2f, color);
    }

    public static void drawPill(GuiGraphics g, int x, int y, int w, int h, int color, float alpha) {
        drawRoundedRect(g, x, y, w, h, Math.min(w, h) / 2f, color, alpha);
    }

    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        int d = radius * 2;
        drawPill(g, cx - radius, cy - radius, d, d, color);
    }

    public static void drawCircle(GuiGraphics g, int cx, int cy, int radius, int color, float alpha) {
        int d = radius * 2;
        drawPill(g, cx - radius, cy - radius, d, d, color, alpha);
    }

    public static void drawRoundedRectTopOnly(GuiGraphics g, int x, int y, int w, int h,
                                               float radius, int color) {
        drawRoundedRectTopOnly(g, x, y, w, h, radius, color, 1f);
    }

    public static void drawChevron(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawChevron(g, x, y, w, h, color, 2f);
    }

    public static void drawChevron(GuiGraphics g, int x, int y, int w, int h, int color, float radius) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = RtsShaders.chevron;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;
        float clampedRadius = Math.min(radius, Math.min(halfW, halfH));

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_P0") != null)
            shader.safeGetUniform("u_P0").set(-halfW * 0.7f, -halfH * 0.7f);
        if (shader.safeGetUniform("u_P1") != null)
            shader.safeGetUniform("u_P1").set(halfW * 0.7f, 0f);
        if (shader.safeGetUniform("u_P2") != null)
            shader.safeGetUniform("u_P2").set(-halfW * 0.7f, halfH * 0.7f);
        if (shader.safeGetUniform("u_Radius") != null)
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
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
    }

    public static void drawBorderedRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int borderColor, int fillColor) {
        drawBorderedRoundedRect(g, x, y, w, h, radius, borderColor, fillColor, 1);
    }

    public static void drawBorderedRoundedRect(GuiGraphics g, int x, int y, int w, int h,
                                                 float radius, int borderColor, int fillColor,
                                                 int borderWidth) {
        if (w <= 0 || h <= 0) return;
        g.flush();
        SdfRenderer.drawRoundedRect(g, x, y, w, h, radius, borderColor);
        g.flush();
        int inset = Math.min(borderWidth, Math.min(w, h) / 2);
        SdfRenderer.drawRoundedRect(g, x + inset, y + inset,
                w - 2 * inset, h - 2 * inset,
                Math.max(0, radius - inset), fillColor);
        g.flush();
    }

    public static void drawResetIcon(GuiGraphics g, int x, int y, int size, int color) {
        if (size <= 0) return;
        g.flush();

        ShaderInstance shader = RtsShaders.resetIcon;
        if (shader == null) return;

        float half = size / 2f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_Size") != null)
            shader.safeGetUniform("u_Size").set(half, half);
        if (shader.safeGetUniform("u_Radius") != null)
            shader.safeGetUniform("u_Radius").set(half * 0.7f);
        if (shader.safeGetUniform("u_Thickness") != null)
            shader.safeGetUniform("u_Thickness").set(half * 0.3f);
        if (shader.safeGetUniform("u_Gap") != null)
            shader.safeGetUniform("u_Gap").set(120f);

        var matrix = g.pose().last().pose();
        var backing = new ByteBufferBuilder(256);
        var builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, x, y + size, 0).setUv(-half, half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + size, y + size, 0).setUv(half, half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x + size, y, 0).setUv(half, -half).setColor(r, gr, b, a);
        builder.addVertex(matrix, x, y, 0).setUv(-half, -half).setColor(r, gr, b, a);

        MeshData data = builder.build();
        if (data != null) BufferUploader.drawWithShader(data);
        backing.close();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        float cx = x + half;
        float cy = y + half;
        float ringRadius = half * 0.7f;
        float arrowSize = half * 0.6f;
        float ax = cx + ringRadius * (float) Math.cos(-2.269f);
        float ay = cy + ringRadius * (float) Math.sin(-2.269f);
        int as = Math.round(arrowSize);
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(140f));
        drawChevron(g, -as / 2, -as / 2, as, as, color, 0.5f);
        g.pose().popPose();
    }

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, float u0, float v0,
                                         float u1, float v1, int color) {
        drawTexturedRect(g, x, y, w, h, texture, u0, v0, u1, v1, color, false);
    }

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, float u0, float v0,
                                         float u1, float v1, int color, boolean mipmap) {
        if (w <= 0 || h <= 0) return;

        g.flush();

        ShaderInstance shader = RtsShaders.textured;
        if (shader == null) return;

        float halfW = w / 2f;
        float halfH = h / 2f;

        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);
        if (mipmap) {
            var abstractTex = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(texture);
            if (abstractTex != null) abstractTex.setFilter(true, true);
        }
        RenderSystem.setShader(() -> shader);

        if (shader.safeGetUniform("u_Size") != null)
            shader.safeGetUniform("u_Size").set(halfW, halfH);
        if (shader.safeGetUniform("u_TexBounds") != null)
            shader.safeGetUniform("u_TexBounds").set(u0, v0, u1, v1);

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

    public static void drawTexturedRect(GuiGraphics g, int x, int y, int w, int h,
                                         ResourceLocation texture, int color) {
        drawTexturedRect(g, x, y, w, h, texture, 0f, 0f, 1f, 1f, color);
    }
}
