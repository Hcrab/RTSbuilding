package com.rtsbuilding.rtsbuilding.common.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementStatePresetTest {

    @Test
    void preservesMoreThanOneApprovedPropertySelection() {
        String encoded = PlacementStatePreset.withValue("", "facing", "west");
        encoded = PlacementStatePreset.withValue(encoded, "half", "top");

        assertEquals("facing=west;half=top", encoded);
    }

    @Test
    void replacingAPropertyKeepsTheCanonicalSingleValue() {
        String encoded = PlacementStatePreset.withValue("facing=north;half=bottom", "facing", "east");
        assertEquals("facing=east;half=bottom", encoded);
    }

    @Test
    void boundsAndSanitizesTheNetworkRepresentation() {
        String encoded = PlacementStatePreset.sanitize(
                "facing=west;bad pair;UPPER=value;half=top;" + repeat('x', 300));
        assertEquals("facing=west;half=top", encoded);
        assertTrue(encoded.length() <= PlacementStatePreset.MAX_ENCODED_LENGTH);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

}
