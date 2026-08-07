package com.rtsbuilding.rtsbuilding.client.screen.canvas;

import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiClipStack;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 将 NeoForge 26.1 的 {@link GuiGraphicsExtractor} 接到纯 2D Kit 画布。
 *
 * <p>这个适配器只提交矩形、无阴影文字、裁剪和二维矩阵变换；它不结束共享批次、
 * 不触碰世界渲染，也不改变 Extractor 的 {@code nextStratum()} 生命周期。窗口和
 * 控件可以因此复用 Kit 的同一套几何与主题，而 26.1 平台差异仍留在这一处。</p>
 */
public final class MinecraftUiCanvas implements UiCanvas2D {
    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final BuilderScreen screen;
    private final double opacity;
    private final UiClipStack clips = new UiClipStack();

    public MinecraftUiCanvas(GuiGraphicsExtractor graphics, Font font) {
        this(graphics, font, null, 1.0D);
    }

    public MinecraftUiCanvas(
            GuiGraphicsExtractor graphics, Font font, BuilderScreen screen) {
        this(graphics, font, screen, 1.0D);
    }

    /** 为正在进出场的窗口提供局部透明度；不修改共享渲染缓冲的全局状态。 */
    public MinecraftUiCanvas(
            GuiGraphicsExtractor graphics, Font font, BuilderScreen screen, double opacity) {
        if (graphics == null || font == null) {
            throw new IllegalArgumentException("graphics and font must not be null");
        }
        this.graphics = graphics;
        this.font = font;
        this.screen = screen;
        this.opacity = Math.max(0.0D, Math.min(1.0D, opacity));
    }

    @Override
    public void fill(UiRect rect, UiColor color) {
        fill(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), color);
    }

    @Override
    public void fill(double x, double y, double width, double height, UiColor color) {
        graphics.fill(round(x), round(y), round(x + width), round(y + height),
                withOpacity(color.toArgb()));
    }

    @Override
    public void text(String text, double x, double topY, UiColor color) {
        graphics.text(font, text == null ? "" : text, round(x), round(topY),
                withOpacity(color.toArgb()), false);
    }

    @Override
    public void pushClip(UiRect clip) {
        applyClip(clips.push(clip));
    }

    @Override
    public void popClip() {
        UiRect parent = clips.pop();
        graphics.disableScissor();
        if (parent != null) {
            applyClip(parent);
        }
    }

    @Override
    public void pushTransform() {
        graphics.pose().pushMatrix();
    }

    @Override
    public void popTransform() {
        graphics.pose().popMatrix();
    }

    @Override
    public void translate(double x, double y) {
        graphics.pose().translate((float) x, (float) y);
    }

    @Override
    public void scale(double x, double y) {
        graphics.pose().scale((float) x, (float) y);
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

    private int withOpacity(int color) {
        int sourceAlpha = color >>> 24 & 0xFF;
        int alpha = (int) Math.round(sourceAlpha * this.opacity);
        return alpha << 24 | color & RtsMainlineTheme.LEGACY_00FFFFFF.toArgb();
    }
}
