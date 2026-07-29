package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

public class ToggleSwitch {

    private static final int TRACK_W = 28;
    private static final int TRACK_H = 14;
    private static final int THUMB_SIZE = 10;
    private static final int THUMB_OFF = 2;

    private static final int TRACK_OFF = 0xFF555555;
    private static final int THUMB_COLOR = 0xFFFFFFFF;

    private boolean on;
    private final AnimFloat anim = AnimFloat.hover();
    private final Consumer<Boolean> onChange;
    private boolean lastExternalOn;
    private int lastX, lastY;

    public ToggleSwitch(boolean initialState, Consumer<Boolean> onChange) {
        this.on = initialState;
        this.anim.snapTo(initialState ? 1f : 0f);
        this.onChange = onChange;
    }

    public ToggleSwitch() {
        this(false, null);
    }

    public void setOn(boolean on) {
        if (this.on != on) {
            this.on = on;
            this.anim.target(on ? 1f : 0f);
            if (onChange != null) onChange.accept(on);
        }
    }

    public boolean isOn() { return on; }

    public void toggle() { setOn(!on); }

    public int getWidth() { return TRACK_W; }
    public int getHeight() { return TRACK_H; }

    private void renderAt(GuiGraphics g, int x, int y, float t) {
        int trackColor = lerpColor(TRACK_OFF, DarkUiPalette.toggleOn(), t);
        int thumbX = x + THUMB_OFF + Math.round(t * (TRACK_W - THUMB_SIZE - 2 * THUMB_OFF));
        SdfRenderer.drawPill(g, x, y, TRACK_W, TRACK_H, trackColor);
        SdfRenderer.drawPill(g, thumbX, y + THUMB_OFF, THUMB_SIZE, THUMB_SIZE, THUMB_COLOR);
    }

    public void render(GuiGraphics g, int x, int y) {
        lastX = x;
        lastY = y;
        renderAt(g, x, y, anim.get());
    }

    public void render(GuiGraphics g, int x, int y, boolean externalOn) {
        lastX = x;
        lastY = y;
        if (externalOn != lastExternalOn) {
            lastExternalOn = externalOn;
            anim.target(externalOn ? 1f : 0f);
        }
        renderAt(g, x, y, anim.get());
    }

    public boolean isClicked(double mouseX, double mouseY) {
        return mouseX >= lastX && mouseX < lastX + TRACK_W && mouseY >= lastY && mouseY < lastY + TRACK_H;
    }

    public boolean handleClick(double mouseX, double mouseY) {
        if (isClicked(mouseX, mouseY)) {
            toggle();
            return true;
        }
        return false;
    }

    public boolean handleClick(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TRACK_W && mouseY >= y && mouseY < y + TRACK_H && handleClick(mouseX, mouseY);
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0.005f) return from;
        if (t >= 0.995f) return to;
        int a = lerpComp((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpComp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpComp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }
}
