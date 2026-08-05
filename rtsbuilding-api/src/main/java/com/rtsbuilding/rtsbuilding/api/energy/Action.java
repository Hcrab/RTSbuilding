package com.rtsbuilding.rtsbuilding.api.energy;

import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Represents an action to perform on an energy container or handler.
 * <p>
 * Modeled after Mekanism's {@code mekanism.api.Action}: callers that only want to
 * probe capacity without actually mutating state pass {@link #SIMULATE}, while
 * callers that want to commit the change pass {@link #EXECUTE}.
 */
public enum Action {
    EXECUTE(FluidAction.EXECUTE),
    SIMULATE(FluidAction.SIMULATE);

    private final FluidAction fluidAction;

    Action(FluidAction fluidAction) {
        this.fluidAction = fluidAction;
    }

    /**
     * @return {@code true} if this action represents execution.
     */
    public boolean execute() {
        return this == EXECUTE;
    }

    /**
     * @return {@code true} if this action represents simulation.
     */
    public boolean simulate() {
        return this == SIMULATE;
    }

    /**
     * Converts this action to the corresponding {@link FluidAction}.
     */
    public FluidAction toFluidAction() {
        return fluidAction;
    }

    /**
     * Compounds this action with a boolean based execution.
     *
     * @param execute {@code true} if it should execute if this action already is an execute action.
     *
     * @return Compounded action.
     */
    public Action combine(boolean execute) {
        return get(execute && execute());
    }

    /**
     * Gets an action based on a boolean representing execution.
     *
     * @param execute {@code true} for {@link #EXECUTE}.
     *
     * @return Action.
     */
    public static Action get(boolean execute) {
        return execute ? EXECUTE : SIMULATE;
    }

    /**
     * Gets an action from the corresponding {@link FluidAction}.
     *
     * @param action FluidAction.
     *
     * @return Action.
     */
    public static Action fromFluidAction(FluidAction action) {
        if (action == FluidAction.EXECUTE) {
            return EXECUTE;
        }
        return SIMULATE;
    }
}
