package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ContainerOverlayStyleTest {
    @Test
    void 搜索按钮与刷新状态保持明确语义() {
        assertSame(ContainerOverlayStyle.SEARCH_IDLE,
                ContainerOverlayStyle.searchBackground(false));
        assertSame(ContainerOverlayStyle.SEARCH_FOCUSED,
                ContainerOverlayStyle.searchBackground(true));
        assertSame(ContainerOverlayStyle.BUTTON_HOVER,
                ContainerOverlayStyle.controlBackground(true));
        assertSame(ContainerOverlayStyle.SHIFT_IMPORT_IDLE,
                ContainerOverlayStyle.shiftImportBackground(true, false));
        assertSame(ContainerOverlayStyle.REFRESH_RUNNING,
                ContainerOverlayStyle.refreshBackground(true, false));
    }
}
