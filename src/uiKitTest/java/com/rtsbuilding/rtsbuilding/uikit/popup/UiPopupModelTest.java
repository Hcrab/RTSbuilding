package com.rtsbuilding.rtsbuilding.uikit.popup;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiPopupModelTest {
    @Test
    void 关闭弹窗不处理输入() {
        UiPopupModel popup = new UiPopupModel(new UiRect(10, 10, 20, 20), true, true);
        assertEquals(UiPopupModel.PressDecision.PASS, popup.press(15, 15));
        assertFalse(popup.escape());
    }

    @Test
    void 内部点击始终消费但不关闭() {
        UiPopupModel popup = popup(true, true);
        assertEquals(UiPopupModel.PressDecision.INSIDE_CONSUMED, popup.press(15, 15));
        assertTrue(popup.isOpen());
    }

    @Test
    void 外部关闭点击不会穿透到世界() {
        UiPopupModel popup = popup(false, true);
        assertEquals(UiPopupModel.PressDecision.DISMISSED_AND_BLOCKED, popup.press(1, 1));
        assertFalse(popup.isOpen());
    }

    @Test
    void 不可外部关闭的模态弹窗阻断外部() {
        UiPopupModel popup = popup(true, false);
        assertEquals(UiPopupModel.PressDecision.OUTSIDE_BLOCKED, popup.press(1, 1));
        assertTrue(popup.isOpen());
    }

    @Test
    void 非模态非关闭弹窗允许外部通过() {
        UiPopupModel popup = popup(false, false);
        assertEquals(UiPopupModel.PressDecision.PASS, popup.press(1, 1));
        assertTrue(popup.isOpen());
    }

    @Test
    void escape一次关闭当前弹窗() {
        UiPopupModel popup = popup(true, true);
        assertTrue(popup.escape());
        assertFalse(popup.escape());
    }

    @Test
    void 拒绝空边界() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiPopupModel(UiRect.EMPTY, true, true));
    }

    private static UiPopupModel popup(boolean modal, boolean dismiss) {
        UiPopupModel popup = new UiPopupModel(new UiRect(10, 10, 20, 20), modal, dismiss);
        popup.open();
        return popup;
    }
}
