package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.GuideWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.GuideWindowStyle;

/**
 * 指南主题行与滚动条的纯 Canvas chrome。
 *
 * <p>主题行保持旧生产实现的五矩形提交顺序和角点覆盖；滚动条只绘制轨道与滑块。
 * 本类不绘制图标、文字，也不读取指南主题目录或 Core 状态。</p>
 */
public final class GuideWindowChromeRenderer {
    public static final int TOPIC_PRIMITIVE_COUNT = 5;
    public static final int SCROLLBAR_PRIMITIVE_COUNT = 2;

    private GuideWindowChromeRenderer() {
    }

    public static int renderTopic(UiCanvas2D canvas, UiRect bounds, boolean selected) {
        require(canvas, bounds);
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.fill(bounds, GuideWindowStyle.topicBackground(selected));
        canvas.fill(x, y, width + 1.0D, 1.0D,
                GuideWindowStyle.topicBorderLight(selected));
        canvas.fill(x, y + height, width + 1.0D, 1.0D,
                GuideWindowStyle.TOPIC_BORDER_DARK);
        canvas.fill(x, y, 1.0D, height + 1.0D,
                GuideWindowStyle.topicBorderLight(selected));
        canvas.fill(x + width, y, 1.0D, height + 1.0D,
                GuideWindowStyle.TOPIC_BORDER_DARK);
        return TOPIC_PRIMITIVE_COUNT;
    }

    public static int renderScrollbar(UiCanvas2D canvas, UiRect track,
                                      int scroll, int total, int visible) {
        require(canvas, track);
        if (total <= visible || track.isEmpty()) {
            return 0;
        }
        double height = track.getHeight();
        double knobHeight = Math.max(GuideWindowLayout.SCROLLBAR_MIN_KNOB_H,
                height * visible / Math.max(visible + 1, total));
        int maxScroll = Math.max(1, total - visible);
        int safeScroll = Math.max(0, Math.min(maxScroll, scroll));
        double knobY = track.getY()
                + (height - knobHeight) * safeScroll / maxScroll;
        canvas.fill(track, GuideWindowStyle.SCROLLBAR_TRACK);
        canvas.fill(new UiRect(track.getX(), knobY, track.getWidth(), knobHeight),
                GuideWindowStyle.SCROLLBAR_KNOB);
        return SCROLLBAR_PRIMITIVE_COUNT;
    }

    private static void require(UiCanvas2D canvas, UiRect bounds) {
        if (canvas == null || bounds == null) {
            throw new IllegalArgumentException("canvas and bounds must not be null");
        }
    }
}
