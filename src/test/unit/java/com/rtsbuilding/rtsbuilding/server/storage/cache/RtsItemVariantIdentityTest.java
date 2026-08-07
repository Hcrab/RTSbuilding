package com.rtsbuilding.rtsbuilding.server.storage.cache;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RtsItemVariantIdentityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sameItemDifferentComponentsAreDifferentVariants() {
        ItemStack emeraldUpgrade = named("Emerald Top Upgrade", 1);
        ItemStack lapisUpgrade = named("Lapis Top Upgrade", 16);

        RtsItemVariantKey emeraldKey = RtsItemVariantKey.of(emeraldUpgrade);
        RtsItemVariantKey sameEmeraldKey = RtsItemVariantKey.of(named("Emerald Top Upgrade", 64));
        RtsItemVariantKey lapisKey = RtsItemVariantKey.of(lapisUpgrade);

        assertEquals(emeraldKey, sameEmeraldKey,
                "stack count must not change the storage variant identity");
        assertNotEquals(emeraldKey, lapisKey,
                "different ItemStack components must remain separate storage variants");
    }

    @Test
    void handlerAndAggregateCachesRetainEveryComponentVariant() {
        MutableVariantHandler handler = new MutableVariantHandler(
                named("Emerald Top Upgrade", 3),
                named("Lapis Top Upgrade", 5));
        RtsHandlerCache cache = new RtsHandlerCache();

        cache.update(handler);

        Map<RtsItemVariantKey, Long> variants = new HashMap<>();
        cache.getAvailableItemVariants(variants);
        assertEquals(2, variants.size(), "cache must expose both component variants");
        assertEquals(3L, variants.get(RtsItemVariantKey.of(named("Emerald Top Upgrade", 1))));
        assertEquals(5L, variants.get(RtsItemVariantKey.of(named("Lapis Top Upgrade", 1))));

        Map<String, Long> coarseTotals = new HashMap<>();
        cache.getAvailableItems(coarseTotals);
        assertEquals(8L, coarseTotals.get("minecraft:diamond"),
                "coarse item totals must still combine variants for routing");

        RtsAggregateStorage aggregate = new RtsAggregateStorage();
        aggregate.mount(100, handler, cache);
        Map<RtsItemVariantKey, Long> aggregateVariants = new HashMap<>();
        aggregate.getAvailableItemVariants(aggregateVariants);
        assertEquals(variants, aggregateVariants,
                "aggregate storage must forward the complete variant identity");

        handler.setSlot(0, ItemStack.EMPTY);
        cache.update(handler);
        variants.clear();
        cache.getAvailableItemVariants(variants);
        assertEquals(1, variants.size(), "removing one variant must not remove its sibling variant");
        assertEquals(5L, variants.get(RtsItemVariantKey.of(named("Lapis Top Upgrade", 1))));
    }

    private static ItemStack named(String name, int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static final class MutableVariantHandler implements IItemHandler {
        private final ItemStack[] slots;

        private MutableVariantHandler(ItemStack... slots) {
            this.slots = slots;
        }

        private void setSlot(int slot, ItemStack stack) {
            this.slots[slot] = stack;
        }

        @Override
        public int getSlots() {
            return this.slots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.slots[slot].copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
