package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家可见组件族与其最低语义令牌覆盖清单。
 *
 * <p>该目录不保存颜色；它是设置预览、JSON 校验和架构测试共同使用的“不能漏掉谁”清单。</p>
 */
public final class UiThemeCoverageCatalog {
    public enum ComponentFamily {
        GLOBAL_CHROME,
        TOP_BAR,
        BOTTOM_BAR,
        STORAGE,
        CRAFT_TERMINAL,
        QUICK_BUILD,
        WORKFLOW,
        BLUEPRINT,
        SETTINGS,
        MODAL,
        GUIDE_AND_TOOLS,
        HUD_OVERLAY,
        WORLD_RENDERING
    }

    private static final Map<ComponentFamily, Set<UiThemeToken>> REQUIRED;

    static {
        EnumMap<ComponentFamily, Set<UiThemeToken>> map =
                new EnumMap<ComponentFamily, Set<UiThemeToken>>(ComponentFamily.class);
        Set<UiThemeToken> completeSurface = Collections.unmodifiableSet(
                EnumSet.allOf(UiThemeToken.class));
        for (ComponentFamily family : ComponentFamily.values()) {
            map.put(family, completeSurface);
        }
        REQUIRED = Collections.unmodifiableMap(map);
    }

    public static Set<UiThemeToken> required(ComponentFamily family) {
        Set<UiThemeToken> tokens = REQUIRED.get(family);
        if (tokens == null) throw new IllegalArgumentException("unknown component family: " + family);
        return tokens;
    }

    public static Map<ComponentFamily, Set<UiThemeToken>> snapshot() {
        return REQUIRED;
    }

    private UiThemeCoverageCatalog() {
    }
}
