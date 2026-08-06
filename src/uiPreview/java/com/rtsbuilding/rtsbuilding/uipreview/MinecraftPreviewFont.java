package com.rtsbuilding.rtsbuilding.uipreview;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 从 Minecraft 1.21.1 自带资源读取拉丁位图与 Unihex 中文字形。
 *
 * <p>本类只复现字体供应器的字形、宽度与九像素行高，不处理翻译、布局或 Modern UI。
 * 这样离屏预览不会再把 Java 等宽字体和 Minecraft 中文字体拼在同一行。</p>
 */
final class MinecraftPreviewFont {
    private static final int GLYPH_ROWS = 16;
    private static final int CJK_ADVANCE = 9;
    private static final int LINE_HEIGHT = 9;
    private final Map<Integer, Glyph> glyphs = new HashMap<Integer, Glyph>();
    private final Map<Long, BufferedImage> tintedImages = new HashMap<Long, BufferedImage>();

    MinecraftPreviewFont(Path unifontArchive, File clientJar) {
        loadAscii(clientJar);
        loadUnihex(unifontArchive);
    }

    boolean hasGlyph(int codePoint) {
        return glyphs.containsKey(codePoint);
    }

    int advance(int codePoint) {
        Glyph glyph = glyphs.get(codePoint);
        return glyph == null ? 0 : glyph.advance;
    }

    int lineHeight() {
        return LINE_HEIGHT;
    }

    void draw(Graphics2D graphics, int codePoint, double x, double topY, Color color) {
        Glyph glyph = glyphs.get(codePoint);
        if (glyph == null) return;
        long cacheKey = (long) codePoint << 32 | color.getRGB() & 0xFFFFFFFFL;
        BufferedImage image = tintedImages.get(cacheKey);
        if (image == null) {
            image = rasterize(glyph, color.getRGB());
            tintedImages.put(cacheKey, image);
        }
        graphics.drawImage(image,
                (int) Math.round(x), (int) Math.round(topY),
                glyph.drawWidth, glyph.drawHeight, null);
    }

    private void loadAscii(File clientJar) {
        if (clientJar == null || !clientJar.isFile()) return;
        try (ZipFile zip = new ZipFile(clientJar)) {
            ZipEntry entry = zip.getEntry("assets/minecraft/textures/font/ascii.png");
            if (entry == null) return;
            BufferedImage atlas = ImageIO.read(zip.getInputStream(entry));
            if (atlas == null) return;
            int cellWidth = atlas.getWidth() / 16;
            int cellHeight = atlas.getHeight() / 16;
            for (int codePoint = 32; codePoint <= 126; codePoint++) {
                int cell = codePoint;
                BufferedImage source = atlas.getSubimage(
                        cell % 16 * cellWidth, cell / 16 * cellHeight,
                        cellWidth, cellHeight);
                int visibleWidth = visibleWidth(source);
                int advance = codePoint == 32 ? 4 : Math.max(1, visibleWidth + 1);
                glyphs.put(codePoint, Glyph.bitmap(source, advance));
            }
        } catch (IOException | RuntimeException ignored) {
            // 本地缓存不完整时仍允许预览回退；验证任务会显式报告资源缺失。
        }
    }

    private void loadUnihex(Path archive) {
        if (archive == null || !Files.isRegularFile(archive)) return;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".hex")) continue;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zip, StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null) parseUnihexLine(line);
                break;
            }
        } catch (IOException | RuntimeException ignored) {
            // 拉丁位图仍可使用；缺少的字符由画布做最后回退。
        }
    }

    private void parseUnihexLine(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0) return;
        int codePoint = Integer.parseInt(line.substring(0, colon), 16);
        if (codePoint < 0x2E80 || codePoint > 0xFAFF) return;
        String bitmap = line.substring(colon + 1).trim();
        int pixelWidth = bitmap.length() / GLYPH_ROWS * 4;
        if (pixelWidth != 8 && pixelWidth != 16 && pixelWidth != 24 && pixelWidth != 32) return;
        int digitsPerRow = pixelWidth / 4;
        int[] rows = new int[GLYPH_ROWS];
        for (int row = 0; row < GLYPH_ROWS; row++) {
            rows[row] = (int) Long.parseLong(
                    bitmap.substring(row * digitsPerRow, (row + 1) * digitsPerRow), 16);
        }
        glyphs.put(codePoint, Glyph.unihex(pixelWidth, rows, CJK_ADVANCE));
    }

    private static int visibleWidth(BufferedImage image) {
        int right = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) right = Math.max(right, x);
            }
        }
        return right + 1;
    }

    private static BufferedImage rasterize(Glyph glyph, int argb) {
        BufferedImage image = new BufferedImage(
                glyph.sourceWidth, glyph.sourceHeight, BufferedImage.TYPE_INT_ARGB);
        if (glyph.bitmap != null) {
            for (int y = 0; y < glyph.sourceHeight; y++) {
                for (int x = 0; x < glyph.sourceWidth; x++) {
                    if ((glyph.bitmap.getRGB(x, y) >>> 24) != 0) image.setRGB(x, y, argb);
                }
            }
            return image;
        }
        for (int row = 0; row < GLYPH_ROWS; row++) {
            for (int column = 0; column < glyph.sourceWidth; column++) {
                int bit = glyph.sourceWidth - column - 1;
                if ((glyph.rows[row] >>> bit & 1) != 0) image.setRGB(column, row, argb);
            }
        }
        return image;
    }

    private static final class Glyph {
        private final BufferedImage bitmap;
        private final int[] rows;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int drawWidth;
        private final int drawHeight;
        private final int advance;

        private Glyph(BufferedImage bitmap, int[] rows, int sourceWidth, int sourceHeight,
                      int drawWidth, int drawHeight, int advance) {
            this.bitmap = bitmap;
            this.rows = rows;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.drawWidth = drawWidth;
            this.drawHeight = drawHeight;
            this.advance = advance;
        }

        private static Glyph bitmap(BufferedImage image, int advance) {
            return new Glyph(image, null, image.getWidth(), image.getHeight(),
                    image.getWidth(), image.getHeight(), advance);
        }

        private static Glyph unihex(int pixelWidth, int[] rows, int advance) {
            return new Glyph(null, rows, pixelWidth, GLYPH_ROWS,
                    pixelWidth / 2, GLYPH_ROWS / 2, advance);
        }
    }
}
