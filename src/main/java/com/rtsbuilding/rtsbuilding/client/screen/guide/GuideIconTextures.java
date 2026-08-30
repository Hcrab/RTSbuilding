package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiIcon;
import net.minecraft.resources.ResourceLocation;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 指南主题图标的单一纹理目录。
 *
 * <p>能够复用正式顶栏语义的条目直接使用既有四态资源；其余条目使用透明单色纹理，
 * 由 {@link GuidePanel} 在边界处着色。本类不负责布局、输入或主题页状态。</p>
 */
public final class GuideIconTextures {
    public record Entry(ResourceLocation texture, boolean tinted) {
    }

    public static Entry entry(GuideUiIcon icon) {
        return switch (icon) {
            case HAND -> fixed(TOPBAR_INTERACT_ACTIVE);
            case LINK -> fixed(TOPBAR_LINK_ACTIVE);
            case FUNNEL -> fixed(TOPBAR_FUNNEL_ACTIVE);
            case ROTATE -> fixed(TOPBAR_ROTATE_ACTIVE);
            case BUILD -> fixed(TOPBAR_QUICK_BUILD_ACTIVE);
            case PICKAXE -> fixed(TOPBAR_ULTIMINE_ACTIVE);
            case GRID -> fixed(TOPBAR_CHUNK_VIEW_ACTIVE);
            case SEARCH -> tinted("search");
            case SORT -> tinted("sort");
            case CLOCK -> tinted("clock");
            case DROPLET -> tinted("droplet");
            case PIN -> tinted("pin");
            case CRAFT -> tinted("craft");
            case SLIDER -> tinted("slider");
            case TOGGLE -> tinted("toggle");
            case GEAR -> fixed(TOPBAR_GEAR_ACTIVE);
        };
    }

    private static Entry fixed(ResourceLocation texture) {
        return new Entry(texture, false);
    }

    private static Entry tinted(String id) {
        return new Entry(ResourceLocation.tryParse(
                "rtsbuilding:textures/gui/guide/" + id + ".png"), true);
    }

    private GuideIconTextures() {
    }
}
