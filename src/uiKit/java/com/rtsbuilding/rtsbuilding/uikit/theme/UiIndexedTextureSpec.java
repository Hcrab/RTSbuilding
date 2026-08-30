package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Palette 单源纹理的精确索引色说明。
 *
 * <p>索引匹配使用完整 ARGB 等值，不做近似色、容差或“看起来像”推断；任何未知非透明像素
 * 都会让烘焙失败，从而避免损坏资源悄悄产生错误主题。</p>
 */
public final class UiIndexedTextureSpec {
    public enum Role {
        BACKGROUND,
        DARK_EDGE,
        GLYPH,
        GLYPH_SHADOW,
        SUCCESS,
        SUCCESS_DARK,
        ERROR,
        ERROR_DARK,
        BUTTON_HIGHLIGHT,
        BUTTON_CORNER,
        BUTTON_SHADOW,
        INDICATOR_OUTSIDE,
        INDICATOR_HIGHLIGHT,
        INDICATOR_FILL,
        INDICATOR_SHADOW
    }

    /** PR #133 图标和其顶栏 hover 帧使用的三色索引规范。 */
    public static final UiIndexedTextureSpec PR133_THREE_TONE = builder()
            .index(0xFF445468, Role.BACKGROUND)
            .index(0xFF1A202A, Role.DARK_EDGE)
            .index(0xFFA6CCF2, Role.GLYPH)
            .build();

    /**
     * PR #133 终端右侧按钮及其预处理 24×24 衍生素材的三色规范。
     *
     * <p>与图标三色表不同，这三种颜色表达的是上/左高光、内部填充与下/右暗边，
     * 因此必须保留为三个不同的几何角色，不能当作普通图标 glyph 处理。</p>
     */
    public static final UiIndexedTextureSpec CONTRIBUTOR_TERMINAL_BUTTON = builder()
            .index(0xFF536679, Role.BUTTON_HIGHLIGHT)
            .index(0xFF324153, Role.BACKGROUND)
            .index(0xFF1A202A, Role.BUTTON_SHADOW)
            .build();

    /**
     * 合成终端排序按钮的完整五色索引规范。
     *
     * <p>前三色来自 PR #133 的按钮本体，后两色来自 v2 排序图标。它们只负责换色，
     * 不允许烘焙器移动、缩放或重组任何像素。</p>
     */
    public static final UiIndexedTextureSpec V2_TERMINAL_SORT_BUTTON = builder()
            .index(0xFF536679, Role.BUTTON_HIGHLIGHT)
            .index(0xFF324153, Role.BACKGROUND)
            .index(0xFF1A202A, Role.BUTTON_SHADOW)
            .index(0xFFC3C2D0, Role.GLYPH)
            .index(0xFF7F7E8E, Role.GLYPH_SHADOW)
            .build();

    /** quest_detect 保留绿/红双状态语义，不把它们压扁成普通 glyph。 */
    public static final UiIndexedTextureSpec PR133_QUEST = builder()
            .index(0xFF445468, Role.BACKGROUND)
            .index(0xFF1A202A, Role.DARK_EDGE)
            .index(0xFFA6CCF2, Role.GLYPH)
            .index(0xFF7CE478, Role.SUCCESS)
            .index(0xFF0C2612, Role.SUCCESS_DARK)
            .index(0xFFECA2A2, Role.ERROR)
            .index(0xFF332323, Role.ERROR_DARK)
            .build();

    /**
     * Legacy {@code general/default_button.png} 的完整索引表。
     *
     * <p>这里不是重画按钮：四个状态块的每一种原始颜色都只被归入“高光、填充、
     * 转角、阴影”之一。烘焙器只替换颜色，像素位置、倒角和四状态结构必须原样保留。</p>
     */
    public static final UiIndexedTextureSpec LEGACY_DEFAULT_BUTTON = builder()
            .index(0xFF536679, Role.BUTTON_HIGHLIGHT)
            .index(0xFF222A34, Role.BACKGROUND)
            .index(0xFF202732, Role.BUTTON_CORNER)
            .index(0xFF0D1015, Role.BUTTON_SHADOW)
            .index(0xFFA6CCF2, Role.BUTTON_HIGHLIGHT)
            .index(0xFF445468, Role.BACKGROUND)
            .index(0xFF404E64, Role.BUTTON_CORNER)
            .index(0xFF1A202A, Role.BUTTON_SHADOW)
            .index(0xFF3E723C, Role.BUTTON_HIGHLIGHT)
            .index(0xFF163117, Role.BACKGROUND)
            .index(0xFF112E16, Role.BUTTON_CORNER)
            .index(0xFF061309, Role.BUTTON_SHADOW)
            .index(0xFF7CE478, Role.BUTTON_HIGHLIGHT)
            .index(0xFF2C622E, Role.BACKGROUND)
            .index(0xFF225C2C, Role.BUTTON_CORNER)
            .index(0xFF0C2612, Role.BUTTON_SHADOW)
            .build();

