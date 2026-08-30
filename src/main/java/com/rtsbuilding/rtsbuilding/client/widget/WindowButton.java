package com.rtsbuilding.rtsbuilding.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.theme.DefaultButtonTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
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
 * Supports both smooth scaled textures and native-size pixel textures.
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
    /** 像素图模式不拉伸纹理，只在点击热区内按原生尺寸居中绘制。 */
    private boolean nativePixelTexture;
    /** 像素艺术即使需要适配既有按钮尺寸，也必须使用最近邻过滤。 */
    private boolean pixelArtTexture;

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
        this.pixelArtTexture = true;
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
        UiControlAnimationState.Snapshot animation = updateAnimation(effectiveHovered);
        UiControlVisualStyle visual = UiControlVisualStyle.animated(
                this.visualRole, animation);

        if (textureWidth > 0 && textureHeight > 0
                && renderAnimatedTexture(guiGraphics, animation)) {
            // 纹理按钮和纯色按钮消费同一组平滑交互通道。
        } else {
            // Render with solid colour
            renderWithLegacyTemplate(guiGraphics, animation, visual);
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
    private void renderWithTexture(GuiGraphics guiGraphics, ResourceLocation resolvedTexture,
                                   int currentV, int currentHeight, double alpha) {
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
        guiGraphics.setColor(1.0F, 1.0F, 1.0F,
                (float) Math.max(0.0D, Math.min(1.0D, alpha)));

        // 像素图必须使用最近邻；连续色纹理仍保留原有的平滑缩放。
        int minFilter = this.pixelArtTexture
                ? org.lwjgl.opengl.GL11.GL_NEAREST
                : org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
        int magFilter = this.pixelArtTexture
                ? org.lwjgl.opengl.GL11.GL_NEAREST
                : org.lwjgl.opengl.GL11.GL_LINEAR;
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            minFilter
        );
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            magFilter
        );
        if (!this.pixelArtTexture) {
            // 连续色纹理缩放时保留原有各向异性过滤；像素图不启用该路径。
            try {
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
            } catch (Exception ignored) {
                // 驱动不支持该扩展时继续使用普通线性过滤。
            }
        }

        if (this.nativePixelTexture) {
            int drawX = WindowButtonLayout.nativeTextureX(
                    this.getX(), this.width, textureWidth);
            int drawY = WindowButtonLayout.nativeTextureY(
                    this.getY(), this.height, currentHeight);
            guiGraphics.blit(
                    resolvedTexture, drawX, drawY,
                    textureU, currentV,
                    textureWidth, currentHeight,
                    fullTextureWidth, fullTextureHeight);
        } else {
            guiGraphics.pose().pushPose();
            float scaleX = (float) this.width / textureWidth;
            float scaleY = (float) this.height / textureHeight;
            guiGraphics.pose().translate(this.getX(), this.getY(), 0);
            guiGraphics.pose().scale(scaleX, scaleY, 1.0f);
            guiGraphics.blit(
                    resolvedTexture, 0, 0,
                    textureU, currentV,
                    textureWidth, currentHeight,
                    fullTextureWidth, fullTextureHeight);
            guiGraphics.pose().popPose();
        }

        // Restore default settings
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
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
     * 纹理态使用与纯色按钮相同的动画快照做交叉淡化。各权重严格相加为 1，
     * 快速滑过或在悬停中切换选中态时不会闪回空白帧。
     */
    private boolean renderAnimatedTexture(
            GuiGraphics guiGraphics, UiControlAnimationState.Snapshot animation) {
        if (this.stateTextureProvider == null) {
            if (this.textureLocation == null) {
                return false;
            }
            double hover = animation.hover();
            if (this.hoverTextureV == this.textureV
                    && this.hoverTextureHeight == this.textureHeight) {
                renderWithTexture(guiGraphics, this.textureLocation,
                        this.textureV, this.textureHeight, 1.0D);
            } else {
                if (hover < 0.999D) {
                    renderWithTexture(guiGraphics, this.textureLocation,
                            this.textureV, this.textureHeight, 1.0D - hover);
                }
                if (hover > 0.001D) {
                    renderWithTexture(guiGraphics, this.textureLocation,
                            this.hoverTextureV, this.hoverTextureHeight, hover);
                }
            }
            return true;
        }

        double pressed = animation.press();
        double selected = (1.0D - pressed) * animation.selection();
        double hovered = (1.0D - pressed)
                * (1.0D - animation.selection()) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - pressed - selected - hovered);
        boolean rendered = false;
        rendered |= renderStateTexture(guiGraphics, UiTextureState.INACTIVE, inactive);
        rendered |= renderStateTexture(guiGraphics, UiTextureState.HOVER, hovered);
        rendered |= renderStateTexture(guiGraphics, UiTextureState.ACTIVE, selected);
        rendered |= renderStateTexture(guiGraphics, UiTextureState.PRESSED, pressed);
        return rendered;
    }

    private boolean renderStateTexture(
            GuiGraphics guiGraphics, UiTextureState state, double weight) {
        if (weight <= 0.001D) {
            return false;
        }
        ResourceLocation texture = this.stateTextureProvider.resolve(state);
        if (texture == null) {
            return false;
        }
        renderWithTexture(guiGraphics, texture,
                this.textureV, this.textureHeight, weight);
        return true;
    }

    /**
     * Renders the button with solid colours (RTS dark style).
     */
    private void renderWithLegacyTemplate(
            GuiGraphics guiGraphics,
            UiControlAnimationState.Snapshot animation,
            UiControlVisualStyle visual) {
        // Palette 与 Legacy 都从 default_button 原素材取像素；这里只切片和交叉淡化。
        DefaultButtonTextureRenderer.renderAnimated(
                guiGraphics,
                new UiRect(this.getX(), this.getY(), this.width, this.height),
                animation,
                visual.getOverlay());
    }

    private UiControlAnimationState.Snapshot updateAnimation(boolean hovered) {
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
        return this.visualAnimation.update(
                state, Config.isUiAnimationsEnabled());
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

    /**
     * 让纹理保留原生像素尺寸并在按钮热区内居中；只应对像素艺术资源启用。
     */
    public void setNativePixelTexture(boolean nativePixelTexture) {
        this.nativePixelTexture = nativePixelTexture;
        if (nativePixelTexture) {
            this.pixelArtTexture = true;
        }
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
