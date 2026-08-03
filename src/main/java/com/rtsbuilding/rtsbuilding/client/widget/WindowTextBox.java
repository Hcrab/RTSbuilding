package com.rtsbuilding.rtsbuilding.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowTextBoxChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowTextBoxLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowTextBoxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
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
    private boolean chromeVisible = true;

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

    /** 允许已有面板自行绘制输入框外框，同时继续复用版本兼容的编辑区域。 */
    public WindowTextBox setChromeVisible(boolean chromeVisible) {
        this.chromeVisible = chromeVisible;
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
        moveCursorToEnd();
        setHighlightPos(getCursorPosition());
        return this;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderWidget(new RtsGuiContext(poseStack), mouseX, mouseY, partialTick);
    }

    /** 供版本稳定的 RTS 面板渲染路径直接调用。 */
    public void render(RtsGuiContext g, int mouseX, int mouseY, float partialTick) {
        renderWidget(g, mouseX, mouseY, partialTick);
    }

    public int getX() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
    }

    public void renderWidget(RtsGuiContext g, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        int x = this.x;
        int y = this.y;
        Font font = Minecraft.getInstance().font;
        UiRect bounds = new UiRect(x, y, this.width, this.height);
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                bounds, font.lineHeight, font.width(getValue()),
                this.centeredText, !getValue().isEmpty());
        if (this.chromeVisible) {
            WindowTextBoxChromeRenderer.render(
                    new MinecraftUiCanvas(g, font), geometry, isFocused());
        }

        if (getValue().isEmpty() && !isFocused() && !this.placeholder.isEmpty()) {
            WindowTextBoxLayout.Geometry placeholderGeometry = WindowTextBoxLayout.geometry(
                    bounds, font.lineHeight, font.width(this.placeholder),
                    this.centeredText, false);
            g.drawString(font, this.placeholder,
                    (int) placeholderGeometry.placeholderX,
                    (int) placeholderGeometry.textY,
                    WindowTextBoxStyle.PLACEHOLDER.toArgb(), false);
        }
        renderInnerEditBox(g, mouseX, mouseY, partialTick, x, y, geometry);
    }

    private void renderInnerEditBox(
            RtsGuiContext g, int mouseX, int mouseY, float partialTick,
            int outerX, int outerY, WindowTextBoxLayout.Geometry geometry) {
        int oldWidth = this.width;
        int oldHeight = this.height;
        int innerWidth = (int) geometry.inner.getWidth();
        int innerX = (int) geometry.inner.getX();
        int innerHeight = (int) geometry.inner.getHeight();
        int innerY = (int) geometry.inner.getY();
        setX(innerX);
        this.y = innerY;
        this.width = innerWidth;
        this.height = innerHeight;
        try {
            super.renderButton(g.pose(), mouseX, mouseY, partialTick);
        } finally {
            this.width = oldWidth;
            this.height = oldHeight;
            setX(outerX);
            this.y = outerY;
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
        WindowTextBox textBox = new WindowTextBox(
                x, y, width, WindowTextBoxLayout.DEFAULT_H);
        textBox.setPlaceholder("Search");
        textBox.setMaxLength(WindowTextBoxLayout.DEFAULT_MAX_LENGTH);
        return textBox;
    }
}
