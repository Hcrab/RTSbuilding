package com.rtsbuilding.rtsbuilding.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Create 数值设置对 RTS 主键、产品范围与服务端窄安全门的纯决策边界。 */
class RtsCreateValueSettingsPolicyTest {
    @Test
    void onlyEligiblePrimaryWorldClicksStartTheCreateHold() {
        assertTrue(RtsCreateValueSettingsPolicy.shouldStartHold(true, true, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(false, true, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(true, false, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(true, true, false));
    }

    @Test
    void createGlobalInputOwnsAdventureAndClipboardRules() {
        assertTrue(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(true, false));
        assertFalse(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(false, false));
        assertFalse(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(true, true));
    }

    @Test
    void everyServerAuthorityGateMustPass() {
        assertTrue(RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                true, true, true, true, true, true, true));

        for (int rejectedGate = 0; rejectedGate < 7; rejectedGate++) {
            boolean[] gates = {true, true, true, true, true, true, true};
            gates[rejectedGate] = false;
            assertFalse(RtsCreateValueSettingsPolicy.shouldApplyOnServer(
                    gates[0], gates[1], gates[2], gates[3],
                    gates[4], gates[5], gates[6]),
                    "第 " + rejectedGate + " 个服务端权限门不得被绕过");
        }
    }
}
