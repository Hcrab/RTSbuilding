package com.rtsbuilding.rtsbuilding.release;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定多人开发时不能再退回手工 JAVA_HOME、复制模组和主菜单假烟测的基础设施。 */
class ClientRuntimeInfrastructureContractTest {
    @Test
    void gradleWrapperOwnsTheJava25DaemonRequirement() throws IOException {
        Path criteria = Path.of("gradle/gradle-daemon-jvm.properties");
        assertTrue(Files.isRegularFile(criteria));
        Properties properties = new Properties();
        try (var input = Files.newInputStream(criteria)) {
            properties.load(input);
        }

        assertEquals("25", properties.getProperty("toolchainVersion"));
        assertEquals("MICROSOFT", properties.getProperty("toolchainVendor"));
        assertTrue(properties.stringPropertyNames().stream()
                .anyMatch(key -> key.equals("toolchainUrl.WINDOWS.X86_64")));

        String buildLauncher = read("build-1.12.2.bat");
        String cleanLauncher = read("runClient-1.12.2.bat");
        assertFalse(buildLauncher.contains("JAVA_HOME"));
        assertFalse(cleanLauncher.contains("JAVA_HOME"));
        assertTrue(buildLauncher.contains("gradlew.bat"));
        assertTrue(cleanLauncher.contains("gradlew.bat"));
    }

    @Test
    void clientSmokeEntersAWorldAndCrossesTheOriginalCrashWindow() throws IOException {
        String reminder = read("src/main/java/com/rtsbuilding/rtsbuilding/client/compat/"
                + "RtsClientOnboardingReminder.java");
        String smoke = read("src/main/java/com/rtsbuilding/rtsbuilding/client/compat/"
                + "RtsClientStartupSmoke.java");
        String gradle = read("gradle/client-smoke.gradle");

        int reminderDelay = intConstant(reminder, "SHOW_DELAY_TICKS");
        int worldStable = intConstant(smoke, "WORLD_STABLE_TICKS");
        assertTrue(worldStable > reminderDelay,
                "客户端 smoke 必须跨过入门提醒延迟，才能覆盖最初的 ClientTick NPE");
        assertTrue(smoke.contains("instanceof GuiMainMenu"));
        assertTrue(smoke.contains("launchIntegratedServer("));
        assertTrue(smoke.contains("RtsClientPacketGateway.sendToggleCamera("));
        assertTrue(smoke.contains("ClientRtsController.get().isEnabled()"));
        assertTrue(smoke.contains("minecraft.shutdown()"));

        assertTrue(gradle.contains("tasks.register('runClientSmoke', JavaExec)"));
        assertTrue(gradle.contains("RTS_112_CLIENT_SMOKE PASS"));
        assertTrue(gradle.contains("'[Client thread/FATAL]'"));
        assertTrue(read("build-1.12.2.bat").contains("build clientCheck"));
        assertTrue(Files.isRegularFile(Path.of("runClientSmoke-1.12.2.bat")));
    }

    @Test
    void developmentAndE2euModsAreDeclaredAndLockedByGradle() throws IOException {
        String build = read("build.gradle");
        String development = read("gradle/client-development.gradle");
        String e2eu = read("gradle/e2eu.gradle");

        assertTrue(build.contains("'clean-client', 'dev-client', 'e2eu-client'"));
        assertTrue(build.contains("classpath += configurations.rtsDevelopmentMods"));
        assertTrue(development.contains("rtsDevelopmentMods(rfg.deobf("));
        assertTrue(development.contains("mezz.jei:jei_1.12.2:4.16.1.1013"));
        assertTrue(Files.isRegularFile(Path.of("runDevClient-1.12.2.bat")));

        assertTrue(e2eu.contains("configurations.e2euMods"));
        assertTrue(e2eu.contains("curse.maven:e2eu-"));
        assertTrue(e2eu.contains("duplicatesStrategy = DuplicatesStrategy.FAIL"));
        List<String> lock = Files.readAllLines(Path.of("gradle/e2eu-1.3.9.2.lock")).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toList());
        assertEquals(249, lock.size());
        assertEquals(249, lock.stream().distinct().count());
    }

    private static int intConstant(String source, String name) {
        String marker = "int " + name + " = ";
        int start = source.indexOf(marker);
        assertTrue(start >= 0, "missing constant " + name);
        start += marker.length();
        int end = source.indexOf(';', start);
        assertTrue(end > start, "unterminated constant " + name);
        return Integer.parseInt(source.substring(start, end).trim());
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
