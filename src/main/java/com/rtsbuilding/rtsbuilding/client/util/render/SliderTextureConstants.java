package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.resources.ResourceLocation;

/**
 * 滑条/滚动条共享贴图常量——轨道与滑块共用贴图参数在此统一定义，
 * 避免 {@code ScrollBar} 和 {@code ScaleSliderComponent} 重复声明。
 *
 * <p><b>轨道贴图：</b>{@code mouse_wheel.png}（32×32，水平双主题，垂直分半↔正常/活跃）</p>
 * <p><b>滑块贴图：</b>{@code base_ui_2.png}（32×32，水平双主题，垂直分半↔正常/活跃）</p>
 */
public final class SliderTextureConstants {

    // ======================== 贴图尺寸 ========================

    /** 贴图宽度（轨道/滑块通用） */
    public static final int TEX_W = 32;
    /** 轨道贴图文件高度（mouse_wheel.png：32×32） */
    public static final int TEX_H = 32;
    /** 滑块贴图文件高度（base_ui_2.png：32×48） */
    public static final int THUMB_TEX_H = 48;
    /** 选中态垂直偏移（y=32-48，暂未使用） */
    public static final int SELECTED_V_OFFSET = 32;
    /** 贴图单一主题半区尺寸 */
    public static final int HALF_W = 16;
    public static final int HALF_H = 16;
    /** 状态切换垂直偏移（正常态 0-16，活跃态 16-32） */
    public static final int STATE_OFFSET = HALF_H;
    /** 九宫格边框 */
    public static final int BORDER = 2;

    // ======================== 轨道贴图（mouse_wheel.png） ========================

    private static final ResourceLocation TRACK_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/mouse_wheel.png");
    private static final TextureInfo TRACK_TEX_INFO = new TextureInfo(
            TRACK_TEXTURE, TEX_W, TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    public static final NineSliceRegion TRACK_NINE_SLICE = new NineSliceRegion(
            new SpriteRegion(TRACK_TEX_INFO, 0, 0, HALF_W, HALF_H), BORDER);

    // ======================== 滑块贴图（base_ui_2.png） ========================

    private static final ResourceLocation THUMB_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final TextureInfo THUMB_TEX_INFO = new TextureInfo(
            THUMB_TEXTURE, TEX_W, THUMB_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    public static final NineSliceRegion THUMB_NINE_SLICE = new NineSliceRegion(
            new SpriteRegion(THUMB_TEX_INFO, 0, 0, HALF_W, HALF_H), BORDER);

    private SliderTextureConstants() {}
}
