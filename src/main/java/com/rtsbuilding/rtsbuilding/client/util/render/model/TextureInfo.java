package com.rtsbuilding.rtsbuilding.client.util.render.model;

import net.minecraft.resources.ResourceLocation;

/**
 * 贴图元数据——描述一张贴图文件的完整规格。
 * <p>集中管理贴图的：路径、尺寸、主题布局方式、纹理过滤策略。
 * 每一张贴图在整个应用中只应有一个对应的 {@code TextureInfo} 实例，
 * 避免各组件散落重复的尺寸常量和过滤逻辑。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * // 文件顶部定义
 * private static final TextureInfo UI_TEX = new TextureInfo(
 *     ResourceLocation.tryParse("rtsbuilding:textures/gui/base/base_ui/base_ui_1.png"),
 *     32, 32, ThemeLayout.HORIZONTAL_PAIR, FilterMode.PIXEL);
 *
 * // 创建精灵帧
 * SpriteRegion normal = new SpriteRegion(UI_TEX, 0, 0, 16, 16);
 * SpriteRegion hovered = normal.withVOffset(16);
 *
 * // 直接绘制
 * SpriteRenderer.drawSprite(g, normal.withTheme(), dstX, dstY, dstW, dstH);
 * }</pre>
 */
public record TextureInfo(
        ResourceLocation location,
        int fullWidth,
        int fullHeight,
        ThemeLayout themeLayout,
        FilterMode filterMode
) {
    // ======================== 主题布局策略 ========================

    /**
     * 主题布局枚举——定义精灵图中亮/暗主题的排列方式。
     */
    public enum ThemeLayout {
        /** 无主题分割：贴图不包含主题双份 */
        NONE,
        /** 水平双主题：左半=暗色，右半=亮色（默认方式） */
        HORIZONTAL_PAIR
    }

    // ======================== 纹理过滤策略 ========================

    /**
     * 纹理过滤策略——定义贴图缩放时的像素采样方式。
     * <p>策略附着在 {@link TextureInfo} 上，由 {@link com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer#drawSprite}
     * 自动应用，调用方无需手动设置过滤参数。</p>
     */
    public enum FilterMode {
        /**
         * 最近邻过滤（GL_NEAREST）。
         * <p>适合像素风格的低分辨率贴图（如按钮图标），
         * 放大时保持清晰锐利的锯齿边缘，不会产生模糊。</p>
         */
        PIXEL,

        /**
         * 双线性过滤（GL_LINEAR）。
         * <p>适合一般尺寸的贴图缩放，放大时边缘平滑，
         * 但过度缩小会产生锯齿闪烁。</p>
         */
        NORMAL,

        /**
         * 三线性过滤（GL_LINEAR_MIPMAP_LINEAR）。
         * <p>适合高分辨率写实风格贴图（如照片级纹理），
         * 首次绘制时自动生成 mipmap，缩小渲染时消除闪烁和锯齿。
         * <b>不适用于像素风格贴图</b>——mipmap 会导致颜色渗漏。</p>
         */
        HQ
    }

    // ======================== 便利计算 ========================

    /** 单主题半区宽度（仅对 HORIZONTAL_PAIR 有意义） */
    public int halfWidth() {
        return switch (themeLayout) {
            case HORIZONTAL_PAIR -> fullWidth / 2;
            case NONE -> fullWidth;
        };
    }

    /** 单主题半区高度 */
    public int halfHeight() {
        return switch (themeLayout) {
            case HORIZONTAL_PAIR -> fullHeight;
            case NONE -> fullHeight;
        };
    }
}
