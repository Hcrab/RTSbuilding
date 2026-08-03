package com.rtsbuilding.rtsbuilding.client.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowButtonTextureStateTest {
    @Test
    void 空闲纹理和主线的悬停按下选中纹理有明确边界() {
        assertFalse(WindowButton.useActiveTexture(false, false, false));
        assertTrue(WindowButton.useActiveTexture(false, true, false));
        assertTrue(WindowButton.useActiveTexture(false, false, true));
        assertTrue(WindowButton.useActiveTexture(true, false, false));
    }
}
