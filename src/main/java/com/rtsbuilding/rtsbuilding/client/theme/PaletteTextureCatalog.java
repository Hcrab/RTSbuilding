package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import net.minecraft.resources.ResourceLocation;

/** Palette 单源纹理目录；路径必须直接指向 Legacy / 贡献者原素材。 */
public final class PaletteTextureCatalog {
    public static ResourceLocation topBar(TopBarTypes.TopBarButtonId id) {
        if (id == null) return null;
        String key;
        switch (id) {
            case INTERACT: key = "mode_interact"; break;
            case LINK: key = "mode_link"; break;
            case FUNNEL: key = "mode_funnel"; break;
            case ROTATE: key = "mode_rotate"; break;
            case QUICK_BUILD: key = "quick_build"; break;
            case QUEST_DETECT: key = "quest_detect"; break;
            case CHUNK_VIEW: key = "chunk_view"; break;
            case RANGE_CULLING: key = "filter_block"; break;
            case GUIDE: key = "guide"; break;
            case DEVELOPER: key = "developer"; break;
            case GEAR: key = "settings_gear"; break;
            default: return null;
        }
        return ResourceLocation.tryParse(
                "rtsbuilding:textures/gui/topbar/" + key + "_hover.png");
    }

    public static ResourceLocation quickBuild(String key) {
        return ResourceLocation.tryParse(
                "rtsbuilding:textures/gui/new_2nd_icons/" + key + ".png");
    }

    private PaletteTextureCatalog() {
    }
}
