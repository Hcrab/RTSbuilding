package com.rtsbuilding.rtsbuilding.uicore.craftterminal;

/**
 * 合成终端中可点击的语义动作。
 *
 * <p>这里不保存 Minecraft 菜单、网络包或鼠标状态。布局层只返回语义动作，正式屏幕再决定
 * 如何执行，因此绘制、命中测试和离屏预览不会各自维护一套坐标判断。</p>
 */
public enum CraftTerminalUiAction {
    SEARCH,
    SEARCH_CLEAR,
    SEARCH_MODE,
    SEARCH_PIN,
    CYCLE_ROWS,
    SORT,
    SORT_DIRECTION,
    CLEAR_TO_STORAGE,
    CLEAR_TO_INVENTORY,
    DEPOSIT_ALL,
    DEPOSIT_HOTBAR,
    SCROLLBAR
}
