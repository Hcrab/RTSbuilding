package com.rtsbuilding.rtsbuilding.client.widget;

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
    private static final int TEXT_COLOR = 0xFFEAF2FF;
    private static final int TEXT_COLOR_UNEDITABLE = 0xFF777F8B;
    private static final int BG_COLOR = 0xFF202832;
    private static final int BORDER_COLOR = 0xFF3A4555;
    private static final int BORDER_COLOR_FOCUSED = 0xFF6D7C90;
    private static final int PLACEHOLDER_COLOR = 0xFF68778A;
    private static final int TEXT_PADDING_X = 4;

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
        setTextColor(TEXT_COLOR);
        setTextColorUneditable(TEXT_COLOR_UNEDITABLE);
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
    public void renderWidget(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        int x = getX();
        int y = getY();
        int borderColor = isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR;
        g.fill(x, y, x + this.width, y + this.height, BG_COLOR);
        g.fill(x, y, x + this.width, y + 1, borderColor);
        g.fill(x, y + this.height - 1, x + this.width, y + this.height, borderColor);
        g.fill(x, y, x + 1, y + this.height, borderColor);
        g.fill(x + this.width - 1, y, x + this.width, y + this.height, borderColor);

        if (getValue().isEmpty() && !isFocused() && !this.placeholder.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int textY = y + (this.height - font.lineHeight) / 2;
            int textX = this.centeredText
                    ? x + Math.max(TEXT_PADDING_X, (this.width - font.width(this.placeholder)) / 2)
                    : x + TEXT_PADDING_X;
            g .text(font, this.placeholder, textX, textY, PLACEHOLDER_COLOR, false);
        }
        renderInnerEditBox(g, mouseX, mouseY, partialTick, x, y);
    }

    private void renderInnerEditBox(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, int outerX, int outerY) {
        int oldWidth = this.width;
        int oldHeight = this.height;
        int innerWidth = Math.max(1, oldWidth - TEXT_PADDING_X * 2);
        int innerX = outerX + TEXT_PADDING_X;
        int innerHeight = Math.max(8, Math.min(20, oldHeight - 2));
        int innerY = outerY + Math.max(1, (oldHeight - innerHeight) / 2);
        if (this.centeredText && !getValue().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(getValue());
            if (textWidth < innerWidth) {
                innerX += (innerWidth - textWidth) / 2;
            }
        }
        setX(innerX);
        setY(innerY);
        this.width = innerWidth;
        this.height = innerHeight;
        try {
            super.renderWidget(g, mouseX, mouseY, partialTick);
        } finally {
            this.width = oldWidth;
            this.height = oldHeight;
            setX(outerX);
            setY(outerY);
        }
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
        WindowTextBox textBox = new WindowTextBox(x, y, width, 20);
        textBox.setPlaceholder("Search");
        textBox.setMaxLength(256);
        return textBox;
    }
}
