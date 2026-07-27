package com.rtsbuilding.rtsbuilding.client.util.render.model;

import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;

public record SpriteRegion(
        TextureInfo texture,
        int u, int v,
        int regionWidth, int regionHeight
) {

    

    
    public SpriteRegion withTheme() {
        return switch (texture.themeLayout()) {
            case HORIZONTAL_PAIR -> {
                int offset = ThemeManager.getInstance().isLightMode() ? texture.halfWidth() : 0;
                yield new SpriteRegion(texture, u + offset, v, regionWidth, regionHeight);
            }
            case NONE -> this;
        };
    }

    
    public SpriteRegion withVOffset(int yOffset) {
        return new SpriteRegion(texture, u, v + yOffset, regionWidth, regionHeight);
    }

    
    public SpriteRegion withUOffset(int xOffset) {
        return new SpriteRegion(texture, u + xOffset, v, regionWidth, regionHeight);
    }

    
    public SpriteRegion withThemeAndVOffset(int yOffset) {
        SpriteRegion themed = withTheme();
        return new SpriteRegion(themed.texture, themed.u, themed.v + yOffset,
                themed.regionWidth, themed.regionHeight);
    }
}
