package com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.LeftDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay.RightDownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.EasingFunctions;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    // ======================== screen_ui.png 装饰遮罩贴图 ========================

    /** 屏幕装饰九宫格贴图（256×256，水平左暗右亮，垂直上正常下激活） */
    private static final ResourceLocation SCREEN_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/screen_ui.png");
    private static final int SCREEN_UI_TEX_W = 256;
    private static final int SCREEN_UI_TEX_FILE_H = 256;
    private static final int SCREEN_UI_STATE_H = 128;
    private static final int SCREEN_UI_BORDER = 8;
    private static final TextureInfo SCREEN_UI_TEX_INFO = new TextureInfo(
            SCREEN_UI_TEXTURE, SCREEN_UI_TEX_W, SCREEN_UI_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SCREEN_UI_NINE_SLICE = NineSliceRegion.fullTheme(
            SCREEN_UI_TEX_INFO, SCREEN_UI_STATE_H, SCREEN_UI_BORDER);

    // ======================== 位置 / 尺寸 ========================

    private int x;
    private int y;
    private int width;
    private int height;

    /** 最近的鼠标 X（由 DownSidebarPanel 在渲染前设置） */
    private int lastMouseX;
    /** 最近的鼠标 Y */
    private int lastMouseY;

    /** 是否正在拖拽嵌层分隔条（拖拽时抑制所有悬浮逻辑） */
    private boolean dividerDragging;

    /** 悬浮状态交叉渐变动画（0=常态，1=悬浮态） */
    private final FloatAnimation hoverAnim = FloatAnimation.builder()
            .from(0f).to(0f)
            .duration(150L)
            .easing(EasingFunctions.LINEAR)
            .startFromCurrent(true)
            .build();
    /** 上一帧的悬浮状态，用于检测变化 */
    private boolean prevHovered;

    /**
     * 设置嵌层分隔条拖拽状态。
     *
     * @param dragging 是否正在拖拽分隔条
     */
    public void setDividerDragging(boolean dragging) {
        this.dividerDragging = dragging;
    }

    /**
     * 当前是否正在拖拽嵌层分隔条。
     */
    protected boolean isDividerDragging() {
        return this.dividerDragging;
    }
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

    /**
     * 设置最近一次鼠标位置（由外部在渲染前调用，供子类 hover 检测使用）。
     */
    public void setLastMousePos(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    public int getLastMouseX() { return lastMouseX; }
    public int getLastMouseY() { return lastMouseY; }

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

        // 拖拽分隔条时抑制所有悬浮逻辑，避免干扰
        if (dividerDragging) {
            hovered = false;
        }

        // 检测悬浮状态变化，启动交叉渐变动画
        if (hovered != prevHovered) {
            hoverAnim.start(hovered ? 1f : 0f);
            prevHovered = hovered;
        }
        hoverAnim.tick();
        float hoverT = hoverAnim.getValue();

        // 背景 overlay 交叉淡入淡出
        CrossFadeRenderer.render(hoverT,
                () -> SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme(), x, y, width, height),
                () -> SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme().withVOffset(OVERLAY_ACTIVE_V_OFFSET), x, y, width, height));

        // 启用 Scissor 裁剪确保内容不溢出层边界
        g.flush();
        Screen screen = Minecraft.getInstance().screen;
        int inset = 2;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, x + inset, y + inset, x + width - inset, y + height - inset);
        } else {
            g.enableScissor(x + inset, y + inset, x + width - inset, y + height - inset);
        }

        // 先渲染内容，再叠 screen_ui.png 装饰遮罩
        renderContent(g);

        // screen_ui 装饰遮罩交叉淡入淡出
        CrossFadeRenderer.render(hoverT,
                () -> SpriteRenderer.drawNineSlice(g, SCREEN_UI_NINE_SLICE.withTheme(), x, y, width, height),
                () -> SpriteRenderer.drawNineSlice(g, SCREEN_UI_NINE_SLICE.withTheme().withVOffset(SCREEN_UI_STATE_H), x, y, width, height));

        g.disableScissor();

        // Scissor 解除后渲染悬浮提示等不受裁剪限制的内容
        postRenderContent(g);
    }

    /**
     * 子类在此方法中渲染专属内容（按钮、标签、图标等），
     * 已在 {@link #render(GuiGraphics, boolean)} 的 Scissor 保护区间内。
     */
    protected void renderContent(GuiGraphics g) {
        // 默认无内容，子类按需重写
    }

    /**
     * Scissor 裁剪结束后调用，供子类渲染不受裁剪限制的内容（如 tooltip）。
     */
    protected void postRenderContent(GuiGraphics g) {
        // 默认无操作，子类按需重写
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

    // ======================== 鼠标事件（默认空实现，子类按需重写）=======================

    /**
     * 鼠标点击事件。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 按钮编号（0=左键）
     * @return true 表示事件已消费
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 鼠标滚轮滚动事件。
     *
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     * @param scrollX  水平滚动量
     * @param scrollY  垂直滚动量
     * @return true 表示事件已消费
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    /**
     * 鼠标释放事件。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 按钮编号
     * @return true 表示事件已消费
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 鼠标拖拽事件。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 按钮编号
     * @param dragX  拖拽 X 偏移量
     * @param dragY  拖拽 Y 偏移量
     * @return true 表示事件已消费
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /**
     * 按键按下事件。
     *
     * @param keyCode   按键代码
     * @param scanCode  扫描码
     * @param modifiers 修饰键掩码
     * @return true 表示事件已消费
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * 字符输入事件。
     *
     * @param codePoint Unicode 字符
     * @param modifiers 修饰键掩码
     * @return true 表示事件已消费
     */
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
