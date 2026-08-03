package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RTS UI 可换色系统的完整核心语义目录。
 *
 * <p>枚举名是 Java API，{@link #serializedId()} 是稳定 JSON API。新增令牌必须同步更新五套
 * 内置主题和覆盖测试；不得在加载时用 Legacy 静默补齐用户主题缺项。</p>
 */
public enum UiThemeToken {
    CANVAS("canvas"),
    TOP_BAR("top_bar"),
    BOTTOM_BAR("bottom_bar"),
    SURFACE("surface"),
    SURFACE_RAISED("surface_raised"),
    SURFACE_SUNKEN("surface_sunken"),

    BORDER_STRONG("border_strong"),
    BORDER_SOFT("border_soft"),
    DIVIDER("divider"),
    FOCUS_RING("focus_ring"),

    TEXT_PRIMARY("text_primary"),
    TEXT_SECONDARY("text_secondary"),
    TEXT_MUTED("text_muted"),
    TEXT_ON_ACCENT("text_on_accent"),

    CONTROL_IDLE("control_idle"),
    CONTROL_HOVER("control_hover"),
    CONTROL_PRESSED("control_pressed"),
    CONTROL_SELECTED("control_selected"),
    CONTROL_DISABLED("control_disabled"),

    ICON_PRIMARY("icon_primary"),
    ICON_MUTED("icon_muted"),
    ICON_ON_ACCENT("icon_on_accent"),

    ACCENT_PRIMARY("accent_primary"),
    ACCENT_SECONDARY("accent_secondary"),
    SUCCESS("success"),
    WARNING("warning"),
    ERROR("error"),
    DESTRUCTIVE("destructive"),

    SLOT_IDLE("slot_idle"),
    SLOT_HOVER("slot_hover"),
    SLOT_SELECTED("slot_selected"),
    SLOT_MISSING("slot_missing"),

    SCROLLBAR_TRACK("scrollbar_track"),
    SCROLLBAR_THUMB("scrollbar_thumb"),
    SCROLLBAR_THUMB_HOVER("scrollbar_thumb_hover"),

    WORLD_SELECTION("world_selection"),
    WORLD_SELECTION_FILL("world_selection_fill", true),
    WORLD_LINK_ENDPOINT("world_link_endpoint"),
    WORLD_INVALID("world_invalid"),
    WORLD_GHOST_VALID("world_ghost_valid", true),
    WORLD_GHOST_INVALID("world_ghost_invalid", true);

    private static final Map<String, UiThemeToken> BY_SERIALIZED_ID;

    static {
        Map<String, UiThemeToken> values = new LinkedHashMap<String, UiThemeToken>();
        for (UiThemeToken token : UiThemeToken.values()) {
            if (values.put(token.serializedId, token) != null) {
                throw new IllegalStateException("duplicate theme token id: " + token.serializedId);
            }
        }
        BY_SERIALIZED_ID = Collections.unmodifiableMap(values);
    }

    private final String serializedId;
    private final boolean translucentAllowed;

    UiThemeToken(String serializedId) {
        this(serializedId, false);
    }

    UiThemeToken(String serializedId, boolean translucentAllowed) {
        this.serializedId = serializedId;
        this.translucentAllowed = translucentAllowed;
    }

    public String serializedId() {
        return serializedId;
    }

    public boolean translucentAllowed() {
        return translucentAllowed;
    }

    public static UiThemeToken bySerializedId(String id) {
        return BY_SERIALIZED_ID.get(id);
    }

    public static Map<String, UiThemeToken> serializedCatalog() {
        return BY_SERIALIZED_ID;
    }
}
