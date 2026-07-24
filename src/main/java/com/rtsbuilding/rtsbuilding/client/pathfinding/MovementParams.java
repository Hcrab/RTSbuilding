package com.rtsbuilding.rtsbuilding.client.pathfinding;

import org.jetbrains.annotations.Nullable;


public record MovementParams(
        double speed,
        boolean threeDimensional,
        boolean allowSprint,
        boolean applyApproachSlowdown,
        boolean applyEntityInsideSlow,
        @Nullable StuckBehavior stuckBehavior,
        boolean useInputSystem,
        boolean arrivalCheckHorizontalOnly
) {

    
    public MovementParams(
            double speed,
            boolean threeDimensional,
            boolean allowSprint,
            boolean applyApproachSlowdown,
            boolean applyEntityInsideSlow,
            @Nullable StuckBehavior stuckBehavior
    ) {
        this(speed, threeDimensional, allowSprint, applyApproachSlowdown,
                applyEntityInsideSlow, stuckBehavior, false, false);
    }

    
    public enum StuckBehavior {
        
        JUMP,

        
        FLOAT_UP,

        
        FLY_UP,

        
        NONE
    }
}
