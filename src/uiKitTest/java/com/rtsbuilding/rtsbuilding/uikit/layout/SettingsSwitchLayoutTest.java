package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SettingsSwitchLayoutTest {
    @Test
    void 调色板主题沿用主线像素母版尺寸() {
        SettingsSwitchLayout.Geometry off = SettingsSwitchLayout.geometry(10, 20, 0.0D);
        SettingsSwitchLayout.Geometry on = SettingsSwitchLayout.geometry(10, 20, 1.0D);

        assertEquals(new UiRect(10, 20, 66, 29), off.bounds);
        assertEquals(new UiRect(10, 22.5D, 66, 24), off.track);
        assertEquals(new UiRect(10, 20, 26, 29), off.knob);
        assertEquals(new UiRect(50, 20, 26, 29), on.knob);
    }

    @Test
    void 动画只改变旋钮位置不改变母版大小() {
        SettingsSwitchLayout.Geometry middle = SettingsSwitchLayout.geometry(10, 20, 0.5D);

        assertEquals(new UiRect(30, 20, 26, 29), middle.knob);
        assertEquals(SettingsSwitchLayout.WIDTH, middle.bounds.getWidth());
        assertEquals(SettingsSwitchLayout.HEIGHT, middle.bounds.getHeight());
    }
}
