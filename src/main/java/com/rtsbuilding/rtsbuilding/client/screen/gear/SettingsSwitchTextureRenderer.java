package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.client.util.RtsTextureRenderer;
import com.rtsbuilding.rtsbuilding.client.theme.UiThemeTextureCache;
import com.rtsbuilding.rtsbuilding.uikit.layout.SettingsSwitchLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Legacy 设置开关纹理的分层动画绘制器。
 *
 * <p>原图是 360×900 的四态图集，每态由 4 倍分辨率的轨道和方形滑块组成。
 * 本类只拆分并重组已有素材：左侧已开启轨道随进度增长，右侧关闭轨道随进度缩短，
 * 滑块在两者之间真实移动。它不拥有设置值、点击命中或主题选择。</p>
 */
final class SettingsSwitchTextureRenderer {
    static final int WIDTH = SettingsSwitchLayout.WIDTH;
    static final int HEIGHT = SettingsSwitchLayout.HEIGHT;

    private static final ResourceLocation TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/general/switch_button.png");
    private static final int SHEET_W = 360;
    private static final int SHEET_H = 900;
    private static final int TRACK_W = SettingsSwitchLayout.KNOB_TRAVEL;
    private static final int TRACK_H = SettingsSwitchLayout.TRACK_HEIGHT;
    private static final int KNOB_W = SettingsSwitchLayout.KNOB_WIDTH;
    private static final int KNOB_H = SettingsSwitchLayout.KNOB_HEIGHT;
    private static final int SOURCE_TRACK_W = 161;
    private static final int SOURCE_OFF_TRACK_X = 151;
    private static final int SOURCE_ON_TRACK_X = 48;
    private static final int SOURCE_KNOB_W = 103;
    private static final int SOURCE_KNOB_H = 116;

    static void render(RtsGuiContext graphics, int x, int y,
                       double selection, double hover) {
        double selected = clamp(selection);
        double hovered = clamp(hover);
        SettingsSwitchLayout.Geometry geometry = SettingsSwitchLayout.geometry(x, y, selected);
        float leftWidth = (float) (TRACK_W * selected);
        float rightWidth = TRACK_W - leftWidth;
        int consumedSource = (int) Math.round(SOURCE_TRACK_W * selected);
        float trackY = (float) geometry.track.getY();

        if (leftWidth > 0.01F) {
            draw(graphics, x, trackY, leftWidth, TRACK_H,
                    SOURCE_ON_TRACK_X, 515,
                    Math.max(1, consumedSource), 95,
                    UiTextureState.ACTIVE, 1.0D - hovered);
            draw(graphics, x, trackY, leftWidth, TRACK_H,
                    SOURCE_ON_TRACK_X, 742,
                    Math.max(1, consumedSource), 95,
                    UiTextureState.ACTIVE, hovered);
        }
        if (rightWidth > 0.01F) {
            int remainingSource = Math.max(1, SOURCE_TRACK_W - consumedSource);
            int sourceX = SOURCE_OFF_TRACK_X + consumedSource;
            float rightX = x + leftWidth + KNOB_W;
            draw(graphics, rightX, trackY, rightWidth, TRACK_H,
                    sourceX, 64, remainingSource, 96,
                    UiTextureState.INACTIVE, 1.0D - hovered);
            draw(graphics, rightX, trackY, rightWidth, TRACK_H,
                    sourceX, 288, remainingSource, 96,
                    UiTextureState.HOVER, hovered);
        }

        float knobX = (float) geometry.knob.getX();
        draw(graphics, knobX, y, KNOB_W, KNOB_H,
                48, 54, SOURCE_KNOB_W, SOURCE_KNOB_H,
                UiTextureState.INACTIVE,
                (1.0D - selected) * (1.0D - hovered));
        draw(graphics, knobX, y, KNOB_W, KNOB_H,
                48, 278, SOURCE_KNOB_W, SOURCE_KNOB_H,
                UiTextureState.HOVER,
                (1.0D - selected) * hovered);
        draw(graphics, knobX, y, KNOB_W, KNOB_H,
                209, 503, SOURCE_KNOB_W, SOURCE_KNOB_H,
                UiTextureState.ACTIVE,
                selected * (1.0D - hovered));
        draw(graphics, knobX, y, KNOB_W, KNOB_H,
                209, 730, SOURCE_KNOB_W, SOURCE_KNOB_H,
                UiTextureState.ACTIVE,
                selected * hovered);
    }

    private static void draw(RtsGuiContext graphics,
                             float x, float y, float width, float height,
                             int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                             UiTextureState state, double opacity) {
        if (opacity <= 0.001D || width <= 0.01F || height <= 0.01F) {
            return;
        }
        int alpha = (int) Math.round(clamp(opacity) * 255.0D);
        int tint = alpha >= 255
                ? RtsTextureRenderer.NO_TINT
                : alpha << 24 | RtsTextureRenderer.NO_TINT >>> Byte.SIZE;
        ResourceLocation texture = TEXTURE;
        if (UiThemeRuntime.manager().active().renderMode() == UiThemeRenderMode.PALETTE) {
            texture = UiThemeTextureCache.INSTANCE.resolve(
                    TEXTURE, state, UiIndexedTextureSpec.LEGACY_SETTINGS_SWITCH);
        }
        RtsTextureRenderer.drawTextureHighPrecision(
                graphics, texture,
                x, y, width, height,
                sourceX, sourceY, sourceWidth, sourceHeight,
                SHEET_W, SHEET_H, 0.0F, tint);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private SettingsSwitchTextureRenderer() {
    }
}
