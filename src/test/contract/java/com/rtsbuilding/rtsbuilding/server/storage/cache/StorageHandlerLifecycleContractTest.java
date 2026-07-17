package com.rtsbuilding.rtsbuilding.server.storage.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固化网络存储处理器的所有权边界，防止配置切换、切维度和退出流程重新引入悬空引用。
 */
class StorageHandlerLifecycleContractTest {

    @Test
    void endpointOwnerMustDetachTickBorrowerBeforeDestructiveRelease() throws IOException {
        String source = read("server/storage/cache/RtsEndpointLeaseCache.java");
        int detach = source.indexOf(
                "detachHandler(playerId, NeoForgeItemStorageAdapter.wrap(handler))");
        int release = source.indexOf("releaseNetworkHandler(handler)");

        assertTrue(detach >= 0, "端点销毁前必须通知 Tick 聚合缓存卸载借用对象");
        assertTrue(release > detach, "必须先卸载借用方，再清空 AE 网络处理器内部引用");
    }

    @Test
    void tickCacheMustNeverDestroyBorrowedNetworkHandlers() throws IOException {
        String source = read("server/service/RtsStorageTickService.java");

        assertFalse(source.contains("RtsAe2Compat.releaseNetworkHandler"),
                "Tick 服务只拥有快照，不拥有 AE Handler 的销毁权");
        assertFalse(source.contains("RtsBdCompat.releaseNetworkHandler"),
                "Tick 服务只拥有快照，不拥有 BD Handler 的销毁权");
    }

    @Test
    void playerLifecycleMustClearBorrowerBeforeInvalidatingEndpointOwner() throws IOException {
        String session = read("server/service/impl/RtsSessionServiceImpl.java");
        assertOrderedInMethod(session, "void onRtsDisabled", "cleanupPlayerCaches(player)",
                "RtsEndpointLeaseCache.INSTANCE.invalidatePlayer", "关闭 RTS");
        assertOrderedInMethod(session, "void onPlayerLogout", "cleanupPlayerCaches(player)",
                "RtsEndpointLeaseCache.INSTANCE.invalidatePlayer", "玩家退出");

        String mod = read("RtsbuildingMod.java");
        assertOrderedInMethod(mod, "void onPlayerChangedDimension", "unregisterPlayer(serverPlayer)",
                "RtsEndpointLeaseCache.INSTANCE.invalidatePlayer", "切换维度");
    }

    @Test
    void releasedAeHandlerAndNullReflectionTargetMustFailClosed() throws IOException {
        String aeSource = read("compat/ae2/RtsAe2Compat.java");

        assertTrue(aeSource.contains("if (this.released || this.storageService == null) return;"),
                "已释放的 AE 快照刷新必须直接停止");
        assertTrue(aeSource.contains("target == null && !Modifier.isStatic(method.getModifiers())"),
                "反射层不得对空实例调用非静态方法");

        String rsSource = read("compat/refinedstorage/RtsRefinedStorageCompat.java");
        assertTrue(rsSource.contains("target == null && !Modifier.isStatic(method.getModifiers())"),
                "RS 网络节点失效时也必须拒绝对空实例执行反射调用");
    }

    private static void assertOrderedInMethod(String source, String methodMarker,
            String borrowerCleanup, String ownerCleanup, String scenario) {
        int method = source.indexOf(methodMarker);
        int borrower = source.indexOf(borrowerCleanup, method);
        int owner = source.indexOf(ownerCleanup, method);
        assertTrue(method >= 0 && borrower > method && owner > borrower,
                scenario + "必须先清理 Tick 借用方，再销毁端点所有者");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
