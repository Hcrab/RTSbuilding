package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import com.rtsbuilding.rtsbuilding.platform.crafting.Ingredient;
import com.rtsbuilding.rtsbuilding.platform.crafting.LegacyRecipeCompat;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 1.12.2 远程合成执行器：真实匹配、真实剩余物、失败回滚。 */
public final class RtsCraftingExecutor {
    private RtsCraftingExecutor() {}

    public static void openCraftTerminal(final EntityPlayerMP player, final RtsStorageSession session) {
        if (player == null || session == null || !RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) return;
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            status(player, "Link at least one storage first.");
            return;
        }
        // 1.7.10 没有 IInteractionObject/菜单注册表；沿用原版工作台的开窗协议，
        // 再把服务端容器替换为 RTS 子类。客户端收到 type=1 后先创建 GuiCrafting，
        // 随后的 RTS 生命周期钩子会在同一 windowId 上换成合成终端界面。
        if (player.openContainer != player.inventoryContainer) {
            player.closeScreen();
        }
        player.getNextWindowId();
        player.playerNetServerHandler.sendPacket(new S2DPacketOpenWindow(
                player.currentWindowId, 1, "RTS Craft Terminal", 9, true));
        RtsCraftTerminalMenu menu = new RtsCraftTerminalMenu(
                player.inventory, player.worldObj, com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.blockPosition(player));
        menu.windowId = player.currentWindowId;
        player.openContainer = menu;
        menu.addCraftingToCrafters(player);
        RtsRemoteMenuService.relaxOpenedMenuValidation(player.openContainer);
        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
    }

    public static void craftRecipeToLinked(EntityPlayerMP player, RtsStorageSession session,
            String recipeId, int craftCount) {
        if (player == null || session == null || !RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) return;
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session) || RtsCraftingUtils.isBlank(recipeId)) {
            RtsCraftingSearch.refreshCraftables(player, session); return;
        }
        IRecipe recipe = resolveRecipe(recipeId);
        if (!RtsCraftingSearch.supportsWorkbenchCraftPanelRecipe(recipe)) {
            RtsCraftingSearch.refreshCraftables(player, session); return;
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (linked.isEmpty()) { RtsCraftingSearch.refreshCraftables(player, session); return; }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(linked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        ItemStack preview = RtsCraftingSearch.resolveCraftablePreviewResult(recipe, player);
        String resultLabel = com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview) ? "item" : preview.getDisplayName();
        ResourceLocation previewId = com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview) ? null : RtsRegistries.ITEMS.getKey(preview.getItem());
        int requested = Math.max(1, Math.min(999, craftCount));
        int completed = 0;
        int total = 0;
        boolean storageFull = false;
        String resultId = previewId == null ? "" : previewId.toString();
        Map<String, Integer> consumed = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < requested; i++) {
            CraftExecutionResult result = craftSingleRecipeToLinked(player, extractHandlers, insertHandlers, recipe);
            if (!result.success()) { storageFull = result.storageFull(); break; }
            completed++;
            total += result.resultCount();
            if (!RtsCraftingUtils.isBlank(result.resultItemId())) resultId = result.resultItemId();
            RtsCraftingUtils.mergeConsumedCounts(consumed, result.consumedCounts());
        }

        ServiceRegistry.getInstance().serviceOp().refreshPage(player, session);
        RtsCraftingSearch.refreshCraftables(player, session);
        if (completed == 0) {
            status(player, storageFull ? "Craft: linked storage is full." : "Craft: missing ingredients.");
            return;
        }
        ServiceRegistry.getInstance().page().recordRecentItem(session, resultId,
                S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, total);
        RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsCraftFeedbackPayload(resultId, total,
                new ArrayList<String>(consumed.keySet()), new ArrayList<Integer>(consumed.values())));
        StringBuilder summary = new StringBuilder("Crafted ").append(total).append(' ').append(resultLabel);
        if (completed < requested) summary.append(" (").append(completed).append('/').append(requested)
                .append(" crafts), ").append(storageFull ? "linked storage full." : "missing ingredients for the rest.");
        else summary.append('.');
        status(player, summary.toString());
        QuestService.runQuestDetect(player, session, false);
        RtsPendingPlacementService.tryResumeAfterStorageChange(player, Collections.singletonList(resultId));
    }

    private static IRecipe resolveRecipe(String recipeId) {
        return LegacyRecipeCompat.byId(recipeId);
    }

    private static CraftExecutionResult craftSingleRecipeToLinked(EntityPlayerMP player,
            List<IItemHandler> extractHandlers, List<IItemHandler> insertHandlers, IRecipe recipe) {
        boolean includePlayer = !(player.openContainer instanceof RtsCraftTerminalMenu);
        Ingredient[] required = RtsCraftingUtils.mapCraftingIngredients(recipe);
        CraftIngredientPlan plan = RtsCraftingAvailability.resolveCraftIngredientPlan(recipe,
                RtsCraftingAvailability.snapshotAvailable(player, extractHandlers, includePlayer));
        if (plan == null) return CraftExecutionResult.failure(false);

        ExtractedIngredient[] extracted = new ExtractedIngredient[9];
        InventoryCrafting input = RtsCraftingUtils.newCraftingGrid();
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = required[i];
            if (RtsCraftingUtils.isIngredientEmpty(ingredient)) continue;
            ExtractedIngredient taken = takePlannedIngredientForCraft(
                    extractHandlers, player, ingredient, plan.prototypeAt(i), includePlayer);
            if (taken == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(taken.stack())) {
                rollbackCraftIngredients(insertHandlers, player, extracted);
                return CraftExecutionResult.failure(false);
            }
            extracted[i] = taken;
            input.setInventorySlotContents(i, RtsCraftingUtils.one(taken.stack()));
        }
        if (!recipe.matches(input, player.worldObj)) {
            rollbackCraftIngredients(insertHandlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }
        ItemStack result = recipe.getCraftingResult(input);
        if (result == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(result)) {
            rollbackCraftIngredients(insertHandlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        List<ItemStack> outputs = new ArrayList<ItemStack>();
        outputs.add(result.copy());
        List<ItemStack> remaining = LegacyRecipeCompat.remainingItems(input);
        if (remaining != null) for (ItemStack stack : remaining) if (stack != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) outputs.add(stack.copy());

        List<ItemStack> stored = new ArrayList<ItemStack>();
        for (ItemStack output : outputs) {
            ItemStack remainder = RtsTransferInserter.storeToLinkedOnlyPreferExisting(insertHandlers, output);
            int storedCount = Math.max(0, output.stackSize - (remainder == null ? 0 : remainder.stackSize));
            if (storedCount > 0) {
                ItemStack storedPart = output.copy(); storedPart.stackSize = storedCount; stored.add(storedPart);
            }
            if (remainder != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remainder)) {
                rollbackStoredCraftOutputs(insertHandlers, stored);
                rollbackCraftIngredients(insertHandlers, player, extracted);
                return CraftExecutionResult.failure(true);
            }
        }
        ResourceLocation id = RtsRegistries.ITEMS.getKey(result.getItem());
        return new CraftExecutionResult(true, false, id == null ? "" : id.toString(),
                Math.max(1, result.stackSize), RtsCraftingUtils.collectConsumedCounts(extracted));
    }

    private static ExtractedIngredient takePlannedIngredientForCraft(List<IItemHandler> handlers,
            EntityPlayerMP player, Ingredient ingredient, ItemStack prototype, boolean includePlayer) {
        if (!RtsCraftingUtils.isIngredientEmpty(ingredient) && prototype != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(prototype)
                && ingredient.apply(prototype)) {
            ItemStack linked = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, prototype);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(linked) && ingredient.apply(linked)) return new ExtractedIngredient(linked, false);
            if (includePlayer) {
                ItemStack inventory = RtsTransferExtractor.extractOneMatchingPrototypeFromPlayer(player, prototype);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(inventory) && ingredient.apply(inventory)) return new ExtractedIngredient(inventory, true);
            }
        }
        return takeIngredientForCraft(handlers, player, ingredient, includePlayer);
    }

    private static ExtractedIngredient takeIngredientForCraft(List<IItemHandler> handlers,
            EntityPlayerMP player, Ingredient ingredient, boolean includePlayer) {
        ItemStack linked = extractOneMatchingIngredient(handlers, ingredient, null);
        if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(linked)) return new ExtractedIngredient(linked, false);
        if (!includePlayer) return null;
        ItemStack inventory = extractOneMatchingIngredientFromPlayer(player, ingredient, null);
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(inventory) ? null : new ExtractedIngredient(inventory, true);
    }

    private static void rollbackCraftIngredients(List<IItemHandler> handlers, EntityPlayerMP player,
            ExtractedIngredient[] extracted) {
        for (int i = extracted.length - 1; i >= 0; i--) {
            ExtractedIngredient ingredient = extracted[i];
            if (ingredient == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(ingredient.stack())) continue;
            if (ingredient.fromPlayer()) {
                RtsTransferInserter.moveToPlayerInventoryOnly(player, ingredient.stack());
            } else {
                ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, ingredient.stack());
                if (remain != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
        }
    }

    private static void rollbackStoredCraftOutputs(List<IItemHandler> handlers, List<ItemStack> stored) {
        for (int i = stored.size() - 1; i >= 0; i--) {
            ItemStack prototype = stored.get(i);
            int amount = prototype.stackSize;
            while (amount > 0) {
                ItemStack removed = RtsTransferExtractor.extractOneMatchingPrototypeFromLinked(handlers, prototype);
                if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(removed)) break;
                amount -= removed.stackSize;
            }
        }
    }

    static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient, ItemStack preferred) {
        if (RtsCraftingUtils.isIngredientEmpty(ingredient) || handlers == null) return null;
        if (preferred != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preferred) && ingredient.apply(preferred)) {
            ItemStack exact = extractOneMatchingIngredientFromHandlers(handlers, ingredient, preferred);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(exact)) return exact;
        }
        return extractOneMatchingIngredientFromHandlers(handlers, ingredient, null);
    }

    private static ItemStack extractOneMatchingIngredientFromHandlers(List<IItemHandler> handlers,
            Ingredient ingredient, ItemStack preferred) {
        for (IItemHandler handler : handlers) {
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || !ingredient.apply(stack)
                        || (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preferred) && !RtsCraftingUtils.sameStack(stack, preferred))) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(simulated) || !ingredient.apply(simulated)
                        || (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preferred) && !RtsCraftingUtils.sameStack(simulated, preferred))) continue;
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted) && ingredient.apply(extracted)) return extracted;
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted)) RtsTransferInserter.insertToHandlerPreferExisting(handler, extracted);
            }
        }
        return null;
    }

    static ItemStack extractOneMatchingIngredientCombined(List<IItemHandler> handlers, EntityPlayerMP player,
            Ingredient ingredient, ItemStack preferred) {
        ItemStack linked = extractOneMatchingIngredient(handlers, ingredient, preferred);
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(linked) ? extractOneMatchingIngredientFromPlayer(player, ingredient, preferred) : linked;
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(EntityPlayerMP player,
            Ingredient ingredient, ItemStack preferred) {
        if (player == null || RtsCraftingUtils.isIngredientEmpty(ingredient)) return null;
        int start = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int pass = 0; pass < 2; pass++) {
            boolean exact = pass == 0 && preferred != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preferred);
            if (pass == 0 && !exact) continue;
            for (int slot = start; slot < end; slot++) {
                ItemStack current = player.inventory.getStackInSlot(slot);
                if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(current) || !ingredient.apply(current)
                        || (exact && !RtsCraftingUtils.sameStack(current, preferred))) continue;
                ItemStack extracted = current.splitStack(1);
                player.inventory.setInventorySlotContents(slot, com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(current) ? null : current);
                return extracted;
            }
        }
        return null;
    }

    public static ItemStack[] snapshotCraftGridBlueprint(ContainerWorkbench menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) blueprint[i] = RtsCraftingUtils.one(menu.getSlot(1 + i).getStack());
        return blueprint;
    }

    private static void status(EntityPlayerMP player, String message) {
        com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(player, new ChatComponentText(message), true);
    }
}
