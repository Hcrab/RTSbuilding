package com.rtsbuilding.rtsbuilding.uipreview;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证预览不会因宿主机字体不同而改变原版 1.12.2 的像素输出。 */
class LegacyMinecraftFontRendererTest {
    private static LegacyMinecraftFontRenderer font;

    @BeforeAll
    static void loadVanillaFont() {
        String path = System.getProperty(LegacyMinecraftFontRenderer.CLIENT_JAR_PROPERTY);
        assertTrue(path != null && !path.trim().isEmpty(),
                "Gradle must inject the resolved RFG 1.12.2 client.jar");
        font = LegacyMinecraftFontRenderer.fromClientJar(new File(path));
    }

    @Test
    void asciiWidthAndFormattingMatchCharacterAdvances() {
        assertEquals(font.charWidth('A') + font.charWidth('B') + font.charWidth('C'),
                font.stringWidth("ABC"));
        assertEquals(font.stringWidth("ABC") + 3, font.stringWidth("\u00a7lABC"));
        assertEquals("AB", font.trimStringToWidth("ABCD",
                font.charWidth('A') + font.charWidth('B')));
    }

    @Test
    void simplifiedChineseUsesVanillaUnicodePageAndHalfPixelAdvance() {
        int niWidth = font.charWidth('\u4f60');
        int haoWidth = font.charWidth('\u597d');
        assertTrue(niWidth > 0, "你 must come from unicode_page_4f.png");
        assertTrue(haoWidth > 0, "好 must come from unicode_page_59.png");
        assertEquals(niWidth + haoWidth, font.stringWidth("你好"));
        assertEquals("你", font.trimStringToWidth("你好", niWidth));
    }

    @Test
    void drawingTheSameMixedLanguageTextIsDeterministic() {
        BufferedImage first = render("ABC 你好");
        BufferedImage second = render("ABC 你好");
        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                assertEquals(first.getRGB(x, y), second.getRGB(x, y),
                        "pixel differs at " + x + ',' + y);
            }
        }
    }

    @Test
    void previewCanvasContainsNoSystemFontFallback() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/BufferedImageUiCanvas.java")),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("java.awt.Font"));
        assertFalse(source.contains("Microsoft YaHei"));
        assertFalse(source.contains("DIALOG_INPUT"));
        assertFalse(source.contains("graphics.drawString("));
    }

    private static BufferedImage render(String text) {
        BufferedImage image = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            font.drawString(graphics, text, 2.0D, 2.0D, Color.WHITE, 1.0D);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
