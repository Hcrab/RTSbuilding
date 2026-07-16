package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsHandlerCacheTest {

    @Test
    void transientSnapshotFailureMustNotCrashServerTickAndCanRetry() {
        RtsHandlerCache cache = new RtsHandlerCache();
        FailOnceRefreshHandler handler = new FailOnceRefreshHandler();

        assertDoesNotThrow(() -> cache.update(handler),
                "外部储存网络切换瞬间失败时不能打穿服务端 Tick");
        assertDoesNotThrow(() -> cache.update(handler),
                "下一刷新周期应能重新尝试兼容层快照");
        assertEquals(2, handler.attempts);
    }

    private static final class FailOnceRefreshHandler implements IItemHandler, RefreshableSnapshotHandler {
        private int attempts;

        @Override
        public void ensureFreshSnapshot() {
            this.attempts++;
            if (this.attempts == 1) {
                throw new IllegalStateException("network changed during snapshot");
            }
        }

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
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
            return false;
        }
    }
}
