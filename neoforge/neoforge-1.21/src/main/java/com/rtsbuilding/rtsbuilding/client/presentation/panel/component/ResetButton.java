package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import net.minecraft.client.gui.GuiGraphics;

public class ResetButton {



    public static final int BTN_SIZE = 16;
    private static final int BORDER_WIDTH = 1;

    private final AnimFloat hoverState = AnimFloat.hover();

    private int areaX, areaY;

    private Runnable resetAction;

    public void setResetAction(Runnable action) {
        this.resetAction = action;
    }

    public void render(GuiGraphics g, int mx, int my, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        boolean hovering = mx >= btnX && mx < btnX + BTN_SIZE
                && my >= btnY && my < btnY + BTN_SIZE;
        float t = this.hoverState.track(hovering);

        int fillColor = lerpColor(DarkUiPalette.bg(), DarkUiPalette.accent(), t);
        SdfRenderer.drawBorderedRoundedRect(g, btnX, btnY, BTN_SIZE, BTN_SIZE, 4,
                DarkUiPalette.black(), fillColor, BORDER_WIDTH);

        int iconSize = BTN_SIZE * 2 / 3;
        int iconOff = (BTN_SIZE - iconSize) / 2;
        int iconColor = ThemeManager.getInstance().isLightMode() ? 0xFF333333 : 0xFFCCCCCC;
        SdfRenderer.drawResetIcon(g, btnX + iconOff, btnY + iconOff, iconSize, iconColor);
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

    public boolean handleClick(double mx, double my) {
        if (mx >= areaX && mx < areaX + BTN_SIZE
                && my >= areaY && my < areaY + BTN_SIZE) {
            if (resetAction != null) {
                resetAction.run();
            }
            return true;
        }
        return false;
    }
}
