package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintWindowStyle;

/**
 * 蓝图放置/捕获浮窗内容区的纯 Canvas chrome renderer。
 *
 * <p>本类只绘制区块底、状态底、文本框底和主动作框，不绘制文字，不调用 Minecraft
 * 控件，也不执行保存、建造或清除。生产与离屏共用这些 primitive，平台层只负责字体、
 * EditBox 和按钮生命周期。</p>
 */
public final class BlueprintWindowChromeRenderer {
    private BlueprintWindowChromeRenderer() {
    }

    public static void renderSection(UiCanvas2D canvas, UiRect bounds) {
        require(canvas, bounds);
        canvas.fill(bounds, BlueprintWindowStyle.SECTION_BACKGROUND);
        canvas.fill(bounds.getX(), bounds.getY(), bounds.getWidth(), 1.0D,
                BlueprintWindowStyle.SECTION_BORDER_LIGHT);
        canvas.fill(bounds.getX(), bounds.bottom() - 1.0D, bounds.getWidth(), 1.0D,
                BlueprintWindowStyle.SECTION_BORDER_DARK);
    }

    public static void renderStatus(UiCanvas2D canvas, UiRect bounds) {
        require(canvas, bounds);
        canvas.fill(bounds, BlueprintWindowStyle.STATUS_BACKGROUND);
        canvas.fill(bounds.getX(), bounds.getY(), bounds.getWidth(), 1.0D,
                BlueprintWindowStyle.STATUS_BORDER_LIGHT);
    }

    public static void renderPrimaryAction(UiCanvas2D canvas, UiRect bounds) {
        require(canvas, bounds);
        canvas.fill(bounds, BlueprintWindowStyle.PRIMARY_ACTION_BACKGROUND);
        double x = bounds.getX();
        double y = bounds.getY();
        double w = bounds.getWidth();
        double h = bounds.getHeight();
        canvas.fill(x - 1.0D, y - 1.0D, w + 2.0D, 1.0D,
                BlueprintWindowStyle.PRIMARY_ACTION_BORDER);
        canvas.fill(x - 1.0D, y + h, w + 2.0D, 1.0D,
                BlueprintWindowStyle.PRIMARY_ACTION_BORDER);
        canvas.fill(x - 1.0D, y - 1.0D, 1.0D, h + 2.0D,
                BlueprintWindowStyle.PRIMARY_ACTION_BORDER);
        canvas.fill(x + w, y - 1.0D, 1.0D, h + 2.0D,
                BlueprintWindowStyle.PRIMARY_ACTION_BORDER);
    }

    public static void renderField(UiCanvas2D canvas, UiRect bounds, boolean enabled) {
        require(canvas, bounds);
        UiCompactFrameRenderer.frame(canvas, bounds,
                BlueprintWindowStyle.fieldBackground(enabled),
                BlueprintWindowStyle.FIELD_BORDER_LIGHT,
                BlueprintWindowStyle.FIELD_BORDER_DARK);
    }

    public static void renderDisabledFieldOverlay(UiCanvas2D canvas, UiRect bounds) {
        require(canvas, bounds);
        canvas.fill(bounds, BlueprintWindowStyle.DISABLED_FIELD_OVERLAY);
    }

    private static void require(UiCanvas2D canvas, UiRect bounds) {
        if (canvas == null || bounds == null) {
            throw new IllegalArgumentException("canvas and bounds must not be null");
        }
    }
}
