package com.rtsbuilding.rtsbuilding.client.widget;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiMotionSpec;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiValueAnimation;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WindowSliderChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowSliderLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Window-style horizontal slider, suitable for RTS panels.
 * <p>
 * Supports click-and-drag value adjustment with track and knob rendering.
 */
public class WindowSlider {

    /** 当前父窗口的视觉透明度，仅包住一次 render 调用，不参与数值或命中。 */
    private static double globalOpacity = 1.0D;

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
    /** 逻辑值立即生效，显示值只负责平滑追随，绝不延迟业务回调。 */
    private final UiValueAnimation displayValue =
            new UiValueAnimation(SystemUiClock.INSTANCE);

    public WindowSlider(int x, int y, int width, int height, int min, int max, int value) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = Mth.clamp(value, min, this.max);
    }

    // ======================== Properties ========================

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

    // ======================== Rendering ========================

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        WindowSliderChromeRenderer.render(
                new MinecraftUiCanvas(g, net.minecraft.client.Minecraft.getInstance().font,
                        null, globalOpacity),
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

    /**
     * 由父窗口在绘制子控件前暂存透明度，并在 finally 中恢复，避免相邻窗口互相串色。
     */
    public static double setGlobalOpacity(double opacity) {
        double previous = globalOpacity;
        globalOpacity = Math.max(0.0D, Math.min(1.0D, opacity));
        return previous;
    }

    // ======================== Private helpers ========================

    private WindowSliderLayout.Geometry geometry() {
        double animatedValue = displayValue.update(
                value, Config.isUiAnimationsEnabled(), UiMotionSpec.SLIDER_MS);
        return WindowSliderLayout.geometry(bounds(), min, max, animatedValue, value);
    }

    private UiRect bounds() {
        return new UiRect(x, y, width, height);
    }

    private void setValueFromMouse(double mouseX) {
        setValue(WindowSliderLayout.valueAt(bounds(), min, max, mouseX));
    }
}
