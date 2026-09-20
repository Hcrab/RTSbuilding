package com.rtsbuilding.rtsbuilding.common.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 模拟 Forge 原始读取先于 ConfigSpec 默认补齐和修正。 */
class RtsServerConfigRawSnapshotTest {
    @Test
    void rawValueIsKeptBeforeFrameworkCorrection() {
        String old = "maxSelectionSizeX = 36\n";
        RtsServerConfigRawSnapshot snapshot = RtsServerConfigRawSnapshot.read("old.toml", () -> old);
        String corrected = old.replace("36", "64");
        assertTrue(snapshot.contents().contains("36")); assertFalse(snapshot.contents().equals(corrected));
    }
    @Test
    void unreadableSourceIsNotNewFile() {
        RtsServerConfigRawSnapshot snapshot = RtsServerConfigRawSnapshot.read("broken.toml", () -> { throw new IllegalStateException(); });
        assertTrue(snapshot.failure()); assertFalse(snapshot.isNewFile());
    }
}
