package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 离屏 UI 审图专用的 Minecraft 世界背景。
 *
 * <p>该类只决定审图时使用哪张静态世界截图以及如何等比例裁切，不参与客户端世界
 * 渲染，也不会进入正式模组资源。默认使用暖正午；Gradle 可通过
 * {@code -PuiPreviewWorldTime=sunset|night|noon} 切换。</p>
 */
enum UiPreviewWorldBackground {
    SUNSET("sunset.png", 0),
    NIGHT("night.png", 0),
    // 暖正午截图顶部包含 21 像素 Windows 标题栏，裁切后再参与等比例铺满。
    NOON("noon.png", 21);

    private static final String PROPERTY = "rts.ui.preview.worldTime";

    private final String fileName;
    private final int cropTop;
    private BufferedImage image;

    UiPreviewWorldBackground(String fileName, int cropTop) {
        this.fileName = fileName;
        this.cropTop = cropTop;
    }

    static UiPreviewWorldBackground selected() {
        String requested = System.getProperty(PROPERTY, "noon")
                .trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(requested);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown UI preview world time '"
                    + requested.toLowerCase(Locale.ROOT)
                    + "'; expected sunset, night or noon", error);
        }
    }

    /** 离屏回归启动时同时核对三张背景，避免非默认时段长期悄悄损坏。 */
    static void verifyAll(UiRect target) {
        String previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            if (selected() != NOON) {
                throw new IllegalStateException(
                        "unspecified UI preview world time must default to noon");
            }
            for (UiPreviewWorldBackground background : values()) {
                System.setProperty(PROPERTY, background.name().toLowerCase(Locale.ROOT));
                if (selected() != background) {
                    throw new IllegalStateException("preview world-time selector drifted: "
                            + background.name().toLowerCase(Locale.ROOT));
                }
                BufferedImage image = background.image();
                UiRect source = coverSource(
                        image.getWidth(), image.getHeight(), background.cropTop, target);
                double sourceAspect = source.getWidth() / source.getHeight();
                double targetAspect = target.getWidth() / target.getHeight();
                if (Math.abs(sourceAspect - targetAspect) > 0.000001D
                        || source.getX() < 0.0D
                        || source.getY() < background.cropTop
                        || source.right() > image.getWidth() + 0.000001D
                        || source.bottom() > image.getHeight() + 0.000001D) {
                    throw new IllegalStateException("distorted or escaped preview background crop: "
                            + background.name().toLowerCase(Locale.ROOT) + " -> " + source);
                }
            }
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
        }
    }

    void render(BufferedImageUiCanvas canvas, UiRect target) {
        BufferedImage background = image();
        canvas.imageRegion(background, coverSource(
                background.getWidth(), background.getHeight(), cropTop, target), target);
    }

    /** 计算 CSS cover 等价的源区域；始终保持原图宽高比，绝不拉伸世界画面。 */
    static UiRect coverSource(int sourceWidth, int sourceHeight, int cropTop, UiRect target) {
        if (sourceWidth <= 0 || sourceHeight <= cropTop
                || target.getWidth() <= 0.0D || target.getHeight() <= 0.0D) {
            throw new IllegalArgumentException("source and target dimensions must be positive");
        }
        double usableHeight = sourceHeight - cropTop;
        double targetAspect = target.getWidth() / target.getHeight();
        double sourceAspect = sourceWidth / usableHeight;
        if (sourceAspect > targetAspect) {
            double width = usableHeight * targetAspect;
            return new UiRect((sourceWidth - width) / 2.0D, cropTop, width, usableHeight);
        }
        double height = sourceWidth / targetAspect;
        return new UiRect(0.0D, cropTop + (usableHeight - height) / 2.0D,
                sourceWidth, height);
    }

    private BufferedImage image() {
        if (image != null) return image;
        String path = "/backgrounds/" + fileName;
        try (InputStream stream = UiPreviewWorldBackground.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("missing classpath resource " + path);
            }
            BufferedImage loaded = ImageIO.read(stream);
            if (loaded == null) throw new IOException("unsupported image format " + path);
            image = loaded;
            return loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load UI preview background " + path, error);
        }
    }

}
