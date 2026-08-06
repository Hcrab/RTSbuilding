package com.rtsbuilding.rtsbuilding.client.screen.culling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Flywheel 实例同步与 admission guard 共用的显隐语义。 */
class RtsFlywheelCullingPolicyTest {
    @Test
    void culledBlockEntitiesAreRemovedAndRejected() {
        assertFalse(RtsFlywheelCullingPolicy.shouldAdmit(true));
        assertEquals(
                RtsFlywheelCullingPolicy.SyncAction.REMOVE,
                RtsFlywheelCullingPolicy.actionFor(true));
    }

    @Test
    void visibleBlockEntitiesAreAddedAndAdmitted() {
        assertTrue(RtsFlywheelCullingPolicy.shouldAdmit(false));
        assertEquals(
                RtsFlywheelCullingPolicy.SyncAction.ADD,
                RtsFlywheelCullingPolicy.actionFor(false));
    }
}
