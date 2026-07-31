package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** 快速建造窗的纯输入语义。 */
public final class QuickBuildUiAction {
    public enum Type {
        SELECT_MODE, SELECT_SHAPE, ACTIVATE_CONTROL, SET_CHAIN_LIMIT,
        SELECT_CATALOG_PAGE, SELECT_CONVENIENCE_TOOL, SET_CONVENIENCE_PARAMETER,
        CLOSE
    }
    public final Type type;
    public final QuickBuildUiMode mode;
    public final QuickBuildUiShape shape;
    public final QuickBuildUiControl.Id control;
    public final int value;
    public final QuickBuildUiCatalogPage catalogPage;
    public final QuickBuildUiConvenienceTool convenienceTool;
    public final QuickBuildUiConvenienceParameter convenienceParameter;
    private QuickBuildUiAction(Type type, QuickBuildUiMode mode, QuickBuildUiShape shape,
                               QuickBuildUiControl.Id control, int value,
                               QuickBuildUiCatalogPage catalogPage,
                               QuickBuildUiConvenienceTool convenienceTool,
                               QuickBuildUiConvenienceParameter convenienceParameter) {
        this.type=type; this.mode=mode; this.shape=shape; this.control=control; this.value=value;
        this.catalogPage=catalogPage; this.convenienceTool=convenienceTool;
        this.convenienceParameter=convenienceParameter;
    }
    public static QuickBuildUiAction mode(QuickBuildUiMode v){return new QuickBuildUiAction(Type.SELECT_MODE,v,null,null,0,null,null,null);}
    public static QuickBuildUiAction shape(QuickBuildUiShape v){return new QuickBuildUiAction(Type.SELECT_SHAPE,null,v,null,0,null,null,null);}
    public static QuickBuildUiAction control(QuickBuildUiControl.Id v){return new QuickBuildUiAction(Type.ACTIVATE_CONTROL,null,null,v,0,null,null,null);}
    public static QuickBuildUiAction limit(int v){return new QuickBuildUiAction(Type.SET_CHAIN_LIMIT,null,null,null,v,null,null,null);}
    public static QuickBuildUiAction catalog(QuickBuildUiCatalogPage v){return new QuickBuildUiAction(Type.SELECT_CATALOG_PAGE,null,null,null,0,v,null,null);}
    public static QuickBuildUiAction convenienceTool(QuickBuildUiConvenienceTool v){return new QuickBuildUiAction(Type.SELECT_CONVENIENCE_TOOL,null,null,null,0,null,v,null);}
    public static QuickBuildUiAction convenienceParameter(QuickBuildUiConvenienceParameter p,int v){return new QuickBuildUiAction(Type.SET_CONVENIENCE_PARAMETER,null,null,null,v,null,null,p);}
    public static QuickBuildUiAction close(){return new QuickBuildUiAction(Type.CLOSE,null,null,null,0,null,null,null);}
}
