package com.rtsbuilding.rtsbuilding.server.service.crafting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCraftingGridFillerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shapelessRefillKeepsPlayersMiddleRowLayout() {
        FakeCraftMenu menu = new FakeCraftMenu();
        ItemStackHandler storage = new ItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 16));

        ItemStack[] blueprint = emptyBlueprint();
        blueprint[3] = new ItemStack(Items.STONE);
        RtsCraftingGridFiller.refillCraftGridToSnapshotCounts(
                menu, List.of(storage), null, blueprint, false);

        assertEquals(Items.STONE, menu.getSlot(4).getItem().getItem(),
                "玩家放在中排左槽的无序配方材料必须补回同一槽");
        assertTrue(menu.getSlot(1).getItem().isEmpty(),
                "补料不能把无序配方规范化到左上角");
        assertTrue(menu.getSlot(2).getItem().isEmpty());
        assertTrue(menu.getSlot(3).getItem().isEmpty());
    }

    @Test
    void refillUsesExactBlueprintComponentsInsteadOfItemIdOnly() {
        FakeCraftMenu menu = new FakeCraftMenu();
        ItemStackHandler storage = new ItemStackHandler(2);
        ItemStack wrongDamage = new ItemStack(Items.DIAMOND_SWORD);
        wrongDamage.setDamageValue(1);
        ItemStack expectedDamage = new ItemStack(Items.DIAMOND_SWORD);
        expectedDamage.setDamageValue(2);
        storage.setStackInSlot(0, wrongDamage.copy());
        storage.setStackInSlot(1, expectedDamage.copy());

        ItemStack[] blueprint = emptyBlueprint();
        blueprint[4] = expectedDamage.copy();
        RtsCraftingGridFiller.refillCraftGridToSnapshotCounts(
                menu, List.of(storage), null, blueprint, false);

        assertEquals(Items.DIAMOND_SWORD, menu.getSlot(5).getItem().getItem());
        assertEquals(2, menu.getSlot(5).getItem().getDamageValue());
        assertEquals(1, menu.getSlot(5).getItem().getCount());
        assertEquals(1, storage.getStackInSlot(0).getDamageValue(),
                "同物品 ID 但组件不匹配的工具不能被误提取");
    }

    @Test
    void unchangedCatalystIsNotDuplicated() {
        FakeCraftMenu menu = new FakeCraftMenu();
        menu.getSlot(5).set(new ItemStack(Items.STONE));
        ItemStackHandler storage = new ItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 16));

        ItemStack[] beforeCraft = emptyBlueprint();
        beforeCraft[4] = new ItemStack(Items.STONE);
        RtsCraftingGridFiller.refillCraftGridToSnapshotCounts(
                menu, List.of(storage), null, beforeCraft, false);

        assertEquals(1, menu.getSlot(5).getItem().getCount(),
                "原样留在合成槽的催化剂不能再补一份");
        assertEquals(16, storage.getStackInSlot(0).getCount());
    }

    @Test
    void refillRestoresOnlyActuallyConsumedCount() {
        FakeCraftMenu menu = new FakeCraftMenu();
        menu.getSlot(5).set(new ItemStack(Items.STONE, 9));
        ItemStackHandler storage = new ItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 16));

        ItemStack[] beforeCraft = emptyBlueprint();
        beforeCraft[4] = new ItemStack(Items.STONE, 10);
        RtsCraftingGridFiller.refillCraftGridToSnapshotCounts(
                menu, List.of(storage), null, beforeCraft, false);

        assertEquals(10, menu.getSlot(5).getItem().getCount());
        assertEquals(15, storage.getStackInSlot(0).getCount());
    }

    private static ItemStack[] emptyBlueprint() {
        ItemStack[] blueprint = new ItemStack[9];
        java.util.Arrays.fill(blueprint, ItemStack.EMPTY);
        return blueprint;
    }

    private static final class FakeCraftMenu extends AbstractContainerMenu {
        private final SimpleContainer slots = new SimpleContainer(10);

        private FakeCraftMenu() {
            super(MenuType.GENERIC_9x1, 1);
            for (int i = 0; i < 10; i++) {
                addSlot(new Slot(this.slots, i, 0, 0));
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
