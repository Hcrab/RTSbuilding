package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningDropBufferStackSplitTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(
                    java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void oversizedLogicalStackIsSplitIntoLegalVanillaStacks() {
        RtsMiningDropBufferState buffer = new RtsMiningDropBufferState();

        int accepted = buffer.enqueueMerged(new ItemStack(Items.COBBLESTONE), 130);

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
        buffer.stacks.add(new ItemStack(Items.COBBLESTONE, 63));
        buffer.bufferedItems = 63;

        int accepted = buffer.enqueueMerged(new ItemStack(Items.COBBLESTONE), 130);

        assertEquals(130, accepted);
        assertEquals(java.util.List.of(64, 64, 64, 1),
                buffer.stacks.stream().map(ItemStack::getCount).toList());
    }

    @Test
    void persistedStacksSurviveARecentlyLoweredConfiguredCapacity() {
        RtsMiningDropBufferState buffer = new RtsMiningDropBufferState();
        buffer.stacks.add(new ItemStack(Items.STONE));
        buffer.stacks.add(new ItemStack(Items.COBBLESTONE));
        buffer.stacks.add(new ItemStack(Items.DIRT));
        buffer.stacks.add(new ItemStack(Items.SAND));
        buffer.bufferedItems = 4;

        // 模拟配置已经从16调到4；恢复旧存档仍使用物理上限，不删除第5个已接纳栈。
        int accepted = buffer.restoreMerged(new ItemStack(Items.GRAVEL), 1);

        assertEquals(1, accepted);
        assertEquals(5, buffer.stacks.size());
        assertTrue(RtsMiningDropBufferPolicy.canRestorePersistedStack(4));
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
