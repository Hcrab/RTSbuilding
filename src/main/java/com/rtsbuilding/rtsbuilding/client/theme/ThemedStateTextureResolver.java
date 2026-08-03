package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.resources.ResourceLocation;

/** 双轨按钮的唯一纹理路由入口；不允许调用方自行猜测当前模式。 */
public final class ThemedStateTextureResolver {
    public static ResourceLocation resolve(LegacyTextureSet legacy,
                                           ResourceLocation paletteSource,
                                           UiTextureState state) {
        return resolve(legacy, paletteSource, state, UiIndexedTextureSpec.PR133_THREE_TONE);
    }

    public static ResourceLocation resolve(LegacyTextureSet legacy,
                                           ResourceLocation paletteSource,
                                           UiTextureState state,
                                           UiIndexedTextureSpec spec) {
        if (legacy == null || paletteSource == null || state == null) {
            throw new IllegalArgumentException("theme texture route must be complete");
        }
        if (UiThemeRuntime.manager().active().renderMode() == UiThemeRenderMode.LEGACY_DIRECT) {
            return legacy.resolve(state);
        }
        return UiThemeTextureCache.INSTANCE.resolve(paletteSource, state, spec);
    }

    private ThemedStateTextureResolver() {
    }
}
