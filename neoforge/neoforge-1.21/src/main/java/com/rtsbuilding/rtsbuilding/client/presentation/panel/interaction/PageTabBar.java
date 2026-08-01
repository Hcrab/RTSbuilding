package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交互面板顶部的容器标签栏（仿 Edge 浏览器标签页）：
 * 每个框选到的容器目标一个标签，点击标签直接打开对应容器，
 * 当前打开的容器标签高亮；标签数量超出可用宽度时支持横向滚动。
 *
 * <p>样式：深色工具栏底 + 顶部圆角标签；非活动标签顶部缩进、半透明浮起，
 * 悬停渐亮；活动标签顶满凸起、亮色填充与工具栏底自然分界。</p>
 */
public final class PageTabBar {

    public static final int TAB_BAR_H = 16;
    /** 标签条深色底颜色（由宿主面板负责铺底，可超出标签条区域）。 */
    public static final int TAB_BAR_BG_COLOR = 0x55000000;

    private static final int ICON_SIZE = 12;
    private static final int ICON_TEXT_GAP = 4;
    private static final int PAD_H = 8;
    private static final int TAB_GAP = 2;
    /** 非活动标签顶部缩进（活动标签顶满，形成 Edge 式凸起感）。 */
    private static final int TAB_TOP_INSET = 2;
    /** 标签顶部圆角半径。 */
    private static final float TAB_RADIUS = 8.0f;
    /** 非活动标签底色不透明度。 */
    private static final float TAB_INACTIVE_ALPHA = 0.9f;

    private final Map<Integer, AnimFloat> hoverById = new HashMap<>();
    private int scrollX;

    /**
     * 单个标签页的描述：图标、标题、关联条目下标（-1 表示无关联，如外部打开的容器）。
     */
    public record Tab(ItemStack icon, Component title, int entryIndex) {
        boolean hasIcon() {
            return icon != null && !icon.isEmpty();
        }
    }

    /**
     * 渲染标签条区域内的所有标签页（仿 Edge：非活动标签先画，活动标签最后画并覆盖）。
     *
     * @param x、y、width、height 标签条区域（绝对坐标）。
     */
    public void render(GuiGraphics g, int x, int y, int width, int height,
                       int mouseX, int mouseY, @Nullable Tab activeTab, List<Tab> tabs) {
        if (height <= 0 || tabs.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        int totalW = computeTotalWidth(mc, tabs);
        int maxScroll = Math.max(0, totalW - width + 8);
        scrollX = Mth.clamp(scrollX, 0, maxScroll);

        // 2. 非活动标签：顶部缩进、半透明浮起，悬停渐亮
        int tabX = x + 4 - scrollX;
        for (Tab tab : tabs) {
            int tabW = tabWidth(mc, tab);
            boolean selected = activeTab != null && tab.entryIndex() == activeTab.entryIndex();
            if (selected) {
                tabX += tabW + TAB_GAP;
                continue;
            }
            int tabTop = y + TAB_TOP_INSET;
            int tabH = height - TAB_TOP_INSET - 1;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabTop && mouseY < tabTop + tabH;
            float t = hoverById.computeIfAbsent(tab.entryIndex(), k -> AnimFloat.hover()).track(hovered);
            // 非活动标签：默认 p7，悬浮渐变到 p1
            int fillColor = ColorAnimation.lerpRGB(DarkUiPalette.p7(), DarkUiPalette.p1(), t);
            SdfRenderer.drawRoundedRectTopOnly(g, tabX, tabTop, tabW, tabH,
                    TAB_RADIUS, fillColor, TAB_INACTIVE_ALPHA);
            drawTabContent(g, mc, tab, tabX, tabTop, tabH, ThemeManager.getTextColor());
            tabX += tabW + TAB_GAP;
        }

        // 3. 活动标签：顶满凸起、亮色填充，最后画以覆盖相邻标签
        if (activeTab != null) {
            int activeIndex = activeTab.entryIndex();
            tabX = x + 4 - scrollX;
            for (Tab tab : tabs) {
                int tabW = tabWidth(mc, tab);
                if (tab.entryIndex() == activeIndex) {
                    // 选中标签：恒用 p5（toggleOn），文字保持亮白；底边内缩 1px 不贴出标签栏
                    SdfRenderer.drawRoundedRectTopOnly(g, tabX, y, tabW, height - 1,
                            TAB_RADIUS, DarkUiPalette.toggleOn(), 1.0f);
                    drawTabContent(g, mc, tab, tabX, y, height - 1, ThemeManager.getHoverTextColor());
                    break;
                }
                tabX += tabW + TAB_GAP;
            }
        }
    }

    /**
     * 绘制单个标签的图标与标题（垂直居中于标签内容区）。
     */
    private void drawTabContent(GuiGraphics g, Minecraft mc, Tab tab,
                                int tabX, int tabTop, int tabH, int textColor) {
        int cursorX = tabX + PAD_H;
        int centerY = tabTop + tabH / 2;
        if (tab.hasIcon()) {
            renderItemIcon(g, tab.icon(), cursorX + ICON_SIZE / 2, centerY);
            cursorX += ICON_SIZE + ICON_TEXT_GAP;
        }
        int textY = centerY - mc.font.lineHeight / 2;
        TextRenderer.draw(g, tab.title() == null ? "" : tab.title().getString(),
                cursorX, textY, textColor);
    }


    /**
     * 滚轮横向滚动标签栏；返回是否消费了滚轮事件。
     */
    public boolean handleScroll(double scrollY, int width, List<Tab> tabs) {
        if (tabs.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;

        int totalW = computeTotalWidth(mc, tabs);
        int maxScroll = Math.max(0, totalW - width + 8);
        if (maxScroll <= 0) return false;
        scrollX = Mth.clamp(scrollX - (int) Math.round(scrollY * 30), 0, maxScroll);
        return true;
    }

    /**
     * 返回鼠标命中的标签页，未命中返回 {@code null}。
     * 命中区域与标签实际绘制区域一致（含活动标签顶满部分）。
     */
    @Nullable
    public Tab handleClick(double mouseX, double mouseY, int x, int y, int width, int height, List<Tab> tabs) {
        if (tabs.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return null;

        int tabH = height - TAB_TOP_INSET - 1;
        if (tabH <= 0) return null;

        int tabX = x + 4 - scrollX;
        int tabY = y + TAB_TOP_INSET;
        for (Tab tab : tabs) {
            int tabW = tabWidth(mc, tab);
            if (mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabY && mouseY < tabY + tabH) {
                return tab;
            }
            tabX += tabW + TAB_GAP;
        }
        return null;
    }

    private int computeTotalWidth(Minecraft mc, List<Tab> tabs) {
        int total = 0;
        for (Tab tab : tabs) {
            total += tabWidth(mc, tab) + TAB_GAP;
        }
        return total > 0 ? total - TAB_GAP : 0;
    }

    private static int tabWidth(Minecraft mc, Tab tab) {
        String name = tab.title() == null ? "" : tab.title().getString();
        int iconW = tab.hasIcon() ? ICON_SIZE : 0;
        int gap = tab.hasIcon() ? ICON_TEXT_GAP : 0;
        return PAD_H + iconW + gap + mc.font.width(name) + PAD_H;
    }




    private void renderItemIcon(GuiGraphics g, ItemStack stack, int centerX, int centerY) {
        var pose = g.pose();
        pose.pushPose();
        float scale = (float) ICON_SIZE / 16.0f;
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1.0f);
        g.renderItem(stack, -8, -8);
        pose.popPose();
    }
}
