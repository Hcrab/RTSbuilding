package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Custom window button.
 * Supports texture rendering and vector scaling.
 */
public class WindowButton extends AbstractButton {

    public interface OnPress {
        void onPress(WindowButton button);
    }

    private final OnPress onPress;
    private final Identifier textureLocation;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;  // Texture V coordinate for hover state
    private final int hoverTextureHeight;  // Texture height for hover state
    private final int fullTextureWidth;   // Total width of the full texture
    private final int fullTextureHeight;  // Total height of the full texture

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    /**
     * When set, all WindowButton instances suppress hover/focus effects.
     * Used by RtsWindowPanel when rendering a window that is
     * covered by a higher overlapping window.
     */
    private static boolean globalSkipHover;

    /**
     * Creates a solid-colour button.
     */
    public WindowButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    /**
     * Creates a textured button with hover state switching support.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param width button width
     * @param height button height
     * @param message button text
     * @param textureLocation texture resource location (null for solid colour)
     * @param textureU texture U coordinate
     * @param textureV texture V coordinate (normal state)
     * @param textureWidth texture width
     * @param textureHeight texture height (normal state)
     * @param hoverTextureV texture V coordinate for hover state
     * @param hoverTextureHeight texture height for hover state
     * @param fullTextureWidth total width of the full texture
     * @param fullTextureHeight total height of the full texture
     * @param onPress click callback
     */
    public WindowButton(int x, int y, int width, int height, Component message,
                       Identifier textureLocation, int textureU, int textureV,
                       int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
                       int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.textureLocation = textureLocation;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.hoverTextureV = hoverTextureV;
        this.hoverTextureHeight = hoverTextureHeight;
        this.fullTextureWidth = fullTextureWidth;
        this.fullTextureHeight = fullTextureHeight;
    }

    /**
     * Creates a textured button (legacy-compatible, uses same texture for hover).
     */
    public WindowButton(int x, int y, int width, int height, Component message,
                       Identifier textureLocation, int textureU, int textureV,
                       int textureWidth, int textureHeight, OnPress onPress) {
        this(x, y, width, height, message, textureLocation, textureU, textureV,
             textureWidth, textureHeight, textureV, textureHeight,
             textureWidth, textureHeight, onPress);
    }

    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    public void onPress(@NotNull InputWithModifiers input) {
        onPress();
    }

    /**
     * 旧面板仍以直接绘制子控件的方式组织；在全部面板改成控件树前，
     * 这里把调用适配到 26.1 的状态提取入口。
     */
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.mouseClicked(new MouseButtonEvent(
                mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
    }

    @Override
    protected void extractContents(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            // Render with texture (vector scaling)
            renderWithTexture(guiGraphics);
        } else {
            // Render with solid colour
            renderWithSolidColor(guiGraphics);
        }

        // Calculate text position (centred)
        int textColor = this.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        // Draw text
        if (!label.isEmpty()) {
            guiGraphics .text(minecraft.font, label, textX, textY, textColor, false);
        }
    }

    /**
     * Renders the button with a texture (supports vector scaling and hover effects).
     */
    private void renderWithTexture(GuiGraphicsExtractor guiGraphics) {
        // Select texture region based on hover state (covered windows forced to non-hover texture)
        boolean effectiveHovered = isHovered() && !globalSkipHover;
        int currentV = effectiveHovered ? hoverTextureV : textureV;
        int currentHeight = effectiveHovered ? hoverTextureHeight : textureHeight;
        // 26.1 只提取 GUI 渲染状态；纹理绑定、混合与过滤由渲染管线统一管理。
        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            textureLocation,
            this.getX(),
            this.getY(),
            textureU,
            currentV,
            this.width,
            this.height,
            textureWidth,
            currentHeight,
            fullTextureWidth,
            fullTextureHeight
        );
    }

    /**
     * Renders the button with solid colours (RTS dark style).
     */
    private void renderWithSolidColor(GuiGraphicsExtractor guiGraphics) {
        // Determine background colour (covered windows forced to non-hover colour)
        int backgroundColor = (!globalSkipHover && this.isHoveredOrFocused()) ? BUTTON_HOVER : BUTTON_BACKGROUND;
        RtsClientUiUtil.drawPanelFrame(guiGraphics,
                this.getX(), this.getY(), this.width, this.height,
                backgroundColor, BORDER_LIGHT, BORDER_DARK);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    /**
     * Sets whether all WindowButton instances should globally skip
     * hover/focus visual effects during the next render call.
     */
    public static void setGlobalSkipHover(boolean skip) {
        globalSkipHover = skip;
    }
}
