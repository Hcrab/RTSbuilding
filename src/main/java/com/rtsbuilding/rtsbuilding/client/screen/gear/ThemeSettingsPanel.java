package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeValidator;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * UI 主题选择与预览窗口。
 *
 * <p>本类只管理客户端草稿选择、预览和导入导出入口；主题格式校验、文件边界和 GPU 纹理缓存
 * 继续分别由 {@link UiThemeStorage} 与主题渲染层负责，避免设置窗口成为新的全能类。</p>
 */
public final class ThemeSettingsPanel extends RtsWindowPanel {
    private static final int LIST_W = 150;
    private static final int ROW_H = 34;
    private static final int BUTTON_H = 22;
    private static final int EDITOR_W = 180;

    private String draftId;
    private int themeScroll;
    private String statusKey = "screen.rtsbuilding.theme.status.ready";
    private final ThemeEditorPane editor = new ThemeEditorPane();
    private boolean layoutAtNativeScale;

    public void open() {
        this.draftId = UiThemeRuntime.manager().active().id();
        this.editor.setSource(UiThemeRuntime.manager().active());
        this.themeScroll = 0;
        this.statusKey = "screen.rtsbuilding.theme.status.ready";
        this.layoutAtNativeScale = true;
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // open() 发生在旧缩放输入帧内；等下一次原生倍率渲染帧建立后再计算一次边界，
        // 这样窗口既不会沿用过小的虚拟视口，也不会出现首帧命中与绘制坐标错位。
        if (this.layoutAtNativeScale) {
            int width = getDefaultWidth();
            int height = getDefaultHeight();
            setTransientBounds(
                    Math.max(8, (screen.width - width) / 2),
                    Math.max(28, (screen.height - height) / 2),
                    width,
                    height);
            this.layoutAtNativeScale = false;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, screen.font(), screen);
        int x = contentX() + OUTER_INSET;
        int y = contentY() + OUTER_INSET;
        int h = contentHeight() - OUTER_INSET * 2;
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, LIST_W, h),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);

        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int rowY = y + LIST_INSET;
        int visibleThemeRows = visibleThemeRows(h);
        this.themeScroll = Math.min(this.themeScroll,
                Math.max(0, themes.size() - visibleThemeRows));
        for (int index = this.themeScroll;
             index < themes.size() && index < this.themeScroll + visibleThemeRows; index++) {
            UiThemeDefinition theme = themes.get(index);
            drawThemeRow(g, canvas, index - this.themeScroll, theme, x + LIST_INSET, rowY,
                    LIST_W - DOUBLE_LIST_INSET, mouseX, mouseY);
            rowY += ROW_H;
        }

        UiThemeDefinition draft = draftTheme();
        int previewX = x + LIST_W + COLUMN_GAP;
        int previewW = contentWidth() - LIST_W - EDITOR_W - COLUMN_WIDTH_RESERVE;
        ThemePreviewRenderer.render(g, canvas, screen.font(), draft,
                previewX, y, previewW, h - BODY_FOOTER_RESERVE);
        int editorX = previewX + previewW + COLUMN_GAP;
        this.editor.render(g, canvas, editorX, y, EDITOR_W,
                h - BODY_FOOTER_RESERVE, mouseX, mouseY);
        drawActions(g, canvas, x, y + h - FOOTER_TOP_RESERVE,
                contentWidth() - OUTER_INSET * 2,
                mouseX, mouseY, draft);
    }

    private void drawThemeRow(GuiGraphics g, MinecraftUiCanvas canvas, int visibleRow,
                              UiThemeDefinition theme,
                              int x, int y, int w, int mouseX, int mouseY) {
        boolean selected = theme.id().equals(draftId);
        boolean hover = UiRect.contains(x, y, w, ROW_H - 3, mouseX, mouseY);
        com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState.Snapshot animation =
                animateContentControl("theme_row_" + visibleRow, true, hover, selected);
        UiColor background = UiColor.interpolate(
                SettingsWindowStyle.STEP_BACKGROUND,
                SettingsWindowStyle.STEP_HOVER_BACKGROUND,
                animation.hover());
        background = UiColor.interpolate(
                background, SettingsWindowStyle.TOGGLE_ON, animation.selection());
        UiColor border = UiColor.interpolate(
                SettingsWindowStyle.STEP_BORDER,
                SettingsWindowStyle.TOGGLE_ON_BORDER,
                animation.selection());
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, ROW_H - 3), background,
                border,
                SettingsWindowStyle.STEP_DARK_BORDER);
        g.drawString(screen.font(), displayName(theme), x + THEME_NAME_X, y + THEME_NAME_Y,
                SettingsWindowStyle.VALUE.toArgb(), false);
        String mode = theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT
                ? text("screen.rtsbuilding.theme.mode.legacy")
                : text("screen.rtsbuilding.theme.mode.palette");
        g.drawString(screen.font(), mode, x + THEME_NAME_X, y + THEME_MODE_Y,
                SettingsWindowStyle.HINT.toArgb(), false);
    }

    private void drawActions(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w,
                             int mouseX, int mouseY, UiThemeDefinition draft) {
        drawButton(g, canvas, x, y, 78, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.import");
        drawButton(g, canvas, x + ACTION_SECOND_X, y, 78, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.folder");
        drawButton(g, canvas, x + w - ACTION_EXPORT_RIGHT, y, 74, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.export", draft.renderMode() == UiThemeRenderMode.PALETTE);
        drawButton(g, canvas, x + w - ACTION_CANCEL_RIGHT, y, 74, BUTTON_H, mouseX, mouseY,
                "gui.cancel");
        drawButton(g, canvas, x + w - ACTION_APPLY_RIGHT, y, 86, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.apply");
        g.drawString(screen.font(), text(statusKey), x, y + ACTION_STATUS_Y,
                SettingsWindowStyle.HINT.toArgb(), false);
    }

    private void drawButton(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w, int h,
                            int mouseX, int mouseY, String key) {
        drawButton(g, canvas, x, y, w, h, mouseX, mouseY, key, true);
    }

    private void drawButton(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w, int h,
                            int mouseX, int mouseY, String key, boolean enabled) {
        boolean hover = enabled && UiRect.contains(x, y, w, h, mouseX, mouseY);
        com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState.Snapshot animation =
                animateContentControl("theme_action_" + key, enabled, hover, false);
        UiColor enabledBackground = UiColor.interpolate(
                SettingsWindowStyle.STEP_BACKGROUND,
                SettingsWindowStyle.STEP_HOVER_BACKGROUND,
                animation.hover());
        UiColor background = UiColor.interpolate(
                enabledBackground,
                SettingsWindowStyle.VALUE_BACKGROUND,
                animation.disabled());
        UiColor textColor = UiColor.interpolate(
                SettingsWindowStyle.VALUE,
                SettingsWindowStyle.DISABLED_TEXT,
                animation.disabled());
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, h),
                background,
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(), text(key),
                x + w / 2, y + ACTION_TEXT_Y,
                textColor.toArgb());
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int x = contentX() + OUTER_INSET;
        int y = contentY() + OUTER_INSET;
        int h = contentHeight() - OUTER_INSET * 2;
        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int visibleThemeRows = visibleThemeRows(h);
        int rowY = y + LIST_INSET;
        for (int index = this.themeScroll;
             index < themes.size() && index < this.themeScroll + visibleThemeRows; index++) {
            UiThemeDefinition theme = themes.get(index);
            if (UiRect.contains(x + LIST_INSET, rowY, LIST_W - DOUBLE_LIST_INSET,
                    ROW_H - THEME_ROW_BOTTOM, mouseX, mouseY)) {
                this.draftId = theme.id();
                this.editor.setSource(theme);
                this.statusKey = "screen.rtsbuilding.theme.status.draft";
                return;
            }
            rowY += ROW_H;
        }
        int previewX = x + LIST_W + COLUMN_GAP;
        int previewW = contentWidth() - LIST_W - EDITOR_W - COLUMN_WIDTH_RESERVE;
        int editorX = previewX + previewW + COLUMN_GAP;
        if (this.editor.mouseClicked(mouseX, mouseY, editorX, y, EDITOR_W,
                h - BODY_FOOTER_RESERVE)) return;
        int actionY = y + h - FOOTER_TOP_RESERVE;
        int totalW = contentWidth() - ACTION_WIDTH_RESERVE;
        if (UiRect.contains(x, actionY, 78, BUTTON_H, mouseX, mouseY)) {
            importTheme();
        } else if (UiRect.contains(x + ACTION_SECOND_X, actionY, 78, BUTTON_H, mouseX, mouseY)) {
            Util.getPlatform().openUri(UiThemeStorage.defaultStorage().directory().toUri());
        } else if (UiRect.contains(x + totalW - ACTION_EXPORT_RIGHT, actionY,
                74, BUTTON_H, mouseX, mouseY)
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
        } else if (UiRect.contains(x + totalW - ACTION_CANCEL_RIGHT, actionY,
                74, BUTTON_H, mouseX, mouseY)) {
            setOpen(false);
        } else if (UiRect.contains(x + totalW - ACTION_APPLY_RIGHT, actionY,
                86, BUTTON_H, mouseX, mouseY)) {
            applyDraft();
        }
    }

    private void importTheme() {
        java.nio.file.Path selected = ThemeFileDialogs.chooseImport();
        if (selected == null) {
            statusKey = "screen.rtsbuilding.theme.status.import_cancelled";
            return;
        }
        try {
            UiThemeDefinition imported = UiThemeStorage.defaultStorage()
                    .importFile(selected, UiThemeRuntime.registry());
            this.draftId = imported.id();
            this.editor.setSource(imported);
            this.statusKey = "screen.rtsbuilding.theme.status.imported";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("导入 UI 主题失败：{}", selected, failure);
            this.statusKey = "screen.rtsbuilding.theme.status.import_error";
        }
    }

    private void applyDraft() {
        try {
            UiThemeDefinition target = draftTheme();
            if (target.renderMode() == UiThemeRenderMode.PALETTE) {
                UiThemeValidator.validateContrast(target);
            }
            if (this.editor.dirty()) {
                UiThemeStorage.defaultStorage().export(target);
                UiThemeRuntime.registry().registerOrReplaceUser(target);
            }
            UiThemeRuntime.manager().activate(target.id());
            UiThemeStorage.defaultStorage().saveActiveId(target.id());
            this.draftId = target.id();
            this.editor.setSource(target);
            statusKey = "screen.rtsbuilding.theme.status.applied";
        } catch (IllegalArgumentException invalidTheme) {
            RtsbuildingMod.LOGGER.warn("UI 主题未通过可读性校验：{}", invalidTheme.getMessage());
            statusKey = "screen.rtsbuilding.theme.status.contrast_error";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("保存活动 UI 主题失败", failure);
            statusKey = "screen.rtsbuilding.theme.status.apply_error";
        }
    }

    private UiThemeDefinition draftTheme() {
        if (this.editor.dirty()) return this.editor.snapshot();
        if (draftId == null || !UiThemeRuntime.registry().contains(draftId)) {
            return UiThemeRuntime.manager().active();
        }
        return UiThemeRuntime.registry().require(draftId);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.editor.editable()) {
            int x = contentX() + OUTER_INSET;
            int y = contentY() + OUTER_INSET;
            int h = contentHeight() - OUTER_INSET * 2;
            int previewX = x + LIST_W + COLUMN_GAP;
            int previewW = contentWidth() - LIST_W - EDITOR_W - COLUMN_WIDTH_RESERVE;
            if (this.editor.mouseDragged(mouseX, mouseY,
                    previewX + previewW + COLUMN_GAP, y, EDITOR_W,
                    h - BODY_FOOTER_RESERVE)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.editor.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = contentX() + OUTER_INSET;
        int y = contentY() + OUTER_INSET;
        int h = contentHeight() - OUTER_INSET * 2;
        int previewX = x + LIST_W + COLUMN_GAP;
        int previewW = contentWidth() - LIST_W - EDITOR_W - COLUMN_WIDTH_RESERVE;
        if (UiRect.contains(x, y, LIST_W, h - BODY_FOOTER_RESERVE,
                mouseX, mouseY)) {
            int maximum = Math.max(0, UiThemeRuntime.registry().snapshot().size()
                    - visibleThemeRows(h));
            this.themeScroll = Math.max(0, Math.min(maximum,
                    this.themeScroll + (scrollY > 0 ? -1 : 1)));
            return true;
        }
        this.editor.mouseScrolled(scrollY, previewX + previewW + COLUMN_GAP,
                y, EDITOR_W, h - BODY_FOOTER_RESERVE, mouseX, mouseY);
        return true;
    }

    @Override
    protected void onClose() {
        this.editor.release();
        super.onClose();
    }

    private Component displayName(UiThemeDefinition theme) {
        return theme.nameKey().startsWith("screen.rtsbuilding.")
                ? Component.translatable(theme.nameKey()) : Component.literal(theme.nameKey());
    }

    private String text(String key) {
        return Component.translatable(key).getString();
    }

    private static int visibleThemeRows(int contentHeight) {
        return Math.max(1,
                (contentHeight - LIST_INSET - LIST_FOOTER_RESERVE) / ROW_H);
    }

    @Override protected Component getTitle() { return Component.translatable("screen.rtsbuilding.theme.title"); }
    @Override protected int getDefaultWidth() {
        return preferredWindowWidth(screen == null ? PREFERRED_WINDOW_W : screen.width);
    }
    @Override protected int getDefaultHeight() {
        return preferredWindowHeight(screen == null ? PREFERRED_WINDOW_H : screen.height);
    }
    @Override protected int getMinWindowWidth() { return MIN_WINDOW_W; }
    @Override protected int getMinWindowHeight() { return MIN_WINDOW_H; }

    @Override
    protected int getMaxWindowHeight() {
        return screen == null ? PREFERRED_WINDOW_H : preferredWindowHeight(screen.height);
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(28, (screen.height - this.windowHeight) / 2);
    }
}
