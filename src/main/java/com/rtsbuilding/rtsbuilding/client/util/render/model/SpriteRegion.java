package com.rtsbuilding.rtsbuilding.client.util.render.model;

import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;

/**
 * 精灵图区域——描述一张贴图中的某个矩形区域（一帧/一个状态）。
 * <p>这是整个精灵图渲染体系的<b>核心传递对象</b>，替代以往散落的
 * {@code (texture, srcX, srcY, srcW, srcH, texW, texH)} 七散参数。</p>
 *
 * <p>通过 {@link #withTheme()} 自动应用双主题偏移，
 * 通过 {@link #withVOffset(int)} 切换状态帧（正常→悬浮→选中）。</p>
 */
public record SpriteRegion(
        TextureInfo texture,
        int u, int v,
        int regionWidth, int regionHeight
) {

    // ======================== 主题偏移 ========================

    /**
     * 根据当前主题（亮/暗）偏移精灵区域的 X 坐标。
     * <p>对于 {@link TextureInfo.ThemeLayout#HORIZONTAL_PAIR} 布局，
     * 亮色主题偏移到右半区，暗色保持左半区。</p>
     *
     * @return 应用了主题偏移的新 SpriteRegion（不修改原对象）
     */
    public SpriteRegion withTheme() {
        return switch (texture.themeLayout()) {
            case HORIZONTAL_PAIR -> {
                int offset = ThemeManager.getInstance().isLightMode() ? texture.halfWidth() : 0;
                yield new SpriteRegion(texture, u + offset, v, regionWidth, regionHeight);
            }
            case NONE -> this;
        };
    }

    /**
     * 带 V 偏移创建副本（用于切换正常/悬浮/选中状态，不修改原对象）。
     *
     * @param yOffset 垂直方向像素偏移
     * @return 偏移后的新 SpriteRegion
     */
    public SpriteRegion withVOffset(int yOffset) {
        return new SpriteRegion(texture, u, v + yOffset, regionWidth, regionHeight);
    }

    /**
     * 带 U 偏移创建副本。
     *
     * @param xOffset 水平方向像素偏移
     * @return 偏移后的新 SpriteRegion
     */
    public SpriteRegion withUOffset(int xOffset) {
        return new SpriteRegion(texture, u + xOffset, v, regionWidth, regionHeight);
    }

    /**
     * 直接应用主题偏移 + 指定 V 偏移（常见操作，避免两个链式调用）。
     *
     * @param yOffset 垂直方向像素偏移
     * @return 偏移后的新 SpriteRegion
     */
    public SpriteRegion withThemeAndVOffset(int yOffset) {
        SpriteRegion themed = withTheme();
        return new SpriteRegion(themed.texture, themed.u, themed.v + yOffset,
                themed.regionWidth, themed.regionHeight);
    }
}
