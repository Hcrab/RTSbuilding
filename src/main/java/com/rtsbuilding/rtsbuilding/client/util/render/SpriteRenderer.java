package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.util.*;
import net.minecraft.client.gui.GuiGraphics;
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
 *   <li>{@link #drawNineSliceFloatingPanel} — 浮窗/提示框背景</li>
 * </ul>
 *
 * <p>每帧渲染流程：</p>
 * <ol>
 *   <li>通过 {@link FilterState} 自动去重设置 OpenGL 过滤参数</li>
 *   <li>通过 {@link BlendScope} 确保 blend 状态正确配对</li>
 *   <li>通过 {@link NineSliceTiler} 计算九宫格拼贴坐标</li>
 * </ol>
 */
public final class SpriteRenderer {

    // ======================== 面板贴图常量 ========================

    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/ui.png");
    private static final ResourceLocation DRAG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/drag_ui.png");
    private static final ResourceLocation FLOATING_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/floating_ui.png");

    /** 面板背景贴图（32×32，水平双主题，2状态，像素过滤） */
    private static final int PANEL_TEX_W = 32;
    private static final int PANEL_TEX_FILE_H = 32;
    private static final int PANEL_TEX_STATE_H = 16;
    private static final int PANEL_TEX_HOVER_V_OFFSET = 16;
    private static final int PANEL_BORDER = 4;

    /** 浮窗背景贴图（32×16，水平双主题，像素过滤） */
    private static final int FLOATING_TEX_W = 32;
    private static final int FLOATING_TEX_FILE_H = 16;
    private static final int FLOATING_STATE_H = 16;
    private static final int FLOATING_BORDER = 2;

    /** 拖拽栏贴图 */
    private static final int DRAG_TEX_FILE_H = 16;

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
    private static int getThemeOffset(SpriteRegion region) {
        return switch (region.texture().themeLayout()) {
            case HORIZONTAL_PAIR ->
                    ThemeManager.getInstance().isLightMode() ? region.texture().halfWidth() : 0;
            case NONE -> 0;
        };
    }

    /**
     * 计算九宫格在当前主题下的水平偏移量（像素）。
     */
    private static int getNineSliceThemeOffset(NineSliceRegion spec) {
        return getThemeOffset(spec.region());
    }

    // ======================== 单精灵图绘制 ========================

    /**
     * 绘制精灵图——自动选择过滤策略，不应用主题偏移。
     * <p>如需主题偏移，调用方需自行通过 {@link SpriteRegion#withTheme()} 预转换，
     * 或使用 {@link #drawStateSprite} 等集成方法。</p>
     */
    public static void drawSprite(GuiGraphics g, SpriteRegion region,
                                   int dstX, int dstY, int dstW, int dstH) {
        FilterState.getInstance().apply(region.texture());
        g.blit(region.texture().location(), dstX, dstY, dstW, dstH,
                region.u(), region.v(),
                region.regionWidth(), region.regionHeight(),
                region.texture().fullWidth(), region.texture().fullHeight());
    }

    /**
     * 绘制精灵图（带显式主题偏移）——避免调用 {@link SpriteRegion#withTheme()} 创建中间对象。
     *
     * @param themeOffset 主题水平偏移量（像素），通过 {@link #getThemeOffset} 计算
     */
    private static void drawSprite(GuiGraphics g, SpriteRegion region, int themeOffset,
                                    int dstX, int dstY, int dstW, int dstH) {
        FilterState.getInstance().apply(region.texture());
        g.blit(region.texture().location(), dstX, dstY, dstW, dstH,
                region.u() + themeOffset, region.v(),
                region.regionWidth(), region.regionHeight(),
                region.texture().fullWidth(), region.texture().fullHeight());
    }

    // ======================== 九宫格渲染 ========================

    /**
     * 绘制九宫格精灵图——自动处理 blend 状态。
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
    private static void drawNineSlice(GuiGraphics g, NineSliceRegion spec, int themeOffset,
                                       int dstX, int dstY, int dstW, int dstH) {
        SpriteRegion r = spec.region();
        TextureInfo texInfo = r.texture();
        ResourceLocation texture = texInfo.location();
        int texW = texInfo.fullWidth();
        int texFileH = texInfo.fullHeight();
        int u = r.u() + themeOffset;

        FilterState.getInstance().apply(texInfo);

        try (BlendScope blend = BlendScope.normal()) {
            NineSliceTiler.forEachTile(
                    u, r.v(), r.regionWidth(), r.regionHeight(), spec.border(),
                    dstX, dstY, dstW, dstH,
                    (sx, sy, sw, sh, dx, dy, dw, dh) ->
                            g.blit(texture, dx, dy, dw, dh, sx, sy, sw, sh, texW, texFileH));
        }
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

    /** 浮窗/悬浮提示九宫格背景——零中间对象分配 */
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h) {
        SpriteRegion r = FLOATING_NINE_SLICE.region();
        int themeOffset = getThemeOffset(r);
        drawNineSliceRaw(g, r.texture(), r.u() + themeOffset, r.v(),
                r.regionWidth(), r.regionHeight(), FLOATING_NINE_SLICE.border(), x, y, w, h);
    }

    /**
     * 九宫格原始渲染——直接使用显式坐标，不创建任何中间对象。
     * 供面板快捷方法和 {@link NineSliceGpuCache} 回退路径使用。
     */
    private static void drawNineSliceRaw(GuiGraphics g, TextureInfo texInfo,
                                          int u, int v, int regionW, int regionH, int border,
                                          int dstX, int dstY, int dstW, int dstH) {
        ResourceLocation texture = texInfo.location();
        int texW = texInfo.fullWidth();
        int texFileH = texInfo.fullHeight();

        FilterState.getInstance().apply(texInfo);

        try (BlendScope blend = BlendScope.normal()) {
            NineSliceTiler.forEachTile(
                    u, v, regionW, regionH, border,
                    dstX, dstY, dstW, dstH,
                    (sx, sy, sw, sh, dx, dy, dw, dh) ->
                            g.blit(texture, dx, dy, dw, dh, sx, sy, sw, sh, texW, texFileH));
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
        CrossFadeRenderer.render(hoverT,
                () -> drawSprite(g, normal, themeOffset, dstX, dstY, dstW, dstH),
                () -> drawSprite(g, hovered, themeOffset, dstX, dstY, dstW, dstH));
    }
}
