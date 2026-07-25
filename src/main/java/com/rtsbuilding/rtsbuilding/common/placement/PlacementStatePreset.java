package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 待放置方块的轻量 BlockState 预设。
 *
 * <p>网络只传经过长度和字符限制的“属性=值”，服务端再针对实际放下来的方块解析。
 * 本类不接受方块 ID、方块实体数据或任意 NBT，因此它与世界中已有方块的旋转请求完全分离。</p>
 */
public final class PlacementStatePreset {
    public static final int MAX_ENCODED_LENGTH = 256;
    private static final int MAX_PROPERTIES = 8;
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_]{1,32}");

    private PlacementStatePreset() {
    }

    public static String withValue(String encoded, String propertyName, String valueName) {
        Map<String, String> values = decode(encoded);
        if (isToken(propertyName) && isToken(valueName)) {
            values.put(propertyName, valueName);
        }
        return encode(values);
    }

    public static String sanitize(String encoded) {
        return encode(decode(encoded));
    }

    /**
     * 从中键选中的世界方块复制玩家可安全预选的放置属性。
     *
     * <p>这里只复制方向、轴、上下半部、上下半砖、附着面和 16 段角度；
     * 不复制方块实体、含水状态或其他可能改变物品数量/结构的数据。</p>
     */
    public static String fromBlockState(BlockState state) {
        if (state == null || state.isAir()) {
            return "";
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            Comparable<?> value = state.getValue(property);
            String valueName = propertyValueName(property, value);
            if (isAllowed(state, property, valueName)) {
                values.put(property.getName(), valueName);
            }
        }
        return encode(values);
    }

    public static BlockState apply(BlockState state, String encoded) {
        if (state == null || encoded == null || encoded.isBlank()) {
            return state;
        }
        BlockState result = state;
        for (Map.Entry<String, String> entry : decode(encoded).entrySet()) {
            Property<?> property = result.getProperties().stream()
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst()
                    .orElse(null);
            if (property != null && isAllowed(result, property, entry.getValue())) {
                result = applyValue(result, property, entry.getValue());
            }
        }
        return result;
    }

    private static boolean isAllowed(BlockState state, Property<?> property, String valueName) {
        Class<?> valueClass = property.getValueClass();
        if (Direction.class.isAssignableFrom(valueClass)
                || Direction.Axis.class.isAssignableFrom(valueClass)
                || Half.class.isAssignableFrom(valueClass)
                || AttachFace.class.isAssignableFrom(valueClass)) {
            return true;
        }
        if (SlabType.class.isAssignableFrom(valueClass)) {
            return state.getBlock() instanceof SlabBlock && !"double".equals(valueName);
        }
        return property instanceof IntegerProperty
                && "rotation".equals(property.getName());
    }

    private static <T extends Comparable<T>> BlockState applyValue(
            BlockState state, Property<T> property, String valueName) {
        Optional<T> value = property.getValue(valueName);
        return value.map(candidate -> state.setValue(property, candidate)).orElse(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(Property property, Comparable value) {
        return property.getName(value);
    }

    private static Map<String, String> decode(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return values;
        }
        String bounded = encoded.length() > MAX_ENCODED_LENGTH
                ? encoded.substring(0, MAX_ENCODED_LENGTH)
                : encoded;
        for (String pair : bounded.split(";")) {
            int split = pair.indexOf('=');
            if (split <= 0 || split >= pair.length() - 1) {
                continue;
            }
            String name = pair.substring(0, split);
            String value = pair.substring(split + 1);
            if (isToken(name) && isToken(value)) {
                values.put(name, value);
                if (values.size() >= MAX_PROPERTIES) {
                    break;
                }
            }
        }
        return values;
    }

    private static String encode(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() > 0) {
                result.append(';');
            }
            String next = entry.getKey() + "=" + entry.getValue();
            if (result.length() + next.length() > MAX_ENCODED_LENGTH) {
                break;
            }
            result.append(next);
        }
        return result.toString();
    }

    private static boolean isToken(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }
}
