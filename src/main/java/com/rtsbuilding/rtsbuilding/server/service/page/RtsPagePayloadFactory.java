package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for constructing storage page payloads.
 *
 * <p>Extracted from {@link RtsPageCore} to separate payload-assembly concerns
 * from page-building logic. This class is responsible for building
 * {@link S2CRtsStoragePagePayload} instances from raw sorted/filtered data,
 * including linked-ref metadata, funnel summaries, and UI slot payloads.
 */
public final class RtsPagePayloadFactory {

    private RtsPagePayloadFactory() {
    }

    // ---- Empty payload -------------------------------------------------------

    /**
     * Builds a page payload representing an empty storage (no items or fluids).
     */
    public static S2CRtsStoragePagePayload buildEmpty(ServerPlayer player, RtsStorageSession session) {
        LinkedRefPayload linkedRefs = buildLinkedRefPayload(player, session);
        int qSlotCount = RtsStorageBindings.QUICK_SLOT_COUNT;
        int gbSlotCount = RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
        return new S2CRtsStoragePagePayload(
                RtsLinkedStorageResolver.hasAnyStorage(player, session),
                RtsLinkedStorageResolver.buildAnyStorageSummary(player, session),
                linkedRefs.positions(), linkedRefs.names(), linkedRefs.modes(),
                linkedRefs.priorities(), linkedRefs.iconItemIds(), linkedRefs.worldAvailable(),
                0, 1, 0,
                session.browser.search, session.browser.category,
                (byte) session.browser.sort.ordinal(), session.browser.ascending,
                session.autoStoreMinedDrops, session.useBdNetwork,
                List.of(RtsPageSharedHelpers.CATEGORY_ALL),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.<Byte>of(),
                RtsStorageUiPayloads.buildQuickSlotPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildQuickSlotPreviewPayload(session, qSlotCount),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, gbSlotCount),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, gbSlotCount),
                session.funnel.funnelEnabled, List.of(), List.of());
    }

    // ---- Linked ref payload ---------------------------------------------------

    /**
     * Builds a structured payload describing each linked storage reference,
     * including its position, display name, mode, priority, icon, and whether
     * the target block is currently loaded and visible in-world.
     */
    public static LinkedRefPayload buildLinkedRefPayload(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.linkedStorages.isEmpty()) {
            return new LinkedRefPayload(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        ServerLevel level = player.serverLevel();
        List<Long> positions = new ArrayList<>(session.linkedStorages.size());
        List<String> names = new ArrayList<>(session.linkedStorages.size());
        List<Byte> modes = new ArrayList<>(session.linkedStorages.size());
        List<Integer> priorities = new ArrayList<>(session.linkedStorages.size());
        List<String> iconItemIds = new ArrayList<>(session.linkedStorages.size());
        List<Boolean> worldAvailable = new ArrayList<>(session.linkedStorages.size());
        for (LinkedStorageRef ref : session.linkedStorages) {
            boolean backpackLink = ref != null && session.linkedBackpackUuids.containsKey(ref);
            if (ref == null || ref.pos() == null || (!backpackLink && !currentDimension.equals(ref.dimension()))) {
                continue;
            }
            BlockPos pos = ref.pos();
            boolean visible = RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref);
            positions.add(pos.asLong());
            names.add(resolveLinkedRefName(level, session, ref, visible));
            modes.add(session.linkedModes.getOrDefault(ref, C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL));
            priorities.add(RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedPriorities.getOrDefault(ref, 0)));
            iconItemIds.add(resolveLinkedRefIconItemId(level, session, ref, visible));
            worldAvailable.add(visible);
        }
        return new LinkedRefPayload(positions, names, modes, priorities, iconItemIds, worldAvailable);
    }

    private static String resolveLinkedRefName(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref,
            boolean worldVisible) {
        if (worldVisible && level != null && ref != null && ref.pos() != null && level.hasChunkAt(ref.pos())) {
            return RtsLinkedStorageResolver.resolveDisplayName(level, ref.pos());
        }
        String cached = session == null || ref == null ? "" : session.linkedNames.get(ref);
        return cached == null || cached.isBlank() ? "Linked Storage" : cached;
    }

    private static String resolveLinkedRefIconItemId(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref,
            boolean worldVisible) {
        if (!worldVisible) {
            String backpackItemId = session == null || ref == null ? "" : session.linkedBackpackItemIds.get(ref);
            return backpackItemId == null ? "" : backpackItemId;
        }
        BlockPos pos = ref.pos();
        Item item = level.getBlockState(pos).getBlock().asItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }

    // ---- Funnel buffer summary -----------------------------------------------

    /**
     * Summarizes the funnel buffer contents into an ordered map keyed by item id.
     */
    public static Map<String, Long> summarizeFunnelBuffer(RtsStorageSession session) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            counts.merge(id.toString(), (long) stack.getCount(), RtsPageCore::saturatedAdd);
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (var entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }
}
