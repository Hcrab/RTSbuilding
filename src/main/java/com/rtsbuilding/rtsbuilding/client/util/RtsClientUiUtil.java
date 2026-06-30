package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class RtsClientUiUtil {
    private static final float SLOT_COUNT_SCALE = 0.65F;
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE;

    // ======================== 九宫格面板贴图 ========================
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/ui.png");
    private static final ResourceLocation DRAG_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/drag_ui.png");
    /** 贴图宽度（双主题横向翻倍） */
    private static final int PANEL_TEX_W = 32;
    /** 贴图文件总高度（16 正常 + 16 悬浮高亮） */
    private static final int PANEL_TEX_FILE_H = 32;
    /** 单个状态的高度 */
    private static final int PANEL_TEX_STATE_H = 16;
    /** 悬浮高亮状态的源 Y 偏移 */
    private static final int PANEL_TEX_HOVER_V_OFFSET = 16;
    private static final int PANEL_BORDER = 4;

    // ======================== 浮窗/悬浮提示贴图 ========================
    private static final ResourceLocation FLOATING_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/floating_ui.png");
    /** 贴图宽度（双主题横向翻倍） */
    private static final int FLOATING_TEX_W = 32;
    /** 贴图文件总高度 */
    private static final int FLOATING_TEX_FILE_H = 16;
    /** 单个状态高度 */
    private static final int FLOATING_STATE_H = 16;
    /** 九宫格边框宽度 */
    private static final int FLOATING_BORDER = 2;

    // ======================== 拖拽标题栏贴图 ========================
    /** drag_ui.png 为 16×16，暂无悬浮高亮状态 */
    private static final int DRAG_TEX_FILE_H = 16;

    private RtsClientUiUtil() {
    }

    // ======================== 双主题全局状态（委托给 ThemeManager）========================

    public static boolean isLightMode() {
        return ThemeManager.getInstance().isLightMode();
    }

    public static void setLightMode(boolean mode) {
        ThemeManager.getInstance().setLightMode(mode);
    }

    public static void drawPanelFrame(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int light, int dark) {
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.hLine(x, x + w, y, light);
        guiGraphics.hLine(x, x + w, y + h, dark);
        guiGraphics.vLine(x, y, y + h, light);
        guiGraphics.vLine(x + w, y, y + h, dark);
    }

    // ======================== 交叉淡入淡出渲染 ========================

    /**
     * 三段式交叉淡入淡出渲染工具。
     *
     * <p>根据动画进度 {@code t} 自动选择渲染模式：</p>
     * <ul>
     *   <li>{@code t ≈ 0}（≤0.001）→ 只执行 {@code normalRenderer}</li>
     *   <li>过渡中（0.001 &lt; t &lt; 0.999）→ normalRenderer 全不透明 + hoverRenderer 按 alpha=t 淡入叠加</li>
     *   <li>{@code t ≈ 1}（≥0.999）→ 只执行 {@code hoverRenderer}</li>
     * </ul>
     *
     * @param t               动画进度 [0, 1]
     * @param normalRenderer  普通态渲染
     * @param hoverRenderer   悬浮态渲染
     */
    public static void renderCrossFade(float t, Runnable normalRenderer, Runnable hoverRenderer) {
        if (t > 0.001f && t < 0.999f) {
            beginCrossFadeBlend();
            normalRenderer.run();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, t);
            hoverRenderer.run();
            endCrossFadeBlend();
        } else if (t >= 0.999f) {
            hoverRenderer.run();
        } else {
            normalRenderer.run();
        }
    }

    /** 开启交叉淡入淡出混合模式 */
    private static void beginCrossFadeBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE,
                org.lwjgl.opengl.GL11.GL_ZERO);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** 关闭交叉淡入淡出混合模式 */
    private static void endCrossFadeBlend() {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    /**
     * 使用九宫格贴图绘制面板背景。
     *
     * <p>ui.png 贴图为 32×32（左半=暗色，右半=明亮，下半部分为悬浮高亮），采用 4px 九宫格布局：
     * <ul>
     *   <li>左上角/右上角/左下角/右下角各 4×4 像素 → 面板四角</li>
     *   <li>上/下边缘 8×4 像素区域 → 水平平铺填充</li>
     *   <li>左/右边缘 4×8 像素区域 → 垂直平铺填充</li>
     *   <li>中间 8×8 像素区域 → 双向平铺填充</li>
     * </ul>
     *
     * @param g       GuiGraphics
     * @param x       面板左上角 X
     * @param y       面板左上角 Y
     * @param w       面板宽度
     * @param h       面板高度
     * @param hovered 是否悬停（切换贴图到下半部分）
     */
    public static void drawNineSlicePanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        drawNineSlice(g, PANEL_TEXTURE, x, y, w, h,
                PANEL_BORDER, PANEL_TEX_W, PANEL_TEX_FILE_H, PANEL_TEX_STATE_H,
                hovered ? PANEL_TEX_HOVER_V_OFFSET : 0);
    }

    /**
     * 使用九宫格贴图绘制拖拽标题栏背景。
     *
     * <p>drag_ui.png 为 16×16，与 ui.png 采用相同的 4px 九宫格布局。
     *
     * @param g       GuiGraphics
     * @param x       标题栏左上角 X
     * @param y       标题栏左上角 Y
     * @param w       标题栏宽度
     * @param h       标题栏高度
     * @param hovered 是否悬停（切换贴图到下半部分）
     */
    public static void drawNineSliceDragPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        drawNineSlice(g, DRAG_TEXTURE, x, y, w, h,
                PANEL_BORDER, PANEL_TEX_W, DRAG_TEX_FILE_H, PANEL_TEX_STATE_H, 0);
    }

    /**
     * 使用九宫格贴图绘制浮窗/悬浮提示背景。
     *
     * <p>floating_ui.png 贴图为 32×16（左半=暗色，右半=明亮），采用 2px 九宫格布局：
     * <ul>
     *   <li>四角各 2×2 像素 → 不拉伸</li>
     *   <li>上/下边缘 → 水平平铺填充</li>
     *   <li>左/右边缘 → 垂直平铺填充</li>
     *   <li>中间区域 → 双向平铺填充</li>
     * </ul>
     *
     * @param g  GuiGraphics
     * @param x  面板左上角 X
     * @param y  面板左上角 Y
     * @param w  面板宽度
     * @param h  面板高度
     */
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, FLOATING_UI_TEXTURE, x, y, w, h,
                FLOATING_BORDER, FLOATING_TEX_W, FLOATING_TEX_FILE_H, FLOATING_STATE_H, 0);
    }

    /**
     * 通用九宫格贴图绘制（平铺拼装）。
     *
     * <p>将源贴图按 {@code border} × {@code border} 分割为 9 个区域，四角保持原尺寸渲染，
     * 四边和中间区域通过循环平铺拼装填充，避免拉伸导致的像素变形。
     *
     * <p>支持状态切换：通过 {@code srcYOffset} 控制读取源贴图的起始 Y 位置，
     * 适用于同一贴图纵向排列多个状态（如正常 + 悬浮高亮）。
     *
     * @param g           GuiGraphics
     * @param tex          贴图资源
     * @param x            目标区域左上 X
     * @param y            目标区域左上 Y
     * @param w            目标区域宽度
     * @param h            目标区域高度
     * @param border       边框像素宽度（源贴图边框大小）
     * @param texW         源贴图总宽度
     * @param texFileH     源贴图文件总高度（用于 blit UV 计算）
     * @param stateH       单个状态的高度
     * @param srcYOffset   源贴图读取 Y 偏移（正常=0，悬浮高亮=16）
     */
    public static void drawNineSlice(GuiGraphics g, ResourceLocation tex,
            int x, int y, int w, int h, int border, int texW, int texFileH,
            int stateH, int srcYOffset) {
        // 双主题贴图横向各占一半，用半宽定位源坐标
        int halfW = texW / 2;
        int srcXOffset = isLightMode() ? halfW : 0;
        int innerW = w - 2 * border;
        int innerH = h - 2 * border;
        int srcInnerW = halfW - 2 * border;
        int srcInnerH = stateH - 2 * border;

        // 四角（不拉伸）
        g.blit(tex, x, y, border, border, srcXOffset, srcYOffset, border, border, texW, texFileH);
        g.blit(tex, x + w - border, y, border, border,
                srcXOffset + halfW - border, srcYOffset, border, border, texW, texFileH);
        g.blit(tex, x, y + h - border, border, border,
                srcXOffset, srcYOffset + stateH - border, border, border, texW, texFileH);
        g.blit(tex, x + w - border, y + h - border, border, border,
                srcXOffset + halfW - border, srcYOffset + stateH - border, border, border, texW, texFileH);

        // 上边缘（水平平铺）
        if (innerW > 0) {
            for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, x + w - border - dx);
                g.blit(tex, dx, y, tileW, border, srcXOffset + border, srcYOffset, tileW, border, texW, texFileH);
            }
        }
        // 下边缘（水平平铺）
        if (innerW > 0) {
            for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, x + w - border - dx);
                g.blit(tex, dx, y + h - border, tileW, border,
                        srcXOffset + border, srcYOffset + stateH - border, tileW, border, texW, texFileH);
            }
        }
        // 左边缘（垂直平铺）
        if (innerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                g.blit(tex, x, dy, border, tileH, srcXOffset, srcYOffset + border, border, tileH, texW, texFileH);
            }
        }
        // 右边缘（垂直平铺）
        if (innerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                g.blit(tex, x + w - border, dy, border, tileH,
                        srcXOffset + halfW - border, srcYOffset + border, border, tileH, texW, texFileH);
            }
        }
        // 中心（双向平铺）
        if (innerW > 0 && innerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, x + w - border - dx);
                    g.blit(tex, dx, dy, tileW, tileH,
                            srcXOffset + border, srcYOffset + border, tileW, tileH, texW, texFileH);
                }
            }
        }
    }

    /**
     * 从贴图指定矩形区域绘制九宫格拼装（不与现有 {@link #drawNineSlice} 共享半区假设）。
     *
     * <p>与 {@link #drawNineSlice} 不同，此方法不假定源区域占满贴图的整个左/右半区，
     * 而是接受显式的源矩形坐标 {@code (srcX, srcY, srcW, srcH)}，
     * 适用于贴图内只有一小块区域是九宫格源纹理的场景（如滚动条滑条/滑块）。</p>
     *
     * <p>仍支持双主题：自动根据主题偏移源 X 到左半区或右半区。</p>
     *
     * @param g          GuiGraphics
     * @param tex         贴图资源
     * @param x           目标区域左上 X
     * @param y           目标区域左上 Y
     * @param w           目标区域宽度
     * @param h           目标区域高度
     * @param border      九宫格边框像素宽度
     * @param texW        贴图文件总宽度
     * @param texFileH    贴图文件总高度
     * @param srcX        源矩形左上 X（在贴图半区内的偏移，不含双主题偏移）
     * @param srcY        源矩形左上 Y
     * @param srcW        源矩形宽度（半区内）
     * @param srcH        源矩形高度
     */
    public static void drawNineSliceRegion(GuiGraphics g, ResourceLocation tex,
            int x, int y, int w, int h, int border,
            int texW, int texFileH,
            int srcX, int srcY, int srcW, int srcH) {
        int halfW = texW / 2;
        int themeOffset = isLightMode() ? halfW : 0;
        int srcLeft = themeOffset + srcX;
        int srcTop = srcY;

        int innerW = w - 2 * border;
        int innerH = h - 2 * border;
        int srcInnerW = srcW - 2 * border;
        int srcInnerH = srcH - 2 * border;

        // 四角（不拉伸）
        g.blit(tex, x, y, border, border, srcLeft, srcTop, border, border, texW, texFileH);
        g.blit(tex, x + w - border, y, border, border,
                srcLeft + srcW - border, srcTop, border, border, texW, texFileH);
        g.blit(tex, x, y + h - border, border, border,
                srcLeft, srcTop + srcH - border, border, border, texW, texFileH);
        g.blit(tex, x + w - border, y + h - border, border, border,
                srcLeft + srcW - border, srcTop + srcH - border, border, border, texW, texFileH);

        // 上边缘（水平平铺）
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, x + w - border - dx);
                g.blit(tex, dx, y, tileW, border, srcLeft + border, srcTop, tileW, border, texW, texFileH);
            }
        }
        // 下边缘（水平平铺）
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, x + w - border - dx);
                g.blit(tex, dx, y + h - border, tileW, border,
                        srcLeft + border, srcTop + srcH - border, tileW, border, texW, texFileH);
            }
        }
        // 左边缘（垂直平铺）
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                g.blit(tex, x, dy, border, tileH, srcLeft, srcTop + border, border, tileH, texW, texFileH);
            }
        }
        // 右边缘（垂直平铺）
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                g.blit(tex, x + w - border, dy, border, tileH,
                        srcLeft + srcW - border, srcTop + border, border, tileH, texW, texFileH);
            }
        }
        // 中心区域（双向平铺）
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            for (int dy = y + border; dy < y + h - border; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, y + h - border - dy);
                for (int dx = x + border; dx < x + w - border; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, x + w - border - dx);
                    g.blit(tex, dx, dy, tileW, tileH,
                            srcLeft + border, srcTop + border, tileW, tileH, texW, texFileH);
                }
            }
        }
    }

    // ======================== 统一文字渲染 ========================

    /**
     * 使用最近邻过滤绘制像素贴图，保持低分辨率贴图的清晰锐利边缘。
     *
     * <p>与 {@link #drawScaledImage} 使用双线性过滤不同，此方法使用
     * GL_NEAREST 过滤器，适合绘制像素风格（Pixel Art）的低分辨率贴图，
     * 避免放大时产生模糊效果。</p>
     *
     * @param g           GuiGraphics
     * @param texture     贴图资源
     * @param x           目标区域左上 X
     * @param y           目标区域左上 Y
     * @param drawW       目标绘制宽度
     * @param drawH       目标绘制高度
     * @param srcX        源贴图起始 X
     * @param srcY        源贴图起始 Y
     * @param srcW        源贴图区域宽度
     * @param srcH        源贴图区域高度
     * @param texW        源贴图文件总宽度
     * @param texFileH    源贴图文件总高度
     */
    public static void drawPixelImage(GuiGraphics g, ResourceLocation texture,
            int x, int y, int drawW, int drawH,
            int srcX, int srcY, int srcW, int srcH,
            int texW, int texFileH) {
        // 先绑定贴图，再显式设置最近邻过滤，确保 OpenGL 状态正确
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                org.lwjgl.opengl.GL11.GL_NEAREST);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                org.lwjgl.opengl.GL11.GL_NEAREST);
        g.blit(texture, x, y, drawW, drawH, srcX, srcY, srcW, srcH, texW, texFileH);
    }

    /**
     * 使用双线性过滤绘制贴图（自动处理过滤，缩放更平滑）。
     *
     * <p>从源贴图的指定矩形区域缩放到目标尺寸绘制，并设置纹理过滤为双线性（LINEAR），
     * 避免默认的 GL_NEAREST 导致缩放锯齿。适用于任何尺寸的贴图缩放显示。
     *
     * @param g           GuiGraphics
     * @param texture     贴图资源
     * @param x           目标区域左上 X
     * @param y           目标区域左上 Y
     * @param drawW       目标绘制宽度
     * @param drawH       目标绘制高度
     * @param srcX        源贴图起始 X
     * @param srcY        源贴图起始 Y
     * @param srcW        源贴图区域宽度
     * @param srcH        源贴图区域高度
     * @param texW        源贴图文件总宽度
     * @param texFileH    源贴图文件总高度
     */
    public static void drawScaledImage(GuiGraphics g, ResourceLocation texture,
            int x, int y, int drawW, int drawH,
            int srcX, int srcY, int srcW, int srcH,
            int texW, int texFileH) {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (tex != null) {
            tex.setFilter(true, false);
        }
        g.blit(texture, x, y, drawW, drawH, srcX, srcY, srcW, srcH, texW, texFileH);
    }

    /**
     * 使用双线性过滤绘制整张贴图（从左上角 (0,0) 开始）。
     *
     * @param g       GuiGraphics
     * @param texture 贴图资源
     * @param x       目标区域左上 X
     * @param y       目标区域左上 Y
     * @param drawW   目标绘制宽度
     * @param drawH   目标绘制高度
     * @param texW    源贴图总宽度
     * @param texH    源贴图总高度
     */
    public static void drawScaledImage(GuiGraphics g, ResourceLocation texture,
            int x, int y, int drawW, int drawH, int texW, int texH) {
        drawScaledImage(g, texture, x, y, drawW, drawH, 0, 0, texW, texH, texW, texH);
    }

    /**
     * 统一的 UI 文字渲染方法。
     *
     * <p>项目中所有 UI 文字显示都应使用此方法，确保字体来源、无阴影渲染等参数保持一致。
     * 如需后续统一调整字体、阴影效果或颜色系统，只需修改此方法即可。</p>
     *
     * @param g     GuiGraphics
     * @param text  要绘制的文字
     * @param x     目标 X 坐标
     * @param y     目标 Y 坐标
     * @param color 文字 ARGB 颜色
     */
    public static void drawUiText(GuiGraphics g, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, text, x, y, color, false);
    }

    /**
     * 统一的 UI 文字渲染方法（Component 版本）。
     */
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
        String safeText = text == null ? "" : text;
        drawUiText(guiGraphics, safeText, centerX - font.width(safeText) / 2, y, color);
    }

    public static void drawCenteredStringNoShadow(GuiGraphics guiGraphics, Font font, Component text,
            int centerX, int y, int color) {
        drawCenteredStringNoShadow(guiGraphics, font, text == null ? "" : text.getString(), centerX, y, color);
    }

    public static String compactCount(long value) {
        long positive = Math.max(0L, value);
        if (positive >= EFFECTIVELY_INFINITE_COUNT) {
            return "INF";
        }
        if (positive < 1_000L) {
            return Long.toString(positive);
        }
        if (positive < 10_000L) {
            return String.format("%.2fK", positive / 1_000.0).replaceAll("\\.?0+K$", "K");
        }
        if (positive < 100_000L) {
            return String.format("%.1fK", positive / 1_000.0).replaceAll("\\.?0+K$", "K");
        }
        if (positive < 1_000_000L) {
            return (positive / 1_000L) + "K";
        }
        if (positive < 10_000_000L) {
            return String.format("%.2fM", positive / 1_000_000.0).replaceAll("\\.?0+M$", "M");
        }
        if (positive < 100_000_000L) {
            return String.format("%.1fM", positive / 1_000_000.0).replaceAll("\\.?0+M$", "M");
        }
        if (positive < 1_000_000_000L) {
            return (positive / 1_000_000L) + "M";
        }
        if (positive < 10_000_000_000L) {
            return String.format("%.2fB", positive / 1_000_000_000.0).replaceAll("\\.?0+B$", "B");
        }
        if (positive < 100_000_000_000L) {
            return String.format("%.1fB", positive / 1_000_000_000.0).replaceAll("\\.?0+B$", "B");
        }
        return (positive / 1_000_000_000L) + "B";
    }

    public static String compactFluidAmount(long milliBuckets) {
        long buckets = Math.max(0L, milliBuckets / 1000L);
        if (buckets >= 1_000_000L) {
            return String.format("%.1fM B", buckets / 1_000_000.0);
        }
        if (buckets >= 1_000L) {
            return String.format("%.1fK B", buckets / 1_000.0);
        }
        return buckets + " B";
    }

    public static void drawSlotCountOverlay(GuiGraphics guiGraphics, Font font, int slotX, int slotY, int slotSize, String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        guiGraphics.fill(slotX + 1, slotY + slotSize - 7, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        guiGraphics.pose().translate(0.0F, 0.0F, 1.0F);
        guiGraphics.pose().scale(SLOT_COUNT_SCALE, SLOT_COUNT_SCALE, 1.0F);

        int scaledX = Math.round((slotX + slotSize - 2) / SLOT_COUNT_SCALE);
        int scaledY = Math.round((slotY + slotSize - 7) / SLOT_COUNT_SCALE);
        int textWidth = font.width(countText);
        guiGraphics.drawString(font, countText, scaledX - textWidth, scaledY, color, true);
        guiGraphics.pose().popPose();
    }
}
