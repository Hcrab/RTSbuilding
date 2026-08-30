package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import net.minecraft.resources.ResourceLocation;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Legacy Direct 轨道的公开四状态纹理解析器。
 *
 * <p>本类只返回现有稳定资源路径。它不读取像素、不判断资源来自模组还是材质包，也不调用
 * Palette 烘焙器；因此标准 Minecraft 资源优先级会自然接管第三方 Legacy 材质包。</p>
 */
public final class LegacyStateTextureResolver {
    public static ResourceLocation topBar(TopBarTypes.TopBarButtonId id, UiTextureState state) {
        LegacyTextureSet set = topBarSet(id);
        return set == null ? null : set.resolve(state);
    }

    public static LegacyTextureSet topBarSet(TopBarTypes.TopBarButtonId id) {
        if (id == null) return null;
        switch (id) {
            case INTERACT:
                return set(TOPBAR_INTERACT_INACTIVE, TOPBAR_INTERACT_HOVER,
                        TOPBAR_INTERACT_ACTIVE, TOPBAR_INTERACT_PRESSED);
            case LINK:
                return set(TOPBAR_LINK_INACTIVE, TOPBAR_LINK_HOVER,
                        TOPBAR_LINK_ACTIVE, TOPBAR_LINK_PRESSED);
            case FUNNEL:
                return set(TOPBAR_FUNNEL_INACTIVE, TOPBAR_FUNNEL_HOVER,
                        TOPBAR_FUNNEL_ACTIVE, TOPBAR_FUNNEL_PRESSED);
            case ROTATE:
                return set(TOPBAR_ROTATE_INACTIVE, TOPBAR_ROTATE_HOVER,
                        TOPBAR_ROTATE_ACTIVE, TOPBAR_ROTATE_PRESSED);
            case QUICK_BUILD:
                return set(TOPBAR_QUICK_BUILD_INACTIVE, TOPBAR_QUICK_BUILD_HOVER,
                        TOPBAR_QUICK_BUILD_ACTIVE, TOPBAR_QUICK_BUILD_PRESSED);
            case QUEST_DETECT:
                return set(TOPBAR_QUEST_DETECT_INACTIVE, TOPBAR_QUEST_DETECT_HOVER,
                        TOPBAR_QUEST_DETECT_ACTIVE, TOPBAR_QUEST_DETECT_PRESSED);
            case CHUNK_VIEW:
                return set(TOPBAR_CHUNK_VIEW_INACTIVE, TOPBAR_CHUNK_VIEW_HOVER,
                        TOPBAR_CHUNK_VIEW_ACTIVE, TOPBAR_CHUNK_VIEW_PRESSED);
            case RANGE_CULLING:
                return set(TOPBAR_RANGE_CULLING_INACTIVE, TOPBAR_RANGE_CULLING_HOVER,
                        TOPBAR_RANGE_CULLING_ACTIVE, TOPBAR_RANGE_CULLING_PRESSED);
            case GUIDE:
                return set(TOPBAR_GUIDE_INACTIVE, TOPBAR_GUIDE_HOVER,
                        TOPBAR_GUIDE_ACTIVE, TOPBAR_GUIDE_PRESSED);
            case DEVELOPER:
                return set(TOPBAR_DEVELOPER_INACTIVE, TOPBAR_DEVELOPER_HOVER,
                        TOPBAR_DEVELOPER_ACTIVE, TOPBAR_DEVELOPER_PRESSED);
            case GEAR:
                return set(TOPBAR_GEAR_INACTIVE, TOPBAR_GEAR_HOVER,
                        TOPBAR_GEAR_ACTIVE, TOPBAR_GEAR_PRESSED);
            default:
                return null;
        }
    }

    private static LegacyTextureSet set(ResourceLocation inactive, ResourceLocation hover,
                                        ResourceLocation active, ResourceLocation pressed) {
        return new LegacyTextureSet(inactive, hover, active, pressed);
    }

    private LegacyStateTextureResolver() {
    }
}
