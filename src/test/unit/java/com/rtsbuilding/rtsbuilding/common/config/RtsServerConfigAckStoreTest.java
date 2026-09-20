package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 生产客户端接收路径使用的保存 ACK 邮箱回归；查询和广播不能吞掉待消费结果。 */
class RtsServerConfigAckStoreTest {
    @Test
    void updateAckSurvivesQueryAndHigherRevisionBroadcast() {
        RtsServerConfigAckStore store = new RtsServerConfigAckStore();
        RtsServerConfigView view = RtsServerConfigView.defaults().readOnly();
        RtsServerConfigUpdateResult saved = new RtsServerConfigUpdateResult(
                RtsServerConfigUpdateResult.Status.APPLIED, view, "saved");
        RtsServerConfigUpdateResult query = new RtsServerConfigUpdateResult(
                RtsServerConfigUpdateResult.Status.NO_CHANGE, view, "query");
        long session = 91L;

        store.accept(new RtsServerConfigWire.Response(session, 1, saved), session, 1);
        store.accept(new RtsServerConfigWire.Response(session, 2, query), session, 1);
        store.accept(new RtsServerConfigWire.Response(session, 0, query), session, 1);
        store.accept(new RtsServerConfigWire.Response(session + 1, 1, query), session, 1);

        assertSame(saved, store.result());
        store.clear();
        assertNull(store.result());
    }

    @Test
    void queryBeforeUpdateDoesNotBecomeAFalseSaveAck() {
        RtsServerConfigAckStore store = new RtsServerConfigAckStore();
        RtsServerConfigView view = RtsServerConfigView.defaults().readOnly();
        RtsServerConfigUpdateResult query = new RtsServerConfigUpdateResult(
                RtsServerConfigUpdateResult.Status.NO_CHANGE, view, "query");
        RtsServerConfigUpdateResult saved = new RtsServerConfigUpdateResult(
                RtsServerConfigUpdateResult.Status.APPLIED, view, "saved");

        store.accept(new RtsServerConfigWire.Response(12L, 1, query), 12L, 2);
        assertNull(store.result());
        store.accept(new RtsServerConfigWire.Response(12L, 2, saved), 12L, 2);
        assertSame(saved, store.result());
    }
}
