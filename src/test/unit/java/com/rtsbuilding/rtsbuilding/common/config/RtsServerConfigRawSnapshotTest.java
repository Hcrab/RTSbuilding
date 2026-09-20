package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模拟原始读取先于 ConfigSpec 补默认/修正的加载时序。 */
class RtsServerConfigRawSnapshotTest {
    @Test
    void rawAxisValueSurvivesFrameworkCorrectionBoundary() {
        String old = "maxSelectionSizeX = 36\nmaxSelectionSizeY = 36\n";
        RtsServerConfigRawSnapshot beforeSpec = RtsServerConfigRawSnapshot.read("old-server.toml", () -> old);
        String correctedByFramework = old.replace("maxSelectionSizeX = 36", "maxSelectionSizeX = 64");
        assertTrue(beforeSpec.hasBytes());
        assertTrue(beforeSpec.contents().contains("maxSelectionSizeX = 36"));
        assertFalse(beforeSpec.contents().equals(correctedByFramework));
    }

    @Test
    void unreadableSourceDoesNotLookLikeNewFile() {
        RtsServerConfigRawSnapshot failed = RtsServerConfigRawSnapshot.read(
                "broken-server.toml", () -> { throw new IllegalStateException("denied"); });
        assertTrue(failed.failure());
        assertFalse(failed.isNewFile());
    }
}
