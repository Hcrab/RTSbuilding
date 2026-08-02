package com.rtsbuilding.rtsbuilding.client.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 严格读取 GUI 兼容案例清单。
 *
 * <p>这个类明确不猜测未知字段或 adapter。拼错的测试配置应在启动时失败，而不是把夹具错误伪装成
 * {@code NO_OPEN}。它也不访问注册表；方块是否真实存在由运行中的服务端 setup adapter 回报。</p>
 */
final class RtsGuiCompatSuiteLoader {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "suiteId", "stableTicks", "openTimeoutTicks", "cases");
    private static final Set<String> CASE_FIELDS = Set.of(
            "id", "blockId", "distanceProfile", "distance", "depth", "setupAdapter",
            "setupWaitTicks", "interactionItemId", "expectedMenuRegex", "expectedScreenRegex");
    private static final Set<String> SUPPORTED_ADAPTERS = Set.of(
            "single_block",
            "vanilla_chest",
            "vanilla_crafting",
            "vanilla_furnace",
            "vanilla_enchanting",
            "vanilla_anvil",
            "vanilla_smithing",
            "vanilla_stonecutter",
            "vanilla_brewing",
            "vanilla_grindstone",
            "oritech_assembler",
            "oritech_centrifuge",
            "powah_reactor");

    private RtsGuiCompatSuiteLoader() {
    }

    static RtsGuiCompatSuite load(Path path) throws IOException {
        JsonElement parsed = JsonParser.parseString(Files.readString(path));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("GUI compat suite root must be an object: " + path);
        }
        JsonObject root = parsed.getAsJsonObject();
        rejectUnknown(root, ROOT_FIELDS, "root");
        String suiteId = requiredString(root, "suiteId");
        int stableTicks = positiveInt(root, "stableTicks", 60);
        int openTimeoutTicks = positiveInt(root, "openTimeoutTicks", 120);
        JsonArray rawCases = root.getAsJsonArray("cases");
        if (rawCases == null || rawCases.isEmpty()) {
            throw new IllegalArgumentException("GUI compat suite must contain at least one case");
        }

        List<RtsGuiCompatCase> cases = new ArrayList<>();
        for (int index = 0; index < rawCases.size(); index++) {
            JsonElement element = rawCases.get(index);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("cases[" + index + "] must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            rejectUnknown(object, CASE_FIELDS, "cases[" + index + "]");
            String adapter = optionalString(object, "setupAdapter", "single_block");
            if (!SUPPORTED_ADAPTERS.contains(adapter)) {
                throw new IllegalArgumentException("Unsupported setupAdapter " + adapter
                        + " in cases[" + index + "]");
            }
            cases.add(new RtsGuiCompatCase(
                    requiredString(object, "id"),
                    requiredString(object, "blockId"),
                    resolveDistance(object),
                    optionalString(object, "depth", "OPEN_STABLE"),
                    adapter,
                    positiveInt(object, "setupWaitTicks", 40),
                    optionalString(object, "interactionItemId", ""),
                    optionalString(object, "expectedMenuRegex", "DISCOVER_THEN_LOCK"),
                    optionalString(object, "expectedScreenRegex", "DISCOVER_THEN_LOCK")));
        }
        long distinctIds = cases.stream().map(RtsGuiCompatCase::id).distinct().count();
        if (distinctIds != cases.size()) {
            throw new IllegalArgumentException("GUI compat suite contains duplicate case ids");
        }
        return new RtsGuiCompatSuite(suiteId, stableTicks, openTimeoutTicks, List.copyOf(cases));
    }

    static RtsGuiCompatSuite single(RtsGuiCompatCase guiCase, int stableTicks, int openTimeoutTicks) {
        return new RtsGuiCompatSuite("single-case", stableTicks, openTimeoutTicks, List.of(guiCase));
    }

    private static int resolveDistance(JsonObject object) {
        if (object.has("distance")) {
            return positiveInt(object, "distance", 24);
        }
        return switch (optionalString(object, "distanceProfile", "FAR_24")) {
            case "FAR_24" -> 24;
            case "COLD_160" -> 160;
            default -> throw new IllegalArgumentException("Unsupported distanceProfile in case: "
                    + object.get("distanceProfile"));
        };
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String location) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown GUI compat field " + location + "." + key);
            }
        }
    }

    private static String requiredString(JsonObject object, String key) {
        String value = optionalString(object, key, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing GUI compat field " + key);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString().trim();
    }

    private static int positiveInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        int value = element == null || element.isJsonNull() ? fallback : element.getAsInt();
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    record RtsGuiCompatSuite(String suiteId, int stableTicks, int openTimeoutTicks,
            List<RtsGuiCompatCase> cases) {
    }
}
