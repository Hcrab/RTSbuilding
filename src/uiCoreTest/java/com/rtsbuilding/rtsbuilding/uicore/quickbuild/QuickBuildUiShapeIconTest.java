package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuickBuildUiShapeIconTest {
    @Test
    void everyShapeOwnsOneUniqueContributorIconKey() {
        Set<String> keys = new HashSet<String>();
        for (QuickBuildUiShape shape : QuickBuildUiShape.values()) {
            assertFalse(shape.contributorIconKey.isEmpty(), shape.name());
            keys.add(shape.contributorIconKey);
        }
        assertEquals(QuickBuildUiShape.values().length, keys.size());
        assertEquals(new HashSet<String>(Arrays.asList("chain", "single", "line", "surface", "wall", "round", "cylinder", "ball", "cube")), keys);
    }
}
