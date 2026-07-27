package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiSelectionAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelHeaderLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelHeaderStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.FontRenderer;

/**
 * 底栏框体与头部控件的 Minecraft 绘制适配器。
 *
 * <p>本类只消费 Core 快照、Kit 几何/主题和页签动画值，不执行切页、刷新或开窗动作。
 * 头部所有文字均关闭阴影，避免后续浮窗覆盖时从半透明批次中穿透。</p>
 */
public final class BottomPanelHeaderRenderer {
    private BottomPanelHeaderRenderer() {
    }

    public static void render(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            BottomPanelHeaderLayout layout,
            BottomBarUiState state,
            UiSelectionAnimationSet<BottomBarUiTab> animations,
            boolean animationsEnabled,
            String creativeLabel,
            String storageLabel,
            String blueprintLabel,
            String pluginLabel,
            int mouseX,
            int mouseY) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(layout.panel.x, layout.panel.y,
                        layout.panel.width, layout.panel.height),
                BottomPanelHeaderStyle.PANEL_BACKGROUND,
                BottomPanelHeaderStyle.PANEL_BORDER_LIGHT,
                BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        fill(graphics, layout.header, BottomPanelHeaderStyle.HEADER_BACKGROUND);
        graphics.drawString(
                font, "RTS", layout.logoX(), layout.logoY(),
                argb(BottomPanelHeaderStyle.LOGO_TEXT), false);

        for (BottomPanelHeaderLayout.TabArea tabArea : layout.tabs) {
            boolean active = state.activeTab == tabArea.tab;
            boolean hovered = tabArea.area.contains(mouseX, mouseY);
            drawFrame(
                    canvas,
                    tabArea.area,
                    BottomPanelHeaderStyle.tabBackground(active, hovered),
                    BottomPanelHeaderStyle.tabBorder(active),
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK);
            double strength = animations.value(
                    tabArea.tab, active, animationsEnabled);
            UiColor overlay = UiColor.interpolate(
                    BottomPanelHeaderStyle.TRANSPARENT,
                    BottomPanelHeaderStyle.TAB_ANIMATION_OVERLAY,
                    strength);
            fillInside(graphics, tabArea.area, overlay);
            drawCenteredNoShadow(
                    graphics, font,
                    trim(font, tabLabel(
                            tabArea.tab,
                            creativeLabel,
                            storageLabel,
                            blueprintLabel),
                            tabArea.area.width
                                    - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    tabArea.area,
                    BottomPanelHeaderStyle.tabText(active));
        }

        if (layout.selectedStatus.width > 0) {
            graphics.drawString(
                    font,
                    trim(font, state.selectedStatus,
                            layout.selectedStatus.width),
                    layout.selectedStatus.x,
                    layout.selectedStatus.y,
                    argb(BottomPanelHeaderStyle.STATUS_TEXT),
                    false);
        }

        boolean refreshHovered = layout.refresh.contains(mouseX, mouseY);
        boolean refreshDirty = state.activeTab == BottomBarUiTab.STORAGE
                && !state.storageScanning
                && state.refreshHighlighted;
        UiColor refreshBorder =
                BottomPanelHeaderStyle.refreshBorder(refreshDirty);
        drawFrame(
                canvas,
                layout.refresh,
                BottomPanelHeaderStyle.refreshBackground(
                        state.storageScanning, refreshDirty, refreshHovered),
                refreshBorder,
                refreshDirty
                        ? refreshBorder
                        : BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        drawCenteredNoShadow(
                graphics, font, "R", layout.refresh,
                refreshDirty
                        ? BottomPanelHeaderStyle.TAB_ACTIVE_TEXT
                        : BottomPanelHeaderStyle.ACTION_TEXT);

        drawFrame(
                canvas,
                layout.guide,
                BottomPanelHeaderStyle.actionBackground(
                        layout.guide.contains(mouseX, mouseY)),
                BottomPanelHeaderStyle.ACTION_BORDER,
                BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        drawCenteredNoShadow(
                graphics, font, "i", layout.guide,
                BottomPanelHeaderStyle.ACTION_TEXT);

        if (layout.pluginVisible) {
            drawFrame(
                    canvas,
                    layout.plugin,
                    BottomPanelHeaderStyle.pluginBackground(
                            layout.plugin.contains(mouseX, mouseY)),
                    BottomPanelHeaderStyle.ACTION_BORDER,
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK);
            drawCenteredNoShadow(
                    graphics, font,
                    trim(font, pluginLabel,
                            layout.plugin.width
                                    - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    layout.plugin,
                    BottomPanelHeaderStyle.PLUGIN_TEXT);
        }
    }

    private static String tabLabel(
            BottomBarUiTab tab,
            String creativeLabel,
            String storageLabel,
            String blueprintLabel) {
        if (tab == BottomBarUiTab.CREATIVE) {
            return creativeLabel;
        }
        return tab == BottomBarUiTab.BLUEPRINTS
                ? blueprintLabel
                : storageLabel;
    }

    private static String trim(FontRenderer font, String text, int width) {
        return RtsClientUiUtil.trimToWidth(
                font, text == null ? "" : text, Math.max(0, width));
    }

    private static void drawFrame(
            UiCanvas2D canvas,
            BottomPanelHeaderLayout.Area area,
            UiColor background,
            UiColor light,
            UiColor dark) {
        UiCompactFrameRenderer.frame(
                canvas, new UiRect(area.x, area.y, area.width, area.height),
                background, light, dark);
    }

    private static void drawCenteredNoShadow(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            String text,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        int textX = area.x + (area.width - font.getStringWidth(text)) / 2;
        int textY = area.y + Math.max(0, (area.height - font.FONT_HEIGHT) / 2);
        graphics.drawString(font, text, textX, textY, argb(color), false);
    }

    private static void fillInside(
            LegacyGuiGraphics graphics,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        int inset = BottomPanelHeaderLayout.TAB_ANIMATION_INSET;
        graphics.fill(
                area.x + inset,
                area.y + inset,
                area.x + area.width - inset,
                area.y + area.height - inset,
                argb(color));
    }

    private static void fill(
            LegacyGuiGraphics graphics,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        graphics.fill(
                area.x, area.y,
                area.x + area.width,
                area.y + area.height,
                argb(color));
    }

    private static int argb(UiColor color) {
        return color.toArgb();
    }
}
