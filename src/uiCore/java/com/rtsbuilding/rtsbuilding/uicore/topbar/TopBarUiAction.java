package com.rtsbuilding.rtsbuilding.uicore.topbar;

/** 顶栏点击的纯输入。 */
public final class TopBarUiAction {
    public final TopBarUiButtonId buttonId;

    private TopBarUiAction(TopBarUiButtonId buttonId) {
        if (buttonId == null) throw new IllegalArgumentException("buttonId");
        this.buttonId = buttonId;
    }

    public static TopBarUiAction click(TopBarUiButtonId buttonId) {
        return new TopBarUiAction(buttonId);
    }
}
