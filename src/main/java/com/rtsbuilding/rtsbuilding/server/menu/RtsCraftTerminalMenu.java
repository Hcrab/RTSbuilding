package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS 合成终端菜单，继承原版工作台菜单以支持远程合成操作。
 * 允许玩家在任意位置打开合成界面，并在从合成槽（slot 0）取走物品时
 * 记录合成产出并自动从关联存储中补满材料。
 */
public final class RtsCraftTerminalMenu extends CraftingMenu {

    /**
     * 构造合成终端菜单。
     *
     * @param containerId 容器 ID
     * @param inventory   玩家背包
     * @param access      容器访问权限
     */
    public RtsCraftTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
    }

    /**
     * 始终返回 true，允许玩家在任何位置使用该合成终端。
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 当合成格子内容发生变化时，触发同步更新。
     */
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        this.broadcastChanges();
    }

    /**
     * 处理玩家点击合成槽（slot 0）的逻辑：<br>
     * 1. 点击前快照当前合成蓝图的材料布局；<br>
     * 2. 解析当前配方；<br>
     * 3. 调用父类处理点击（取走合成结果）；<br>
     * 4. 取走物品后，记录合成产出并尝试从关联存储中补满材料。
     */
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
                ServiceRegistry.getInstance().crafting().recordCraftedOutput(serverPlayer, carried.copy());
            }
            ServiceRegistry.getInstance().crafting().refillCraftGridFromLinked(serverPlayer, this, blueprint, recipe);
        }
    }

    /**
     * 快照当前合成格子（slot 1~9）中的物品布局作为蓝图。<br>
     * 每个物品只保留 1 份副本，用于后续识别配方和补料。
     *
     * @return 长度为 9 的蓝图数组
     */
    private ItemStack[] snapshotBlueprint() {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            ItemStack stack = this.getSlot(1 + i).getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }

    /**
     * 解析当前合成格子布局对应的合成配方。
     *
     * @param player 服务器端玩家
     * @return 匹配的合成配方，若未匹配则返回 null
     */
    private CraftingRecipe resolveCurrentRecipe(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            stacks.add(this.getSlot(1 + i).getItem().copy());
        }
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, stacks), level)
                .map(RecipeHolder::value)
                .orElse(null);
    }
}
