package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Shared constants and helper utility methods for the transfer sub-package.
 *
 * <p>This class provides constants and utility methods shared by multiple classes in the transfer sub-package.
 * Package-private by design, not exposed externally.
 * All methods are {@code static}, the class itself is a non-instantiable utility class.
 *
 * <p><b>Constants:</b>
 * <ul>
 *   <li>{@link #PLAYER_HOTBAR_SLOT_COUNT} = {@value #PLAYER_HOTBAR_SLOT_COUNT} — Number of player hotbar slots</li>
 *   <li>{@link #PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} = {@value #PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} —
 *       Main inventory end index (exclusive), corresponds to 36 slots (9 hotbar + 27 main inventory)</li>
 *   <li>{@link #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} = {@value #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} —
 *       Maximum auto-craft iterations per Shift+Import</li>
 * </ul>
 *
 * <p><b>Utility methods:</b>
 * <ul>
 *   <li>{@link #shouldIncludePlayerMainInventoryInStorageView(ServerPlayer, RtsStorageSession)} —
 *       Determines if the player's main inventory should be included as a visible source/sink in the storage browser view;
 *       Returns {@code true} only when no linked storage exists and no primary BD network is available</li>
 *   <li>{@link #movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu)} —
 *       Determines if quick move from linked storage should go to player inventory (instead of menu slots);
 *       Returns {@code true} for {@code InventoryMenu} or any {@code CraftingMenu} (incl. RTS craft terminal)</li>
 *   <li>{@link #clampHotbarSlot(int)} — Clamps hotbar slot index to [0, 8] range</li>
 *   <li>{@link #getPlayerMainInventoryStart(ServerPlayer)} — Returns main inventory start index (always 0)</li>
 *   <li>{@link #getPlayerMainInventoryEndExclusive(ServerPlayer)} —
 *       Returns main inventory end index, taking the minimum of {@code PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} and
 *       the actual container size</li>
 * </ul>
 */
final class RtsTransferUtils {
    static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;

    private RtsTransferUtils() {
    }

    /**
     * Returns whether the player's main inventory should be included as a visible source/sink in the storage browser view.
     */
    static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        // 背包始终计入存储视图：背包条目以 MODE_PLAYER_INVENTORY 独立标识显示
        return player != null;
    }

    /**
     * Checks whether quick move from linked storage should target the player's main inventory
     * (instead of the currently open menu's slots).
     */
    static boolean movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu menu) {
        // RTS 合成终端虽是 CraftingMenu 子类，但其输入槽位不是背包也不是可存放容器，
        // 快速转移时一律并入背包目标，避免物品被塞进合成终端槽位
        return menu instanceof InventoryMenu || menu instanceof CraftingMenu;
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

}
