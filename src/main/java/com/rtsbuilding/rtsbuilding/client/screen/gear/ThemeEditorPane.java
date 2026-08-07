package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeDraft;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * 主题窗口右侧的 Palette 草稿编辑器。
 *
 * <p>它拥有令牌选择、颜色草稿和色轮拖动状态，却不切换全局主题、不写磁盘。Legacy
 * 主题由于直接使用原始资源包纹理，明确显示为不可编辑，不能被伪装成仅能改色的 Palette。</p>
 */
final class ThemeEditorPane {
    private static final int TOKEN_ROW_H = 18;
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int LIST_PICKER_GAP = 6;

    private final ThemeColorPicker picker = new ThemeColorPicker();
    private UiThemeDefinition source;
    private UiThemeDraft draft;
    private UiThemeToken selectedToken = UiThemeToken.ACCENT_PRIMARY;
    private int tokenScroll;
    private boolean dirty;
    private boolean wheelDragging;
    private boolean valueDragging;

    void setSource(UiThemeDefinition next) {
        if (next == this.source) return;
        this.source = next;
        this.dirty = false;
        this.tokenScroll = 0;
        this.wheelDragging = false;
        this.valueDragging = false;
        if (next != null && next.renderMode() == UiThemeRenderMode.PALETTE) {
            this.draft = new UiThemeDraft(next);
            this.picker.setColor(this.draft.color(this.selectedToken));
        } else {
            this.draft = null;
        }
    }

    boolean editable() {
        return this.draft != null;
    }

    boolean dirty() {
        return this.dirty;
    }

    UiThemeDefinition snapshot() {
        return this.draft == null ? this.source : this.draft.snapshot();
    }

