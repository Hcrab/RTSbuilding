package com.rtsbuilding.rtsbuilding.client.util;

/**
 * 九宫格源矩形规格——将源坐标、尺寸和边框宽度聚合成一个不可变对象。
 *
 * <p>用于 {@link RtsClientUiUtil#drawNineSlice} 方法，替代原始的 5 个散参数，
 * 杜绝传参顺序错误。</p>
 *
 * <p>通过 {@link #withYOffset(int)} 支持状态切换（正常/悬浮），
 * 通过 {@link #fullTheme(int, int, int)} 快捷创建最常用的全主题规格。</p>
 */
public record NineSliceSource(int srcX, int srcY, int srcW, int srcH, int border) {

    /** 从单主题全宽创建（srcX=0, srcY=0）。适用于占满整个半区的贴图布局。 */
    public static NineSliceSource fullTheme(int regionW, int regionH, int border) {
        return new NineSliceSource(0, 0, regionW, regionH, border);
    }

    /** 带 Y 偏移创建副本（用于切换正常/悬浮状态，不修改原对象）。 */
    public NineSliceSource withYOffset(int yOffset) {
        return new NineSliceSource(srcX, srcY + yOffset, srcW, srcH, border);
    }
}
