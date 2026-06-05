package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared button for RTS window-style panels.
 *
 * <p>The widget owns only visual button rendering and press dispatch. It does
 * not decide panel layout, input priority, storage behavior, or camera routing;
 * those responsibilities stay with the owning screen/panel. Keeping this small
 * lets future window migrations reuse the same button affordance without
 * changing existing container overlays or world interaction paths.
 */
public class WindowButton extends AbstractButton {
    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int DISABLED_TEXT_COLOR = 0xFF8A94A1;
    private static final int BUTTON_BACKGROUND = 0xAA1C232D;
    private static final int BUTTON_HOVER = 0xCC2C3D52;
    private static final int BUTTON_DISABLED = 0x88434A55;
    private static final int BORDER_COLOR = 0xFF647B92;
    private static final int BORDER_HOVER = 0xFF8FA8C4;
    private static final int BORDER_DARK = 0xFF0D1117;

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;
    private final int hoverTextureHeight;
    private final int fullTextureWidth;
    private final int fullTextureHeight;

    @FunctionalInterface
    public interface OnPress {
        void onPress(WindowButton button);
    }

    public WindowButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    public WindowButton(int x, int y, int width, int height, Component message,
            ResourceLocation textureLocation, int textureU, int textureV,
            int textureWidth, int textureHeight, OnPress onPress) {
        this(x, y, width, height, message, textureLocation, textureU, textureV,
                textureWidth, textureHeight, textureV, textureHeight,
                textureWidth, textureHeight, onPress);
    }

    public WindowButton(int x, int y, int width, int height, Component message,
            ResourceLocation textureLocation, int textureU, int textureV,
            int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
            int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress == null ? button -> {} : onPress;
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

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.textureLocation != null && this.textureWidth > 0 && this.textureHeight > 0) {
            renderWithTexture(g);
        } else {
            renderWithSolidColor(g);
        }
        renderCenteredText(g);
    }

    private void renderWithTexture(GuiGraphics g) {
        int currentV = this.isHoveredOrFocused() ? this.hoverTextureV : this.textureV;
        int currentHeight = this.isHoveredOrFocused() ? this.hoverTextureHeight : this.textureHeight;
        g.pose().pushPose();
        g.pose().translate(getX(), getY(), 0.0F);
        g.pose().scale((float) this.width / this.textureWidth, (float) this.height / currentHeight, 1.0F);
        g.blit(this.textureLocation, 0, 0, this.textureU, currentV,
                this.textureWidth, currentHeight, this.fullTextureWidth, this.fullTextureHeight);
        g.pose().popPose();
    }

    private void renderWithSolidColor(GuiGraphics g) {
        int background = !this.active
                ? BUTTON_DISABLED
                : this.isHoveredOrFocused() ? BUTTON_HOVER : BUTTON_BACKGROUND;
        int border = this.isHoveredOrFocused() && this.active ? BORDER_HOVER : BORDER_COLOR;
        RtsClientUiUtil.drawPanelFrame(g, getX(), getY(), this.width, this.height, background, border, BORDER_DARK);
    }

    private void renderCenteredText(GuiGraphics g) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }
        Component message = getMessage();
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, message.getString(), Math.max(0, this.width - 6));
        int textX = getX() + (this.width - minecraft.font.width(label)) / 2;
        int textY = getY() + (this.height - minecraft.font.lineHeight) / 2;
        g.drawString(minecraft.font, label, textX, textY, this.active ? TEXT_COLOR : DISABLED_TEXT_COLOR, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
