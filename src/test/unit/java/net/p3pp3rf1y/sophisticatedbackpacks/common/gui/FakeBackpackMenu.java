package net.p3pp3rf1y.sophisticatedbackpacks.common.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/**
 * 测试用的 Sophisticated Backpacks 菜单替身。
 *
 * <p>它只模拟包名和最小菜单契约，不模拟背包内部逻辑。这样 smoke test 可以稳定验证
 * RTSBuilding 对 Sophisticated 本地菜单的保护分支，而不需要在单元测试 worker 中加载真实模组。
 */
public final class FakeBackpackMenu extends Container {
    public FakeBackpackMenu(int containerId) {
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
