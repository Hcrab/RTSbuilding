package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowButtonChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.File;
import java.io.IOException;

/**
 * Headless time slices for Quick Build's right-column toggle handoff.
 *
 * <p>It consumes the production animation state, chrome renderer and geometry only; it
 * neither simulates gameplay/input nor creates a Minecraft client. This keeps the screenshot
 * contract aligned with the player-visible rule: state changes immediately while color eases
 * within the unchanged hit rectangle.</p>
 *
 * <p>只消费生产端的 {@link UiControlAnimationState}、{@link WindowButtonChromeRenderer}
 * 和 {@link QuickBuildWindowLayout}；不模拟玩法、输入或 Minecraft 客户端。这样截图能直接
 * 审核“状态立即切换、颜色短暂过渡、命中矩形不变”这一玩家可见约定。</p>
 */
final class QuickBuildControlAnimationPreviewRenderer {
    static final int SLICE_COUNT = 5;

    private QuickBuildControlAnimationPreviewRenderer() {
    }

    static void render(File outputDirectory) throws IOException {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState selecting = new UiControlAnimationState(clock);
        UiControlAnimationState clearing = new UiControlAnimationState(clock);
        selecting.update(state(false), true);
        clearing.update(state(true), true);

        for (int slice = 0; slice < SLICE_COUNT; slice++) {
            UiControlAnimationState.Snapshot left = selecting.update(state(true), true);
            UiControlAnimationState.Snapshot right = clearing.update(state(false), true);
            writeSlice(outputDirectory, slice, left, right);
            if (slice + 1 < SLICE_COUNT) {
                clock.advanceMillis(UiControlAnimationState.SELECTION_DURATION_MS
                        / (SLICE_COUNT - 1));
            }
        }
    }

    private static UiControlState state(boolean selected) {
        return new UiControlState(true, true, false, false, false,
                selected, false, false, "");
    }

    private static void writeSlice(
            File outputDirectory,
            int slice,
            UiControlAnimationState.Snapshot left,
            UiControlAnimationState.Snapshot right) throws IOException {
        BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(480, 96, 2.0D);
        try {
            canvas.clear(new Color(RtsMainlineTheme.WINDOW_BACKGROUND.toArgb(), true));
            drawControl(canvas, 12, 12, "Fill", left);
            drawControl(canvas, 116, 12, "Hollow", right);
            File output = new File(outputDirectory,
                    String.format("animation_quick_build_switch_%03d.png", slice * 25));
            ImageIO.write(canvas.image(), "png", output);
            if (!output.isFile() || output.length() == 0L) {
                throw new IOException("Cannot write Quick Build animation slice: " + output);
            }
        } finally {
            canvas.close();
        }
    }

    private static void drawControl(
            BufferedImageUiCanvas canvas,
            int x,
            int y,
            String label,
            UiControlAnimationState.Snapshot animation) {
        UiRect bounds = new UiRect(x, y,
                QuickBuildWindowLayout.CONTROL_W, QuickBuildWindowLayout.CONTROL_H);
        UiControlVisualStyle visual = UiControlVisualStyle.animated(
                UiControlRole.TOGGLE, animation);
        WindowButtonChromeRenderer.renderSolid(canvas, bounds, visual);

        QuickBuildStyle.ControlIndicatorVisual indicator =
                QuickBuildStyle.animatedControlIndicator(animation);
        int indicatorX = x + QuickBuildWindowLayout.CONTROL_ICON_INSET;
        int indicatorY = y + QuickBuildWindowLayout.CONTROL_ICON_INSET;
        int indicatorSize = QuickBuildWindowLayout.CONTROL_ICON_SIZE;
        canvas.fill(indicatorX, indicatorY, indicatorSize, indicatorSize, indicator.darkEdge);
        canvas.fill(indicatorX + 1, indicatorY + 1, indicatorSize - 2, indicatorSize - 2,
                indicator.lightEdge);
        canvas.fill(indicatorX + 2, indicatorY + 2, indicatorSize - 4, indicatorSize - 4,
                indicator.background);
        canvas.fill(indicatorX + 6, indicatorY + 6, 4, 4, indicator.glyph);
        canvas.centeredText(label,
                x + QuickBuildWindowLayout.CONTROL_W / 2.0D + 5.0D,
                y + 14.0D, UiMainlinePreviewStyle.color(visual.getText()));
    }
}
