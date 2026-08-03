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
        map.put(ComponentFamily.GLOBAL_CHROME, set(
                UiThemeToken.CANVAS, UiThemeToken.SURFACE, UiThemeToken.SURFACE_RAISED,
                UiThemeToken.SURFACE_SUNKEN, UiThemeToken.BORDER_STRONG,
                UiThemeToken.BORDER_SOFT, UiThemeToken.DIVIDER, UiThemeToken.TEXT_PRIMARY,
                UiThemeToken.TEXT_SECONDARY, UiThemeToken.TEXT_MUTED,
                UiThemeToken.FOCUS_RING, UiThemeToken.CONTROL_DISABLED));
        map.put(ComponentFamily.TOP_BAR, set(
                UiThemeToken.TOP_BAR, UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.CONTROL_PRESSED, UiThemeToken.CONTROL_SELECTED,
                UiThemeToken.ICON_PRIMARY, UiThemeToken.ICON_MUTED,
                UiThemeToken.ICON_ON_ACCENT, UiThemeToken.TEXT_ON_ACCENT,
                UiThemeToken.ACCENT_PRIMARY, UiThemeToken.ACCENT_SECONDARY));
        map.put(ComponentFamily.BOTTOM_BAR, set(
                UiThemeToken.BOTTOM_BAR, UiThemeToken.SLOT_IDLE, UiThemeToken.SLOT_HOVER,
                UiThemeToken.SLOT_SELECTED, UiThemeToken.SLOT_MISSING,
                UiThemeToken.SCROLLBAR_TRACK, UiThemeToken.SCROLLBAR_THUMB));
        map.put(ComponentFamily.STORAGE, set(
                UiThemeToken.SURFACE, UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.SUCCESS, UiThemeToken.WARNING, UiThemeToken.ERROR,
                UiThemeToken.WORLD_LINK_ENDPOINT));
        map.put(ComponentFamily.CRAFT_TERMINAL, set(
                UiThemeToken.SURFACE, UiThemeToken.SURFACE_RAISED,
                UiThemeToken.SURFACE_SUNKEN, UiThemeToken.SLOT_IDLE,
                UiThemeToken.SLOT_HOVER, UiThemeToken.SLOT_MISSING,
                UiThemeToken.SCROLLBAR_TRACK, UiThemeToken.SCROLLBAR_THUMB));
        map.put(ComponentFamily.QUICK_BUILD, set(
                UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.CONTROL_PRESSED, UiThemeToken.CONTROL_SELECTED,
                UiThemeToken.ACCENT_PRIMARY, UiThemeToken.DESTRUCTIVE));
        map.put(ComponentFamily.WORKFLOW, set(
                UiThemeToken.CONTROL_IDLE, UiThemeToken.CONTROL_HOVER,
                UiThemeToken.SUCCESS, UiThemeToken.WARNING, UiThemeToken.ERROR,
                UiThemeToken.DESTRUCTIVE));
        map.put(ComponentFamily.BLUEPRINT, set(
                UiThemeToken.SURFACE, UiThemeToken.CONTROL_SELECTED,
                UiThemeToken.SUCCESS, UiThemeToken.WARNING, UiThemeToken.ERROR,
                UiThemeToken.WORLD_GHOST_VALID, UiThemeToken.WORLD_GHOST_INVALID));
        map.put(ComponentFamily.SETTINGS, set(
                UiThemeToken.SURFACE, UiThemeToken.CONTROL_IDLE,
                UiThemeToken.CONTROL_HOVER, UiThemeToken.CONTROL_SELECTED,
                UiThemeToken.FOCUS_RING, UiThemeToken.SCROLLBAR_TRACK,
                UiThemeToken.SCROLLBAR_THUMB_HOVER));
        map.put(ComponentFamily.MODAL, set(
                UiThemeToken.SURFACE_RAISED, UiThemeToken.TEXT_PRIMARY,
                UiThemeToken.TEXT_MUTED, UiThemeToken.CONTROL_IDLE,
                UiThemeToken.CONTROL_SELECTED, UiThemeToken.DESTRUCTIVE));
        map.put(ComponentFamily.GUIDE_AND_TOOLS, set(
                UiThemeToken.TEXT_PRIMARY, UiThemeToken.TEXT_SECONDARY,
                UiThemeToken.TEXT_MUTED, UiThemeToken.SUCCESS,
                UiThemeToken.WARNING, UiThemeToken.ERROR));
        map.put(ComponentFamily.HUD_OVERLAY, set(
                UiThemeToken.CANVAS, UiThemeToken.BORDER_STRONG,
                UiThemeToken.TEXT_PRIMARY, UiThemeToken.SUCCESS,
                UiThemeToken.WARNING, UiThemeToken.ERROR));
        map.put(ComponentFamily.WORLD_RENDERING, set(
                UiThemeToken.WORLD_SELECTION, UiThemeToken.WORLD_SELECTION_FILL,
                UiThemeToken.WORLD_LINK_ENDPOINT, UiThemeToken.WORLD_INVALID,
                UiThemeToken.WORLD_GHOST_VALID, UiThemeToken.WORLD_GHOST_INVALID));
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

    private static Set<UiThemeToken> set(UiThemeToken first, UiThemeToken... rest) {
        EnumSet<UiThemeToken> values = EnumSet.of(first, rest);
        return Collections.unmodifiableSet(values);
    }

    private UiThemeCoverageCatalog() {
    }
}
