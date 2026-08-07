package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeStorage;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeValidator;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * 生产主题选择、预览和导入导出窗口。
 *
 * <p>窗口只承担客户端草稿交互。严格格式校验和配置目录边界由 {@link UiThemeStorage}
 * 负责，颜色草稿由 {@link ThemeEditorPane} 负责；这让取消不会污染当前使用中的主题。</p>
 */
public final class ThemeSettingsPanel extends RtsWindowPanel {
    private static final int ROW_H = 34;
    private static final int BUTTON_H = 22;

    private String draftId;
    private int themeScroll;
    private String statusKey = "screen.rtsbuilding.theme.status.ready";
    private final ThemeEditorPane editor = new ThemeEditorPane();

    /** 打开时从当前活动主题创建草稿，取消只关闭窗口。 */
    public void open() {
        UiThemeDefinition active = UiThemeRuntime.manager().active();
        this.draftId = active.id();
        this.editor.setSource(active);
        this.themeScroll = 0;
        this.statusKey = "screen.rtsbuilding.theme.status.ready";
        setOpen(true);
        markBroughtToFront();
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, this.screen.font(), this.screen, visualOpacity());
        ThemeSettingsLayout.Geometry layout = themeGeometry();
        int listX = integer(layout.list.getX());
        int listY = integer(layout.list.getY());
        int listHeight = integer(layout.list.getHeight());
        UiCompactFrameRenderer.frame(canvas, layout.list,
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);

        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int visibleRows = visibleThemeRows(listHeight);
        this.themeScroll = Math.min(this.themeScroll, Math.max(0, themes.size() - visibleRows));
        int rowY = listY + LIST_INSET;
        for (int index = this.themeScroll; index < themes.size() && index < this.themeScroll + visibleRows; index++) {
            drawThemeRow(graphics, canvas, themes.get(index), listX + LIST_INSET, rowY,
                    integer(layout.list.getWidth()) - DOUBLE_LIST_INSET, mouseX, mouseY);
            rowY += ROW_H;
        }

