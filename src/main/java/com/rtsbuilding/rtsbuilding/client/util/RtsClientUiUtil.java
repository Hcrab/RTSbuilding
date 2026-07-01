package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端 UI 渲染工具集——所有精灵图绘制、文字渲染的<b>统一出口</b>。
 *
 * <p><b>核心设计原则：</b></p>
 * <ul>
 *   <li>所有精灵图绘制通过 {@link #drawSprite} + {@link SpriteRegion}，自动处理过滤和主题偏移</li>
 *   <li>所有九宫格渲染通过 {@link #drawNineSliceRegion} + {@link NineSliceRegion}</li>
 *   <li>文字渲染通过 {@link #drawUiText}</li>
 *   <li>交叉淡入淡出通过 {@link #renderCrossFade}</li>
 *   <li>不直接操作 OpenGL 过滤参数，全部由 {@link TextureInfo.FilterMode} 驱动</li>
 * </ul>
 */
public final class RtsClientUiUtil {
    private static final float SLOT_COUNT_SCALE = 0.65F;
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    // ======================== 贴图资源路径 ========================

    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/ui.png");
    private static final ResourceLocation DRAG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/drag_ui.png");
    private static final ResourceLocation FLOATING_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/floating_ui.png");

    // ======================== TextureInfo 规格常量 ========================

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

    /** 已生成 mipmap 的纹理集合 */
    private static final Set<ResourceLocation> MIPMAP_GENERATED = new HashSet<>();

    private RtsClientUiUtil() {}

    // ======================== 双主题全局状态 ========================

    public static boolean isLightMode() {
        return ThemeManager.getInstance().isLightMode();
    }

    public static void setLightMode(boolean mode) {
        ThemeManager.getInstance().setLightMode(mode);
    }

    // ======================== 交叉淡入淡出 ========================

    public static void renderCrossFade(float t, Runnable normalRenderer, Runnable hoverRenderer) {
        if (t > 0.001f && t < 0.999f) {
            try (BlendScope blend = BlendScope.crossFade()) {
                normalRenderer.run();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, t);
                hoverRenderer.run();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        } else if (t >= 0.999f) {
            hoverRenderer.run();
        } else {
            normalRenderer.run();
        }
    }

    // ======================== 统一精灵图绘制 ========================

    /**
     * 统一精灵图绘制入口——根据 SpriteRegion 中的 TextureInfo 自动选择过滤策略。
     * <p>所有精灵图绘制最终都应通过此方法。</p>
     */
    public static void drawSprite(GuiGraphics g, SpriteRegion region,
                                   int dstX, int dstY, int dstW, int dstH) {
        applyFilter(region.texture());
        g.blit(region.texture().location(), dstX, dstY, dstW, dstH,
                region.u(), region.v(),
                region.regionWidth(), region.regionHeight(),
                region.texture().fullWidth(), region.texture().fullHeight());
    }

    private static void applyFilter(TextureInfo info) {
        var loc = info.location();
        var mode = info.filterMode();

        var tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
        RenderSystem.setShaderTexture(0, loc);
        switch (mode) {
            case PIXEL -> {
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                if (tex != null) tex.setFilter(false, false);
                // 限制只使用基级，忽略上次 HQ 残留的 mipmap 数据
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL12.GL_TEXTURE_MAX_LEVEL, 0);
            }
            case NORMAL -> {
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                if (tex != null) tex.setFilter(true, false);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL12.GL_TEXTURE_MAX_LEVEL, 0);
            }
            case HQ -> {
                if (tex != null) {
                    tex.setFilter(true, true);
                    if (MIPMAP_GENERATED.add(loc)) {
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    }
                }
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                // 恢复完整 mipmap 链
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D,
                        GL12.GL_TEXTURE_MAX_LEVEL, 4);
            }
        }
    }

    // ======================== 九宫格渲染 ========================

    /**
     * 九宫格精灵图渲染——使用 NineSliceRegion 替代旧的散参数形式。
     * 自动处理双主题偏移和 blend 状态。
     */
    public static void drawNineSliceRegion(GuiGraphics g, NineSliceRegion spec,
                                            int dstX, int dstY, int dstW, int dstH) {
        SpriteRegion r = spec.region();
        TextureInfo texInfo = r.texture();
        ResourceLocation texture = texInfo.location();
        int texW = texInfo.fullWidth();
        int texFileH = texInfo.fullHeight();

        applyFilter(texInfo);

        try (BlendScope blend = BlendScope.normal()) {
            NineSliceTiler.forEachTile(
                    r.u(), r.v(), r.regionWidth(), r.regionHeight(), spec.border(),
                    dstX, dstY, dstW, dstH,
                    (sx, sy, sw, sh, dx, dy, dw, dh) ->
                            g.blit(texture, dx, dy, dw, dh, sx, sy, sw, sh, texW, texFileH));
        }
    }

    // ======================== 三段式按钮状态渲染 ========================

    public static void drawStateSprite(GuiGraphics g,
                                        SpriteRegion normal, SpriteRegion hovered, SpriteRegion selected,
                                        boolean isSelected, float hoverT,
                                        int dstX, int dstY, int dstW, int dstH) {
        // 一次性应用主题偏移，避免 cross-fade 分支中每条渲染路径重复调用 withTheme
        SpriteRegion themedNormal = normal.withTheme();
        SpriteRegion themedHovered = hovered.withTheme();
        SpriteRegion themedSelected = selected.withTheme();
        if (isSelected) {
            drawSprite(g, themedSelected, dstX, dstY, dstW, dstH);
            return;
        }
        renderCrossFade(hoverT,
                () -> drawSprite(g, themedNormal, dstX, dstY, dstW, dstH),
                () -> drawSprite(g, themedHovered, dstX, dstY, dstW, dstH));
    }

    // ======================== 便利方法（九宫格面板快捷入口）=======================

    /** 面板背景九宫格（支持悬浮高亮） */
    public static void drawNineSlicePanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        NineSliceRegion spec = hovered
                ? PANEL_NINE_SLICE.withVOffset(PANEL_TEX_HOVER_V_OFFSET)
                : PANEL_NINE_SLICE;
        drawNineSliceRegion(g, spec.withTheme(), x, y, w, h);
    }

    /** 拖拽标题栏九宫格背景 */
    public static void drawNineSliceDragPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        drawNineSliceRegion(g, DRAG_NINE_SLICE.withTheme(), x, y, w, h);
    }

    /** 浮窗/悬浮提示九宫格背景 */
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSliceRegion(g, FLOATING_NINE_SLICE.withTheme(), x, y, w, h);
    }

    // ======================== 面板边框 ========================

    public static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.hLine(x, x + w, y, light);
        guiGraphics.hLine(x, x + w, y + h, dark);
        guiGraphics.vLine(x, y, y + h, light);
        guiGraphics.vLine(x + w, y, y + h, dark);
    }

    // ======================== 统一文字渲染 ========================

    public static void drawUiText(GuiGraphics g, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, text, x, y, color, false);
    }

    public static void drawUiText(GuiGraphics g, Component text, int x, int y, int color) {
        drawUiText(g, text.getString(), x, y, color);
    }

    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    public static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, String text,
            int centerX, int y, int color) {
        drawUiText(guiGraphics, text == null ? "" : text, centerX - font.width(text == null ? "" : text) / 2, y, color);
    }

    public static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, Component text,
            int centerX, int y, int color) {
        drawCenteredStringNoShadow(guiGraphics, font, text == null ? "" : text.getString(), centerX, y, color);
    }

    // ======================== 数值格式化 ========================

    public static String compactCount(long value) {
        long positive = Math.max(0L, value);
        if (positive >= EFFECTIVELY_INFINITE_COUNT) return "INF";
        if (positive < 1_000L) return Long.toString(positive);
        if (positive < 10_000L) return String.format("%.2fK", positive / 1_000.0).replaceAll("\\.?0+K$", "K");
        if (positive < 100_000L) return String.format("%.1fK", positive / 1_000.0).replaceAll("\\.?0+K$", "K");
        if (positive < 1_000_000L) return (positive / 1_000L) + "K";
        if (positive < 10_000_000L) return String.format("%.2fM", positive / 1_000_000.0).replaceAll("\\.?0+M$", "M");
        if (positive < 100_000_000L) return String.format("%.1fM", positive / 1_000_000.0).replaceAll("\\.?0+M$", "M");
        if (positive < 1_000_000_000L) return (positive / 1_000_000L) + "M";
        if (positive < 10_000_000_000L) return String.format("%.2fB", positive / 1_000_000_000.0).replaceAll("\\.?0+B$", "B");
        if (positive < 100_000_000_000L) return String.format("%.1fB", positive / 1_000_000_000.0).replaceAll("\\.?0+B$", "B");
        return (positive / 1_000_000_000L) + "B";
    }

    public static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) return String.format("%.1fM B", buckets / 1_000_000.0);
        if (buckets >= 1_000L) return String.format("%.1fK B", buckets / 1_000.0);
        return buckets + " B";
    }

    public static void drawSlotCountOverlay(GuiGraphics guiGraphics, Font font, int slotX, int slotY, int slotSize, String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) return;
        // 背景在原始坐标空间绘制
        guiGraphics.fill(slotX + 1, slotY + slotSize - 7, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        // 文字在缩放坐标空间绘制：将原点移到目标右下角，缩放后文字右对齐
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(slotX + slotSize - 2, slotY + slotSize - 7, 300.0F);
        guiGraphics.pose().scale(SLOT_COUNT_SCALE, SLOT_COUNT_SCALE, 1.0F);
        guiGraphics.drawString(font, countText, -font.width(countText), 0, color, true);
        guiGraphics.pose().popPose();
    }

    // GL11 constants helper (avoids fully qualifying org.lwjgl.opengl.GL11 everywhere)
    private static final class GL11 {
        static final int GL_TEXTURE_2D = org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
        static final int GL_TEXTURE_MIN_FILTER = org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
        static final int GL_TEXTURE_MAG_FILTER = org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
        static final int GL_NEAREST = org.lwjgl.opengl.GL11.GL_NEAREST;
        static final int GL_LINEAR = org.lwjgl.opengl.GL11.GL_LINEAR;
        static final int GL_LINEAR_MIPMAP_LINEAR = org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
    }
}
