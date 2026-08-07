package com.rtsbuilding.rtsbuilding.client.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRenderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeRuntime;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 严格的 schemaVersion 1 Palette 主题 JSON 编解码器。 */
public final class UiThemeJsonCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_SHORT_TEXT = 128;
    private static final int MAX_DESCRIPTION = 512;
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{8}");
    private static final Set<String> ROOT_FIELDS = set("schemaVersion", "id", "name", "author",
            "description", "renderMode", "textureSet", "tokens", "components");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    public UiThemeDefinition decode(String json) {
        if (json == null) throw new IllegalArgumentException("theme JSON must not be null");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("theme JSON exceeds 1 MiB");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid theme JSON", failure);
        }
        requireDepth(parsed, 0);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("theme root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        requireExactFields(root, ROOT_FIELDS, "root");
        if (requiredInt(root, "schemaVersion") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schemaVersion");
        }
        String id = requiredText(root, "id", MAX_SHORT_TEXT);
        if (UiThemeRuntime.registry().contains(id)) {
            throw new IllegalArgumentException("user theme cannot replace built-in id: " + id);
        }
        if (!"palette".equals(requiredText(root, "renderMode", 32))) {
            throw new IllegalArgumentException("user theme renderMode must be palette");
        }
        EnumMap<UiThemeToken, UiColor> tokens = readTokens(requiredObject(root, "tokens"));
        Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> components =
                readComponents(requiredObject(root, "components"), tokens);
        UiThemeDefinition definition = new UiThemeDefinition(id,
                requiredText(root, "name", MAX_SHORT_TEXT),
                requiredText(root, "author", MAX_SHORT_TEXT),
                requiredText(root, "description", MAX_DESCRIPTION), UiThemeRenderMode.PALETTE,
                requiredText(root, "textureSet", MAX_SHORT_TEXT), true, tokens, components);
        UiThemeValidator.validateContrast(definition);
        return definition;
    }

    public String encode(UiThemeDefinition definition) {
        if (definition == null || definition.renderMode() != UiThemeRenderMode.PALETTE) {
            throw new IllegalArgumentException("only Palette themes can be exported");
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", definition.id());
        root.addProperty("name", definition.nameKey());
        root.addProperty("author", definition.author());
        root.addProperty("description", definition.descriptionKey());
        root.addProperty("renderMode", "palette");
        root.addProperty("textureSet", definition.textureSet());
        JsonObject tokens = new JsonObject();
        for (UiThemeToken token : UiThemeToken.values()) {
            tokens.addProperty(token.serializedId(), color(definition.color(token)));
        }
        root.add("tokens", tokens);
        JsonObject components = new JsonObject();
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            JsonObject colors = new JsonObject();
            for (UiThemeToken token : UiThemeCoverageCatalog.required(family)) {
                colors.addProperty(token.serializedId(), color(definition.componentColor(family, token)));
            }
            components.add(familyId(family), colors);
        }
        root.add("components", components);
        return PRETTY.toJson(root) + System.lineSeparator();
    }

    private static EnumMap<UiThemeToken, UiColor> readTokens(JsonObject object) {
        requireExactFields(object, UiThemeToken.serializedCatalog().keySet(), "tokens");
        EnumMap<UiThemeToken, UiColor> result = new EnumMap<>(UiThemeToken.class);
        for (UiThemeToken token : UiThemeToken.values()) {
            result.put(token, parseColor(requiredText(object, token.serializedId(), 9),
                    "tokens." + token.serializedId()));
        }
        return result;
    }

    private static Map<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>>
    readComponents(JsonObject object, Map<UiThemeToken, UiColor> tokens) {
        Set<String> familyIds = new HashSet<>();
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) familyIds.add(familyId(family));
        requireExactFields(object, familyIds, "components");
        EnumMap<UiThemeCoverageCatalog.ComponentFamily, Map<UiThemeToken, UiColor>> result =
                new EnumMap<>(UiThemeCoverageCatalog.ComponentFamily.class);
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            String familyId = familyId(family);
            JsonObject colors = requiredObject(object, familyId);
            Set<String> expected = new HashSet<>();
            for (UiThemeToken token : UiThemeCoverageCatalog.required(family)) expected.add(token.serializedId());
            requireExactFields(colors, expected, "components." + familyId);
            EnumMap<UiThemeToken, UiColor> resolved = new EnumMap<>(UiThemeToken.class);
            for (UiThemeToken token : UiThemeCoverageCatalog.required(family)) {
                String path = "components." + familyId + "." + token.serializedId();
                String value = requiredText(colors, token.serializedId(), MAX_SHORT_TEXT);
                if (value.startsWith("$")) {
                    UiThemeToken reference = UiThemeToken.bySerializedId(value.substring(1));
                    if (reference == null) throw new IllegalArgumentException(path + " references unknown core token");
                    resolved.put(token, tokens.get(reference));
                } else {
                    resolved.put(token, parseColor(value, path));
                }
            }
            result.put(family, resolved);
        }
        return result;
    }

    private static UiColor parseColor(String value, String path) {
        if (!COLOR.matcher(value).matches()) throw new IllegalArgumentException(path + " must be #AARRGGBB");
        return new UiColor((int) Long.parseUnsignedLong(value.substring(1), 16));
    }

    private static String color(UiColor color) {
        return String.format(Locale.ROOT, "#%08X", color.toArgb());
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return value.getAsJsonObject();
    }

    private static String requiredText(JsonObject object, String key, int maxLength) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String text = value.getAsString();
        if (text.trim().isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(key + " length must be 1.." + maxLength);
        }
        return text;
    }

    private static int requiredInt(JsonObject object, String key) {
        JsonElement value = object.get(key);
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return value.getAsInt();
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    private static void requireExactFields(JsonObject object, Set<String> expected, String path) {
        Set<String> actual = object.keySet();
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(expected);
            throw new IllegalArgumentException(path + " fields mismatch; missing=" + missing + ", unknown=" + unknown);
        }
    }

    private static void requireDepth(JsonElement element, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("theme JSON nesting is too deep");
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) requireDepth(entry.getValue(), depth + 1);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) requireDepth(child, depth + 1);
        }
    }

    private static String familyId(UiThemeCoverageCatalog.ComponentFamily family) {
        return family.name().toLowerCase(Locale.ROOT);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
