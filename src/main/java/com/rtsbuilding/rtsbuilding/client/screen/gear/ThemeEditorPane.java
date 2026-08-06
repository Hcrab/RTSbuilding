package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeDraft;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.SettingsWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import static com.rtsbuilding.rtsbuilding.uikit.layout.ThemeSettingsLayout.*;

/**
 * 主题窗口右侧的令牌编辑器。
 *
 * <p>该类拥有令牌列表滚动、v2 色轮交互和内存草稿，不负责活动主题切换、磁盘写入或浮窗生命周期。
 * 这样颜色编辑即使失败或取消，也不会污染当前玩家正在使用的主题。</p>
 */
final class ThemeEditorPane {
    private static final int TOKEN_ROW_H = 18;
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int MIN_VISIBLE_ROWS = 2;
    private static final int LIST_PICKER_GAP = 6;
    private final ThemeColorPicker picker = new ThemeColorPicker();
    private final UiControlAnimationRegistry<UiThemeToken> tokenAnimations =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, UiThemeToken.values().length);
    private UiThemeDefinition source;
    private UiThemeDraft draft;
    private UiThemeToken selectedToken = UiThemeToken.ACCENT_PRIMARY;
    private int tokenScroll;
    private boolean dirty;
    private boolean wheelDragging;
    private boolean valueDragging;

    void setSource(UiThemeDefinition next) {
        if (next == source) return;
        source = next;
        tokenAnimations.clear();
        dirty = false;
        tokenScroll = 0;
        wheelDragging = false;
        valueDragging = false;
        if (next != null && next.renderMode() == UiThemeRenderMode.PALETTE) {
            draft = new UiThemeDraft(next);
            picker.setColor(draft.color(selectedToken));
        } else {
            draft = null;
        }
    }

    boolean editable() {
        return draft != null;
    }

    boolean dirty() {
        return dirty;
    }

    UiThemeDefinition snapshot() {
        return draft == null ? source : draft.snapshot();
    }

    void render(GuiGraphics g, MinecraftUiCanvas canvas, int x, int y, int w, int h,
                int mouseX, int mouseY) {
        UiCompactFrameRenderer.frame(canvas, new UiRect(x, y, w, h),
                SettingsWindowStyle.VALUE_BACKGROUND, SettingsWindowStyle.VALUE_BORDER,
                SettingsWindowStyle.VALUE_DARK_BORDER);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("screen.rtsbuilding.theme.editor"),
                x + EDITOR_TITLE_X, y + EDITOR_TITLE_Y,
                SettingsWindowStyle.VALUE.toArgb(), false);
        if (!editable()) {
            g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    Component.translatable("screen.rtsbuilding.theme.editor.legacy"),
                    x + EDITOR_TITLE_X, y + EDITOR_LEGACY_Y,
                    SettingsWindowStyle.HINT.toArgb(), false);
            return;
        }

        UiThemeToken[] tokens = UiThemeToken.values();
        int listY = y + EDITOR_LIST_Y;
        int listW = w - EDITOR_LIST_WIDTH_RESERVE;
        int visibleRows = visibleRows(h);
        tokenScroll = Math.min(tokenScroll, Math.max(0, tokens.length - visibleRows));
        for (int row = 0; row < visibleRows; row++) {
            int index = tokenScroll + row;
            if (index >= tokens.length) break;
            UiThemeToken token = tokens[index];
            int rowY = listY + row * TOKEN_ROW_H;
            boolean selected = token == selectedToken;
            boolean hover = UiRect.contains(x + EDITOR_LIST_INSET, rowY, listW,
                    TOKEN_ROW_H - EDITOR_ROW_BOTTOM, mouseX, mouseY);
            var animation = tokenAnimations.update(
                    token,
                    new UiControlState(
                            true, true, hover, false, false,
                            selected, false, false, ""),
                    Config.isUiAnimationsEnabled());
            var rowBackground = com.rtsbuilding.rtsbuilding.uikit.theme.UiColor.interpolate(
                    SettingsWindowStyle.STEP_BACKGROUND,
                    SettingsWindowStyle.STEP_HOVER_BACKGROUND,
                    animation.hover());
            rowBackground = com.rtsbuilding.rtsbuilding.uikit.theme.UiColor.interpolate(
                    rowBackground,
                    SettingsWindowStyle.TOGGLE_ON,
                    animation.selection());
            g.fill(x + EDITOR_LIST_INSET, rowY, x + EDITOR_LIST_INSET + listW,
                    rowY + TOKEN_ROW_H - EDITOR_ROW_BOTTOM,
                    rowBackground.toArgb());
            g.fill(x + w - EDITOR_SWATCH_RIGHT, rowY + EDITOR_SWATCH_TOP,
                    x + w - EDITOR_SWATCH_END,
                    rowY + TOKEN_ROW_H - EDITOR_SWATCH_BOTTOM,
                    draft.color(token).toArgb());
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String tokenLabel = font.plainSubstrByWidth(Component.translatable(
                    "screen.rtsbuilding.theme.token." + token.serializedId()).getString(),
                    w - EDITOR_LABEL_WIDTH_RESERVE);
            g.drawString(font, tokenLabel,
                    x + EDITOR_LABEL_X, rowY + EDITOR_LABEL_Y,
                    SettingsWindowStyle.VALUE.toArgb(), false);
        }

        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + h - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        picker.render(g, pickerX, pickerY, wheelDragging, valueDragging);
        String hex = String.format(java.util.Locale.ROOT, "#%08X", draft.color(selectedToken).toArgb());
        g.drawString(net.minecraft.client.Minecraft.getInstance().font, hex,
                pickerX + ThemeColorPicker.WHEEL_SIZE + ThemeColorPicker.VALUE_GAP
                        + ThemeColorPicker.VALUE_W + EDITOR_HEX_GAP,
                pickerY + EDITOR_HEX_Y, SettingsWindowStyle.VALUE.toArgb(), false);
        g.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("screen.rtsbuilding.theme.editor.drag_hint"),
                pickerX + ThemeColorPicker.WHEEL_SIZE + ThemeColorPicker.VALUE_GAP
                        + ThemeColorPicker.VALUE_W + EDITOR_HEX_GAP,
                pickerY + EDITOR_HINT_Y, SettingsWindowStyle.HINT.toArgb(), false);
    }

    boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h) {
        if (!editable()) return false;
        int listY = y + EDITOR_LIST_Y;
        int visibleRows = visibleRows(h);
        if (UiRect.contains(x + EDITOR_LIST_INSET, listY,
                w - EDITOR_LIST_WIDTH_RESERVE, TOKEN_ROW_H * visibleRows,
                mouseX, mouseY)) {
            int row = (int) ((mouseY - listY) / TOKEN_ROW_H);
            int index = tokenScroll + row;
            if (index >= 0 && index < UiThemeToken.values().length) {
                selectedToken = UiThemeToken.values()[index];
                picker.setColor(draft.color(selectedToken));
                return true;
            }
        }
        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + h - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        if (picker.insideWheel(mouseX, mouseY, pickerX, pickerY)) {
            wheelDragging = true;
            picker.pickWheel(mouseX, mouseY, pickerX, pickerY);
            applyPicker();
            return true;
        }
        if (picker.insideValue(mouseX, mouseY, pickerX, pickerY)) {
            valueDragging = true;
            picker.pickValue(mouseY, pickerY);
            applyPicker();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int x, int y, int w, int h) {
        int pickerX = x + EDITOR_PICKER_INSET;
        int pickerY = y + h - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        if (wheelDragging) picker.pickWheel(mouseX, mouseY, pickerX, pickerY);
        else if (valueDragging) picker.pickValue(mouseY, pickerY);
        else return false;
        applyPicker();
        return true;
    }

    boolean mouseReleased() {
        boolean wasDragging = wheelDragging || valueDragging;
        wheelDragging = false;
        valueDragging = false;
        return wasDragging;
    }

    boolean mouseScrolled(double delta, int x, int y, int w, int h,
                          double mouseX, double mouseY) {
        if (!editable() || !UiRect.contains(x, y, w, h, mouseX, mouseY)) return false;
        int maximum = Math.max(0, UiThemeToken.values().length - visibleRows(h));
        tokenScroll = Math.max(0, Math.min(maximum, tokenScroll + (delta > 0 ? -1 : 1)));
        return true;
    }

    private void applyPicker() {
        draft.setColor(selectedToken, picker.color());
        dirty = true;
    }

    void release() {
        picker.release();
    }

    private static int visibleRows(int height) {
        int pickerTop = height - ThemeColorPicker.WHEEL_SIZE - EDITOR_PICKER_BOTTOM;
        int available = pickerTop - EDITOR_LIST_Y - LIST_PICKER_GAP;
        return Math.max(MIN_VISIBLE_ROWS,
                Math.min(MAX_VISIBLE_ROWS, available / TOKEN_ROW_H));
    }
}
