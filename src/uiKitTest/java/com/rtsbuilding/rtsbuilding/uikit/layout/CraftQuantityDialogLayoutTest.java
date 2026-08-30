package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftQuantityDialogLayoutTest {
    @Test
    void 居中布局为底部动作留出空间并使用半开选项命中() {
        CraftQuantityDialogLayout.Layout layout =
                CraftQuantityDialogLayout.resolve(400, 300);
        assertEquals(81, layout.panelX);
        assertEquals(52, layout.panelY);
        assertEquals(0, CraftQuantityDialogLayout.optionIndexAt(
                layout, 0, 6, layout.optionsX + 2, layout.optionsY + 2));
        assertEquals(-1, CraftQuantityDialogLayout.optionIndexAt(
                layout, 0, 6, layout.optionsX + layout.optionsW, layout.optionsY + 2));
    }

    @Test
    void 所有按钮与面板外部共用布局命中结果() {
        CraftQuantityDialogLayout.Layout layout =
                CraftQuantityDialogLayout.resolve(400, 300);

        assertHit(layout, CraftQuantityDialogLayout.Control.OUTSIDE_PANEL,
                layout.panelX - 1, layout.panelY);
        assertHit(layout, CraftQuantityDialogLayout.Control.CLOSE,
                layout.closeX, layout.closeY);
        assertHit(layout, CraftQuantityDialogLayout.Control.MINUS_TEN,
                layout.minusTenX, layout.inputY);
        assertHit(layout, CraftQuantityDialogLayout.Control.MINUS_ONE,
                layout.minusOneX, layout.inputY);
        assertHit(layout, CraftQuantityDialogLayout.Control.PLUS_ONE,
                layout.plusOneX, layout.inputY);
        assertHit(layout, CraftQuantityDialogLayout.Control.PLUS_TEN,
                layout.plusTenX, layout.inputY);
        assertHit(layout, CraftQuantityDialogLayout.Control.CANCEL,
                layout.cancelX, layout.actionY);
        assertHit(layout, CraftQuantityDialogLayout.Control.CONFIRM,
                layout.confirmX, layout.actionY);

        CraftQuantityDialogLayout.Hit option = CraftQuantityDialogLayout.hitAt(
                layout, 1, 6, layout.optionsX + 2, layout.optionsY + 2);
        assertEquals(CraftQuantityDialogLayout.Control.OPTION, option.control());
        assertEquals(1, option.optionIndex());
    }

    @Test
    void 按钮右下边界保持半开命中() {
        CraftQuantityDialogLayout.Layout layout =
                CraftQuantityDialogLayout.resolve(400, 300);

        assertHit(layout, CraftQuantityDialogLayout.Control.NONE,
                layout.confirmX + CraftQuantityDialogLayout.ACTION_W,
                layout.actionY + CraftQuantityDialogLayout.ACTION_H - 1);
        assertHit(layout, CraftQuantityDialogLayout.Control.NONE,
                layout.plusTenX + CraftQuantityDialogLayout.STEP_W - 1,
                layout.inputY + CraftQuantityDialogLayout.STEP_H);
        assertHit(layout, CraftQuantityDialogLayout.Control.OUTSIDE_PANEL,
                layout.panelX + CraftQuantityDialogLayout.PANEL_W,
                layout.panelY + 1);
    }

    private static void assertHit(CraftQuantityDialogLayout.Layout layout,
                                  CraftQuantityDialogLayout.Control expected,
                                  double mouseX, double mouseY) {
        assertEquals(expected, CraftQuantityDialogLayout.hitAt(
                layout, 0, 6, mouseX, mouseY).control());
    }
}
