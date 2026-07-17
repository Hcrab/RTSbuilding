package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
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
    private static final int PRIMARY_BACKGROUND = 0xCC244E35;
    private static final int PRIMARY_HOVER = 0xDD326A49;
    private static final int PRIMARY_BORDER = 0xFF7FCEA0;
    private static final int SELECTED_BACKGROUND = 0xDD244A67;
    private static final int SELECTED_HOVER = 0xDD315F82;
    private static final int SELECTED_BORDER = 0xFF83BDE8;
    private static final int PENDING_BACKGROUND = 0xDD5A4720;
    private static final int PENDING_BORDER = 0xFFE0B65F;
    private static final int FAILED_BACKGROUND = 0xDD5A2529;
    private static final int FAILED_BORDER = 0xFFE47A82;
    private static final int DESTRUCTIVE_BACKGROUND = 0xCC40252A;
    private static final int DESTRUCTIVE_HOVER = 0xDD583139;
    private static final int DESTRUCTIVE_BORDER = 0xFFD58A91;

    private RtsControlState controlState = RtsControlState.enabled(RtsControlRole.COMMAND);

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

    public WindowButton applyControlState(RtsControlState state) {
        this.controlState = state == null
                ? RtsControlState.enabled(RtsControlRole.COMMAND)
                : state;
        this.active = this.controlState.enabled() && !this.controlState.pending();
        this.setTooltip(this.controlState.disabledReason() == null
                ? null
                : Tooltip.create(this.controlState.disabledReason()));
        return this;
    }

    public RtsControlState controlState() {
        return this.controlState;
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
        boolean hovered = !globalSkipHover && this.isHoveredOrFocused();
        int backgroundColor = resolveBackgroundColor(hovered);
        int borderLight = resolveBorderLightColor();
        RtsClientUiUtil.drawPanelFrame(guiGraphics,
                this.getX(), this.getY(), this.width, this.height,
                backgroundColor, borderLight, BORDER_DARK);
    }

    private int resolveBackgroundColor(boolean hovered) {
        if (!this.active && !this.controlState.pending()) {
            return BUTTON_BACKGROUND;
        }
        if (this.controlState.failed()) {
            return FAILED_BACKGROUND;
        }
        if (this.controlState.pending()) {
            return PENDING_BACKGROUND;
        }
        if (this.controlState.selected()) {
            return hovered ? SELECTED_HOVER : SELECTED_BACKGROUND;
        }
        return switch (this.controlState.role()) {
            case PRIMARY_ACTION -> hovered ? PRIMARY_HOVER : PRIMARY_BACKGROUND;
            case DESTRUCTIVE -> hovered ? DESTRUCTIVE_HOVER : DESTRUCTIVE_BACKGROUND;
            case COMMAND, MODE, TOGGLE -> hovered ? BUTTON_HOVER : BUTTON_BACKGROUND;
        };
    }

    private int resolveBorderLightColor() {
        if (this.controlState.failed()) {
            return FAILED_BORDER;
        }
        if (this.controlState.pending()) {
            return PENDING_BORDER;
        }
        if (this.controlState.selected()) {
            return SELECTED_BORDER;
        }
        return switch (this.controlState.role()) {
            case PRIMARY_ACTION -> PRIMARY_BORDER;
            case DESTRUCTIVE -> DESTRUCTIVE_BORDER;
            case COMMAND, MODE, TOGGLE -> BORDER_LIGHT;
        };
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
