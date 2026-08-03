package com.rtsbuilding.rtsbuilding.server.service.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRemoteDropSafetyPolicyTest {
    @Test
    void 两区块内保留玩家选择而超过边界强制安全入库() {
        double boundary = RtsRemoteDropSafetyPolicy.SAFE_WORLD_DROP_DISTANCE;
        assertFalse(RtsRemoteDropSafetyPolicy.shouldForceAutoStore(boundary * boundary));
        assertTrue(RtsRemoteDropSafetyPolicy.shouldForceAutoStore(
                (boundary + 0.01D) * (boundary + 0.01D)));
    }

    @Test
    void 非法距离不会意外开启策略() {
        assertFalse(RtsRemoteDropSafetyPolicy.shouldForceAutoStore(Double.NaN));
        assertFalse(RtsRemoteDropSafetyPolicy.shouldForceAutoStore(Double.POSITIVE_INFINITY));
    }
}
