package com.rtsbuilding.rtsbuilding.client.screen.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiClipStack;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;

/**
 * {@link RtsGuiContext} 到纯 2D Kit 画布的生产适配器。
 *
 * <p>它只转发当前 GUI 批次中的矩形、无阴影文本、裁剪与 pose 变换；不调用
 * {@code flush/endBatch}，不接触世界渲染，也不拥有 Minecraft 生命周期。</p>
 */
public final class MinecraftUiCanvas implements UiCanvas2D {
    private final RtsGuiContext graphics;
    private final Font font;
    private final BuilderScreen screen;
    private final UiClipStack clips = new UiClipStack();

    public MinecraftUiCanvas(RtsGuiContext graphics, Font font) {
        this(graphics, font, null);
    }

    public MinecraftUiCanvas(RtsGuiContext graphics, Font font, BuilderScreen screen) {
        if (graphics == null || font == null) {
            throw new IllegalArgumentException("graphics and font must not be null");
        }
        this.graphics = graphics;
        this.font = font;
        this.screen = screen;
    }

    @Override
    public void fill(UiRect rect, UiColor color) {
        fill(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), color);
    }

    @Override
    public void fill(double x, double y, double width, double height, UiColor color) {
        graphics.fill(round(x), round(y), round(x + width), round(y + height), color.toArgb());
    }

    @Override
    public void text(String text, double x, double topY, UiColor color) {
        graphics.drawString(font, text == null ? "" : text,
                round(x), round(topY), color.toArgb(), false);
    }

    @Override
    public void pushClip(UiRect clip) {
        applyClip(clips.push(clip));
    }

    @Override
    public void popClip() {
        UiRect parent = clips.pop();
        graphics.disableScissor();
        if (parent != null) applyClip(parent);
    }

    @Override
    public void pushTransform() {
        graphics.pose().pushPose();
    }

    @Override
    public void popTransform() {
        graphics.pose().popPose();
    }

    @Override
    public void translate(double x, double y) {
        graphics.pose().translate((float) x, (float) y, 0.0F);
    }

    @Override
    public void scale(double x, double y) {
        graphics.pose().scale((float) x, (float) y, 1.0F);
    }

    private void applyClip(UiRect clip) {
        int x1 = round(clip.getX());
        int y1 = round(clip.getY());
        int x2 = round(clip.right());
        int y2 = round(clip.bottom());
        if (screen != null) {
            screen.enableRtsScissor(graphics, x1, y1, x2, y2);
        } else {
            graphics.enableScissor(x1, y1, x2, y2);
        }
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
