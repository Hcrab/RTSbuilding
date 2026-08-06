package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowSliderChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowSliderLayout;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiMotionSpec;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiValueAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

/**
 * Window-style horizontal slider, suitable for RTS panels.
 * <p>
 * Supports click-and-drag value adjustment with track and knob rendering.
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
    /** 逻辑值立刻提交；该对象只让 Legacy 纹理轨道上的旋钮平滑追随。 */
    private final UiValueAnimation displayValue = new UiValueAnimation(SystemUiClock.INSTANCE);

    public WindowSlider(int x, int y, int width, int height, int min, int max, int value) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathHelper.clamp(value, min, this.max);
    }

    // ======================== Properties ========================

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        int clamped = MathHelper.clamp(value, min, max);
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

    // ======================== Rendering ========================

    public void render(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        WindowSliderChromeRenderer.render(
                new MinecraftUiCanvas(g, Minecraft.getMinecraft().fontRenderer),
                geometry());
    }

    // ======================== Input handling ========================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;
        if (bounds().contains(mouseX, mouseY)) {
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
        setValueFromMouse(mouseX);
        return true;
    }

    // ======================== Private helpers ========================

    private WindowSliderLayout.Geometry geometry() {
        double shown = displayValue.update(value, Config.isUiAnimationsEnabled(), UiMotionSpec.SLIDER_MS);
        return WindowSliderLayout.geometry(bounds(), min, max, shown, value);
    }

    private UiRect bounds() {
        return new UiRect(x, y, width, height);
    }

    private void setValueFromMouse(double mouseX) {
        setValue(WindowSliderLayout.valueAt(bounds(), min, max, mouseX));
    }
}
