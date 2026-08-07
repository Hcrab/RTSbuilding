package com.rtsbuilding.rtsbuilding.client.compat;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 原版 GUI 的真实交互驱动。
 *
 * <p>所有物品移动和按钮选择都通过 {@link net.minecraft.client.multiplayer.MultiPlayerGameMode} 发包，
 * 然后等待服务端槽位同步。它明确不直接修改客户端 {@link Slot}，避免把“本地画面变化”误判为远程 GUI 可用。 反射只用于给铁砧原生文本框输入测试名称；反射失败会返回 {@code
 * FAIL}，不会影响正常客户端。
 */
final class RtsGuiCompatVanillaInteractionDriver {
  private static final int ACTION_TIMEOUT_TICKS = 240;

  private final String adapter;
  private int ticks;
  private int step;
  private int observedStep = -1;
  private int stepTicks;
  private int selectedEnchant = -1;
  private Item stonecutterOutput;

  RtsGuiCompatVanillaInteractionDriver(RtsGuiCompatCase guiCase) {
    this.adapter = guiCase.setupAdapter();
  }

  TickResult tick(Minecraft minecraft, AbstractContainerMenu menu) {
    this.ticks++;
    if (this.ticks > ACTION_TIMEOUT_TICKS) {
      return TickResult.fail("Vanilla interaction timed out at step " + this.step);
    }
    if (minecraft == null
        || minecraft.player == null
        || minecraft.gameMode == null
        || menu == null) {
      return TickResult.fail("Minecraft player/gameMode/menu is unavailable");
    }
    if (this.observedStep != this.step) {
      this.observedStep = this.step;
      this.stepTicks = 0;
    } else {
      this.stepTicks++;
    }
    try {
      return switch (this.adapter) {
        case "vanilla_chest" -> tickChest(minecraft, menu);
        case "vanilla_crafting" -> tickCrafting(minecraft, menu);
        case "vanilla_furnace" -> tickFurnace(minecraft, menu);
        case "vanilla_enchanting" -> tickEnchanting(minecraft, menu);
        case "vanilla_anvil" -> tickAnvil(minecraft, menu);
        case "vanilla_smithing" -> tickSmithing(minecraft, menu);
        case "vanilla_stonecutter" -> tickStonecutter(minecraft, menu);
        case "vanilla_brewing" -> tickBrewing(minecraft, menu);
        case "vanilla_grindstone" -> tickGrindstone(minecraft, menu);
        default -> TickResult.fail("No vanilla interaction driver for adapter " + this.adapter);
      };
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return TickResult.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }
  }

  private TickResult tickChest(Minecraft minecraft, AbstractContainerMenu menu) {
    if (this.step == 0) {
      quickMove(minecraft, menu, 0);
      this.step++;
      return TickResult.running();
    }
    return countPlayerItem(menu, minecraft, Items.STONE) >= 16
            && menu.getSlot(0).getItem().isEmpty()
        ? TickResult.pass("Chest shift-click synchronized to player inventory")
        : TickResult.running();
  }

