package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.FunnelBufferEntry;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.FluidContainerCompat;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将储存页网络载荷解码为不可变客户端快照。
 *
 * <p>它只负责验证注册表 ID、复制安全预览和对齐并行数组；不修改会话状态、
 * 不发包，也不决定刷新时机。这样门面消费一次完整快照，旧的逐字段解码路径可以删除。</p>
 */
final class StoragePagePayloadDecoder {
    private StoragePagePayloadDecoder() {}

    static DecodedPage decode(S2CRtsStoragePagePayload payload, String fallbackLinkedName) {
        List<BlockPos> positions = new ArrayList<>();
        List<LinkedStorageEntry> linked = new ArrayList<>();
        for (int i = 0; i < payload.linkedPositions().size(); i++) {
            Long packed = payload.linkedPositions().get(i);
            if (packed == null) continue;
            BlockPos pos = BlockPos.fromLong(packed.longValue());
            positions.add(pos);
            linked.add(decodeLinked(payload, i, pos, fallbackLinkedName));
        }

        List<StorageEntry> items = new ArrayList<>();
        int itemSize = Math.min(payload.itemStacks().size(), payload.counts().size());
        for (int i = 0; i < itemSize; i++) {
            ItemStack stack = payload.itemStacks().get(i);
            if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) continue;
            ItemStack preview = stack.copy();
            preview.stackSize = 1;
            ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS
                    .getKey(preview.getItem());
            if (id != null) items.add(new StorageEntry(preview, id.toString(), payload.counts().get(i),
                    id.getResourceDomain(), id.getResourcePath()));
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        if (payload.totalCountsSnapshot()) {
            int totalSize = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
            for (int i = 0; i < totalSize; i++) {
                String itemId = payload.totalItemIds().get(i);
                ResourceLocation id = parseId(itemId);
                if (id != null && RtsRegistries.ITEMS.containsKey(id)) {
                    totals.put(itemId, Math.max(0L, payload.totalItemCounts().get(i)));
                }
            }
        }

        List<FluidEntry> fluids = new ArrayList<>();
        int fluidSize = Math.min(payload.fluidIds().size(), Math.min(payload.fluidAmounts().size(), payload.fluidCapacities().size()));
        for (int i = 0; i < fluidSize; i++) {
            String fluidId = payload.fluidIds().get(i);
            ResourceLocation id = parseId(fluidId);
            Fluid fluid = id == null ? null : FluidRegistry.getFluid(fluidId);
            if (fluid == null) continue;
            FluidStack stack = new FluidStack(fluid, net.minecraftforge.fluids.FluidContainerRegistry.BUCKET_VOLUME);
            fluids.add(new FluidEntry(fluidId, fluid.getLocalizedName(stack),
                    payload.fluidAmounts().get(i), payload.fluidCapacities().get(i),
                    id.getResourceDomain(), id.getResourcePath(),
                    FluidContainerCompat.getFilledBucket(stack)));
        }

        List<RecentEntry> recent = new ArrayList<>();
        int recentSize = Math.min(payload.recentIds().size(), Math.min(payload.recentAmounts().size(),
                Math.min(payload.recentCapacities().size(), payload.recentKinds().size())));
        for (int i = 0; i < recentSize; i++) {
            RecentEntry entry = decodeRecent(payload.recentIds().get(i), payload.recentAmounts().get(i),
                    payload.recentCapacities().get(i), payload.recentKinds().get(i));
            if (entry != null) recent.add(entry);
        }

        List<FunnelBufferEntry> funnel = new ArrayList<>();
        int funnelSize = Math.min(payload.funnelBufferItemIds().size(), payload.funnelBufferCounts().size());
        for (int i = 0; i < funnelSize; i++) {
            String itemId = payload.funnelBufferItemIds().get(i);
            ResourceLocation id = parseId(itemId);
            long count = Math.max(0L, payload.funnelBufferCounts().get(i));
            Item item = id == null ? null : RtsRegistries.ITEMS.getValue(id);
            if (item != null && count > 0L) {
                funnel.add(new FunnelBufferEntry(new ItemStack(item), itemId, count));
            }
        }
        return new DecodedPage(immutable(positions), immutable(linked), immutable(items),
                Collections.unmodifiableMap(new LinkedHashMap<String, Long>(totals)), immutable(fluids),
                immutable(recent), immutable(funnel));
    }

