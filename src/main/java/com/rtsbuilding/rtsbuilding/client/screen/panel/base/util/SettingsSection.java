package com.rtsbuilding.rtsbuilding.client.screen.panel.base.util;

import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

/**
 * 可折叠设置分区抽象基类——为设置面板中的折叠分区提供通用渲染、交互逻辑
 * 以及统一的开关/滑条布局辅助方法。
 *
 * <p>子类只需提供标题 key 和内容行数组，即可获得完整的展开/收起动画、
 * 九宫格背景、悬浮高亮和点击切换功能。
 * 子类可直接使用 {@link #renderLabel}、{@link #renderToggle}、{@link #renderSlider}
 * 等辅助方法渲染标准开关/滑条行。</p>
 */
public abstract class SettingsSection {
    // ======================== 通用布局常量 ========================

    private static final int LEFT_PADDING = 8;
    private static final int CONTENT_TOP_GAP = 0;
    /** 行高（含行间距），默认 20px */
    private static final int LINE_HEIGHT = 20;

    private static final int HOVER_BG_COLOR = 0x22334455;

    /** 左内边距（标签距左边缘） */
    protected static final int LEFT_PAD = 6;
    /** 右内边距（开关距右边缘） */
    protected static final int RIGHT_PAD = 6;
    /** 滑条与标签间距 */
    protected static final int SLIDER_GAP = 8;
    /** 中位线与右侧控件起始的间距 */
    protected static final int MID_DIVIDER_GAP = 4;

    /** 返回内容区中位线右侧控件的起始 X（中位线 + 间距） */
    protected static int midControlX(int x, int w) {
        return x + w / 2 + MID_DIVIDER_GAP;
    }

    // ======================== 实例字段 ========================

    private final CollapsibleSection section;

    // ======================== 子类可访问的布局常量 ========================

    protected int getLeftPadding() { return LEFT_PADDING; }
    protected int getLineHeight() { return LINE_HEIGHT; }
    protected int getTextColor() { return ThemeManager.getTextColor(); }
    protected int getHoverBgColor() { return HOVER_BG_COLOR; }
    protected int getHoverTextColor() { return ThemeManager.getHoverTextColor(); }

    /** 根据主题返回分隔线颜色：暗色主题白色，亮色主题黑色 */
    private static int getSeparatorColor() {
        return ThemeManager.getInstance().isLightMode() ? 0xFF000000 : 0xFFFFFFFF;
    }

    // ======================== 构造 ========================

    protected SettingsSection(String titleKey) {
        this.section = new CollapsibleSection(titleKey);
    }

    // ======================== 抽象方法 ========================

    /**
     * 返回内容区需要多少行的高度（用于计算裁剪区域高度）。
     * <p>子类根据 {@link #renderContent} 中使用的最大行号 + 1 返回此值，
     * 确保开关（32px）等超出单行高度的控件不会被裁剪。</p>
     */
    protected abstract int getContentRowCount();

    /**
     * 获取内容行数（替代每帧创建 String[] 数组，避免 GC 压力）。
     * <p>子类若未覆写 {@link #renderContent}，应直接通过此方法获取行数。</p>
     */
    protected int getContentLineCount() {
        return getContentRowCount();
    }

    /**
     * 返回内容区完整高度（用于通知折叠条背景和裁剪计算）。
     * <p>子类可覆写此方法返回动画高度值，实现依赖内容显隐时折叠条平滑伸缩。</p>
     */
    protected int getEffectiveContentHeight() {
        return getContentRowCount() * LINE_HEIGHT + 6;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染设置分区（标题栏 + 展开内容）。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentY, int contentW) {
        int headerX = contentX + LEFT_PADDING;
        int headerY = contentY + 8;
        int headerW = contentW - LEFT_PADDING * 2;

        int lineCount = getContentLineCount();
        int contentFullH = getEffectiveContentHeight();
        section.drawHeader(g, mouseX, mouseY, headerX, headerY, headerW, contentFullH);

        // 标题底部分隔线——展开后才绘制
        int headerBottom = headerY + CollapsibleSection.headerHeight();

        int animH = (int) (contentFullH * section.getContentProgress());
        if (animH > 0) {
            // 2px 粗分隔线，左右各留 5px 边距
            g.fill(headerX + 5, headerBottom - 2, headerX + headerW - 5, headerBottom, getSeparatorColor());

            int contentTop = headerBottom + CONTENT_TOP_GAP;
            enableScissor(g, headerX, contentTop, headerX + headerW, contentTop + animH);
            renderContent(g, mouseX, mouseY, headerX, contentTop, headerW, lineCount);
            g.disableScissor();
        }
    }

