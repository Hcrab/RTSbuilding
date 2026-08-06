package com.rtsbuilding.rtsbuilding.network.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止仓储搜索再次被错误地绑定到 RTS 相机状态。
 *
 * <p>容器 overlay 在相机退出后仍可按客户端缓存继续显示，因此分页和搜索请求必须可达服务层；
 * 真正的功能权限仍由服务端 {@code STORAGE_BROWSER} 校验负责。
 */
class StoragePageOverlayAvailabilityContractTest {
    private static final Path ACTIVE_HANDLER = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network/storage/handler/RtsStoragePageHandlers1122.java");
    private static final Path LEGACY_HANDLER = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/network/storage/handler/RtsPageHandlers.java");
    private static final Path PAGE_SERVICE = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsPageServiceImpl.java");

    @Test
    void visibleStorageOverlayCanRefreshWithoutAnActiveCamera() throws IOException {
        assertNoCameraGate(read(ACTIVE_HANDLER));
        assertNoCameraGate(read(LEGACY_HANDLER));
    }

    @Test
    void pageServiceStillOwnsStorageBrowserAuthorization() throws IOException {
        String service = read(PAGE_SERVICE);

        assertTrue(service.contains(
                "RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)"));
    }

    private static void assertNoCameraGate(String handler) {
        assertFalse(handler.contains("RtsCameraManager"));
        assertFalse(handler.contains("active(player)"));
        assertFalse(handler.contains("active(p)"));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
