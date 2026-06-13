package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Shared constants and utility methods for the transfer sub-package.
 */
final class RtsTransferUtils {
    static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;

    private RtsTransferUtils() {
    }

    /**
     * Returns whether the player's main inventory should be included as a visible
     * source/sink in the storage browser view.
     */
    static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        if (player == null || player.containerMenu instanceof RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && session.linkedStorages.isEmpty() && !hasPrimaryBdNetwork(player)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    /**
     * Checks whether a quick-move from linked storage should target the player's
     * main inventory (instead of the currently open menu's slots).
     */
    static boolean movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu
                || (menu instanceof CraftingMenu && !(menu instanceof RtsCraftTerminalMenu));
    }

    static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(PLAYER_HOTBAR_SLOT_COUNT - 1, slot));
    }

    static int getPlayerMainInventoryStart(ServerPlayer player) {
        return 0;
    }

    static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }

    private static boolean hasPrimaryBdNetwork(ServerPlayer player) {
        return com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat.hasPrimaryNetwork(player);
    }
}
