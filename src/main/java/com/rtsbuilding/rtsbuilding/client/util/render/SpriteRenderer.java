package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceTiler;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 精灵图渲染器——统一管理精灵图、九宫格、面板背景和按钮状态渲染。
 *
 * <p>本类集中管理所有与精灵图相关的渲染逻辑，调用方无需关心 OpenGL 状态管理。
 * 渲染相关的资源规格配置（如面板贴图常量）也收归此处，避免散落各处。</p>
 *
 * <p><b>面板快捷方法：</b></p>
 * <ul>
 *   <li>{@link #drawNineSlicePanel} — 面板背景（含悬浮高亮）</li>
 *   <li>{@link #drawNineSliceDragPanel} — 拖拽标题栏背景</li>
 *   <li>{@link #drawNineSliceFloatingPanel} — 浮窗/提示框背景（含悬浮高亮）</li>
 * </ul>
 *
 * <p><b>性能优化：</b>九宫格渲染使用 VertexConsumer 批量提交所有瓷砖到单一 draw call，
 * 替代逐 tile 调用 {@code g.blit()} 的旧方案。纹理过滤和混合状态由
 * {@link GuiRenderTypes} 缓存的 {@link net.minecraft.client.renderer.RenderType} 管理。</p>
 */
public final class SpriteRenderer {

    // ======================== 面板贴图常量 ========================

    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_1.png");
    private static final ResourceLocation DRAG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_6.png");
    private static final ResourceLocation FLOATING_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");

    /** 面板背景贴图（32×32，水平双主题，2状态，像素过滤） */
    private static final int PANEL_TEX_W = 32;
    private static final int PANEL_TEX_FILE_H = 32;
    private static final int PANEL_TEX_STATE_H = 16;
    private static final int PANEL_TEX_HOVER_V_OFFSET = 16;
    private static final int PANEL_BORDER = 4;

    /** 浮窗背景贴图（32×48，水平双主题，2状态，像素过滤） */
    private static final int FLOATING_TEX_W = 32;
    private static final int FLOATING_TEX_FILE_H = 48;
    private static final int FLOATING_STATE_H = 16;
    private static final int FLOATING_TEX_HOVER_V_OFFSET = 16;
    private static final int FLOATING_BORDER = 2;

    /** 拖拽栏贴图 */
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

    // ======================== 主题偏移计算（避免 withTheme() 创建中间对象）====================

    /**
     * 计算精灵区域在当前主题下的水平偏移量（像素）。
     * 主题不会在单帧内变化，调用方只需在渲染开头计算一次即可复用。
     */
    public static int getThemeOffset(SpriteRegion region) {
        return switch (region.texture().themeLayout()) {
            case HORIZONTAL_PAIR ->
                    ThemeManager.getInstance().isLightMode() ? region.texture().halfWidth() : 0;
            case NONE -> 0;
        };
    }

    /**
     * 计算九宫格在当前主题下的水平偏移量（像素）。
     */
    public static int getNineSliceThemeOffset(NineSliceRegion spec) {
        return getThemeOffset(spec.region());
    }

    // ======================== 单精灵图绘制 ========================

    /**
     * 绘制精灵图——使用 VertexConsumer 批量提交，单次 draw call 完成。
     * <p>如需主题偏移，调用方需自行通过 {@link SpriteRegion#withTheme()} 预转换，
     * 或使用 {@link #drawStateSprite} 等集成方法。</p>
     */
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

    /**
     * 绘制精灵图（带显式主题偏移）——使用 VertexConsumer 批量提交。
     * <p>相比 {@link #drawSprite(GuiGraphics, SpriteRegion, int, int, int, int)},
     * 此方法接受预计算的主题偏移，避免 {@link SpriteRegion#withTheme()} 的对象分配。</p>
     *
     * @param themeOffset 主题水平偏移量（像素），通过 {@link #getThemeOffset} 计算
     */
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

    /**
     * 即时模式绘制精灵图——直接使用 {@code g.blit()} 渲染，不经过批处理。
     * <p>仅用于 {@link #drawStateSprite} 的交叉淡入淡出路径（需要每 pass 独立提交以保证 blend 正确）。</p>
     */
    private static void drawSpriteImmediate(GuiGraphics g, SpriteRegion region, int themeOffset,
                                             int dstX, int dstY, int dstW, int dstH) {
        FilterState.getInstance().apply(region.texture());
        g.blit(region.texture().location(), dstX, dstY, dstW, dstH,
                region.u() + themeOffset, region.v(),
                region.regionWidth(), region.regionHeight(),
                region.texture().fullWidth(), region.texture().fullHeight());
    }

    // ======================== 九宫格渲染 ========================

    /**
     * 绘制九宫格精灵图——使用 VertexConsumer 批量提交。
     * <p>不自动应用主题偏移。如需主题适配，调用方需通过
     * {@link NineSliceRegion#withTheme()} 预先处理。</p>
     */
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec,
                                      int dstX, int dstY, int dstW, int dstH) {
        drawNineSlice(g, spec, 0, dstX, dstY, dstW, dstH);
    }

    /**
     * 绘制九宫格（带显式主题偏移）——避免调用 {@link NineSliceRegion#withTheme()} 创建中间对象。
     *
     * @param themeOffset 主题水平偏移量（像素），通过 {@link #getNineSliceThemeOffset} 计算
     */
    public static void drawNineSlice(GuiGraphics g, NineSliceRegion spec, int themeOffset,
                                      int dstX, int dstY, int dstW, int dstH) {
        SpriteRegion r = spec.region();
        drawNineSliceRaw(g, r.texture(),
                r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), spec.border(),
                dstX, dstY, dstW, dstH);
    }

    // ======================== 面板快捷方法 ========================

    /** 面板背景九宫格（支持悬浮高亮）——零中间对象分配 */
    public static void drawNineSlicePanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int vOffset = hovered ? PANEL_TEX_HOVER_V_OFFSET : 0;
        SpriteRegion r = PANEL_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        int u = r.u() + themeOffset;
        int v = r.v() + vOffset;
        drawNineSliceRaw(g, r.texture(), u, v, r.regionWidth(), r.regionHeight(),
                PANEL_NINE_SLICE.border(), x, y, w, h);
    }

    /** 拖拽标题栏九宫格背景——零中间对象分配 */
    public static void drawNineSliceDragPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        SpriteRegion r = DRAG_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        drawNineSliceRaw(g, r.texture(), r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), DRAG_NINE_SLICE.border(), x, y, w, h);
    }

    /** 浮窗/悬浮提示九宫格背景（支持悬浮高亮）——零中间对象分配 */
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        int vOffset = hovered ? FLOATING_TEX_HOVER_V_OFFSET : 0;
        SpriteRegion r = FLOATING_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        drawNineSliceRaw(g, r.texture(), r.u() + themeOffset, r.v() + vOffset,
                r.regionWidth(), r.regionHeight(), FLOATING_NINE_SLICE.border(), x, y, w, h);
    }

    /**
     * 九宫格原始渲染——使用 VertexConsumer 批量提交所有瓷砖，单次 draw call 完成。
     * <p>替代逐 tile 调用 {@code g.blit()} 的旧方案，消除 N 次 draw call 开销。</p>
     */
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

    // ======================== 行平铺渲染（替代逐格绘制网格背景）=======================

    /**
     * 绘制平铺行——在水平方向重复精灵 cols 次，单次 batch 提交。
     * 源精灵图在 dstW 宽度内重复绘制，每 tileW 像素重复一次 UV。
     * 替代逐格调用 drawSprite，将 CPU 循环次数从 cols 减少到 1，
     * 同时消除 per-cell 取模/除法开销。
     */
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

    // ======================== 行平铺渲染（带主题偏移）=======================

    /**
     * 绘制平铺行（带显式主题偏移）——避免调用 {@link SpriteRegion#withTheme()} 创建中间对象。
     * 在水平方向重复精灵 cols 次，单次 batch 提交。
     *
     * @param themeOffset 主题水平偏移量（像素），通过 {@link #getThemeOffset} 计算
     */
    public static void drawTiledRow(GuiGraphics g, SpriteRegion region, int themeOffset,
                                     int dstX, int dstY, int tileW, int tileH, int cols) {
        drawTiledRowRange(g, region, themeOffset, dstX, dstY, tileW, tileH, 0, cols - 1);
    }

    // ======================== 网格平铺渲染（单次 batch 提交全部）=======================

    /**
     * 批量绘制网格背景——单次调用完成整个网格的平铺渲染，将 {@code getBuffer} 调用从 N 行减少到 1 次。
     * <p>使用 {@link GuiRenderTypes} 直接创建 {@link RenderType}，支持滚动偏移和裁剪边界检测。</p>
     *
     * @param region         源精灵区域（未应用主题偏移）
     * @param themeOffset    主题水平偏移量，通过 {@link #getThemeOffset} 计算
     * @param originX        网格左上角 X
     * @param originY        网格左上角 Y
     * @param tileW          每个格子的宽度
     * @param tileH          每个格子的高度
     * @param gap            格子间距
     * @param cols           列数
     * @param rows           行数
     * @param scroll         滚动偏移量
     * @param clipTop        裁剪区域上边界（含）
     * @param clipBottom     裁剪区域下边界（不含）
     */
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

    /**
     * 绘制平铺列范围——在水平方向从 startCol 到 endCol 重复精灵 cols 次，单次 batch 提交。
     * 供 {@link #drawTiledRow} 和 {@link #drawTiledGrid} 内部使用。
     */
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

    // ======================== 三段式按钮状态渲染 ========================

    /**
     * 渲染三段式按钮状态（正常/悬浮/选中），自动应用主题偏移。
     * <p>主题偏移在内部直接计算，不创建中间 {@link SpriteRegion} 对象。
     * 相比外部调用 {@code normal.withTheme()} 再传给此方法，
     * 可减少每帧每按钮 3 个 SpriteRegion 分配。</p>
     */
    public static void drawStateSprite(GuiGraphics g,
                                        SpriteRegion normal, SpriteRegion hovered, SpriteRegion selected,
                                        boolean isSelected, float hoverT,
                                        int dstX, int dstY, int dstW, int dstH) {
        // 一次性计算主题偏移，避免三条渲染路径各自调用 withTheme() 创建中间对象
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
