package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningDropBufferStackSplitTest {
    static {
        Bootstrap.register();
    }

    private static final Item COBBLESTONE = Item.getItemFromBlock(Blocks.COBBLESTONE);

    @Test
    void oversizedLogicalStackIsSplitIntoLegalVanillaStacks() {
        RtsMiningDropBufferState buffer = new RtsMiningDropBufferState();

        int accepted = buffer.enqueueMerged(new ItemStack(COBBLESTONE), 130);

        assertEquals(130, accepted);
        assertEquals(130, buffer.bufferedItems);
        assertEquals(java.util.List.of(64, 64, 2),
                buffer.stacks.stream().map(ItemStack::getCount).toList());
        assertTrue(buffer.stacks.stream()
                .allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()));
    }

    @Test
    void fragmentedExistingStackMergesBeforeCreatingLegalNewStacks() {
        RtsMiningDropBufferState buffer = new RtsMiningDropBufferState();
        buffer.stacks.add(new ItemStack(COBBLESTONE, 63));
        buffer.bufferedItems = 63;

        int accepted = buffer.enqueueMerged(new ItemStack(COBBLESTONE), 130);

        assertEquals(130, accepted);
        assertEquals(java.util.List.of(64, 64, 64, 1),
                buffer.stacks.stream().map(ItemStack::getCount).toList());
    }

    @Test
    void fullNoticeWaitsOneSecondInsteadOfFiringImmediately() {
        RtsMiningDropBufferState buffer = new RtsMiningDropBufferState();
        buffer.bufferedItems = RtsMiningDropBufferState.MAX_BUFFERED_ITEMS;

        buffer.updateFullState(100L);

        assertTrue(!buffer.shouldNotifyFull(119L, 20L));
        assertTrue(buffer.shouldNotifyFull(120L, 20L));
    }
}
