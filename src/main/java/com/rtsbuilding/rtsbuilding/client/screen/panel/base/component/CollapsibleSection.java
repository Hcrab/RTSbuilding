package com.rtsbuilding.rtsbuilding.client.screen.panel.base.component;

import com.mojang.math.Axis;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimationFactory;
import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 可折叠分区组件——用于在面板中创建可折叠的分区标题栏。
 *
 * <p>管理分区的展开/折叠状态、标题渲染和点击检测，
 * 支持展开/折叠内容区域。</p>
 */
public class CollapsibleSection {
    // ======================== 标题栏尺寸 ========================

    private static final int SECTION_HEADER_H = 22;

    // ======================== 折叠标题栏背景贴图 (fold_ui.png) ========================

    private static final ResourceLocation FOLD_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/fold_ui.png");
    private static final int FOLD_TEX_W = 32;
    private static final int FOLD_TEX_FILE_H = 32;
    private static final int FOLD_TEX_STATE_H = 16;
    private static final int FOLD_BORDER = 4;
    private static final TextureInfo FOLD_TEX_INFO = new TextureInfo(
            FOLD_TEXTURE, FOLD_TEX_W, FOLD_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion FOLD_NINE_SLICE = NineSliceRegion.fullTheme(
            FOLD_TEX_INFO, FOLD_TEX_STATE_H, FOLD_BORDER);
    private static final int FOLD_BTN_SIZE = 16;

    // ======================== 折叠箭头贴图 ========================

    private static final ResourceLocation FOLD_ARROW_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/fold_arrow.png");
    /** 源区域宽度（单帧 512px）——等于 {@link TextureInfo#halfWidth()} */
    private static final int FOLD_ARROW_TEX_W = 512;
    /** 贴图文件总宽度（双主题翻倍为 1024）——等于 {@link TextureInfo#fullWidth()} */
    private static final int FOLD_ARROW_TEX_FILE_W = 1024;
    private static final int FOLD_ARROW_TEX_FILE_H = 1024;
    private static final int FOLD_ARROW_STATE_H = 512;

    private static final TextureInfo FOLD_ARROW_TEX_INFO = new TextureInfo(
            FOLD_ARROW_TEXTURE, FOLD_ARROW_TEX_FILE_W, FOLD_ARROW_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);

    // ======================== 布局常量 ========================

    private static final int ARROW_X_OFFSET = 5;
    private static final int ARROW_Y_OFFSET = 3;
    private static final int TITLE_X_OFFSET = 23;
    private static final int TITLE_Y_OFFSET = 7;
    private static final int TITLE_WIDTH_SUB = 42;

    // ======================== 实例字段 ========================

    private boolean expanded;
    private final String titleKey;
    /** 缓存的标题翻译文本，避免每帧 Component.translatable() */
    private String cachedTitle;

    /** 箭头旋转平滑动画器 */
    private final FloatAnimation arrowAnim = AnimationFactory.newHoverAnim();
    /** 悬浮状态管理器 */
    private final HoverStateManager hoverState = new HoverStateManager();
    /** 内容展开/收起平滑动画器 */
    private final FloatAnimation contentAnim = AnimationFactory.newExpandAnim();
    private int contentFullHeight;

    // ======================== 构造与状态管理 ========================

    public CollapsibleSection(String titleKey) {
        this.titleKey = titleKey;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            this.arrowAnim.start(this.expanded ? 1.0f : 0.0f);
            this.contentAnim.start(this.expanded ? 1.0f : 0.0f);
        }
    }

    public void toggle() {
        this.expanded = !this.expanded;
        this.arrowAnim.start(this.expanded ? 1.0f : 0.0f);
        this.contentAnim.start(this.expanded ? 1.0f : 0.0f);
    }

    // ======================== 渲染 ========================

    /**
     * 渲染分区标题栏。
     *
     * @param g              GuiGraphics
     * @param mouseX         鼠标 X
     * @param mouseY         鼠标 Y
     * @param x              标题栏左上角 X
     * @param y              标题栏左上角 Y
     * @param sectionWidth   标题栏宽度
     * @param contentHeight  展开时的完整内容高度（用于扩展悬浮检测区域），0 表示不扩展
     */
    public void drawHeader(GuiGraphics g, int mouseX, int mouseY, int x, int y, int sectionWidth, int contentHeight) {
        this.contentAnim.tick();
        this.contentFullHeight = contentHeight;
        updateHoverState(mouseX, mouseY, x, y, sectionWidth, contentHeight);
        renderHoverBackground(g, x, y, sectionWidth);
        renderArrow(g, x, y);
        renderTitle(g, x, y, sectionWidth);
    }

