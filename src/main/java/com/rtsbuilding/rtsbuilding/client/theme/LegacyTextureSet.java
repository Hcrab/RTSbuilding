package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.resources.ResourceLocation;

/** 一组公开且完整的 Legacy Direct 四状态资源路径。 */
public final class LegacyTextureSet {
    private final ResourceLocation inactive;
    private final ResourceLocation hover;
    private final ResourceLocation active;
    private final ResourceLocation pressed;

    public LegacyTextureSet(ResourceLocation inactive, ResourceLocation hover,
                            ResourceLocation active, ResourceLocation pressed) {
        if (inactive == null || hover == null || active == null || pressed == null) {
            throw new IllegalArgumentException("Legacy texture set must contain all four states");
        }
        this.inactive = inactive;
        this.hover = hover;
        this.active = active;
        this.pressed = pressed;
    }

    public ResourceLocation resolve(UiTextureState state) {
        switch (state) {
            case HOVER: return hover;
            case ACTIVE: return active;
            case PRESSED: return pressed;
            case INACTIVE:
            default: return inactive;
        }
    }
}
