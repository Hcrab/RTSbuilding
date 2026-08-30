package net.p3pp3rf1y.sophisticatedbackpacks.common.gui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 测试用的 Sophisticated Backpacks 菜单替身。
 *
 * <p>它只模拟包名和最小菜单契约，不模拟背包内部逻辑。这样 smoke test 可以稳定验证
 * RTSBuilding 对 Sophisticated 本地菜单的保护分支，而不需要在单元测试 worker 中加载真实模组。
 */
public final class FakeBackpackMenu extends AbstractContainerMenu {
    public FakeBackpackMenu(int containerId) {
        super(null, containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