    private static LinkedStorageEntry decodeLinked(S2CRtsStoragePagePayload payload, int index, BlockPos pos, String fallbackName) {
        String label = index < payload.linkedNames().size() ? payload.linkedNames().get(index) : fallbackName;
        if (label == null || label.trim().isEmpty()) label = "Linked Storage";
        byte mode = index < payload.linkedModes().size() ? payload.linkedModes().get(index) : C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
        int priority = index < payload.linkedPriorities().size() ? payload.linkedPriorities().get(index) : 0;
        boolean available = index < payload.linkedWorldAvailable().size() && Boolean.TRUE.equals(payload.linkedWorldAvailable().get(index));
        ItemStack preview = null;
        String iconId = index < payload.linkedIconItemIds().size() ? payload.linkedIconItemIds().get(index) : "";
        ResourceLocation iconKey = parseId(iconId);
        Item icon = iconKey == null ? null : RtsRegistries.ITEMS.getValue(iconKey);
        if (icon != null) preview = new ItemStack(icon);
        return new LinkedStorageEntry(pos, label, mode, priority, preview, available);
    }

    private static RecentEntry decodeRecent(String idText, long amount, long capacity, byte kind) {
        ResourceLocation id = parseId(idText);
        if (id == null) return null;
        boolean fluidKind = kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
        if (fluidKind) {
            Fluid fluid = FluidRegistry.getFluid(idText);
            if (fluid == null) return null;
            FluidStack stack = new FluidStack(fluid, net.minecraftforge.fluids.FluidContainerRegistry.BUCKET_VOLUME);
            return new RecentEntry(true, idText, fluid.getLocalizedName(stack),
                    Math.max(0L, amount), Math.max(0L, capacity), kind,
                    FluidContainerCompat.getFilledBucket(stack));
        }
        Item item = RtsRegistries.ITEMS.getValue(id);
        if (item == null) return null;
        ItemStack preview = new ItemStack(item);
        return new RecentEntry(false, idText, preview.getDisplayName(), Math.max(0L, amount), 0L, kind, preview);
    }

    private static ResourceLocation parseId(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : new ResourceLocation(text);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    /**
     * 一次储存页解码的不可变结果，保留 record 风格访问器以减少调用方改动。
     */
    static final class DecodedPage {
        private final List<BlockPos> positions;
        private final List<LinkedStorageEntry> linked;
        private final List<StorageEntry> items;
        private final Map<String, Long> totals;
        private final List<FluidEntry> fluids;
        private final List<RecentEntry> recent;
        private final List<FunnelBufferEntry> funnel;

        DecodedPage(List<BlockPos> positions, List<LinkedStorageEntry> linked, List<StorageEntry> items,
                    Map<String, Long> totals, List<FluidEntry> fluids, List<RecentEntry> recent,
                    List<FunnelBufferEntry> funnel) {
            this.positions = positions;
            this.linked = linked;
            this.items = items;
            this.totals = totals;
            this.fluids = fluids;
            this.recent = recent;
            this.funnel = funnel;
        }

        List<BlockPos> positions() { return this.positions; }
        List<LinkedStorageEntry> linked() { return this.linked; }
        List<StorageEntry> items() { return this.items; }
        Map<String, Long> totals() { return this.totals; }
        List<FluidEntry> fluids() { return this.fluids; }
        List<RecentEntry> recent() { return this.recent; }
        List<FunnelBufferEntry> funnel() { return this.funnel; }
    }
}
