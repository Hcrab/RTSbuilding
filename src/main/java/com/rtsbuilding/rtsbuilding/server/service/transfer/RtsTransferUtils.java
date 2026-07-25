package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * transfer 子包的共享常量和辅助工具方法。
 *
 * <p>此类提供 transfer 子包中多个类共享的常量和工具方法。
 * 包级私有（package-private）设计，不对外暴露。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>常量：</b>
 * <ul>
 *   <li>{@link #PLAYER_HOTBAR_SLOT_COUNT} = {@value #PLAYER_HOTBAR_SLOT_COUNT} — 玩家快捷栏槽位数</li>
 *   <li>{@link #PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} = {@value #PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} —
 *       主背包结束索引（不含），对应 36 格（9 快捷栏 + 27 主背包）</li>
 *   <li>{@link #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} = {@value #SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} —
 *       Shift+导入时单次最多自动合成次数</li>
 * </ul>
 *
 * <p><b>工具方法：</b>
 * <ul>
 *   <li>{@link #shouldIncludePlayerMainInventoryInStorageView(ServerPlayer, RtsStorageSession)} —
 *       判断玩家主背包是否应作为可见源/接收器包含在储存浏览器视图中；
 *       在无链接存储且非合成终端菜单时返回 {@code true}</li>
 *   <li>{@link #movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu)} —
 *       判断从链接存储的快速移动是否应发往玩家背包（而非菜单槽位）；
 *       对于 {@code InventoryMenu} 或普通 {@code CraftingMenu} 返回 {@code true}</li>
 *   <li>{@link #clampHotbarSlot(int)} — 将快捷栏槽位索引限制在 [0, 8] 范围内</li>
 *   <li>{@link #getPlayerMainInventoryStart(ServerPlayer)} — 返回主背包起始索引（始终 0）</li>
 *   <li>{@link #getPlayerMainInventoryEndExclusive(ServerPlayer)} —
 *       返回主背包结束索引，取 {@code PLAYER_MAIN_INVENTORY_END_EXCLUSIVE} 与
 *       实际容器大小的较小值</li>
 * </ul>
 */
final class RtsTransferUtils {
    static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;

    private RtsTransferUtils() {
    }

    /**
     * 返回玩家的主背包是否应作为可见的源/接收器包含在存储浏览器视图中。
     */
    static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        if (player == null || player.containerMenu instanceof RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && session.linkedStorageInfo.isEmpty() && !hasPrimaryBdNetwork(player)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    /**
     * 检查从链接存储的快速移动是否应针对玩家的主背包
     * （而不是当前打开菜单的槽位）。
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
