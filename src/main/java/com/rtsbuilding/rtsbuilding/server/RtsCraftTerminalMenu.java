package com.rtsbuilding.rtsbuilding.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

public final class RtsCraftTerminalMenu extends CraftingMenu {
    public RtsCraftTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        this.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        ItemStack[] blueprint = null;
        if (slotId == 0 && player instanceof ServerPlayer) {
            blueprint = snapshotBlueprint();
        }

        super.clicked(slotId, button, clickType, player);

        if (slotId == 0 && player instanceof ServerPlayer serverPlayer && blueprint != null) {
            ItemStack carried = serverPlayer.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                RtsStorageManager.recordCraftedOutput(serverPlayer, carried.copy());
            }
            RtsStorageManager.refillCraftGridFromLinked(serverPlayer, this, blueprint);
        }
    }

    private ItemStack[] snapshotBlueprint() {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            ItemStack stack = this.getSlot(1 + i).getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }
}
