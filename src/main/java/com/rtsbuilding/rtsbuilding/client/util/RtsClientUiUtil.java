package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL30;

import java.util.HashSet;
import java.util.Set;

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

    /** 已生成 mipmap 的纹理集合，避免重复生成 */
    private static final Set<ResourceLocation> MIPMAP_GENERATED = new HashSet<>();

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

    // ======================== 预声明规格常量 ========================

    private static final NineSliceSource PANEL_SPEC = NineSliceSource.fullTheme(
            PANEL_TEX_W / 2, PANEL_TEX_STATE_H, PANEL_BORDER);
    private static final NineSliceSource FLOATING_SPEC = NineSliceSource.fullTheme(
            FLOATING_TEX_W / 2, FLOATING_STATE_H, FLOATING_BORDER);
    private static final NineSliceSource DRAG_SPEC = NineSliceSource.fullTheme(
            PANEL_TEX_W / 2, PANEL_TEX_STATE_H, PANEL_BORDER);

    // ======================== 统一核心方法 ========================

    /**
     * 九宫格拼装渲染——唯一核心入口。
     *
     * <p>将源贴图按九宫格分割为 9 个区域：四角保持原尺寸渲染，四边和中间区域通过
     * 循环平铺拼装填充，避免拉伸导致的像素变形。</p>
     *
     * <p>自动处理：</p>
     * <ul>
     *   <li><b>双主题偏移</b>——根据 {@link ThemeManager#isLightMode()} 自动偏移到左半或右半</li>
     *   <li><b>Blend 状态</b>——自动启用半透明混合，无需调用方手动管理</li>
     * </ul>
     *
     * @param g          GuiGraphics
     * @param texture    贴图资源
     * @param texW       贴图文件总宽度
     * @param texFileH   贴图文件总高度
     * @param dstX       目标区域左上 X
     * @param dstY       目标区域左上 Y
     * @param dstW       目标区域宽度
     * @param dstH       目标区域高度
     * @param src        九宫格源规格（源坐标 + 边框）
     */
    public static void drawNineSlice(GuiGraphics g, ResourceLocation texture,
                                     int texW, int texFileH,
                                     int dstX, int dstY, int dstW, int dstH,
                                     NineSliceSource src) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 双主题偏移：暗色=左半区，亮色=右半区
        int halfW = texW / 2;
        int themeOffset = isLightMode() ? halfW : 0;
        int srcLeft = themeOffset + src.srcX();
        int srcTop = src.srcY();

        int b = src.border();
        int innerW = dstW - 2 * b;
        int innerH = dstH - 2 * b;
        int srcInnerW = src.srcW() - 2 * b;
        int srcInnerH = src.srcH() - 2 * b;

        // 四角（不拉伸）
        g.blit(texture, dstX, dstY, b, b, srcLeft, srcTop, b, b, texW, texFileH);
        g.blit(texture, dstX + dstW - b, dstY, b, b,
                srcLeft + src.srcW() - b, srcTop, b, b, texW, texFileH);
        g.blit(texture, dstX, dstY + dstH - b, b, b,
                srcLeft, srcTop + src.srcH() - b, b, b, texW, texFileH);
        g.blit(texture, dstX + dstW - b, dstY + dstH - b, b, b,
                srcLeft + src.srcW() - b, srcTop + src.srcH() - b, b, b, texW, texFileH);

        // 上边缘（水平平铺）
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                g.blit(texture, dx, dstY, tileW, b, srcLeft + b, srcTop, tileW, b, texW, texFileH);
            }
        }
        // 下边缘（水平平铺）
        if (innerW > 0 && srcInnerW > 0) {
            for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                g.blit(texture, dx, dstY + dstH - b, tileW, b,
                        srcLeft + b, srcTop + src.srcH() - b, tileW, b, texW, texFileH);
            }
        }
        // 左边缘（垂直平铺）
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                g.blit(texture, dstX, dy, b, tileH, srcLeft, srcTop + b, b, tileH, texW, texFileH);
            }
        }
        // 右边缘（垂直平铺）
        if (innerH > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                g.blit(texture, dstX + dstW - b, dy, b, tileH,
                        srcLeft + src.srcW() - b, srcTop + b, b, tileH, texW, texFileH);
            }
        }
        // 中心区域（双向平铺）
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            for (int dy = dstY + b; dy < dstY + dstH - b; dy += srcInnerH) {
                int tileH = Math.min(srcInnerH, dstY + dstH - b - dy);
                for (int dx = dstX + b; dx < dstX + dstW - b; dx += srcInnerW) {
                    int tileW = Math.min(srcInnerW, dstX + dstW - b - dx);
                    g.blit(texture, dx, dy, tileW, tileH,
                            srcLeft + b, srcTop + b, tileW, tileH, texW, texFileH);
                }
            }
        }
    }

    // ======================== 便利方法 ========================

    /**
     * 使用九宫格贴图绘制面板背景（ui.png 32×32，4px 边框，支持悬浮高亮）。
     *
     * @param g       GuiGraphics
     * @param x       面板左上角 X
     * @param y       面板左上角 Y
     * @param w       面板宽度
     * @param h       面板高度
     * @param hovered 是否悬停（切换贴图到下半部分）
     */
    public static void drawNineSlicePanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        drawNineSlice(g, PANEL_TEXTURE, PANEL_TEX_W, PANEL_TEX_FILE_H, x, y, w, h,
                hovered ? PANEL_SPEC.withYOffset(PANEL_TEX_HOVER_V_OFFSET) : PANEL_SPEC);
    }

    /**
     * 使用九宫格贴图绘制拖拽标题栏背景（drag_ui.png 16×16，4px 边框）。
     *
     * @param g       GuiGraphics
     * @param x       标题栏左上角 X
     * @param y       标题栏左上角 Y
     * @param w       标题栏宽度
     * @param h       标题栏高度
     * @param hovered 保留参数（当前未使用）
     */
    public static void drawNineSliceDragPanel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        drawNineSlice(g, DRAG_TEXTURE, PANEL_TEX_W, DRAG_TEX_FILE_H, x, y, w, h, DRAG_SPEC);
    }

    /**
     * 使用九宫格贴图绘制浮窗/悬浮提示背景（floating_ui.png 32×16，2px 边框）。
     *
     * @param g  GuiGraphics
     * @param x  面板左上角 X
     * @param y  面板左上角 Y
     * @param w  面板宽度
     * @param h  面板高度
     */
    public static void drawNineSliceFloatingPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, FLOATING_UI_TEXTURE, FLOATING_TEX_W, FLOATING_TEX_FILE_H, x, y, w, h, FLOATING_SPEC);
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
        // 绑定并显式设置双线性过滤（确保 OpenGL 状态正确）
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        // 同步更新 AbstractTexture 的状态，避免后续其他绑定覆盖
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
     * 使用三线性过滤的高质量贴图绘制（mipmap + 双线性）。
     *
     * <p>与 {@link #drawScaledImage} 使用普通双线性过滤不同，此方法：</p>
     * <ul>
     *   <li>首次绘制时自动生成 mipmap（{@link GL30#glGenerateMipmap}）</li>
     *   <li>缩小过滤器使用 {@code GL_LINEAR_MIPMAP_LINEAR}（三线性过滤），
     *       大幅减少缩小渲染时的锯齿和闪烁</li>
     *   <li>放大过滤器使用 {@code GL_LINEAR}，保持放大时边缘平滑</li>
     * </ul>
     *
     * <p><b>注意：</b>此方法适合高分辨率贴图（如照片级纹理），
     * 对于像素风格的低分辨率贴图应使用 {@link #drawPixelImage}。
     * mipmap 仅在首次绘制时生成一次，后续调用无额外开销。</p>
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
    public static void drawHighQualityImage(GuiGraphics g, ResourceLocation texture,
            int x, int y, int drawW, int drawH,
            int srcX, int srcY, int srcW, int srcH,
            int texW, int texFileH) {
        // 1. 通知 AbstractTexture 使用 mipmap 过滤（确保后续绑定也保持此设置）
        var tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (tex != null) {
            tex.setFilter(true, true);  // blur=true, mipmap=true → GL_LINEAR_MIPMAP_LINEAR
        }

        // 2. 绑定纹理（setShaderTexture 会触发 AbstractTexture.bind()，应用 filter 设置）
        RenderSystem.setShaderTexture(0, texture);

        // 3. 首次使用时生成 mipmap
        if (tex != null && MIPMAP_GENERATED.add(texture)) {
            GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
        }

        // 4. 显式设置三线性过滤（覆盖 AbstractTexture 可能未正确应用的情况）
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
                org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR);
        RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
                org.lwjgl.opengl.GL11.GL_LINEAR);

        // 5. 绘制
        g.blit(texture, x, y, drawW, drawH, srcX, srcY, srcW, srcH, texW, texFileH);
    }

    /**
     * 使用三线性过滤绘制整张贴图（从左上角 (0,0) 开始）。
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
    public static void drawHighQualityImage(GuiGraphics g, ResourceLocation texture,
            int x, int y, int drawW, int drawH, int texW, int texH) {
        drawHighQualityImage(g, texture, x, y, drawW, drawH, 0, 0, texW, texH, texW, texH);
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
