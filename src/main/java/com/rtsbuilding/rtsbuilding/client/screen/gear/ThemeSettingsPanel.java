package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

/**
 * UI 主题选择与预览窗口。
 *
 * <p>本类只管理客户端草稿选择、预览和导入导出入口；主题格式校验、文件边界和 GPU 纹理缓存
 * 继续分别由 {@link UiThemeStorage} 与主题渲染层负责，避免设置窗口成为新的全能类。</p>
 */
public final class ThemeSettingsPanel extends RtsWindowPanel {
    private static final int DEFAULT_W = 520;
    private static final int DEFAULT_H = 330;
    private static final int LIST_W = 176;
    private static final int ROW_H = 34;
    private static final int BUTTON_H = 22;

    private String draftId;
    private String statusKey = "screen.rtsbuilding.theme.status.ready";

    public void open() {
        this.draftId = UiThemeRuntime.manager().active().id();
        this.statusKey = "screen.rtsbuilding.theme.status.ready";
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        int x = contentX() + 8;
        int y = contentY() + 8;
        int h = contentHeight() - 16;
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, LIST_W, h),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);

        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int rowY = y + 6;
        for (UiThemeDefinition theme : themes) {
            if (rowY + ROW_H > y + h - 54) break;
            drawThemeRow(g, canvas, theme, x + 6, rowY, LIST_W - 12, mouseX, mouseY);
            rowY += ROW_H;
        }

        UiThemeDefinition draft = draftTheme();
        int previewX = x + LIST_W + 10;
        int previewW = contentWidth() - LIST_W - 36;
        drawPreview(g, canvas, draft, previewX, y, previewW, h - 62);
        drawActions(g, canvas, x, y + h - 48, LIST_W + 10 + previewW,
                mouseX, mouseY, draft);
    }

    private void drawThemeRow(GuiGraphics g, MinecraftUiCanvas canvas, UiThemeDefinition theme,
                              int x, int y, int w, int mouseX, int mouseY) {
        boolean selected = theme.id().equals(draftId);
        boolean hover = UiRect.contains(x, y, w, ROW_H - 3, mouseX, mouseY);
        UiColor background = selected ? SettingsWindowStyle.TOGGLE_ON
                : hover ? SettingsWindowStyle.STEP_HOVER_BACKGROUND : SettingsWindowStyle.STEP_BACKGROUND;
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, ROW_H - 3), background,
                selected ? SettingsWindowStyle.TOGGLE_ON_BORDER : SettingsWindowStyle.STEP_BORDER,
                SettingsWindowStyle.STEP_DARK_BORDER);
        g.drawString(screen.font(), displayName(theme), x + 8, y + 6,
                SettingsWindowStyle.VALUE.toArgb(), false);
        String mode = theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT
                ? text("screen.rtsbuilding.theme.mode.legacy")
                : text("screen.rtsbuilding.theme.mode.palette");
        g.drawString(screen.font(), mode, x + 8, y + 18,
                SettingsWindowStyle.HINT.toArgb(), false);
    }

    private void drawPreview(GuiGraphics g, MinecraftUiCanvas canvas, UiThemeDefinition theme,
                             int x, int y, int w, int h) {
        UiColor canvasColor = theme.color(UiThemeToken.CANVAS);
        UiColor surface = theme.color(UiThemeToken.SURFACE);
        UiColor raised = theme.color(UiThemeToken.SURFACE_RAISED);
        UiColor border = theme.color(UiThemeToken.BORDER_STRONG);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, h), surface, border,
                theme.color(UiThemeToken.BORDER_SOFT));
        g.fill(x + 8, y + 8, x + w - 8, y + 34, theme.color(UiThemeToken.TOP_BAR).toArgb());
        g.drawString(screen.font(), text("screen.rtsbuilding.theme.preview"), x + 16, y + 17,
                theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        g.fill(x + 8, y + 42, x + w - 8, y + h - 8, canvasColor.toArgb());

        int controlY = y + 54;
        drawSampleControl(g, theme, x + 18, controlY, 72, 24, UiThemeToken.CONTROL_IDLE,
                "screen.rtsbuilding.theme.sample.idle");
        drawSampleControl(g, theme, x + 98, controlY, 72, 24, UiThemeToken.CONTROL_HOVER,
                "screen.rtsbuilding.theme.sample.hover");
        drawSampleControl(g, theme, x + 178, controlY, 72, 24, UiThemeToken.CONTROL_SELECTED,
                "screen.rtsbuilding.theme.sample.active");

        int slotY = controlY + 38;
        for (int i = 0; i < 6; i++) {
            int slotX = x + 18 + i * 34;
            g.fill(slotX, slotY, slotX + 28, slotY + 28,
                    theme.color(i == 2 ? UiThemeToken.SLOT_HOVER : UiThemeToken.SLOT_IDLE).toArgb());
            g.renderOutline(slotX, slotY, 28, 28, border.toArgb());
        }
        g.fill(x + w - 28, slotY, x + w - 22, slotY + 78,
                theme.color(UiThemeToken.SCROLLBAR_TRACK).toArgb());
        g.fill(x + w - 29, slotY + 18, x + w - 21, slotY + 47,
                theme.color(UiThemeToken.SCROLLBAR_THUMB).toArgb());

        int statusY = slotY + 46;
        g.fill(x + 18, statusY, x + w - 42, statusY + 38, raised.toArgb());
        g.drawString(screen.font(), text("screen.rtsbuilding.theme.sample.primary"),
                x + 26, statusY + 7, theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(), false);
        g.drawString(screen.font(), text("screen.rtsbuilding.theme.sample.secondary"),
                x + 26, statusY + 20, theme.color(UiThemeToken.TEXT_SECONDARY).toArgb(), false);
        int chipY = statusY + 48;
        UiThemeToken[] chips = {UiThemeToken.SUCCESS, UiThemeToken.WARNING,
                UiThemeToken.ERROR, UiThemeToken.ACCENT_PRIMARY};
        for (int i = 0; i < chips.length; i++) {
            g.fill(x + 18 + i * 42, chipY, x + 52 + i * 42, chipY + 10,
                    theme.color(chips[i]).toArgb());
        }
    }

    private void drawSampleControl(GuiGraphics g, UiThemeDefinition theme, int x, int y,
                                   int w, int h, UiThemeToken token, String labelKey) {
        g.fill(x, y, x + w, y + h, theme.color(token).toArgb());
        g.renderOutline(x, y, w, h, theme.color(UiThemeToken.BORDER_STRONG).toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), text(labelKey),
                x + w / 2, y + 8, theme.color(UiThemeToken.TEXT_PRIMARY).toArgb());
    }

    private void drawActions(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w,
                             int mouseX, int mouseY, UiThemeDefinition draft) {
        drawButton(g, canvas, x, y, 78, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.reload");
        drawButton(g, canvas, x + 84, y, 78, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.folder");
        drawButton(g, canvas, x + w - 246, y, 74, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.export", draft.renderMode() == UiThemeRenderMode.PALETTE);
        drawButton(g, canvas, x + w - 166, y, 74, BUTTON_H, mouseX, mouseY,
                "gui.cancel");
        drawButton(g, canvas, x + w - 86, y, 86, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.apply");
        g.drawString(screen.font(), text(statusKey), x, y + 30,
                SettingsWindowStyle.HINT.toArgb(), false);
    }

    private void drawButton(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w, int h,
                            int mouseX, int mouseY, String key) {
        drawButton(g, canvas, x, y, w, h, mouseX, mouseY, key, true);
    }

    private void drawButton(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w, int h,
                            int mouseX, int mouseY, String key, boolean enabled) {
        boolean hover = enabled && UiRect.contains(x, y, w, h, mouseX, mouseY);
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, h),
                enabled ? (hover ? SettingsWindowStyle.STEP_HOVER_BACKGROUND
                        : SettingsWindowStyle.STEP_BACKGROUND) : SettingsWindowStyle.VALUE_BACKGROUND,
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), text(key), x + w / 2, y + 7,
                (enabled ? SettingsWindowStyle.VALUE : SettingsWindowStyle.DISABLED_TEXT).toArgb());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int x = contentX() + 8;
        int y = contentY() + 8;
        int h = contentHeight() - 16;
        int rowY = y + 6;
        for (UiThemeDefinition theme : UiThemeRuntime.registry().snapshot()) {
            if (rowY + ROW_H > y + h - 54) break;
            if (UiRect.contains(x + 6, rowY, LIST_W - 12, ROW_H - 3, mouseX, mouseY)) {
                this.draftId = theme.id();
                this.statusKey = "screen.rtsbuilding.theme.status.draft";
                return;
            }
            rowY += ROW_H;
        }
        int actionY = y + h - 48;
        int totalW = contentWidth() - 26;
        if (UiRect.contains(x, actionY, 78, BUTTON_H, mouseX, mouseY)) {
            List<String> errors = UiThemeStorage.defaultStorage().loadAll(UiThemeRuntime.registry());
            statusKey = errors.isEmpty() ? "screen.rtsbuilding.theme.status.reloaded"
                    : "screen.rtsbuilding.theme.status.reload_error";
        } else if (UiRect.contains(x + 84, actionY, 78, BUTTON_H, mouseX, mouseY)) {
            Util.getPlatform().openUri(UiThemeStorage.defaultStorage().directory().toUri());
        } else if (UiRect.contains(x + totalW - 246, actionY, 74, BUTTON_H, mouseX, mouseY)
                && draftTheme().renderMode() == UiThemeRenderMode.PALETTE) {
            try {
                UiThemeDefinition selected = draftTheme();
                String copyId = selected.id().startsWith("rtsbuilding:")
                        ? "user:" + selected.id().substring(selected.id().indexOf(':') + 1) + "_copy"
                        : selected.id();
                UiThemeStorage.defaultStorage().exportUserCopy(selected, copyId);
                statusKey = "screen.rtsbuilding.theme.status.exported";
            } catch (IOException | RuntimeException failure) {
                RtsbuildingMod.LOGGER.warn("导出 UI 主题失败", failure);
                statusKey = "screen.rtsbuilding.theme.status.export_error";
            }
        } else if (UiRect.contains(x + totalW - 166, actionY, 74, BUTTON_H, mouseX, mouseY)) {
            setOpen(false);
        } else if (UiRect.contains(x + totalW - 86, actionY, 86, BUTTON_H, mouseX, mouseY)) {
            applyDraft();
        }
    }

    private void applyDraft() {
        try {
            UiThemeRuntime.manager().activate(draftTheme().id());
            UiThemeStorage.defaultStorage().saveActiveId(draftTheme().id());
            statusKey = "screen.rtsbuilding.theme.status.applied";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("保存活动 UI 主题失败", failure);
            statusKey = "screen.rtsbuilding.theme.status.apply_error";
        }
    }

    private UiThemeDefinition draftTheme() {
        if (draftId == null || !UiThemeRuntime.registry().contains(draftId)) {
            return UiThemeRuntime.manager().active();
        }
        return UiThemeRuntime.registry().require(draftId);
    }

    private Component displayName(UiThemeDefinition theme) {
        return Component.translatable(theme.nameKey());
    }

    private String text(String key) {
        return Component.translatable(key).getString();
    }

    @Override protected Component getTitle() { return Component.translatable("screen.rtsbuilding.theme.title"); }
    @Override protected int getDefaultWidth() { return DEFAULT_W; }
    @Override protected int getDefaultHeight() { return DEFAULT_H; }
    @Override protected int getMinWindowWidth() { return 460; }
    @Override protected int getMinWindowHeight() { return 286; }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(28, (screen.height - this.windowHeight) / 2);
    }
}
