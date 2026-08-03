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
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;

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
            EntityPlayerMP player, RtsStorageSession session,
            ContainerWorkbench craftingMenu, ItemStack[] blueprint) {
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint, null);
    }

    public static void refillCraftGridFromLinked(
            EntityPlayerMP player, RtsStorageSession session,
            ContainerWorkbench craftingMenu, ItemStack[] blueprint, IRecipe recipe) {
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
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        Ingredient[] ingredients = recipe == null ? null : RtsCraftingUtils.mapCraftingIngredients(recipe);
        refillCraftGridFromBlueprint(craftingMenu, handlers, player, blueprint, ingredients, false, true);
        craftingMenu.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
    }

    // ---- refill from ids / stacks (network packets) ------------------------------

    /**
     * 从客户端发送的物品 ID 重新填充当前合成网格。
     */
    public static void refillCurrentCraftGridFromBlueprintIds(
            EntityPlayerMP player, RtsStorageSession session,
            List<String> blueprintIds, String craftedItemId, int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintIds == null || blueprintIds.size() != 9) {
            return;
        }
        if (!(player.openContainer instanceof ContainerWorkbench)) {
            return;
        }
        ContainerWorkbench craftingMenu = (ContainerWorkbench) player.openContainer;
        if (session != null && !RtsCraftingUtils.isBlank(craftedItemId) && craftedCount > 0) {
            ServiceRegistry.getInstance().page().recordRecentItem(session, craftedItemId,
                    S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
        }
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            String itemId = blueprintIds.get(i);
            if (RtsCraftingUtils.isBlank(itemId)) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            ResourceLocation key;
            try { key = new ResourceLocation(itemId); } catch (RuntimeException invalid) { key = null; }
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            blueprint[i] = new ItemStack(ForgeRegistries.ITEMS.getValue(key));
        }
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    /**
     * 从客户端发送的精确物品原型重新填充当前合成网格。
     */
    public static void refillCurrentCraftGridFromBlueprintStacks(
            EntityPlayerMP player, RtsStorageSession session,
            List<ItemStack> blueprintStacks, String craftedItemId, int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintStacks == null || blueprintStacks.size() != 9) {
            return;
        }
        if (!(player.openContainer instanceof ContainerWorkbench)) {
            return;
        }
        ContainerWorkbench craftingMenu = (ContainerWorkbench) player.openContainer;
        if (session != null && !RtsCraftingUtils.isBlank(craftedItemId) && craftedCount > 0) {
            ServiceRegistry.getInstance().page().recordRecentItem(session, craftedItemId,
                    S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
        }
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            ItemStack stack = blueprintStacks.get(i);
            blueprint[i] = RtsCraftingUtils.one(stack);
        }
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint);
    }

    // ---- JEI transfer ------------------------------------------------------------

    public static void applyJeiTransfer(
            EntityPlayerMP player, RtsStorageSession session,
            String recipeId, List<ItemStack> ingredientPrototypes,
            boolean maxTransfer, boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!(player.openContainer instanceof ContainerWorkbench)) {
            return;
        }
        ContainerWorkbench craftingMenu = (ContainerWorkbench) player.openContainer;
        if (RtsCraftingUtils.isBlank(recipeId)) {
            return;
        }
        ResourceLocation key;
        try { key = new ResourceLocation(recipeId); } catch (RuntimeException invalid) { return; }
        IRecipe craftingRecipe = CraftingManager.getRecipe(key);
        if (craftingRecipe == null) return;

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
                ItemStack existing = grid.getStack();
                if (existing.isEmpty()) {
                    cleared.add(ItemStack.EMPTY);
                    continue;
                }
                ItemStack copy = existing.copy();
                grid.putStack(ItemStack.EMPTY);
                grid.onSlotChanged();
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
                if (RtsCraftingUtils.isIngredientEmpty(ingredient)) {
                    continue;
                }
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getStack();
                if (!existing.isEmpty()) {
                    if (!ingredient.apply(existing)) {
                        continue;
                    }
                    if (existing.getCount() >= existing.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = RtsTransferExtractor.extractOneMatchingPrototypeCombined(
                            extractHandlers, player, existing);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    existing.grow(1);
                    grid.onSlotChanged();
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
                grid.putStack(extracted);
                grid.onSlotChanged();
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
        craftingMenu.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
        if (anyInserted) {
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- low-level grid refill loop ----------------------------------------------

    /**
     * 执行从链接存储/玩家回退的低级网格填充循环。
     */
    public static void refillCraftGridFromBlueprint(
            ContainerWorkbench menu, List<IItemHandler> handlers, EntityPlayerMP player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        refillCraftGridFromBlueprint(menu, handlers, player, blueprint, null, fillAll, includePlayerFallback);
    }

    public static void refillCraftGridFromBlueprint(
            ContainerWorkbench menu, List<IItemHandler> handlers, EntityPlayerMP player,
            ItemStack[] blueprint, Ingredient[] ingredients,
            boolean fillAll, boolean includePlayerFallback) {
        if (blueprint == null || blueprint.length != 9) {
            return;
        }
        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack blueprintStack = blueprint[i];
                Ingredient ingredient = ingredients != null && i < ingredients.length ? ingredients[i] : Ingredient.EMPTY;
                boolean hasBlueprint = blueprintStack != null && !blueprintStack.isEmpty();
                boolean hasIngredient = !RtsCraftingUtils.isIngredientEmpty(ingredient);
                // 玩家实际摆放的 3x3 蓝图决定哪些槽需要回填。配方 Ingredient 通常被归一化到左上角，
                // 不能让它在蓝图为空的槽凭空生成材料，否则中排摆放的台阶配方会被搬到最上排。
                if (!hasBlueprint) {
                    continue;
                }
                boolean ingredientMatchesBlueprint = hasIngredient && ingredient.apply(blueprintStack);
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getStack();
                if (!current.isEmpty()) {
                    if (ingredientMatchesBlueprint
                            ? !ingredient.apply(current)
                            : !RtsCraftingUtils.sameStack(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(handlers, player, current)
                            : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, current);
                    if (extracted.isEmpty() || !RtsCraftingUtils.sameStack(current, extracted)) {
                        if (!extracted.isEmpty()) {
                            RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(handlers, player, extracted);
                        }
                        continue;
                    }
                    current.grow(1);
                    grid.onSlotChanged();
                    inserted = true;
                    changed = true;
                    continue;
                }

                ItemStack extracted = extractCraftGridRefillStack(
                        handlers, player,
                        ingredientMatchesBlueprint ? ingredient : Ingredient.EMPTY,
                        blueprintStack, includePlayerFallback);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.putStack(extracted);
                grid.onSlotChanged();
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

    private static ItemStack extractCraftGridRefillStack(
            List<IItemHandler> handlers, EntityPlayerMP player,
            Ingredient ingredient, ItemStack preferred, boolean includePlayerFallback) {
        boolean hasIngredient = !RtsCraftingUtils.isIngredientEmpty(ingredient);
        if (hasIngredient) {
            ItemStack extracted = includePlayerFallback
                    ? RtsCraftingExecutor.extractOneMatchingIngredientCombined(handlers, player, ingredient, preferred)
                    : RtsCraftingExecutor.extractOneMatchingIngredient(handlers, ingredient, preferred);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        if (preferred == null || preferred.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return includePlayerFallback
                ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(handlers, player, preferred)
                : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, preferred);
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
            if (RtsCraftingUtils.isIngredientEmpty(ingredient) || prototype == null || prototype.isEmpty()) {
                continue;
            }
            if (ingredient.apply(prototype)) {
                sanitized[i] = RtsCraftingUtils.one(prototype);
            }
        }
        return sanitized;
    }
}