        UiThemeDefinition draft = draftTheme();
        ThemePreviewRenderer.render(graphics, canvas, this.screen.font(), draft,
                integer(layout.preview.getX()), integer(layout.preview.getY()),
                integer(layout.preview.getWidth()), integer(layout.preview.getHeight()));
        this.editor.render(graphics, canvas, this.screen.font(),
                integer(layout.editor.getX()), integer(layout.editor.getY()),
                integer(layout.editor.getWidth()), integer(layout.editor.getHeight()), mouseX, mouseY);
        drawActions(graphics, canvas, integer(layout.actions.getX()), integer(layout.actions.getY()),
                integer(layout.actions.getWidth()), mouseX, mouseY, draft);
    }

    private void drawThemeRow(GuiGraphicsExtractor graphics, MinecraftUiCanvas canvas,
                              UiThemeDefinition theme, int x, int y, int width,
                              int mouseX, int mouseY) {
        boolean selected = theme.id().equals(this.draftId);
        boolean hover = !areChildControlsSuppressed() && contains(x, y, width, ROW_H - 3, mouseX, mouseY);
        var animation = animateContentControl("theme_row_" + theme.id(), true, hover, selected);
        UiColor background = UiColor.interpolate(SettingsWindowStyle.STEP_BACKGROUND,
                SettingsWindowStyle.STEP_HOVER_BACKGROUND, animation.hover());
        background = UiColor.interpolate(background, SettingsWindowStyle.TOGGLE_ON, animation.selection());
        UiColor border = UiColor.interpolate(SettingsWindowStyle.STEP_BORDER,
                SettingsWindowStyle.TOGGLE_ON_BORDER, animation.selection());
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, ROW_H - 3), background, border,
                SettingsWindowStyle.STEP_DARK_BORDER);
        graphics.text(this.screen.font(), displayName(theme), x + THEME_NAME_X, y + THEME_NAME_Y,
                withVisualOpacity(SettingsWindowStyle.VALUE.toArgb()), false);
        String mode = text(theme.renderMode() == UiThemeRenderMode.LEGACY_DIRECT
                ? "screen.rtsbuilding.theme.mode.legacy" : "screen.rtsbuilding.theme.mode.palette");
        graphics.text(this.screen.font(), mode, x + THEME_NAME_X, y + THEME_MODE_Y,
                withVisualOpacity(SettingsWindowStyle.HINT.toArgb()), false);
    }

    private void drawActions(GuiGraphicsExtractor graphics, MinecraftUiCanvas canvas, int x, int y, int width,
                             int mouseX, int mouseY, UiThemeDefinition draft) {
        drawButton(graphics, canvas, x, y, ACTION_IMPORT_W, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.import", true);
        drawButton(graphics, canvas, x + ACTION_SECOND_X, y, ACTION_FOLDER_W, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.folder", true);
        drawButton(graphics, canvas, x + width - ACTION_EXPORT_RIGHT, y, 74, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.export", draft.renderMode() == UiThemeRenderMode.PALETTE);
        drawButton(graphics, canvas, x + width - ACTION_CANCEL_RIGHT, y, 74, BUTTON_H, mouseX, mouseY,
                "gui.cancel", true);
        drawButton(graphics, canvas, x + width - ACTION_APPLY_RIGHT, y, 86, BUTTON_H, mouseX, mouseY,
                "screen.rtsbuilding.theme.apply", true);
        var lines = this.screen.font().split(Component.translatable(this.statusKey), THEME_LIST_W - 4);
        for (int index = 0; index < Math.min(ACTION_STATUS_MAX_LINES, lines.size()); index++) {
            graphics.text(this.screen.font(), lines.get(index), x,
                    y + ACTION_STATUS_Y + index * this.screen.font().lineHeight,
                    withVisualOpacity(SettingsWindowStyle.HINT.toArgb()), false);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, MinecraftUiCanvas canvas, int x, int y, int width,
                            int height, int mouseX, int mouseY, String key, boolean enabled) {
        boolean hover = enabled && !areChildControlsSuppressed() && contains(x, y, width, height, mouseX, mouseY);
        var animation = animateContentControl("theme_action_" + key, enabled, hover, false);
        UiColor enabledBackground = UiColor.interpolate(SettingsWindowStyle.STEP_BACKGROUND,
                SettingsWindowStyle.STEP_HOVER_BACKGROUND, animation.hover());
        UiColor background = UiColor.interpolate(enabledBackground,
                SettingsWindowStyle.VALUE_BACKGROUND, animation.disabled());
        UiColor label = UiColor.interpolate(SettingsWindowStyle.VALUE,
                SettingsWindowStyle.DISABLED_TEXT, animation.disabled());
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, height), background,
                SettingsWindowStyle.STEP_BORDER, SettingsWindowStyle.STEP_DARK_BORDER);
        RtsClientUiUtil.drawCenteredStringNoShadow(graphics, this.screen.font(), text(key), x + width / 2,
                ThemeSettingsLayout.actionTextTop(y, height, this.screen.font().lineHeight),
                withVisualOpacity(label.toArgb()));
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || areChildControlsSuppressed()) return;
        ThemeSettingsLayout.Geometry layout = themeGeometry();
        int listX = integer(layout.list.getX());
        int rowY = integer(layout.list.getY()) + LIST_INSET;
        List<UiThemeDefinition> themes = UiThemeRuntime.registry().snapshot();
        int visibleRows = visibleThemeRows(integer(layout.list.getHeight()));
        for (int index = this.themeScroll; index < themes.size() && index < this.themeScroll + visibleRows; index++) {
            UiThemeDefinition theme = themes.get(index);
            if (contains(listX + LIST_INSET, rowY, THEME_LIST_W - DOUBLE_LIST_INSET,
                    ROW_H - THEME_ROW_BOTTOM, mouseX, mouseY)) {
                this.draftId = theme.id();
                this.editor.setSource(theme);
                this.statusKey = "screen.rtsbuilding.theme.status.draft";
                return;
            }
            rowY += ROW_H;
        }
        if (this.editor.mouseClicked(mouseX, mouseY, integer(layout.editor.getX()), integer(layout.editor.getY()),
                integer(layout.editor.getWidth()), integer(layout.editor.getHeight()))) return;

        int x = integer(layout.actions.getX());
        int y = integer(layout.actions.getY());
        int width = integer(layout.actions.getWidth());
        if (contains(x, y, ACTION_IMPORT_W, BUTTON_H, mouseX, mouseY)) {
            importTheme();
        } else if (contains(x + ACTION_SECOND_X, y, ACTION_FOLDER_W, BUTTON_H, mouseX, mouseY)) {
            openThemeFolder();
        } else if (contains(x + width - ACTION_EXPORT_RIGHT, y, 74, BUTTON_H, mouseX, mouseY)
                && draftTheme().renderMode() == UiThemeRenderMode.PALETTE) {
            exportTheme();
        } else if (contains(x + width - ACTION_CANCEL_RIGHT, y, 74, BUTTON_H, mouseX, mouseY)) {
            setOpen(false);
        } else if (contains(x + width - ACTION_APPLY_RIGHT, y, 86, BUTTON_H, mouseX, mouseY)) {
            applyDraft();
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && !areChildControlsSuppressed() && this.editor.editable()) {
            ThemeSettingsLayout.Geometry layout = themeGeometry();
            if (this.editor.mouseDragged(mouseX, mouseY, integer(layout.editor.getX()), integer(layout.editor.getY()),
                    integer(layout.editor.getWidth()), integer(layout.editor.getHeight()))) return true;
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
        ThemeSettingsLayout.Geometry layout = themeGeometry();
        if (contains(integer(layout.list.getX()), integer(layout.list.getY()), integer(layout.list.getWidth()),
                integer(layout.list.getHeight()), mouseX, mouseY)) {
            int maximum = Math.max(0, UiThemeRuntime.registry().snapshot().size()
                    - visibleThemeRows(integer(layout.list.getHeight())));
            this.themeScroll = Math.max(0, Math.min(maximum, this.themeScroll + (scrollY > 0 ? -1 : 1)));
            return true;
        }
        return this.editor.mouseScrolled(scrollY, integer(layout.editor.getX()), integer(layout.editor.getY()),
                integer(layout.editor.getWidth()), integer(layout.editor.getHeight()), mouseX, mouseY);
    }

    @Override
    protected void onClose() {
        this.editor.release();
        super.onClose();
    }

    private void importTheme() {
        Path selected = ThemeFileDialogs.chooseImport();
        if (selected == null) {
            this.statusKey = "screen.rtsbuilding.theme.status.import_cancelled";
            return;
        }
        try {
            UiThemeDefinition imported = UiThemeStorage.defaultStorage().importFile(selected, UiThemeRuntime.registry());
            this.draftId = imported.id();
            this.editor.setSource(imported);
            this.statusKey = "screen.rtsbuilding.theme.status.imported";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("导入 UI 主题失败：{}", selected, failure);
            this.statusKey = "screen.rtsbuilding.theme.status.import_error";
        }
    }

    /** 打开用户主题目录；目录不存在时先创建，避免平台文件管理器收到无效路径。 */
    private void openThemeFolder() {
        Path directory = UiThemeStorage.defaultStorage().directory();
        try {
            Files.createDirectories(directory);
            Util.getPlatform().openFile(directory.toFile());
        } catch (IOException failure) {
            RtsbuildingMod.LOGGER.warn("打开 UI 主题目录失败：{}", directory, failure);
        }
    }

    private void exportTheme() {
        try {
            UiThemeDefinition selected = draftTheme();
            String copyId = selected.id().startsWith("rtsbuilding:")
                    ? "user:" + selected.id().substring(selected.id().indexOf(':') + 1) + "_copy"
                    : selected.id();
            UiThemeStorage.defaultStorage().exportUserCopy(selected, copyId);
            this.statusKey = "screen.rtsbuilding.theme.status.exported";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("导出 UI 主题失败", failure);
            this.statusKey = "screen.rtsbuilding.theme.status.export_error";
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
            this.statusKey = "screen.rtsbuilding.theme.status.applied";
        } catch (IllegalArgumentException invalidTheme) {
            RtsbuildingMod.LOGGER.warn("UI 主题未通过可读性校验：{}", invalidTheme.getMessage());
            this.statusKey = "screen.rtsbuilding.theme.status.contrast_error";
        } catch (IOException | RuntimeException failure) {
            RtsbuildingMod.LOGGER.warn("保存活动 UI 主题失败", failure);
            this.statusKey = "screen.rtsbuilding.theme.status.apply_error";
        }
    }

    private UiThemeDefinition draftTheme() {
        if (this.editor.dirty()) return this.editor.snapshot();
        if (this.draftId == null || !UiThemeRuntime.registry().contains(this.draftId)) {
            return UiThemeRuntime.manager().active();
        }
        return UiThemeRuntime.registry().require(this.draftId);
    }

    private ThemeSettingsLayout.Geometry themeGeometry() {
        return ThemeSettingsLayout.geometry(contentX(), contentY(), contentWidth(), contentHeight());
    }

    private Component displayName(UiThemeDefinition theme) {
        return theme.nameKey().startsWith("screen.rtsbuilding.")
                ? Component.translatable(theme.nameKey()) : Component.literal(theme.nameKey());
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    private static int visibleThemeRows(int contentHeight) {
        return Math.max(1, (contentHeight - LIST_INSET - LIST_FOOTER_RESERVE) / ROW_H);
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int integer(double value) {
        return (int) Math.round(value);
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.theme.title");
    }

    @Override
    protected int getDefaultWidth() {
        return preferredWindowWidth(this.screen == null ? PREFERRED_WINDOW_W : this.screen.width);
    }

    @Override
    protected int getDefaultHeight() {
        return preferredWindowHeight(this.screen == null ? PREFERRED_WINDOW_H : this.screen.height);
    }

    @Override
    protected int getMinWindowWidth() {
        return MIN_WINDOW_W;
    }

    @Override
    protected int getMinWindowHeight() {
        return MIN_WINDOW_H;
    }

    @Override
    protected int getMaxWindowHeight() {
        return this.screen == null ? PREFERRED_WINDOW_H : preferredWindowHeight(this.screen.height);
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(28, (this.screen.height - this.windowHeight) / 2);
    }
}
