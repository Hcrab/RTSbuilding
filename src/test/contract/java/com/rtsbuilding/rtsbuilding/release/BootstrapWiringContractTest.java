package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.12.2 公共端、客户端和构建入口中不能靠人工记忆维持的基础接线。 */
class BootstrapWiringContractTest {
    @Test
    void commonAndClientBootstrapsOwnTheirRequiredRegistrations() throws IOException {
        String mod = read("src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");
        assertInOrder(mod,
                "Config.initialize(",
                "RtsPayloadRegistrar.register();",
                "RtsCreativeTabs.register();",
                "RtsBlocks.register();",
                "RtsItems.register();",
                "RtsEntities.register(this);",
                "MinecraftForge.EVENT_BUS.register(gameEvents);",
                "initializeClientSide();");
        assertInOrder(mod,
                "ServiceRegistry.init();",
                "RtsAPIImpl.init();",
                "RtsPipelineRegistration.registerAll();",
                "RtsOperationDiagnostics.install();");

        String client = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/bootstrap/RtsClientModEvents.java");
        assertInOrder(client,
                "ClientKeyMappings.register();",
                "RenderingRegistry.registerEntityRenderingHandler(",
                "MinecraftForge.EVENT_BUS.register(RtsCameraRenderSync.INSTANCE);",
                "MinecraftForge.EVENT_BUS.register(RtsVisualOverlayRenderer.class);",
                "initializeMovementModes();",
                "registered = true;");
    }

    @Test
    void buildPluginsArePinnedAndCleanClientHasAnIsolatedRunDirectory() throws IOException {
        String build = read("build.gradle");
        assertFalse(build.matches("(?s).*version\\s+['\\\"][^'\\\"]*\\+['\\\"].*"),
                "Gradle 插件不得使用动态版本，否则无人值守构建会随上游发布漂移");
        assertTrue(build.contains("tasks.named('runClient', JavaExec)"),
                "runClient 必须保留独立开发实例配置");
        assertTrue(build.contains(".getOrElse('clean-client')")
                        && build.contains("workingDir file(\"run/${clientInstance}\")"),
                "runClient 不得复用整合包或其他 smoke test 的游戏目录");
        assertTrue(count(build, "languageVersion.set(JavaLanguageVersion.of(8))") >= 3,
                "portable 自测、服务端和客户端运行任务都必须显式使用 Java 8");
        assertTrue(build.contains("attribute_map['MixinConfigs']")
                        && build.contains("propertyStringList('mixin_early_configs')"),
                "Manifest 只能提前声明 Minecraft 基类 Mixin，第三方目标必须留给晚期发现器");
        assertTrue(build.contains("mixin_annotation_processor_version")
                        && build.contains("annotationProcessor (\"zone.rong:mixinbooter:"),
                "MixinBooter 运行库与旧版 refmap 处理器必须分开固定，不能再次产出无映射 Mixin JAR");
        assertTrue(build.contains("verifyMixinRuntimeArtifacts")
                        && build.contains("Mixin refmap is missing from the final JAR")
                        && build.contains("func_70071_h_")
                        && build.contains("func_75145_c"),
                "build 必须检查最终 JAR 的加载器、配置和远程菜单 SRG 映射，而不是只看源码");
        String mixinLoader = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/bootstrap/RtsMixinConfigLoader.java");
        assertTrue(mixinLoader.contains("implements IFMLLoadingPlugin, IEarlyMixinLoader")
                        && mixinLoader.contains("mixins.rtsbuilding.json")
                        && !mixinLoader.contains("mixins.rtsbuilding_jei.json")
                        && !mixinLoader.contains("mixins.rtsbuilding_jade.json"),
                "不能只依赖 Manifest；MM 的 MixinBooter 5 必须在目标类加载前显式排队配置");
        String lateMixinLoader = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/bootstrap/RtsLateMixinConfigLoader.java");
        assertTrue(lateMixinLoader.contains("implements ILateMixinLoader")
                        && lateMixinLoader.contains("mixins.rtsbuilding_jei.json")
                        && lateMixinLoader.contains("mixins.rtsbuilding_jade.json")
                        && !lateMixinLoader.contains("\"mixins.rtsbuilding.json\""),
                "第三方模组 Mixin 必须晚期排队，且不得把 Minecraft 基类配置带回晚期阶段");
        String gradleProperties = read("gradle.properties");
        assertTrue(gradleProperties.contains("is_coremod = true")
                        && gradleProperties.contains(
                        "coremod_plugin_class_name = com.rtsbuilding.rtsbuilding.bootstrap.RtsMixinConfigLoader"),
                "IEarlyMixinLoader 必须注册为 Forge coremod，MixinBooter 5 才会发现它");
        String mixinConfig = read("src/main/resources/mixins.rtsbuilding.json");
        assertTrue(mixinConfig.contains("RemoteBasePlayerContainerMixin")
                        && mixinConfig.contains("RemoteContainerPlayerMixin"),
                "1.12 的基类与服务端玩家两道容器存活检查必须同时接线");
        assertTrue(Files.isRegularFile(Path.of("runClient-1.12.2.bat")),
                "必须保留可双击启动的 1.12.2 客户端入口");
        assertTrue(Files.isRegularFile(Path.of("gradle/gradle-daemon-jvm.properties")),
                "Gradle wrapper 必须声明 Java 25 Daemon，不得要求开发者反复手改 JAVA_HOME");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void assertInOrder(String source, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = source.indexOf(token, previous + 1);
            assertTrue(current > previous, token + " 缺失或生命周期顺序错误");
            previous = current;
        }
    }

    private static int count(String source, String token) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(token, cursor)) >= 0) {
            count++;
            cursor += token.length();
        }
        return count;
    }
}
