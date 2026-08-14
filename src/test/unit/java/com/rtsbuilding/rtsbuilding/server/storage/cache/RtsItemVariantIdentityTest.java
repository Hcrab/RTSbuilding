package com.rtsbuilding.rtsbuilding.server.storage.cache;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RtsItemVariantIdentityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sameItemDifferentNbtAreDifferentVariants() {
        RtsItemVariantKey emerald = RtsItemVariantKey.of(named("Emerald", 1));
        RtsItemVariantKey sameEmerald = RtsItemVariantKey.of(named("Emerald", 64));
        RtsItemVariantKey lapis = RtsItemVariantKey.of(named("Lapis", 16));
        assertEquals(emerald, sameEmerald, "数量不能改变变体身份");
        assertNotEquals(emerald, lapis, "不同 NBT 必须保持为不同储存条目");
    }

    @Test
    void cacheRetainsEveryNbtVariant() {
        MutableVariantHandler handler = new MutableVariantHandler(
                named("Emerald", 3), named("Lapis", 5));
        RtsHandlerCache cache = new RtsHandlerCache();
        cache.update(handler);

        Map<RtsItemVariantKey, Long> variants = new HashMap<>();
        cache.getAvailableItemVariants(variants);
        assertEquals(2, variants.size());
        assertEquals(3L, variants.get(RtsItemVariantKey.of(named("Emerald", 1))));
        assertEquals(5L, variants.get(RtsItemVariantKey.of(named("Lapis", 1))));

        handler.setSlot(0, ItemStack.EMPTY);
        cache.update(handler);
        variants.clear();
        cache.getAvailableItemVariants(variants);
        assertEquals(1, variants.size(), "删除一个变体不能连带删除同 ID 的另一个变体");
    }

    private static ItemStack named(String name, int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.setHoverName(Component.literal(name));
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

        @Override public int getSlots() { return this.slots.length; }
        @Override public ItemStack getStackInSlot(int slot) { return this.slots[slot].copy(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}
