package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;

/**
 * 普通按钮/选择控件共用的固定九块 chrome renderer。
 *
 * <p>本类只绘制背景、边框和禁用覆盖层；标签与图标仍由平台按字体和纹理能力绘制。
 * 隐藏控件不会提交任何原语，命中和 Action 也始终由调用方立即处理。</p>
 */
public final class UiControlChromeRenderer {
    public static int frame(UiCanvas2D canvas, UiRect bounds,
                            UiControlRole role, UiControlState state) {
        UiControlVisualStyle style = style(canvas, bounds, role, state);
        if (style == null) return 0;
        int quads = UiChromeRenderer.frame(canvas, bounds, 1.0D,
                style.getBackground(), style.getBorderLight(), style.getBorderDark());
        drawOverlay(canvas, bounds, style);
        return quads;
    }

    /** 同屏大量纯色按钮使用的低成本语义框体；角色、状态和禁用覆盖层与九宫格入口一致。 */
    public static int compactFrame(UiCanvas2D canvas, UiRect bounds,
                                   UiControlRole role, UiControlState state) {
        UiControlVisualStyle style = style(canvas, bounds, role, state);
        if (style == null) return 0;
        int primitives = UiCompactFrameRenderer.frame(canvas, bounds,
                style.getBackground(), style.getBorderLight(), style.getBorderDark());
        drawOverlay(canvas, bounds, style);
        return primitives + (style.getOverlay().alpha() > 0 ? 1 : 0);
    }

    private static UiControlVisualStyle style(UiCanvas2D canvas, UiRect bounds,
                                              UiControlRole role, UiControlState state) {
        if (canvas == null || bounds == null || role == null || state == null) {
            throw new IllegalArgumentException("canvas, bounds, role and state must not be null");
        }
        return state.isVisible() ? UiControlVisualStyle.resolve(role, state) : null;
    }

    private static void drawOverlay(UiCanvas2D canvas, UiRect bounds, UiControlVisualStyle style) {
        if (style.getOverlay().alpha() > 0) {
            canvas.fill(new UiRect(bounds.getX() + 1.0D, bounds.getY() + 1.0D,
                    Math.max(0.0D, bounds.getWidth() - 1.0D),
                    Math.max(0.0D, bounds.getHeight() - 1.0D)), style.getOverlay());
        }
    }

    private UiControlChromeRenderer() {
    }
}