    void render(GuiGraphicsExtractor graphics, MinecraftUiCanvas canvas, Font font,
                int x, int y, int width, int height, int mouseX, int mouseY) {
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, width, height),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);
        graphics.text(font, Component.translatable("screen.rtsbuilding.theme.editor"),
                x + EDITOR_TITLE_X, y + EDITOR_TITLE_Y, SettingsWindowStyle.VALUE.toArgb(), false);
        if (!editable()) {
            graphics.text(font, Component.translatable("screen.rtsbuilding.theme.editor.legacy"),
                    x + EDITOR_TITLE_X, y + EDITOR_LEGACY_Y, SettingsWindowStyle.HINT.toArgb(), false);
            return;
        }

        UiThemeToken[] tokens = UiThemeToken.values();
        int listY = y + EDITOR_LIST_Y;
        int listWidth = width - EDITOR_LIST_WIDTH_RESERVE;
        int visibleRows = visibleRows(height);
        this.tokenScroll = Math.min(this.tokenScroll, Math.max(0, tokens.length - visibleRows));
        for (int row = 0; row < visibleRows; row++) {
            int index = this.tokenScroll + row;
            if (index >= tokens.length) break;
            UiThemeToken token = tokens[index];
            int rowY = listY + row * TOKEN_ROW_H;
            boolean selected = token == this.selectedToken;
            boolean hover = contains(x + EDITOR_LIST_INSET, rowY, listWidth,
                    TOKEN_ROW_H - EDITOR_ROW_BOTTOM, mouseX, mouseY);
            UiColor background = selected ? SettingsWindowStyle.TOGGLE_ON
                    : hover ? SettingsWindowStyle.STEP_HOVER_BACKGROUND : SettingsWindowStyle.STEP_BACKGROUND;
            graphics.fill(x + EDITOR_LIST_INSET, rowY, x + EDITOR_LIST_INSET + listWidth,
                    rowY + TOKEN_ROW_H - EDITOR_ROW_BOTTOM, background.toArgb());
            graphics.fill(x + width - EDITOR_SWATCH_RIGHT, rowY + EDITOR_SWATCH_TOP,
                    x + width - EDITOR_SWATCH_END, rowY + TOKEN_ROW_H - EDITOR_SWATCH_BOTTOM,
                    this.draft.color(token).toArgb());
            String tokenLabel = font.plainSubstrByWidth(Component.translatable(
                    "screen.rtsbuilding.theme.token." + token.serializedId()).getString(),
                    width - EDITOR_LABEL_WIDTH_RESERVE);
            graphics.text(font, tokenLabel, x + EDITOR_LABEL_X, rowY + EDITOR_LABEL_Y,
                    SettingsWindowStyle.VALUE.toArgb(), false);
        }

        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + height - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        this.picker.render(graphics, pickerX, pickerY, this.wheelDragging, this.valueDragging);
        String hex = String.format(Locale.ROOT, "#%08X", this.draft.color(this.selectedToken).toArgb());
        int detailX = pickerX + ThemeColorPicker.WHEEL_SIZE + ThemeColorPicker.VALUE_GAP
                + ThemeColorPicker.VALUE_W + EDITOR_HEX_GAP;
        graphics.text(font, hex, detailX, pickerY + EDITOR_HEX_Y,
                SettingsWindowStyle.VALUE.toArgb(), false);
        int detailWidth = Math.max(1, x + width - EDITOR_TEXT_RIGHT_INSET - detailX);
        var hints = font.split(Component.translatable("screen.rtsbuilding.theme.editor.drag_hint"), detailWidth);
        for (int line = 0; line < Math.min(3, hints.size()); line++) {
            graphics.text(font, hints.get(line), detailX,
                    pickerY + EDITOR_HINT_Y + line * font.lineHeight,
                    SettingsWindowStyle.HINT.toArgb(), false);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int x, int y, int width, int height) {
        if (!editable()) return false;
        int listY = y + EDITOR_LIST_Y;
        int visibleRows = visibleRows(height);
        if (contains(x + EDITOR_LIST_INSET, listY, width - EDITOR_LIST_WIDTH_RESERVE,
                TOKEN_ROW_H * visibleRows, mouseX, mouseY)) {
            int row = (int) ((mouseY - listY) / TOKEN_ROW_H);
            int index = this.tokenScroll + row;
            if (index >= 0 && index < UiThemeToken.values().length) {
                this.selectedToken = UiThemeToken.values()[index];
                this.picker.setColor(this.draft.color(this.selectedToken));
                return true;
            }
        }
        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + height - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        if (this.picker.insideWheel(mouseX, mouseY, pickerX, pickerY)) {
            this.wheelDragging = true;
            this.picker.pickWheel(mouseX, mouseY, pickerX, pickerY);
            applyPicker();
            return true;
        }
        if (this.picker.insideValue(mouseX, mouseY, pickerX, pickerY)) {
            this.valueDragging = true;
            this.picker.pickValue(mouseY, pickerY);
            applyPicker();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int x, int y, int width, int height) {
        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + height - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        if (this.wheelDragging) this.picker.pickWheel(mouseX, mouseY, pickerX, pickerY);
        else if (this.valueDragging) this.picker.pickValue(mouseY, pickerY);
        else return false;
        applyPicker();
        return true;
    }

    boolean mouseReleased() {
        boolean wasDragging = this.wheelDragging || this.valueDragging;
        this.wheelDragging = false;
        this.valueDragging = false;
        return wasDragging;
    }

    boolean mouseScrolled(double delta, int x, int y, int width, int height,
                          double mouseX, double mouseY) {
        if (!editable() || !contains(x, y, width, height, mouseX, mouseY)) return false;
        int maximum = Math.max(0, UiThemeToken.values().length - visibleRows(height));
        this.tokenScroll = Math.max(0, Math.min(maximum, this.tokenScroll + (delta > 0 ? -1 : 1)));
        return true;
    }

    void release() {
        this.picker.release();
    }

    private void applyPicker() {
        this.draft.setColor(this.selectedToken, this.picker.color());
        this.dirty = true;
    }

    private static int visibleRows(int height) {
        int pickerTop = height - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        int available = pickerTop - EDITOR_LIST_Y - LIST_PICKER_GAP;
        return Math.max(MIN_VISIBLE_ROWS, Math.min(MAX_VISIBLE_ROWS, available / TOKEN_ROW_H));
    }

    private static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
