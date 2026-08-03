package com.rtsbuilding.rtsbuilding.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowButtonLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
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

    /** 按当前视觉状态延迟解析纹理；用于 Legacy / Palette 双轨按钮。 */
    public interface StateTextureProvider {
        ResourceLocation resolve(UiTextureState state);
    }

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final StateTextureProvider stateTextureProvider;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;  // Texture V coordinate for hover state
    private final int hoverTextureHeight;  // Texture height for hover state
    private final int fullTextureWidth;   // Total width of the full texture
    private final int fullTextureHeight;  // Total height of the full texture
    private final UiControlAnimationState visualAnimation =
            new UiControlAnimationState(SystemUiClock.INSTANCE);
    private UiControlRole visualRole = UiControlRole.COMMAND;
    private boolean selectedVisual;
    private boolean pressedVisual;

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
        this.stateTextureProvider = null;
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
     * 创建使用四状态语义 provider 的纹理按钮。纹理路径会在每帧按 pressed、selected、hover、idle
     * 的优先级解析，因此切换主题后不需要重建控件或更改点击矩形。
     */
    public WindowButton(int x, int y, int width, int height, Component message,
                        StateTextureProvider stateTextureProvider,
                        int sourceWidth, int sourceHeight, OnPress onPress) {
        super(x, y, width, height, message);
        if (stateTextureProvider == null) {
            throw new IllegalArgumentException("stateTextureProvider");
        }
        this.onPress = onPress;
        this.textureLocation = null;
        this.stateTextureProvider = stateTextureProvider;
        this.textureU = 0;
        this.textureV = 0;
        this.textureWidth = sourceWidth;
        this.textureHeight = sourceHeight;
        this.hoverTextureV = 0;
        this.hoverTextureHeight = sourceHeight;
        this.fullTextureWidth = sourceWidth;
        this.fullTextureHeight = sourceHeight;
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
        boolean effectiveHovered = !globalSkipHover
                && this.isHoveredOrFocused();
        UiControlVisualStyle visual = resolveVisual(effectiveHovered);

        ResourceLocation resolvedTexture = resolveTexture(effectiveHovered);
        if (resolvedTexture != null && textureWidth > 0 && textureHeight > 0) {
            // Render with texture (vector scaling)
            renderWithTexture(guiGraphics, resolvedTexture);
        } else {
            // Render with solid colour
            renderWithSolidColor(guiGraphics, visual);
        }

        // Calculate text position (centred)
        int textColor = visual.getText().toArgb();
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, this.getMessage().getString(),
                WindowButtonLayout.textWidth(this.width));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = WindowButtonLayout.textY(this.getY(), this.height);

        // Draw text
        if (!label.isEmpty()) {
            guiGraphics.drawString(minecraft.font, label, textX, textY, textColor, false);
        }
    }

    /**
     * Renders the button with a texture (supports vector scaling and hover effects).
     */
    private void renderWithTexture(GuiGraphics guiGraphics, ResourceLocation resolvedTexture) {
        // Ensure the texture is loaded
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(resolvedTexture);

        if (texture == null) {
            // Try to trigger automatic texture loading
            try {
                // Use setShaderTexture to trigger texture loading
                RenderSystem.setShaderTexture(0, resolvedTexture);

                // Try to get the texture again
                texture = textureManager.getTexture(resolvedTexture);

                if (texture == null) {
                    // If still not loaded, draw a red rectangle as a hint
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                            WindowButtonStyle.MISSING_TEXTURE.toArgb());
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // If still not loaded, draw a red rectangle as a hint
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                        WindowButtonStyle.MISSING_TEXTURE.toArgb());
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
        RenderSystem.setShaderTexture(0, resolvedTexture);

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
            resolvedTexture,
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

    private ResourceLocation resolveTexture(boolean hovered) {
        if (stateTextureProvider == null) return textureLocation;
        UiTextureState state = this.pressedVisual
                ? UiTextureState.PRESSED
                : this.selectedVisual
                        ? UiTextureState.ACTIVE
                        : hovered ? UiTextureState.HOVER : UiTextureState.INACTIVE;
        return stateTextureProvider.resolve(state);
    }

    /**
     * Renders the button with solid colours (RTS dark style).
     */
    private void renderWithSolidColor(
            GuiGraphics guiGraphics,
            UiControlVisualStyle visual) {
        // 被更高层浮窗覆盖时，上层统一抑制 hover/focus 视觉。
        WindowButtonChromeRenderer.renderSolid(
                new MinecraftUiCanvas(guiGraphics, Minecraft.getInstance().font),
                new UiRect(this.getX(), this.getY(), this.width, this.height),
                visual);
    }

    private UiControlVisualStyle resolveVisual(boolean hovered) {
        UiControlState state = this.active
                ? new UiControlState(
                        true,
                        true,
                        hovered,
                        this.isFocused(),
                        this.pressedVisual,
                        this.selectedVisual,
                        false,
                        false,
                        "")
                : new UiControlState(
                        true,
                        false,
                        false,
                        false,
                        false,
                        this.selectedVisual,
                        false,
                        false,
                        "disabled");
        UiControlAnimationState.Snapshot animation =
                this.visualAnimation.update(
                        state, Config.isUiAnimationsEnabled());
        return UiControlVisualStyle.animated(this.visualRole, animation);
    }

    /**
     * 设置按钮在通用主题中的业务角色；只影响视觉，不改变点击行为。
     */
    public void setVisualRole(UiControlRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role");
        }
        this.visualRole = role;
    }

    /**
     * 更新可插值的选中视觉；业务状态仍由面板自己的 Core 快照持有。
     */
    public void setSelectedVisual(boolean selected) {
        this.selectedVisual = selected;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean consumed = super.mouseClicked(mouseX, mouseY, button);
        if (consumed) {
            this.pressedVisual = true;
        }
        return consumed;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.pressedVisual = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
