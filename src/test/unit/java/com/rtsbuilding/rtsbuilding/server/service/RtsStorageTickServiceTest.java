package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsStorageTickServiceTest {
    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clearPlayerState() {
        RtsStorageTickService.INSTANCE.unregisterPlayer(playerId);
    }

    @Test
    void duplicateHandlerIdentityMustOnlyMountAndRefreshOnce() {
        CountingHandler handler = new CountingHandler();

        RtsStorageTickService.INSTANCE.registerPlayer(playerId, List.of(handler, handler, handler));

        assertEquals(1, handler.refreshes, "同一个 Handler 对象不能被重复挂载");
        RtsStorageTickService.INSTANCE.forceRefresh(playerId);
        assertEquals(2, handler.refreshes, "一次强制刷新只能访问该 Handler 一次");
    }

    @Test
    void equalButDistinctHandlersMustRemainIndependent() {
        CountingHandler first = new CountingHandler();
        CountingHandler second = new CountingHandler();

        RtsStorageTickService.INSTANCE.registerPlayer(playerId, List.of(first, second));

        assertEquals(1, first.refreshes);
        assertEquals(1, second.refreshes,
                "第三方 Handler 即使 equals() 相同，也不能被当成同一个网络端点");

        assertTrue(RtsStorageTickService.INSTANCE.detachHandler(playerId, first));
        assertFalse(RtsStorageTickService.INSTANCE.detachHandler(playerId, first),
                "重复卸载必须保持幂等");
        RtsStorageTickService.INSTANCE.forceRefresh(playerId);

        assertEquals(1, first.refreshes, "已卸载的处理器不能再被 Tick 服务访问");
        assertEquals(2, second.refreshes, "另一个相等但不同身份的处理器必须继续工作");
    }

    /** 故意让所有实例 equals() 相等，用于验证缓存严格采用对象身份。 */
    private static final class CountingHandler implements IItemHandler, RefreshableSnapshotHandler {
        private int refreshes;

        @Override
        public void ensureFreshSnapshot() {
            this.refreshes++;
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

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CountingHandler;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
