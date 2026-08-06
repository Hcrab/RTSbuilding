package com.rtsbuilding.rtsbuilding.uipreview;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 离屏预览使用的 1.12.2 原版位图字体。
 *
 * <p>这个类只读取 RFG 已解析的 Minecraft client.jar 内的字体资源，不依赖操作系统安装的
 * 字体，也不接触 Minecraft 客户端类。它复刻 1.12.2 {@code FontRenderer} 的默认字库映射、
 * Unicode 半像素宽度、字宽和格式控制码规则，供普通 JVM 的预览和像素契约测试共用。</p>
 */
final class LegacyMinecraftFontRenderer {
    static final String CLIENT_JAR_PROPERTY = "rts.uipreview.minecraftClientJar";

    private static final String ASCII_FONT_PATH =
            "assets/minecraft/textures/font/ascii.png";
    private static final String GLYPH_SIZES_PATH =
            "assets/minecraft/font/glyph_sizes.bin";
    private static final String UNICODE_PAGE_PATH =
            "assets/minecraft/textures/font/unicode_page_%02x.png";
    private static final int FONT_HEIGHT = 9;

    /* 与 1.12.2 FontRenderer 中的默认字符表一致。 */
    private static final String DEFAULT_FONT =
            "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130"
                    + "\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
                    + " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
                    + "abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7"
                    + "\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb"
                    + "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";

    private final File clientJar;
    private final BufferedImage asciiFont;
    private final int[] charWidth = new int[256];
    private final byte[] glyphWidth;
    private final Map<Integer, BufferedImage> unicodePages = new HashMap<Integer, BufferedImage>();
    private final Map<GlyphKey, BufferedImage> tintedGlyphs =
            new HashMap<GlyphKey, BufferedImage>();

    private LegacyMinecraftFontRenderer(File clientJar, BufferedImage asciiFont, byte[] glyphWidth) {
        this.clientJar = clientJar;
        this.asciiFont = asciiFont;
        this.glyphWidth = glyphWidth;
        readAsciiWidths();
    }

    static LegacyMinecraftFontRenderer fromConfiguredClientJar() {
        String configured = System.getProperty(CLIENT_JAR_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("Headless UI preview requires the 1.12.2 client.jar. "
                    + "Run the Gradle UI preview task so it can inject -D" + CLIENT_JAR_PROPERTY + ".");
        }
        return fromClientJar(new File(configured));
    }

