package com.rtsbuilding.rtsbuilding.server.service.page;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsStorageUiPayloads;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 负责把会话中的链接引用转换为客户端可显示、可精确回写的快照字段。 */
public final class RtsPagePayloadFactory {
    private RtsPagePayloadFactory() {
    }

    public static S2CRtsStoragePagePayload buildEmpty(EntityPlayerMP player, RtsStorageSession session) {
        LinkedRefPayload refs = buildLinkedRefPayload(player, session);
        int quickSlots = RtsStorageBindings.QUICK_SLOT_COUNT;
        int guiBindings = RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
        return new S2CRtsStoragePagePayload(
                RtsLinkedStorageResolver.hasAnyStorage(player, session),
                RtsLinkedStorageResolver.buildAnyStorageSummary(player, session),
                refs.positions(), refs.dimensions(), refs.names(), refs.modes(), refs.priorities(),
                refs.iconItemIds(), refs.worldAvailable(), 0, 1, 0, true,
                session.browser.search, session.browser.category, (byte) session.browser.sort.ordinal(),
                session.browser.ascending, session.sessionFlags.autoStoreMinedDrops,
                session.sessionFlags.useBdNetwork,
                Collections.singletonList(RtsPageSharedHelpers.CATEGORY_ALL),
                Collections.<ItemStack>emptyList(), Collections.<Long>emptyList(),
                Collections.<String>emptyList(), Collections.<Long>emptyList(),
                Collections.<String>emptyList(), Collections.<Long>emptyList(),
                Collections.<Long>emptyList(), Collections.<String>emptyList(),
                Collections.<Long>emptyList(), Collections.<Long>emptyList(),
                Collections.<Byte>emptyList(), RtsStorageUiPayloads.buildQuickSlotPayload(session, quickSlots),
                RtsStorageUiPayloads.buildQuickSlotPreviewPayload(session, quickSlots),
                RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, guiBindings),
                RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, guiBindings),
                session.funnel.funnelEnabled, Collections.<String>emptyList(), Collections.<Long>emptyList());
    }

    public static LinkedRefPayload buildLinkedRefPayload(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null || session.linkedStorageInfo.isEmpty()) {
            return emptyRefs();
        }
        boolean crossDimensionAllowed = RtsLinkedStorageResolver.isCrossDimensionStorageAllowed(player);
        List<Long> positions = new ArrayList<Long>();
        List<Integer> dimensions = new ArrayList<Integer>();
        List<String> names = new ArrayList<String>();
        List<String> icons = new ArrayList<String>();
        List<Byte> modes = new ArrayList<Byte>();
        List<Integer> priorities = new ArrayList<Integer>();
        List<Boolean> available = new ArrayList<Boolean>();

        for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
            boolean backpack = ref != null && session.linkedStorageInfo.hasBackpackUuid(ref);
            if (ref == null || ref.pos() == null
                    || (!backpack && ref.dimension() != player.dimension && !crossDimensionAllowed)) {
                continue;
            }
            WorldServer targetLevel = player.getServer() == null
                    ? null : player.getServer().getWorld(ref.dimension());
            boolean visible = RtsLinkedStorageResolver.isLinkedRefWorldVisible(player, session, ref);
            positions.add(ref.pos().toLong());
            dimensions.add(ref.dimension());
            names.add(resolveName(targetLevel, session, ref, visible));
            modes.add(session.linkedStorageInfo.getMode(ref));
            priorities.add(RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedStorageInfo.getPriority(ref)));
            icons.add(resolveIcon(targetLevel, session, ref, visible));
            available.add(visible);
        }
        return new LinkedRefPayload(positions, dimensions, names, modes, priorities, icons, available);
    }

    private static LinkedRefPayload emptyRefs() {
        return new LinkedRefPayload(Collections.<Long>emptyList(), Collections.<Integer>emptyList(),
                Collections.<String>emptyList(), Collections.<Byte>emptyList(),
                Collections.<Integer>emptyList(), Collections.<String>emptyList(),
                Collections.<Boolean>emptyList());
    }

    private static String resolveName(WorldServer level, RtsStorageSession session,
            LinkedStorageRef ref, boolean visible) {
        if (visible && level != null && level.isBlockLoaded(ref.pos())) {
            return RtsLinkedStorageResolver.resolveDisplayName(level, ref.pos());
        }
        String cached = session.linkedStorageInfo.getName(ref);
        return cached == null || cached.trim().isEmpty() ? "Linked Storage" : cached;
    }

    private static String resolveIcon(WorldServer level, RtsStorageSession session,
            LinkedStorageRef ref, boolean visible) {
        if (!visible || level == null || !level.isBlockLoaded(ref.pos())) {
            String id = session.linkedStorageInfo.getBackpackItemId(ref);
            return id == null ? "" : id;
        }
        Item item = Item.getItemFromBlock(level.getBlockState(ref.pos()).getBlock());
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? "" : id.toString();
    }

    /** 只用于页面显示汇总；真实漏斗栈始终保留在会话中。 */
    public static Map<String, Long> summarizeFunnelBuffer(RtsStorageSession session) {
        Map<String, Long> counts = new TreeMap<String, Long>();
        if (session == null) return new LinkedHashMap<String, Long>();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null) continue;
            Long old = counts.get(id.toString());
            counts.put(id.toString(), RtsPageCore.saturatedAdd(old == null ? 0L : old.longValue(), stack.getCount()));
        }
        return new LinkedHashMap<String, Long>(counts);
    }
}
