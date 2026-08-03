package com.rtsbuilding.rtsbuilding.client.screen.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointerGestureClassifierTest {
    @Test
    void 小范围鼠标抖动仍然是点击() {
        assertFalse(PointerGestureClassifier.isIntentionalDrag(
                100.0D, 80.0D, 103.0D, 82.0D, 4.0D));
    }

    @Test
    void 超过阈值才判定为镜头拖动() {
        assertTrue(PointerGestureClassifier.isIntentionalDrag(
                100.0D, 80.0D, 105.0D, 80.0D, 4.0D));
    }

    @Test
    void 无效起点不会吞掉一次点击() {
        assertFalse(PointerGestureClassifier.isIntentionalDrag(
                Double.NaN, Double.NaN, 105.0D, 80.0D, 4.0D));
    }
}
