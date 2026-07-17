package com.rtsbuilding.rtsbuilding.server.storage.port;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 固化 26.1 滩头阵地的物品储存边界，避免后续修改把 NeoForge 接口重新扩散进业务层。
 */
class RtsStoragePortBoundaryContractTest {
    private static final Path SERVER_ROOT =
            Path.of("src/main/java/com/rtsbuilding/rtsbuilding/server");

    @Test
    void semanticPortDoesNotImportNeoForgeApis() throws IOException {
        try (var files = Files.walk(SERVER_ROOT.resolve("storage/port"))) {
            for (Path path : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(path).contains("import net.neoforged"),
                        SERVER_ROOT.relativize(path) + " 不得直接导入 NeoForge API");
            }
        }
    }

    @Test
    void deprecatedItemHandlerStaysInsideExplicitBoundaryFiles() throws IOException {
        Set<String> allowed = Set.of(
                "service/resolver/RtsLinkedHandlerResolutionService.java",
                "storage/cache/RtsEndpointLeaseCache.java",
                "storage/handler/RtsLinkedCapabilities.java",
                "storage/session/BdCacheState.java");
        Set<String> actual;
        try (var files = Files.walk(SERVER_ROOT)) {
            actual = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RtsStoragePortBoundaryContractTest::importsNeoForgeItemHandler)
                    .map(SERVER_ROOT::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .collect(Collectors.toSet());
        }

        assertEquals(allowed, actual,
                "IItemHandler 只能停留在端点解析、租约和兼容边界");
    }

    @Test
    void linkedFluidBusinessPathDoesNotImportNeoForgeFluidTypes() throws IOException {
        for (String relative : Set.of(
                "storage/model/LinkedFluidHandler.java",
                "storage/view/LinkedFluidHandlerView.java",
                "storage/RtsStorageFluids.java",
                "service/fluids/RtsFluidNetworkOperator.java",
                "service/fluids/RtsFluidBufferService.java",
                "service/fluids/RtsFluidWorldPlacer.java",
                "pipeline/blueprint/BlueprintTickPipe.java",
                "service/page/RtsPageCore.java")) {
            String source = Files.readString(SERVER_ROOT.resolve(relative));
            assertFalse(source.contains("import net.neoforged.neoforge.fluids"),
                    relative + " 不得绕过流体端口重新依赖 NeoForge");
        }
    }

    private static boolean importsNeoForgeItemHandler(Path path) {
        try {
            return Files.readString(path)
                    .contains("import net.neoforged.neoforge.items.IItemHandler;");
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取源码边界：" + path, exception);
        }
    }
}
