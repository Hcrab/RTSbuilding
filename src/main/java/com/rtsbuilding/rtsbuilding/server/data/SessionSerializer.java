package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsBrowserState;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.session.SessionFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.UUID;

/**
 * 存储会话的细粒度序列化工具——替代 {@code RtsStorageSessionCodec} 和 {@code RtsLinkedStorageCodec}。
 *
 * <p>每个方法负责 session 中一个独立子模块的序列化/反序列化，
 * 与 {@link SessionComponents} 中的细粒度组件一一对应。
 * 不持有任何状态，纯工具方法。
 */
public final class SessionSerializer {

    private SessionSerializer() {
    }

    // ======================================================================
    //  统一入口：从合并 NBT 加载全部会话字段
    // ======================================================================

    /**
     * 从合并的 NBT 根节点加载会话的全部字段。
     * 先加载细粒度子模块，再回退字段级读取。
     */
    public static void loadAll(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        loadBrowserFields(session, root);
        loadFlagsFields(session, root);
        loadLinkedStorage(player, session, root);
        loadUiMemory(player, session, root);
        loadPlacement(player, session, root);
        loadDestroy(player, session, root);
        loadDropBuffer(player, session, root);
        loadFunnel(player, session, root);
    }

    /** 保存完整 ItemStack 组件，确保正常存档/重启不会丢失已接住的掉落。 */
    public static CompoundTag serializeDropBuffer(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        ListTag stacks = new ListTag();
        int count = 0;
        for (ItemStack stack : session.miningDropBuffer.stacks) {
            if (stack == null || stack.isEmpty()
                    || stacks.size() >= com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
                continue;
            }
            int accepted = Math.min(stack.getCount(),
                    com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_BUFFERED_ITEMS - count);
            if (accepted <= 0) break;
            stacks.add(com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.save(
                    stack.copyWithCount(accepted), player.registryAccess()));
            count += accepted;
        }
        root.put("drop_buffer_stacks", stacks);
        root.putLong("drop_buffer_since", session.miningDropBuffer.firstQueuedGameTime);
        root.putBoolean("drop_buffer_blocked_timer_v2", true);
        return root;
    }

    private static void loadDropBuffer(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        var buffer = session.miningDropBuffer;
        buffer.stacks.clear();
        buffer.bufferedItems = 0;
        ListTag stacks = root.getListOrEmpty("drop_buffer_stacks");
        for (int i = 0; i < stacks.size()
                && buffer.stacks.size() < com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS;
                i++) {
            ItemStack stack = com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.load(
                    stacks.getCompoundOrEmpty(i), player.registryAccess());
            if (stack.isEmpty()) continue;
            int accepted = Math.min(stack.getCount(), buffer.remainingCapacity());
            if (accepted <= 0) break;
            buffer.stacks.addLast(stack.copyWithCount(accepted));
            buffer.bufferedItems += accepted;
        }
        // 旧存档的 since 表示“进入缓存的时间”，不能继续当成真实储存堵塞时间，否则登录即误回退。
        buffer.firstQueuedGameTime = buffer.stacks.isEmpty()
                || !root.getBooleanOr("drop_buffer_blocked_timer_v2", false)
                ? -1L
                : root.getLongOr("drop_buffer_since", 0L);
        buffer.fullNoticeSent = false;
    }

