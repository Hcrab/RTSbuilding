package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
 * 储存浏览器页面数据包工厂，负责组装发送给客户端的网络数据包。
 *
 * <p>从 {@link RtsPageCore} 中提取，专注于数据包装配关注点。
 * 将原始排序/过滤后的数据转换为完整的 {@link S2CRtsStoragePagePayload} 实例。
 *
 * <p><b>核心方法：</b>
 * <ul>
 *   <li>{@link #buildEmpty} — 构建表示空白储存的空页面数据包</li>
 *   <li>{@link #buildLinkedRefPayload} — 构建链接存储引用的结构化数据包
 *   （位置、名称、模式、优先级、图标、世界可用性）</li>
 *   <li>{@link #summarizeFunnelBuffer} — 将漏斗缓冲区内容汇总为物品 ID→数量的有序映射</li>
 * </ul>
 */
public final class RtsPagePayloadFactory {

    private RtsPagePayloadFactory() {
    }

    // ---- Empty payload -------------------------------------------------------

    /**
     * 构建表示空白储存（无物品或流体）的页面数据包。
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
                true,
                session.browser.search, session.browser.category,
                (byte) session.browser.sort.ordinal(), session.browser.ascending,
                session.sessionFlags.autoStoreMinedDrops, session.sessionFlags.useBdNetwork,
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
     * 构建描述每个链接储存引用的结构化数据包，
     * 包括位置、显示名称、模式、优先级、图标以及目标方块是否已加载且在世界上可见。
     */
    public static LinkedRefPayload buildLinkedRefPayload(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.linkedStorageInfo.isEmpty()) {
            return new LinkedRefPayload(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        ResourceKey<Level> currentDimension = player.level().dimension();
        ServerLevel level = player.level();
        List<LinkedStorageRef> storageRefs = session.linkedStorageInfo.getAll();
        List<Long> positions = new ArrayList<>(storageRefs.size());
        List<String> names = new ArrayList<>(storageRefs.size());
        List<Byte> modes = new ArrayList<>(storageRefs.size());
        List<Integer> priorities = new ArrayList<>(storageRefs.size());
        List<String> iconItemIds = new ArrayList<>(storageRefs.size());
        List<Boolean> worldAvailable = new ArrayList<>(storageRefs.size());
        for (LinkedStorageRef ref : storageRefs) {
            boolean backpackLink = ref != null && session.linkedStorageInfo.hasBackpackUuid(ref);
            if (ref == null || ref.pos() == null || (!backpackLink && !currentDimension.equals(ref.dimension()))) {
                continue;
            }
            BlockPos pos = ref.pos();
            boolean visible = RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref);
            positions.add(pos.asLong());
            names.add(resolveLinkedRefName(level, session, ref, visible));
            modes.add(session.linkedStorageInfo.getMode(ref));
            priorities.add(RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedStorageInfo.getPriority(ref)));
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
        String cached = session == null || ref == null ? "" : session.linkedStorageInfo.getName(ref);
        return cached == null || cached.isBlank() ? "Linked Storage" : cached;
    }

    private static String resolveLinkedRefIconItemId(ServerLevel level, RtsStorageSession session, LinkedStorageRef ref,
            boolean worldVisible) {
        if (!worldVisible) {
            String backpackItemId = session == null || ref == null ? "" : session.linkedStorageInfo.getBackpackItemId(ref);
            return backpackItemId == null ? "" : backpackItemId;
        }
        BlockPos pos = ref.pos();
        Item item = level.getBlockState(pos).getBlock().asItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }

    // ---- Funnel buffer summary -----------------------------------------------

    /**
     * 将漏斗缓冲区内容汇总为以物品 ID 为键的有序映射。
     */
    public static Map<String, Long> summarizeFunnelBuffer(RtsStorageSession session) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
