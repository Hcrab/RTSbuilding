package com.rtsbuilding.rtsbuilding.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止独立 UI source set 进入发布 JAR、却从 1.12 ForgeGradle 开发运行 classpath 消失。 */
class UiDevRuntimeClasspathContractTest {
    @Test
    void uiCoreAndKitAreOnLegacyMainAndTestClasspaths() throws Exception {
        String uiBuild = Files.readString(Path.of("gradle/rts-ui.gradle"));

        assertTrue(uiBuild.contains("main {")
                        && occurrences(uiBuild,
                        "compileClasspath += sourceSets.uiCore.output + sourceSets.uiKit.output") >= 2,
                "1.12 ForgeGradle 的 main 与 test 编译 classpath 都必须看见 UI Core/Kit 输出");
        assertTrue(occurrences(uiBuild,
                        "runtimeClasspath += sourceSets.uiCore.output + sourceSets.uiKit.output") >= 2,
                "1.12 ForgeGradle 的 main 与 test 运行 classpath 都必须看见 UI Core/Kit 输出");
        assertTrue(uiBuild.contains("test {"),
                "普通测试 source set 必须显式继承 Core/Kit 输出");
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
