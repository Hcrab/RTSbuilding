package com.rtsbuilding.rtsbuilding.client.widget;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * 绐楀彛椋庢牸鐨勬按骞虫粦鏉★紝閫傜敤浜?RTS 闈㈡澘銆?
 * <p>
 * 鏀寔榧犳爣鐐瑰嚮鍜屾嫋鎷借皟鏁村€硷紝甯︽湁婊戣建鍜屾棆閽覆鏌撱€?
 */
public class WindowSlider {

    private int x;
    private int y;
    private int width;
    private int height;
    private int min;
    private int max;
    private int value;
    private boolean visible = true;
    private boolean dragging = false;
    private Consumer<Integer> onChange;

    // ======================== 棰滆壊甯搁噺 ========================
    private static final int TRACK_BG = 0xFF07090D;
    private static final int TRACK_FILL = 0xFF313946;
    private static final int KNOB_COLOR = 0xFF5FE36C;
    private static final int TRACK_H = 4;
    private static final int KNOB_W = 8;
    private static final int KNOB_H = 12;

    public WindowSlider(int x, int y, int width, int height, int min, int max, int value) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = Mth.clamp(value, min, this.max);
    }

    // ======================== 灞炴€?========================

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        int clamped = Mth.clamp(value, min, max);
        if (this.value != clamped) {
            this.value = clamped;
            if (onChange != null) {
                onChange.accept(this.value);
            }
        }
    }

    public void setRange(int min, int max) {
        this.min = min;
        this.max = Math.max(min, max);
        setValue(this.value);
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public WindowSlider onChange(Consumer<Integer> onChange) {
        this.onChange = onChange;
        return this;
    }

    // ======================== 娓叉煋 ========================

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;

        int knobX = knobPosition();
        int trackCenterY = y + height / 2;

        // 婊戣建鑳屾櫙
        g.fill(x, trackCenterY - TRACK_H / 2, x + width, trackCenterY + TRACK_H - TRACK_H / 2, TRACK_BG);
        g.fill(x + 1, trackCenterY - TRACK_H / 2 + 1, x + width - 1, trackCenterY + TRACK_H - TRACK_H / 2 - 1, TRACK_FILL);

        // 鏃嬮挳
        int knobY = trackCenterY - KNOB_H / 2;
        g.fill(knobX - KNOB_W / 2, knobY, knobX + KNOB_W - KNOB_W / 2, knobY + KNOB_H, KNOB_COLOR);
    }

    // ======================== 杈撳叆澶勭悊 ========================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            setValueFromMouse(mouseX);
            this.dragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!visible || !dragging || button != 0) return false;
        double clampedX = Mth.clamp(mouseX, x, x + width);
        setValueFromMouse(clampedX);
        return true;
    }

    // ======================== 绉佹湁杈呭姪 ========================

    private int knobPosition() {
        if (max <= min) return x;
        double fraction = (value - min) / (double) (max - min);
        return x + (int) Math.round(fraction * width);
    }

    private void setValueFromMouse(double mouseX) {
        double fraction = (mouseX - x) / Math.max(1.0D, width);
        fraction = Mth.clamp(fraction, 0.0D, 1.0D);
        int newValue = (int) Math.round(min + fraction * (max - min));
        setValue(Mth.clamp(newValue, min, max));
    }
}
