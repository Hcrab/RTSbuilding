package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止 UI 独立 source set 只进入发布 JAR，却从 Forge 开发运行的模组类加载空间消失。 */
class UiDevRuntimeClasspathContractTest {
    @Test
    void uiCoreAndKitAreRegisteredAsForgeModSourcesForDevRuns() throws Exception {
        String uiBuild = Files.readString(Path.of("gradle/rts-ui.gradle"));

        assertTrue(uiBuild.contains("minecraft.runs.configureEach"));
        assertTrue(uiBuild.contains("source sourceSets.uiCore"),
                "uiCore 必须注册进 Forge dev run 的模组源，否则运行时找不到 PointerCapture");
        assertTrue(uiBuild.contains("source sourceSets.uiKit"),
                "uiKit 必须注册进 Forge dev run 的模组源，否则生产 UI 无法解析共享布局和主题类");
        assertTrue(uiBuild.contains("verifyUiDevModSources"),
                "构建期必须检查 Forge dev mod source，不能只检查发布 JAR");
        assertTrue(uiBuild.contains("test {")
                        && uiBuild.contains("compileClasspath += sourceSets.uiCore.output + sourceSets.uiKit.output"),
                "普通测试编译也必须看到 Core/Kit 输出");
    }
}
