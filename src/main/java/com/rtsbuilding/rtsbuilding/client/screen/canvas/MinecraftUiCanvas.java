package com.rtsbuilding.rtsbuilding.client.screen.canvas;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiClipStack;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/** 将 1.12 立即绘制 GUI 适配为纯 UI Core/Kit 画布。 */
public final class MinecraftUiCanvas implements UiCanvas2D {
    private final LegacyGuiGraphics graphics;
    private final FontRenderer font;
    private final BuilderScreen screen;
    private final UiClipStack clips = new UiClipStack();

    public MinecraftUiCanvas(LegacyGuiGraphics graphics, FontRenderer font) {
        this(graphics, font, null);
    }

    public MinecraftUiCanvas(LegacyGuiGraphics graphics, FontRenderer font, BuilderScreen screen) {
        if (graphics == null || font == null) throw new IllegalArgumentException("graphics and font must not be null");
        this.graphics = graphics;
        this.font = font;
        this.screen = screen;
    }

    @Override public void fill(UiRect rect, UiColor color) {
        fill(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), color);
    }
    @Override public void fill(double x, double y, double width, double height, UiColor color) {
        graphics.fill(round(x), round(y), round(x + width), round(y + height), color.toArgb());
    }
    @Override public void text(String text, double x, double topY, UiColor color) {
        graphics.drawString(font, text == null ? "" : text, round(x), round(topY), color.toArgb(), false);
    }
    @Override public void pushClip(UiRect clip) { applyClip(clips.push(clip)); }
    @Override public void popClip() {
        UiRect parent = clips.pop();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (parent != null) applyClip(parent);
    }
    @Override public void pushTransform() { graphics.pushPose(); }
    @Override public void popTransform() { graphics.popPose(); }
    @Override public void translate(double x, double y) {
        com.rtsbuilding.rtsbuilding.platform.render.GlStateManager.translate(x, y, 0.0D);
    }
    @Override public void scale(double x, double y) { graphics.scale((float) x, (float) y, 1.0F); }

    private void applyClip(UiRect clip) {
        int x = round(clip.getX());
        int y = round(clip.getY());
        int width = Math.max(0, round(clip.getWidth()));
        int height = Math.max(0, round(clip.getHeight()));
        if (this.screen != null) {
            this.screen.enableRtsScissor(
                    this.graphics, x, y, x + width, y + height);
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int factor = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * factor, minecraft.displayHeight - (y + height) * factor,
                width * factor, height * factor);
    }

    private static int round(double value) { return (int) Math.round(value); }
}
