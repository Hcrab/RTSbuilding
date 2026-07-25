package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 统一负责把持久化中的维度 ID 转换为 {@link ResourceKey}。
 *
 * <p>该适配器刻意不引用 {@code Registries.DIMENSION}。这样任务编解码器既能在
 * 完整游戏运行时使用，也能在不启动 Minecraft 注册表的纯 JVM 测试中使用。
 * 这里只建立资源键身份，不负责确认目标维度当前是否已加载；世界解析仍由调用者负责。
 */
public final class RtsDimensionKeys {
    private static final ResourceKey<Registry<Level>> LEVEL_REGISTRY =
            ResourceKey.createRegistryKey(new ResourceLocation("minecraft", "dimension"));

    private RtsDimensionKeys() {
    }

    public static ResourceKey<Level> create(ResourceLocation dimensionId) {
        return ResourceKey.create(LEVEL_REGISTRY, dimensionId);
    }

    @Nullable
    public static ResourceKey<Level> parse(@Nullable String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(dimensionId);
        return parsed == null ? null : create(parsed);
    }
}
