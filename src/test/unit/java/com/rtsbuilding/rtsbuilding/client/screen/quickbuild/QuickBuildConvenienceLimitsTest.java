package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickBuildConvenienceLimitsTest {
    @Test
    void oldIntegerMaximumPreferenceBecomesUsableWithoutErasingStoredIntent() {
        var stored = new QuickBuildUiConvenienceSettings(3, 3, Integer.MAX_VALUE, 0, 15, 256);
        var limits = new QuickBuildConvenienceLimits(SelectionVolumeLimit.defaults(), 8192);

        var effective = limits.effective(stored);

        assertEquals(3, effective.sizeX());
        assertEquals(3, effective.sizeY());
        assertEquals(64, effective.sizeZ());
        assertEquals(Integer.MAX_VALUE, stored.sizeZ());
    }

    @Test
    void configuredLongThinSelectionsRemainAvailable() {
        var limits = new QuickBuildConvenienceLimits(
                new SelectionVolumeLimit(8192, 8192, 128, 1024), 20000);
        var stored = new QuickBuildUiConvenienceSettings(4096, 1, 1, 0, 15, 12000);

        assertEquals(stored, limits.effective(stored));
        assertEquals(8192, limits.maximum(QuickBuildUiConvenienceParameter.SIZE_X));
        assertEquals(128, limits.maximum(QuickBuildUiConvenienceParameter.SIZE_Y));
        assertEquals(1024, limits.maximum(QuickBuildUiConvenienceParameter.SIZE_Z));
        assertEquals(20000, limits.maximum(QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS));
    }

    @Test
    void unlimitedAxisConfigurationStillUsesTheRealVolumeCapacity() {
        var limits = new QuickBuildConvenienceLimits(
                new SelectionVolumeLimit(4096, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), 8192);

        assertEquals(4096, limits.maximum(QuickBuildUiConvenienceParameter.SIZE_Z));
        assertEquals(15, limits.maximum(QuickBuildUiConvenienceParameter.CHUNK_UP));
        assertEquals(15, limits.maximum(QuickBuildUiConvenienceParameter.CHUNK_DOWN));
    }

    @Test
    void chunkExtensionExcludesAnchorLayerAndHandlesSubChunkBudgets() {
        var normal = new QuickBuildConvenienceLimits(SelectionVolumeLimit.defaults(), 8192);
        var small = new QuickBuildConvenienceLimits(new SelectionVolumeLimit(100, 64, 64, 64), 8192);

        assertEquals(63, normal.maximum(QuickBuildUiConvenienceParameter.CHUNK_DOWN));
        assertEquals(0, small.maximum(QuickBuildUiConvenienceParameter.CHUNK_DOWN));
        assertEquals(0, small.clamp(QuickBuildUiConvenienceParameter.CHUNK_DOWN, Integer.MAX_VALUE));
    }
}
