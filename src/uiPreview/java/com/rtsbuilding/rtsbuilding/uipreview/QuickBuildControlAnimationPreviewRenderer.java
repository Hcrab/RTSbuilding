package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.QuickBuildChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

/**
 * 输出 Quick Build 右栏开关从开到关、从关到开的五个固定时间片。
 *
 * <p>该预览直接消费生产用 {@link UiControlAnimationState} 和 chrome renderer，
 * 每一帧复用同一个命中矩形；它不模拟玩法点击，也不另造一套离屏颜色公式。</p>
 */
final class QuickBuildControlAnimationPreviewRenderer {
    static final int SLICE_COUNT = 5;

    static void render(File outputDirectory) throws IOException {
        FixedUiClock clock = new FixedUiClock(0L);
        UiMainlineAssets assets = new UiMainlineAssets();
        UiControlAnimationState selecting = new UiControlAnimationState(clock);
        UiControlAnimationState clearing = new UiControlAnimationState(clock);
        selecting.update(state(false), true);
        clearing.update(state(true), true);

        for (int slice = 0; slice < SLICE_COUNT; slice++) {
            UiControlAnimationState.Snapshot left = selecting.update(state(true), true);
            UiControlAnimationState.Snapshot right = clearing.update(state(false), true);
            writeSlice(outputDirectory, assets, slice, left, right);
            if (slice + 1 < SLICE_COUNT) {
                clock.advanceMillis(UiControlAnimationState.SELECTION_DURATION_MS
                        / (SLICE_COUNT - 1));
            }
        }
    }

    private static UiControlState state(boolean selected) {
        return new UiControlState(
                true, true, false, false, false,
                selected, false, false, "");
    }

    private static void writeSlice(
            File outputDirectory,
            UiMainlineAssets assets,
            int slice,
            UiControlAnimationState.Snapshot left,
            UiControlAnimationState.Snapshot right) throws IOException {
        BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(520, 88, 2.0D);
        try {
            canvas.clear(UiMainlinePreviewStyle.color(
                    RtsMainlineTheme.WINDOW_BACKGROUND));
            drawControl(canvas, assets, 6, 6, "Fill", left);
            drawControl(canvas, assets, 104, 6, "Hollow", right);
            drawShape(canvas, assets, 214, 6, QuickBuildUiShape.BLOCK, left);
            File output = new File(outputDirectory,
                    String.format("animation_quick_build_switch_%03d.png", slice * 25));
            ImageIO.write(canvas.image(), "png", output);
            if (!output.isFile() || output.length() == 0L) {
                throw new IOException("Cannot write Quick Build switch animation slice: " + output);
            }
        } finally {
            canvas.close();
        }
    }

    /**
     * 按生产端 32×32 按钮尺寸叠加贡献者的 24×24 四态纹理。
     * BufferedImage 画布固定使用最近邻，因此该切片会同时守住“按钮不缩水”和“选中不跳帧”。
     */
    private static void drawShape(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            int x,
            int y,
            QuickBuildUiShape shape,
            UiControlAnimationState.Snapshot animation) {
        UiRect target = new UiRect(
                x, y,
                QuickBuildWindowLayout.SHAPE_SLOT,
                QuickBuildWindowLayout.SHAPE_SLOT);
        double pressed = animation.press();
        double selected = (1.0D - pressed) * animation.selection();
        double hovered = (1.0D - pressed)
                * (1.0D - animation.selection()) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - pressed - selected - hovered);
        canvas.image(assets.quickBuildShape(shape, UiTextureState.INACTIVE), target, inactive);
        canvas.image(assets.quickBuildShape(shape, UiTextureState.HOVER), target, hovered);
        canvas.image(assets.quickBuildShape(shape, UiTextureState.ACTIVE), target, selected);
        canvas.image(assets.quickBuildShape(shape, UiTextureState.PRESSED), target, pressed);
    }

    private static void drawControl(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            int x,
            int y,
            String label,
            UiControlAnimationState.Snapshot animation) {
        UiRect bounds = new UiRect(
                x, y, QuickBuildWindowLayout.CONTROL_W,
                QuickBuildWindowLayout.CONTROL_H);
        UiControlVisualStyle rowVisual = UiControlVisualStyle.animated(
                UiControlRole.TOGGLE, animation);
        DefaultButtonPreviewRenderer.renderAnimated(canvas, assets, bounds, animation);
        drawIndicator(canvas, assets,
                new UiRect(
                        x + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                        y + QuickBuildWindowLayout.CONTROL_ICON_INSET,
                        QuickBuildWindowLayout.CONTROL_ICON_SIZE,
                        QuickBuildWindowLayout.CONTROL_ICON_SIZE),
                animation);
        canvas.centeredText(
                label,
                x + QuickBuildWindowLayout.CONTROL_W / 2.0D + 5.0D,
                y + 14.0D,
                UiMainlinePreviewStyle.color(rowVisual.getText()));
    }

    private static void drawIndicator(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            UiRect target,
            UiControlAnimationState.Snapshot animation) {
        double selected = animation.selection();
        double hovered = (1.0D - selected) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - selected - hovered);
        drawIndicatorState(canvas, assets, target,
                UiTextureState.INACTIVE, 0, inactive);
        drawIndicatorState(canvas, assets, target,
                UiTextureState.HOVER, 512, hovered);
        drawIndicatorState(canvas, assets, target,
                UiTextureState.ACTIVE, 1024, selected);
    }

    private static void drawIndicatorState(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            UiRect target,
            UiTextureState state,
            int sourceY,
            double opacity) {
        canvas.imageRegion(
                assets.quickBuildIndicator(state),
                new UiRect(0, sourceY, 512, 512),
                target,
                opacity);
    }

    private QuickBuildControlAnimationPreviewRenderer() {
    }
}
