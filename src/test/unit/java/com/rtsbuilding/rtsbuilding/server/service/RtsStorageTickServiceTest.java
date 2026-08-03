package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.view.LinkedItemHandlerView;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsStorageTickServiceTest {
    private final UUID playerId = UUID.randomUUID();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

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

    @Test
    void extractOnlyModeMustReplaceAggregateMountWithoutLosingEndpointIdentity() {
        ItemStackHandler endpoint = new ItemStackHandler(1);

        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(new LinkedItemHandlerView(endpoint, true)));
        assertTrue(aggregate.insert(new ItemStack(Blocks.STONE, 4), false).isEmpty());
        assertEquals(4, endpoint.getStackInSlot(0).getCount());

        RtsAggregateStorage afterModeChange = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(new LinkedItemHandlerView(endpoint, false)));
        assertSame(aggregate, afterModeChange, "同一原始端点切换权限时必须复用聚合缓存");

        ItemStack rejected = afterModeChange.insert(new ItemStack(Blocks.STONE, 3), false);
        assertEquals(3, rejected.getCount(), "Extract-only 挂载必须原样拒绝聚合缓存写入");
        assertEquals(4, endpoint.getStackInSlot(0).getCount(), "拒绝写入后机器内容不能变化");

        RtsStorageTickService.INSTANCE.forceRefresh(playerId);
        ItemStack extracted = afterModeChange.extract(Item.getItemFromBlock(Blocks.STONE), 2);
        assertEquals(2, extracted.getCount(), "Extract-only 仍必须允许从机器提取");
        assertEquals(2, endpoint.getStackInSlot(0).getCount());

        RtsAggregateStorage afterReenable = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(new LinkedItemHandlerView(endpoint, true)));
        assertSame(aggregate, afterReenable);
        assertTrue(afterReenable.insert(new ItemStack(Blocks.STONE, 5), false).isEmpty());
        assertEquals(7, endpoint.getStackInSlot(0).getCount(), "恢复双向后应立即重新允许写入");
    }

    @Test
    void extractOnlyViewMustReplaceLegacyRawAggregateMount() {
        ItemStackHandler endpoint = new ItemStackHandler(1);
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(endpoint));
        assertTrue(aggregate.insert(new ItemStack(Blocks.STONE, 2), false).isEmpty());

        RtsAggregateStorage guarded = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(new LinkedItemHandlerView(endpoint, false)));
        ItemStack rejected = guarded.insert(new ItemStack(Blocks.STONE, 3), false);

        assertSame(aggregate, guarded);
        assertEquals(3, rejected.getCount(), "旧版遗留的原始挂载也必须被只提取视图替换");
        assertEquals(2, endpoint.getStackInSlot(0).getCount(), "替换权限视图后不得继续向端点写入");
    }

    @Test
    void cachedAggregateViewMustObservePermissionChangesWithoutRemount() {
        ItemStackHandler endpoint = new ItemStackHandler(1);
        AtomicBoolean storeAllowed = new AtomicBoolean(true);
        LinkedItemHandlerView policyView = new LinkedItemHandlerView(endpoint, storeAllowed::get);
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.registerPlayer(
                playerId, List.of(policyView));

        assertTrue(aggregate.insert(new ItemStack(Blocks.STONE, 2), false).isEmpty());
        storeAllowed.set(false);

        ItemStack rejected = aggregate.insert(new ItemStack(Blocks.STONE, 3), false);
        assertEquals(3, rejected.getCount(), "Cached aggregate views must observe Extract Only immediately");
        assertEquals(2, endpoint.getStackInSlot(0).getCount(), "No item may be inserted after permission changes");

        storeAllowed.set(true);
        assertTrue(aggregate.insert(new ItemStack(Blocks.STONE, 1), false).isEmpty());
        assertEquals(3, endpoint.getStackInSlot(0).getCount());
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
