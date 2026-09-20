package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forge 与 NeoForge 共用同一 typed 配置协议的纯回归。 */
class RtsServerConfigWireTest {
    @Test
    void updateRoundTripPreservesTypesAndWidePositiveValues() {
        RtsServerConfigUpdateRequest update = new RtsServerConfigUpdateRequest(9, List.of(
                new RtsServerConfigChange(RtsServerConfigChange.Key.ENABLE_BLUEPRINTS, new RtsServerConfigChange.BooleanValue(false)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HISTORY_RETENTION_SECONDS, new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.TASK_ENGINE_MAX_NANOS_PER_TICK, new RtsServerConfigChange.LongValue(Long.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.AREA_MINE_MAX_HARVEST_TIER, new RtsServerConfigChange.StringValue("DIAMOND")),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_TREE_BLOCKS, new RtsServerConfigChange.IntValue(262_144)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.HOME_SELECTION_RADIUS_BLOCKS, new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_DIMENSION, new RtsServerConfigChange.IntValue(Integer.MAX_VALUE)),
                new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_SHAPE_RADIUS, new RtsServerConfigChange.IntValue(Integer.MAX_VALUE))));
        RtsServerConfigWire.Request decoded = RtsServerConfigWire.decodeRequest(RtsServerConfigWire.encodeUpdate(77L, 4, update));
        assertFalse(decoded.isQuery()); assertEquals(update, decoded.update().request());
    }
    @Test
    void badTypeUnknownFieldTrailingDataAndBudgetFailClosed() {
        byte[] encoded = RtsServerConfigWire.encodeUpdate(1L, 1, new RtsServerConfigUpdateRequest(1, List.of(new RtsServerConfigChange(RtsServerConfigChange.Key.MAX_ACTION_RADIUS_BLOCKS, new RtsServerConfigChange.IntValue(128)))));
        byte[] key = encoded.clone(); key[20] = 0x7f; key[21] = (byte) 0xff; assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(key));
        byte[] type = encoded.clone(); type[22] = (byte) RtsServerConfigChange.Type.BOOLEAN.ordinal(); assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(type));
        assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class, () -> RtsServerConfigWire.decodeRequest(new byte[RtsServerConfigWire.MAX_PACKET_BYTES + 1]));
    }
    @Test
    void oldSessionCannotReplaceCurrentSession() {
        RtsServerConfigClientSession session = new RtsServerConfigClientSession(); long id = session.begin();
        RtsServerConfigUpdateResult result = RtsServerConfigUpdateResult.applied(RtsServerConfigView.defaults().readOnly());
        assertFalse(session.accept(new RtsServerConfigWire.Response(id + 1, 1, result)));
        assertTrue(session.accept(new RtsServerConfigWire.Response(id, 1, result)));
        assertTrue(session.accept(new RtsServerConfigWire.Response(id, 2, result)));
        assertFalse(session.accept(new RtsServerConfigWire.Response(id, 1, result)));
        session.clear(); assertFalse(session.accept(new RtsServerConfigWire.Response(id, 0, result)));
    }
}
