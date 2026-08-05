package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

/**
 * 相机灵敏度、平滑开关和拖拽反向选项的唯一状态 owner。
 *
 * <p>负责预设索引换算和设置状态，不采样输入、不求解运动，也不写持久化；
 * {@link CameraOrbitService} 仅在编排输入时读取缩放值。</p>
 */
final class CameraSensitivitySettings {
    private static final float MIN_ROTATE = 1.00F;
    private static final float MAX_ROTATE = 10.00F;
    private static final float ROTATE_STEP = 0.50F;
    private static final float[] PRESETS = {0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F};
    private static final int DEFAULT_INDEX = 2;

    private float rotate = 5.00F;
    private int input = DEFAULT_INDEX;
    private int pan = DEFAULT_INDEX;
    private int view = DEFAULT_INDEX;
    private int keyboard = DEFAULT_INDEX;
    private int wheel = DEFAULT_INDEX;
    private boolean smooth;
    private boolean invertPanX;
    private boolean invertPanY;

    float rotate() { return rotate; }
    void increaseRotate() { rotate = MathHelper.clamp(rotate + ROTATE_STEP, MIN_ROTATE, MAX_ROTATE); }
    void decreaseRotate() { rotate = MathHelper.clamp(rotate - ROTATE_STEP, MIN_ROTATE, MAX_ROTATE); }

    String inputLabel() { return label(view); }
    int inputIndex() { return input; }
    int presetCount() { return PRESETS.length; }
    void setInputFraction(double value) { setAll(fromFraction(value)); }
    void cycleInput() { setAll((sanitize(input) + 1) % PRESETS.length); }
    float inputScale() { return PRESETS[sanitize(input)]; }

    String panLabel() { return label(pan); }
    int panIndex() { return pan; }
    void setPanFraction(double value) { pan = fromFraction(value); }
    float panScale() { return PRESETS[sanitize(pan)]; }

    String viewLabel() { return label(view); }
    int viewIndex() { return view; }
    void setViewFraction(double value) { view = fromFraction(value); input = view; }
    float viewScale() { return PRESETS[sanitize(view)]; }

    String keyboardLabel() { return label(keyboard); }
    int keyboardIndex() { return keyboard; }
    void setKeyboardFraction(double value) { keyboard = fromFraction(value); }
    float keyboardScale() { return PRESETS[sanitize(keyboard)]; }

    String wheelLabel() { return label(wheel); }
    int wheelIndex() { return wheel; }
    void setWheelFraction(double value) { wheel = fromFraction(value); }
    float wheelScale() { return PRESETS[sanitize(wheel)]; }

    boolean smooth() { return smooth; }
    void setSmooth(boolean value) { smooth = value; }
    boolean invertPanX() { return invertPanX; }
    void setInvertPanX(boolean value) { invertPanX = value; }
    boolean invertPanY() { return invertPanY; }
    void setInvertPanY(boolean value) { invertPanY = value; }

    private void setAll(int index) { input = index; pan = index; view = index; keyboard = index; wheel = index; }
    private static String label(int index) { return String.format(java.util.Locale.ROOT, "x%.2f", PRESETS[sanitize(index)]); }
    private static int fromFraction(double fraction) {
        return sanitize((int) Math.round(MathHelper.clamp(fraction, 0.0D, 1.0D) * (PRESETS.length - 1)));
    }
    private static int sanitize(int index) { return MathHelper.clamp(index, 0, PRESETS.length - 1); }
}
