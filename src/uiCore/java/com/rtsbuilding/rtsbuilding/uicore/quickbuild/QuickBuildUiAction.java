package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/**
 * Quick Build 的纯输入语义。
 *
 * <p>动作只描述玩家意图，不绑定 Minecraft 控件、网络包或服务端执行。目录选择因此既能在
 * Build 下投影为 Smart Fill，也能在 Destroy 下投影为便利工具，而无需增加第三个主模式按钮。</p>
 */
public final class QuickBuildUiAction {
    public enum Type {
        SELECT_MODE, SELECT_SHAPE, ACTIVATE_CONTROL, SET_CHAIN_LIMIT,
        SELECT_CATALOG_PAGE, SELECT_CONVENIENCE_TOOL, SET_CONVENIENCE_PARAMETER,
        SET_SMART_FILL_MAX_BLOCKS, SET_SMART_FILL_DIAMETER,
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
            QuickBuildUiControl.Id control, int value, QuickBuildUiCatalogPage catalogPage,
            QuickBuildUiConvenienceTool convenienceTool,
            QuickBuildUiConvenienceParameter convenienceParameter) {
        this.type = type;
        this.mode = mode;
        this.shape = shape;
        this.control = control;
        this.value = value;
        this.catalogPage = catalogPage;
        this.convenienceTool = convenienceTool;
        this.convenienceParameter = convenienceParameter;
    }

    public static QuickBuildUiAction mode(QuickBuildUiMode value) {
        return new QuickBuildUiAction(Type.SELECT_MODE, value, null, null, 0,
                null, null, null);
    }

    public static QuickBuildUiAction shape(QuickBuildUiShape value) {
        return new QuickBuildUiAction(Type.SELECT_SHAPE, null, value, null, 0,
                null, null, null);
    }

    public static QuickBuildUiAction control(QuickBuildUiControl.Id value) {
        return new QuickBuildUiAction(Type.ACTIVATE_CONTROL, null, null, value, 0,
                null, null, null);
    }

    public static QuickBuildUiAction limit(int value) {
        return new QuickBuildUiAction(Type.SET_CHAIN_LIMIT, null, null, null, value,
                null, null, null);
    }

    public static QuickBuildUiAction catalog(QuickBuildUiCatalogPage value) {
        return new QuickBuildUiAction(Type.SELECT_CATALOG_PAGE, null, null, null, 0,
                value, null, null);
    }

    public static QuickBuildUiAction convenienceTool(QuickBuildUiConvenienceTool value) {
        return new QuickBuildUiAction(Type.SELECT_CONVENIENCE_TOOL, null, null, null, 0,
                null, value, null);
    }

    public static QuickBuildUiAction convenienceParameter(
            QuickBuildUiConvenienceParameter parameter, int value) {
        return new QuickBuildUiAction(Type.SET_CONVENIENCE_PARAMETER, null, null, null, value,
                null, null, parameter);
    }

    public static QuickBuildUiAction smartFillMaxBlocks(int value) {
        return new QuickBuildUiAction(Type.SET_SMART_FILL_MAX_BLOCKS, null, null, null, value,
                null, null, null);
    }

    public static QuickBuildUiAction smartFillDiameter(int value) {
        return new QuickBuildUiAction(Type.SET_SMART_FILL_DIAMETER, null, null, null, value,
                null, null, null);
    }

    public static QuickBuildUiAction close() {
        return new QuickBuildUiAction(Type.CLOSE, null, null, null, 0,
                null, null, null);
    }
}