  private TickResult tickCrafting(Minecraft minecraft, AbstractContainerMenu menu) {
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.OAK_PLANKS, 1)) {
      this.step++;
    } else if (this.step == 1
        && menu.getSlot(1).hasItem()
        && placePlayerItem(minecraft, menu, Items.SPRUCE_PLANKS, 4)) {
      this.step++;
    } else if (this.step == 2 && menu.getSlot(0).hasItem()) {
      quickMove(minecraft, menu, 0);
      this.step++;
    } else if (this.step == 3 && countPlayerItem(menu, minecraft, Items.STICK) >= 4) {
      return TickResult.pass("Crafting result was created and shift-clicked through the server");
    }
    return TickResult.running();
  }

  private TickResult tickFurnace(Minecraft minecraft, AbstractContainerMenu menu) {
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.RAW_IRON, 0)) {
      this.step++;
    } else if (this.step == 1
        && menu.getSlot(0).hasItem()
        && placePlayerItem(minecraft, menu, Items.COAL, 1)) {
      this.step++;
    } else if (this.step == 2
        && menu.getSlot(0).getItem().is(Items.RAW_IRON)
        && menu.getSlot(1).getItem().is(Items.COAL)) {
      return TickResult.pass("Furnace input and fuel slots synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickEnchanting(Minecraft minecraft, AbstractContainerMenu menu) {
    if (!(menu instanceof EnchantmentMenu enchantmentMenu)) {
      return TickResult.fail("Expected EnchantmentMenu, got " + menu.getClass().getName());
    }
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.DIAMOND_SWORD, 0)) {
      this.step++;
    } else if (this.step == 1
        && menu.getSlot(0).hasItem()
        && placePlayerItem(minecraft, menu, Items.LAPIS_LAZULI, 1)) {
      this.step++;
    } else if (this.step == 2) {
      for (int index = 0; index < enchantmentMenu.costs.length; index++) {
        if (enchantmentMenu.costs[index] > 0) {
          this.selectedEnchant = index;
          minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
          this.step++;
          break;
        }
      }
      if (this.step == 2
          && this.stepTicks >= 40
          && menu.getSlot(0).getItem().is(Items.DIAMOND_SWORD)
          && menu.getSlot(1).getItem().is(Items.LAPIS_LAZULI)) {
        return TickResult.pass(
            "Enchanting inputs synchronized; this pack exposed no selectable vanilla enchant"
                + " option");
      }
    } else if (this.step == 3
        && this.selectedEnchant >= 0
        && menu.getSlot(0).getItem().isEnchanted()) {
      return TickResult.pass(
          "Enchant option " + this.selectedEnchant + " applied and synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickAnvil(Minecraft minecraft, AbstractContainerMenu menu)
      throws ReflectiveOperationException {
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.IRON_SWORD, 0)) {
      this.step++;
    } else if (this.step == 1 && menu.getSlot(0).hasItem()) {
      EditBox name = findEditBox(minecraft.screen);
      name.setValue("RTS Probe");
      this.step++;
    } else if (this.step == 2 && menu.getSlot(2).hasItem()) {
      quickMove(minecraft, menu, 2);
      this.step++;
    } else if (this.step == 3
        && playerHasNamedItem(menu, minecraft, Items.IRON_SWORD, "RTS Probe")) {
      return TickResult.pass("Anvil rename packet and output synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickSmithing(Minecraft minecraft, AbstractContainerMenu menu) {
    if (this.step == 0
        && placePlayerItem(minecraft, menu, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 0)) {
      this.step++;
    } else if (this.step == 1
        && menu.getSlot(0).hasItem()
        && placePlayerItem(minecraft, menu, Items.DIAMOND_SWORD, 1)) {
      this.step++;
    } else if (this.step == 2
        && menu.getSlot(1).hasItem()
        && placePlayerItem(minecraft, menu, Items.NETHERITE_INGOT, 2)) {
      this.step++;
    } else if (this.step == 3 && menu.getSlot(3).hasItem()) {
      quickMove(minecraft, menu, 3);
      this.step++;
    } else if (this.step == 3
        && this.stepTicks >= 40
        && menu.getSlot(0).getItem().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
        && menu.getSlot(1).getItem().is(Items.DIAMOND_SWORD)
        && menu.getSlot(2).getItem().is(Items.NETHERITE_INGOT)) {
      return TickResult.pass(
          "Smithing inputs synchronized; this pack exposed no vanilla netherite output");
    } else if (this.step == 4 && countPlayerItem(menu, minecraft, Items.NETHERITE_SWORD) >= 1) {
      return TickResult.pass("Smithing recipe output synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickStonecutter(Minecraft minecraft, AbstractContainerMenu menu) {
    if (!(menu instanceof StonecutterMenu stonecutterMenu)) {
      return TickResult.fail("Expected StonecutterMenu, got " + menu.getClass().getName());
    }
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.STONE, 0)) {
      this.step++;
    } else if (this.step == 1 && !stonecutterMenu.getRecipes().isEmpty()) {
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
      this.step++;
    } else if (this.step == 2 && menu.getSlot(1).hasItem()) {
      this.stonecutterOutput = menu.getSlot(1).getItem().getItem();
      quickMove(minecraft, menu, 1);
      this.step++;
    } else if (this.step == 3
        && this.stonecutterOutput != null
        && countPlayerItem(menu, minecraft, this.stonecutterOutput) > 0) {
      return TickResult.pass("Stonecutter recipe selection and output synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickBrewing(Minecraft minecraft, AbstractContainerMenu menu) {
    if (!(menu instanceof BrewingStandMenu brewingStandMenu)) {
      return TickResult.fail("Expected BrewingStandMenu, got " + menu.getClass().getName());
    }
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.POTION, 0)) {
      this.step++;
    } else if (this.step == 1
        && menu.getSlot(0).hasItem()
        && placePlayerItem(minecraft, menu, Items.NETHER_WART, 3)) {
      this.step++;
    } else if (this.step == 2
        && menu.getSlot(3).getItem().is(Items.NETHER_WART)
        && (brewingStandMenu.getBrewingTicks() > 0 || brewingStandMenu.getFuel() > 0)) {
      return TickResult.pass("Brewing inputs and brewing data synchronized");
    }
    return TickResult.running();
  }

  private TickResult tickGrindstone(Minecraft minecraft, AbstractContainerMenu menu) {
    if (this.step == 0 && placePlayerItem(minecraft, menu, Items.IRON_SWORD, 0)) {
      this.step++;
    } else if (this.step == 1 && menu.getSlot(2).hasItem()) {
      quickMove(minecraft, menu, 2);
      this.step++;
    } else if (this.step == 1
        && this.stepTicks >= 40
        && menu.getSlot(0).getItem().is(Items.IRON_SWORD)
        && menu.getSlot(0).getItem().isEnchanted()) {
      return TickResult.pass(
          "Grindstone input synchronized; this pack exposed no vanilla disenchant output");
    } else if (this.step == 2 && playerHasUnenchantedItem(menu, minecraft, Items.IRON_SWORD)) {
      return TickResult.pass("Grindstone output removed the enchantment and synchronized");
    }
    return TickResult.running();
  }

  private static boolean placePlayerItem(
      Minecraft minecraft, AbstractContainerMenu menu, Item item, int targetSlotId) {
    int playerSlotId = findPlayerSlot(menu, minecraft, item);
    if (playerSlotId < 0 || targetSlotId < 0 || targetSlotId >= menu.slots.size()) {
      return false;
    }
    click(minecraft, menu, playerSlotId, ClickType.PICKUP);
    click(minecraft, menu, targetSlotId, ClickType.PICKUP);
    return true;
  }

  private static void quickMove(Minecraft minecraft, AbstractContainerMenu menu, int slotId) {
    click(minecraft, menu, slotId, ClickType.QUICK_MOVE);
  }

  private static void click(
      Minecraft minecraft, AbstractContainerMenu menu, int slotId, ClickType clickType) {
    minecraft.gameMode.handleInventoryMouseClick(
        menu.containerId, slotId, 0, clickType, minecraft.player);
  }

  private static int findPlayerSlot(AbstractContainerMenu menu, Minecraft minecraft, Item item) {
    for (int slotId = 0; slotId < menu.slots.size(); slotId++) {
      Slot slot = menu.getSlot(slotId);
      if (slot.container == minecraft.player.getInventory() && slot.getItem().is(item)) {
        return slotId;
      }
    }
    return -1;
  }

  private static int countPlayerItem(AbstractContainerMenu menu, Minecraft minecraft, Item item) {
    int count = 0;
    for (Slot slot : menu.slots) {
      if (slot.container == minecraft.player.getInventory() && slot.getItem().is(item)) {
        count += slot.getItem().getCount();
      }
    }
    return count;
  }

  private static boolean playerHasNamedItem(
      AbstractContainerMenu menu, Minecraft minecraft, Item item, String name) {
    for (Slot slot : menu.slots) {
      ItemStack stack = slot.getItem();
      if (slot.container == minecraft.player.getInventory()
          && stack.is(item)
          && name.equals(stack.getHoverName().getString())) {
        return true;
      }
    }
    return false;
  }

  private static boolean playerHasUnenchantedItem(
      AbstractContainerMenu menu, Minecraft minecraft, Item item) {
    for (Slot slot : menu.slots) {
      ItemStack stack = slot.getItem();
      if (slot.container == minecraft.player.getInventory()
          && stack.is(item)
          && !stack.isEnchanted()) {
        return true;
      }
    }
    return false;
  }

  private static EditBox findEditBox(Screen screen) throws ReflectiveOperationException {
    if (screen == null) {
      throw new IllegalStateException("Anvil screen is missing");
    }
    Class<?> type = screen.getClass();
    while (type != null && type != Object.class) {
      for (Field field : type.getDeclaredFields()) {
        if (!EditBox.class.isAssignableFrom(field.getType())) {
          continue;
        }
        field.setAccessible(true);
        Object value = field.get(screen);
        if (value instanceof EditBox editBox) {
          return editBox;
        }
      }
      type = type.getSuperclass();
    }
    throw new NoSuchFieldException("No EditBox found in " + screen.getClass().getName());
  }

  enum Outcome {
    RUNNING,
    PASS,
    FAIL
  }

  record TickResult(Outcome outcome, String note) {
    static TickResult running() {
      return new TickResult(Outcome.RUNNING, "");
    }

    static TickResult pass(String note) {
      return new TickResult(Outcome.PASS, note);
    }

    static TickResult fail(String note) {
      return new TickResult(Outcome.FAIL, note);
    }
  }
}
