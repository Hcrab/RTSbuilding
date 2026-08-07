package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
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
    private final Player terminalPlayer;

    /**
     * 构造合成终端菜单。
     *
     * @param containerId 容器 ID
     * @param inventory   玩家背包
     * @param access      容器访问权限
     */
    public RtsCraftTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(containerId, inventory, access);
        this.terminalPlayer = inventory.player;
        recreateTerminalSlots();
    }

    /**
     * 终端仍沿用原版 CraftingMenu 的槽位顺序和服务端合成语义，只把同一批真实槽位摆到
     * 终端皮肤的 3×3 合成区和玩家背包区域。这样 Shift 导入仍可按既有 menu slot 编号走
     * C2S 权威路径，不需要额外的近距离或客户端会话确认。
     */
    private void recreateTerminalSlots() {
        replaceTerminalSlot(0, new ResultSlot(this.terminalPlayer, this.craftSlots, this.resultSlots, 0,
                CraftTerminalLayout.menuSlotX(0), CraftTerminalLayout.menuSlotY(0)));
        for (int index = 1; index <= 45; index++) {
            Slot original = this.slots.get(index);
            replaceTerminalSlot(index, new Slot(original.container, original.getContainerSlot(),
                    CraftTerminalLayout.menuSlotX(index), CraftTerminalLayout.menuSlotY(index)));
        }
    }

    /** 保持菜单索引不变，只替换为构造时已带终端坐标的真实槽位。 */
    private void replaceTerminalSlot(int menuSlot, Slot replacement) {
        replacement.index = this.slots.get(menuSlot).index;
        this.slots.set(menuSlot, replacement);
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
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
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
        return level.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, stacks), level)
                .map(RecipeHolder::value)
                .orElse(null);
    }
}