    public static CompoundTag serializeFunnel(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        root.putBoolean("funnel_enabled", session.funnel.funnelEnabled);
        if (session.funnel.funnelTarget != null && session.funnel.funnelTargetDimension != null) {
            root.putLong("funnel_target", session.funnel.funnelTarget.asLong());
            root.putString("funnel_target_dimension",
                    session.funnel.funnelTargetDimension.identifier().toString());
        }
        root.putInt("funnel_cooldown", Math.max(0, session.funnel.funnelTickCooldown));
        ListTag stacks = new ListTag();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack != null && !stack.isEmpty()
                    && stacks.size() < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS) {
                stacks.add(com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.save(
                        stack, player.registryAccess()));
            }
        }
        root.put("funnel_buffer", stacks);
        return root;
    }

    private static void loadFunnel(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.funnel.funnelEnabled = root.getBooleanOr("funnel_enabled", false);
        ResourceKey<Level> targetDimension = parseDimensionKey(
                root.getStringOr("funnel_target_dimension", ""));
        if (root.contains("funnel_target") && targetDimension != null) {
            session.funnel.funnelTarget = BlockPos.of(root.getLongOr("funnel_target", 0L)).immutable();
            session.funnel.funnelTargetDimension = targetDimension;
        } else {
            // 旧存档没有维度身份时不能猜测当前世界，否则切维后可能在同坐标误吸物品。
            session.funnel.funnelTarget = null;
            session.funnel.funnelTargetDimension = null;
        }
        session.funnel.funnelTickCooldown = Math.max(0, root.getIntOr("funnel_cooldown", 0));
        session.funnel.funnelBuffer.clear();
        ListTag stacks = root.getListOrEmpty("funnel_buffer");
        for (int i = 0; i < stacks.size()
                && session.funnel.funnelBuffer.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS; i++) {
            ItemStack stack = com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.load(
                    stacks.getCompoundOrEmpty(i), player.registryAccess());
            if (!stack.isEmpty()) session.funnel.funnelBuffer.add(stack);
        }
    }

    // ======================================================================
    //  浏览状态（字段级加载到 final browser 对象）
    // ======================================================================

    /** 序列化浏览状态到 NBT */
    public static CompoundTag serializeBrowser(RtsBrowserState v) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("page", Math.max(0, v.page));
        tag.putString("search", v.search);
        tag.putString("category", RtsStoragePageBuilder.normalizeCategory(v.category));
        tag.putInt("sort", (v.sort == null ? RtsStorageSort.QUANTITY : v.sort).ordinal());
        tag.putBoolean("ascending", v.ascending);
        tag.putString("craft_search", v.craftSearch);
        tag.putBoolean("craft_show_unavailable", v.craftShowUnavailable);
        tag.putInt("craft_requested_count", Math.max(1, Math.min(999, v.craftRequestedCount)));
        return tag;
    }

    /** 将会话标志序列化到 NBT */
    public static CompoundTag serializeFlags(SessionFlags v) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("auto_store", v.autoStoreMinedDrops);
        tag.putBoolean("use_bd", v.useBdNetwork);
        ListTag fluids = new ListTag();
        for (var entry : v.internalFluidMb.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            CompoundTag ft = new CompoundTag();
            ft.putString("id", entry.getKey());
            ft.putLong("amount", entry.getValue());
            fluids.add(ft);
        }
        tag.put("fluids", fluids);
        return tag;
    }

    private static void loadBrowserFields(RtsStorageSession session, CompoundTag tag) {
        session.browser.page = tag.contains("page") ? Math.max(0, tag.getIntOr("page", 0)) : 0;
        session.browser.search = tag.contains("search") ? tag.getStringOr("search", "").trim() : "";
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(tag.getStringOr("category", ""));
        session.browser.sort = parseSort(tag.getIntOr("sort", 0));
        session.browser.ascending = tag.contains("ascending") && tag.getBooleanOr("ascending", false);
        session.browser.craftSearch = tag.contains("craft_search") ? tag.getStringOr("craft_search", "").trim() : "";
        session.browser.craftShowUnavailable = tag.contains("craft_show_unavailable") && tag.getBooleanOr("craft_show_unavailable", false);
        session.browser.craftRequestedCount = tag.contains("craft_requested_count")
                ? Math.max(1, Math.min(999, tag.getIntOr("craft_requested_count", 0)))
                : RtsBrowserState.CRAFTABLE_BATCH_SIZE;
    }

    private static void loadFlagsFields(RtsStorageSession session, CompoundTag tag) {
        session.sessionFlags.autoStoreMinedDrops = !tag.contains("auto_store") || tag.getBooleanOr("auto_store", false);
        session.sessionFlags.useBdNetwork = !tag.contains("use_bd") || tag.getBooleanOr("use_bd", false);
        session.sessionFlags.internalFluidMb.clear();
        ListTag fluids = tag.getListOrEmpty("fluids");
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag ft = fluids.getCompoundOrEmpty(i);
            String id = ft.getStringOr("id", "");
            long amount = ft.getLongOr("amount", 0L);
            if (!id.isBlank() && amount > 0) {
                session.sessionFlags.internalFluidMb.put(id, amount);
            }
        }
    }

    private static RtsStorageSort parseSort(int ordinal) {
        RtsStorageSort[] values = RtsStorageSort.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RtsStorageSort.QUANTITY;
    }

    // ======================================================================
    //  链接存储
    // ======================================================================

    public static CompoundTag serializeLinkedStorage(RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        ListTag linkedEntries = new ListTag();
        long[] linkedPacked = new long[session.linkedStorageInfo.size()];
        byte[] linkedModes = new byte[session.linkedStorageInfo.size()];
        int[] linkedPriorities = new int[session.linkedStorageInfo.size()];
        for (int i = 0; i < session.linkedStorageInfo.size(); i++) {
            LinkedStorageRef ref = session.linkedStorageInfo.get(i);
            if (ref == null || ref.pos() == null || ref.dimension() == null) continue;

            byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(
                    session.linkedStorageInfo.getMode(ref));
            int priority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedStorageInfo.getPriority(ref));
            linkedPacked[i] = ref.pos().asLong();
            linkedModes[i] = linkMode;
            linkedPriorities[i] = priority;

            CompoundTag linkedTag = new CompoundTag();
            linkedTag.putLong("pos", ref.pos().asLong());
            linkedTag.putString("dimension", ref.dimension().identifier().toString());
            linkedTag.putByte("mode", linkMode);
            linkedTag.putInt("priority", priority);
            UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
            if (backpackUuid != null) com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(linkedTag, "bpUuid", backpackUuid);
            String backpackItemId = session.linkedStorageInfo.getBackpackItemId(ref);
            if (isRegisteredItemId(backpackItemId)) linkedTag.putString("bpItem", backpackItemId);
            if (session.linkedStorageInfo.isDetached(ref)) linkedTag.putBoolean("bpDetached", true);
            linkedEntries.add(linkedTag);
        }
        root.put("linked_entries", linkedEntries);
        root.putLongArray("linked_positions", linkedPacked);
        root.putByteArray("linked_modes", linkedModes);
        root.putIntArray("linked_priorities", linkedPriorities);
        if (!session.linkedStorageInfo.isEmpty()) {
            LinkedStorageRef first = session.linkedStorageInfo.get(0);
            if (first != null && first.dimension() != null) {
                root.putString("linked_dimension", first.dimension().identifier().toString());
            }
        }
        return root;
    }

    public static void loadLinkedStorage(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.linkedStorageInfo.clear();

        byte[] linkedModes = root.getByteArray("linked_modes").orElseGet(() -> new byte[0]);
        int[] linkedPriorities = root.getIntArray("linked_priorities").orElseGet(() -> new int[0]);

        ResourceKey<Level> legacyDimension = null;
        String legacyDimensionId = root.getStringOr("linked_dimension", "");
        if (!legacyDimensionId.isBlank()) legacyDimension = parseDimensionKey(legacyDimensionId);

        ListTag linkedEntries = root.getListOrEmpty("linked_entries");
        if (!linkedEntries.isEmpty()) {
            loadLinkedStorageModern(linkedEntries, session);
            return;
        }

        ServerLevel level = player.level();
        ResourceKey<Level> dimension = legacyDimension == null ? level.dimension() : legacyDimension;
        long[] linkedPackedPositions = root.getLongArray("linked_positions").orElseGet(() -> new long[0]);
        for (int i = 0; i < linkedPackedPositions.length; i++) {
            LinkedStorageRef ref = new LinkedStorageRef(dimension, BlockPos.of(linkedPackedPositions[i]).immutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = i < linkedModes.length ? linkedModes[i] : RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL;
                int priority = i < linkedPriorities.length ? linkedPriorities[i] : 0;
                session.linkedStorageInfo.add(ref,
                        RtsLinkedStorageResolver.sanitizeLinkMode(linkMode),
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
            }
        }
    }

    private static void loadLinkedStorageModern(ListTag linkedEntries, RtsStorageSession session) {
        for (int i = 0; i < linkedEntries.size(); i++) {
            CompoundTag linkedTag = linkedEntries.getCompoundOrEmpty(i);
            if (!linkedTag.contains("pos")) continue;

            ResourceKey<Level> dimension = parseDimensionKey(linkedTag.getStringOr("dimension", ""));
            if (dimension == null) continue;

            LinkedStorageRef ref = new LinkedStorageRef(dimension, BlockPos.of(linkedTag.getLongOr("pos", 0L)).immutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkedTag.getByteOr("mode", (byte) 0));
                int priority = linkedTag.contains("priority") ? linkedTag.getIntOr("priority", 0) : 0;
                UUID backpackUuid = linkedTag.contains("bpUuid") ? com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(linkedTag, "bpUuid") : null;
                String backpackItemId = isRegisteredItemId(linkedTag.getStringOr("bpItem", ""))
                        ? linkedTag.getStringOr("bpItem", "") : null;
                session.linkedStorageInfo.add(ref, linkMode,
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority),
                        backpackUuid, backpackItemId);
                if (linkedTag.getBooleanOr("bpDetached", false)) session.linkedStorageInfo.markDetached(ref);
            }
        }
    }

    // ======================================================================
    //  UI 记忆（近期条目 + 快速槽位 + GUI 绑定）
    // ======================================================================

    public static CompoundTag serializeUiMemory(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        saveRecentEntries(session, root);
        saveQuickSlots(player, session, root);
        saveGuiBindings(session, root);
        return root;
    }

    public static void loadUiMemory(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        loadRecentEntries(session, root);
        loadQuickSlots(player, session, root);
        loadGuiBindings(session, root);
    }

    // -- 近期条目 --

    private static void saveRecentEntries(RtsStorageSession session, CompoundTag root) {
        ListTag list = new ListTag();
        for (RecentEntry entry : session.uiMemory.getRecentEntries()) {
            if (entry == null || entry.id() == null || entry.id().isBlank()) continue;
            CompoundTag tag = new CompoundTag();
            tag.putString("id", entry.id());
            tag.putLong("amount", Math.max(0L, entry.amount()));
            tag.putLong("capacity", Math.max(0L, entry.capacity()));
            tag.putByte("kind", entry.kind());
            list.add(tag);
        }
        root.put("recent_entries", list);
    }

    private static void loadRecentEntries(RtsStorageSession session, CompoundTag root) {
        session.uiMemory.getRecentEntries().clear();
        ListTag list = root.getListOrEmpty("recent_entries");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            String id = tag.getStringOr("id", "");
            long amount = tag.getLongOr("amount", 0L);
            if (id.isBlank() || amount <= 0L) continue;
            Identifier key = Identifier.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            session.uiMemory.addRecentEntryLast(new RecentEntry(
                    id, amount, Math.max(0L, tag.getLongOr("capacity", 0L)), tag.getByteOr("kind", (byte) 0)));
            if (session.uiMemory.getRecentEntries().size() >= RtsStorageRecentEntries.RECENT_ENTRY_LIMIT) break;
        }
    }

    // -- 快速槽位 --

    private static void saveQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        ListTag list = new ListTag();
        for (int i = 0; i < session.uiMemory.getQuickSlotCount(); i++) {
            String itemId = session.uiMemory.getQuickSlotItemId(i);
            if (itemId.isBlank()) continue;
            Identifier key = Identifier.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;

            CompoundTag tag = new CompoundTag();
            tag.putInt("slot", i);
            tag.putString("item_id", itemId);
            ItemStack preview = i < session.uiMemory.getQuickSlotPreviews().length
                    && session.uiMemory.getQuickSlotPreview(i) != null
                    ? session.uiMemory.getQuickSlotPreview(i) : ItemStack.EMPTY;
            if (!preview.isEmpty() && preview.is(BuiltInRegistries.ITEM.getValue(key))) {
                tag.put("stack", com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.save(
                        preview.copyWithCount(1), player.registryAccess()));
            }
            list.add(tag);
        }
        root.put("quick_slots", list);
    }

    private static void loadQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getQuickSlotItemIds(), "");
        Arrays.fill(session.uiMemory.getQuickSlotPreviews(), ItemStack.EMPTY);
        ListTag list = root.getListOrEmpty("quick_slots");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            int slot = tag.getIntOr("slot", 0);
            String itemId = tag.getStringOr("item_id", "");
            if (slot < 0 || slot >= RtsStorageBindings.QUICK_SLOT_COUNT || itemId.isBlank()) continue;
            Identifier key = Identifier.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;

            session.uiMemory.setQuickSlotItemId(slot, itemId);
            ItemStack preview = ItemStack.EMPTY;
            if (tag.contains("stack")) {
                preview = com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.load(
                        tag.getCompoundOrEmpty("stack"), player.registryAccess());
                if (!preview.isEmpty() && !preview.is(BuiltInRegistries.ITEM.getValue(key))) preview = ItemStack.EMPTY;
            }
            session.uiMemory.setQuickSlotPreview(slot, preview.isEmpty()
                    ? new ItemStack(BuiltInRegistries.ITEM.getValue(key))
                    : preview.copyWithCount(1));
        }
    }

    // -- GUI 绑定 --

    private static void saveGuiBindings(RtsStorageSession session, CompoundTag root) {
        ListTag list = new ListTag();
        for (int i = 0; i < session.uiMemory.getGuiBindingCount(); i++) {
            GuiBinding binding = session.uiMemory.getGuiBinding(i);
            if (binding == null || binding.pos() == null || binding.dimension() == null) continue;

            CompoundTag tag = new CompoundTag();
            tag.putInt("slot", i);
            tag.putLong("pos", binding.pos().asLong());
            tag.putString("dimension", binding.dimension().identifier().toString());
            if (binding.face() != null) tag.putByte("face", (byte) binding.face().get3DDataValue());
            tag.putString("label", binding.label() == null ? "" : binding.label());
            tag.putString("item_id", binding.itemId() == null ? "" : binding.itemId());
            list.add(tag);
        }
        root.put("gui_bindings", list);
    }

    private static void loadGuiBindings(RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getGuiBindings(), null);
        ListTag list = root.getListOrEmpty("gui_bindings");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompoundOrEmpty(i);
            int slot = tag.getIntOr("slot", 0);
            if (slot < 0 || slot >= RtsStorageBindings.GUI_BINDING_SLOT_COUNT
                    || !tag.contains("pos")) continue;

            String dimensionId = tag.getStringOr("dimension", "");
            Identifier key = Identifier.tryParse(dimensionId);
            if (key == null) continue;

            String label = tag.getStringOr("label", "");
            String itemId = tag.getStringOr("item_id", "");
            Identifier itemKey = Identifier.tryParse(itemId);
            String normalizedItemId = itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey) ? itemId : "";
            Direction face = null;
            if (tag.contains("face")) {
                int faceId = tag.getByteOr("face", (byte) 0);
                if (faceId >= 0 && faceId < Direction.values().length) face = Direction.from3DDataValue(faceId);
            }
            session.uiMemory.setGuiBinding(slot, new GuiBinding(
                    BlockPos.of(tag.getLongOr("pos", 0L)).immutable(),
                    ResourceKey.create(Registries.DIMENSION, key),
                    label, normalizedItemId, face));
        }
    }

    // ======================================================================
    //  放置任务
    // ======================================================================

    public static CompoundTag serializePlacement(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        // 新命令不会再写入这些队列；非空值只可能是旧存档迁移 shadow。
        // 在 TaskStore root rev1 ACK 前继续保存 shadow，避免 Session 先清空而迁移任务尚未落盘。
        ListTag recoveryList = new ListTag();
        int serializedClaims = 0;
        for (var job : session.placement.recoveryJobs) {
            if (job == null) continue;
            if (recoveryList.size()
                    >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS
                    || serializedClaims
                    >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS) {
                break;
            }
            CompoundTag jobTag = new CompoundTag();
            com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(jobTag, "operation_id", job.operationId());
            jobTag.putString("dimension", job.dimension().identifier().toString());
            jobTag.putLong("target", job.targetPos().asLong());
            ListTag claims = new ListTag();
            for (var claim : job.claims()) {
                if (claims.size()
                        >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB
                        || serializedClaims
                        >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS) {
                    break;
                }
                CompoundTag claimTag = new CompoundTag();
                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(claimTag, "id", claim.entityId());
                claimTag.putInt("ordinal", claim.ordinal());
                claimTag.put("stack", com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.save(
                        claim.expectedStack(), player.registryAccess()));
                claims.add(claimTag);
                serializedClaims++;
            }
            jobTag.put("entities", claims);
            recoveryList.add(jobTag);
        }
        root.put("placed_recovery_jobs", recoveryList);
        return root;
    }

    public static void loadPlacement(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.placement.recoveryJobs.clear();
        ListTag recoveryList = root.getListOrEmpty("placed_recovery_jobs");
        int loadedClaims = 0;
        for (int i = 0; i < recoveryList.size()
                && session.placement.recoveryJobs.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS
                && loadedClaims
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; i++) {
            CompoundTag jobTag = recoveryList.getCompoundOrEmpty(i);
            ResourceKey<Level> dimension = parseDimensionKey(jobTag.getStringOr("dimension", ""));
            // 旧版没有 operationId/ordinal/stack，无法证明 claim 身份，保守留给世界实体自行处理。
            if (dimension == null || !com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(jobTag, "operation_id")) continue;
            java.util.ArrayDeque<com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim>
                    claims = new java.util.ArrayDeque<>();
            ListTag encodedClaims = jobTag.getListOrEmpty("entities");
            for (int j = 0; j < encodedClaims.size()
                    && claims.size()
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB
                    && loadedClaims
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; j++) {
                CompoundTag claimTag = encodedClaims.getCompoundOrEmpty(j);
                // 旧版只有 UUID、没有物品指纹；保守放弃自动接管，让实体继续留在世界中。
                if (!com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.hasUuid(claimTag, "id")
                        || !claimTag.contains("ordinal")
                        || claimTag.getIntOr("ordinal", 0) < 0
                        || !claimTag.contains("stack")) continue;
                ItemStack expected = com.rtsbuilding.rtsbuilding.common.persist.RtsItemStackNbt.load(
                        claimTag.getCompoundOrEmpty("stack"), player.registryAccess());
                if (expected.isEmpty()) continue;
                claims.addLast(new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim(
                        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(claimTag, "id"), claimTag.getIntOr("ordinal", 0), expected));
                loadedClaims++;
            }
            if (!claims.isEmpty()) {
                session.placement.recoveryJobs.addLast(
                        new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob(
                                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(jobTag, "operation_id"), dimension,
                                BlockPos.of(jobTag.getLongOr("target", 0L)).immutable(), claims));
            }
        }
    }

    // ======================================================================
    //  破坏任务
    // ======================================================================

    public static CompoundTag serializeDestroy(ServerPlayer player, RtsStorageSession session) {
        return new CompoundTag();
    }

    public static void loadDestroy(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        // 拆除任务只由 TaskStore 持有；旧 Session 队列不再恢复。
    }

    // ======================================================================
    //  工具方法
    // ======================================================================

    /** 将维度 ID 字符串解析为 ResourceKey<Level> */
    public static ResourceKey<Level> parseDimensionKey(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) return null;
        Identifier key = Identifier.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    private static boolean isRegisteredItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        Identifier key = Identifier.tryParse(itemId);
        return key != null && BuiltInRegistries.ITEM.containsKey(key);
    }
}
