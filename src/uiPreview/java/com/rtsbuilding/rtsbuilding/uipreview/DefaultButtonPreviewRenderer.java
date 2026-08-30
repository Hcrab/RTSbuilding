package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.layout.DefaultButtonTextureLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;

/** 离屏预览对 Legacy 通用按钮母版的只读九宫切片适配器。 */
final class DefaultButtonPreviewRenderer {
    static void render(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            UiRect bounds,
            UiTextureState state,
            double opacity) {
        if (opacity <= 0.001D) return;
        for (DefaultButtonTextureLayout.Slice slice
                : DefaultButtonTextureLayout.slices(bounds, state)) {
            canvas.imageRegion(
                    assets.defaultButton(state),
                    slice.source(), slice.target(), opacity);
        }
    }

    static void renderAnimated(
            BufferedImageUiCanvas canvas,
            UiMainlineAssets assets,
            UiRect bounds,
            UiControlAnimationState.Snapshot animation) {
        double pressed = animation.press();
        double selected = (1.0D - pressed) * animation.selection();
        double hovered = (1.0D - pressed)
                * (1.0D - animation.selection()) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - pressed - selected - hovered);
        render(canvas, assets, bounds, UiTextureState.INACTIVE, inactive);
        render(canvas, assets, bounds, UiTextureState.HOVER, hovered);
        render(canvas, assets, bounds, UiTextureState.ACTIVE, selected);
        render(canvas, assets, bounds, UiTextureState.PRESSED, pressed);
    }

    private DefaultButtonPreviewRenderer() {
    }
}
