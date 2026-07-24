package com.rtsbuilding.rtsbuilding.client.util.render.model;

public record NineSliceRegion(
        SpriteRegion region,
        int border
) {

    
    public static NineSliceRegion fullTheme(TextureInfo texture, int regionH, int border) {
        SpriteRegion fullRegion = new SpriteRegion(texture, 0, 0, texture.halfWidth(), regionH);
        return new NineSliceRegion(fullRegion, border);
    }

    

    
    public NineSliceRegion withTheme() {
        return new NineSliceRegion(region.withTheme(), border);
    }

    
    public NineSliceRegion withVOffset(int yOffset) {
        return new NineSliceRegion(region.withVOffset(yOffset), border);
    }
}
