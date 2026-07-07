package com.rtsbuilding.rtsbuilding.client.util.render.model;

import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;

/**
 * 九宫格精灵图规格——描述一个需要九宫格拼贴渲染的精灵区域。
 * <p>将源矩形 + 边框宽度聚合成一个不可变对象，传递给
 * {@link SpriteRenderer#drawNineSlice} 进行九宫格渲染。</p>
 *
 * <p>与 {@link SpriteRegion} 的关系：九宫格是精灵区域的一种渲染方式，
 * 在标准精灵区域的基础上附加了边框宽度信息。</p>
 *
 * <p>通过 {@link #withTheme()} 自动应用双主题偏移，
 * 通过 {@link #withVOffset(int)} 切换状态帧。</p>
 *
 * <p>快捷创建：</p>
 * <ul>
 *   <li>{@link #fullTheme(TextureInfo, int, int)} — 从左上角开始的半区全宽九宫格</li>
 * </ul>
 */
public record NineSliceRegion(
        SpriteRegion region,
        int border
) {

    /**
     * 从贴图的半区左上角创建九宫格规格。
     * <p>常用于占满整张半区的背景贴图。源区域从 (0, 0) 开始，
     * 宽度=半区宽度，高度=指定区域高度。</p>
     *
     * @param texture  贴图元数据
     * @param regionH  单状态区域高度
     * @param border   九宫格边框宽度
     * @return NineSliceRegion 实例
     */
    public static NineSliceRegion fullTheme(TextureInfo texture, int regionH, int border) {
        SpriteRegion fullRegion = new SpriteRegion(texture, 0, 0, texture.halfWidth(), regionH);
        return new NineSliceRegion(fullRegion, border);
    }

    // ======================== 状态转换 ========================

    /**
     * 应用当前主题偏移。
     *
     * @return 主题偏移后的新 NineSliceRegion
     */
    public NineSliceRegion withTheme() {
        return new NineSliceRegion(region.withTheme(), border);
    }

    /**
     * 带 V 偏移创建副本（用于切换正常/悬浮状态）。
     *
     * @param yOffset 垂直偏移量
     * @return 偏移后的新 NineSliceRegion
     */
    public NineSliceRegion withVOffset(int yOffset) {
        return new NineSliceRegion(region.withVOffset(yOffset), border);
    }
}
