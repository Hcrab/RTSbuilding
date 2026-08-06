package com.rtsbuilding.rtsbuilding.compat.create;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Create Value Settings 对 RTS 主键、回落和无距离服务端提交的最小决策边界。 */
class RtsCreateValueSettingsPolicyTest {
    @Test
    void onlyEligiblePrimaryWorldClicksStartTheCreateHold() {
        assertTrue(RtsCreateValueSettingsPolicy.shouldStartHold(true, true, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(false, true, true),
                "破坏、旋转或其它鼠标键不得被 Create Value Settings 消费。");
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(true, false, true),
                "UI 区域不得穿透到世界 Value Settings 交互。");
        assertFalse(RtsCreateValueSettingsPolicy.shouldStartHold(true, true, false),
                "非 Create 或不合格行为必须回落现有 RTS 世界右键。");
    }

    @Test
    void createGlobalInputGateRequiresNativePermissionAndNoClipboard() {
        assertTrue(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(true, false),
                "Create 允许输入且主手不是剪贴板时，RTS 才可继续定位 Value Settings 行为。");
        assertFalse(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(false, false),
                "Create 拒绝冒险模式等全局输入时必须回落既有 RTS 右键。");
        assertFalse(RtsCreateValueSettingsPolicy.allowsCreateGlobalInput(true, true),
                "主手持有 Create 剪贴板时必须保持 Create 原生入口的让行语义。");
    }

    @Test
    void serverSubmissionDependsOnSessionLoadedTargetAndCreateValidationNotPlayerDistance() {
        assertTrue(RtsCreateValueSettingsPolicy.shouldApplyOnServer(true, true, true, true),
                "有效 RTS 会话、已加载目标和 Create board 合法值应可远程保存。");
        assertFalse(RtsCreateValueSettingsPolicy.shouldApplyOnServer(false, true, true, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldApplyOnServer(true, false, true, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldApplyOnServer(true, true, false, true));
        assertFalse(RtsCreateValueSettingsPolicy.shouldApplyOnServer(true, true, true, false));
    }
}
