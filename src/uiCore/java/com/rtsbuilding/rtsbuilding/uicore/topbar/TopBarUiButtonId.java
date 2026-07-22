package com.rtsbuilding.rtsbuilding.uicore.topbar;

/** 顶栏正式按钮的稳定语义标识；布局序号与主线素材槽位保持一致。 */
public enum TopBarUiButtonId {
    INTERACT(0, true),
    LINK(1, true),
    FUNNEL(2, true),
    ROTATE(3, true),
    QUICK_BUILD(4, false),
    QUEST_DETECT(5, false),
    CHUNK_VIEW(6, false),
    RANGE_CULLING(7, false),
    GUIDE(8, false),
    DEVELOPER(9, false),
    GEAR(10, false);

    public final int layoutIndex;
    public final boolean modeButton;

    TopBarUiButtonId(int layoutIndex, boolean modeButton) {
        this.layoutIndex = layoutIndex;
        this.modeButton = modeButton;
    }
}
