package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import com.mojang.math.Axis;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** 标签关闭按钮尺寸（悬停标签时显示在右侧）。 */
    private static final int CLOSE_BTN_SIZE = 12;
    /** 标题与关闭按钮的间距。 */
    private static final int CLOSE_GAP = 4;
    /** 关闭按钮悬停背景色（半透明白，叠加在标签底色上）。 */
    private static final int CLOSE_HOVER_BG = 0x3DFFFFFF;

    /** 悬停动画状态：以标签的稳定 ID 为键，条目增删后动画不会错位。 */
    private final Map<Object, AnimFloat> hoverById = new HashMap<>();
    private int scrollX;

    /**
     * 单个标签页的描述：稳定 ID、图标、标题、关联条目下标（-1 表示无关联，如外部打开的容器）。
     * 稳定 ID 用于动画状态追踪，须在条目增删间保持稳定（如归一化键）。
     */
    public record Tab(Object stableId, ItemStack icon, Component title, int entryIndex) {
        boolean hasIcon() {
            return icon != null && !icon.isEmpty();
        }

        /** 仅有关联条目（可被移除）的标签才显示关闭按钮。 */
        boolean hasCloseButton() {
            return entryIndex >= 0;
        }
    }

    /**
     * 标签点击命中结果：命中的标签与是否命中关闭按钮（关闭按钮优先于切换）。
     */
    public record TabHit(@Nullable Tab tab, boolean onCloseButton) {
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
            boolean hovered = isHovered(mouseX, mouseY, tabX, tabTop, tabW, tabH);
            float t = hoverById.computeIfAbsent(tab.stableId(), k -> AnimFloat.hover()).track(hovered);
            // 非活动标签：默认 p7，悬浮渐变到 p1
            int fillColor = ColorAnimation.lerpRGB(DarkUiPalette.p7(), DarkUiPalette.p1(), t);
            SdfRenderer.drawRoundedRectTopOnly(g, tabX, tabTop, tabW, tabH,
                    TAB_RADIUS, fillColor, TAB_INACTIVE_ALPHA);
            int textColor = ThemeManager.getTextColor();
            drawTabContent(g, mc, tab, tabX, tabTop, tabH, textColor);
            if (tab.hasCloseButton() && hovered) {
                drawCloseButton(g, tabX, tabW, tabTop, tabH, mouseX, mouseY, textColor);
            }
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
                    int textColor = ThemeManager.getHoverTextColor();
                    drawTabContent(g, mc, tab, tabX, y, height - 1, textColor);
                    if (tab.hasCloseButton() && isHovered(mouseX, mouseY, tabX, y, tabW, height - 1)) {
                        drawCloseButton(g, tabX, tabW, y, height - 1, mouseX, mouseY, textColor);
                    }
                    break;
                }
                tabX += tabW + TAB_GAP;
            }
        }

        // 4. 清理已不存在的标签的悬停动画状态（防泄漏与错位）
        if (hoverById.size() > tabs.size() * 2) {
            Set<Object> liveIds = new HashSet<>();
            for (Tab tab : tabs) {
                liveIds.add(tab.stableId());
            }
            hoverById.keySet().retainAll(liveIds);
        }
    }

    /**
     * 绘制标签关闭按钮（×）：仅当鼠标悬停在标签上时调用；
     * 按钮自身悬停时叠加半透明圆底（Edge 风格）。
     */
    private void drawCloseButton(GuiGraphics g, int tabX, int tabW, int tabTop, int tabH,
                                 double mouseX, double mouseY, int iconColor) {
        int btnX = tabX + tabW - PAD_H - CLOSE_BTN_SIZE;
        int btnY = tabTop + (tabH - CLOSE_BTN_SIZE) / 2;
        int cx = btnX + CLOSE_BTN_SIZE / 2;
        int cy = btnY + CLOSE_BTN_SIZE / 2;
        if (mouseX >= btnX && mouseX < btnX + CLOSE_BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + CLOSE_BTN_SIZE) {
            SdfRenderer.drawCircle(g, cx, cy, CLOSE_BTN_SIZE / 2, CLOSE_HOVER_BG);
            g.flush();
        }
        drawCrossIcon(g, cx, cy, iconColor);
    }

    /** 用两条 45° 斜线绘制 × 图标（细线居中）。 */
    private void drawCrossIcon(GuiGraphics g, int cx, int cy, int color) {
        int half = 3;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        g.hLine(-half, half, 0, color);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-45));
        g.hLine(-half, half, 0, color);
        g.pose().popPose();
    }

    private static boolean isHovered(double mouseX, double mouseY, int tabX, int tabTop, int tabW, int tabH) {
        return mouseX >= tabX && mouseX < tabX + tabW
                && mouseY >= tabTop && mouseY < tabTop + tabH;
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
     * 返回鼠标命中的标签页与关闭按钮状态，未命中返回 {@code null}。
     * 命中区域与标签实际绘制区域一致：活动标签顶满（从 y 开始），非活动标签顶部缩进。
     */
    @Nullable
    public TabHit handleClick(double mouseX, double mouseY, int x, int y, int width, int height,
                              @Nullable Tab activeTab, List<Tab> tabs) {
        if (tabs.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return null;

        int tabX = x + 4 - scrollX;
        for (Tab tab : tabs) {
            int tabW = tabWidth(mc, tab);
            // 活动标签与绘制一致：顶满（y 起）；非活动标签缩进 TAB_TOP_INSET，顶部 2px 属于活动标签专属区域
            boolean selected = activeTab != null && tab.entryIndex() == activeTab.entryIndex();
            int tabTop = selected ? y : y + TAB_TOP_INSET;
            int tabH = selected ? height - 1 : height - TAB_TOP_INSET - 1;
            if (mouseX >= tabX && mouseX < tabX + tabW
                    && mouseY >= tabTop && mouseY < tabTop + tabH) {
                boolean onClose = tab.hasCloseButton() && isOverCloseButton(mouseX, mouseY, tabX, tabW, tabTop, tabH);
                return new TabHit(tab, onClose);
            }
            tabX += tabW + TAB_GAP;
        }
        return null;
    }

    private static boolean isOverCloseButton(double mouseX, double mouseY, int tabX, int tabW, int tabTop, int tabH) {
        int btnX = tabX + tabW - PAD_H - CLOSE_BTN_SIZE;
        int btnY = tabTop + (tabH - CLOSE_BTN_SIZE) / 2;
        return mouseX >= btnX && mouseX < btnX + CLOSE_BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + CLOSE_BTN_SIZE;
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
        int closeW = tab.hasCloseButton() ? CLOSE_GAP + CLOSE_BTN_SIZE : 0;
        return PAD_H + iconW + gap + mc.font.width(name) + closeW + PAD_H;
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
