package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.*;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Handles craft execution: single-craft loop, ingredient extraction, output storage, rollback.
 */
public final class RtsCraftingExecutor {

    private RtsCraftingExecutor() {
    }

    /**
     * Opens the remote crafting terminal from RTS mode.
     */
    public static void openCraftTerminal(ServerPlayer player, RtsStorageSession session) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            player.displayClientMessage(Component.literal("Link at least one storage first."), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new RtsCraftTerminalMenu(
                        containerId, inventory,
                        new ContainerLevelAccess() {
                            @Override
                            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
                                return Optional.ofNullable(evaluator.apply(player.serverLevel(), player.blockPosition()));
                            }

                            @Override
                            public void execute(BiConsumer<Level, BlockPos> consumer) {
                                consumer.accept(player.serverLevel(), player.blockPosition());
                            }
                        }),
                Component.literal("RTS Craft Terminal")));
        RtsRemoteMenuService.relaxOpenedMenuValidation(player.containerMenu);
        ServiceOperationTemplate.refreshPage(player, session);
    }

    /**
     * Crafts a recipe into linked storage, up to {@code craftCount} times.
     */
    public static void craftRecipeToLinked(ServerPlayer player, RtsStorageSession session, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }
        RecipeHolder<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (raw == null || !(raw.value() instanceof CraftingRecipe craftingRecipe)) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }
        if (!RtsCraftingSearch.supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            RtsCraftingSearch.refreshCraftables(player, session);
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ItemStack previewResult = resolveCraftablePreviewResult(craftingRecipe, player);
        String resultLabel = previewResult.isEmpty() ? "item" : previewResult.getHoverName().getString();
        ResourceLocation previewResultId = previewResult.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(previewResult.getItem());
        int requestedCrafts = Math.max(1, Math.min(999, craftCount));
        int completedCrafts = 0;
        int totalCraftedCount = 0;
        boolean storageFull = false;
        String craftedItemId = previewResultId == null ? "" : previewResultId.toString();
        Map<String, Integer> consumedCounts = new java.util.LinkedHashMap<>();

        for (int i = 0; i < requestedCrafts; i++) {
            CraftExecutionResult result = craftSingleRecipeToLinked(
                    player, extractHandlers, insertHandlers, craftingRecipe);
            if (!result.success()) {
                storageFull = result.storageFull();
                break;
            }
            completedCrafts++;
            totalCraftedCount += result.resultCount();
            if (!result.resultItemId().isBlank()) {
                craftedItemId = result.resultItemId();
            }
            RtsCraftingUtils.mergeConsumedCounts(consumedCounts, result.consumedCounts());
        }

        ServiceOperationTemplate.refreshPage(player, session);
        RtsCraftingSearch.refreshCraftables(player, session);
        if (completedCrafts <= 0) {
            if (storageFull) {
                player.displayClientMessage(Component.literal("Craft: linked storage is full."), true);
            } else {
                player.displayClientMessage(Component.literal("Craft: missing ingredients."), true);
            }
            return;
        }

        RtsStorageRecentEntries.recordRecentItem(session, craftedItemId,
                S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, totalCraftedCount);
        ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
        PacketDistributor.sendToPlayer(player, new S2CRtsCraftFeedbackPayload(
                craftedItemId, totalCraftedCount,
                new ArrayList<>(consumedCounts.keySet()),
                new ArrayList<>(consumedCounts.values())));
        StringBuilder summary = new StringBuilder("Crafted ")
                .append(totalCraftedCount).append(" ").append(resultLabel);
        if (completedCrafts < requestedCrafts) {
            summary.append(" (").append(completedCrafts).append("/").append(requestedCrafts).append(" crafts)");
            summary.append(storageFull ? ", linked storage full." : ", missing ingredients for the rest.");
        } else {
            summary.append(".");
        }
        player.displayClientMessage(Component.literal(summary.toString()), true);
        QuestService.runQuestDetect(player, session, false);
        // 合成完成后自动尝试恢复挂起放置作业
        RtsPendingPlacementService.tryResumeAfterStorageChange(player);
    }

    // ---- single craft -----------------------------------------------------------

    private static CraftExecutionResult craftSingleRecipeToLinked(
            ServerPlayer player, List<IItemHandler> extractHandlers,
            List<IItemHandler> insertHandlers, CraftingRecipe recipe) {
        boolean includePlayerFallback = !(player.containerMenu instanceof RtsCraftTerminalMenu);

        Ingredient[] required = RtsCraftingUtils.mapCraftingIngredients(recipe);
        if (required.length != 9) {
            return CraftExecutionResult.failure(false);
        }
        CraftIngredientPlan plan = RtsCraftingAvailability.resolveCraftIngredientPlan(
                recipe,
                RtsCraftingAvailability.snapshotAvailable(
                        player, extractHandlers, includePlayerFallback));
        if (plan == null) {
            return CraftExecutionResult.failure(false);
        }

        ExtractedIngredient[] extracted = new ExtractedIngredient[9];
        List<ItemStack> inputStacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = required[i];
            if (ingredient == null || ingredient.isEmpty()) {
                inputStacks.add(ItemStack.EMPTY);
                continue;
            }
            ExtractedIngredient taken = takePlannedIngredientForCraft(
                    extractHandlers, player, ingredient, plan.prototypeAt(i), includePlayerFallback);
            if (taken == null || taken.stack().isEmpty()) {
                rollbackCraftIngredients(insertHandlers, player, extracted);
                return CraftExecutionResult.failure(false);
            }
            extracted[i] = taken;
            inputStacks.add(taken.stack().copyWithCount(1));
        }

        CraftingInput input = CraftingInput.of(3, 3, inputStacks);
        if (!recipe.matches(input, player.serverLevel())) {
            rollbackCraftIngredients(insertHandlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }
        ItemStack result = recipe.assemble(input, player.registryAccess());
        if (result.isEmpty()) {
            rollbackCraftIngredients(insertHandlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result.copy());
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        for (ItemStack remain : remaining) {
            if (!remain.isEmpty()) {
                outputs.add(remain.copy());
            }
        }

        List<ItemStack> storedOutputs = new ArrayList<>(outputs.size());
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(insertHandlers, stack);
            int storedCount = Math.max(0, stack.getCount() - remain.getCount());
            if (storedCount > 0) {
                storedOutputs.add(stack.copyWithCount(storedCount));
            }
            if (!remain.isEmpty()) {
                rollbackStoredCraftOutputs(insertHandlers, storedOutputs);
                rollbackCraftIngredients(insertHandlers, player, extracted);
                return CraftExecutionResult.failure(true);
            }
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        return new CraftExecutionResult(true, false,
                resultId == null ? "" : resultId.toString(),
                Math.max(1, result.getCount()),
                RtsCraftingUtils.collectConsumedCounts(extracted));
    }

    // ---- ingredient extraction ---------------------------------------------------

    private static ExtractedIngredient takePlannedIngredientForCraft(
            List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, ItemStack prototype, boolean includePlayerFallback) {
        if (ingredient == null || ingredient.isEmpty() || prototype == null || prototype.isEmpty() || !ingredient.test(prototype)) {
            return takeIngredientForCraft(handlers, player, ingredient, includePlayerFallback);
        }
        ItemStack fromLinked = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!fromLinked.isEmpty() && ingredient.test(fromLinked)) {
            return new ExtractedIngredient(fromLinked, false);
        }
        if (!includePlayerFallback) {
            return takeIngredientForCraft(handlers, player, ingredient, false);
        }
        ItemStack fromPlayer = RtsTransferExtractor.extractOneMatchingPrototypeFromPlayer(player, prototype);
        if (!fromPlayer.isEmpty() && ingredient.test(fromPlayer)) {
            return new ExtractedIngredient(fromPlayer, true);
        }
        return takeIngredientForCraft(handlers, player, ingredient, true);
    }

    private static ExtractedIngredient takeIngredientForCraft(
            List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, boolean includePlayerFallback) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
        if (!fromLinked.isEmpty()) {
            return new ExtractedIngredient(fromLinked, false);
        }
        if (!includePlayerFallback) {
            return null;
        }
        ItemStack fromPlayer = extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY);
        if (!fromPlayer.isEmpty()) {
            return new ExtractedIngredient(fromPlayer, true);
        }
        return null;
    }

    // ---- rollback ----------------------------------------------------------------

    private static void rollbackCraftIngredients(
            List<IItemHandler> handlers, ServerPlayer player, ExtractedIngredient[] extracted) {
        for (int i = extracted.length - 1; i >= 0; i--) {
            ExtractedIngredient ingredient = extracted[i];
            if (ingredient == null || ingredient.stack().isEmpty()) {
                continue;
            }
            if (ingredient.fromPlayer()) {
                RtsTransferInserter.moveToPlayerInventoryOnly(player, ingredient.stack());
                continue;
            }
            ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, ingredient.stack());
            if (!remain.isEmpty()) {
                RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
        }
    }

    private static void rollbackStoredCraftOutputs(List<IItemHandler> handlers, List<ItemStack> storedOutputs) {
        for (int i = storedOutputs.size() - 1; i >= 0; i--) {
            ItemStack stored = storedOutputs.get(i);
            int remaining = stored.getCount();
            while (remaining > 0) {
                ItemStack extracted = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, stored);
                if (extracted.isEmpty()) {
                    break;
                }
                remaining -= extracted.getCount();
            }
        }
    }

    // ---- ingredient extraction helpers --------------------------------------------

    private static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient) {
        return extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
    }

    static ItemStack extractOneMatchingIngredient(
            List<IItemHandler> handlers, Ingredient ingredient, ItemStack preferred) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromHandlers(handlers, ingredient, preferred);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromHandlers(handlers, ingredient, ItemStack.EMPTY);
    }

    private static ItemStack extractOneMatchingIngredientFromHandlers(
            List<IItemHandler> handlers, Ingredient ingredient, ItemStack preferred) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(stack, preferred)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (extracted.isEmpty()) {
                    continue;
                }
                if (ingredient.test(extracted)) {
                    return extracted;
                }
                ItemStack remainder = RtsTransferInserter.insertToHandlerPreferExisting(handler, extracted);
                if (!remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- ingredient extraction (package-visible for GridFiller) -------------------

    static ItemStack extractOneMatchingIngredientCombined(
            List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, ItemStack preferred) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, preferred);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, preferred);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(
            ServerPlayer player, Ingredient ingredient, ItemStack preferred) {
        if (player == null || ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromPlayer(player, ingredient, preferred, true);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY, false);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(
            ServerPlayer player, Ingredient ingredient, ItemStack preferred, boolean requirePreferredMatch) {
        int start = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ingredient.test(current)) {
                continue;
            }
            if (requirePreferredMatch && !ItemStack.isSameItemSameComponents(current, preferred)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty() && ingredient.test(extracted)) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- preview result -----------------------------------------------------------

    private static ItemStack resolveCraftablePreviewResult(CraftingRecipe recipe, ServerPlayer player) {
        if (recipe == null || player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.getResultItem(player.registryAccess());
        if (!result.isEmpty()) {
            return result.copy();
        }
        Ingredient[] mapped = RtsCraftingUtils.mapCraftingIngredients(recipe);
        java.util.List<ItemStack> previewStacks = new ArrayList<>(9);
        for (Ingredient ingredient : mapped) {
            if (ingredient == null || ingredient.isEmpty()) {
                previewStacks.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length <= 0 || options[0].isEmpty()) {
                return ItemStack.EMPTY;
            }
            previewStacks.add(options[0].copyWithCount(1));
        }
        return recipe.assemble(CraftingInput.of(3, 3, previewStacks), player.registryAccess()).copy();
    }

    // ---- craft grid refill from blueprint ----------------------------------------

    /**
     * Captures a one-item-per-slot blueprint of the current crafting grid.
     */
    public static ItemStack[] snapshotCraftGridBlueprint(
            net.minecraft.world.inventory.CraftingMenu menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.inventory.Slot grid = menu.getSlot(1 + i);
            ItemStack stack = grid.getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }
}
