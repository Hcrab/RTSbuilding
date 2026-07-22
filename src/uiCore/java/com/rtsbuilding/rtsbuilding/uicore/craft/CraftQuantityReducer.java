package com.rtsbuilding.rtsbuilding.uicore.craft;

/** 合成数量窗纯状态机。 */
public final class CraftQuantityReducer {
    private CraftQuantityReducer() {
    }

    public static CraftQuantityTransition apply(CraftQuantityState state, CraftQuantityAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        switch (action.type) {
            case SELECT:
                return none(state.with(action.value, state.scroll, state.quantity,
                        state.replaceOnNextDigit, state.open));
            case MOVE:
                return none(state.with(state.selectedIndex + action.value, state.scroll,
                        state.quantity, state.replaceOnNextDigit, state.open));
            case ADJUST:
                return none(state.with(state.selectedIndex, state.scroll,
                        state.quantity + action.value, false, state.open));
            case APPEND_DIGITS:
                return none(appendDigits(state, action.text));
            case BACKSPACE:
                return none(backspace(state));
            case CLEAR:
                return none(state.with(state.selectedIndex, state.scroll, 1, true, state.open));
            case CONFIRM:
                if (!state.canConfirm()) return none(state);
                CraftQuantityOption selected = state.selected();
                return new CraftQuantityTransition(state.with(state.selectedIndex, state.scroll,
                        state.quantity, state.replaceOnNextDigit, false),
                        CraftQuantityTransition.Command.CONFIRM,
                        selected.recipeId, state.quantity);
            case CANCEL:
                return new CraftQuantityTransition(state.with(state.selectedIndex, state.scroll,
                        state.quantity, state.replaceOnNextDigit, false),
                        CraftQuantityTransition.Command.CANCEL, "", 0);
            default:
                return none(state);
        }
    }

    private static CraftQuantityState appendDigits(CraftQuantityState state, String text) {
        if (text == null || text.trim().isEmpty()) return state;
        StringBuilder digits = new StringBuilder(state.replaceOnNextDigit
                ? "" : Integer.toString(state.quantity));
        for (int index = 0; index < text.length() && digits.length() < 3; index++) {
            char value = text.charAt(index);
            if (Character.isDigit(value)) digits.append(value);
        }
        if (digits.length() == 0) return state;
        String normalized = digits.toString().replaceFirst("^0+(?!$)", "");
        return state.with(state.selectedIndex, state.scroll, parse(normalized), false, state.open);
    }

    private static CraftQuantityState backspace(CraftQuantityState state) {
        String value = Integer.toString(state.quantity);
        int next = value.length() <= 1 ? 1 : parse(value.substring(0, value.length() - 1));
        return state.with(state.selectedIndex, state.scroll, next, false, state.open);
    }

    private static int parse(String text) {
        try {
            return CraftQuantityState.clamp(Integer.parseInt(text), 1, CraftQuantityState.MAX_COUNT);
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private static CraftQuantityTransition none(CraftQuantityState state) {
        return new CraftQuantityTransition(state, CraftQuantityTransition.Command.NONE, "", 0);
    }
}
