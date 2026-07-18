package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成网格填充器，负责将物品从链接存储自动填入工作台的 3x3 合成网格。
 *
 * <p>支持三种填充模式：
 * <ul>
 *   <li><b>蓝图填充</b>（{@link #refillCraftGridFromLinked}）— 根据预定义的物品蓝图，
 *   从链接存储逐槽填充合成网格，支持单次填充和多次堆叠填充（最多 64 轮）</li>
 *   <li><b>网络包填充</b>（{@link #refillCurrentCraftGridFromBlueprintIds} / 
 *   {@link #refillCurrentCraftGridFromBlueprintStacks}）— 从客户端发送的物品 ID
 *   或物品原型栈列表填充当前合成网格</li>
 *   <li><b>JEI 一键填充</b>（{@link #applyJeiTransfer}）— 支持 JEI 配方传输集成，
 *   可清除现有网格、首选原型匹配、多次堆叠填充</li>
 * </ul>
 *
 * <p>填充时优先匹配精确原型，回退到任意匹配的材料。
 * 若网格中已有物品，会自动检测堆叠上限并尝试增量填充。
 */
public final class RtsCraftingGridFiller {

    private RtsCraftingGridFiller() {
    }

    // ---- refill from linked storage (player result click) -----------------------

    /**
     * 使用单物品蓝图从链接存储填充打开的合成网格。
     */
    public static void refillCraftGridFromLinked(
            ServerPlayer player, RtsStorageSession session,
            AbstractContainerMenu craftingMenu, ItemStack[] blueprint) {
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint, null);
    }

    public static void refillCraftGridFromLinked(
            ServerPlayer player, RtsStorageSession session,
            AbstractContainerMenu craftingMenu, ItemStack[] blueprint, CraftingRecipe recipe) {
        if (session == null || craftingMenu == null || blueprint == null || blueprint.length != 9) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        refillCraftGridFromBlueprint(craftingMenu, extractHandlers, insertHandlers, player,
                blueprint, recipe, false, true);
        craftingMenu.broadcastChanges();
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
    }

    // ---- refill from ids / stacks (network packets) ------------------------------

    /**
     * 从客户端发送的物品 ID 重新填充当前合成网格。
     */
    public static void refillCurrentCraftGridFromBlueprintIds(
            ServerPlayer player, RtsStorageSession session,
            List<String> blueprintIds, String craftedItemId, int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintIds == null || blueprintIds.size() != 9) {
            return;
        }
        if (!(player.containerMenu instanceof AbstractContainerMenu craftingMenu)) {
            return;
        }
        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            ServiceRegistry.getInstance().page().recordRecentItem(session, craftedItemId,
                    S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
        }
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            String itemId = blueprintIds.get(i);
            if (itemId == null || itemId.isBlank()) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            blueprint[i] = new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    /**
     * 从客户端发送的精确物品原型重新填充当前合成网格。
     */
    public static void refillCurrentCraftGridFromBlueprintStacks(
            ServerPlayer player, RtsStorageSession session,
            List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintStacks == null || blueprintStacks.size() != 9) {
            return;
        }
        if (!(player.containerMenu instanceof AbstractContainerMenu craftingMenu)) {
            return;
        }
        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            ServiceRegistry.getInstance().page().recordRecentItem(session, craftedItemId,
                    S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
        }
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            ItemStack stack = blueprintStacks.get(i);
            blueprint[i] = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    // ---- JEI transfer ------------------------------------------------------------

    public static void applyJeiTransfer(
            ServerPlayer player, RtsStorageSession session,
            String recipeId, List<ItemStack> ingredientPrototypes,
            boolean maxTransfer, boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!(player.containerMenu instanceof AbstractContainerMenu craftingMenu)) {
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            return;
        }
        RecipeHolder<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (raw == null || !(raw.value() instanceof CraftingRecipe craftingRecipe)) {
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        Ingredient[] required = RtsCraftingUtils.mapCraftingIngredients(craftingRecipe);
        if (required.length != 9) {
            return;
        }
        ItemStack[] preferredPrototypes = sanitizeIngredientPrototypes(required, ingredientPrototypes);
        CraftIngredientPlan plannedFallback = RtsCraftingAvailability.resolveCraftIngredientPlan(
                craftingRecipe,
                RtsCraftingAvailability.snapshotAvailable(
                        player, extractHandlers, true));

        List<ItemStack> cleared = new ArrayList<>(9);
        if (clearGridFirst) {
            for (int i = 0; i < 9; i++) {
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (existing.isEmpty()) {
                    cleared.add(ItemStack.EMPTY);
                    continue;
                }
                ItemStack copy = existing.copy();
                grid.set(ItemStack.EMPTY);
                grid.setChanged();
                cleared.add(copy);
            }
        } else {
            for (int i = 0; i < 9; i++) {
                cleared.add(ItemStack.EMPTY);
            }
        }

        boolean anyInserted = false;
        int maxPasses = maxTransfer ? 64 : 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean passInsertedAny = false;
            for (int i = 0; i < 9; i++) {
                Ingredient ingredient = required[i];
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (!existing.isEmpty()) {
                    if (!ingredient.test(existing)) {
                        continue;
                    }
                    if (existing.getCount() >= existing.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = RtsCraftingExecutor.extractOneMatchingIngredientCombined(
                            extractHandlers, player, ingredient, existing);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    existing.grow(1);
                    grid.setChanged();
                    passInsertedAny = true;
                    anyInserted = true;
                    continue;
                }

                ItemStack preferred = preferredPrototypes[i];
                if (preferred.isEmpty() && plannedFallback != null) {
                    preferred = plannedFallback.prototypeAt(i);
                }
                ItemStack extracted = RtsCraftingExecutor.extractOneMatchingIngredientCombined(
                        extractHandlers, player, ingredient, preferred);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                passInsertedAny = true;
                anyInserted = true;
            }

            if (!passInsertedAny) {
                break;
            }
            if (!maxTransfer) {
                break;
            }
        }

        for (ItemStack stack : cleared) {
            if (stack.isEmpty()) {
                continue;
            }
            RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, stack);
        }
        RtsCraftingUtils.refreshCraftingResult(craftingMenu);
        craftingMenu.broadcastChanges();
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
        if (anyInserted) {
            QuestService.runQuestDetect(player, session, false);
        }
    }

    /**
     * Clear the crafting grid — either return items to linked storage or move to player inventory.
     */
    public static void clearCraftingGrid(ServerPlayer player, RtsStorageSession session,
                                          boolean toPlayerInventory) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!(player.containerMenu instanceof AbstractContainerMenu craftingMenu)) {
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        boolean changed = false;
        for (int i = 0; i < 9; i++) {
            var gridSlot = craftingMenu.getSlot(1 + i);
            ItemStack stack = gridSlot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            gridSlot.set(ItemStack.EMPTY);
            gridSlot.setChanged();
            changed = true;

            if (toPlayerInventory) {
                player.getInventory().placeItemBackInInventory(stack);
            } else {
                RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, stack);
            }
        }

        if (changed) {
            RtsCraftingUtils.refreshCraftingResult(craftingMenu);
            craftingMenu.broadcastChanges();
            ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
        }
    }

    public static void applyUniversalJeiTransfer(
            ServerPlayer player, RtsStorageSession session,
            List<ItemStack> prototypes, List<Integer> quantities,
            boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        if (session == null || prototypes == null || quantities == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!(player.containerMenu instanceof AbstractContainerMenu craftingMenu)) {
            return;
        }

        int size = Math.min(prototypes.size(), quantities.size());
        if (size == 0) {
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        // 阶段1: 清空合成格
        List<ItemStack> cleared = new ArrayList<>(9);
        if (clearGridFirst) {
            for (int i = 0; i < 9; i++) {
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (existing.isEmpty()) {
                    cleared.add(ItemStack.EMPTY);
                } else {
                    cleared.add(existing.copy());
                    grid.set(ItemStack.EMPTY);
                    grid.setChanged();
                }
            }
        } else {
            for (int i = 0; i < 9; i++) {
                cleared.add(ItemStack.EMPTY);
            }
        }

        // 阶段2: 按原型列表从存储取料 → 填入合成格（前9种物品，同物品堆叠≤64）
        boolean anyInserted = false;
        int gridIndex = 0;
        for (int itemIdx = 0; itemIdx < size; itemIdx++) {
            ItemStack prototype = prototypes.get(itemIdx);
            int needed = Math.max(1, quantities.get(itemIdx));
            if (prototype == null || prototype.isEmpty() || needed <= 0) {
                continue;
            }

            while (needed > 0 && gridIndex < 9) {
                Slot targetSlot = craftingMenu.getSlot(1 + gridIndex);
                ItemStack existing = targetSlot.getItem();

                if (existing.isEmpty()) {
                    int batch = Math.min(needed, prototype.getMaxStackSize());
                    ItemStack extracted = extractPrototypeFromLinked(extractHandlers, player, prototype, batch);
                    if (extracted.isEmpty()) break;
                    targetSlot.set(extracted);
                    targetSlot.setChanged();
                    needed -= extracted.getCount();
                    anyInserted = true;
                    gridIndex++;
                    continue;
                }

                if (ItemStack.isSameItemSameComponents(existing, prototype)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int canAdd = existing.getMaxStackSize() - existing.getCount();
                    int batch = Math.min(needed, canAdd);
                    ItemStack extracted = extractPrototypeFromLinked(extractHandlers, player, prototype, batch);
                    if (extracted.isEmpty()) { gridIndex++; continue; }
                    existing.grow(extracted.getCount());
                    targetSlot.setChanged();
                    needed -= extracted.getCount();
                    anyInserted = true;
                    if (existing.getCount() >= existing.getMaxStackSize()) gridIndex++;
                    continue;
                }

                gridIndex++;
            }

            // 阶段3: 溢出物品放入玩家背包
            while (needed > 0) {
                int batch = Math.min(needed, prototype.getMaxStackSize());
                ItemStack extracted = extractPrototypeFromLinked(extractHandlers, player, prototype, batch);
                if (extracted.isEmpty()) break;
                needed -= extracted.getCount();
                anyInserted = true;
                RtsTransferInserter.moveToPlayerInventoryOnly(player, extracted);
            }
        }

        // 被清空的原物品放回存储
        for (ItemStack stack : cleared) {
            if (stack.isEmpty()) continue;
            RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, stack);
        }

        RtsCraftingUtils.refreshCraftingResult(craftingMenu);
        craftingMenu.broadcastChanges();
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
        if (anyInserted) {
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- low-level grid refill loop ----------------------------------------------

    /**
     * 从关联存储中提取指定原型物品，优先匹配精确原型，回退到物品类型匹配。
     */
    private static ItemStack extractPrototypeFromLinked(
            List<IItemHandler> handlers, ServerPlayer player,
            ItemStack prototype, int count) {
        if (prototype == null || prototype.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack exact = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!exact.isEmpty()) {
            ItemStack result = exact.copy();
            int remaining = count - exact.getCount();
            while (remaining > 0) {
                ItemStack more = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, prototype);
                if (more.isEmpty()) break;
                result.grow(more.getCount());
                remaining -= more.getCount();
            }
            return result;
        }
        return RtsTransferExtractor.extractMatchingFromLinked(
                handlers, prototype.getItem(), count);
    }

    // ---- low-level grid refill (blueprint-only, AE2 style) -----------------------

    /**
     * 以蓝图（玩家放置物品）为模板从存储补料。不依赖配方 Ingredient，避免无序配方位置错位。
     */
    public static void refillCraftGridFromBlueprint(
            AbstractContainerMenu menu, List<IItemHandler> extractHandlers,
            List<IItemHandler> insertHandlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        refillCraftGridFromBlueprint(menu, extractHandlers, insertHandlers, player,
                blueprint, null, fillAll, includePlayerFallback);
    }

    /**
     * 以蓝图为模板补料，可选配方用于 fuzzy 变体匹配和剩余物品清理。
     */
    public static void refillCraftGridFromBlueprint(
            AbstractContainerMenu menu, List<IItemHandler> extractHandlers,
            List<IItemHandler> insertHandlers, ServerPlayer player,
            ItemStack[] blueprint, CraftingRecipe recipe,
            boolean fillAll, boolean includePlayerFallback) {
        if (blueprint == null || blueprint.length != 9) {
            return;
        }

        // P2-A: 将配方剩余物品（空桶等）移出合成格，优先放入背包，其次存入存储
        evictRemainingItems(menu, blueprint, player, insertHandlers);

        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack template = blueprint[i];
                if (template == null || template.isEmpty()) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();

                if (!current.isEmpty()) {
                    // AE2 风格：槽中仍有物品时不补充，等消耗完后再补
                    continue;
                }

                // 空槽：精确提取 → fuzzy 回退
                ItemStack extracted = includePlayerFallback
                        ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(extractHandlers, player, template)
                        : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(extractHandlers, template);
                if (extracted.isEmpty() && recipe != null) {
                    extracted = extractFuzzyVariant(extractHandlers, player, template, blueprint, recipe, i);
                }
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
            RtsCraftingUtils.refreshCraftingResult(menu);
        }
    }

    // ---- helpers ----------------------------------------------------------------

    /**
     * 将配方剩余物品（空桶等）从合成格移出。检测标准：槽中物品与蓝图表不匹配。
     */
    private static void evictRemainingItems(AbstractContainerMenu menu, ItemStack[] blueprint,
                                             ServerPlayer player, List<IItemHandler> insertHandlers) {
        for (int i = 0; i < 9; i++) {
            Slot grid = menu.getSlot(1 + i);
            ItemStack current = grid.getItem();
            ItemStack template = blueprint[i];
            if (current.isEmpty() || template.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(current, template)) continue;
            // 剩余物品：移出
            ItemStack toMove = current.copy();
            grid.set(ItemStack.EMPTY);
            grid.setChanged();
            if (!player.getInventory().add(toMove)) {
                RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, toMove);
            }
        }
    }

    /**
     * P1 fuzzy 变体匹配：精确原型提取失败时，分两阶段寻找替代品。
     * 阶段1：同 Item 类型但不同组件（附魔物品、损坏工具等）— 仅对可损坏/有组件的物品触发。
     * 阶段2：全存储扫描（tag 原料 #minecraft:logs 等跨 Item 类型的变体）— 用配方重新验证。
     */
    private static ItemStack extractFuzzyVariant(
            List<IItemHandler> handlers, ServerPlayer player,
            ItemStack template, ItemStack[] blueprint, CraftingRecipe recipe, int slotIndex) {
        if (player == null || recipe == null || template.isEmpty()) return ItemStack.EMPTY;

        ItemStack expectedResult = recipe.getResultItem(player.registryAccess());
        if (expectedResult.isEmpty()) return ItemStack.EMPTY;

        // 构建测试输入
        List<ItemStack> testInput = new ArrayList<>(9);
        for (int j = 0; j < 9; j++) {
            testInput.add(blueprint[j].isEmpty() ? ItemStack.EMPTY : blueprint[j].copyWithCount(1));
        }

        boolean trySameItemOnly = template.isDamageableItem() || !template.getComponentsPatch().isEmpty();

        // 阶段1：同 Item 类型的组件变体（仅当模板有组件或可损坏时）
        if (trySameItemOnly) {
            ItemStack result = scanStorageForVariant(handlers, player, template, testInput, slotIndex,
                    recipe, expectedResult, blueprint, true);
            if (!result.isEmpty()) return result;
        }

        // 阶段2：全存储扫描，不限 Item 类型（tag 原料变体如 #minecraft:logs）
        return scanStorageForVariant(handlers, player, template, testInput, slotIndex,
                recipe, expectedResult, blueprint, false);
    }

    /** 遍历存储，用配方验证寻找匹配的替代品 */
    private static ItemStack scanStorageForVariant(
            List<IItemHandler> handlers, ServerPlayer player,
            ItemStack template, List<ItemStack> testInput, int slotIndex,
            CraftingRecipe recipe, ItemStack expectedResult, ItemStack[] blueprint,
            boolean sameItemOnly) {
        for (IItemHandler handler : handlers) {
            for (int s = 0; s < handler.getSlots(); s++) {
                ItemStack candidate = handler.getStackInSlot(s);
                if (candidate.isEmpty()) continue;
                if (sameItemOnly && candidate.getItem() != template.getItem()) continue;
                if (ItemStack.isSameItemSameComponents(candidate, template)) continue;

                testInput.set(slotIndex, candidate.copyWithCount(1));
                CraftingInput ci = CraftingInput.of(3, 3, testInput);
                if (recipe.matches(ci, player.serverLevel())) {
                    ItemStack assembled = recipe.assemble(ci, player.registryAccess());
                    if (ItemStack.isSameItemSameComponents(assembled, expectedResult)) {
                        ItemStack extracted = handler.extractItem(s, 1, false);
                        if (!extracted.isEmpty()) {
                            return extracted;
                        }
                    }
                }
                testInput.set(slotIndex, blueprint[slotIndex].isEmpty()
                        ? ItemStack.EMPTY : blueprint[slotIndex].copyWithCount(1));
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- JEI helper --------------------------------------------------------------

    private static ItemStack[] sanitizeIngredientPrototypes(Ingredient[] required, List<ItemStack> prototypes) {
        ItemStack[] sanitized = new ItemStack[9];
        for (int i = 0; i < sanitized.length; i++) {
            sanitized[i] = ItemStack.EMPTY;
        }
        if (required == null || required.length != 9 || prototypes == null) {
            return sanitized;
        }
        for (int i = 0; i < sanitized.length && i < prototypes.size(); i++) {
            Ingredient ingredient = required[i];
            ItemStack prototype = prototypes.get(i);
            if (ingredient == null || ingredient.isEmpty() || prototype == null || prototype.isEmpty()) {
                continue;
            }
            if (ingredient.test(prototype)) {
                sanitized[i] = prototype.copyWithCount(1);
            }
        }
        return sanitized;
    }
}
