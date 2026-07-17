package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class RtsEndpointLeaseCacheTest {
    private final UUID playerId = UUID.randomUUID();
    private final AtomicInteger releases = new AtomicInteger();
    private final RtsEndpointLeaseCache cache = new RtsEndpointLeaseCache(
            (owner, handler) -> releases.incrementAndGet());

    @AfterEach
    void clear() {
        cache.invalidatePlayer(playerId);
    }

    @Test
    void stableEndpointResolvesOnlyOnceUntilBlockEntityChanges() {
        AtomicInteger resolves = new AtomicInteger();
        Object firstBlockEntity = new Object();
        IItemHandler first = mock(IItemHandler.class);
        IItemHandler second = mock(IItemHandler.class);

        IItemHandler a = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return first;
                });
        IItemHandler b = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, firstBlockEntity, () -> {
                    resolves.incrementAndGet();
                    return second;
                });

        assertSame(a, b);
        assertEquals(1, resolves.get());

        IItemHandler c = cache.resolveItem(playerId, Level.OVERWORLD,
                BlockPos.ZERO, null, new Object(), () -> {
                    resolves.incrementAndGet();
                    return second;
                });
        assertSame(second, c);
        assertEquals(2, resolves.get());
        assertEquals(1, releases.get(), "替换租约必须释放旧处理器一次");

        cache.invalidatePlayer(playerId);
        cache.invalidatePlayer(playerId);
        assertEquals(2, releases.get(), "同一租约只能释放一次");
    }

    @Test
    void invalidationMustDetachTickBorrowerBeforeReleasingHandler() {
        ReleasableHandler handler = new ReleasableHandler();
        RtsEndpointLeaseCache ownedCache = new RtsEndpointLeaseCache((owner, leasedHandler) -> {
            RtsStorageTickService.INSTANCE.detachHandler(owner, (RtsItemStorage) leasedHandler);
            ((ReleasableHandler) leasedHandler).release();
        });

        try {
            ownedCache.resolveItem(playerId, Level.OVERWORLD, BlockPos.ZERO,
                    null, new Object(), () -> handler);
            RtsStorageTickService.INSTANCE.registerPlayer(playerId, List.of(handler));

            ownedCache.invalidatePlayer(playerId);

            assertEquals(1, handler.releaseCount.get(), "端点所有者应当只销毁一次处理器");
            assertDoesNotThrow(() -> RtsStorageTickService.INSTANCE.forceRefresh(playerId),
                    "租约释放后，下一次 Tick 刷新不应再访问失效的 AE/RS 处理器");
            assertEquals(1, handler.refreshAttempts.get(),
                    "异常隔离不能掩盖悬空借用；释放后必须完全停止刷新");
        } finally {
            ownedCache.invalidatePlayer(playerId);
            RtsStorageTickService.INSTANCE.unregisterPlayer(playerId);
        }
    }

    @Test
    void clearingTickCacheMustNotDestroyReusableEndpointLease() {
        Object endpointIdentity = new Object();
        ReleasableHandler handler = new ReleasableHandler();
        RtsEndpointLeaseCache ownedCache = new RtsEndpointLeaseCache((owner, leasedHandler) -> {
            RtsStorageTickService.INSTANCE.detachHandler(owner, (RtsItemStorage) leasedHandler);
            ((ReleasableHandler) leasedHandler).release();
        });

        try {
            IItemHandler first = ownedCache.resolveItem(playerId, Level.OVERWORLD, BlockPos.ZERO,
                    null, endpointIdentity, () -> handler);
            RtsStorageTickService.INSTANCE.registerPlayer(playerId, List.of(handler));

            // 模拟页面解析暂时得到空 Handler 列表：只清快照，端点租约仍应保持可复用。
            RtsStorageTickService.INSTANCE.unregisterPlayer(playerId);
            IItemHandler reused = ownedCache.resolveItem(playerId, Level.OVERWORLD, BlockPos.ZERO,
                    null, endpointIdentity, () -> new ReleasableHandler());

            assertSame(first, reused);
            assertEquals(0, handler.releaseCount.get(),
                    "临时清空聚合缓存不能提前销毁仍由端点租约持有的处理器");
        } finally {
            ownedCache.invalidatePlayer(playerId);
            RtsStorageTickService.INSTANCE.unregisterPlayer(playerId);
        }
        assertEquals(1, handler.releaseCount.get());
    }

    /** 模拟 AE2 release() 清空内部网络引用后，旧缓存仍尝试刷新的真实崩溃条件。 */
    private static final class ReleasableHandler
            implements IItemHandler, RefreshableSnapshotHandler, RtsItemStorage {
        private final AtomicInteger releaseCount = new AtomicInteger();
        private final AtomicInteger refreshAttempts = new AtomicInteger();
        private boolean released;

        @Override
        public void release() {
            this.released = true;
            this.releaseCount.incrementAndGet();
        }

        @Override
        public void ensureFreshSnapshot() {
            this.refreshAttempts.incrementAndGet();
            if (this.released) {
                throw new IllegalStateException("released handler was refreshed");
            }
        }

        @Override
        public void refreshSnapshot() {
            ensureFreshSnapshot();
        }

        @Override
        public Object identity() {
            return this;
        }

        @Override
        public int slotCount() {
            return getSlots();
        }

        @Override
        public ItemStack stackInSlot(int slot) {
            return getStackInSlot(slot);
        }

        @Override
        public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
            return insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extract(int slot, int amount, boolean simulate) {
            return extractItem(slot, amount, simulate);
        }

        @Override
        public int slotLimit(int slot) {
            return getSlotLimit(slot);
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
