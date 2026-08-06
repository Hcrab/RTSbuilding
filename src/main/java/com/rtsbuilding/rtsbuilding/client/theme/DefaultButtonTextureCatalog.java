package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiIndexedTextureSpec;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用按钮的唯一素材入口。
 *
 * <p>Legacy 直接返回资源包可覆盖的原始 {@code default_button.png}；Palette 也读取同一张
 * 原图，只通过严格索引表逐像素换色。这里刻意不存在 Palette 专属按钮贴图。</p>
 */
public final class DefaultButtonTextureCatalog {
    public static final ResourceLocation LEGACY_SOURCE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/general/default_button.png");

    public static ResourceLocation resolve(UiTextureState state) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (UiThemeRuntime.manager().active().renderMode()
                == UiThemeRenderMode.LEGACY_DIRECT) {
            return LEGACY_SOURCE;
        }
        return UiThemeTextureCache.INSTANCE.resolve(
                LEGACY_SOURCE, state, UiIndexedTextureSpec.LEGACY_DEFAULT_BUTTON);
    }

    private DefaultButtonTextureCatalog() {
    }
}
