package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceTiler;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public final class SpriteRenderer {

    

    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_1.png");
    private static final ResourceLocation DRAG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_6.png");
    private static final ResourceLocation FLOATING_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");

    
    private static final int PANEL_TEX_W = 32;
    private static final int PANEL_TEX_FILE_H = 32;
    private static final int PANEL_TEX_STATE_H = 16;
    private static final int PANEL_TEX_HOVER_V_OFFSET = 16;
    private static final int PANEL_BORDER = 4;

    
    private static final int FLOATING_TEX_W = 32;
    private static final int FLOATING_TEX_FILE_H = 48;
    private static final int FLOATING_STATE_H = 16;
    private static final int FLOATING_TEX_HOVER_V_OFFSET = 16;
    private static final int FLOATING_BORDER = 2;

    
    private static final int DRAG_TEX_FILE_H = 32;

    private static final TextureInfo PANEL_TEX_INFO = new TextureInfo(
            PANEL_TEXTURE, PANEL_TEX_W, PANEL_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final TextureInfo FLOATING_TEX_INFO = new TextureInfo(
            FLOATING_UI_TEXTURE, FLOATING_TEX_W, FLOATING_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final TextureInfo DRAG_TEX_INFO = new TextureInfo(
            DRAG_TEXTURE, PANEL_TEX_W, DRAG_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);

    private static final NineSliceRegion PANEL_NINE_SLICE = NineSliceRegion.fullTheme(
            PANEL_TEX_INFO, PANEL_TEX_STATE_H, PANEL_BORDER);
    private static final NineSliceRegion FLOATING_NINE_SLICE = NineSliceRegion.fullTheme(
            FLOATING_TEX_INFO, FLOATING_STATE_H, FLOATING_BORDER);
    private static final NineSliceRegion DRAG_NINE_SLICE = NineSliceRegion.fullTheme(
            DRAG_TEX_INFO, PANEL_TEX_STATE_H, PANEL_BORDER);

    private SpriteRenderer() {}

    

    
    public static int getThemeOffset(SpriteRegion region) {
        return switch (region.texture().themeLayout()) {
            case HORIZONTAL_PAIR ->
                    ThemeManager.getInstance().isLightMode() ? region.texture().halfWidth() : 0;
            case NONE -> 0;
        };
    }

    
    public static int getNineSliceThemeOffset(NineSliceRegion spec) {
        return getThemeOffset(spec.region());
    }

    

    
    public static void drawSprite(GuiGraphics g, SpriteRegion region,
                                   int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        float u0 = (float) region.u() / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
    }

    
    public static void drawSprite(GuiGraphics g, SpriteRegion region, int themeOffset,
                                   int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();
        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;
        buffer.addVertex(matrix, dstX, dstY + dstH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY + dstH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX + dstW, dstY, 0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
        buffer.addVertex(matrix, dstX, dstY, 0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
    }

    
    private static void drawSpriteImmediate(GuiGraphics g, SpriteRegion region, int themeOffset,
                                             int dstX, int dstY, int dstW, int dstH) {
        FilterState.getInstance().apply(region.texture());
        g.blit(region.texture().location(), dstX, dstY, dstW, dstH,
                region.u() + themeOffset, region.v(),
                region.regionWidth(), region.regionHeight(),
                region.texture().fullWidth(), region.texture().fullHeight());
    }

    

    
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec,
                                      int dstX, int dstY, int dstW, int dstH) {
        drawNineSlice(g, spec, 0, dstX, dstY, dstW, dstH);
    }

    
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec, int themeOffset,
                                      int dstX, int dstY, int dstW, int dstH) {
        SpriteRegion r = spec.region();
        drawNineSliceRaw(g, r.texture(),
                r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), spec.border(),
                dstX, dstY, dstW, dstH);
    }

    

    
    public static void drawNineSlicePanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int vOffset = hovered ? PANEL_TEX_HOVER_V_OFFSET : 0;
        SpriteRegion r = PANEL_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        int u = r.u() + themeOffset;
        int v = r.v() + vOffset;
        drawNineSliceRaw(g, r.texture(), u, v, r.regionWidth(), r.regionHeight(),
                PANEL_NINE_SLICE.border(), x, y, w, h);
    }

    
    public static void drawNineSliceDragPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        SpriteRegion r = DRAG_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        drawNineSliceRaw(g, r.texture(), r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), DRAG_NINE_SLICE.border(), x, y, w, h);
    }

    
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int vOffset = hovered ? FLOATING_TEX_HOVER_V_OFFSET : 0;
        SpriteRegion r = FLOATING_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        drawNineSliceRaw(g, r.texture(), r.u() + themeOffset, r.v() + vOffset,
                r.regionWidth(), r.regionHeight(), FLOATING_NINE_SLICE.border(), x, y, w, h);
    }

    
    private static void drawNineSliceRaw(GuiGraphics g, TextureInfo texInfo,
                                          int u, int v, int regionW, int regionH, int border,
                                          int dstX, int dstY, int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) return;

        ResourceLocation texture = texInfo.location();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();

        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        VertexConsumer buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        NineSliceTiler.forEachTile(
                u, v, regionW, regionH, border,
                dstX, dstY, dstW, dstH,
                (sx, sy, sw, sh, dx, dy, dw, dh) -> {
                    float u0 = (float) sx / texW;
                    float v0 = (float) sy / texH;
                    float u1 = (float) (sx + sw) / texW;
                    float v1 = (float) (sy + sh) / texH;
                    buffer.addVertex(matrix, dx,     dy + dh, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
                    buffer.addVertex(matrix, dx + dw, dy + dh, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
                    buffer.addVertex(matrix, dx + dw, dy,      0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
                    buffer.addVertex(matrix, dx,     dy,      0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
                });
    }

    

    
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        if (cols <= 0 || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) region.u() / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        for (int col = 0; col < cols; col++) {
            int dx = dstX + col * tileW;
            buffer.addVertex(matrix, dx,     dstY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY,       0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx,     dstY,       0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
        }
    }

    

    
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region, int themeOffset,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        drawTiledRowRange(g, region, themeOffset, dstX, dstY, tileW, tileH, 0, cols - 1);
    }

    

    
    public static void drawTiledGrid(GuiGraphics g, SpriteRegion region, int themeOffset,
                                      int originX, int originY,
                                      int tileW, int tileH, int gap,
                                      int cols, int rows,
                                      int scroll, int clipTop, int clipBottom) {
        if (cols <= 0 || rows <= 0 || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        int stride = tileH + gap;
        for (int row = 0; row < rows; row++) {
            int rowY = originY + row * stride - scroll;
            if (rowY + tileH <= clipTop || rowY >= clipBottom) continue;
            for (int col = 0; col < cols; col++) {
                int dx = originX + col * (tileW + gap);
                buffer.addVertex(matrix, dx,         rowY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx + tileW, rowY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx + tileW, rowY,         0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
                buffer.addVertex(matrix, dx,         rowY,         0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    
    private static void drawTiledRowRange(GuiGraphics g, SpriteRegion region, int themeOffset,
                                           int dstX, int dstY, int tileW, int tileH,
                                           int startCol, int endCol) {
        if (startCol > endCol || tileW <= 0 || tileH <= 0) return;
        var texture = region.texture().location();
        var texInfo = region.texture();
        int texW = texInfo.fullWidth();
        int texH = texInfo.fullHeight();
        var renderType = GuiRenderTypes.fromTextureInfo(texture, texInfo.filterMode());
        var buffer = g.bufferSource().getBuffer(renderType);
        var matrix = g.pose().last().pose();

        float u0 = (float) (region.u() + themeOffset) / texW;
        float v0 = (float) region.v() / texH;
        float u1 = (float) (region.u() + themeOffset + region.regionWidth()) / texW;
        float v1 = (float) (region.v() + region.regionHeight()) / texH;

        for (int col = startCol; col <= endCol; col++) {
            int dx = dstX + col * tileW;
            buffer.addVertex(matrix, dx,         dstY + tileH, 0).setUv(u0, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY + tileH, 0).setUv(u1, v1).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx + tileW, dstY,         0).setUv(u1, v0).setColor(1f, 1f, 1f, 1f);
            buffer.addVertex(matrix, dx,         dstY,         0).setUv(u0, v0).setColor(1f, 1f, 1f, 1f);
        }
    }

    

    
    public static void drawStateSprite(GuiGraphics g,
                                        SpriteRegion normal, SpriteRegion hovered, SpriteRegion selected,
                                        boolean isSelected, float hoverT,
                                        int dstX, int dstY, int dstW, int dstH) {
        
        int themeOffset = getThemeOffset(normal);
        if (isSelected) {
            drawSprite(g, selected, themeOffset, dstX, dstY, dstW, dstH);
            return;
        }
        CrossFadeRenderer.render(g, hoverT,
                () -> drawSpriteImmediate(g, normal, themeOffset, dstX, dstY, dstW, dstH),
                () -> drawSpriteImmediate(g, hovered, themeOffset, dstX, dstY, dstW, dstH));
    }
}
