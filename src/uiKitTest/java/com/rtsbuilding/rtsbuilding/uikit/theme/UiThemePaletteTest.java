package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiThemePaletteTest {
    @Test
    void argb通道可逆() {
        UiColor color = UiColor.argb(128, 12, 34, 56);
        assertEquals(128, color.alpha());
        assertEquals(12, color.red());
        assertEquals(34, color.green());
        assertEquals(56, color.blue());
    }

    @Test
    void 颜色插值确定并钳制进度() {
        UiColor black = UiColor.opaque(0, 0, 0);
        UiColor white = UiColor.opaque(255, 255, 255);
        assertEquals(UiColor.opaque(128, 128, 128), UiColor.interpolate(black, white, 0.5));
        assertEquals(black, UiColor.interpolate(black, white, -1));
        assertEquals(white, UiColor.interpolate(black, white, 2));
    }

    @Test
    void 首个主题自动成为活动主题() {
        UiThemePalette palette = new UiThemePalette();
        UiThemeTokens dark = theme("dark", 20);
        palette.register(dark);
        assertSame(dark, palette.active());
    }

    @Test
    void 主题按注册顺序快照并可切换() {
        UiThemePalette palette = new UiThemePalette();
        UiThemeTokens dark = theme("dark", 20);
        UiThemeTokens light = theme("light", 230);
        palette.register(dark);
        palette.register(light);
        palette.activate("light");
        assertSame(light, palette.active());
        assertEquals("dark", palette.snapshot().get(0).getId());
        assertThrows(UnsupportedOperationException.class,
                () -> palette.snapshot().add(theme("other", 80)));
    }

    @Test
    void 重复和未知主题立即失败() {
        UiThemePalette palette = new UiThemePalette();
        palette.register(theme("dark", 20));
        assertThrows(IllegalArgumentException.class, () -> palette.register(theme("dark", 30)));
        assertThrows(IllegalArgumentException.class, () -> palette.activate("missing"));
    }

    @Test
    void 主题id必须是稳定小写格式() {
        assertThrows(IllegalArgumentException.class, () -> theme("Bad Theme", 20));
    }

    @Test
    void 颜色通道拒绝越界() {
        assertThrows(IllegalArgumentException.class, () -> UiColor.argb(256, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> UiColor.interpolate(UiColor.opaque(0, 0, 0), UiColor.opaque(1, 1, 1), Double.NaN));
    }

    private static UiThemeTokens theme(String id, int base) {
        UiColor background = UiColor.opaque(base, base, base);
        return new UiThemeTokens(id, background, background, background, UiColor.opaque(90, 90, 90),
                UiColor.opaque(255, 255, 255), UiColor.opaque(180, 180, 180),
                UiColor.opaque(60, 150, 255), UiColor.opaque(230, 60, 60),
                UiColor.argb(120, 0, 0, 0));
    }
}
