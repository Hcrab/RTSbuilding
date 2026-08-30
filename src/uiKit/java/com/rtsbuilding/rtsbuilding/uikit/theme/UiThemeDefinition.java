package com.rtsbuilding.rtsbuilding.uikit.theme;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 完整、不可变且不依赖 Minecraft 的主题定义。
 *
 * <p>该类只保存已经验证过的数据，不读取文件、不创建 GPU 资源，也不负责选择活动主题。</p>
 */
public final class UiThemeDefinition {
    private static final Pattern NAMESPACED_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private final String id;
    private final String nameKey;
    private final String author;
    private final String descriptionKey;
    private final UiThemeRenderMode renderMode;
    private final String textureSet;
    private final boolean editable;
    private final Map<UiThemeToken, UiColor> tokens;
    private final Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> components;

    public UiThemeDefinition(String id, String nameKey, String author, String descriptionKey,
                             UiThemeRenderMode renderMode, String textureSet, boolean editable,
                             Map<UiThemeToken, UiColor> tokens) {
        this(id, nameKey, author, descriptionKey, renderMode, textureSet, editable,
                tokens, defaultComponents(tokens));
    }

    public UiThemeDefinition(String id, String nameKey, String author, String descriptionKey,
                             UiThemeRenderMode renderMode, String textureSet, boolean editable,
                             Map<UiThemeToken, UiColor> tokens,
                             Map<UiThemeCoverageCatalog.ComponentFamily,
                                     Map<UiThemeToken, UiColor>> components) {
        if (id == null || !NAMESPACED_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("theme id must be namespaced: " + id);
        }
        this.id = id;
        this.nameKey = requireText(nameKey, "nameKey");
        this.author = requireText(author, "author");
        this.descriptionKey = requireText(descriptionKey, "descriptionKey");
        this.renderMode = require(renderMode, "renderMode");
        this.textureSet = requireText(textureSet, "textureSet");
        this.editable = editable;

        EnumMap<UiThemeToken, UiColor> copy = new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
        if (tokens != null) copy.putAll(tokens);
        for (UiThemeToken token : UiThemeToken.values()) {
            UiColor color = copy.get(token);
            if (color == null) {
                throw new IllegalArgumentException("missing theme token: " + token.serializedId());
            }
            if (!token.translucentAllowed() && color.alpha() == 0) {
                throw new IllegalArgumentException("critical theme token is transparent: "
                        + token.serializedId());
            }
        }
        if (copy.size() != UiThemeToken.values().length) {
            throw new IllegalArgumentException("theme contains unsupported token keys");
        }
        if (renderMode == UiThemeRenderMode.LEGACY_DIRECT && editable) {
            throw new IllegalArgumentException("Legacy Direct theme must be immutable");
        }
        this.tokens = Collections.unmodifiableMap(copy);
        this.components = validateComponents(components);
    }

    public String id() { return id; }
    public String nameKey() { return nameKey; }
    public String author() { return author; }
    public String descriptionKey() { return descriptionKey; }
    public UiThemeRenderMode renderMode() { return renderMode; }
    public String textureSet() { return textureSet; }
    public boolean editable() { return editable; }
    public UiColor color(UiThemeToken token) { return tokens.get(token); }
    public Map<UiThemeToken, UiColor> tokens() { return tokens; }

    public UiColor componentColor(UiThemeCoverageCatalog.ComponentFamily family,
                                  UiThemeToken token) {
        Map<UiThemeToken, UiColor> colors = components.get(family);
        UiColor color = colors == null ? null : colors.get(token);
        return color == null ? color(token) : color;
    }

    public Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> components() {
        return components;
    }

    private static Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>>
    defaultComponents(Map<UiThemeToken, UiColor> tokens) {
        EnumMap<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> result =
                new EnumMap<UiThemeCoverageCatalog.ComponentFamily,
                        Map<UiThemeToken, UiColor>>(UiThemeCoverageCatalog.ComponentFamily.class);
        if (tokens == null) return result;
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            EnumMap<UiThemeToken, UiColor> colors = new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
            for (UiThemeToken token : UiThemeCoverageCatalog.required(family)) {
                colors.put(token, tokens.get(token));
            }
            result.put(family, colors);
        }
        return result;
    }

    private static Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>>
    validateComponents(Map<UiThemeCoverageCatalog.ComponentFamily,
            Map<UiThemeToken, UiColor>> supplied) {
        EnumMap<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> result =
                new EnumMap<UiThemeCoverageCatalog.ComponentFamily,
                        Map<UiThemeToken, UiColor>>(UiThemeCoverageCatalog.ComponentFamily.class);
        if (supplied == null) throw new IllegalArgumentException("components must not be null");
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            Map<UiThemeToken, UiColor> input = supplied.get(family);
            if (input == null) {
                throw new IllegalArgumentException("missing component family: " + family.name());
            }
            Set<UiThemeToken> required = UiThemeCoverageCatalog.required(family);
            if (!input.keySet().equals(required)) {
                java.util.EnumSet<UiThemeToken> missing = java.util.EnumSet.noneOf(UiThemeToken.class);
                for (UiThemeToken token : required) if (!input.containsKey(token)) missing.add(token);
                throw new IllegalArgumentException("component family " + family.name()
                        + " must exactly cover " + required + "; missing=" + missing);
            }
            EnumMap<UiThemeToken, UiColor> copy = new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
            for (UiThemeToken token : required) {
                UiColor color = input.get(token);
                if (color == null) throw new IllegalArgumentException(
                        "missing component color: " + family.name() + "." + token.serializedId());
                copy.put(token, color);
            }
            result.put(family, Collections.unmodifiableMap(copy));
        }
        if (supplied.size() != UiThemeCoverageCatalog.ComponentFamily.values().length) {
            throw new IllegalArgumentException("components contain unknown families");
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }
}
