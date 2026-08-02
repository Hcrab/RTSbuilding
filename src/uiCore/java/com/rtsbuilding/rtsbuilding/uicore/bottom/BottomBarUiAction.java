package com.rtsbuilding.rtsbuilding.uicore.bottom;

/** 底部终端的无 Minecraft 输入语义。 */
public final class BottomBarUiAction {
    public enum Type {
        SELECT_TAB, REFRESH, OPEN_GUIDE, OPEN_PLUGINS,
        SET_SEARCH, CLEAR_SEARCH, PREVIOUS_PAGE, NEXT_PAGE,
        CYCLE_SORT, TOGGLE_SORT_DIRECTION, ADJUST_HEIGHT,
        SELECT_CATEGORY, TOGGLE_CATEGORY, SCROLL_CATEGORY,
        SELECT_STORAGE, SELECT_CREATIVE, SELECT_RECENT, SELECT_FLUID,
        SELECT_TOOL, SELECT_EMPTY_HAND, IMPORT_HOTBAR, STORE_FLUID_TOOL,
        SELECT_PIN, CLEAR_PIN, STORE_FLUID_PIN, CYCLE_PIN_PAGE,
        SET_CRAFT_SEARCH, APPLY_CRAFT_SEARCH, TOGGLE_CRAFT_UNAVAILABLE,
        SCROLL_CRAFT, OPEN_CRAFT_QUANTITY, OPEN_CRAFT_TERMINAL,
        SELECT_GUI_BINDING, TOGGLE_GUI_BINDING_PENDING, CLEAR_GUI_BINDING
    }

    public final Type type;
    public final BottomBarUiTab tab;
    public final int index;
    public final int amount;
    public final int maximum;
    public final String value;

    private BottomBarUiAction(Type type, BottomBarUiTab tab, int index,
                              int amount, int maximum, String value) {
        this.type=type; this.tab=tab; this.index=index; this.amount=amount;
        this.maximum=maximum; this.value=value == null ? "" : value;
    }
    public static BottomBarUiAction simple(Type type){return new BottomBarUiAction(type,null,-1,0,0,"");}
    public static BottomBarUiAction tab(BottomBarUiTab tab){return new BottomBarUiAction(Type.SELECT_TAB,tab,-1,0,0,"");}
    public static BottomBarUiAction index(Type type,int index){return new BottomBarUiAction(type,null,index,0,0,"");}
    public static BottomBarUiAction value(Type type,String value){return new BottomBarUiAction(type,null,-1,0,0,value);}
    public static BottomBarUiAction delta(Type type,int amount,int maximum){return new BottomBarUiAction(type,null,-1,amount,maximum,"");}
}
