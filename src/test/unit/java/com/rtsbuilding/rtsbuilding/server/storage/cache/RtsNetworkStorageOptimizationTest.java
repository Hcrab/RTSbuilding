package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.server.storage.view.LinkedItemHandlerView;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RtsNetworkStorageOptimizationTest {
    private static Item STONE;
    private static Item COBBLESTONE;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
        STONE = Item.getItemFromBlock(Blocks.STONE);
        COBBLESTONE = Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    @Test
    void ae2BdAndRsStyleHandlersStoreThroughAnySlotPath() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler handler = FakeNetworkHandler.seeded(kind, Map.of(Items.DIAMOND, 512L));
            MountedNetwork mounted = mount(handler);

            int readsAfterInitialRefresh = handler.stackReads;
            ItemStack remainder = mounted.storage.insert(new ItemStack(STONE, 64), false);

            assertTrue(remainder.isEmpty(), kind + " should accept the whole inserted stack");
            assertEquals(1, handler.insertAnywhereCalls, kind + " should use the bulk insert path");
            assertEquals(0, handler.perSlotInsertCalls, kind + " should not scan slots while inserting");
            assertEquals(readsAfterInitialRefresh, handler.stackReads,
                    kind + " insert should not read storage slots after the cache is warm");
            assertTrue(mounted.storage.drainPendingChanges().contains(itemId(STONE)),
                    kind + " insert should mark the stored item dirty");

            mounted.storage.tickUpdate();
            assertEquals(64L, mounted.storage.getTotalCount(STONE),
                    kind + " cache should see the stored stack after refresh");
        }
    }

    @Test
    void ae2BdAndRsStyleHandlersExtractThroughAnySlotPath() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler handler = FakeNetworkHandler.seeded(kind, Map.of(Items.DIAMOND, 512L));
            MountedNetwork mounted = mount(handler);

            int readsAfterInitialRefresh = handler.stackReads;
            ItemStack extracted = mounted.storage.extract(Items.DIAMOND, 37);

            assertEquals(Items.DIAMOND, extracted.getItem(), kind + " should extract the requested item");
            assertEquals(37, extracted.getCount(), kind + " should extract the requested amount");
            assertEquals(1, handler.extractAnywhereCalls, kind + " should use the bulk extract path");
            assertEquals(0, handler.perSlotExtractCalls, kind + " should not scan slots while extracting");
            assertEquals(readsAfterInitialRefresh, handler.stackReads,
                    kind + " extract should not read storage slots after the cache is warm");

            mounted.storage.tickUpdate();
            assertEquals(475L, mounted.storage.getTotalCount(Items.DIAMOND),
                    kind + " cache should reflect the extracted count after refresh");
        }
    }

    @Test
    void bidirectionalPolicyViewMustPreserveNetworkBulkPaths() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler endpoint = FakeNetworkHandler.seeded(kind, Map.of(Items.DIAMOND, 64L));
            MountedNetwork mounted = mount(new LinkedItemHandlerView(endpoint, true));

            assertTrue(mounted.storage.insert(new ItemStack(STONE, 8), false).isEmpty());
            ItemStack extracted = mounted.storage.extract(Items.DIAMOND, 7);

            assertEquals(8L, endpoint.stored.get(STONE));
            assertEquals(7, extracted.getCount());
            assertEquals(1, endpoint.insertAnywhereCalls,
                    kind + " 的权限视图不能让网络写入退化为逐槽扫描");
            assertEquals(1, endpoint.extractAnywhereCalls,
                    kind + " 的权限视图不能让网络提取退化为逐槽扫描");
            assertEquals(0, endpoint.perSlotInsertCalls);
            assertEquals(0, endpoint.perSlotExtractCalls);
        }
    }

    @Test
    void extractOnlyPolicyViewMustRejectBulkInsertButKeepBulkExtract() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler endpoint = FakeNetworkHandler.seeded(kind, Map.of(Items.DIAMOND, 64L));
            MountedNetwork mounted = mount(new LinkedItemHandlerView(endpoint, false));

            ItemStack rejected = mounted.storage.insert(new ItemStack(STONE, 8), false);
            ItemStack extracted = mounted.storage.extract(Items.DIAMOND, 7);

            assertEquals(8, rejected.getCount());
            assertEquals(0L, endpoint.stored.getOrDefault(STONE, 0L));
            assertEquals(7, extracted.getCount(), "Extract-only 仍应允许走网络批量提取");
            assertEquals(0, endpoint.insertAnywhereCalls,
                    kind + " 的原始网络写入 API 不得被调用");
            assertEquals(1, endpoint.extractAnywhereCalls);
            assertEquals(0, endpoint.perSlotInsertCalls);
            assertEquals(0, endpoint.perSlotExtractCalls);
        }
    }

    @Test
    void searchAndPaginationInputsReadAggregateCacheWithoutTouchingNetworkSlots() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler handler = FakeNetworkHandler.seeded(kind, Map.of(
                    Items.DIAMOND, 512L,
                    Items.EMERALD, 128L,
                    Items.REDSTONE, 11L));
            MountedNetwork mounted = mount(handler);
            int readsAfterInitialRefresh = handler.stackReads;

            Map<String, Long> firstPageCounts = new HashMap<>();
            mounted.storage.getAvailableItems(firstPageCounts);
            Map<String, Long> secondPageCounts = new HashMap<>();
            mounted.storage.getAvailableItems(secondPageCounts);
            ItemStack diamondPrototype = mounted.storage.getPrototype(itemId(Items.DIAMOND));

            assertEquals(firstPageCounts, secondPageCounts,
                    kind + " repeated page/search reads should be stable");
            assertEquals(512L, firstPageCounts.get(itemId(Items.DIAMOND)),
                    kind + " aggregate cache should expose diamond counts");
            assertEquals(128L, firstPageCounts.get(itemId(Items.EMERALD)),
                    kind + " aggregate cache should expose emerald counts");
            assertEquals(Items.DIAMOND, diamondPrototype.getItem(),
                    kind + " aggregate cache should expose item prototypes");
            assertEquals(readsAfterInitialRefresh, handler.stackReads,
                    kind + " search/page reads should not touch network slots once cached");
        }
    }

    @Test
    void batchMiningDropsAutoStoreThroughBulkInsertPath() {
        List<ItemStack> minedDrops = List.of(
                new ItemStack(COBBLESTONE, 64),
                new ItemStack(COBBLESTONE, 64),
                new ItemStack(COBBLESTONE, 17),
                new ItemStack(Items.DIAMOND, 3));

        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler handler = FakeNetworkHandler.seeded(kind, Map.of(STONE, 2048L));
            MountedNetwork mounted = mount(handler);
            int readsAfterInitialRefresh = handler.stackReads;

            for (ItemStack drop : minedDrops) {
                ItemStack remainder = mounted.storage.insert(drop, false);
                assertTrue(remainder.isEmpty(), kind + " should store every simulated mining drop");
            }

            assertEquals(minedDrops.size(), handler.insertAnywhereCalls,
                    kind + " should route each mined drop through the bulk insert path");
            assertEquals(0, handler.perSlotInsertCalls,
                    kind + " batch mining auto-store should not fall back to slot insertion");
            assertEquals(readsAfterInitialRefresh, handler.stackReads,
                    kind + " batch mining auto-store should not read slots during insertion");
            assertTrue(mounted.storage.drainPendingChanges().containsAll(Set.of(
                            itemId(COBBLESTONE),
                            itemId(Items.DIAMOND))),
                    kind + " batch mining auto-store should mark all stored drop types dirty");

            mounted.storage.tickUpdate();
            assertEquals(145L, mounted.storage.getTotalCount(COBBLESTONE),
                    kind + " cache should include all cobblestone drops after refresh");
            assertEquals(3L, mounted.storage.getTotalCount(Items.DIAMOND),
                    kind + " cache should include all diamond drops after refresh");
        }
    }

    @Test
    void refreshableSnapshotsAreCalledOncePerCacheRefresh() {
        for (NetworkKind kind : NetworkKind.values()) {
            FakeNetworkHandler handler = FakeNetworkHandler.seeded(kind, Map.of(Items.DIAMOND, 512L));
            RtsHandlerCache cache = new RtsHandlerCache();

            cache.update(handler);
            cache.update(handler);

            assertEquals(2, handler.refreshCalls,
                    kind + " should refresh its internal snapshot exactly once per cache update");
        }
    }

    private static MountedNetwork mount(FakeNetworkHandler handler) {
        return mount((IItemHandler) handler);
    }

    private static MountedNetwork mount(IItemHandler handler) {
        RtsHandlerCache cache = new RtsHandlerCache();
        cache.update(handler);
        RtsAggregateStorage storage = new RtsAggregateStorage();
        storage.mount(100, handler, cache);
        return new MountedNetwork(storage, cache);
    }

    private static String itemId(Item item) {
        return Item.REGISTRY.getNameForObject(item).toString();
    }

    private record MountedNetwork(RtsAggregateStorage storage, RtsHandlerCache cache) {
    }

    private enum NetworkKind {
        AE2,
        BD,
        RS
    }

    private static final class FakeNetworkHandler implements IItemHandler,
            ReportedCountItemHandler,
            AnySlotInsertItemHandler,
            RefreshableSnapshotHandler {
        private final NetworkKind kind;
        private final LinkedHashMap<Item, Long> stored = new LinkedHashMap<>();
        private List<Item> snapshot = List.of();
        private int stackReads;
        private int insertAnywhereCalls;
        private int extractAnywhereCalls;
        private int perSlotInsertCalls;
        private int perSlotExtractCalls;
        private int refreshCalls;

        private FakeNetworkHandler(NetworkKind kind) {
            this.kind = kind;
        }

        static FakeNetworkHandler seeded(NetworkKind kind, Map<Item, Long> stacks) {
            FakeNetworkHandler handler = new FakeNetworkHandler(kind);
            handler.stored.putAll(stacks);
            handler.ensureFreshSnapshot();
            handler.refreshCalls = 0;
            return handler;
        }

        @Override
        public void ensureFreshSnapshot() {
            this.refreshCalls++;
            this.snapshot = this.stored.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public int getSlots() {
            return this.snapshot.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            this.stackReads++;
            if (slot < 0 || slot >= this.snapshot.size()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(this.snapshot.get(slot), 1);
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.snapshot.size()) {
                return 0L;
            }
            return Math.max(0L, this.stored.getOrDefault(this.snapshot.get(slot), 0L));
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            this.insertAnywhereCalls++;
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                this.stored.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
                ensureFreshSnapshot();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            this.extractAnywhereCalls++;
            if (targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            long available = this.stored.getOrDefault(targetItem, 0L);
            if (available <= 0L) {
                return ItemStack.EMPTY;
            }
            int extracted = (int) Math.min(Integer.MAX_VALUE, Math.min(available, amount));
            if (!simulate) {
                long remaining = available - extracted;
                if (remaining <= 0L) {
                    this.stored.remove(targetItem);
                } else {
                    this.stored.put(targetItem, remaining);
                }
                ensureFreshSnapshot();
            }
            return new ItemStack(targetItem, extracted);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            this.perSlotInsertCalls++;
            return failPerSlot("insertItem");
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            this.perSlotExtractCalls++;
            return failPerSlot("extractItem");
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        private <T> T failPerSlot(String method) {
            fail(this.kind + " fake network should not call per-slot " + method);
            throw new AssertionError("unreachable");
        }
    }
}
