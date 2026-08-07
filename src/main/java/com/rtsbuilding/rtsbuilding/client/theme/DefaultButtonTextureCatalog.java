package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.resources.Identifier;

/**
 * 通用默认按钮原始像素图的唯一生产入口。
 *
 * <p>26.1 的 Extractor 路径仍直接使用资源包可覆写的原图。主题调色由 renderer
 * 在提交时完成，因此不需要第二套贴图、也不会改变九宫格切片和原始像素轮廓。</p>
 */
public final class DefaultButtonTextureCatalog {
    public static final Identifier LEGACY_SOURCE = Identifier.tryParse(
            "rtsbuilding:textures/gui/general/default_button.png");

    public static Identifier resolve(UiTextureState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        return LEGACY_SOURCE;
    }

    private DefaultButtonTextureCatalog() {
    }
}
