package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1.12.2 RTS 合成终端菜单。
 *
 * <p>继承原版 {@link ContainerWorkbench}，保留原版槽位、配方结果和剩余物处理；
 * 只放宽距离校验，并在玩家取走结果后把原有 3×3 蓝图交给服务层从链接储存补料。</p>
 */
public final class RtsCraftTerminalMenu extends ContainerWorkbench {

    public RtsCraftTerminalMenu(InventoryPlayer inventory, World world, BlockPos pos) {
        super(inventory, world, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        super.onCraftMatrixChanged(inventory);
        detectAndSendChanges();
    }

    @Override
    public ItemStack slotClick(int slotId, int button, int clickMode, EntityPlayer player) {
        ItemStack[] blueprint = null;
        IRecipe recipe = null;
        if (slotId == 0 && player instanceof EntityPlayerMP) {
            blueprint = snapshotBlueprint();
            recipe = resolveCurrentRecipe((EntityPlayerMP) player);
        }

        ItemStack result = super.slotClick(slotId, button, clickMode, player);

        if (slotId == 0 && player instanceof EntityPlayerMP && blueprint != null) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            ItemStack carried = serverPlayer.inventory.getItemStack();
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(carried)) {
                ServiceRegistry.getInstance().crafting().recordCraftedOutput(serverPlayer, carried.copy());
            }
            ServiceRegistry.getInstance().crafting()
                    .refillCraftGridFromLinked(serverPlayer, this, blueprint, recipe);
        }
        return result;
    }

    private ItemStack[] snapshotBlueprint() {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            ItemStack stack = this.getSlot(1 + i).getStack();
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                blueprint[i] = null;
            } else {
                blueprint[i] = stack.copy();
                blueprint[i].stackSize = 1;
            }
        }
        return blueprint;
    }

    private IRecipe resolveCurrentRecipe(EntityPlayerMP player) {
        if (player == null) return null;
        for (Object value : CraftingManager.getInstance().getRecipeList()) {
            if (value instanceof IRecipe
                    && ((IRecipe) value).matches(this.craftMatrix, player.worldObj)) {
                return (IRecipe) value;
            }
        }
        return null;
    }
}
