package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReflectiveRtsItemHandlerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wrapsTheStableSlotContractWithoutLoaderTypes() {
        RtsItemHandler handler = ReflectiveRtsItemHandler.tryWrap(new FakeHandler()).orElseThrow();

        assertEquals(2, handler.getSlots());
        assertTrue(handler.getStackInSlot(0).is(Items.STONE));
        assertEquals(3, handler.extractItem(0, 3, true).getCount());
        assertEquals(64, handler.getSlotLimit(0));
        assertTrue(handler.isItemValid(0, new ItemStack(Items.DIRT)));
        assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT, 4), true).isEmpty());
    }

    @Test
    void reflectionFailureFailsClosedWithoutLosingTheInputStack() {
        RtsItemHandler handler = ReflectiveRtsItemHandler.tryWrap(new ThrowingHandler()).orElseThrow();
        ItemStack input = new ItemStack(Items.DIAMOND, 7);

        ItemStack remainder = handler.insertItem(0, input, false);
        assertTrue(remainder.is(Items.DIAMOND));
        assertEquals(7, remainder.getCount());
        assertTrue(handler.extractItem(0, 7, false).isEmpty());
        assertEquals(0, handler.getSlots());
        assertFalse(handler.isItemValid(0, input));
    }

    @Test
    void rejectsObjectsThatDoNotExposeTheCompleteInventoryContract() {
        assertTrue(ReflectiveRtsItemHandler.tryWrap(new Object()).isEmpty());
    }

    public static class FakeHandler {
        public int getSlots() {
            return 2;
        }

        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? new ItemStack(Items.STONE, 8) : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return ItemStack.EMPTY;
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return new ItemStack(Items.STONE, amount);
        }

        public int getSlotLimit(int slot) {
            return 64;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty();
        }
    }

    public static final class ThrowingHandler extends FakeHandler {
        @Override
        public int getSlots() {
            throw new IllegalStateException("boom");
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            throw new IllegalStateException("boom");
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            throw new IllegalStateException("boom");
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            throw new IllegalStateException("boom");
        }
    }
}
