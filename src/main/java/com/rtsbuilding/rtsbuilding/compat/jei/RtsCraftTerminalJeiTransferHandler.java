package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.menu.RtsMenuTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JEI recipe transfer handler for the RTS crafting terminal (C-key).
 * Transfers 3×3 crafting recipes into the RTS terminal's crafting grid,
 * pulling materials from linked storage.
 */
public final class RtsCraftTerminalJeiTransferHandler
        implements IRecipeTransferHandler<RtsCraftTerminalMenu, RecipeHolder<CraftingRecipe>> {
    private static final int CRAFT_GRID_SLOT_START = 1;
    private static final int CRAFT_GRID_SLOT_COUNT = 9;
    private static final int INVENTORY_SLOT_START = 10;
    private static final int INVENTORY_SLOT_COUNT = 36;

    private final IRecipeTransferHandlerHelper transferHelper;

    public RtsCraftTerminalJeiTransferHandler(
            IRecipeTransferHandlerHelper transferHelper) {
        Objects.requireNonNull(transferHelper, "transferHelper");
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<RtsCraftTerminalMenu> getContainerClass() {
        return RtsCraftTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<RtsCraftTerminalMenu>> getMenuType() {
        return Optional.of(RtsMenuTypes.RTS_CRAFT_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(RtsCraftTerminalMenu container, RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return checkMaterialAvailability(recipeSlots);
        }

        PacketDistributor.sendToServer(new C2SRtsJeiTransferPayload(
                recipe.id().toString(),
                buildIngredientPrototypes(recipe.value(), recipeSlots),
                maxTransfer,
                true));
        return null;
    }

    @Nullable
    private IRecipeTransferError checkMaterialAvailability(IRecipeSlotsView recipeSlots) {
        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews().stream()
                .filter(view -> view.getRole() == RecipeIngredientRole.INPUT
                        || view.getRole() == RecipeIngredientRole.CATALYST)
                .toList();
        if (inputViews.isEmpty()) {
            return null;
        }

        List<StorageEntry> storageEntries = ClientRtsController.get().getStorageEntries();
        if (storageEntries.isEmpty()) {
            return null;
        }

        long[] remaining = new long[storageEntries.size()];
        for (int i = 0; i < storageEntries.size(); i++) {
            remaining[i] = storageEntries.get(i).count();
        }

        List<IRecipeSlotView> missingSlots = new ArrayList<>();

        for (IRecipeSlotView view : inputViews) {
            if (view.isEmpty()) {
                continue;
            }
            List<ItemStack> possibleItems = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
            boolean found = false;
            for (ItemStack needed : possibleItems) {
                if (needed.isEmpty()) continue;
                for (int i = 0; i < storageEntries.size(); i++) {
                    if (remaining[i] <= 0) continue;
                    StorageEntry entry = storageEntries.get(i);
                    ResourceLocation neededId = BuiltInRegistries.ITEM.getKey(needed.getItem());
                    if (neededId != null && neededId.toString().equals(entry.itemId())) {
                        remaining[i]--;
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (!found) {
                missingSlots.add(view);
            }
        }

        if (missingSlots.isEmpty()) {
            return null;
        }

        Component message = Component.translatable("jei.tooltip.error.recipe.transfer.missing");
        return transferHelper.createUserErrorForMissingSlots(message, missingSlots);
    }

    private static List<ItemStack> buildIngredientPrototypes(CraftingRecipe recipe, IRecipeSlotsView recipeSlots) {
        List<ItemStack> prototypes = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            prototypes.add(ItemStack.EMPTY);
        }
        if (recipe == null || recipeSlots == null) {
            return prototypes;
        }

        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews().stream()
                .filter(view -> view.getRole() == RecipeIngredientRole.INPUT || view.getRole() == RecipeIngredientRole.CATALYST)
                .toList();
        Ingredient[] mapped = mapCraftingIngredients(recipe);
        int viewIndex = 0;
        for (int slot = 0; slot < mapped.length && viewIndex < inputViews.size(); slot++) {
            Ingredient ingredient = mapped[slot];
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ItemStack chosen = choosePrototype(inputViews.get(viewIndex), ingredient);
            prototypes.set(slot, chosen.isEmpty() ? ItemStack.EMPTY : chosen.copyWithCount(1));
            viewIndex++;
        }
        return prototypes;
    }

    private static ItemStack choosePrototype(IRecipeSlotView view, Ingredient ingredient) {
        if (view == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> stacks = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && ingredient.test(stack)) {
                return stack;
            }
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Ingredient[] mapCraftingIngredients(CraftingRecipe recipe) {
        Ingredient[] mapped = new Ingredient[9];
        for (int i = 0; i < mapped.length; i++) {
            mapped[i] = Ingredient.EMPTY;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = Math.max(1, Math.min(3, shaped.getWidth()));
            int height = Math.max(1, Math.min(3, shaped.getHeight()));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int src = y * width + x;
                    if (src >= 0 && src < ingredients.size()) {
                        mapped[y * 3 + x] = ingredients.get(src);
                    }
                }
            }
            return mapped;
        }

        int count = Math.min(9, ingredients.size());
        for (int i = 0; i < count; i++) {
            mapped[i] = ingredients.get(i);
        }
        return mapped;
    }
}