    static LegacyMinecraftFontRenderer fromClientJar(File clientJar) {
        if (clientJar == null || !clientJar.isFile()) {
            throw new IllegalStateException("Configured 1.12.2 client.jar does not exist: "
                    + String.valueOf(clientJar));
        }
        try {
            ZipFile archive = new ZipFile(clientJar);
            try {
                BufferedImage ascii = readImage(archive, ASCII_FONT_PATH, clientJar);
                byte[] widths = readBytes(archive, GLYPH_SIZES_PATH, clientJar);
                if (widths.length != Character.MAX_VALUE + 1) {
                    throw new IllegalStateException("Invalid 1.12.2 glyph_sizes.bin in " + clientJar
                            + ": expected 65536 bytes, found " + widths.length);
                }
                return new LegacyMinecraftFontRenderer(clientJar, ascii, widths);
            } finally {
                archive.close();
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read 1.12.2 font resources from " + clientJar, error);
        }
    }

    int fontHeight() {
        return FONT_HEIGHT;
    }

    int stringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        boolean bold = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            int characterWidth = charWidth(character);
            if (characterWidth < 0 && index < text.length() - 1) {
                char format = text.charAt(++index);
                if (isColorFormat(format) || isResetFormat(format)) {
                    bold = false;
                } else if (isBoldFormat(format)) {
                    bold = true;
                }
                continue;
            }
            width += characterWidth;
            if (bold && characterWidth > 0) width++;
        }
        return width;
    }

    int charWidth(char character) {
        if (character == 160 || character == ' ') return 4;
        if (character == 167) return -1;

        int defaultIndex = DEFAULT_FONT.indexOf(character);
        if (character > 0 && defaultIndex >= 0) return charWidth[defaultIndex];

        int packed = glyphWidth[character] & 255;
        if (packed == 0) return 0;
        int start = packed >>> 4;
        int end = (packed & 15) + 1;
        return (end - start) / 2 + 1;
    }

    String trimStringToWidth(String text, int maximumWidth) {
        if (text == null || text.isEmpty() || maximumWidth <= 0) return "";
        StringBuilder trimmed = new StringBuilder();
        int width = 0;
        boolean formatting = false;
        boolean bold = false;
        for (int index = 0; index < text.length() && width < maximumWidth; index++) {
            char character = text.charAt(index);
            int characterWidth = charWidth(character);
            if (formatting) {
                formatting = false;
                if (isColorFormat(character) || isResetFormat(character)) {
                    bold = false;
                } else if (isBoldFormat(character)) {
                    bold = true;
                }
            } else if (characterWidth < 0) {
                formatting = true;
            } else {
                width += characterWidth;
                if (bold) width++;
            }
            if (width > maximumWidth) break;
            trimmed.append(character);
        }
        return trimmed.toString();
    }

    /**
     * 以 Minecraft 的左上角文本坐标绘制。调用者通过 {@code scale} 保留原有 canvas 的字号语义。
     */
    void drawString(Graphics2D graphics, String text, double x, double y, Color baseColor,
                    double scale) {
        if (text == null || text.isEmpty() || scale <= 0.0D) return;

        AffineTransform previous = graphics.getTransform();
        graphics.translate(x, y);
        graphics.scale(scale, scale);
        try {
            drawBaseString(graphics, text, baseColor == null ? Color.WHITE : baseColor);
        } finally {
            graphics.setTransform(previous);
        }
    }

    private void drawBaseString(Graphics2D graphics, String text, Color baseColor) {
        Color activeColor = baseColor;
        boolean bold = false;
        double x = 0.0D;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == 167 && index < text.length() - 1) {
                char format = Character.toLowerCase(text.charAt(++index));
                if (isColorFormat(format)) {
                    activeColor = colorForFormat(format, baseColor.getAlpha());
                    bold = false;
                } else if (isResetFormat(format)) {
                    activeColor = baseColor;
                    bold = false;
                } else if (isBoldFormat(format)) {
                    bold = true;
                }
                continue;
            }

            int advance = charWidth(character);
            if (advance <= 0) continue;
            drawCharacter(graphics, character, x, 0.0D, activeColor);
            if (bold) drawCharacter(graphics, character, x + 1.0D, 0.0D, activeColor);
            x += advance + (bold ? 1.0D : 0.0D);
        }
    }

    private void drawCharacter(Graphics2D graphics, char character, double x, double y, Color color) {
        if (character == ' ' || character == 160) return;
        int defaultIndex = DEFAULT_FONT.indexOf(character);
        if (character > 0 && defaultIndex >= 0) {
            int width = Math.min(8, Math.max(1, charWidth[defaultIndex] - 1));
            BufferedImage glyph = tintedGlyph(new GlyphKey(defaultIndex, 0, 0, width,
                    8, color.getRGB()), asciiFont, defaultIndex % 16 * 8,
                    defaultIndex / 16 * 8, width, 8, color);
            drawScaled(graphics, glyph, x, y, (width - 0.01D) / width, 7.99D / 8.0D);
            return;
        }

        int packed = glyphWidth[character] & 255;
        if (packed == 0) return;
        int start = packed >>> 4;
        int end = (packed & 15) + 1;
        int sourceWidth = end - start;
        int page = character / 256;
        BufferedImage source = unicodePage(page);
        int sourceX = character % 16 * 16 + start;
        int sourceY = (character & 255) / 16 * 16;
        BufferedImage glyph = tintedGlyph(new GlyphKey(character, page, start, sourceWidth,
                16, color.getRGB()), source, sourceX, sourceY, sourceWidth, 16, color);
        // 原版 Unicode glyph 将 16px 字格压到约 8px；这就是影响 CJK 宽度的半像素规则。
        drawScaled(graphics, glyph, x, y,
                (sourceWidth - 0.02D) / (sourceWidth * 2.0D), 7.99D / 16.0D);
    }

    private BufferedImage unicodePage(int page) {
        Integer key = Integer.valueOf(page);
        BufferedImage loaded = unicodePages.get(key);
        if (loaded != null) return loaded;
        String path = String.format(UNICODE_PAGE_PATH, key);
        try {
            ZipFile archive = new ZipFile(clientJar);
            try {
                loaded = readImage(archive, path, clientJar);
                unicodePages.put(key, loaded);
                return loaded;
            } finally {
                archive.close();
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read 1.12.2 Unicode font page " + path
                    + " from " + clientJar, error);
        }
    }

    private BufferedImage tintedGlyph(GlyphKey key, BufferedImage source, int sourceX, int sourceY,
                                       int width, int height, Color color) {
        BufferedImage cached = tintedGlyphs.get(key);
        if (cached != null) return cached;
        BufferedImage tinted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int opacity = color.getAlpha();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sourceAlpha = source.getRGB(sourceX + x, sourceY + y) >>> 24;
                if (sourceAlpha == 0) continue;
                int alpha = sourceAlpha * opacity / 255;
                tinted.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        tintedGlyphs.put(key, tinted);
        return tinted;
    }

    private static void drawScaled(Graphics2D graphics, BufferedImage glyph, double x, double y,
                                   double scaleX, double scaleY) {
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.scale(scaleX, scaleY);
        Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        try {
            graphics.drawImage(glyph, transform, null);
        } finally {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        }
    }

    private void readAsciiWidths() {
        int cellWidth = asciiFont.getWidth() / 16;
        int cellHeight = asciiFont.getHeight() / 16;
        float widthScale = 8.0F / (float) cellWidth;
        for (int character = 0; character < 256; character++) {
            int column = character % 16;
            int row = character / 16;
            if (character == 32) charWidth[character] = 4;
            int right = cellWidth - 1;
            for (; right >= 0; right--) {
                boolean empty = true;
                for (int y = 0; y < cellHeight && empty; y++) {
                    if ((asciiFont.getRGB(column * cellWidth + right, row * cellHeight + y)
                            >>> 24) != 0) {
                        empty = false;
                    }
                }
                if (!empty) break;
            }
            right++;
            charWidth[character] = (int) (0.5D + right * widthScale) + 1;
        }
    }

    private static BufferedImage readImage(ZipFile archive, String path, File clientJar)
            throws IOException {
        ZipEntry entry = archive.getEntry(path);
        if (entry == null) {
            throw new IllegalStateException("Missing required 1.12.2 font resource " + path
                    + " in " + clientJar);
        }
        InputStream input = archive.getInputStream(entry);
        try {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("Unreadable 1.12.2 font image " + path
                        + " in " + clientJar);
            }
            return image;
        } finally {
            input.close();
        }
    }

    private static byte[] readBytes(ZipFile archive, String path, File clientJar) throws IOException {
        ZipEntry entry = archive.getEntry(path);
        if (entry == null) {
            throw new IllegalStateException("Missing required 1.12.2 font resource " + path
                    + " in " + clientJar);
        }
        InputStream input = archive.getInputStream(entry);
        try {
            byte[] bytes = new byte[Character.MAX_VALUE + 1];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset == bytes.length && input.read() == -1) return bytes;
            byte[] actual = new byte[offset];
            System.arraycopy(bytes, 0, actual, 0, offset);
            return actual;
        } finally {
            input.close();
        }
    }

    private static boolean isColorFormat(char format) {
        return Character.digit(Character.toLowerCase(format), 16) >= 0;
    }

    private static boolean isBoldFormat(char format) {
        char normalized = Character.toLowerCase(format);
        return normalized == 'l';
    }

    private static boolean isResetFormat(char format) {
        char normalized = Character.toLowerCase(format);
        return normalized == 'r';
    }

    private static Color colorForFormat(char format, int alpha) {
        int index = Character.digit(Character.toLowerCase(format), 16);
        int shadow = index >= 16 ? 1 : 0;
        int brightness = (index >> 3 & 1) * 85;
        int red = (index >> 2 & 1) * 170 + brightness;
        int green = (index >> 1 & 1) * 170 + brightness;
        int blue = (index & 1) * 170 + brightness;
        if (index == 6) red += 85;
        return new Color(red, green, blue, alpha);
    }

    private static final class GlyphKey {
        private final int glyph;
        private final int page;
        private final int start;
        private final int width;
        private final int height;
        private final int color;

        private GlyphKey(int glyph, int page, int start, int width, int height, int color) {
            this.glyph = glyph;
            this.page = page;
            this.start = start;
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GlyphKey)) return false;
            GlyphKey key = (GlyphKey) other;
            return glyph == key.glyph && page == key.page && start == key.start
                    && width == key.width && height == key.height && color == key.color;
        }

        @Override
        public int hashCode() {
            int result = glyph;
            result = 31 * result + page;
            result = 31 * result + start;
            result = 31 * result + width;
            result = 31 * result + height;
            return 31 * result + color;
        }
    }
}
