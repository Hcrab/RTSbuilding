package com.rtsbuilding.rtsbuilding.server.service.transfer;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 合成格支持工具——供 transfer 链路（Shift+点击工作台结果槽自动多轮合成）使用。
 *
 * <p>原实现位于已移除的 craft 子包中；为支持“从链接存储自动补料合成”的
 * Shift+导入能力，将 {@link #snapshotCraftGridBlueprint} 与
 * {@link #refillCraftGridFromBlueprint} 迁至 transfer 包，仅保留 blueprint 原型匹配路径。</p>
 */
final class RtsCraftGridSupport {

    private RtsCraftGridSupport() {
    }

    /**
     * 捕获当前合成格（slot 1~9）的单份蓝图。
     */
    static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Slot grid = menu.getSlot(1 + i);
            ItemStack stack = grid.getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }

    /**
     * 按蓝图原型从链接存储/玩家背包补满合成格。
     */
    static void refillCraftGridFromBlueprint(
            CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        if (menu == null || blueprint == null || blueprint.length != 9) {
            return;
        }
        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack blueprintStack = blueprint[i];
                if (blueprintStack == null || blueprintStack.isEmpty()) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();
                if (!current.isEmpty()) {
                    if (!ItemStack.isSameItemSameComponents(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(handlers, player, current)
                            : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, current);
                    if (extracted.isEmpty() || !ItemStack.isSameItemSameComponents(current, extracted)) {
                        if (!extracted.isEmpty()) {
                            RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(handlers, player, extracted);
                        }
                        continue;
                    }
                    current.grow(1);
                    grid.setChanged();
                    inserted = true;
                    changed = true;
                    continue;
                }

                ItemStack extracted = includePlayerFallback
                        ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(handlers, player, blueprintStack)
                        : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, blueprintStack);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                inserted = true;
                changed = true;
            }
            if (!inserted) {
                break;
            }
            if (!fillAll) {
                break;
            }
        }
        if (changed) {
            refreshCraftingResult(menu);
        }
    }

    /**
     * 触发合成结果槽重新计算（反射获取 CraftingContainer）。
     */
    private static void refreshCraftingResult(CraftingMenu menu) {
        CraftingContainer craftSlots = resolveCraftingContainer(menu);
        if (craftSlots != null) {
            menu.slotsChanged(craftSlots);
        }
    }

    private static CraftingContainer resolveCraftingContainer(CraftingMenu menu) {
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!CraftingContainer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object current = field.get(menu);
                    if (current instanceof CraftingContainer craftSlots) {
                        return craftSlots;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // 走菜单默认同步路径
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
