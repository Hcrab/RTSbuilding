package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.layout.DefaultButtonTextureLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 按 Legacy 默认像素图九宫格提交通用按钮，并在状态间连续淡化。
 *
 * <p>Legacy Direct 始终以白色乘色提交，因而与资源包原图完全一致。Palette 模式
 * 只改变乘色，不替换像素形状、UV 或命中区域；这让已存在的像素美术继续作为主题的
 * 母版，而不是退化为纯色矩形。</p>
 */
public final class DefaultButtonTextureRenderer {
    private DefaultButtonTextureRenderer() {
    }

    public static void renderAnimated(
            GuiGraphicsExtractor graphics,
            UiRect bounds,
            UiControlAnimationState.Snapshot animation,
            UiColor disabledOverlay) {
        renderAnimated(graphics, bounds, animation, disabledOverlay, 1.0D);
    }

    /**
     * 保持 Legacy 九宫格的原有切片和状态，只把父窗口进出场透明度乘到提交颜色上。
     */
    public static void renderAnimated(
            GuiGraphicsExtractor graphics,
            UiRect bounds,
            UiControlAnimationState.Snapshot animation,
            UiColor disabledOverlay,
            double parentOpacity) {
        if (animation == null) {
            throw new IllegalArgumentException("animation");
        }
        double pressed = animation.press();
        double selected = (1.0D - pressed) * animation.selection();
        double hovered = (1.0D - pressed)
                * (1.0D - animation.selection()) * animation.hover();
        double inactive = Math.max(0.0D, 1.0D - pressed - selected - hovered);
        renderState(graphics, bounds, UiTextureState.INACTIVE, inactive * parentOpacity);
        renderState(graphics, bounds, UiTextureState.HOVER, hovered * parentOpacity);
        renderState(graphics, bounds, UiTextureState.ACTIVE, selected * parentOpacity);
        renderState(graphics, bounds, UiTextureState.PRESSED, pressed * parentOpacity);
        drawOverlay(graphics, bounds, disabledOverlay, parentOpacity);
    }

    public static void renderHoverBlend(
            GuiGraphicsExtractor graphics, UiRect bounds, double hover,
            UiColor disabledOverlay) {
        double amount = clamp(hover);
        renderState(graphics, bounds, UiTextureState.INACTIVE, 1.0D - amount);
        renderState(graphics, bounds, UiTextureState.HOVER, amount);
        drawOverlay(graphics, bounds, disabledOverlay);
    }

    public static void renderState(
            GuiGraphicsExtractor graphics, UiRect bounds,
            UiTextureState state, double opacity) {
        if (graphics == null || bounds == null || state == null) {
            throw new IllegalArgumentException("graphics, bounds and state");
        }
        double alpha = clamp(opacity);
        if (alpha <= 0.001D) {
            return;
        }
        Identifier texture = DefaultButtonTextureCatalog.resolve(state);
        int tint = tint(state, alpha);
        for (DefaultButtonTextureLayout.Slice slice
                : DefaultButtonTextureLayout.slices(bounds, state)) {
            drawSlice(graphics, texture, slice, tint);
        }
    }

    private static void drawSlice(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            DefaultButtonTextureLayout.Slice slice,
            int tint) {
        UiRect source = slice.source();
        UiRect target = slice.target();
        if (target.getWidth() <= 0.0D || target.getHeight() <= 0.0D) {
            return;
        }
        RtsTextureRenderer.drawTextureHighPrecision(
                graphics, texture,
                (float) target.getX(), (float) target.getY(),
                (float) target.getWidth(), (float) target.getHeight(),
                (float) source.getX(), (float) source.getY(),
                (float) source.getWidth(), (float) source.getHeight(),
                DefaultButtonTextureLayout.SHEET_WIDTH,
                DefaultButtonTextureLayout.SHEET_HEIGHT,
                0.0F, tint);
    }

    private static void drawOverlay(
            GuiGraphicsExtractor graphics, UiRect bounds, UiColor overlay, double opacity) {
        if (overlay == null || overlay.alpha() <= 0) {
            return;
        }
        graphics.fill(
                (int) Math.round(bounds.getX()),
                (int) Math.round(bounds.getY()),
                (int) Math.round(bounds.right()),
                (int) Math.round(bounds.bottom()),
                alpha(overlay.toArgb(), opacity));
    }

    private static void drawOverlay(
            GuiGraphicsExtractor graphics, UiRect bounds, UiColor overlay) {
        drawOverlay(graphics, bounds, overlay, 1.0D);
    }

    private static int tint(UiTextureState state, double opacity) {
        if (UiThemeRuntime.manager().active().renderMode()
                == UiThemeRenderMode.LEGACY_DIRECT) {
            return alpha(0xFFFFFFFF, opacity);
        }
        UiColor color = switch (state) {
            case HOVER -> RtsMainlineTheme.CONTROL_HOVER_BACKGROUND;
            case PRESSED -> RtsMainlineTheme.CONTROL_PRESSED_BACKGROUND;
            case ACTIVE -> RtsMainlineTheme.CONTROL_SELECTED_BACKGROUND;
            case INACTIVE -> RtsMainlineTheme.BUTTON_BACKGROUND;
        };
        return alpha(color.toArgb(), opacity);
    }

    private static int alpha(int color, double opacity) {
        int sourceAlpha = color >>> 24 & 0xFF;
        int resultAlpha = (int) Math.round(sourceAlpha * clamp(opacity));
        return resultAlpha << 24 | color & 0x00FFFFFF;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
