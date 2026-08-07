package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiSelectionAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelHeaderLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelHeaderStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 底栏边框和头部控件的 26.1 Extractor 绘制适配器。
 *
 * <p>本类只消费 Core 快照、Kit 几何/主题和页签动画，不执行切页、刷新或开窗动作；
 * 所有头部文字均无阴影，浮窗覆盖时不会穿透半透明批次。</p>
 */
public final class BottomPanelHeaderRenderer {
    private BottomPanelHeaderRenderer() {
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            BottomPanelHeaderLayout layout,
            BottomBarUiState state,
            UiSelectionAnimationSet<BottomBarUiTab> animations,
            UiControlAnimationRegistry<String> controlAnimations,
            boolean animationsEnabled,
            String creativeLabel,
            String storageLabel,
            String blueprintLabel,
            String pluginLabel,
            int mouseX,
            int mouseY) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        UiCompactFrameRenderer.frame(canvas,
                new UiRect(layout.panel.x, layout.panel.y, layout.panel.width, layout.panel.height),
                BottomPanelHeaderStyle.PANEL_BACKGROUND,
                BottomPanelHeaderStyle.PANEL_BORDER_LIGHT,
                BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        fill(graphics, layout.header, BottomPanelHeaderStyle.HEADER_BACKGROUND);
        graphics.text(font, "RTS", layout.logoX(), layout.logoY(),
                BottomPanelHeaderStyle.LOGO_TEXT.toArgb(), false);

        for (BottomPanelHeaderLayout.TabArea tabArea : layout.tabs) {
            boolean active = state.activeTab == tabArea.tab;
            boolean hovered = tabArea.area.contains(mouseX, mouseY);
            double hoverStrength = controlAnimations.update("tab." + tabArea.tab.name(),
                    new UiControlState(true, active, false, false, "")
                            .withInteraction(hovered, false, false),
                    animationsEnabled).hover();
            drawFrame(canvas, tabArea.area,
                    BottomPanelHeaderStyle.tabBackground(active, hoverStrength),
                    BottomPanelHeaderStyle.tabBorder(active),
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK);
            double strength = animations.value(tabArea.tab, active, animationsEnabled);
            UiColor overlay = UiColor.interpolate(BottomPanelHeaderStyle.TRANSPARENT,
                    BottomPanelHeaderStyle.TAB_ANIMATION_OVERLAY, strength);
            fillInside(graphics, tabArea.area, overlay);
            drawCenteredNoShadow(graphics, font, trim(font,
                            tabLabel(tabArea.tab, creativeLabel, storageLabel, blueprintLabel),
                            tabArea.area.width - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    tabArea.area, BottomPanelHeaderStyle.tabText(active));
        }

        if (layout.selectedStatus.width > 0) {
            graphics.text(font, trim(font, state.selectedStatus, layout.selectedStatus.width),
                    layout.selectedStatus.x, layout.selectedStatus.y,
                    BottomPanelHeaderStyle.STATUS_TEXT.toArgb(), false);
        }

        boolean refreshHovered = layout.refresh.contains(mouseX, mouseY);
        boolean refreshDirty = state.activeTab == BottomBarUiTab.STORAGE
                && !state.storageScanning && state.refreshHighlighted;
        UiColor refreshBorder = BottomPanelHeaderStyle.refreshBorder(refreshDirty);
        double refreshHover = controlAnimations.update("refresh",
                UiControlState.enabled().withInteraction(refreshHovered, false, false),
                animationsEnabled).hover();
        drawFrame(canvas, layout.refresh,
                BottomPanelHeaderStyle.refreshBackground(
                        state.storageScanning, refreshDirty, refreshHover),
                refreshBorder, refreshDirty ? refreshBorder
                        : BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, "R", layout.refresh,
                refreshDirty ? BottomPanelHeaderStyle.TAB_ACTIVE_TEXT
                        : BottomPanelHeaderStyle.ACTION_TEXT);

        double guideHover = controlAnimations.update("guide",
                UiControlState.enabled().withInteraction(
                        layout.guide.contains(mouseX, mouseY), false, false),
                animationsEnabled).hover();
        drawFrame(canvas, layout.guide,
                BottomPanelHeaderStyle.actionBackground(guideHover),
                BottomPanelHeaderStyle.ACTION_BORDER,
                BottomPanelHeaderStyle.PANEL_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, "i", layout.guide,
                BottomPanelHeaderStyle.ACTION_TEXT);

        if (layout.pluginVisible) {
            double pluginHover = controlAnimations.update("plugin",
                    UiControlState.enabled().withInteraction(
                            layout.plugin.contains(mouseX, mouseY), false, false),
                    animationsEnabled).hover();
            drawFrame(canvas, layout.plugin,
                    BottomPanelHeaderStyle.pluginBackground(pluginHover),
                    BottomPanelHeaderStyle.ACTION_BORDER,
                    BottomPanelHeaderStyle.PANEL_BORDER_DARK);
            drawCenteredNoShadow(graphics, font,
                    trim(font, pluginLabel, layout.plugin.width
                            - BottomPanelHeaderLayout.TAB_TEXT_INSET * 2),
                    layout.plugin, BottomPanelHeaderStyle.PLUGIN_TEXT);
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
        return tab == BottomBarUiTab.BLUEPRINTS ? blueprintLabel : storageLabel;
    }

    private static String trim(Font font, String text, int width) {
        return RtsClientUiUtil.trimToWidth(font, text == null ? "" : text,
                Math.max(0, width));
    }

    private static void drawFrame(
            UiCanvas2D canvas,
            BottomPanelHeaderLayout.Area area,
            UiColor background,
            UiColor light,
            UiColor dark) {
        UiCompactFrameRenderer.frame(canvas,
                new UiRect(area.x, area.y, area.width, area.height),
                background, light, dark);
    }

    private static void drawCenteredNoShadow(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        int textX = area.x + (area.width - font.width(text)) / 2;
        int textY = area.y + Math.max(0, (area.height - font.lineHeight) / 2);
        graphics.text(font, text, textX, textY, color.toArgb(), false);
    }

    private static void fillInside(
            GuiGraphicsExtractor graphics,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        int inset = BottomPanelHeaderLayout.TAB_ANIMATION_INSET;
        graphics.fill(area.x + inset, area.y + inset,
                area.x + area.width - inset, area.y + area.height - inset,
                color.toArgb());
    }

    private static void fill(
            GuiGraphicsExtractor graphics,
            BottomPanelHeaderLayout.Area area,
            UiColor color) {
        graphics.fill(area.x, area.y, area.x + area.width,
                area.y + area.height, color.toArgb());
    }
}
