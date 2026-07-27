package com.rtsbuilding.rtsbuilding.client.util.render.model;

import net.minecraft.resources.ResourceLocation;

public record TextureInfo(
        ResourceLocation location,
        int fullWidth,
        int fullHeight,
        ThemeLayout themeLayout,
        FilterMode filterMode
) {
    

    
    public enum ThemeLayout {
        
        NONE,
        
        HORIZONTAL_PAIR
    }

    

    
    public enum FilterMode {
        
        PIXEL,

        
        NORMAL,

        
        HQ
    }

    

    
    public int halfWidth() {
        return switch (themeLayout) {
            case HORIZONTAL_PAIR -> fullWidth / 2;
            case NONE -> fullWidth;
        };
    }

    
    public int halfHeight() {
        return switch (themeLayout) {
            case HORIZONTAL_PAIR -> fullHeight;
            case NONE -> fullHeight;
        };
    }
}
