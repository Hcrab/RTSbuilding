package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Waystones 客户端开窗桥的白名单、权威顺序与 fail-open 边界。 */
class ClientOnlyBlockGuiCompatContractTest {
    @Test
    void waystoneClientHalfRunsOnlyAfterTheAuthoritativePacket() throws Exception {
        String service = read("client/service/BuildPlacementService.java");
        int start = service.indexOf("public void interactEmpty(");
        int end = service.indexOf("public void interactEntityEmpty(", start);
        String method = service.substring(start, end);

        assertInOrder(method,
                "sendInteractBlockEmptyHand(",
                "RtsClientOnlyBlockGuiCompat.tryOpenAfterAuthoritativeSend(hit)");

        String compat = read("client/compat/RtsClientOnlyBlockGuiCompat.java");
        assertTrue(compat.contains("new ResourceLocation(\"waystones\", \"waystone\")"));
        assertTrue(compat.contains("state.getBlock().onBlockActivated("));
        assertTrue(compat.contains("catch (RuntimeException | LinkageError failure)"));
        assertTrue(compat.contains("event=CLIENT_ONLY_GUI_FALLBACK"));
        assertFalse(compat.contains("Class.forName"),
                "白名单桥不应通过反射加载可选模组类，缺少 Waystones 时必须安全类加载");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding/" + relative),
                StandardCharsets.UTF_8);
    }

    private static void assertInOrder(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex,
                first + " 必须先于 " + second);
    }
}
