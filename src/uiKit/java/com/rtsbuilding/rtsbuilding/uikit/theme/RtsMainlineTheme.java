package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 当前 1.21.1 主线固定栏与浮窗实际使用的语义颜色。
 *
 * <p>值来自生产绘制代码；预览器不得自行发明另一套调色盘。平台代码可以只在
 * 边界处把 {@link UiColor#toArgb()} 转成自己的颜色表示。</p>
 */
public final class RtsMainlineTheme {
    /** 透明色只用于插值和“无覆盖层”状态，不应替代明确的控件背景。 */
    public static final UiColor TRANSPARENT = new UiColor(0x00000000);

    public static final UiColor TOP_BAR_BACKGROUND = theme(UiThemeToken.TOP_BAR, 0xC0101116);
    public static final UiColor WINDOW_BACKGROUND = theme(UiThemeToken.SURFACE, 0xFF161C24);
    public static final UiColor WINDOW_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF6C839A);
    public static final UiColor WINDOW_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF0D1117);
    public static final UiColor WINDOW_BORDER_HOVER_LIGHT = theme(UiThemeToken.FOCUS_RING, 0xFFAAC8E8);
    public static final UiColor WINDOW_BORDER_HOVER_DARK = theme(UiThemeToken.ACCENT_SECONDARY, 0xFF2A3A4A);
    public static final UiColor WINDOW_TITLE = theme(UiThemeToken.SURFACE_RAISED, 0xCC233345);
    public static final UiColor WINDOW_TITLE_TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0xFFF2F7FF);

    public static final UiColor BOTTOM_BACKGROUND = theme(UiThemeToken.BOTTOM_BAR, 0xD014151A);
    public static final UiColor BOTTOM_HEADER = theme(UiThemeToken.SURFACE_RAISED, 0xCC1C242F);
    public static final UiColor BOTTOM_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF64788E);
    public static final UiColor BOTTOM_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF0D1015);
    public static final UiColor TAB_ACTIVE = theme(UiThemeToken.CONTROL_SELECTED, 0xCC355B4C);
    public static final UiColor TAB_ACTIVE_BORDER = theme(UiThemeToken.ACCENT_PRIMARY, 0xFF7CCB93);
    public static final UiColor TAB_IDLE = theme(UiThemeToken.CONTROL_IDLE, 0x8826303B);
    public static final UiColor TAB_IDLE_BORDER = theme(UiThemeToken.BORDER_SOFT, 0xFF536679);
    public static final UiColor PRIMARY_TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0xFFF2F6FB);
    public static final UiColor SECONDARY_TEXT = theme(UiThemeToken.TEXT_SECONDARY, 0xFFD8E2EE);
    public static final UiColor MUTED_TEXT = theme(UiThemeToken.TEXT_MUTED, 0xFF9FB0C2);

    public static final UiColor CONTROL_IDLE_BACKGROUND = theme(UiThemeToken.CONTROL_IDLE, 0xAA1F2329);
    public static final UiColor CONTROL_IDLE_BORDER_LIGHT = theme(UiThemeToken.BORDER_SOFT, 0xFF5B6673);
    public static final UiColor CONTROL_IDLE_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF0D0E10);
    public static final UiColor CONTROL_IDLE_ICON = theme(UiThemeToken.ICON_MUTED, 0xFFBDC9D6);
    public static final UiColor CONTROL_HOVER_BACKGROUND = theme(UiThemeToken.CONTROL_HOVER, 0xFF1D2530);
    public static final UiColor CONTROL_HOVER_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF7A90AA);
    public static final UiColor CONTROL_HOVER_ICON = theme(UiThemeToken.ICON_PRIMARY, 0xFFD9E3EF);
    public static final UiColor CONTROL_PRESSED_BACKGROUND = theme(UiThemeToken.CONTROL_PRESSED, 0xFF1F5037);
    public static final UiColor CONTROL_PRESSED_BORDER_LIGHT = theme(UiThemeToken.ACCENT_SECONDARY, 0xFF6AA784);
    public static final UiColor CONTROL_SELECTED_BACKGROUND = theme(UiThemeToken.CONTROL_SELECTED, 0xFF2D6B47);
    public static final UiColor CONTROL_SELECTED_BORDER_LIGHT = theme(UiThemeToken.ACCENT_PRIMARY, 0xFF9AD2AE);
    public static final UiColor CONTROL_SELECTED_ICON = theme(UiThemeToken.ICON_ON_ACCENT, 0xFFF4FBF5);
    public static final UiColor CONTROL_DISABLED_OVERLAY = theme(UiThemeToken.CONTROL_DISABLED, 0x880B0E12);
    public static final UiColor CONTROL_PENDING = theme(UiThemeToken.WARNING, 0xFFFFC96B);
    public static final UiColor CONTROL_ERROR = theme(UiThemeToken.ERROR, 0xFFE36B6B);

    public static final UiColor BUTTON_BACKGROUND = theme(UiThemeToken.CONTROL_IDLE, 0xAA2A3340);
    public static final UiColor BUTTON_PRIMARY_BACKGROUND = theme(UiThemeToken.CONTROL_SELECTED, 0xAA345A38);
    public static final UiColor BUTTON_DESTRUCTIVE_BACKGROUND = theme(UiThemeToken.DESTRUCTIVE, 0xAA473030);
    public static final UiColor BUTTON_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF667D95);
    public static final UiColor BUTTON_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF111821);
    public static final UiColor BUTTON_TEXT = theme(UiThemeToken.TEXT_PRIMARY, 0xFFFFFFFF);

    public static final UiColor INPUT_BACKGROUND = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF202833);
    public static final UiColor INPUT_BORDER_LIGHT = theme(UiThemeToken.BORDER_STRONG, 0xFF61758A);
    public static final UiColor INPUT_BORDER_DARK = theme(UiThemeToken.SURFACE_SUNKEN, 0xFF11161C);

    public static final UiColor STATUS_LINKED = theme(UiThemeToken.SUCCESS, 0xFFB8FFB8);
    public static final UiColor STATUS_UNLINKED = theme(UiThemeToken.WARNING, 0xFFFFD8AE);
    public static final UiColor GUIDE_HINT = theme(UiThemeToken.WARNING, 0xFFE7C46A);
    public static final UiColor TOOLTIP_BACKGROUND = theme(UiThemeToken.CANVAS, 0xF010141A);
    public static final UiColor TOOLTIP_BORDER = theme(UiThemeToken.BORDER_STRONG, 0xFF6C839A);
    public static final UiColor SLOT_COUNT_BACKGROUND = theme(UiThemeToken.SURFACE_SUNKEN, 0xB0000000);

    /** 选中插值只叠加很薄的绿色，避免改变现有纹理的识别度。 */
    public static final UiColor SELECTION_ANIMATION_OVERLAY = theme(UiThemeToken.WORLD_SELECTION_FILL, 0x4A7CCB93);

    public static final UiColor LEGACY_00FF0000 = new UiColor(0x00ff0000);
    public static final UiColor LEGACY_00FFFFFF = new UiColor(0x00ffffff);
    public static final UiColor LEGACY_221D2530 = new UiColor(0x221d2530);
    public static final UiColor LEGACY_22FFFFFF = new UiColor(0x22ffffff);
    public static final UiColor LEGACY_3340FF80 = new UiColor(0x3340ff80);
    public static final UiColor LEGACY_44220000 = new UiColor(0x44220000);
    public static final UiColor LEGACY_4488BEF4 = new UiColor(0x4488bef4);
    public static final UiColor LEGACY_66000000 = new UiColor(0x66000000);
    public static final UiColor LEGACY_66324126 = new UiColor(0x66324126);
    public static final UiColor LEGACY_66334455 = new UiColor(0x66334455);
    public static final UiColor LEGACY_66566A7C = new UiColor(0x66566a7c);
    public static final UiColor LEGACY_6688BEF4 = new UiColor(0x6688bef4);
    public static final UiColor LEGACY_77313A45 = new UiColor(0x77313a45);
    public static final UiColor LEGACY_78000000 = new UiColor(0x78000000);
    public static final UiColor LEGACY_88202B36 = new UiColor(0x88202b36);
    public static final UiColor LEGACY_88A0B4C8 = new UiColor(0x88a0b4c8);
    public static final UiColor LEGACY_88D0D8E4 = new UiColor(0x88d0d8e4);
    public static final UiColor LEGACY_99101620 = new UiColor(0x99101620);
    public static final UiColor LEGACY_AA131313 = new UiColor(0xaa131313);
    public static final UiColor LEGACY_AA1A1A1A = new UiColor(0xaa1a1a1a);
    public static final UiColor LEGACY_AA1A1E24 = new UiColor(0xaa1a1e24);
    public static final UiColor LEGACY_AA1A212B = new UiColor(0xaa1a212b);
    public static final UiColor LEGACY_AA1D2A37 = new UiColor(0xaa1d2a37);
    public static final UiColor LEGACY_AA20262E = new UiColor(0xaa20262e);
    public static final UiColor LEGACY_AA202731 = new UiColor(0xaa202731);
    public static final UiColor LEGACY_AA202832 = new UiColor(0xaa202832);
    public static final UiColor LEGACY_AA202833 = new UiColor(0xaa202833);
    public static final UiColor LEGACY_AA214131 = new UiColor(0xaa214131);
    public static final UiColor LEGACY_AA223B2E = new UiColor(0xaa223b2e);
    public static final UiColor LEGACY_AA24303A = new UiColor(0xaa24303a);
    public static final UiColor LEGACY_AA24303C = new UiColor(0xaa24303c);
    public static final UiColor LEGACY_AA253043 = new UiColor(0xaa253043);
    public static final UiColor LEGACY_AA25364A = new UiColor(0xaa25364a);
    public static final UiColor LEGACY_AA2A2A2A = new UiColor(0xaa2a2a2a);
    public static final UiColor LEGACY_AA2A3340 = new UiColor(0xaa2a3340);
    public static final UiColor LEGACY_AA2A3846 = new UiColor(0xaa2a3846);
    public static final UiColor LEGACY_AA2B3642 = new UiColor(0xaa2b3642);
    public static final UiColor LEGACY_AA2C5A41 = new UiColor(0xaa2c5a41);
    public static final UiColor LEGACY_AA304153 = new UiColor(0xaa304153);
    public static final UiColor LEGACY_AA345A38 = new UiColor(0xaa345a38);
    public static final UiColor LEGACY_AA36506A = new UiColor(0xaa36506a);
    public static final UiColor LEGACY_AA3E5368 = new UiColor(0xaa3e5368);
    public static final UiColor LEGACY_AA3F2323 = new UiColor(0xaa3f2323);
    public static final UiColor LEGACY_AA3F627E = new UiColor(0xaa3f627e);
    public static final UiColor LEGACY_AA402626 = new UiColor(0xaa402626);
    public static final UiColor LEGACY_AA473030 = new UiColor(0xaa473030);
    public static final UiColor LEGACY_AA4C6E39 = new UiColor(0xaa4c6e39);
    public static final UiColor LEGACY_AA5A3D2A = new UiColor(0xaa5a3d2a);
    public static final UiColor LEGACY_AACEE1FF = new UiColor(0xaacee1ff);
    public static final UiColor LEGACY_AFC0D3 = new UiColor(0xafc0d3);
    public static final UiColor LEGACY_BB17202A = new UiColor(0xbb17202a);
    public static final UiColor LEGACY_BFD2E6 = new UiColor(0xbfd2e6);
    public static final UiColor LEGACY_C9F0C7 = new UiColor(0xc9f0c7);
    public static final UiColor LEGACY_CC101820 = new UiColor(0xcc101820);
    public static final UiColor LEGACY_CC17202A = new UiColor(0xcc17202a);
    public static final UiColor LEGACY_CC233345 = new UiColor(0xcc233345);
    public static final UiColor LEGACY_CC243341 = new UiColor(0xcc243341);
    public static final UiColor LEGACY_CC2B3440 = new UiColor(0xcc2b3440);
    public static final UiColor LEGACY_CC2B4055 = new UiColor(0xcc2b4055);
    public static final UiColor LEGACY_CC2C873F = new UiColor(0xcc2c873f);
    public static final UiColor LEGACY_CC2E5B43 = new UiColor(0xcc2e5b43);
    public static final UiColor LEGACY_CC2E6A50 = new UiColor(0xcc2e6a50);
    public static final UiColor LEGACY_CC2F5B45 = new UiColor(0xcc2f5b45);
    public static final UiColor LEGACY_CC2F6B47 = new UiColor(0xcc2f6b47);
    public static final UiColor LEGACY_CC334052 = new UiColor(0xcc334052);
    public static final UiColor LEGACY_CC3A2630 = new UiColor(0xcc3a2630);
    public static final UiColor LEGACY_CC3AA156 = new UiColor(0xcc3aa156);
    public static final UiColor LEGACY_CC684040 = new UiColor(0xcc684040);
    public static final UiColor LEGACY_D80D1117 = new UiColor(0xd80d1117);
    public static final UiColor LEGACY_D8E3EE = new UiColor(0xd8e3ee);
    public static final UiColor LEGACY_D8E6F5 = new UiColor(0xd8e6f5);
    public static final UiColor LEGACY_DD05070B = new UiColor(0xdd05070b);
    public static final UiColor LEGACY_DDDDDD = new UiColor(0xdddddd);
    public static final UiColor LEGACY_E4ECF6 = new UiColor(0xe4ecf6);
    public static final UiColor LEGACY_EAF2FF = new UiColor(0xeaf2ff);
    public static final UiColor LEGACY_EE121922 = new UiColor(0xee121922);
    public static final UiColor LEGACY_EE151A22 = new UiColor(0xee151a22);
    public static final UiColor LEGACY_EE171C24 = new UiColor(0xee171c24);
    public static final UiColor LEGACY_EE1A2430 = new UiColor(0xee1a2430);
    public static final UiColor LEGACY_EF111820 = new UiColor(0xef111820);
    public static final UiColor LEGACY_F0182028 = new UiColor(0xf0182028);
    public static final UiColor LEGACY_F0C4C4 = new UiColor(0xf0c4c4);
    public static final UiColor LEGACY_F2F7FF = new UiColor(0xf2f7ff);
    public static final UiColor LEGACY_FF0A0D12 = new UiColor(0xff0a0d12);
    public static final UiColor LEGACY_FF0B0E13 = new UiColor(0xff0b0e13);
    public static final UiColor LEGACY_FF0B1016 = new UiColor(0xff0b1016);
    public static final UiColor LEGACY_FF0B1017 = new UiColor(0xff0b1017);
    public static final UiColor LEGACY_FF0C0D10 = new UiColor(0xff0c0d10);
    public static final UiColor LEGACY_FF0D1015 = new UiColor(0xff0d1015);
    public static final UiColor LEGACY_FF0D1117 = new UiColor(0xff0d1117);
    public static final UiColor LEGACY_FF0D1218 = new UiColor(0xff0d1218);
    public static final UiColor LEGACY_FF10161D = new UiColor(0xff10161d);
    public static final UiColor LEGACY_FF11161C = new UiColor(0xff11161c);
    public static final UiColor LEGACY_FF11171E = new UiColor(0xff11171e);
    public static final UiColor LEGACY_FF111821 = new UiColor(0xff111821);
    public static final UiColor LEGACY_FF111921 = new UiColor(0xff111921);
    public static final UiColor LEGACY_FF123A1D = new UiColor(0xff123a1d);
    public static final UiColor LEGACY_FF161A20 = new UiColor(0xff161a20);
    public static final UiColor LEGACY_FF1B222C = new UiColor(0xff1b222c);
    public static final UiColor LEGACY_FF202833 = new UiColor(0xff202833);
    public static final UiColor LEGACY_FF3C4A5A = new UiColor(0xff3c4a5a);
    public static final UiColor LEGACY_FF405064 = new UiColor(0xff405064);
    public static final UiColor LEGACY_FF415266 = new UiColor(0xff415266);
    public static final UiColor LEGACY_FF43566B = new UiColor(0xff43566b);
    public static final UiColor LEGACY_FF46576A = new UiColor(0xff46576a);
    public static final UiColor LEGACY_FF4B5F73 = new UiColor(0xff4b5f73);
    public static final UiColor LEGACY_FF4E5A67 = new UiColor(0xff4e5a67);
    public static final UiColor LEGACY_FF596D84 = new UiColor(0xff596d84);
    public static final UiColor LEGACY_FF5C7188 = new UiColor(0xff5c7188);
    public static final UiColor LEGACY_FF5E738A = new UiColor(0xff5e738a);
    public static final UiColor LEGACY_FF61758A = new UiColor(0xff61758a);
    public static final UiColor LEGACY_FF64788E = new UiColor(0xff64788e);
    public static final UiColor LEGACY_FF667D95 = new UiColor(0xff667d95);
    public static final UiColor LEGACY_FF67758A = new UiColor(0xff67758a);
    public static final UiColor LEGACY_FF6B8FA0 = new UiColor(0xff6b8fa0);
    public static final UiColor LEGACY_FF6C8197 = new UiColor(0xff6c8197);
    public static final UiColor LEGACY_FF6C839A = new UiColor(0xff6c839a);
    public static final UiColor LEGACY_FF6E8799 = new UiColor(0xff6e8799);
    public static final UiColor LEGACY_FF742833 = new UiColor(0xff742833);
    public static final UiColor LEGACY_FF7489A0 = new UiColor(0xff7489a0);
    public static final UiColor LEGACY_FF74E88C = new UiColor(0xff74e88c);
    public static final UiColor LEGACY_FF78B28C = new UiColor(0xff78b28c);
    public static final UiColor LEGACY_FF7F92A8 = new UiColor(0xff7f92a8);
    public static final UiColor LEGACY_FF85A7C5 = new UiColor(0xff85a7c5);
    public static final UiColor LEGACY_FF88BEF4 = new UiColor(0xff88bef4);
    public static final UiColor LEGACY_FF8BA4B8 = new UiColor(0xff8ba4b8);
    public static final UiColor LEGACY_FF8EA5B8 = new UiColor(0xff8ea5b8);
    public static final UiColor LEGACY_FF8EC8FF = new UiColor(0xff8ec8ff);
    public static final UiColor LEGACY_FF8EEA9B = new UiColor(0xff8eea9b);
    public static final UiColor LEGACY_FF8FA8C3 = new UiColor(0xff8fa8c3);
    public static final UiColor LEGACY_FF9A3340 = new UiColor(0xff9a3340);
    public static final UiColor LEGACY_FF9EACB9 = new UiColor(0xff9eacb9);
    public static final UiColor LEGACY_FF9FB0C2 = new UiColor(0xff9fb0c2);
    public static final UiColor LEGACY_FF9FB2C4 = new UiColor(0xff9fb2c4);
    public static final UiColor LEGACY_FF9FB8D3 = new UiColor(0xff9fb8d3);
    public static final UiColor LEGACY_FF9FC7E6 = new UiColor(0xff9fc7e6);
    public static final UiColor LEGACY_FFA06010 = new UiColor(0xffa06010);
    public static final UiColor LEGACY_FFAEE8AE = new UiColor(0xffaee8ae);
    public static final UiColor LEGACY_FFB07820 = new UiColor(0xffb07820);
    public static final UiColor LEGACY_FFB7CDE2 = new UiColor(0xffb7cde2);
    public static final UiColor LEGACY_FFB8C7D6 = new UiColor(0xffb8c7d6);
    public static final UiColor LEGACY_FFB8FFB8 = new UiColor(0xffb8ffb8);
    public static final UiColor LEGACY_FFBCD0E2 = new UiColor(0xffbcd0e2);
    public static final UiColor LEGACY_FFC03020 = new UiColor(0xffc03020);
    public static final UiColor LEGACY_FFC89030 = new UiColor(0xffc89030);
    public static final UiColor LEGACY_FFCDEBFF = new UiColor(0xffcdebff);
    public static final UiColor LEGACY_FFCFE3F7 = new UiColor(0xffcfe3f7);
    public static final UiColor LEGACY_FFD04040 = new UiColor(0xffd04040);
    public static final UiColor LEGACY_FFD08030 = new UiColor(0xffd08030);
    public static final UiColor LEGACY_FFD6AAAA = new UiColor(0xffd6aaaa);
    public static final UiColor LEGACY_FFD8E6F5 = new UiColor(0xffd8e6f5);
    public static final UiColor LEGACY_FFE07070 = new UiColor(0xffe07070);
    public static final UiColor LEGACY_FFE7C46A = new UiColor(0xffe7c46a);
    public static final UiColor LEGACY_FFE7F2FF = new UiColor(0xffe7f2ff);
    public static final UiColor LEGACY_FFE8C840 = new UiColor(0xffe8c840);
    public static final UiColor LEGACY_FFE8F4FF = new UiColor(0xffe8f4ff);
    public static final UiColor LEGACY_FFE8F6FF = new UiColor(0xffe8f6ff);
    public static final UiColor LEGACY_FFEAF2FF = new UiColor(0xffeaf2ff);
    public static final UiColor LEGACY_FFF7E6A8 = new UiColor(0xfff7e6a8);
    public static final UiColor LEGACY_FFFFA2AE = new UiColor(0xffffa2ae);
    public static final UiColor LEGACY_FFFFAA = new UiColor(0xffffaa);
    public static final UiColor LEGACY_FFFFB0B0 = new UiColor(0xffffb0b0);
    public static final UiColor LEGACY_FFFFC06C = new UiColor(0xffffc06c);
    public static final UiColor LEGACY_FFFFC472 = new UiColor(0xffffc472);
    public static final UiColor LEGACY_FFFFC96B = new UiColor(0xffffc96b);
    public static final UiColor LEGACY_FFFFD080 = new UiColor(0xffffd080);
    public static final UiColor LEGACY_FFFFD1D7 = new UiColor(0xffffd1d7);
    public static final UiColor LEGACY_FFFFD4D4 = new UiColor(0xffffd4d4);
    public static final UiColor LEGACY_FFFFFF = new UiColor(0xffffff);
    public static final UiColor LEGACY_FFFFFFFF = new UiColor(0xffffffff);

    private static UiColor theme(UiThemeToken token, int legacyArgb) {
        return UiColor.themeComponent(
                UiThemeCoverageCatalog.ComponentFamily.GLOBAL_CHROME, token, legacyArgb);
    }

    private RtsMainlineTheme() {
    }
}