    /**
     * 渲染内容行。子类可重写此方法添加自定义绘制（如颜色指示器）。
     */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {

        for (int i = 0; i < lineCount; i++) {
            int lineY = y + 4 + i * LINE_HEIGHT;
        }
    }

    // ======================== 裁剪 ========================

    private static void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, x1, y1, x2, y2);
        } else {
            g.enableScissor(x1, y1, x2, y2);
        }
    }

    // ======================== 交互 ========================

    /**
     * 处理鼠标点击。命中标题栏时切换展开/折叠；
     * 展开状态下命中内容行时回调 {@link #onContentLineClick}。
     *
     * @return true 如果点击被该分区消费
     */
    public boolean handleClick(double mouseX, double mouseY, int contentX, int contentY, int contentW) {
        int headerX = contentX + LEFT_PADDING;
        int headerY = contentY + 8;
        int headerW = contentW - LEFT_PADDING * 2;
        if (section.isHeaderClicked(mouseX, mouseY, headerX, headerY, headerW)) {
            section.toggle();
            return true;
        }
        if (isExpanded()) {
            int lineCount = getContentLineCount();
            int contentFullH = getEffectiveContentHeight();
            int animH = (int) (contentFullH * section.getContentProgress());
            if (animH > 0) {
                int contentTop = headerY + CollapsibleSection.headerHeight() + CONTENT_TOP_GAP;
                if (mouseX >= headerX + 2 && mouseX < headerX + headerW - 2
                        && mouseY >= contentTop && mouseY < contentTop + animH) {
                    for (int i = 0; i < lineCount; i++) {
                        if (onContentLineClick(i, mouseX, mouseY, contentX, contentY, contentW)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 内容行点击回调。子类可重写此方法实现点击交互（如切换开关、循环选项）。
     */
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        return false;
    }

    // ======================== 行坐标辅助 ========================

    /** 第 N 行的顶部 Y（相对内容区起点 y） */
    protected int rowY(int y, int row) {
        return y + 4 + LINE_HEIGHT * row;
    }

    /** 第 N 行文字绘制的 Y 坐标 */
    protected int textY(int y, int row) {
        return rowY(y, row) + 2;
    }

    // ======================== 开关辅助 ========================

    /** 右对齐开关的 X */
    protected int toggleX(int w) {
        return w - ThemeSwitchComponent.SIZE - RIGHT_PAD;
    }

    /** 右对齐开关的 Y——开关中心对齐文字中心 */
    protected int toggleY(int y, int row) {
        int textCenter = textY(y, row) + Minecraft.getInstance().font.lineHeight / 2;
        return textCenter - ThemeSwitchComponent.SIZE / 2;
    }

    /** 在指定行渲染标签文本（左对齐） */
    protected void renderLabel(GuiGraphics g, String text, int x, int y, int row) {
        RtsClientUiUtil.drawUiText(g, text, x + LEFT_PAD, textY(y, row), getTextColor());
    }

    /** 在指定行渲染右对齐开关 */
    protected void renderToggle(GuiGraphics g, int mx, int my,
                                 int x, int y, int w, int row,
                                 ThemeSwitchComponent toggle, boolean state) {
        toggle.render(g, mx, my, x + toggleX(w), toggleY(y, row), state);
    }

    // ======================== 滑条辅助 ========================

    /**
     * 在指定行渲染标签 + 滑条，并缓存轨道坐标。
     *
     * @param trackPos 输出：轨道位置缓存
     */
    protected void renderSlider(GuiGraphics g, int mx, int my,
                                 int x, int y, int w, int row, String label,
                                 ScaleSliderComponent slider, SliderTrack trackPos,
                                 double min, double max, double value) {
        RtsClientUiUtil.drawUiText(g, label, x + LEFT_PAD, textY(y, row), getTextColor());
        int lineCenterY = textY(y, row) + Minecraft.getInstance().font.lineHeight / 2;
        int controlStart = midControlX(x, w);
        trackPos.trackX = controlStart;
        trackPos.trackY = lineCenterY - 2;
        trackPos.trackW = Mth.clamp(x + w - RIGHT_PAD - controlStart, 20, x + w - RIGHT_PAD - controlStart);
        trackPos.slider = slider;
        slider.render(g, mx, my, trackPos.trackX, trackPos.trackY, trackPos.trackW, min, max, value);
    }

    /** 滑条轨道位置与引用缓存 */
    public static final class SliderTrack {
        public int trackX, trackY, trackW;
        public ScaleSliderComponent slider;
    }

    // ======================== 状态查询 ========================

    public boolean isExpanded() {
        return section.isExpanded();
    }

    public void setExpanded(boolean expanded) {
        section.setExpanded(expanded);
    }

    public int totalHeight(int contentW) {
        int contentFullH = getEffectiveContentHeight();
        return section.totalHeight(contentFullH);
    }
}
