package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 待放置方块的轻量 1.12 {@link IBlockState} 预设。
 *
 * <p>网络只传经过长度、字符和属性白名单约束的“属性=值”。服务端再针对实际
 * 方块的 {@link IProperty#getAllowedValues()} 解析；无法由当前状态表达的值保持不变。</p>
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

    /** 只复制方向、轴、上下半部、半砖、附着面和 16 段角度。 */
    public static String fromBlockState(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR) {
            return "";
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (IProperty<?> property : state.getPropertyKeys()) {
            Comparable<?> value = getValue(state, property);
            String valueName = propertyValueName(property, value);
            if (isAllowed(state, property, valueName)) {
                values.put(property.getName(), valueName);
            }
        }
        return encode(values);
    }

    public static IBlockState apply(IBlockState state, String encoded) {
        if (state == null || encoded == null || encoded.trim().isEmpty()) {
            return state;
        }
        IBlockState result = state;
        for (Map.Entry<String, String> entry : decode(encoded).entrySet()) {
            IProperty<?> property = findProperty(result, entry.getKey());
            if (property != null && isAllowed(result, property, entry.getValue())) {
                result = applyValue(result, property, entry.getValue());
            }
        }
        return result;
    }

    private static boolean isAllowed(IBlockState state, IProperty<?> property, String valueName) {
        if (!isToken(property.getName()) || !isToken(valueName) || !hasValue(property, valueName)) {
            return false;
        }
        String name = property.getName();
        if (("facing".equals(name) || "horizontal_facing".equals(name))
                && EnumFacing.class.isAssignableFrom(property.getValueClass())) {
            return true;
        }
        if ("axis".equals(name) || "horizontal_axis".equals(name)) {
            return "x".equals(valueName) || "y".equals(valueName) || "z".equals(valueName);
        }
        if ("half".equals(name)) {
            boolean halfValue = "top".equals(valueName) || "bottom".equals(valueName)
                    || "upper".equals(valueName) || "lower".equals(valueName);
            if (!halfValue) return false;
            if (state.getBlock() instanceof BlockSlab) {
                return !((BlockSlab) state.getBlock()).isDouble();
            }
            return true;
        }
        if ("slab_type".equals(name)) {
            return state.getBlock() instanceof BlockSlab
                    && !((BlockSlab) state.getBlock()).isDouble()
                    && ("top".equals(valueName) || "bottom".equals(valueName));
        }
        if ("attach_face".equals(name)) {
            return "floor".equals(valueName) || "wall".equals(valueName)
                    || "ceiling".equals(valueName);
        }
        if ("rotation".equals(name) && Integer.class.isAssignableFrom(property.getValueClass())) {
            try {
                int rotation = Integer.parseInt(valueName);
                return rotation >= 0 && rotation < 16;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static IProperty<?> findProperty(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals(name)) return property;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasValue(IProperty property, String valueName) {
        Collection<? extends Comparable> allowed = property.getAllowedValues();
        for (Comparable candidate : allowed) {
            if (valueName.equals(property.getName(candidate))) return true;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState applyValue(IBlockState state, IProperty property, String valueName) {
        Collection<? extends Comparable> allowed = property.getAllowedValues();
        for (Comparable candidate : allowed) {
            if (valueName.equals(property.getName(candidate))) {
                return state.withProperty(property, candidate);
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> getValue(IBlockState state, IProperty property) {
        return state.getValue(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(IProperty property, Comparable value) {
        return property.getName(value);
    }

    private static Map<String, String> decode(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) {
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
            if (result.length() > 0) result.append(';');
            String next = entry.getKey() + "=" + entry.getValue();
            if (result.length() + next.length() > MAX_ENCODED_LENGTH) break;
            result.append(next);
        }
        return result.toString();
    }

    private static boolean isToken(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }
}
