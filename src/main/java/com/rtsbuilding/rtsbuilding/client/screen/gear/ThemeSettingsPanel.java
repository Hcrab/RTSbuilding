package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import java.io.IOException;
import java.util.List;

/**
 * 1.12 原生主题选择与预览浮窗。
 *
 * <p>该类负责同一 UIKit 布局下的选择、预览和本地持久化；不解析主题 JSON、不烘焙纹理、
 * 不持有服务器配置，也不决定 RTS 功能能否使用。主题切换只会影响现有窗口下一帧读取到的语义色。</p>
 */
public final class ThemeSettingsPanel extends RtsWindowPanel {
    private static final int ROW_H = 32;
    private static final int ACTION_H = 22;
    private static final int PREVIEW_TITLE_X = 12;
    private static final int PREVIEW_TITLE_Y = 10;
    private static final int PREVIEW_BAR_X = 8;
    private static final int PREVIEW_BAR_Y = 26;
    private static final int PREVIEW_BAR_HORIZONTAL_INSET = 16;
    private static final int PREVIEW_BAR_H = 26;
    private static final int PREVIEW_CELL_Y = 64;
    private static final int PREVIEW_CELL_X = 14;
    private static final int PREVIEW_CELL_PITCH = 72;
    private static final int PREVIEW_CELL_W = 62;
    private static final int PREVIEW_CELL_H = 28;
    private static final int PREVIEW_SAMPLE_Y = 46;
    private static final int PREVIEW_SAMPLE_MIN_W = 40;
    private static final int PREVIEW_SAMPLE_HORIZONTAL_INSET = 28;
    private static final int PREVIEW_SAMPLE_TEXT_X = 22;
    private static final int PREVIEW_SAMPLE_PRIMARY_Y = 52;
    private static final int PREVIEW_SAMPLE_SECONDARY_Y = 64;
    private String draftId;
    private int scroll;
    private String statusKey = "screen.rtsbuilding.theme.status.ready";

    public void open() {
        draftId = UiThemeRuntime.manager().active().id();
        scroll = 0;
        statusKey = "screen.rtsbuilding.theme.status.ready";
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(LegacyGuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, screen.font(), screen);
        ThemeSettingsLayout.Geometry layout = geometry();
        UiThemeDefinition selected = selected();
        drawThemeList(graphics, canvas, layout, mouseX, mouseY, selected);
        drawPreview(graphics, canvas, layout.preview, selected);
        drawActions(graphics, canvas, layout.actions, mouseX, mouseY, selected);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        ThemeSettingsLayout.Geometry layout = geometry();
        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int rows = visibleRows(layout);
        UiRect list = layout.list;
        if (list.contains(mouseX, mouseY)) {
            int index = scroll + (int) ((mouseY - list.getY() - ThemeSettingsLayout.LIST_INSET) / ROW_H);
            if (index >= 0 && index < themes.size() && index < scroll + rows) {
                draftId = themes.get(index).id();
                statusKey = "screen.rtsbuilding.theme.status.selected";
            }
            return;
        }
        UiRect apply = applyBounds(layout.actions);
        if (apply.contains(mouseX, mouseY)) {
            applyDraft();
            return;
        }
        if (cancelBounds(layout.actions).contains(mouseX, mouseY)) setOpen(false);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        ThemeSettingsLayout.Geometry layout = geometry();
        if (!layout.list.contains(mouseX, mouseY)) return false;
        int maximum = Math.max(0, UiThemeRuntime.registry().snapshot().size() - visibleRows(layout));
        scroll = MathHelper.clamp(scroll + (scrollY > 0.0D ? -1 : 1), 0, maximum);
        return true;
    }

    @Override protected ITextComponent getTitle() { return new TextComponentTranslation("screen.rtsbuilding.theme.title"); }
    @Override protected int getDefaultWidth() { return ThemeSettingsLayout.PREFERRED_WINDOW_W; }
    @Override protected int getDefaultHeight() { return ThemeSettingsLayout.PREFERRED_WINDOW_H; }
    @Override protected int getMinWindowWidth() { return ThemeSettingsLayout.MIN_WINDOW_W; }
    @Override protected int getMinWindowHeight() { return ThemeSettingsLayout.MIN_WINDOW_H; }

    @Override
    protected void computeDefaultPosition() {
        windowX = Math.max(8, (screen.width - windowWidth) / 2);
        windowY = Math.max(28, (screen.height - windowHeight) / 2);
    }

