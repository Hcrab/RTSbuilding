package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverlayStyleTest {
    @Test
    void 扫描状态保持错误不可用与完成语义分离() {
        assertSame(OverlayStyle.STATUS_ERROR, OverlayStyle.questStatus(true, true));
        assertSame(OverlayStyle.STATUS_UNAVAILABLE, OverlayStyle.questStatus(false, true));
        assertSame(OverlayStyle.STATUS_NORMAL, OverlayStyle.questStatus(false, false));
        assertSame(OverlayStyle.PROGRESS_ERROR, OverlayStyle.questProgress(true, true));
        assertSame(OverlayStyle.PROGRESS_COMPLETE, OverlayStyle.questProgress(false, true));
        assertSame(OverlayStyle.PROGRESS_RUNNING, OverlayStyle.storageProgress(true));
    }

    @Test
    void 伤害闪烁保留半透明上限并钳制() {
        assertEquals(0x80FF0000, OverlayStyle.damageFlash(1.0D).toArgb());
        assertEquals(0x00FF0000, OverlayStyle.damageFlash(-1.0D).toArgb());
        assertThrows(IllegalArgumentException.class,
                () -> OverlayStyle.damageFlash(Double.NaN));
    }
}
