package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.FunnelBufferEntry;
import com.rtsbuilding.rtsbuilding.client.record.LinkedStorageEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
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
            BlockPos pos = BlockPos.of(packed.longValue());
            positions.add(pos);
            linked.add(decodeLinked(payload, i, pos, fallbackLinkedName));
        }

        List<StorageEntry> items = new ArrayList<>();
        int itemSize = Math.min(payload.itemStacks().size(), payload.counts().size());
        for (int i = 0; i < itemSize; i++) {
            ItemStack stack = payload.itemStacks().get(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack preview = stack.copyWithCount(1);
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(preview.getItem());
            if (id != null) items.add(new StorageEntry(preview, id.toString(), payload.counts().get(i), id.getNamespace(), id.getPath()));
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        if (payload.totalCountsSnapshot()) {
            int totalSize = Math.min(payload.totalItemIds().size(), payload.totalItemCounts().size());
            for (int i = 0; i < totalSize; i++) {
                String itemId = payload.totalItemIds().get(i);
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    totals.put(itemId, Math.max(0L, payload.totalItemCounts().get(i)));
                }
            }
        }

        List<FluidEntry> fluids = new ArrayList<>();
        int fluidSize = Math.min(payload.fluidIds().size(), Math.min(payload.fluidAmounts().size(), payload.fluidCapacities().size()));
        for (int i = 0; i < fluidSize; i++) {
            String fluidId = payload.fluidIds().get(i);
            ResourceLocation id = ResourceLocation.tryParse(fluidId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) continue;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidVariant variant = FluidVariant.of(fluid);
            fluids.add(new FluidEntry(fluidId, FluidVariantAttributes.getName(variant).getString(),
                    payload.fluidAmounts().get(i), payload.fluidCapacities().get(i), id.getNamespace(), id.getPath(),
                    new ItemStack(fluid.getBucket())));
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
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            long count = Math.max(0L, payload.funnelBufferCounts().get(i));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id) && count > 0L) {
                funnel.add(new FunnelBufferEntry(new ItemStack(BuiltInRegistries.ITEM.get(id)), itemId, count));
            }
        }
        return new DecodedPage(List.copyOf(positions), List.copyOf(linked), List.copyOf(items), Map.copyOf(totals),
                List.copyOf(fluids), List.copyOf(recent), List.copyOf(funnel));
    }

    private static LinkedStorageEntry decodeLinked(S2CRtsStoragePagePayload payload, int index, BlockPos pos, String fallbackName) {
        String dimensionId = index < payload.linkedDimensions().size()
                ? payload.linkedDimensions().get(index) : "";
        String label = index < payload.linkedNames().size() ? payload.linkedNames().get(index) : fallbackName;
        if (label == null || label.isBlank()) label = "Linked Storage";
        byte mode = index < payload.linkedModes().size() ? payload.linkedModes().get(index) : C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
        int priority = index < payload.linkedPriorities().size() ? payload.linkedPriorities().get(index) : 0;
        boolean available = index < payload.linkedWorldAvailable().size() && Boolean.TRUE.equals(payload.linkedWorldAvailable().get(index));
        ItemStack preview = ItemStack.EMPTY;
        String iconId = index < payload.linkedIconItemIds().size() ? payload.linkedIconItemIds().get(index) : "";
        ResourceLocation iconKey = ResourceLocation.tryParse(iconId);
        if (iconKey != null && BuiltInRegistries.ITEM.containsKey(iconKey)) preview = new ItemStack(BuiltInRegistries.ITEM.get(iconKey));
        return new LinkedStorageEntry(pos, dimensionId, label, mode, priority, preview, available);
    }

    private static RecentEntry decodeRecent(String idText, long amount, long capacity, byte kind) {
        ResourceLocation id = idText == null ? null : ResourceLocation.tryParse(idText);
        if (id == null) return null;
        boolean fluidKind = kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
        if (fluidKind) {
            if (!BuiltInRegistries.FLUID.containsKey(id)) return null;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            FluidVariant variant = FluidVariant.of(fluid);
            return new RecentEntry(true, idText, FluidVariantAttributes.getName(variant).getString(),
                    Math.max(0L, amount), Math.max(0L, capacity), kind, new ItemStack(fluid.getBucket()));
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) return null;
        ItemStack preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
        return new RecentEntry(false, idText, preview.getHoverName().getString(), Math.max(0L, amount), 0L, kind, preview);
    }

    record DecodedPage(List<BlockPos> positions, List<LinkedStorageEntry> linked, List<StorageEntry> items,
                       Map<String, Long> totals, List<FluidEntry> fluids, List<RecentEntry> recent,
                       List<FunnelBufferEntry> funnel) {}
}
