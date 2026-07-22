package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.performance.UiRenderStats;
import com.rtsbuilding.rtsbuilding.uikit.skin.UiNineSliceLayout;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Stack;

/**
 * 仅用于离屏预览 source set 的 BufferedImage 画布。
 *
 * <p>它不认识世界、方块、FBO 或 Minecraft 共享缓冲区。九宫格每次固定提交
 * 九个矩形，绝不根据目标面积继续平铺。</p>
 */
public final class BufferedImageUiCanvas implements AutoCloseable {
    private final BufferedImage image;
    private final Graphics2D graphics;
    private final double scale;
    private final int logicalWidth;
    private final int logicalHeight;
    private final UiRenderStats stats = new UiRenderStats();
    private final Stack<Shape> clipStack = new Stack<Shape>();
    private int maximumNineSliceQuads;

    public BufferedImageUiCanvas(int width, int height) {
        this(width, height, 1.0D);
    }

    public BufferedImageUiCanvas(int width, int height, double scale) {
        if (scale <= 0.0D || Double.isNaN(scale) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("scale must be finite and positive");
        }
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        this.scale = scale;
        this.logicalWidth = Math.max(1, (int) Math.round(width / scale));
        this.logicalHeight = Math.max(1, (int) Math.round(height / scale));
        graphics.scale(scale, scale);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setFont(new Font(Font.DIALOG_INPUT, Font.PLAIN, 10));
    }

    public BufferedImage image() {
        return image;
    }

    public int primitiveCount() {
        return (int) stats.snapshot().primitives;
    }

    public int maximumNineSliceQuads() {
        return maximumNineSliceQuads;
    }

    public UiRenderStats.Snapshot statsSnapshot() {
        return stats.snapshot();
    }

    public double scale() {
        return scale;
    }

    public void configureFont(String language) {
        String family = language != null && language.startsWith("zh")
                ? "Microsoft YaHei UI" : Font.DIALOG_INPUT;
        graphics.setFont(new Font(family, Font.PLAIN, 10));
    }

    public int textWidth(String text) {
        return graphics.getFontMetrics().stringWidth(text == null ? "" : text);
    }

    public String trimToWidth(String text, int maximumWidth) {
        if (text == null || maximumWidth <= 0) return "";
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maximumWidth) return text;
        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) + suffixWidth > maximumWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    public void recordLayoutRebuild() {
        stats.addLayoutRebuilds(1);
    }

    public void recordScannedItems(long count) {
        stats.addScannedItems(count);
    }

    public void recordSort() {
        stats.addSorts(1);
    }

    public void clear(Color color) {
        graphics.setColor(color);
        graphics.fillRect(0, 0, logicalWidth, logicalHeight);
        stats.addPrimitives(1);
    }

    public void fill(UiRect rect, Color color) {
        graphics.setColor(color);
        graphics.fillRect(round(rect.getX()), round(rect.getY()),
                round(rect.getWidth()), round(rect.getHeight()));
        stats.addPrimitives(1);
    }

    public void stroke(UiRect rect, Color color) {
        graphics.setColor(color);
        graphics.drawRect(round(rect.getX()), round(rect.getY()),
                Math.max(0, round(rect.getWidth()) - 1), Math.max(0, round(rect.getHeight()) - 1));
        stats.addPrimitives(1);
    }

    public void text(String text, double x, double baselineY, Color color) {
        graphics.setColor(color);
        graphics.drawString(text, round(x), round(baselineY));
        stats.addPrimitives(1);
    }

    public void centeredText(String text, double centerX, double baselineY, Color color) {
        text(text, centerX - textWidth(text) / 2.0D, baselineY, color);
    }

    public void horizontalLine(double x1, double x2, double y, Color color) {
        graphics.setColor(color);
        graphics.drawLine(round(x1), round(y), round(x2), round(y));
        stats.addPrimitives(1);
    }

    public void verticalLine(double x, double y1, double y2, Color color) {
        graphics.setColor(color);
        graphics.drawLine(round(x), round(y1), round(x), round(y2));
        stats.addPrimitives(1);
    }

    public void image(BufferedImage texture, UiRect target) {
        if (texture == null) return;
        graphics.drawImage(texture, round(target.getX()), round(target.getY()),
                round(target.right()), round(target.bottom()), 0, 0,
                texture.getWidth(), texture.getHeight(), null);
        stats.addPrimitives(1);
    }

    public void imageRegion(BufferedImage texture, UiRect source, UiRect target) {
        if (texture == null) return;
        graphics.drawImage(texture, round(target.getX()), round(target.getY()),
                round(target.right()), round(target.bottom()),
                round(source.getX()), round(source.getY()), round(source.right()), round(source.bottom()), null);
        stats.addPrimitives(1);
    }

    public void withFontSize(float size, Runnable draw) {
        Font old = graphics.getFont();
        graphics.setFont(old.deriveFont(size));
        try {
            draw.run();
        } finally {
            graphics.setFont(old);
        }
    }

    public void pushClip(UiRect clip) {
        clipStack.push(graphics.getClip());
        graphics.clipRect(round(clip.getX()), round(clip.getY()),
                round(clip.getWidth()), round(clip.getHeight()));
    }

    public void popClip() {
        if (clipStack.isEmpty()) {
            throw new IllegalStateException("clip stack underflow");
        }
        graphics.setClip(clipStack.pop());
    }

    /** 使用四角、四边和中心固定九块绘制面板。 */
    public void nineSlice(UiRect target, int border, Color edge, Color center) {
        int safeBorder = Math.max(1, border);
        List<UiNineSliceLayout.Slice> slices = UiNineSliceLayout.calculate(
                new UiRect(0, 0, safeBorder * 3.0D, safeBorder * 3.0D),
                target, safeBorder, safeBorder, safeBorder, safeBorder);
        for (UiNineSliceLayout.Slice slice : slices) {
            fill(slice.getTarget(), slice.getPart() == UiNineSliceLayout.Part.CENTER ? center : edge);
        }
        stats.addNineSliceQuads(slices.size());
        maximumNineSliceQuads = Math.max(maximumNineSliceQuads, slices.size());
    }

    @Override
    public void close() {
        if (!clipStack.isEmpty()) {
            throw new IllegalStateException("unbalanced clip stack");
        }
        graphics.dispose();
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
