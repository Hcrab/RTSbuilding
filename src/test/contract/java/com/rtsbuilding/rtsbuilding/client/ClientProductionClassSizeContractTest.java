package com.rtsbuilding.rtsbuilding.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * “彻底占领”客户端类尺寸硬门禁。
 *
 * <p>四个生产目录不允许重新出现千行类；几个高风险编排壳拥有更低的明确上限。
 * 这不是风格提示，而是阻止职责重新回流到大神类的构建契约。</p>
 */
class ClientProductionClassSizeContractTest {
    private static final Path CLIENT = Path.of(
            "src/client/java/com/rtsbuilding/rtsbuilding/client");

    @Test
    void productionClientDomainsContainNoThousandLineClasses() throws IOException {
        for (String domain : new String[]{"screen", "controller", "input", "service"}) {
            Path root = CLIENT.resolve(domain);
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertLineLimit(file, 999);
                }
            }
        }
    }

    @Test
    void highRiskOrchestratorsStayBelowTheirDedicatedLimits() throws IOException {
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("screen/standalone/BuilderScreen.java", 700);
        limits.put("screen/handler/ScreenShapeController.java", 700);
        limits.put("screen/blueprint/BlueprintPanel.java", 700);
        limits.put("controller/ClientRtsController.java", 800);
        limits.put("controller/StorageStateManager.java", 800);
        limits.put("input/RtsClientInputGate.java", 700);
        limits.put("service/CameraOrbitService.java", 800);

        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            assertLineLimit(CLIENT.resolve(entry.getKey()), entry.getValue());
        }
    }

    private static void assertLineLimit(Path file, int maximum) throws IOException {
        long lines;
        try (Stream<String> source = Files.lines(file, StandardCharsets.UTF_8)) {
            lines = source.count();
        }
        assertTrue(lines <= maximum,
                file + " 超过生产硬门禁：" + lines + " > " + maximum);
    }
}
