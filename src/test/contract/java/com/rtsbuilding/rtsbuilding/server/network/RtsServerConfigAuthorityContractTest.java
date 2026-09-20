package com.rtsbuilding.rtsbuilding.server.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Forge 配置 Query/Update、权威保存、在线刷新和原始迁移边界。 */
class RtsServerConfigAuthorityContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void serverHandlerUsesActualServerPlayerAndTypedResult() throws IOException {
        String source = read(ROOT.resolve("server/network/RtsServerConfigNetwork.java"));
        assertTrue(source.contains("RtsServerConfigWire.decodeRequest(data)"));
        assertTrue(source.contains("Config.currentServerSettings(canEdit(player))"));
        assertTrue(source.contains("Config.applyServerConfig(update.request(), player)"));
        assertTrue(source.contains("result.view(), result.message(), result.status()"));
        assertTrue(source.contains("RtsServerConfigUpdateResult.Status.SAVE_FAILED"));
        assertFalse(source.contains("broadcastCurrent(player.server)"));
        assertTrue(source.contains("if (Config.pollServerConfigRuntimeChange()) broadcastCurrent(event.getServer());"));
        assertTrue(source.contains("requestedSession"));
        assertTrue(source.contains("configuration session superseded"));
        assertFalse(source.contains("applyServerConfig(update.request(), true)"));
    }

    @Test
    void forgeLifecycleRefreshesAndClearsSessions() throws IOException {
        String source = read(ROOT.resolve("server/network/RtsServerConfigNetwork.java"));
        assertTrue(source.contains("SESSIONS.remove(p.getUUID())"));
        assertTrue(source.contains("SESSIONS.remove(event.getEntity().getUUID())"));
        assertTrue(source.contains("Config.pollServerConfigRuntimeChange()"));
        assertTrue(source.contains("Config.endServerConfigRuntime()"));
        assertTrue(source.contains("Config.clearRawConfigSnapshots()"));
    }

    @Test
    void typedResponsesUseClientProjectionForRemoteConsumers() throws IOException {
        String view = read(ROOT.resolve("common/config/RtsServerConfigView.java"));
        String network = read(ROOT.resolve("server/network/RtsServerConfigNetwork.java"));
        assertTrue(view.contains("clientProjection()"));
        assertTrue(view.contains("defaults.taskEngineMaxUnitsPerTick()"));
        assertTrue(network.contains("view.clientProjection()"));
    }

    @Test
    void progressionToggleUsesAuthoritativeTransactionalSave() throws IOException {
        String source = read(ROOT.resolve("network/progression/handler/RtsProgressionNetworkHandlers.java"));
        assertTrue(source.contains("Config.applyServerConfig("));
        assertTrue(source.contains("new RtsServerConfigUpdateRequest"));
        assertTrue(source.contains("if (!result.succeeded())"));
        assertFalse(source.contains("Config.setSurvivalProgressionEnabled(payload.enabled())"));
    }

    @Test
    void forgeRegistrarAndClientExposeTypedPath() throws IOException {
        String registrar = read(ROOT.resolve("network/RtsForgePayloadRegistrar.java"));
        String packets = read(ROOT.resolve("network/config/RtsServerConfigPackets.java"));
        String client = read(ROOT.resolve("client/network/RtsClientServerConfigNetwork.java"));
        String ackStore = read(ROOT.resolve("common/config/RtsServerConfigAckStore.java"));
        assertTrue(registrar.contains("RtsServerConfigPackets.register(registrar)"));
        assertTrue(packets.contains("playToServer(C2SRtsServerConfigQueryPayload.TYPE"));
        assertTrue(packets.contains("playToServer(C2SRtsServerConfigUpdatePayload.TYPE"));
        assertTrue(packets.contains("playToClient(S2CRtsServerConfigResultPayload.TYPE"));
        assertTrue(client.contains("requestCurrent"));
        assertTrue(client.contains("save(List<RtsServerConfigChange> changes)"));
        assertTrue(client.contains("lastResult()"));
        assertTrue(ackStore.contains("response.result()"));
        assertTrue(client.contains("SAVE_ACK.accept(response"));
        assertTrue(client.contains("RtsServerConfigAckStore"));
        assertTrue(client.contains("Config.applySynchronizedServerView(SESSION.current())"));
        assertTrue(client.contains("hasSingleplayerServer()"),
                "integrated server must not receive a stale client projection");
    }

    @Test
    void synchronizedClientProjectionNeverWritesRemoteValuesToDisk() throws IOException {
        String source = read(ROOT.resolve("Config.java"));
        int start = source.indexOf("applySynchronizedServerView");
        int end = source.indexOf("public static boolean canEditServerConfig", start);
        assertTrue(start >= 0 && end > start);
        assertFalse(source.substring(start, end).contains("SERVER_SPEC.save()"));
        assertTrue(source.substring(start, end).contains("applyServerChange"));
    }

    @Test
    void runtimeProjectionListsClientRulesWithoutServerBudgets() throws IOException {
        String config = read(ROOT.resolve("Config.java"));
        assertTrue(config.contains("clientServerConfigProjectionToml()"));
        assertTrue(config.contains("maxSelectionSizeX"));
        assertTrue(config.contains("maxShapeDimension"));
        assertTrue(config.contains("maxBatchBindingSelectionVolume"));
        assertTrue(config.contains("只把客户端现有消费者需要的世界规则"));
    }

    @Test
    void productionApplyPathKeepsFailureConflictAndNoChangeStatuses() throws IOException {
        String config = read(ROOT.resolve("Config.java"));
        String persistence = read(ROOT.resolve("common/config/RtsServerConfigPersistence.java"));
        String validator = read(ROOT.resolve("common/config/RtsServerConfigValidator.java"));
        assertTrue(config.contains("RtsServerConfigValidator.validate(current, request, true)"));
        assertTrue(config.contains("RtsServerConfigUpdateResult.Status.NO_CHANGE"));
        assertTrue(config.contains("RtsServerConfigUpdateResult.saveFailed("));
        assertTrue(config.contains("if (!persistence.committed())"));
        assertTrue(config.contains("return RtsServerConfigUpdateResult.applied("));
        assertTrue(persistence.contains("catch (RuntimeException failure)"));
        assertTrue(persistence.contains("savePrevious.run()"));
        assertTrue(config.contains("markRuntimeConfigSaved()"));
        assertTrue(validator.contains("!editable || !current.editable()"));
        assertTrue(validator.contains("request.expectedRevision() != current.revision()"));
    }

    @Test
    void rawFilesAreCapturedBeforeForgeRegistrationAndMigration() throws IOException {
        String source = read(ROOT.resolve("RtsbuildingMod.java"));
        int commonCapture = source.indexOf("Config.captureRawCommonConfig(readRawCommonSnapshot())");
        int configRegistration = source.indexOf("ModLoadingContext.get().registerConfig");
        int serverRegistration = source.indexOf("Config.SERVER_CONFIG_SPEC");
        int migration = source.indexOf("Config.consumePendingLegacyServerMigration()");
        assertTrue(commonCapture >= 0 && configRegistration > commonCapture);
        assertTrue(serverRegistration > configRegistration);
        assertTrue(migration >= 0);
        assertFalse(source.contains("captureRawServerSnapshot(event.getServer())"));
        assertFalse(source.contains("Config.migrateLegacyServerDefaults()"));

        String serverFixture = read(Path.of("src/test/resources/fixtures/config/rtsbuilding-server-old-axis.toml"));
        String commonFixture = read(Path.of("src/test/resources/fixtures/config/rtsbuilding-common-old-generated.toml"));
        assertTrue(serverFixture.contains("areaMineMaxWidth = 80"));
        assertFalse(serverFixture.contains("maxSelectionVolume ="));
        assertTrue(commonFixture.contains("enableSurvivalProgression = true"));
        assertTrue(commonFixture.contains("maxActionRadiusBlocks = 192"));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
