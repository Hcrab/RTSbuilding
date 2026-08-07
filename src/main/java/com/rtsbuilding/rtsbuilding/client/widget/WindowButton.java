package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.theme.DefaultButtonTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowButtonLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
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
    /** Palette 只乘色 Legacy 原图；未指定时保持旧的白色原样提交。 */
    private UiColor textureTint;

    private RtsControlState controlState = RtsControlState.enabled(RtsControlRole.COMMAND);
    private final UiControlAnimationState visualAnimation =
            new UiControlAnimationState(SystemUiClock.INSTANCE);
    private boolean pressedVisual;

    /**
     * When set, all WindowButton instances suppress hover/focus effects.
     * Used by RtsWindowPanel when rendering a window that is
     * covered by a higher overlapping window.
     */
    private static boolean globalSkipHover;
    private static double globalOpacity = 1.0D;

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

    /**
     * 指定纹理在 Palette 主题下使用的语义乘色。该方法不改变贴图、UV 或点击区域。
     */
    public WindowButton setTextureTint(UiColor textureTint) {
        this.textureTint = textureTint;
        return this;
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
        boolean consumed = this.mouseClicked(new MouseButtonEvent(
                mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        if (consumed) {
            this.pressedVisual = true;
        }
        return consumed;
    }

    @Override
    protected void extractContents(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean effectiveHovered = !globalSkipHover && this.isHoveredOrFocused();
        UiControlAnimationState.Snapshot animation = updateAnimation(effectiveHovered);
        UiControlVisualStyle visual = UiControlVisualStyle.animated(
                visualRole(), animation);

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            // 业务专属贴图仍由资源包控制；通用按钮改走同一套九宫格像素母版。
            renderWithTexture(guiGraphics);
        } else {
            DefaultButtonTextureRenderer.renderAnimated(
                    guiGraphics,
                    new UiRect(this.getX(), this.getY(), this.width, this.height),
                    animation, visual.getOverlay(), globalOpacity);
        }

        int textColor = applyOpacity(visual.getText().toArgb());
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, this.getMessage().getString(),
                WindowButtonLayout.textWidth(this.width));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = WindowButtonLayout.textY(this.getY(), this.height);

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
        try {
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
                fullTextureHeight,
                applyOpacity(textureTint == null ? RtsMainlineTheme.LEGACY_FFFFFFFF.toArgb() : textureTint.toArgb())
            );
        } catch (RuntimeException ignored) {
            // 资源包提供了非法贴图时给出主题化可见提示，避免点击热区变成无反馈空洞。
            guiGraphics.fill(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    applyOpacity(WindowButtonStyle.MISSING_TEXTURE.toArgb()));
        }
    }

    /** 将既有业务状态转换成 Core 的视觉快照；不改变原来的可点击规则。 */
    private UiControlAnimationState.Snapshot updateAnimation(boolean hovered) {
        String disabledReason = this.controlState.disabledReason() == null
                ? "disabled" : this.controlState.disabledReason().getString();
        UiControlState state = new UiControlState(
                true,
                this.active,
                hovered,
                this.isFocused(),
                this.pressedVisual && this.active,
                this.controlState.selected(),
                this.controlState.pending(),
                this.controlState.failed(),
                this.active ? "" : disabledReason);
        UiControlAnimationState.Snapshot snapshot = this.visualAnimation.update(
                state, Config.isUiAnimationsEnabled());
        this.pressedVisual = false;
        return snapshot;
    }

    private UiControlRole visualRole() {
        return switch (this.controlState.role()) {
            case PRIMARY_ACTION -> UiControlRole.PRIMARY_ACTION;
            case DESTRUCTIVE -> UiControlRole.DESTRUCTIVE;
            case MODE -> UiControlRole.MODE;
            case TOGGLE -> UiControlRole.TOGGLE;
            case COMMAND -> UiControlRole.COMMAND;
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
    /**
     * 为同一帧的父窗口临时抑制悬停，并返回先前状态，避免相邻浮窗串扰。
     */
    public static boolean setGlobalSkipHover(boolean skip) {
        boolean previous = globalSkipHover;
        globalSkipHover = skip;
        return previous;
    }

    /**
     * 由父窗口在绘制其子控件前暂存透明度。返回旧值，调用方必须在 finally 中恢复。
     */
    public static double setGlobalOpacity(double opacity) {
        double previous = globalOpacity;
        globalOpacity = Math.max(0.0D, Math.min(1.0D, opacity));
        return previous;
    }

    private static int applyOpacity(int color) {
        int sourceAlpha = color >>> 24 & 0xFF;
        int alpha = (int) Math.round(sourceAlpha * globalOpacity);
        return alpha << 24 | color & RtsMainlineTheme.LEGACY_00FFFFFF.toArgb();
    }
}
