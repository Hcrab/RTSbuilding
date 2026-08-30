package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 设置页开关的像素母版布局。
 *
 * <p>本类只定义轨道、旋钮和动画行程，不负责主题颜色或纹理。主线纹理主题与
 * 调色板主题必须共同使用这里的几何数据，避免切换主题后控件尺寸和位置跳变。
 */
public final class SettingsSwitchLayout {
    public static final int WIDTH = 66;
    public static final int HEIGHT = 29;
    public static final int TRACK_HEIGHT = 24;
    public static final int KNOB_WIDTH = 26;
    public static final int KNOB_HEIGHT = 29;
    public static final int KNOB_TRAVEL = WIDTH - KNOB_WIDTH;

    private SettingsSwitchLayout() {
    }

    /** 按主线像素素材的原始比例计算开关轨道和旋钮位置。 */
    public static Geometry geometry(double x, double y, double selection) {
        double progress = Math.max(0.0D, Math.min(1.0D, selection));
        double knobX = x + KNOB_TRAVEL * progress;
        return new Geometry(
                new UiRect(x, y, WIDTH, HEIGHT),
                new UiRect(x, y + (HEIGHT - TRACK_HEIGHT) / 2.0D,
                        WIDTH, TRACK_HEIGHT),
                new UiRect(knobX, y, KNOB_WIDTH, KNOB_HEIGHT));
    }

    public static final class Geometry {
        public final UiRect bounds;
        public final UiRect track;
        public final UiRect knob;

        private Geometry(UiRect bounds, UiRect track, UiRect knob) {
            this.bounds = bounds;
            this.track = track;
            this.knob = knob;
        }
    }
}
