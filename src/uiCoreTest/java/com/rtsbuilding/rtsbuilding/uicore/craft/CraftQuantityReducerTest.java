package com.rtsbuilding.rtsbuilding.uicore.craft;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CraftQuantityReducerTest {
    @Test
    void inputClampsAndFirstDigitReplacesDefault() {
        CraftQuantityState state = state();
        state = CraftQuantityReducer.apply(state, CraftQuantityAction.text("42")).state;
        assertEquals(42, state.quantity);
        assertEquals(999, CraftQuantityReducer.apply(state,
                CraftQuantityAction.value(CraftQuantityAction.Type.ADJUST, 2000)).state.quantity);
    }

    @Test
    void unavailableRecipeCannotConfirmButAvailableOneCan() {
        CraftQuantityState state = CraftQuantityReducer.apply(state(),
                CraftQuantityAction.value(CraftQuantityAction.Type.SELECT, 1)).state;
        assertEquals(CraftQuantityTransition.Command.NONE,
                CraftQuantityReducer.apply(state,
                        CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM)).command);
        state = CraftQuantityReducer.apply(state,
                CraftQuantityAction.value(CraftQuantityAction.Type.SELECT, 0)).state;
        assertEquals(CraftQuantityTransition.Command.CONFIRM,
                CraftQuantityReducer.apply(state,
                        CraftQuantityAction.simple(CraftQuantityAction.Type.CONFIRM)).command);
    }

    private static CraftQuantityState state() {
        return new CraftQuantityState(true, "Chest", "minecraft:chest", Arrays.asList(
                new CraftQuantityOption("minecraft:chest", "8 planks", "", 1, true),
                new CraftQuantityOption("minecraft:trapped_chest", "", "Missing iron", 1, false)),
                0, 0, 1, 1, true);
    }
}
