package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiControlChromeRendererTest {
    @Test
    void 角色只改变语义背景且始终固定九块() {
        CapturingCanvas canvas = new CapturingCanvas();
        int quads = UiControlChromeRenderer.frame(canvas, new UiRect(2, 3, 30, 14),
                UiControlRole.PRIMARY_ACTION, UiControlState.enabled());

        assertEquals(9, quads);
        assertEquals(9, canvas.colors.size());
        assertEquals(RtsMainlineTheme.BUTTON_PRIMARY_BACKGROUND, canvas.colors.get(4));
    }

    @Test
    void 隐藏控件不提交原语且禁用覆盖层有界() {
        CapturingCanvas canvas = new CapturingCanvas();
        assertEquals(0, UiControlChromeRenderer.frame(canvas, new UiRect(0, 0, 20, 10),
                UiControlRole.COMMAND, UiControlState.hidden()));
        assertEquals(0, canvas.rects.size());

        UiControlChromeRenderer.frame(canvas, new UiRect(10, 20, 20, 10),
                UiControlRole.COMMAND, UiControlState.disabled("locked"));
        assertEquals(10, canvas.rects.size());
        assertEquals(new UiRect(11, 21, 19, 9), canvas.rects.get(9));
        assertEquals(RtsMainlineTheme.CONTROL_DISABLED_OVERLAY, canvas.colors.get(9));
    }

    @Test
    void 紧凑入口复用同一角色状态但只提交五块() {
        CapturingCanvas canvas = new CapturingCanvas();
        int primitives = UiControlChromeRenderer.compactFrame(canvas,
                new UiRect(2, 3, 40, 16), UiControlRole.PRIMARY_ACTION,
                UiControlState.enabled());
        assertEquals(5, primitives);
        assertEquals(5, canvas.rects.size());
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiRect> rects = new ArrayList<UiRect>();
        private final List<UiColor> colors = new ArrayList<UiColor>();

        @Override
        public void fill(UiRect rect, UiColor color) {
            rects.add(rect);
            colors.add(color);
        }

        @Override public void text(String text, double x, double topY, UiColor color) { }
        @Override public void pushClip(UiRect clip) { }
        @Override public void popClip() { }
        @Override public void pushTransform() { }
        @Override public void popTransform() { }
        @Override public void translate(double x, double y) { }
        @Override public void scale(double x, double y) { }
    }
}
