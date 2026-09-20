package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 范围选区旧配置的唯一迁移边界。
 *
 * <p>ConfigSpec 会为缺失键提供默认值，单看 IntValue 无法分辨“玩家已写入”与“框架
 * 自动补齐”。因此迁移先读取原始 TOML 的键存在性，再按 canonical 值、旧体积、旧轴
 * 和旧 size 的规则生成一次归一结果。正常 getter 只读取归一后的新键，不重复解释旧值。</p>
 */
final class MiningSelectionConfigMigration {
    private static final int DEFAULT_LEGACY_AXIS = 36;
    private static final int DEFAULT_CANONICAL_AXIS = 64;
    private static final Pattern INTEGER_LINE = Pattern.compile(
            "(?m)^\\s*[\\\"']?([A-Za-z0-9_.-]+)[\\\"']?\\s*=\\s*(-?\\d+)(?:\\s*#.*)?$");

    private MiningSelectionConfigMigration() {
    }

    record Source(boolean canonicalVolumePresent, int canonicalVolume,
                  boolean canonicalSizeXPresent, int canonicalSizeX,
                  boolean canonicalSizeYPresent, int canonicalSizeY,
                  boolean canonicalSizeZPresent, int canonicalSizeZ,
                  boolean legacyVolumePresent, int legacyVolume,
                  boolean legacyWidthPresent, int legacyWidth,
                  boolean legacyHeightPresent, int legacyHeight,
                  boolean legacyDepthPresent, int legacyDepth,
                  boolean legacySizePresent, int legacySize,
                  boolean legacyTargetsPresent, int legacyTargets) {

        static Source fromRawToml(String toml) {
            Map<String, Integer> values = new HashMap<>();
            if (toml != null) {
                String section = "";
                for (String line : toml.split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        section = trimmed.substring(1, trimmed.length() - 1).trim();
                        continue;
                    }
                    Matcher matcher = INTEGER_LINE.matcher(line);
                    if (matcher.find()) {
                        String key = matcher.group(1);
                        try {
                            values.put(section.isEmpty() || key.indexOf('.') >= 0 ? key : section + "." + key,
                                    Integer.parseInt(matcher.group(2)));
                        } catch (NumberFormatException ignored) {
                            // 损坏的旧行不能阻止服务端继续加载并使用安全默认值。
                        }
                    }
                }
            }
            return new Source(
                    values.containsKey("mining.maxSelectionVolume"), value(values, "mining.maxSelectionVolume"),
                    values.containsKey("mining.maxSelectionSizeX"), value(values, "mining.maxSelectionSizeX"),
                    values.containsKey("mining.maxSelectionSizeY"), value(values, "mining.maxSelectionSizeY"),
                    values.containsKey("mining.maxSelectionSizeZ"), value(values, "mining.maxSelectionSizeZ"),
                    values.containsKey("mining.areaMineMaxVolume"), value(values, "mining.areaMineMaxVolume"),
                    values.containsKey("mining.areaMineMaxWidth"), value(values, "mining.areaMineMaxWidth"),
                    values.containsKey("mining.areaMineMaxHeight"), value(values, "mining.areaMineMaxHeight"),
                    values.containsKey("mining.areaMineMaxDepth"), value(values, "mining.areaMineMaxDepth"),
                    values.containsKey("mining.areaMineMaxSize"), value(values, "mining.areaMineMaxSize"),
                    values.containsKey("mining.areaDestroyMaxTargets"), value(values, "mining.areaDestroyMaxTargets"));
        }

        private static int value(Map<String, Integer> values, String key) {
            return values.getOrDefault(key, 0);
        }

