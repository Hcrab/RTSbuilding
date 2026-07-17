package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS 合成终端菜单（AE2 风格布局）。
 *
 * <p>槽位布局：
 * <pre>
 *   slot 0:       合成结果     (134, 148)
 *   slots 1-9:    3×3 合成网格 (26, 140) 起
 *   slots 10-36:  玩家背包     (8, 220)  起，3行
 *   slots 37-45:  快捷栏       (8, 278)
 * </pre>
 *
 * <p>实现基于 {@link CraftingMenu} 的内部结构（CraftingContainer + ResultContainer），
 * 但完全自定义槽位坐标以匹配 AE2 终端布局。
 */
public final class RtsCraftTerminalMenu extends AbstractContainerMenu {

    private static final int RESULT_SLOT = 0;
    private static final int CRAFT_GRID_START = 1;
    private static final int CRAFT_GRID_END = 10;
    private static final int INV_START = 10;
    private static final int INV_END = 37;
    private static final int HOTBAR_START = 37;
    private static final int HOTBAR_END = 46;

    final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    final ResultContainer resultSlots = new ResultContainer();
    public final ContainerLevelAccess access;
    private final Player player;

    public RtsCraftTerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public RtsCraftTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(RtsMenuTypes.RTS_CRAFT_TERMINAL.get(), containerId);
        this.access = access;
        this.player = inventory.player;

        // Result slot (slot 0)
        this.addSlot(new ResultSlot(inventory.player, this.craftSlots, this.resultSlots, 0, 134, 148));

        // 3×3 crafting grid (slots 1-9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(this.craftSlots, row * 3 + col, 26 + col * 18, 140 + row * 18));
            }
        }

        // Player inventory (slots 10-36)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 220 + row * 18));
            }
        }

        // Hotbar (slots 37-45)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 278));
        }

        this.slotsChanged(this.craftSlots);
    }

    /**
     * 始终返回 true，允许玩家在任何位置使用该合成终端。
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void slotsChanged(Container inventory) {
        this.access.execute((level, pos) -> {
            var input = CraftingInput.of(3, 3, craftSlots.getItems().stream().toList());
            var recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, level)
                    .orElse(null);
            if (recipe == null) {
                this.resultSlots.setItem(0, ItemStack.EMPTY);
            } else {
                this.resultSlots.setItem(0, recipe.value().assemble(input, level.registryAccess()));
            }
        });
        super.slotsChanged(inventory);
    }

    /**
     * 处理玩家点击合成槽的逻辑：快照蓝图 → 父类点击 → 补料。
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        ItemStack[] blueprint = null;
        CraftingRecipe recipe = null;
        if (slotId == RESULT_SLOT && player instanceof ServerPlayer) {
            blueprint = snapshotBlueprint();
            recipe = resolveCurrentRecipe((ServerPlayer) player);
        }

        super.clicked(slotId, button, clickType, player);

        if (slotId == RESULT_SLOT && player instanceof ServerPlayer serverPlayer && blueprint != null) {
            ItemStack carried = serverPlayer.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                ServiceRegistry.getInstance().crafting().recordCraftedOutput(serverPlayer, carried.copy());
            }
            ServiceRegistry.getInstance().crafting().refillCraftGridFromLinked(serverPlayer, this, blueprint, recipe);
        }
    }

    /**
     * Shift+click 传送逻辑：结果槽 → 玩家背包；合成格/背包槽 → 快捷栏/背包。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = slot.getItem();
        ItemStack copy = sourceStack.copy();

        if (index == RESULT_SLOT) {
            // Take result → try to fit into player inventory
            this.access.execute((level, pos) -> sourceStack.getItem().onCraftedBy(sourceStack, level, player));
            if (!this.moveItemStackTo(sourceStack, INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(sourceStack, copy);
        } else if (index >= CRAFT_GRID_START && index < CRAFT_GRID_END) {
            // Crafting grid → move to inventory
            if (!this.moveItemStackTo(sourceStack, INV_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= INV_START && index < HOTBAR_END) {
            // Player inventory → try hotbar first, then main inventory
            if (index >= HOTBAR_START) {
                if (!this.moveItemStackTo(sourceStack, INV_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (sourceStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (sourceStack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, sourceStack);
        if (index == RESULT_SLOT) {
            player.drop(sourceStack, false);
        }

        return copy;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.removeItemNoUpdate(0);
        this.access.execute((level, pos) -> this.clearContainer(player, this.craftSlots));
    }

    /**
     * 快照当前合成格子（slot 1~9）中的物品布局作为蓝图。
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
