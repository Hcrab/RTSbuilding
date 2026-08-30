package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PlayerStatusStyleTest {
    @Test
    void healthAndFoodThresholdsPreserveProductionSemantics() {
        assertEquals(PlayerStatusStyle.HEALTH_HIGH, PlayerStatusStyle.health(1.0D));
        assertEquals(PlayerStatusStyle.HEALTH_MEDIUM, PlayerStatusStyle.health(0.5D));
        assertEquals(PlayerStatusStyle.HEALTH_LOW, PlayerStatusStyle.health(0.25D));
        assertEquals(PlayerStatusStyle.FOOD_HIGH, PlayerStatusStyle.food(2.0D));
        assertEquals(PlayerStatusStyle.FOOD_LOW, PlayerStatusStyle.food(-1.0D));
    }

    @Test
    void nonFiniteRatiosFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerStatusStyle.health(Double.NaN));
    }
}