        static Source values(int canonicalVolume, int canonicalX, int canonicalY, int canonicalZ,
                             int legacyVolume, int legacyWidth, int legacyHeight, int legacyDepth,
                             int legacySize, int legacyTargets) {
            return new Source(canonicalVolume > 0, canonicalVolume,
                    canonicalX > 0, canonicalX, canonicalY > 0, canonicalY,
                    canonicalZ > 0, canonicalZ, legacyVolume > 0, legacyVolume,
                    legacyWidth > 0, legacyWidth, legacyHeight > 0, legacyHeight,
                    legacyDepth > 0, legacyDepth, legacySize > 0, legacySize,
                    legacyTargets > 0, legacyTargets);
        }
    }

    record Result(int volume, int sizeX, int sizeY, int sizeZ, boolean changed) {
    }

    static Result resolve(Source source) {
        if (source == null) {
            return new Result(MiningLimits.DEFAULT_VOLUME, DEFAULT_CANONICAL_AXIS,
                    DEFAULT_CANONICAL_AXIS, DEFAULT_CANONICAL_AXIS, true);
        }
        boolean canonicalVolume = source.canonicalVolumePresent() && source.canonicalVolume() > 0;
        int volume = canonicalVolume ? MiningLimits.clampVolume(source.canonicalVolume())
                : source.legacyVolumePresent() && source.legacyVolume() > 0
                ? MiningLimits.clampVolume(source.legacyVolume()) : MiningLimits.DEFAULT_VOLUME;

        // 没有任何正的旧键时代表全新文件或全 0 占位符；不能把旧 schema 的 36
        // 错套到新安装。只有确实看见旧挖掘 volume/轴/size 值时，缺失轴才回退 36。
        // 旧区域破坏 targets 只提供数量回退，不能凭空引入挖掘的旧轴长限制。
        boolean legacyValuePresent = (source.legacyVolumePresent() && source.legacyVolume() > 0)
                || (source.legacyWidthPresent() && source.legacyWidth() > 0)
                || (source.legacyHeightPresent() && source.legacyHeight() > 0)
                || (source.legacyDepthPresent() && source.legacyDepth() > 0)
                || (source.legacySizePresent() && source.legacySize() > 0);
        int fallback = source.legacySizePresent() && source.legacySize() > 0
                ? clampAxis(source.legacySize()) : (legacyValuePresent ? DEFAULT_LEGACY_AXIS : DEFAULT_CANONICAL_AXIS);
        int x = canonicalAxis(source.canonicalSizeXPresent(), source.canonicalSizeX(),
                canonicalVolume, source.legacyWidthPresent(), source.legacyWidth(), fallback);
        int y = canonicalAxis(source.canonicalSizeYPresent(), source.canonicalSizeY(),
                canonicalVolume, source.legacyHeightPresent(), source.legacyHeight(), fallback);
        int z = canonicalAxis(source.canonicalSizeZPresent(), source.canonicalSizeZ(),
                canonicalVolume, source.legacyDepthPresent(), source.legacyDepth(), fallback);

        // areaDestroyMaxTargets 只有在完全没有新/旧体积线索时作为最后的历史兼容回退。
        if (!canonicalVolume && !legacyValuePresent
                && source.legacyTargetsPresent() && source.legacyTargets() > 0) {
            volume = MiningLimits.clampVolume(source.legacyTargets());
        }

        boolean changed = !source.canonicalVolumePresent() || !source.canonicalSizeXPresent()
                || !source.canonicalSizeYPresent() || !source.canonicalSizeZPresent()
                || source.canonicalVolume() != volume || source.canonicalSizeX() != x
                || source.canonicalSizeY() != y || source.canonicalSizeZ() != z;
        return new Result(volume, x, y, z, changed);
    }

    private static int canonicalAxis(boolean present, int value, boolean canonicalVolume,
                                     boolean legacyPresent, int legacyValue, int fallback) {
        if (present && value > 0) return clampAxis(value);
        if (canonicalVolume) return DEFAULT_CANONICAL_AXIS;
        return legacyPresent && legacyValue > 0 ? clampAxis(legacyValue) : fallback;
    }

    private static int clampAxis(int value) {
        return Math.max(1, value);
    }

    /** 保留旧调用方的体积返回形状；新调用方应使用 {@link #resolve(Source)} 取得四个值。 */
    static int resolve(int canonicalVolume, int legacyVolume, int legacyWidth, int legacyHeight,
                       int legacyDepth, int legacySize, int legacyTargets) {
        return resolve(Source.values(canonicalVolume, 0, 0, 0, legacyVolume, legacyWidth,
                legacyHeight, legacyDepth, legacySize, legacyTargets)).volume();
    }

    static boolean needsMigration(Source source) {
        return resolve(source).changed();
    }

    static boolean needsMigration(int canonicalVolume) {
        return canonicalVolume <= 0;
    }
}
