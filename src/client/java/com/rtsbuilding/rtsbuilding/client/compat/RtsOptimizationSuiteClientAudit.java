package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * 本地 Fabric 优化全家桶的客户端启动期严格审计。
 *
 * <p>Fabric API 0.116.x（Minecraft 1.21.1）尚未提供后来版本的客户端 GameTest API，
 * 因此客户端渲染链不能伪装成服务端 GameTest。本类只在开发运行参数明确开启时执行：它负责
 * 确认全家桶确实加载、Sodium 目标类已接受 RTS Mixin，并验证范围剔除的核心判定；它不修改
 * 玩家世界、不持有界面状态，也不进入发布 JAR 的普通运行路径。</p>
 */
public final class RtsOptimizationSuiteClientAudit {
    private static final String ENABLE_PROPERTY = "rtsbuilding.optimizationSuite";
    private static final List<String> REQUIRED_MODS = List.of(
            "sodium",
            "lithium",
            "ferritecore",
            "immediatelyfast",
            "entityculling",
            "moreculling",
            "modernfix",
            "c2me",
            "noisium",
            "krypton",
            "cloth-config");
    private static final List<String> REQUIRED_RUNTIME_COMPONENTS = List.of(
            "cloth-basic-math",
            "c2me-base",
            "c2me-rewrites-chunk-system",
            "c2me-threading-lighting",
            "conditional-mixin",
            "mixinsquared",
            "net_lenni0451_reflect",
            "transition",
            "trender",
            "com_velocitypowered_velocity-native");

    private RtsOptimizationSuiteClientAudit() {
    }

    public static void verifyIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : REQUIRED_MODS) {
            require(loader.isModLoaded(modId), "缺少本地优化兼容模组：" + modId);
        }
        for (String modId : REQUIRED_RUNTIME_COMPONENTS) {
            require(loader.isModLoaded(modId), "优化模组的嵌套运行库没有进入 Loom 类路径：" + modId);
        }

        verifySodiumMixinHandlers();
        verifySodiumRendererInvalidationApi();
        verifyCullingSemantics();
        RtsbuildingMod.LOGGER.info(
                "Fabric 优化全家桶客户端审计通过：{} 个模组、{} 个运行组件，Sodium 范围剔除注入有效",
                REQUIRED_MODS.size(), REQUIRED_RUNTIME_COMPONENTS.size());
        RtsbuildingMod.LOGGER.info("RTS_OPTIMIZATION_SUITE_CLIENT_AUDIT=PASSED");
    }

    private static void verifySodiumMixinHandlers() {
        Class<?> levelSlice = loadClass("net.caffeinemc.mods.sodium.client.world.LevelSlice");
        List<String> methods = Arrays.stream(levelSlice.getDeclaredMethods()).map(Method::getName).toList();
        require(methods.stream().anyMatch(name -> name.contains("rtsbuilding$cullBlockState")),
                "RTS 方块状态剔除处理器未注入 Sodium LevelSlice");
        require(methods.stream().anyMatch(name -> name.contains("rtsbuilding$cullFluidState")),
                "RTS 流体状态剔除处理器未注入 Sodium LevelSlice");
        require(methods.stream().anyMatch(name -> name.contains("rtsbuilding$cullBlockEntity")),
                "RTS 方块实体剔除处理器未注入 Sodium LevelSlice");
    }

    private static void verifySodiumRendererInvalidationApi() {
        Class<?> renderer = loadClass("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
        require(hasPublicMethod(renderer, "instanceNullable"),
                "SodiumWorldRenderer 缺少 instanceNullable，范围剔除无法安全刷新网格");
        require(hasPublicMethod(renderer, "scheduleRebuildForBlockArea",
                        int.class, int.class, int.class, int.class, int.class, int.class, boolean.class),
                "SodiumWorldRenderer 缺少区域重建入口，范围剔除无法刷新网格");
    }

    private static void verifyCullingSemantics() {
        BlockPos center = new BlockPos(0, 64, 0);
        RtsCullingManager manager = new RtsCullingManager();
        manager.replaceWorldState(
                List.of(new RtsCullingBox(1, center.offset(-8, -4, -8), center.offset(8, 8, 8))),
                List.of(center));
        require(manager.shouldCullWorldBlock(center.offset(1, 0, 0)),
                "范围内普通方块没有被剔除");
        require(!manager.shouldCullWorldBlock(center),
                "显式恢复方块被错误剔除");
        require(!manager.shouldCullWorldBlock(center.offset(32, 0, 0)),
                "范围外方块被错误剔除");
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, RtsOptimizationSuiteClientAudit.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("优化兼容审计找不到运行时类：" + className, exception);
        }
    }

    private static boolean hasPublicMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            owner.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Fabric 优化全家桶客户端审计失败：" + message);
        }
    }
}
