package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowButtonLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.opengl.GL11;

/** 1.12 RTS 窗口按钮，保留纯色/纹理、按下、选中和全局 hover 抑制语义。 */
public class WindowButton extends GuiButton {
    public interface OnPress { void onPress(WindowButton button); }

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final int textureU, textureV, textureWidth, textureHeight;
    private final int hoverTextureV, hoverTextureHeight, fullTextureWidth, fullTextureHeight;
    private final UiControlAnimationState visualAnimation = new UiControlAnimationState(SystemUiClock.INSTANCE);
    private UiControlRole visualRole = UiControlRole.COMMAND;
    private boolean selectedVisual;
    private boolean pressedVisual;
    private boolean focusedVisual;
    private static boolean globalSkipHover;

    public WindowButton(int xPosition, int yPosition, int width, int height, IChatComponent message, OnPress onPress) {
        this(xPosition, yPosition, width, height, message, null, 0, 0, 0, 0, onPress);
    }
    public WindowButton(int xPosition, int yPosition, int width, int height, String message, OnPress onPress) {
        this(xPosition, yPosition, width, height, new ChatComponentText(message == null ? "" : message), onPress);
    }
    public WindowButton(int xPosition, int yPosition, int width, int height, IChatComponent message,
            ResourceLocation texture, int u, int v, int textureWidth, int textureHeight, OnPress onPress) {
        this(xPosition, yPosition, width, height, message, texture, u, v, textureWidth, textureHeight,
                v, textureHeight, textureWidth, textureHeight, onPress);
    }
    public WindowButton(int xPosition, int yPosition, int width, int height, IChatComponent message,
            ResourceLocation texture, int u, int v, int textureWidth, int textureHeight,
            int hoverV, int hoverHeight, int fullWidth, int fullHeight, OnPress onPress) {
        super(0, xPosition, yPosition, width, height, message == null ? "" : message.getUnformattedText());
        this.onPress = onPress;
        this.textureLocation = texture;
        this.textureU = u; this.textureV = v;
        this.textureWidth = textureWidth; this.textureHeight = textureHeight;
        this.hoverTextureV = hoverV; this.hoverTextureHeight = hoverHeight;
        this.fullTextureWidth = fullWidth; this.fullTextureHeight = fullHeight;
    }

    public void onPress() { if (onPress != null) onPress.onPress(this); }

    @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        drawButton(minecraft, mouseX, mouseY, 0.0F);
    }

    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        boolean hoveredNow = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
        this.field_146123_n = hoveredNow;
        boolean effectiveHovered = !globalSkipHover && (hoveredNow || focusedVisual);
        UiControlVisualStyle visual = resolveVisual(effectiveHovered);
        LegacyGuiGraphics graphics = new LegacyGuiGraphics(minecraft,
                new net.minecraft.client.gui.ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaledWidth(),
                new net.minecraft.client.gui.ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaledHeight());
        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            renderTexture(minecraft, graphics, visual, effectiveHovered);
        }
        else WindowButtonChromeRenderer.renderSolid(new MinecraftUiCanvas(graphics, minecraft.fontRenderer),
                new UiRect(xPosition, yPosition, width, height), visual);

        FontRenderer font = minecraft.fontRenderer;
        String label = font.trimStringToWidth(displayString, WindowButtonLayout.textWidth(width));
        if (!label.isEmpty()) {
            int textX = xPosition + (width - font.getStringWidth(label)) / 2;
            int textY = WindowButtonLayout.textY(yPosition, height);
            font.drawString(label, textX, textY, visual.getText().toArgb(), false);
        }
    }

    public void render(LegacyGuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawButton(Minecraft.getMinecraft(), mouseX, mouseY, partialTick);
    }

    private void renderTexture(Minecraft minecraft, LegacyGuiGraphics graphics,
            UiControlVisualStyle visual, boolean effectiveHovered) {
        try {
            minecraft.getTextureManager().bindTexture(textureLocation);
        } catch (RuntimeException exception) {
            graphics.fill(xPosition, yPosition, xPosition + width, yPosition + height, WindowButtonStyle.MISSING_TEXTURE.toArgb());
            return;
        }
        boolean activeTexture = useActiveTexture(
                selectedVisual, effectiveHovered, pressedVisual);
        int currentV = activeTexture ? hoverTextureV : textureV;
        int currentHeight = activeTexture ? hoverTextureHeight : textureHeight;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        try {
            // 旧版 OpenGL 会继承调用方颜色；纹理按钮必须像主线一样从白色无染色状态开始。
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            com.rtsbuilding.rtsbuilding.platform.client.GuiCompat.drawScaledCustomSizeModalRect(
                    xPosition, yPosition, textureU, currentV, textureWidth, currentHeight,
                    width, height, fullTextureWidth, fullTextureHeight);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager.disableBlend();
        }
        if (visual.getOverlay().alpha() > 0) {
            // 锁定按钮沿用主线通用控件的 disabled overlay，不再与可用按钮长得一样。
            graphics.fill(xPosition, yPosition, xPosition + width, yPosition + height, visual.getOverlay().toArgb());
        }
    }

    /** 纹理下半区同时承担主线的 hover、按下与持续选中状态。 */
    static boolean useActiveTexture(boolean selected, boolean hovered, boolean pressed) {
        return selected || hovered || pressed;
    }

    private UiControlVisualStyle resolveVisual(boolean hoveredNow) {
        UiControlState state = enabled
                ? new UiControlState(true, true, hoveredNow, focusedVisual, pressedVisual,
                        selectedVisual, false, false, "")
                : new UiControlState(true, false, false, false, false,
                        selectedVisual, false, false, "disabled");
        return UiControlVisualStyle.animated(visualRole,
                visualAnimation.update(state, Config.isUiAnimationsEnabled()));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !mousePressed(Minecraft.getMinecraft(), (int) mouseX, (int) mouseY)) return false;
        pressedVisual = true;
        onPress();
        return true;
    }
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasPressed = pressedVisual;
        pressedVisual = false;
        super.mouseReleased((int) mouseX, (int) mouseY);
        return button == 0 && wasPressed;
    }
    public void setX(int value) { xPosition = value; }
    public void setY(int value) { yPosition = value; }
    public int getX() { return xPosition; }
    public int getY() { return yPosition; }
    public void setFocused(boolean value) { focusedVisual = value; }
    public boolean isFocused() { return focusedVisual; }
    public void setVisualRole(UiControlRole role) {
        if (role == null) throw new IllegalArgumentException("role");
        visualRole = role;
    }
    public void setSelectedVisual(boolean selected) { selectedVisual = selected; }
    public static void setGlobalSkipHover(boolean skip) { globalSkipHover = skip; }
}
