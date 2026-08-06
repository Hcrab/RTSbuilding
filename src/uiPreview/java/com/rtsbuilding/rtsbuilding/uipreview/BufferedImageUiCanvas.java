package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.performance.UiRenderStats;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Stack;
import java.nio.file.Paths;
import java.io.File;

/**
 * 仅用于离屏预览 source set 的 BufferedImage 画布。
 *
 * <p>它不认识世界、方块、FBO 或 Minecraft 共享缓冲区。九宫格每次固定提交
 * 九个矩形，绝不根据目标面积继续平铺。</p>
 */
public final class BufferedImageUiCanvas implements UiCanvas2D, AutoCloseable {
    private final BufferedImage image;
    private final Graphics2D graphics;
    private final double scale;
    private final int logicalWidth;
    private final int logicalHeight;
    private final UiRenderStats stats = new UiRenderStats();
    private final Stack<Shape> clipStack = new Stack<Shape>();
    private final Stack<AffineTransform> transformStack = new Stack<AffineTransform>();
    private int maximumNineSliceQuads;
    private UiPreviewFontMode fontMode = UiPreviewFontMode.configured();
    private MinecraftPreviewFont minecraftFont;

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
        configureFont(language, UiPreviewFontMode.configured());
    }

    void configureFont(String language, UiPreviewFontMode mode) {
        this.fontMode = mode == null ? UiPreviewFontMode.MODERN_UI : mode;
        boolean chinese = language != null && language.startsWith("zh");
        String family = this.fontMode == UiPreviewFontMode.MODERN_UI
                ? (chinese ? "Microsoft YaHei UI" : "Segoe UI")
                : Font.DIALOG_INPUT;
        graphics.setFont(new Font(family, Font.PLAIN,
                this.fontMode == UiPreviewFontMode.MODERN_UI ? 9 : 10));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                this.fontMode == UiPreviewFontMode.MODERN_UI
                        ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                        : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                this.fontMode == UiPreviewFontMode.MODERN_UI
                        ? RenderingHints.VALUE_FRACTIONALMETRICS_ON
                        : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        if (this.fontMode == UiPreviewFontMode.MINECRAFT && this.minecraftFont == null) {
            String archive = System.getProperty("rts.ui.preview.unifontZip", "");
            String clientJar = System.getProperty("rts.ui.preview.minecraftClientJar", "");
            this.minecraftFont = new MinecraftPreviewFont(
                    archive.isEmpty() ? null : Paths.get(archive),
                    clientJar.isEmpty() ? null : new File(clientJar));
        }
    }

    public int lineHeight() {
        return this.fontMode == UiPreviewFontMode.MINECRAFT && this.minecraftFont != null
                ? this.minecraftFont.lineHeight() : 9;
    }

    public int textWidth(String text) {
        String safe = text == null ? "" : text;
        if (this.fontMode != UiPreviewFontMode.MINECRAFT || this.minecraftFont == null) {
            return (int) Math.ceil(graphics.getFontMetrics().getStringBounds(
                    safe, graphics).getWidth());
        }
        int width = 0;
        for (int offset = 0; offset < safe.length();) {
            int codePoint = safe.codePointAt(offset);
            width += this.minecraftFont.hasGlyph(codePoint)
                    ? this.minecraftFont.advance(codePoint)
                    : graphics.getFontMetrics().stringWidth(
                            new String(Character.toChars(codePoint)));
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    public String trimToWidth(String text, int maximumWidth) {
        if (text == null || maximumWidth <= 0) return "";
        if (textWidth(text) <= maximumWidth) return text;
        String suffix = "...";
        int suffixWidth = textWidth(suffix);
        int end = 0;
        int width = 0;
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            int next = end + Character.charCount(codePoint);
            int glyphWidth = textWidth(text.substring(end, next));
            if (width + glyphWidth + suffixWidth > maximumWidth) break;
            width += glyphWidth;
            end = next;
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

    @Override
    public void fill(UiRect rect, UiColor color) {
        fill(rect, new Color(color.toArgb(), true));
    }

    @Override
    public void fill(double x, double y, double width, double height, UiColor color) {
        graphics.setColor(new Color(color.toArgb(), true));
        graphics.fillRect(round(x), round(y), round(width), round(height));
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
        if (this.fontMode == UiPreviewFontMode.MINECRAFT && this.minecraftFont != null) {
            drawMinecraftText(text == null ? "" : text, x,
                    baselineY - graphics.getFontMetrics().getAscent(), color);
        } else {
            graphics.drawString(text, round(x), round(baselineY));
        }
        stats.addPrimitives(1);
    }

    @Override
    public void text(String text, double x, double topY, UiColor color) {
        text(text == null ? "" : text, x,
                topY + graphics.getFontMetrics().getAscent(), new Color(color.toArgb(), true));
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

    /** 以固定透明度叠加图片，供离屏验证状态纹理交叉淡入。 */
    public void image(BufferedImage texture, UiRect target, double opacity) {
        if (texture == null || opacity <= 0.0D) return;
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                (float) Math.max(0.0D, Math.min(1.0D, opacity))));
        try {
            graphics.drawImage(texture, round(target.getX()), round(target.getY()),
                    round(target.right()), round(target.bottom()), 0, 0,
                    texture.getWidth(), texture.getHeight(), null);
            stats.addPrimitives(1);
        } finally {
            graphics.setComposite(previous);
        }
    }

    public void imageRegion(BufferedImage texture, UiRect source, UiRect target) {
        if (texture == null) return;
        graphics.drawImage(texture, round(target.getX()), round(target.getY()),
                round(target.right()), round(target.bottom()),
                round(source.getX()), round(source.getY()), round(source.right()), round(source.bottom()), null);
        stats.addPrimitives(1);
    }

    public void imageRegion(
            BufferedImage texture, UiRect source, UiRect target, double opacity) {
        if (texture == null || opacity <= 0.0D) return;
        Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                (float) Math.max(0.0D, Math.min(1.0D, opacity))));
        try {
            imageRegion(texture, source, target);
        } finally {
            graphics.setComposite(previous);
        }
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

    @Override
    public void pushClip(UiRect clip) {
        clipStack.push(graphics.getClip());
        graphics.clipRect(round(clip.getX()), round(clip.getY()),
                round(clip.getWidth()), round(clip.getHeight()));
    }

    @Override
    public void popClip() {
        if (clipStack.isEmpty()) {
            throw new IllegalStateException("clip stack underflow");
        }
        graphics.setClip(clipStack.pop());
    }

    @Override
    public void pushTransform() {
        transformStack.push(graphics.getTransform());
    }

    @Override
    public void popTransform() {
        if (transformStack.isEmpty()) {
            throw new IllegalStateException("transform stack underflow");
        }
        graphics.setTransform(transformStack.pop());
    }

    @Override
    public void translate(double x, double y) {
        graphics.translate(x, y);
    }

    @Override
    public void scale(double x, double y) {
        graphics.scale(x, y);
    }

    void recordNineSliceQuads(int count) {
        stats.addNineSliceQuads(count);
        maximumNineSliceQuads = Math.max(maximumNineSliceQuads, count);
    }

    @Override
    public void close() {
        if (!clipStack.isEmpty()) {
            throw new IllegalStateException("unbalanced clip stack");
        }
        if (!transformStack.isEmpty()) {
            throw new IllegalStateException("unbalanced transform stack");
        }
        graphics.dispose();
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private void drawMinecraftText(String text, double x, double topY, Color color) {
        double cursor = x;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String glyphText = new String(Character.toChars(codePoint));
            if (this.minecraftFont.hasGlyph(codePoint)) {
                this.minecraftFont.draw(graphics, codePoint, cursor, topY, color);
                cursor += this.minecraftFont.advance(codePoint);
            } else {
                graphics.drawString(glyphText, round(cursor),
                        round(topY + graphics.getFontMetrics().getAscent()));
                cursor += graphics.getFontMetrics().stringWidth(glyphText);
            }
            offset += Character.charCount(codePoint);
        }
    }
}
