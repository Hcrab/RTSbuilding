package com.rtsbuilding.rtsbuilding.server.task.persistence;

import net.minecraft.util.ResourceLocation;

/**
 * 连接 1.21 名称化维度键与 1.12.2 整数维度 ID 的持久化边界。
 *
 * <p>存档层继续保存字符串，从而不丢失旧快照里的名称化维度；新建的 1.12.2
 * 任务使用规范十进制整数。三个原版维度可以双向迁移，模组维度必须由运行时
 * 注册表提供整数 ID，不能静默猜测。</p>
 */
public final class DimensionIdCodec {
    private DimensionIdCodec() {
    }

    public static String fromDimension(int dimensionId) {
        return Integer.toString(dimensionId);
    }

    public static boolean isCanonical(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            return Integer.toString(Integer.parseInt(value)).equals(value);
        } catch (NumberFormatException ignored) {
            try {
                return new ResourceLocation(value).toString().equals(value);
            } catch (RuntimeException invalidLocation) {
                return false;
            }
        }
    }

    public static int toDimension(String value) {
        if ("minecraft:overworld".equals(value)) return 0;
        if ("minecraft:the_nether".equals(value) || "minecraft:nether".equals(value)) return -1;
        if ("minecraft:the_end".equals(value) || "minecraft:end".equals(value)) return 1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(
                    "模组维度名称必须先通过 1.12.2 维度注册表解析为整数 ID: " + value, invalid);
        }
    }
}
