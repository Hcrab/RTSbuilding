package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowTextBoxChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowTextBoxLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowTextBoxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 1.12 {@link GuiTextField} 的 RTS 窗口包装；外框与内部编辑区始终使用同一内边距。
 */
@SuppressWarnings("this-escape")
public class WindowTextBox extends GuiTextField {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(10000);
    private final FontRenderer font;
    private String placeholder = "";
    private boolean autoScrollToEnd = true;
    private boolean centeredText;
    private Consumer<String> responder = new Consumer<String>() {
        @Override public void accept(String value) { }
    };

    public enum InputMode { ANY, DIGITS_ONLY, LETTERS_ONLY }

    public WindowTextBox(int x, int y, int width, int height) {
        this(Minecraft.getMinecraft().fontRenderer, x, y, width, height);
    }

    public WindowTextBox(FontRenderer font, int x, int y, int width, int height) {
        super(NEXT_ID.incrementAndGet(), resolveFont(font), x, y, width, height);
        this.font = resolveFont(font);
        setEnableBackgroundDrawing(false);
        setTextColor(WindowTextBoxStyle.TEXT.toArgb());
        setDisabledTextColour(WindowTextBoxStyle.TEXT_UNEDITABLE.toArgb());
        setCanLoseFocus(true);
    }

    private static FontRenderer resolveFont(FontRenderer font) {
        return font != null ? font : Minecraft.getMinecraft().fontRenderer;
    }

    public void setPlaceholder(String value) { placeholder = value == null ? "" : value; }
    public String getPlaceholder() { return placeholder; }
    public WindowTextBox setAutoScrollToEnd(boolean value) { autoScrollToEnd = value; return this; }
    public WindowTextBox setCenteredText(boolean value) { centeredText = value; return this; }

    public void setValue(String value) { setText(value); }
    public String getValue() { return getText(); }

    @Override public void setText(String value) {
        String before = getText();
        super.setText(value == null ? "" : value);
        if (autoScrollToEnd) scrollToEnd();
        notifyIfChanged(before);
    }

    @Override public void writeText(String value) {
        String before = getText();
        super.writeText(value);
        notifyIfChanged(before);
    }

    @Override public void deleteFromCursor(int count) {
        String before = getText();
        super.deleteFromCursor(count);
        notifyIfChanged(before);
    }

    @Override public boolean textboxKeyTyped(char typedChar, int keyCode) {
        return super.textboxKeyTyped(typedChar, keyCode);
    }

    public WindowTextBox scrollToEnd() {
        setCursorPositionEnd();
        setSelectionPos(getCursorPosition());
        return this;
    }

    public void renderWidget(LegacyGuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!getVisible()) return;
        GeometryState geometry = geometry();
        WindowTextBoxChromeRenderer.render(new MinecraftUiCanvas(graphics, font), geometry.geometry, isFocused());
        if (getText().isEmpty() && !isFocused() && !placeholder.isEmpty()) {
            WindowTextBoxLayout.Geometry placeholderGeometry = WindowTextBoxLayout.geometry(
                    geometry.bounds, font.FONT_HEIGHT, font.getStringWidth(placeholder), centeredText, false);
            graphics.drawString(font, placeholder, (int) placeholderGeometry.placeholderX,
                    (int) placeholderGeometry.textY, WindowTextBoxStyle.PLACEHOLDER.toArgb(), false);
        }
        int normalColor = WindowTextBoxStyle.TEXT.toArgb();
        int disabledColor = WindowTextBoxStyle.TEXT_UNEDITABLE.toArgb();
        setTextColor(applyAlpha(normalColor, graphics.alphaMultiplier()));
        setDisabledTextColour(applyAlpha(disabledColor, graphics.alphaMultiplier()));
        try {
            withInnerGeometry(geometry.geometry, new Runnable() {
                @Override public void run() { WindowTextBox.super.drawTextBox(); }
            });
        } finally {
            setTextColor(normalColor);
            setDisabledTextColour(disabledColor);
        }
    }

    public void render(LegacyGuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(final int mouseX, final int mouseY, final int button) {
        final boolean[] result = {false};
        withInnerGeometry(geometry().geometry, new Runnable() {
            @Override public void run() { result[0] = WindowTextBox.super.mouseClicked(mouseX, mouseY, button); }
        });
        return result[0];
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked((int) mouseX, (int) mouseY, button);
    }

    public WindowTextBox onTextChanged(Consumer<String> value) {
        responder = value == null ? new Consumer<String>() {
            @Override public void accept(String ignored) { }
        } : value;
        return this;
    }

    public WindowTextBox setInputFilter(final Predicate<String> filter) {
        setValidator(new com.google.common.base.Predicate<String>() {
            @Override public boolean apply(String value) { return filter == null || filter.test(value); }
        });
        return this;
    }

    public WindowTextBox setInputMode(InputMode mode) {
        InputMode safe = mode == null ? InputMode.ANY : mode;
        switch (safe) {
            case DIGITS_ONLY: return setInputFilter(value -> value.matches("\\d*"));
            case LETTERS_ONLY: return setInputFilter(value -> value.matches("[a-zA-Z]*"));
            case ANY: return setInputFilter(value -> true);
            default: throw new AssertionError(safe);
        }
    }

    public WindowTextBox setReadOnly(boolean readOnly) { setEnabled(!readOnly); return this; }
    public void setMaxLength(int length) { setMaxStringLength(length); }
    public void setX(int value) { this.x = value; }
    public void setY(int value) { this.y = value; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }

    public static WindowTextBox createDefault(int x, int y, int width) {
        WindowTextBox box = new WindowTextBox(x, y, width, WindowTextBoxLayout.DEFAULT_H);
        box.setPlaceholder("Search");
        box.setMaxStringLength(WindowTextBoxLayout.DEFAULT_MAX_LENGTH);
        return box;
    }

    private GeometryState geometry() {
        UiRect bounds = new UiRect(x, y, width, height);
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                bounds, font.FONT_HEIGHT, font.getStringWidth(getText()), centeredText, !getText().isEmpty());
        return new GeometryState(bounds, geometry);
    }

    private void withInnerGeometry(WindowTextBoxLayout.Geometry geometry, Runnable action) {
        int oldX = x, oldY = y, oldWidth = width, oldHeight = height;
        x = (int) geometry.inner.getX();
        y = (int) geometry.inner.getY();
        width = Math.max(1, (int) geometry.inner.getWidth());
        height = Math.max(1, (int) geometry.inner.getHeight());
        try { action.run(); }
        finally { x = oldX; y = oldY; width = oldWidth; height = oldHeight; }
    }

    private void notifyIfChanged(String before) {
        if (!getText().equals(before)) responder.accept(getText());
    }

    /** 使原版编辑态文字、选择和光标跟随父窗口，不另建一套文本绘制路径。 */
    private static int applyAlpha(int color, float multiplier) {
        int alpha = (int) Math.round((color >>> 24 & 255) * multiplier);
        return alpha << 24 | color & 16777215;
    }

    private static final class GeometryState {
        private final UiRect bounds;
        private final WindowTextBoxLayout.Geometry geometry;
        private GeometryState(UiRect bounds, WindowTextBoxLayout.Geometry geometry) {
            this.bounds = bounds; this.geometry = geometry;
        }
    }
}
