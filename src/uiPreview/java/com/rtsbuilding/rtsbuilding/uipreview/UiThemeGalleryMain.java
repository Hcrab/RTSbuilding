package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeBuiltins;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 用同一个完整 RTS 场景离屏渲染五套内建主题，并额外生成便于审阅的联系表。
 *
 * <p>该入口只用于开发报告，不创建 Minecraft 客户端或桌面窗口，也不会修改玩家配置。</p>
 */
public final class UiThemeGalleryMain {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int CELL_WIDTH = 640;
    private static final int CELL_HEIGHT = 360;
    private static final int LABEL_HEIGHT = 32;

    private static final ThemeEntry[] THEMES = new ThemeEntry[] {
            new ThemeEntry(UiThemeBuiltins.LEGACY_ID, "01_legacy", "Legacy / Resource Pack"),
            new ThemeEntry(UiThemeBuiltins.CALIBRATED_ID, "02_calibrated_dark", "Calibrated RTS Dark"),
            new ThemeEntry(UiThemeBuiltins.NORD_ID, "03_nord_command", "Nord Command"),
            new ThemeEntry(UiThemeBuiltins.CARBON_ID, "04_carbon_operations", "Carbon Operations"),
            new ThemeEntry(UiThemeBuiltins.MATERIAL_ID, "05_material_field", "Material Field")
    };

    private UiThemeGalleryMain() {
    }

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        File output = UiPreviewMain.outputDirectory(args);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Cannot create theme gallery directory: " + output);
        }
        UiPreviewMain.cleanGeneratedImages(output);
        UiPreviewRenderer renderer = new UiPreviewRenderer();
        UiPreviewScenario scenario = new UiPreviewScenario(
                "theme_gallery", WIDTH, HEIGHT, 1.0D, "zh_cn", true,
                UiPreviewScenario.Variant.QUICK_BUILD_STATES,
                300, 22, "theme comparison", false);
        BufferedImage[] rendered = new BufferedImage[THEMES.length];
        try {
            for (int i = 0; i < THEMES.length; i++) {
                ThemeEntry entry = THEMES[i];
                UiThemeRuntime.manager().activate(entry.id);
                UiPreviewResult result = renderer.render(scenario);
                try {
                    rendered[i] = copy(result.image());
                    ImageIO.write(rendered[i], "png", new File(output, entry.fileName + ".png"));
                } finally {
                    result.close();
                }
            }
            ImageIO.write(contactSheet(rendered), "png",
                    new File(output, "00_five_theme_comparison.png"));
        } finally {
            UiThemeRuntime.manager().fallBackToLegacy();
        }
        System.out.println("Rendered five headless theme previews to " + output.getAbsolutePath());
    }

    private static BufferedImage contactSheet(BufferedImage[] images) {
        BufferedImage sheet = new BufferedImage(CELL_WIDTH * 3,
                2 * (CELL_HEIGHT + LABEL_HEIGHT), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(0xFF090B0E, true));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            graphics.setFont(new Font(Font.DIALOG_INPUT, Font.BOLD, 18));
            for (int i = 0; i < images.length; i++) {
                int x = i < 3 ? i * CELL_WIDTH : CELL_WIDTH / 2 + (i - 3) * CELL_WIDTH;
                int y = i < 3 ? 0 : CELL_HEIGHT + LABEL_HEIGHT;
                graphics.setColor(new Color(0xFF10151C, true));
                graphics.fillRect(x, y, CELL_WIDTH, LABEL_HEIGHT);
                graphics.setColor(new Color(0xFFEAF2FF, true));
                graphics.drawString(THEMES[i].label, x + 12, y + 22);
                graphics.drawImage(images[i], x, y + LABEL_HEIGHT,
                        CELL_WIDTH, CELL_HEIGHT, null);
            }
        } finally {
            graphics.dispose();
        }
        return sheet;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static final class ThemeEntry {
        final String id;
        final String fileName;
        final String label;

        ThemeEntry(String id, String fileName, String label) {
            this.id = id;
            this.fileName = fileName;
            this.label = label;
        }
    }
}
