package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.CullingWindowStyle;

/**
 * 范围剔除危险操作按钮的共享 Chrome。
 *
 * <p>这里只提交五块紧凑框体，不绘制文字、不判断鼠标，也不触发删除。生产窗口与离屏回放
 * 共同调用它，后续调整危险操作层级时不会只改到其中一条绘制路径。</p>
 */
public final class CullingWindowChromeRenderer {
    private CullingWindowChromeRenderer() {
    }

    public static int renderDeleteButton(UiCanvas2D canvas, UiRect bounds, boolean hovered) {
        CullingWindowStyle.DeleteVisual visual =
                CullingWindowStyle.deleteButton(hovered);
        return UiCompactFrameRenderer.frame(
                canvas, bounds, visual.background, visual.border, visual.darkBorder);
    }
}
