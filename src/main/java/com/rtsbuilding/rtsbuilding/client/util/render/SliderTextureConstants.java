package com.rtsbuilding.rtsbuilding.client.util.render;

import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.resources.ResourceLocation;


public final class SliderTextureConstants {

    

    
    public static final int TEX_W = 32;
    
    public static final int TEX_H = 32;
    
    public static final int THUMB_TEX_H = 48;
    
    public static final int SELECTED_V_OFFSET = 32;
    
    public static final int HALF_W = 16;
    public static final int HALF_H = 16;
    
    public static final int STATE_OFFSET = HALF_H;
    
    public static final int BORDER = 2;

    

    private static final ResourceLocation TRACK_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/mouse_wheel.png");
    private static final TextureInfo TRACK_TEX_INFO = new TextureInfo(
            TRACK_TEXTURE, TEX_W, TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    public static final NineSliceRegion TRACK_NINE_SLICE = new NineSliceRegion(
            new SpriteRegion(TRACK_TEX_INFO, 0, 0, HALF_W, HALF_H), BORDER);

    

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
