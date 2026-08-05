package com.rtsbuilding.rtsbuilding.common.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically creates collision/selection boxes for blocks from their model
 * JSON files.
 * <p>
 * Modeled after {@code ModelJsonVoxelShapeParser} from Honey Jar Resources: each
 * element's {@code from}/{@code to} corners (pixel coordinates 0–16) are read
 * from the model's {@code elements} array, converted into an {@link AABB}, and
 * OR-combined into a single {@link VoxelShape}. Shapes are cached per model and
 * per facing, and elements may opt out of the collision box via a
 * {@code custom_data.skip_collision} marker or a {@code #nocollide} name prefix.
 */
public final class RtsModelShapeParser {

    private static final String ASSETS_PATH = "/assets/";

    private static final Map<String, List<ElementData>> modelCache = new ConcurrentHashMap<>();
    private static final Map<String, Map<Direction, VoxelShape>> shapeCache = new ConcurrentHashMap<>();

    private RtsModelShapeParser() {
    }

    /**
     * Builds (or returns cached) the collision/selection shape for a model.
     *
     * @param modId      The namespace of the model, e.g. {@code "rtsbuilding"}.
     * @param modelPath  The model path relative to {@code assets/<modId>/}, e.g. {@code "models/block/energy_bank.json"}.
     * @param direction  The facing the shape should be rotated for.
     *
     * @return The combined {@link VoxelShape}; falls back to a full block when
     *         the model cannot be parsed.
     */
    public static VoxelShape createShapeFromModel(String modId, String modelPath, Direction direction) {
        String cacheKey = buildCacheKey(modId, modelPath);
        Map<Direction, VoxelShape> dirCache = shapeCache.get(cacheKey);
        if (dirCache != null) {
            VoxelShape cached = dirCache.get(direction);
            if (cached != null) {
                return cached;
            }
        }
        VoxelShape shape = generateShape(modId, modelPath, direction);
        shapeCache.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>()).put(direction, shape);
        return shape;
    }

    /** Clears all caches (e.g. on resource reload). */
    public static void clearAllCache() {
        modelCache.clear();
        shapeCache.clear();
    }

    private static VoxelShape generateShape(String modId, String modelPath, Direction direction) {
        List<ElementData> elements = getModelElements(modId, modelPath);
        if (elements.isEmpty()) {
            return Shapes.block();
        }
        List<VoxelShape> shapes = new ArrayList<>();
        for (ElementData element : elements) {
            if (element.skipCollision()) {
                continue;
            }
            double[] from = transformPoint(element.from(), direction);
            double[] to = transformPoint(element.to(), direction);
            shapes.add(createElementShape(from, to));
        }
        return RtsVoxelShapeUtils.combine(shapes);
    }

    private static VoxelShape createElementShape(double[] from, double[] to) {
        double minX = Math.min(from[0], to[0]) / 16.0;
        double minY = Math.min(from[1], to[1]) / 16.0;
        double minZ = Math.min(from[2], to[2]) / 16.0;
        double maxX = Math.max(from[0], to[0]) / 16.0;
        double maxY = Math.max(from[1], to[1]) / 16.0;
        double maxZ = Math.max(from[2], to[2]) / 16.0;
        return Shapes.create(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    // ==================== 缓存管理 ====================

    private static String buildCacheKey(String modId, String modelPath) {
        return modId + ":" + modelPath;
    }

    // ==================== 模型解析 ====================

    private static List<ElementData> getModelElements(String modId, String modelPath) {
        String cacheKey = buildCacheKey(modId, modelPath);
        if (modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }
        List<ElementData> elements = parseModelElements(modId, modelPath);
        modelCache.put(cacheKey, elements);
        return elements;
    }

    private static List<ElementData> parseModelElements(String modId, String modelPath) {
        List<ElementData> elements = new ArrayList<>();
        JsonObject json = loadModelJson(modId, modelPath);
        if (json == null) {
            return elements;
        }
        JsonArray elementsArray = json.getAsJsonArray("elements");
        if (elementsArray == null) {
            return elements;
        }
        for (JsonElement element : elementsArray) {
            JsonObject elementObj = element.getAsJsonObject();
            JsonArray fromArray = elementObj.getAsJsonArray("from");
            JsonArray toArray = elementObj.getAsJsonArray("to");
            if (fromArray != null && toArray != null && fromArray.size() == 3 && toArray.size() == 3) {
                double[] from = jsonArrayToDoubleArray(fromArray);
                double[] to = jsonArrayToDoubleArray(toArray);
                elements.add(new ElementData(from, to, shouldSkipCollision(elementObj)));
            }
        }
        return elements;
    }

    @Nullable
    private static JsonObject loadModelJson(String modId, String modelPath) {
        String resourcePath = ASSETS_PATH + modId + "/" + modelPath;
        try (InputStream inputStream = RtsModelShapeParser.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(inputStream)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks whether an element opts out of the collision box, either via
     * {@code custom_data.skip_collision} or a {@code #nocollide} name prefix.
     */
    private static boolean shouldSkipCollision(JsonObject elementObj) {
        if (elementObj.has("custom_data")) {
            JsonObject customData = elementObj.getAsJsonObject("custom_data");
            if (customData.has("skip_collision")) {
                return customData.get("skip_collision").getAsBoolean();
            }
        }
        if (elementObj.has("name")) {
            return elementObj.get("name").getAsString().startsWith("#nocollide");
        }
        return false;
    }

    private static double[] jsonArrayToDoubleArray(JsonArray jsonArray) {
        double[] array = new double[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            array[i] = jsonArray.get(i).getAsDouble();
        }
        return array;
    }

    // ==================== 方向变换 ====================

    /**
     * Rotates a pixel-space point according to the facing, mirroring how block
     * models rotate (south/west/east are handled; up/down keep the horizontal base).
     */
    private static double[] transformPoint(double[] point, Direction facing) {
        double x = point[0];
        double y = point[1];
        double z = point[2];
        return switch (facing) {
            case SOUTH -> new double[]{16 - x, y, 16 - z};
            case WEST -> new double[]{z, y, 16 - x};
            case EAST -> new double[]{16 - z, y, x};
            case UP -> new double[]{x, 16 - z, y};
            case DOWN -> new double[]{x, z, 16 - y};
            default -> new double[]{x, y, z};
        };
    }

    // ==================== 数据类 ====================

    /**
     * A single model element: pixel-space corners plus an opt-out flag.
     */
    public record ElementData(double[] from, double[] to, boolean skipCollision) {
    }

    // ==================== 缓存生成器 ====================

    /**
     * Instance-level cached shape generator for a specific block model.
     */
    public static class CachedShapeGenerator {

        private final String modId;
        private final String modelPath;
        private final Map<Direction, VoxelShape> instanceCache = new EnumMap<>(Direction.class);

        public CachedShapeGenerator(String modId, String modelPath) {
            this.modId = modId;
            this.modelPath = modelPath;
        }

        /**
         * @return The cached shape for the given facing, parsing on first use.
         */
        public VoxelShape getShape(Direction direction) {
            return instanceCache.computeIfAbsent(direction,
                    dir -> createShapeFromModel(modId, modelPath, dir));
        }

        public void clearCache() {
            instanceCache.clear();
        }
    }
}
