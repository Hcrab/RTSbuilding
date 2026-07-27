package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiIcon;
import net.minecraft.util.ResourceLocation;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 指南主题图标的单一纹理目录。
 *
 * <p>能够复用正式顶栏语义的条目直接使用既有四态资源；其余条目使用透明单色纹理，
 * 由 {@link GuidePanel} 在边界处着色。本类不负责布局、输入或主题页状态。</p>
 */
public final class GuideIconTextures {
    public static final class Entry {
        private final ResourceLocation texture;
        private final boolean tinted;

        private Entry(ResourceLocation texture, boolean tinted) {
            this.texture = texture;
            this.tinted = tinted;
        }

        public ResourceLocation texture() { return this.texture; }
        public boolean tinted() { return this.tinted; }
    }

    public static Entry entry(GuideUiIcon icon) {
        switch (icon) {
            case HAND: return fixed(TOPBAR_INTERACT_ACTIVE);
            case LINK: return fixed(TOPBAR_LINK_ACTIVE);
            case FUNNEL: return fixed(TOPBAR_FUNNEL_ACTIVE);
            case ROTATE: return fixed(TOPBAR_ROTATE_ACTIVE);
            case BUILD: return fixed(TOPBAR_QUICK_BUILD_ACTIVE);
            case PICKAXE: return fixed(TOPBAR_ULTIMINE_ACTIVE);
            case GRID: return fixed(TOPBAR_CHUNK_VIEW_ACTIVE);
            case SEARCH: return tinted("search");
            case SORT: return tinted("sort");
            case CLOCK: return tinted("clock");
            case DROPLET: return tinted("droplet");
            case PIN: return tinted("pin");
            case CRAFT: return tinted("craft");
            case SLIDER: return tinted("slider");
            case TOGGLE: return tinted("toggle");
            case GEAR: return fixed(TOPBAR_GEAR_ACTIVE);
            default: throw new IllegalArgumentException("Unsupported guide icon: " + icon);
        }
    }

    private static Entry fixed(ResourceLocation texture) {
        return new Entry(texture, false);
    }

    private static Entry tinted(String id) {
        return new Entry(new ResourceLocation(
                "rtsbuilding", "textures/gui/guide/" + id + ".png"), true);
    }

    private GuideIconTextures() {
    }
}
