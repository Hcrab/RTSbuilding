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
        assertTrue(build.contains("workingDir file('run/clean-client')"),
                "runClient 不得复用整合包或其他 smoke test 的游戏目录");
        assertTrue(count(build, "languageVersion.set(JavaLanguageVersion.of(8))") >= 3,
                "portable 自测、服务端和客户端运行任务都必须显式使用 Java 8");
        assertTrue(Files.isRegularFile(Path.of("runClient-1.12.2.bat")),
                "必须保留可双击启动的 1.12.2 客户端入口");
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
