package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止 UI 独立 source set 只进入发布 JAR、却从 NeoForge 开发运行的模组类加载空间消失。 */
class UiDevRuntimeClasspathContractTest {
    @Test
    void uiCoreAndKitAreRegisteredAsNeoForgeModSourcesForDevRuns() throws Exception {
        String uiBuild = Files.readString(Path.of("gradle/rts-ui.gradle"));

        assertTrue(uiBuild.contains("sourceSet(sourceSets.uiCore)"),
                "uiCore 必须注册进 neoForge.mods；否则按 G 构造 BuilderScreen 时找不到 PointerCapture");
        assertTrue(uiBuild.contains("sourceSet(sourceSets.uiKit)"),
                "uiKit 必须注册进 neoForge.mods；否则生产 UI 首次解析共享布局/主题时会崩溃");
        assertTrue(uiBuild.contains("verifyUiDevModSources"),
                "需要构建期门禁实际检查 NeoForge dev mod source 输出，不能只检查发布 JAR");
        assertTrue(uiBuild.contains("test {")
                        && uiBuild.contains("compileClasspath += sourceSets.uiCore.output + sourceSets.uiKit.output"),
                "普通 compileTestJava 也必须看到 Core/Kit；否则测试引用生产 UI 时会缺类");
    }
}
