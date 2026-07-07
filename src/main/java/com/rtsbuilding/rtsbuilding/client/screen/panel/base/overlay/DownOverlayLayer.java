package com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.LeftDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.RightDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 下栏内嵌层——管理单个嵌层的裁剪区域和背景渲染。
 *
 * <p>每个嵌层维护自己的位置和尺寸，渲染时使用 Scissor 裁剪确保内容不溢出。
 * 水平分左右两个嵌层，各自的专用实现 {@link LeftDownOverlayLayer} / {@link RightDownOverlayLayer}
 * 通过 {@link #renderContent(GuiGraphics)} 添加子组件。</p>
 *
 * <p>背景贴图使用 {@code overlay_ui.png}（256×256），垂直上半=正常态、下半=激活态。</p>
 */
public abstract class DownOverlayLayer {

    // ======================== 贴图资源 ========================

    /** 下栏内嵌层贴图（256×256，水平左暗右亮，垂直上正常下激活） */
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/overlay_ui.png");
    /** 贴图文件总宽度 */
    private static final int OVERLAY_TEX_W = 256;
    /** 贴图文件总高度 */
    private static final int OVERLAY_TEX_FILE_H = 256;
    /** 单个状态高度 */
    private static final int OVERLAY_STATE_H = 128;
    /** 鼠标位于区域内时使用的源 Y 偏移（下半部分） */
    private static final int OVERLAY_ACTIVE_V_OFFSET = 128;
    /** 九宫格边框宽度 */
    private static final int OVERLAY_BORDER = 8;
    private static final TextureInfo OVERLAY_TEX_INFO = new TextureInfo(
            OVERLAY_TEXTURE, OVERLAY_TEX_W, OVERLAY_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion OVERLAY_NINE_SLICE = NineSliceRegion.fullTheme(
            OVERLAY_TEX_INFO, OVERLAY_STATE_H, OVERLAY_BORDER);

    // ======================== 位置 / 尺寸 ========================

    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * 更新位置和尺寸。
     *
     * @param x      目标区域 X
     * @param y      目标区域 Y
     * @param width  目标区域宽度
     * @param height 目标区域高度
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ======================== 访问器 ========================

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // ======================== 渲染 ========================

    /**
     * 渲染层背景，启用 Scissor 裁剪确保内容不溢出。
     * <p>子类通过 {@link #renderContent(GuiGraphics)} 添加专属内容。</p>
     *
     * @param g       GuiGraphics 实例
     * @param hovered 是否处于悬停/激活状态（使用激活态贴图偏移）
     */
    public void render(GuiGraphics g, boolean hovered) {
        if (width <= 0 || height <= 0) return;

        int srcY = hovered ? OVERLAY_ACTIVE_V_OFFSET : 0;
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme().withVOffset(srcY),
                x, y, width, height);
        renderContent(g);
    }

    /**
     * 子类在此方法中渲染专属内容（按钮、标签、图标等），
     * 已在 {@link #render(GuiGraphics, boolean)} 的 Scissor 保护区间内。
     */
    protected void renderContent(GuiGraphics g) {
        // 默认无内容，子类按需重写
    }

    // ======================== 区域检测 ========================

    /**
     * 检测点是否位于层区域内。
     *
     * @param px 检测点 X
     * @param py 检测点 Y
     * @return 如果点在区域内则返回 true
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