    private ThemeSettingsLayout.Geometry geometry() {
        return ThemeSettingsLayout.geometry(contentX(), contentY(), contentWidth(), contentHeight());
    }

    private void drawThemeList(LegacyGuiGraphics graphics, MinecraftUiCanvas canvas,
                               ThemeSettingsLayout.Geometry layout, int mouseX, int mouseY,
                               UiThemeDefinition selected) {
        UiCompactFrameRenderer.frame(canvas, layout.list,
                selected.color(UiThemeToken.SURFACE), selected.color(UiThemeToken.BORDER_STRONG),
                selected.color(UiThemeToken.BORDER_SOFT));
        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int rows = visibleRows(layout);
        scroll = Math.min(scroll, Math.max(0, themes.size() - rows));
        int rowY = (int) layout.list.getY() + ThemeSettingsLayout.LIST_INSET;
        int rowX = (int) layout.list.getX() + ThemeSettingsLayout.LIST_INSET;
        int rowW = (int) layout.list.getWidth() - ThemeSettingsLayout.DOUBLE_LIST_INSET;
        for (int index = scroll; index < themes.size() && index < scroll + rows; index++) {
            UiThemeDefinition theme = themes.get(index);
            boolean active = theme.id().equals(draftId);
            UiRect row = new UiRect(rowX, rowY, rowW, ROW_H - 2);
            UiColor background = active ? theme.color(UiThemeToken.CONTROL_SELECTED)
                    : selected.color(UiThemeToken.CONTROL_IDLE);
            UiCompactFrameRenderer.frame(canvas, row, background,
                    active ? theme.color(UiThemeToken.ACCENT_PRIMARY) : selected.color(UiThemeToken.BORDER_SOFT),
                    selected.color(UiThemeToken.SURFACE_SUNKEN));
            String name = I18n.format(theme.nameKey());
            graphics.drawString(screen.font(), screen.font().trimStringToWidth(name, rowW - 12),
                    rowX + ThemeSettingsLayout.THEME_NAME_X, rowY + ThemeSettingsLayout.THEME_NAME_Y,
                    selected.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
            graphics.drawString(screen.font(), theme.renderMode().name(),
                    rowX + ThemeSettingsLayout.THEME_NAME_X, rowY + ThemeSettingsLayout.THEME_MODE_Y,
                    selected.color(UiThemeToken.TEXT_MUTED).toArgb(), false);
            rowY += ROW_H;
        }
    }

    private void drawPreview(LegacyGuiGraphics graphics, MinecraftUiCanvas canvas,
                             UiRect bounds, UiThemeDefinition theme) {
        UiCompactFrameRenderer.frame(canvas, bounds, theme.color(UiThemeToken.SURFACE),
                theme.color(UiThemeToken.BORDER_STRONG), theme.color(UiThemeToken.BORDER_SOFT));
        int x = (int) bounds.getX();
        int y = (int) bounds.getY();
        int w = (int) bounds.getWidth();
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.theme.preview"),
                x + PREVIEW_TITLE_X, y + PREVIEW_TITLE_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        canvas.fill(new UiRect(x + PREVIEW_BAR_X, y + PREVIEW_BAR_Y,
                w - PREVIEW_BAR_HORIZONTAL_INSET, PREVIEW_BAR_H), theme.color(UiThemeToken.TOP_BAR));
        int cellY = y + PREVIEW_CELL_Y;
        for (int index = 0; index < 3; index++) {
            UiThemeToken token = index == 0 ? UiThemeToken.CONTROL_IDLE
                    : index == 1 ? UiThemeToken.CONTROL_HOVER : UiThemeToken.CONTROL_SELECTED;
            UiRect cell = new UiRect(x + PREVIEW_CELL_X + index * PREVIEW_CELL_PITCH,
                    cellY, PREVIEW_CELL_W, PREVIEW_CELL_H);
            UiCompactFrameRenderer.frame(canvas, cell, theme.color(token),
                    theme.color(UiThemeToken.BORDER_STRONG), theme.color(UiThemeToken.SURFACE_SUNKEN));
        }
        canvas.fill(new UiRect(x + PREVIEW_CELL_X, cellY + PREVIEW_SAMPLE_Y,
                Math.max(PREVIEW_SAMPLE_MIN_W, w - PREVIEW_SAMPLE_HORIZONTAL_INSET), PREVIEW_CELL_H),
                theme.color(UiThemeToken.SURFACE_SUNKEN));
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.theme.sample.primary"),
                x + PREVIEW_SAMPLE_TEXT_X, cellY + PREVIEW_SAMPLE_PRIMARY_Y,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        graphics.drawString(screen.font(), I18n.format("screen.rtsbuilding.theme.sample.secondary"),
                x + PREVIEW_SAMPLE_TEXT_X, cellY + PREVIEW_SAMPLE_SECONDARY_Y,
                theme.color(UiThemeToken.TEXT_SECONDARY).toArgb(), false);
    }

    private void drawActions(LegacyGuiGraphics graphics, MinecraftUiCanvas canvas,
                             UiRect actions, int mouseX, int mouseY, UiThemeDefinition theme) {
        UiCompactFrameRenderer.frame(canvas, actions, theme.color(UiThemeToken.SURFACE_RAISED),
                theme.color(UiThemeToken.BORDER_STRONG), theme.color(UiThemeToken.SURFACE_SUNKEN));
        drawAction(graphics, canvas, applyBounds(actions), mouseX, mouseY,
                I18n.format("screen.rtsbuilding.theme.apply"), theme, true);
        drawAction(graphics, canvas, cancelBounds(actions), mouseX, mouseY,
                I18n.format("gui.cancel"), theme, false);
        graphics.drawString(screen.font(), I18n.format(statusKey), (int) actions.getX() + 8,
                (int) actions.getY() + ThemeSettingsLayout.ACTION_STATUS_Y,
                theme.color(UiThemeToken.TEXT_SECONDARY).toArgb(), false);
    }

    private void drawAction(LegacyGuiGraphics graphics, MinecraftUiCanvas canvas, UiRect bounds,
                            int mouseX, int mouseY, String text, UiThemeDefinition theme, boolean primary) {
        boolean hover = bounds.contains(mouseX, mouseY);
        UiCompactFrameRenderer.frame(canvas, bounds,
                primary ? (hover ? theme.color(UiThemeToken.CONTROL_HOVER) : theme.color(UiThemeToken.CONTROL_SELECTED))
                        : (hover ? theme.color(UiThemeToken.CONTROL_HOVER) : theme.color(UiThemeToken.CONTROL_IDLE)),
                theme.color(UiThemeToken.BORDER_STRONG), theme.color(UiThemeToken.SURFACE_SUNKEN));
        int x = (int) bounds.getX() + ((int) bounds.getWidth() - screen.font().getStringWidth(text)) / 2;
        graphics.drawString(screen.font(), text, x,
                ThemeSettingsLayout.actionTextTop((int) bounds.getY(), ACTION_H, screen.font().FONT_HEIGHT),
                theme.color(UiThemeToken.TEXT_ON_ACCENT).toArgb(), false);
    }

    private void applyDraft() {
        if (draftId == null || !UiThemeRuntime.registry().contains(draftId)) {
            statusKey = "screen.rtsbuilding.theme.status.missing";
            return;
        }
        UiThemeRuntime.manager().activate(draftId);
        try {
            UiThemeStorage.defaultStorage().saveActiveId(draftId);
            statusKey = "screen.rtsbuilding.theme.status.applied";
        } catch (IOException failure) {
            statusKey = "screen.rtsbuilding.theme.status.save_failed";
            RtsbuildingMod.LOGGER.warn("Unable to save 1.12 UI theme selection", failure);
        }
    }

    private UiThemeDefinition selected() {
        return draftId != null && UiThemeRuntime.registry().contains(draftId)
                ? UiThemeRuntime.registry().require(draftId) : UiThemeRuntime.manager().active();
    }

    private int visibleRows(ThemeSettingsLayout.Geometry layout) {
        return Math.max(1, ((int) layout.actions.getY() - (int) layout.list.getY()
                - ThemeSettingsLayout.LIST_INSET * 2) / ROW_H);
    }

    private static UiRect applyBounds(UiRect actions) {
        return new UiRect(actions.right() - ThemeSettingsLayout.ACTION_APPLY_RIGHT,
                actions.getY() + 8, 72, ACTION_H);
    }

    private static UiRect cancelBounds(UiRect actions) {
        return new UiRect(actions.right() - ThemeSettingsLayout.ACTION_CANCEL_RIGHT,
                actions.getY() + 8, 72, ACTION_H);
    }
}
