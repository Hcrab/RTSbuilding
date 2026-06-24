package com.rtsbuilding.rtsbuilding.client.screen.panel.base.util;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import javax.annotation.Nullable;

/**
 * 可折叠设置分区抽象基类——为设置面板中的折叠分区提供通用渲染和交互逻辑。
 *
 * <p>子类只需提供标题 key 和内容行数组，即可获得完整的展开/收起动画、
 * 九宫格背景、悬浮高亮和点击切换功能。</p>
 */
public abstract class SettingsSection {
    // ======================== 通用布局常量 ========================

    private static final int LEFT_PADDING = 8;
    private static final int CONTENT_TOP_GAP = 0;
    private static final int LINE_HEIGHT = 14;

    private static final int HOVER_BG_COLOR = 0x22334455;
    // ======================== 实例字段 ========================

    private final CollapsibleSection section;
    @Nullable
    private String[] cachedLines;

    // ======================== 子类可访问的布局常量 ========================

    protected int getLeftPadding() { return LEFT_PADDING; }
    protected int getLineHeight() { return LINE_HEIGHT; }
    protected int getTextColor() { return ThemeManager.getTextColor(); }
    protected int getHoverBgColor() { return HOVER_BG_COLOR; }
    protected int getHoverTextColor() { return ThemeManager.getHoverTextColor(); }

    // ======================== 构造 ========================

    protected SettingsSection(String titleKey) {
        this.section = new CollapsibleSection(titleKey);
    }

    // ======================== 抽象方法 ========================

    /** 返回分区内容行数组 */
    protected abstract String[] getContentLines();

    /**
     * 获取内容行数组（带懒加载缓存，避免每帧重复创建数组）。
     * <p>注意：缓存永不自动失效。如果子类 {@link #getContentLines()} 返回动态内容，
     * 必须在内容变更后手动调用 {@link #invalidateCache()}。</p>
     */
    protected String[] getCachedLines() {
        if (cachedLines == null) {
            cachedLines = getContentLines();
        }
        return cachedLines;
    }

    /**
     * 使内容行缓存失效，下次渲染时重新调用 {@link #getContentLines()}。
     * <p>仅在内容行动态变化时需要使用此方法（当前所有子类返回静态数组，暂不涉及）。</p>
     */
    protected void invalidateCache() {
        this.cachedLines = null;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染设置分区（标题栏 + 展开内容）。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentY, int contentW) {
        int headerX = contentX + LEFT_PADDING;
        int headerY = contentY + 8;
        int headerW = contentW - LEFT_PADDING * 2;

        String[] lines = getCachedLines();
        int contentFullH = lines.length * LINE_HEIGHT + 6;
        section.drawHeader(g, mouseX, mouseY, headerX, headerY, headerW, contentFullH);

        int animH = (int) (contentFullH * section.getContentProgress());
        if (animH > 0) {
            int contentTop = headerY + CollapsibleSection.headerHeight() + CONTENT_TOP_GAP;
            enableScissor(g, headerX, contentTop, headerX + headerW, contentTop + animH);
            renderContent(g, mouseX, mouseY, headerX, contentTop, headerW, lines);
            g.disableScissor();
        }
    }

    /**
     * 渲染内容行。子类可重写此方法添加自定义绘制（如颜色指示器）。
     */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String[] lines) {

        for (int i = 0; i < lines.length; i++) {
            int lineY = y + 4 + i * LINE_HEIGHT;
            boolean hovered = mouseX >= x + 2 && mouseX < x + w - 2
                    && mouseY >= lineY && mouseY < lineY + LINE_HEIGHT;
            if (hovered) {
                g.fill(x + 1, lineY, x + w - 1, lineY + LINE_HEIGHT, getHoverBgColor());
            }
            RtsClientUiUtil.drawUiText(g, lines[i], x + 6, lineY + 2,
                    hovered ? getHoverTextColor() : getTextColor());
        }
    }

    // ======================== 裁剪（适配 RTS 缩放） ========================

    /**
     * 启用裁剪，自动适配 BuilderScreen 的固定 RTS GUI 缩放系统。
     * 解决调整游戏窗口后折叠条内容被裁没的问题。
     */
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
        // 展开状态下检测内容行点击
        if (isExpanded()) {
            String[] lines = getCachedLines();
            int contentFullH = lines.length * LINE_HEIGHT + 6;
            int animH = (int) (contentFullH * section.getContentProgress());
            if (animH > 0) {
                int contentTop = headerY + CollapsibleSection.headerHeight() + CONTENT_TOP_GAP;
                for (int i = 0; i < lines.length; i++) {
                    int lineY = contentTop + 4 + i * LINE_HEIGHT;
                    if (mouseX >= headerX + 2 && mouseX < headerX + headerW - 2
                            && mouseY >= lineY && mouseY < lineY + LINE_HEIGHT) {
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
     *
     * @param lineIndex 被点击的行索引
     * @return true 如果点击由该行消费
     */
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        return false;
    }

    // ======================== 状态查询 ========================

    public boolean isExpanded() {
        return section.isExpanded();
    }

    /**
     * 设置展开/折叠状态（直接跳转，无动画）。
     */
    public void setExpanded(boolean expanded) {
        section.setExpanded(expanded);
    }

    /**
     * 计算分区占用的总高度（含动画进度）。
     */
    public int totalHeight(int contentW) {
        int contentFullH = getCachedLines().length * LINE_HEIGHT + 6;
        return section.totalHeight(contentFullH);
    }
}
