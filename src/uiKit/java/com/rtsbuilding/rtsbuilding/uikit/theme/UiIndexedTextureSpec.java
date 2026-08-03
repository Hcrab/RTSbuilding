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
        SUCCESS,
        SUCCESS_DARK,
        ERROR,
        ERROR_DARK
    }

    /** PR #133 图标和其顶栏 hover 帧使用的三色索引规范。 */
    public static final UiIndexedTextureSpec PR133_THREE_TONE = builder()
            .index(0xFF445468, Role.BACKGROUND)
            .index(0xFF1A202A, Role.DARK_EDGE)
            .index(0xFFA6CCF2, Role.GLYPH)
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
