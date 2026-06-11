package com.rtsbuilding.rtsbuilding.server.menu;


import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;


import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

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
        CraftingRecipe recipe = null;
        if (slotId == 0 && player instanceof ServerPlayer) {
            blueprint = snapshotBlueprint();
            recipe = resolveCurrentRecipe((ServerPlayer) player);
        }

        super.clicked(slotId, button, clickType, player);

        if (slotId == 0 && player instanceof ServerPlayer serverPlayer && blueprint != null) {
            ItemStack carried = serverPlayer.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                RtsStorageManager.recordCraftedOutput(serverPlayer, carried.copy());
            }
            RtsStorageManager.refillCraftGridFromLinked(serverPlayer, this, blueprint, recipe);
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

    private CraftingRecipe resolveCurrentRecipe(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        TransientCraftingContainer input = new TransientCraftingContainer(this, 3, 3);
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            stacks.add(this.getSlot(1 + i).getItem().copy());
        }
        for (int i = 0; i < stacks.size(); i++) {
            input.setItem(i, stacks.get(i));
        }
        return player.serverLevel()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, player.serverLevel())
                .orElse(null);
    }
}
