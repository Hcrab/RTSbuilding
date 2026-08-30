package com.rtsbuilding.rtsbuilding.server.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCrossDimensionStorageContractTest {
    @Test
    void featureScopeDoesNotReintroduceCrossDimensionHome() throws IOException {
        String items = read("common/RtsItems.java");
        String features = read("server/progression/RtsFeature.java");
        String recipes = Files.readString(Path.of(
                "src/main/resources/data/rtsbuilding/recipe/cross_dimension_storage_plugin.json"));

        assertTrue(items.contains("CROSS_DIMENSION_STORAGE_PLUGIN"));
        assertTrue(features.contains("CROSS_DIMENSION_STORAGE"));
        assertTrue(recipes.contains("storage_integration_plugin"));
        assertFalse(items.contains("CROSS_DIMENSION_HOME"),
                "Field Deployment 已承载离家作业，不得重新引入重叠的跨维度家园插件");
        assertFalse(features.contains("CROSS_DIMENSION_HOME"));
    }

    @Test
    void wakeIsBoundedShortLivedAndReleasedByLifecycle() throws IOException {
        String wake = read("server/storage/wake/RtsCrossDimensionStorageWakeService.java");
        String config = read("Config.java");
        String lifecycle = read("RtsbuildingMod.java");

        assertTrue(wake.contains("TICKET_LIFESPAN_TICKS = 100"));
        assertTrue(wake.contains("addRegionTicket("));
        assertTrue(wake.contains("new WakeTicketKey(player.getUUID(), packedChunk), false"),
                "跨维度储存票据不能强制实体 Tick");
        assertTrue(wake.contains("CAPACITY_REACHED"));
        assertTrue(config.contains("maxCrossDimensionAwakeChunks"));
        assertTrue(lifecycle.contains("RtsCrossDimensionStorageWakeService.INSTANCE.releasePlayer"));
        assertTrue(lifecycle.contains("RtsCrossDimensionStorageWakeService.INSTANCE.clear"));
    }

    @Test
    void linkedStorageProtocolUsesDimensionAsPartOfEndpointIdentity() throws IOException {
        String page = read("server/service/page/RtsPagePayloadFactory.java");
        String serverPayload = read("network/storage/S2CRtsStoragePagePayload.java");
        String clientEntry = read("client/record/LinkedStorageEntry.java");
        String unlinkPayload = read("network/storage/C2SRtsUnlinkStoragePayload.java");
        String updatePayload = read("network/storage/C2SRtsUpdateLinkedStoragePayload.java");

        assertTrue(page.contains("ref.dimension().location().toString()"));
        assertTrue(serverPayload.contains("List<String> linkedDimensions"));
        assertTrue(clientEntry.contains("String dimensionId"));
        assertTrue(unlinkPayload.contains("ResourceLocation dimension"));
        assertTrue(updatePayload.contains("ResourceLocation dimension"));
    }

    @Test
    void resolutionUsesTargetLevelAndKeepsNetworkCompat() throws IOException {
        String resolver = read("server/service/resolver/RtsLinkedHandlerResolutionService.java");
        String access = read("server/storage/resolver/RtsLinkedStorageResolver.java");
        String capabilities = read("server/storage/handler/RtsLinkedCapabilities.java");

        assertTrue(resolver.contains("player.server.getLevel(ref.dimension())"));
        assertTrue(resolver.contains("canAccessLinkedRef(player, session, ref, targetLevel)"));
        assertTrue(access.contains("RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady"));
        assertTrue(capabilities.contains("RtsAe2Compat.createNetworkItemHandler(player, level, pos)"));
        assertTrue(capabilities.contains("RtsRefinedStorageCompat.createNetworkItemHandler(player, level, pos)"));
    }

    @Test
    void unrelatedDurabilityFlushRemainsIntact() throws IOException {
        String durability = read("server/plugin/RtsPluginDurability.java");
        assertTrue(durability.contains("server.getPlayerList().saveAll()"),
                "吸收社区 PR 时不得顺带删除既有耐久持久化保障");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
