package com.rtsbuilding.rtsbuilding.client.screen.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsPickBlockPlacementSelectorTest {
    @Test
    void matchingHotbarBlockUsesNativeHotbarSelection() {
        var selection = RtsPickBlockPlacementSelector.resolve(
                RtsPickBlockPlacementSelector.MAIN_INVENTORY_SIZE,
                slot -> slot == 4);

        assertEquals(RtsPickBlockPlacementSelector.Route.HOTBAR, selection.route());
        assertEquals(4, selection.slot());
    }

    @Test
    void matchingMainInventoryBlockUsesVanillaPickSwap() {
        var selection = RtsPickBlockPlacementSelector.resolve(
                RtsPickBlockPlacementSelector.MAIN_INVENTORY_SIZE,
                slot -> slot == 17);

        assertEquals(RtsPickBlockPlacementSelector.Route.MAIN_INVENTORY, selection.route());
        assertEquals(17, selection.slot());
    }

    @Test
    void absentBlockKeepsRemoteStorageOrCreativeSelection() {
        var selection = RtsPickBlockPlacementSelector.resolve(
                RtsPickBlockPlacementSelector.MAIN_INVENTORY_SIZE,
                slot -> false);

        assertEquals(RtsPickBlockPlacementSelector.Route.REMOTE, selection.route());
        assertEquals(-1, selection.slot());
    }

}
