package com.rtsbuilding.rtsbuilding.uicore.craft;

/** 合成数量 reducer 的纯结果。 */
public final class CraftQuantityTransition {
    public enum Command { NONE, CONFIRM, CANCEL }
    public final CraftQuantityState state;
    public final Command command;
    public final String recipeId;
    public final int craftCount;

    public CraftQuantityTransition(CraftQuantityState state, Command command,
                                   String recipeId, int craftCount) {
        this.state = state;
        this.command = command;
        this.recipeId = recipeId == null ? "" : recipeId;
        this.craftCount = craftCount;
    }
}
