package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowTextBoxChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowTextBoxLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowTextBoxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Text input styled for RTS window panels.
 *
 * <p>This class is intentionally a thin wrapper around Minecraft's
 * {@link EditBox}. It owns only the dark window-theme chrome, placeholder text,
 * and common input filters. The owning panel remains responsible for focus
 * priority, search semantics, networking, and preventing mouse-wheel leakage to
 * the RTS camera.
 */
public class WindowTextBox extends EditBox {
    private String placeholder = "";
    private boolean autoScrollToEnd = true;
    private boolean centeredText = false;

    public enum InputMode {
        ANY,
        DIGITS_ONLY,
        LETTERS_ONLY
    }

    public WindowTextBox(int x, int y, int width, int height) {
        this(Minecraft.getInstance().font, x, y, width, height);
    }

    public WindowTextBox(Font font, int x, int y, int width, int height) {
        super(resolveFont(font), x, y, width, height, Component.empty());
        setBordered(false);
        setTextColor(WindowTextBoxStyle.TEXT.toArgb());
        setTextColorUneditable(WindowTextBoxStyle.TEXT_UNEDITABLE.toArgb());
        setCanLoseFocus(true);
    }

    private static Font resolveFont(Font font) {
        return font != null ? font : Minecraft.getInstance().font;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    public String getPlaceholder() {
        return this.placeholder;
    }

    public WindowTextBox setAutoScrollToEnd(boolean autoScrollToEnd) {
        this.autoScrollToEnd = autoScrollToEnd;
        return this;
    }

    public WindowTextBox setCenteredText(boolean centeredText) {
        this.centeredText = centeredText;
        return this;
    }

    @Override
    public void setValue(String text) {
        super.setValue(text == null ? "" : text);
        if (this.autoScrollToEnd) {
            scrollToEnd();
        }
    }

    public WindowTextBox scrollToEnd() {
        moveCursorToEnd(false);
        setHighlightPos(getCursorPosition());
        return this;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        int x = getX();
        int y = getY();
        Font font = Minecraft.getInstance().font;
        UiRect bounds = new UiRect(x, y, this.width, this.height);
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                bounds, font.lineHeight, font.width(getValue()),
                this.centeredText, !getValue().isEmpty());
        WindowTextBoxChromeRenderer.render(
                new MinecraftUiCanvas(g, font), geometry, isFocused());

        if (getValue().isEmpty() && !isFocused() && !this.placeholder.isEmpty()) {
            WindowTextBoxLayout.Geometry placeholderGeometry =
                    WindowTextBoxLayout.geometry(
                            bounds, font.lineHeight, font.width(this.placeholder),
                            this.centeredText, false);
            g.text(font, this.placeholder,
                    (int) placeholderGeometry.placeholderX,
                    (int) placeholderGeometry.textY,
                    WindowTextBoxStyle.PLACEHOLDER.toArgb(), false);
        }
        renderInnerEditBox(g, mouseX, mouseY, partialTick, x, y, geometry);
    }

    private void renderInnerEditBox(
            GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick,
            int outerX, int outerY, WindowTextBoxLayout.Geometry geometry) {
        int oldWidth = this.width;
        int oldHeight = this.height;
        int innerWidth = (int) geometry.inner.getWidth();
        int innerX = (int) geometry.inner.getX();
        int innerHeight = (int) geometry.inner.getHeight();
        int innerY = (int) geometry.inner.getY();
        setX(innerX);
        setY(innerY);
        this.width = innerWidth;
        this.height = innerHeight;
        try {
            super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        } finally {
            this.width = oldWidth;
            this.height = oldHeight;
            setX(outerX);
            setY(outerY);
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public WindowTextBox onTextChanged(Consumer<String> responder) {
        setResponder(responder == null ? value -> {} : responder);
        return this;
    }

    public WindowTextBox setInputFilter(Predicate<String> filter) {
        setFilter(filter == null ? value -> true : filter);
        return this;
    }

    public WindowTextBox setInputMode(InputMode mode) {
        InputMode safeMode = mode == null ? InputMode.ANY : mode;
        return switch (safeMode) {
            case DIGITS_ONLY -> setInputFilter(value -> value.matches("\\d*"));
            case LETTERS_ONLY -> setInputFilter(value -> value.matches("[a-zA-Z]*"));
            case ANY -> setInputFilter(value -> true);
        };
    }

    public WindowTextBox setReadOnly(boolean readOnly) {
        setEditable(!readOnly);
        return this;
    }

    public static WindowTextBox createDefault(int x, int y, int width) {
        WindowTextBox textBox = new WindowTextBox(
                x, y, width, WindowTextBoxLayout.DEFAULT_H);
        textBox.setPlaceholder("Search");
        textBox.setMaxLength(WindowTextBoxLayout.DEFAULT_MAX_LENGTH);
        return textBox;
    }
}
