package com.rtsbuilding.rtsbuilding.server.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨维储存的服务端边界契约。
 *
 * <p>该测试保护玩家的正常工作流：已安装插件且拥有有效 RTS 会话时，已链接的异维储存
 * 可以被安全唤醒和访问；它不是跨维打开家园、远程 GUI 或绕过领地权限的后门。</p>
 */
class RtsCrossDimensionStorageContractTest {
    @Test
    void pluginAndRecipeAreVisibleToBothClientAndServer() throws IOException {
        String items = read("common/RtsItems.java");
        String features = read("server/progression/RtsFeature.java");
        String clientCatalog = read("client/plugin/RtsClientPluginCatalog.java");
        String recipe = Files.readString(Path.of(
                "src/main/resources/data/rtsbuilding/recipes/cross_dimension_storage_plugin.json"));

        assertTrue(items.contains("CROSS_DIMENSION_STORAGE_PLUGIN"));
        assertTrue(features.contains("CROSS_DIMENSION_STORAGE"));
        assertTrue(clientCatalog.contains("cross_dimension_storage_plugin"));
        assertTrue(recipe.contains("storage_integration_plugin"));
    }

    @Test
    void wakeIsBoundedAndLifecycleReleased() throws IOException {
        String wake = read("server/storage/wake/RtsCrossDimensionStorageWakeService.java");
        String config = read("Config.java");
        String lifecycle = read("RtsbuildingMod.java");

        assertTrue(wake.contains("TICKET_LIFESPAN_TICKS = 100"));
        assertTrue(wake.contains("CAPACITY_REACHED"));
        assertTrue(wake.contains("addRegionTicket("));
        assertTrue(config.contains("maxCrossDimensionAwakeChunks"));
        assertTrue(lifecycle.contains("RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer"));
        assertTrue(lifecycle.contains("RtsCrossDimensionStorageWakeService.INSTANCE.clear"));
    }

    @Test
    void targetLevelIsResolvedAndCrossDimensionDoesNotUseLocalRange() throws IOException {
        String resolver = read("server/service/resolver/RtsLinkedHandlerResolutionService.java");
        String access = read("server/storage/resolver/RtsLinkedStorageResolver.java");
        String capabilities = read("server/storage/handler/RtsLinkedCapabilities.java");
        String pagePayload = read("network/storage/S2CRtsStoragePagePayload.java");
        String clientEntry = read("client/record/LinkedStorageEntry.java");
        String unlinkPayload = read("network/storage/C2SRtsUnlinkStoragePayload.java");
        String updatePayload = read("network/storage/C2SRtsUpdateLinkedStoragePayload.java");

        assertTrue(resolver.contains("player.server.getLevel(ref.dimension())"));
        assertTrue(resolver.contains("canAccessLinkedRef(player, session, ref, targetLevel)"));
        assertTrue(access.contains("RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady"));
        assertTrue(access.contains("return !sameDimension || RtsCameraManager.isWithinActionRange(player, pos);"),
                "异维已连接储存不能被当前维度的相机射程误伤");
        assertTrue(capabilities.contains("RtsAe2Compat.createNetworkItemHandler(player, level, pos)"));
        assertTrue(capabilities.contains("RtsRefinedStorageCompat.createNetworkItemHandler(player, level, pos)"));
        assertTrue(pagePayload.contains("List<String> linkedDimensions"));
        assertTrue(clientEntry.contains("String dimensionId"));
        assertTrue(unlinkPayload.contains("ResourceLocation dimension"));
        assertTrue(updatePayload.contains("ResourceLocation dimension"));
    }

    @Test
    void remoteGuiUsesDedicatedRemoteInteractionContext() throws IOException {
        String gui = read("server/storage/RtsGuiBindingHelper.java");

        assertTrue(gui.contains("withTemporaryUseItemContext"));
        assertTrue(gui.contains("face.getStepX() * 2.2D"));
        assertTrue(gui.contains("remotePovBlockReach"),
                "远程 GUI 必须使用专用上下文，不能退回原版近距离判定");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