    /** Quick Build 右栏小开关的 Legacy 三状态原图集；禁止用矩形重画其倒角。 */
    public static final UiIndexedTextureSpec LEGACY_MODE_BUTTON = builder()
            .index(0xFF0D1117, Role.INDICATOR_OUTSIDE)
            .index(0xFF788C9F, Role.INDICATOR_HIGHLIGHT)
            .index(0xFF54616E, Role.INDICATOR_FILL)
            .index(0xFF363F47, Role.INDICATOR_SHADOW)
            .index(0xFFB4D2EE, Role.INDICATOR_HIGHLIGHT)
            .index(0xFF7E91A5, Role.INDICATOR_FILL)
            .index(0xFF536679, Role.INDICATOR_OUTSIDE)
            .index(0xFFE0FFDA, Role.INDICATOR_HIGHLIGHT)
            .index(0xFFDAFFDC, Role.INDICATOR_HIGHLIGHT)
            .index(0xFFB3FF9C, Role.INDICATOR_FILL)
            .index(0xFF72BA70, Role.INDICATOR_SHADOW)
            .build();

    /**
     * 设置页开关完整沿用 Legacy {@code general/switch_button.png} 的像素结构。
     *
     * <p>Palette 主题只能替换这些索引色对应的语义颜色，不能另画轨道、旋钮或改变四态图集排布。</p>
     */
    public static final UiIndexedTextureSpec LEGACY_SETTINGS_SWITCH = builder()
            .index(0xFF0D1117, Role.INDICATOR_OUTSIDE)
            .index(0xFF363F47, Role.INDICATOR_SHADOW)
            .index(0xFF54616E, Role.INDICATOR_FILL)
            .index(0xFF696D88, Role.BACKGROUND)
            .index(0xFF72BA70, Role.INDICATOR_SHADOW)
            .index(0xFF7692AC, Role.BACKGROUND)
            .index(0xFF788C9F, Role.INDICATOR_HIGHLIGHT)
            .index(0xFF7E91A5, Role.INDICATOR_FILL)
            .index(0xFF878FA5, Role.BUTTON_HIGHLIGHT)
            .index(0xFFB3FF9C, Role.INDICATOR_FILL)
            .index(0xFFB4D2EE, Role.INDICATOR_HIGHLIGHT)
            .index(0xFFD4FFFF, Role.INDICATOR_HIGHLIGHT)
            .index(0xFFE0FFDA, Role.INDICATOR_HIGHLIGHT)
            .build();

    private final Map<Integer, Role> roles;

    private UiIndexedTextureSpec(Map<Integer, Role> roles) {
        this.roles = Collections.unmodifiableMap(roles);
    }

    public Role role(int sourceArgb) {
        return roles.get(sourceArgb);
    }

    public Map<Integer, Role> roles() {
        return roles;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Integer, Role> roles = new LinkedHashMap<Integer, Role>();

        public Builder index(int sourceArgb, Role role) {
            if (role == null) throw new IllegalArgumentException("role must not be null");
            if ((sourceArgb >>> 24) == 0) {
                throw new IllegalArgumentException("transparent pixels do not need an index role");
            }
            if (roles.put(sourceArgb, role) != null) {
                throw new IllegalArgumentException(String.format(
                        "duplicate indexed color #%08X", sourceArgb));
            }
            return this;
        }

        public UiIndexedTextureSpec build() {
            if (roles.isEmpty()) throw new IllegalStateException("indexed texture spec is empty");
            return new UiIndexedTextureSpec(new LinkedHashMap<Integer, Role>(roles));
        }
    }
}
