package com.rtsbuilding.rtsbuilding.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private final ResourceLocation textureLocation;
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
                       ResourceLocation textureLocation, int textureU, int textureV,
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
                       ResourceLocation textureLocation, int textureU, int textureV,
                       int textureWidth, int textureHeight, OnPress onPress) {
        this(x, y, width, height, message, textureLocation, textureU, textureV,
             textureWidth, textureHeight, textureV, textureHeight,
             textureWidth, textureHeight, onPress);
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
            guiGraphics.drawString(minecraft.font, label, textX, textY, textColor, false);
        }
    }

    /**
     * Renders the button with a texture (supports vector scaling and hover effects).
     */
    private void renderWithTexture(GuiGraphics guiGraphics) {
        // Ensure the texture is loaded
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(textureLocation);

        if (texture == null) {
            // Try to trigger automatic texture loading
            try {
                // Use setShaderTexture to trigger texture loading
                RenderSystem.setShaderTexture(0, textureLocation);

                // Try to get the texture again
                texture = textureManager.getTexture(textureLocation);

                if (texture == null) {
                    // If still not loaded, draw a red rectangle as a hint
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // If still not loaded, draw a red rectangle as a hint
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                return;
            }
        }

        // Select texture region based on hover state (covered windows forced to non-hover texture)
        boolean effectiveHovered = isHovered && !globalSkipHover;
        int currentV = effectiveHovered ? hoverTextureV : textureV;
        int currentHeight = effectiveHovered ? hoverTextureHeight : textureHeight;

        // Enable blend mode for transparency
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE,
            org.lwjgl.opengl.GL11.GL_ZERO
        );

        // Bind texture (bind before setting parameters)
        RenderSystem.setShaderTexture(0, textureLocation);

        // Set high-quality texture filter parameters
        // Minification filter: trilinear (mipmap + linear interpolation)
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
        );
        // Magnification filter: linear interpolation
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR
        );
        // Try setting anisotropic filtering for better angled scaling quality
        // Note: anisotropic filtering is an OpenGL extension, check support
        try {
            // Use ARB_texture_filter_anisotropic extension constants
            int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
            int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;

            int maxAniso = org.lwjgl.opengl.GL11.glGetInteger(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            if (maxAniso > 0) {
                float anisoLevel = Math.min(16.0f, maxAniso);
                org.lwjgl.opengl.GL11.glTexParameterf(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    anisoLevel
                );
            }
        } catch (Exception e) {
            // Ignore unsupported anisotropic filtering
        }

        // Use PoseStack transform for scaling (avoids clipping issues)
        guiGraphics.pose().pushPose();

        // Calculate scale ratio (using button size and texture size to render)
        float scaleX = (float) this.width / textureWidth;
        float scaleY = (float) this.height / textureHeight;

        // Apply scale transform
        guiGraphics.pose().translate(this.getX(), this.getY(), 0);
        guiGraphics.pose().scale(scaleX, scaleY, 1.0f);

        // Draw texture at original size (blit automatically uses currently bound texture)
        guiGraphics.blit(
            textureLocation,
            0,  // Relative to transformed position
            0,  // Relative to transformed position
            textureU,
            currentV,      // Use the corresponding V coordinate
            textureWidth,  // Width to render
            currentHeight, // Height to render
            fullTextureWidth,   // Total width of the full texture
            fullTextureHeight   // Total height of the full texture
        );

        // Restore transform state
        guiGraphics.pose().popPose();

        // Restore default settings
        RenderSystem.disableBlend();
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
    }

    /**
     * Renders the button with solid colours (RTS dark style).
     */
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
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
