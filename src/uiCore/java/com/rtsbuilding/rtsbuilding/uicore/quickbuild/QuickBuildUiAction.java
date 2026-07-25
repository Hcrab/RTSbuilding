package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** 快速建造窗的纯输入语义。 */
public final class QuickBuildUiAction {
    public enum Type { SELECT_MODE, SELECT_SHAPE, ACTIVATE_CONTROL, SET_CHAIN_LIMIT, CLOSE }
    public final Type type;
    public final QuickBuildUiMode mode;
    public final QuickBuildUiShape shape;
    public final QuickBuildUiControl.Id control;
    public final int value;
    private QuickBuildUiAction(Type type, QuickBuildUiMode mode, QuickBuildUiShape shape,
                               QuickBuildUiControl.Id control, int value) {
        this.type=type; this.mode=mode; this.shape=shape; this.control=control; this.value=value;
    }
    public static QuickBuildUiAction mode(QuickBuildUiMode v){return new QuickBuildUiAction(Type.SELECT_MODE,v,null,null,0);}
    public static QuickBuildUiAction shape(QuickBuildUiShape v){return new QuickBuildUiAction(Type.SELECT_SHAPE,null,v,null,0);}
    public static QuickBuildUiAction control(QuickBuildUiControl.Id v){return new QuickBuildUiAction(Type.ACTIVATE_CONTROL,null,null,v,0);}
    public static QuickBuildUiAction limit(int v){return new QuickBuildUiAction(Type.SET_CHAIN_LIMIT,null,null,null,v);}
    public static QuickBuildUiAction close(){return new QuickBuildUiAction(Type.CLOSE,null,null,null,0);}
}
