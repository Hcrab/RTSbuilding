package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Extracts items from RTS linked storage / player inventory for placement.
 *
 * <p>This helper owns only the thin extraction boundary for placement
 * operations ??resolving preferred prototypes, building creative stacks,
 * and selecting from either network-wide (player inventory included) or
 * linked-only sources. It deliberately does not execute placement, manage
 * batch jobs, play sounds, or handle refund logic.
 */
public final class RtsPlacementExtractor {

    private RtsPlacementExtractor() {
    }

    /**
     * Validates and normalises an item prototype against the expected item id.
     * Returns a single-count copy of the prototype stack when it matches, or
     * {@link ItemStack#EMPTY} otherwise.
     */
    public static ItemStack sanitizePrototype(String itemId, ItemStack itemPrototype) {
        if (itemId == null || itemId.isBlank() || itemPrototype == null || itemPrototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation expectedId = ResourceLocation.tryParse(itemId);
        ResourceLocation actualId = BuiltInRegistries.ITEM.getKey(itemPrototype.getItem());
        if (expectedId == null || actualId == null || !expectedId.equals(actualId)) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = itemPrototype.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * Builds a single-count creative-mode stack, preferring the prototype's
     * components when available.
     */
    public static ItemStack creativeStack(Item item, ItemStack preferredStack) {
        if (preferredStack != null && !preferredStack.isEmpty()) {
            ItemStack copy = preferredStack.copy();
            copy.setCount(1);
            return copy;
        }
        return new ItemStack(item);
    }

    /**
     * Extracts one unit of {@code item} from the network (linked handlers +
     * player main inventory), preferring a prototype match when given.
     */
    public static ItemStack extractSelectedFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item item,
                                                        ItemStack preferredStack) {
        if (preferredStack != null && !preferredStack.isEmpty()) {
            return RtsTransferExtractor.extractMatchingFromNetwork(handlers, player, item, preferredStack, 1);
        }
        return RtsTransferExtractor.extractOneFromNetwork(handlers, player, item);
    }

    /**
     * Extracts one unit of {@code item} from linked handlers only, preferring
     * a prototype match when given.
     */
    public static ItemStack extractSelectedFromLinked(List<IItemHandler> handlers, Item item, ItemStack preferredStack) {
        if (preferredStack != null && !preferredStack.isEmpty()) {
            return RtsTransferExtractor.extractMatchingFromLinked(handlers, item, preferredStack, 1);
        }
        return RtsTransferExtractor.extractOneFromLinked(handlers, item);
    }
}
