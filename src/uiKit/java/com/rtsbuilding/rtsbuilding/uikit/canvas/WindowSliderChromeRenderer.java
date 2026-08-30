package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uikit.layout.WindowSliderLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowSliderStyle;

/** 浮窗水平滑块的三矩形轨道与滑块；不拥有数值或拖拽状态。 */
public final class WindowSliderChromeRenderer {
    public static final int PRIMITIVE_COUNT = 3;

    private WindowSliderChromeRenderer() {
    }

    public static int render(UiCanvas2D canvas, WindowSliderLayout.Geometry geometry) {
        if (canvas == null || geometry == null) {
            throw new IllegalArgumentException("canvas and geometry must not be null");
        }
        canvas.fill(geometry.track, WindowSliderStyle.TRACK_BACKGROUND);
        canvas.fill(geometry.trackFill, WindowSliderStyle.TRACK_FILL);
        canvas.fill(geometry.knob, WindowSliderStyle.KNOB);
        return PRIMITIVE_COUNT;
    }
}
