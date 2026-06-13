package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles craft grid refill from linked storage and JEI recipe transfer.
 */
public final class RtsCraftingGridFiller {

    private RtsCraftingGridFiller() {
    }

    // ---- refill from linked storage (player result click) -----------------------

    /**
     * Refills an open crafting grid from linked storage using a one-item blueprint.
     */
    public static void refillCraftGridFromLinked(
            ServerPlayer player, RtsStorageSession session,
            CraftingMenu craftingMenu, ItemStack[] blueprint) {
        refillCraftGridFromLinked(player, session, craftingMenu, blueprint, null);
    }

    public static void refillCraftGridFromLinked(
            ServerPlayer player, RtsStorageSession session,
            CraftingMenu craftingMenu, ItemStack[] blueprint, CraftingRecipe recipe) {
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
        craftingMenu.broadcastChanges();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    // ---- refill from ids / stacks (network packets) ------------------------------

    /**
     * Rehydrates the current crafting grid from item ids sent by the client.
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
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }
        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            RtsStorageRecentEntries.recordRecentItem(session, craftedItemId,
                    com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            RtsSessionService.saveToPlayerNbt(player, session);
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
     * Rehydrates the current crafting grid from exact item prototypes sent by the client.
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
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }
        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            RtsStorageRecentEntries.recordRecentItem(session, craftedItemId,
                    com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            RtsSessionService.saveToPlayerNbt(player, session);
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
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            return;
        }
        Recipe<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (!(raw instanceof CraftingRecipe craftingRecipe)) {
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
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        if (anyInserted) {
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- low-level grid refill loop ----------------------------------------------

    /**
     * Performs the low-level grid refill loop from linked storage / player fallback.
     */
    public static void refillCraftGridFromBlueprint(
            CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        refillCraftGridFromBlueprint(menu, handlers, player, blueprint, null, fillAll, includePlayerFallback);
    }

    public static void refillCraftGridFromBlueprint(
            CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
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
                boolean hasIngredient = ingredient != null && !ingredient.isEmpty();
                if (!hasBlueprint && !hasIngredient) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();
                if (!current.isEmpty()) {
                    if (hasIngredient ? !ingredient.test(current) : !ItemStack.isSameItemSameTags(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? RtsTransferExtractor.extractOneMatchingPrototypeCombined(handlers, player, current)
                            : RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, current);
                    if (extracted.isEmpty() || !ItemStack.isSameItemSameTags(current, extracted)) {
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

                ItemStack extracted = extractCraftGridRefillStack(
                        handlers, player, ingredient, blueprintStack, includePlayerFallback);
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

    private static ItemStack extractCraftGridRefillStack(
            List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, ItemStack preferred, boolean includePlayerFallback) {
        boolean hasIngredient = ingredient != null && !ingredient.isEmpty();
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
