package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配置包预算、字段类型、结果和旧会话隔离的纯回归。 */
class RtsServerConfigWireTest {
    @Test
    void updateRoundTripPreservesTypedChangesAndExpectedRevision() {
        RtsServerConfigUpdateRequest update = new RtsServerConfigUpdateRequest(9, List.of(
                new RtsServerConfigChange(RtsServerConfigChange.Key.ENABLE_BLUEPRINTS,
                        new RtsServerConfigChange.BooleanValue(false)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HISTORY_RETENTION_SECONDS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.TASK_ENGINE_MAX_NANOS_PER_TICK,
                        new RtsServerConfigChange.LongValue(Long.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.AREA_MINE_MAX_HARVEST_TIER,
                        new RtsServerConfigChange.StringValue("DIAMOND")),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_TREE_BLOCKS,
                        new RtsServerConfigChange.IntValue(262_144)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HOME_SELECTION_RADIUS_BLOCKS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_DIMENSION,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_RADIUS,
                        new RtsServerConfigChange.IntValue(Integer.MAX_VALUE))));

        RtsServerConfigWire.Request decoded = RtsServerConfigWire.decodeRequest(
                RtsServerConfigWire.encodeUpdate(77L, 4, update));
        assertFalse(decoded.isQuery());
        assertEquals(77L, decoded.update().sessionId());
        assertEquals(4, decoded.update().requestId());
        assertEquals(update, decoded.update().request());
    }

    @Test
    void unknownKeyTypeTrailingFieldAndOversizedPacketAreRejected() {
        byte[] encoded = RtsServerConfigWire.encodeUpdate(1L, 1,
                new RtsServerConfigUpdateRequest(1, List.of(
                        new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_ACTION_RADIUS_BLOCKS,
                                new RtsServerConfigChange.IntValue(128)))));
        byte[] unknownKey = encoded.clone();
        unknownKey[20] = (byte) 0x7f;
        unknownKey[21] = (byte) 0xff;
        assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(unknownKey));
        byte[] wrongType = encoded.clone();
        wrongType[22] = (byte) RtsServerConfigChange.Type.BOOLEAN.ordinal();
        assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(wrongType));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> RtsServerConfigWire.decodeRequest(new byte[RtsServerConfigWire.MAX_PACKET_BYTES + 1]));
    }

    @Test
    void responseRoundTripCarriesAuthorityFields() {
        RtsServerConfigView defaults = RtsServerConfigView.defaults();
        RtsServerConfigView view = new RtsServerConfigView(12, true, true,
                defaults.enableSurvivalProgression(), defaults.shareSurvivalProgressionWithTeams(),
                defaults.maxActionRadiusBlocks(), defaults.enableBlueprints(), defaults.maxBlueprintBlocks(),
                defaults.maxSelectionVolume(), 80, defaults.maxSelectionSizeY(), defaults.maxSelectionSizeZ(),
                defaults.ultimineMaxBlocks(), defaults.ultimineBlocksPerTick(), defaults.areaMineMaxHarvestTier(),
                defaults.maxTreeBlocks(), defaults.homeSelectionRadiusBlocks(), Integer.MAX_VALUE,
                defaults.maxShapeDimension(), defaults.maxShapeRadius(), defaults.smartFillMaxBlocks(),
                defaults.smartFillDefaultBlocks(), defaults.smartFillMaxDiameter(), defaults.smartFillDefaultDiameter(),
                defaults.maxBatchBindingSelectionVolume(), defaults.maxBatchBindingSizeX(), defaults.maxBatchBindingSizeY(),
                defaults.maxBatchBindingSizeZ(), defaults.maxLinkedStorages(), defaults.funnelPickupRadiusBlocks(),
                defaults.workflowsMaxActivePerPlayer(), Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Long.MAX_VALUE, defaults.defaultStoragePageSize(), defaults.maxStoragePageSize(),
                defaults.pageCacheMaxPlayers(), defaults.ae2NetworkRefreshThrottle(),
                defaults.refinedStorageNetworkRefreshThrottle(), defaults.diagnosticsMaxTraces(),
                defaults.diagnosticsMaxWorkflowLinks(), defaults.diagnosticsMaxTaskLinks());
        RtsServerConfigUpdateResult result = RtsServerConfigUpdateResult.applied(view);
        assertEquals(result, RtsServerConfigWire.decodeResponse(
                RtsServerConfigWire.encodeResponse(7L, 2, result)).result());
    }

    @Test
    void sessionRejectsResponsesFromOldWorldAndAcceptsCurrentBroadcast() {
        RtsServerConfigClientSession session = new RtsServerConfigClientSession();
        long current = session.begin();
        RtsServerConfigUpdateResult result = RtsServerConfigUpdateResult.applied(
                RtsServerConfigView.defaults().readOnly());
        assertFalse(session.accept(new RtsServerConfigWire.Response(current + 1, 1, result)));
        assertTrue(session.accept(new RtsServerConfigWire.Response(current, 1, result)));
        assertTrue(session.accept(new RtsServerConfigWire.Response(current, 2, result)));
        assertFalse(session.accept(new RtsServerConfigWire.Response(current, 1, result)));
        assertTrue(session.accept(new RtsServerConfigWire.Response(current, 0, result)));
        session.clear();
        assertFalse(session.accept(new RtsServerConfigWire.Response(current, 0, result)));
    }
}
