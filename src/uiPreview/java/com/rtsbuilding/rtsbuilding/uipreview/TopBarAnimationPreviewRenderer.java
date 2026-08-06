package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiStateBlendAnimationSet;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * 输出顶部栏互斥模式切换的固定时间片。
 *
 * <p>这里直接读取正式 inactive/active 纹理，并复用生产的有界状态混合器。
 * 时间片只叠加纹理本身，不额外绘制矩形选框。</p>
 */
final class TopBarAnimationPreviewRenderer {
    static final int SLICE_COUNT = 5;
    private static final long DURATION_MILLIS = 80L;

    static void render(File outputDirectory) throws IOException {
        UiMainlineAssets assets = new UiMainlineAssets();
        FixedUiClock clock = new FixedUiClock(0L);
        UiStateBlendAnimationSet<String, String> animations =
                new UiStateBlendAnimationSet<String, String>(
                        clock, Arrays.asList("interact", "link"),
                        Arrays.asList("inactive", "active"),
                        DURATION_MILLIS, UiEasing.EASE_IN_OUT_QUAD);
        animations.update("interact", "active", true);
        animations.update("link", "inactive", true);

        for (int slice = 0; slice < SLICE_COUNT; slice++) {
            animations.update("interact", "inactive", true);
            animations.update("link", "active", true);
            writeSlice(outputDirectory, assets, slice, animations);
            if (slice + 1 < SLICE_COUNT) {
                clock.advanceMillis(DURATION_MILLIS / (SLICE_COUNT - 1));
            }
        }
    }

    private static void writeSlice(File outputDirectory, UiMainlineAssets assets,
                                   int slice,
                                   UiStateBlendAnimationSet<String, String> animations)
            throws IOException {
        BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(112, 40);
        try {
            canvas.clear(new java.awt.Color(RtsMainlineTheme.WINDOW_BACKGROUND.toArgb(), true));
            drawButton(canvas, assets, animations, "interact", 12, "mode_interact");
            drawButton(canvas, assets, animations, "link", 68, "mode_link");
            File output = new File(outputDirectory,
                    String.format("animation_topbar_%03d.png", slice * 25));
            ImageIO.write(canvas.image(), "png", output);
            if (!output.isFile() || output.length() == 0L) {
                throw new IOException("Cannot write top-bar animation slice: " + output);
            }
        } finally {
            canvas.close();
        }
    }

    private static void drawButton(BufferedImageUiCanvas canvas, UiMainlineAssets assets,
                                   UiStateBlendAnimationSet<String, String> animations,
                                   String id, int x, String textureName) {
        UiRect bounds = new UiRect(x + 4, 8, 24, 24);
        canvas.image(assets.topBar(textureName, "inactive"), bounds,
                animations.weight(id, "inactive"));
        canvas.image(assets.topBar(textureName, "active"), bounds,
                animations.weight(id, "active"));
    }

    private TopBarAnimationPreviewRenderer() {
    }
}
