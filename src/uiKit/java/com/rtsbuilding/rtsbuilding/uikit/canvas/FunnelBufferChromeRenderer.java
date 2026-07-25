package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.FunnelBufferStyle;

/**
 * 漏斗缓存面板的纯 Canvas 背景渲染器。
 *
 * <p>它不绘制物品与文字，也不读取漏斗业务状态；生产和离屏只把共享 Layout
 * 计算出的矩形及显隐/hover 语义传入。</p>
 */
public final class FunnelBufferChromeRenderer {
    private FunnelBufferChromeRenderer() {
    }

    public static void renderToggle(UiCanvas2D canvas, UiRect bounds, boolean panelVisible) {
        require(canvas, bounds);
        canvas.fill(bounds, FunnelBufferStyle.toggle(panelVisible));
    }

    public static void renderPanel(UiCanvas2D canvas, UiRect bounds) {
        require(canvas, bounds);
        canvas.fill(bounds, FunnelBufferStyle.PANEL_BACKGROUND);
    }

    public static int renderRow(UiCanvas2D canvas, UiRect row, UiRect slot, boolean hovered) {
        require(canvas, row);
        require(canvas, slot);
        canvas.fill(row, FunnelBufferStyle.ROW_BACKGROUND);
        canvas.fill(slot, FunnelBufferStyle.SLOT_BACKGROUND);
        if (hovered) {
            canvas.fill(row, FunnelBufferStyle.ROW_HOVER_OVERLAY);
        }
        return hovered ? 3 : 2;
    }

    private static void require(UiCanvas2D canvas, UiRect bounds) {
        if (canvas == null || bounds == null) {
            throw new IllegalArgumentException("canvas and bounds must not be null");
        }
    }
}