    /**
     * 更新悬浮状态并驱动背景动画。
     * <p>展开状态下，悬浮检测范围扩展到整个分区（标题栏 + 内容区），
     * 使鼠标移到内容区时折叠条背景保持高亮。</p>
     *
     * @param contentHeight 展开时的完整内容高度，0 表示不扩展检测范围
     */
    private void updateHoverState(int mouseX, int mouseY, int x, int y, int sectionWidth, int contentHeight) {
        int detectH = this.expanded && contentHeight > 0 ? SECTION_HEADER_H + contentHeight : SECTION_HEADER_H;
        this.hoverState.update(isMouseOver(mouseX, mouseY, x, y, sectionWidth, detectH));
    }

    /**
     * 渲染标题栏背景（普通态 / 悬浮过渡 / 完全悬浮三段式交叉淡入淡出）。
     */
    private void renderHoverBackground(GuiGraphics g, int x, int y, int sectionWidth) {
        float t = this.hoverState.getValue();
        CrossFadeRenderer.render(t,
                () -> renderStateBackground(g, x, y, sectionWidth, 0),
                () -> renderStateBackground(g, x, y, sectionWidth, FOLD_TEX_STATE_H));
    }

    /**
     * 渲染单一状态的九宫格背景贴图。
     *
     * @param vOffset 贴图源 Y 偏移（0=普通态，FOLD_TEX_STATE_H=悬浮态）
     */
    private void renderStateBackground(GuiGraphics g, int x, int y, int sectionWidth, int vOffset) {
        // 折叠条背景九宫格：展开时向下延伸覆盖标题栏+内容区
        // 收起时只绘制标题栏区域
        int bgH = SECTION_HEADER_H + (int)(this.contentFullHeight * getContentProgress());
        SpriteRenderer.drawNineSlice(g, FOLD_NINE_SLICE.withTheme().withVOffset(vOffset),
                x, y, sectionWidth, bgH);
    }

    /**
     * 渲染折叠箭头（矢量缩放 + 旋转动画）。
     */
    private void renderArrow(GuiGraphics g, int x, int y) {
        this.arrowAnim.tick();
        g.pose().pushPose();
        g.pose().translate(x + ARROW_X_OFFSET, y + ARROW_Y_OFFSET, 0);
        // 位移至箭头中心，绕 Z 轴旋转，再移回
        float halfBtn = FOLD_BTN_SIZE / 2.0f;
        g.pose().translate(halfBtn, halfBtn, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(this.arrowAnim.getValue() * 90.0f));
        g.pose().translate(-halfBtn, -halfBtn, 0);
        SpriteRegion arrowRegion = new SpriteRegion(FOLD_ARROW_TEX_INFO, 0, 0, FOLD_ARROW_TEX_W, FOLD_ARROW_STATE_H)
                .withTheme();
        SpriteRenderer.drawSprite(g, arrowRegion, 0, 0, FOLD_BTN_SIZE, FOLD_BTN_SIZE);
        g.pose().popPose();
    }

    /**
     * 渲染分区标题文字。
     */
    private void renderTitle(GuiGraphics g, int x, int y, int sectionWidth) {
        if (cachedTitle == null) {
            cachedTitle = Component.translatable(this.titleKey).getString();
        }
        int maxTitleWidth = Math.max(8, sectionWidth - TITLE_WIDTH_SUB);
        TextRenderer.draw(g, TextRenderer.trimToWidth(Minecraft.getInstance().font, cachedTitle, maxTitleWidth),
                x + TITLE_X_OFFSET, y + TITLE_Y_OFFSET,
                ThemeManager.getTextColor());
    }

    // ======================== 点击检测 ========================

    /**
     * 检查鼠标点击是否命中标题栏区域。
     */
    public boolean isHeaderClicked(double mouseX, double mouseY, int x, int y, int sectionWidth) {
        return isMouseOver(mouseX, mouseY, x, y, sectionWidth, SECTION_HEADER_H);
    }

    // ======================== 尺寸计算 ========================

    /**
     * 获取内容展开动画进度。
     *
     * @return 0.0（完全收起）到 1.0（完全展开）
     */
    public float getContentProgress() {
        return this.contentAnim.getValue();
    }

    /**
     * 计算分区总高度（标题栏高度 + 展开时的内容高度）。
     *
     * @param contentHeight 展开时的内容高度（不包含标题栏）
     * @return 分区总高度
     */
    public int totalHeight(int contentHeight) {
        return SECTION_HEADER_H + (int) (contentHeight * getContentProgress());
    }

    /**
     * 标题栏高度常量。
     */
    public static int headerHeight() {
        return SECTION_HEADER_H;
    }

    // ======================== 展开内容区背景（已合并到折叠条背景中）========================

    // ======================== 内部工具 ========================

    private static boolean isMouseOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
