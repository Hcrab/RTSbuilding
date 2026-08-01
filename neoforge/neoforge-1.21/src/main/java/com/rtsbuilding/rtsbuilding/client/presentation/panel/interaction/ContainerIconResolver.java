package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 为已打开的容器屏幕解析一个用于标签页展示的物品/方块图标。
 *
 * <p>解析顺序：</p>
 * <ol>
 *   <li>按 {@link MenuType} 映射（覆盖原版所有容器类型）。</li>
 *   <li>若为模组自定义菜单类型，则尝试取玩家视线命中的方块物品。</li>
 *   <li>兜底使用箱子图标。</li>
 * </ol>
 */
public final class ContainerIconResolver {

    private static final Map<MenuType<?>, ItemStack> MENU_ICONS = new HashMap<>();

    private ContainerIconResolver() {}

    static {
        put(MenuType.GENERIC_9x1, Items.CHEST);
        put(MenuType.GENERIC_9x2, Items.CHEST);
        put(MenuType.GENERIC_9x3, Items.CHEST);
        put(MenuType.GENERIC_9x4, Items.CHEST);
        put(MenuType.GENERIC_9x5, Items.CHEST);
        put(MenuType.GENERIC_9x6, Items.CHEST);
        put(MenuType.GENERIC_3x3, Items.DISPENSER);
        put(MenuType.CRAFTER_3x3, Items.CRAFTER);
        put(MenuType.ANVIL, Items.ANVIL);
        put(MenuType.BEACON, Items.BEACON);
        put(MenuType.BLAST_FURNACE, Items.BLAST_FURNACE);
        put(MenuType.BREWING_STAND, Items.BREWING_STAND);
        put(MenuType.CRAFTING, Items.CRAFTING_TABLE);
        put(MenuType.ENCHANTMENT, Items.ENCHANTING_TABLE);
        put(MenuType.FURNACE, Items.FURNACE);
        put(MenuType.GRINDSTONE, Items.GRINDSTONE);
        put(MenuType.HOPPER, Items.HOPPER);
        put(MenuType.LECTERN, Items.LECTERN);
        put(MenuType.LOOM, Items.LOOM);
        put(MenuType.MERCHANT, Items.EMERALD);
        put(MenuType.SHULKER_BOX, Items.SHULKER_BOX);
        put(MenuType.SMITHING, Items.SMITHING_TABLE);
        put(MenuType.SMOKER, Items.SMOKER);
        put(MenuType.CARTOGRAPHY_TABLE, Items.CARTOGRAPHY_TABLE);
        put(MenuType.STONECUTTER, Items.STONECUTTER);
    }

    private static void put(MenuType<?> type, Item item) {
        MENU_ICONS.put(type, new ItemStack(item));
    }

    /**
     * 解析给定容器屏幕的图标，永不返回 null（失败时返回空栈）。
     */
    public static ItemStack resolve(AbstractContainerScreen<?> screen) {
        if (screen != null && screen.getMenu() != null) {
            ItemStack mapped = MENU_ICONS.get(screen.getMenu().getType());
            if (mapped != null) return mapped;
        }
        ItemStack fromTarget = fromTargetBlock();
        if (!fromTarget.isEmpty()) return fromTarget;
        return fromItemId("minecraft:chest");
    }

    /**
     * 尝试从玩家视线命中的方块解析图标。
     */
    private static ItemStack fromTargetBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return ItemStack.EMPTY;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return ItemStack.EMPTY;
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (state == null || state.isAir()) return ItemStack.EMPTY;
        Item item = state.getBlock().asItem();
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static ItemStack fromItemId(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
}
