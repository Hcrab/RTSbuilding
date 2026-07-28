package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 合成数量窗口在生产与离屏之间共享的域颜色。
 *
 * <p>这里只保留配方可用/缺料等业务语义；普通按钮、输入框和窗口 chrome 继续
 * 使用全局主线主题。该类不负责布局、文字内容或合成请求。</p>
 */
public final class CraftQuantityStyle {
    public static final UiColor MODAL_SCRIM = new UiColor(0x78000000);
    public static final UiColor DIALOG_BACKGROUND = new UiColor(0xEE171C24);
    public static final UiColor CLOSE_BACKGROUND = new UiColor(0xCC2B3440);
    public static final UiColor ITEM_LABEL = new UiColor(0xFFE4ECF6);
    public static final UiColor MUTED_TEXT = new UiColor(0xFFAFC0D3);
    public static final UiColor SECTION_LABEL = new UiColor(0xFFD8E3EE);
    public static final UiColor OPTIONS_BACKGROUND = new UiColor(0xAA202833);
    public static final UiColor OPTIONS_BORDER_LIGHT = new UiColor(0xFF61758A);
    public static final UiColor OPTIONS_BORDER_DARK = new UiColor(0xFF11161C);
    public static final UiColor CRAFTABLE_ROW = new UiColor(0xAA223B2E);
    public static final UiColor MISSING_ROW = new UiColor(0xAA402626);
    public static final UiColor CRAFTABLE_ROW_SELECTED = new UiColor(0xCC2E5B43);
    public static final UiColor MISSING_ROW_SELECTED = new UiColor(0xCC684040);
    public static final UiColor ROW_TEXT = new UiColor(0xFFF2F7FF);
    public static final UiColor CRAFTABLE_BADGE = new UiColor(0xFFC9F0C7);
    public static final UiColor MISSING_BADGE = new UiColor(0xFFF0C4C4);
    public static final UiColor DETAIL = new UiColor(0xFFBCD0E2);
    public static final UiColor DETAIL_MISSING = new UiColor(0xFFD6AAAA);
    public static final UiColor INPUT_SELECTION = new UiColor(0xFF2F5D9B);

    private CraftQuantityStyle() {
    }

    public static UiColor rowBackground(boolean craftable, boolean selected) {
        if (selected) {
            return craftable ? CRAFTABLE_ROW_SELECTED : MISSING_ROW_SELECTED;
        }
        return craftable ? CRAFTABLE_ROW : MISSING_ROW;
    }

    public static UiColor badge(boolean craftable) {
        return craftable ? CRAFTABLE_BADGE : MISSING_BADGE;
    }

    public static UiColor detail(boolean missing) {
        return missing ? DETAIL_MISSING : DETAIL;
    }
}
