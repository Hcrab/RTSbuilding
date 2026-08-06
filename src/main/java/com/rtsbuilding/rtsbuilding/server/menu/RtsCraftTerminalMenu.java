package com.rtsbuilding.rtsbuilding.server.menu;

import com.rtsbuilding.rtsbuilding.common.RtsMenuTypes;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * RTS 一体化合成终端的服务端权威菜单。
 *
 * <p>合成语义直接沿用原版工作台的 {@link RecipeBookMenu}、
 * {@link TransientCraftingContainer} 与 {@link ResultSlot} 组合：配方匹配、成就事件、
 * 配方剩余物和 Shift 合成都不在这里另造一套。该类只拥有终端专用槽位坐标、远程
 * {@link #stillValid(Player)} 规则，以及合成后从 linked storage 按原槽位补料的桥接。</p>
 *
 * <p>槽位索引必须保持原版工作台顺序：结果 0、合成格 1~9、背包 10~36、快捷栏
 * 37~45。网络包、JEI 和批量转移都依赖这个稳定契约。</p>
 */
public final class RtsCraftTerminalMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {
    public static final int RESULT_SLOT = 0;
    public static final int CRAFT_SLOT_START = 1;
    public static final int CRAFT_SLOT_END = 10;
    public static final int INVENTORY_SLOT_START = 10;
    public static final int INVENTORY_SLOT_END = 37;
    public static final int HOTBAR_SLOT_START = 37;
    public static final int HOTBAR_SLOT_END = 46;

    /** 菜单槽位直接复用纯 Java 布局契约，避免服务端槽坐标与客户端 chrome 漂移。 */
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private boolean placingRecipe;

    /** 客户端菜单工厂使用；服务端打开时会传入真实的远程访问上下文。 */
    public RtsCraftTerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public RtsCraftTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(RtsMenuTypes.RTS_CRAFT_TERMINAL.get(), containerId);
        this.access = access;
        this.player = inventory.player;

        this.addSlot(new ResultSlot(inventory.player, this.craftSlots, this.resultSlots,
                0, CraftTerminalLayout.RESULT_X, CraftTerminalLayout.RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                this.addSlot(new Slot(this.craftSlots, column + row * 3,
                        CraftTerminalLayout.CRAFT_GRID_X + column * 18,
                        CraftTerminalLayout.CRAFT_GRID_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                        CraftTerminalLayout.INVENTORY_X + column * 18,
                        CraftTerminalLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column,
                    CraftTerminalLayout.INVENTORY_X + column * 18,
                    CraftTerminalLayout.HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (!this.placingRecipe) {
            this.access.execute((level, ignored) -> updateCraftingResult(
                    this, level, this.player, this.craftSlots, this.resultSlots, null));
        }
    }

    private static void updateCraftingResult(
            AbstractContainerMenu menu,
            Level level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> knownRecipe) {
        if (level.isClientSide) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, knownRecipe);
        if (match.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = match.get();
            if (resultSlots.setRecipeUsed(level, serverPlayer, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        resultSlots.setItem(0, result);
        menu.setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                menu.containerId, menu.incrementStateId(), RESULT_SLOT, result));
    }

    @Override
    public void beginPlacingRecipe() {
        this.placingRecipe = true;
    }

    @Override
    public void finishPlacingRecipe(RecipeHolder<CraftingRecipe> recipe) {
        this.placingRecipe = false;
        this.access.execute((level, ignored) -> updateCraftingResult(
                this, level, this.player, this.craftSlots, this.resultSlots, recipe));
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents itemHelper) {
        this.craftSlots.fillStackedContents(itemHelper);
    }

    @Override
    public void clearCraftingContent() {
        this.craftSlots.clearContent();
        this.resultSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
        return recipe.value().matches(this.craftSlots.asCraftInput(), this.player.level());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, ignored) -> this.clearContainer(player, this.craftSlots));
    }

    /** 捕获点击前的真实槽位组件与数量，用它判断消耗并只补回实际缺少的部分。 */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (button == 0
                && clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE
                && slotId >= INVENTORY_SLOT_START
                && slotId < HOTBAR_SLOT_END
                && player instanceof ServerPlayer serverPlayer) {
            ServiceRegistry.getInstance().transfer()
                    .depositCraftTerminalPlayerSlot(serverPlayer, slotId);
            return;
        }

        ItemStack[] before = null;
        ItemStack craftedOutput = ItemStack.EMPTY;
        if (slotId == RESULT_SLOT && player instanceof ServerPlayer serverPlayer) {
            before = snapshotGrid();
            craftedOutput = this.getSlot(RESULT_SLOT).getItem().copy();
        }

        super.clicked(slotId, button, clickType, player);

        if (slotId == RESULT_SLOT
                && player instanceof ServerPlayer serverPlayer
                && before != null
                && wasGridConsumed(before)) {
            if (!craftedOutput.isEmpty()) {
                ServiceRegistry.getInstance().crafting().recordCraftedOutput(serverPlayer, craftedOutput);
            }
            ServiceRegistry.getInstance().crafting()
                    .refillCraftGridFromLinked(serverPlayer, this, before);
        }
    }

    private ItemStack[] snapshotGrid() {
        ItemStack[] snapshot = new ItemStack[9];
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack stack = this.getSlot(CRAFT_SLOT_START + i).getItem();
            snapshot[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        return snapshot;
    }

    private boolean wasGridConsumed(ItemStack[] before) {
        for (int i = 0; i < 9; i++) {
            ItemStack previous = before[i];
            ItemStack current = this.getSlot(CRAFT_SLOT_START + i).getItem();
            if (previous.isEmpty() != current.isEmpty()) {
                return true;
            }
            if (!previous.isEmpty()
                    && (!ItemStack.isSameItemSameComponents(previous, current)
                    || previous.getCount() != current.getCount())) {
                return true;
            }
        }
        return false;
    }

    /** 供通用补料服务触发原版结果槽刷新，不向客户端暴露可变容器所有权。 */
    public CraftingContainer craftingContainer() {
        return this.craftSlots;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack source = slot.getItem();
            original = source.copy();
            if (index == RESULT_SLOT) {
                this.access.execute((level, ignored) -> source.getItem().onCraftedBy(source, level, player));
                if (!this.moveItemStackTo(source, INVENTORY_SLOT_START, HOTBAR_SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(source, original);
            } else if (index >= INVENTORY_SLOT_START && index < HOTBAR_SLOT_END) {
                if (!this.moveItemStackTo(source, CRAFT_SLOT_START, CRAFT_SLOT_END, false)) {
                    if (index < HOTBAR_SLOT_START) {
                        if (!this.moveItemStackTo(source, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(source, INVENTORY_SLOT_START, INVENTORY_SLOT_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!this.moveItemStackTo(source, INVENTORY_SLOT_START, HOTBAR_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }

            if (source.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (source.getCount() == original.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, source);
            if (index == RESULT_SLOT) {
                player.drop(source, false);
            }
        }
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public int getResultSlotIndex() {
        return RESULT_SLOT;
    }

    @Override
    public int getGridWidth() {
        return this.craftSlots.getWidth();
    }

    @Override
    public int getGridHeight() {
        return this.craftSlots.getHeight();
    }

    @Override
    public int getSize() {
        return CRAFT_SLOT_END;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return slotIndex != RESULT_SLOT;
    }
}
