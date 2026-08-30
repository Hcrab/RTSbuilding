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
import net.minecraft.resources.ResourceLocation;
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
            int remaining = accepted;
            int maxStackSize = Math.max(1, stack.getMaxStackSize());
            while (remaining > 0
                    && stacks.size()
                    < com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
                int chunkSize = Math.min(remaining, maxStackSize);
                stacks.add(stack.copyWithCount(chunkSize).save(player.registryAccess()));
                count += chunkSize;
                remaining -= chunkSize;
            }
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
        ListTag stacks = root.getList("drop_buffer_stacks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stacks.size()
                && buffer.stacks.size() < com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS;
                i++) {
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), stacks.getCompound(i));
            if (stack.isEmpty()) continue;
            int accepted = buffer.enqueueMerged(stack, stack.getCount());
            if (accepted <= 0) break;
        }
        // 旧存档的 since 表示“进入缓存的时间”，不能继续当成真实储存堵塞时间，否则登录即误回退。
        buffer.firstQueuedGameTime = buffer.stacks.isEmpty()
                || !root.getBoolean("drop_buffer_blocked_timer_v2")
                ? -1L
                : root.getLong("drop_buffer_since");
        buffer.fullNoticeSent = false;
    }

    public static CompoundTag serializeFunnel(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();
        root.putBoolean("funnel_enabled", session.funnel.funnelEnabled);
        if (session.funnel.funnelTarget != null && session.funnel.funnelTargetDimension != null) {
            root.putLong("funnel_target", session.funnel.funnelTarget.asLong());
            root.putString("funnel_target_dimension",
                    session.funnel.funnelTargetDimension.location().toString());
        }
        root.putInt("funnel_cooldown", Math.max(0, session.funnel.funnelTickCooldown));
        ListTag stacks = new ListTag();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack != null && !stack.isEmpty()
                    && stacks.size() < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS) {
                stacks.add(stack.save(player.registryAccess()));
            }
        }
        root.put("funnel_buffer", stacks);
        return root;
    }

    private static void loadFunnel(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.funnel.funnelEnabled = root.getBoolean("funnel_enabled");
        ResourceKey<Level> targetDimension = parseDimensionKey(
                root.getString("funnel_target_dimension"));
        if (root.contains("funnel_target", Tag.TAG_LONG) && targetDimension != null) {
            session.funnel.funnelTarget = BlockPos.of(root.getLong("funnel_target")).immutable();
            session.funnel.funnelTargetDimension = targetDimension;
        } else {
            // 旧存档没有维度身份时不能猜测当前世界，否则切维后可能在同坐标误吸物品。
            session.funnel.funnelTarget = null;
            session.funnel.funnelTargetDimension = null;
        }
        session.funnel.funnelTickCooldown = Math.max(0, root.getInt("funnel_cooldown"));
        session.funnel.funnelBuffer.clear();
        ListTag stacks = root.getList("funnel_buffer", Tag.TAG_COMPOUND);
        for (int i = 0; i < stacks.size()
                && session.funnel.funnelBuffer.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS; i++) {
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), stacks.getCompound(i));
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
        session.browser.page = tag.contains("page", Tag.TAG_INT) ? Math.max(0, tag.getInt("page")) : 0;
        session.browser.search = tag.contains("search", Tag.TAG_STRING) ? tag.getString("search").trim() : "";
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(tag.getString("category"));
        session.browser.sort = parseSort(tag.getInt("sort"));
        session.browser.ascending = tag.contains("ascending", Tag.TAG_BYTE) && tag.getBoolean("ascending");
        session.browser.craftSearch = tag.contains("craft_search", Tag.TAG_STRING) ? tag.getString("craft_search").trim() : "";
        session.browser.craftShowUnavailable = tag.contains("craft_show_unavailable", Tag.TAG_BYTE) && tag.getBoolean("craft_show_unavailable");
        session.browser.craftRequestedCount = tag.contains("craft_requested_count", Tag.TAG_INT)
                ? Math.max(1, Math.min(999, tag.getInt("craft_requested_count")))
                : RtsBrowserState.CRAFTABLE_BATCH_SIZE;
    }

    private static void loadFlagsFields(RtsStorageSession session, CompoundTag tag) {
        session.sessionFlags.autoStoreMinedDrops = !tag.contains("auto_store", Tag.TAG_BYTE) || tag.getBoolean("auto_store");
        session.sessionFlags.useBdNetwork = !tag.contains("use_bd", Tag.TAG_BYTE) || tag.getBoolean("use_bd");
        session.sessionFlags.internalFluidMb.clear();
        ListTag fluids = tag.getList("fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag ft = fluids.getCompound(i);
            String id = ft.getString("id");
            long amount = ft.getLong("amount");
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
            linkedTag.putString("dimension", ref.dimension().location().toString());
            linkedTag.putByte("mode", linkMode);
            linkedTag.putInt("priority", priority);
            UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
            if (backpackUuid != null) linkedTag.putUUID("bpUuid", backpackUuid);
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
                root.putString("linked_dimension", first.dimension().location().toString());
            }
        }
        return root;
    }

    public static void loadLinkedStorage(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.linkedStorageInfo.clear();

        byte[] linkedModes = root.getByteArray("linked_modes");
        int[] linkedPriorities = root.getIntArray("linked_priorities");

        ResourceKey<Level> legacyDimension = null;
        String legacyDimensionId = root.getString("linked_dimension");
        if (!legacyDimensionId.isBlank()) legacyDimension = parseDimensionKey(legacyDimensionId);

        ListTag linkedEntries = root.getList("linked_entries", Tag.TAG_COMPOUND);
        if (!linkedEntries.isEmpty()) {
            loadLinkedStorageModern(linkedEntries, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        ResourceKey<Level> dimension = legacyDimension == null ? level.dimension() : legacyDimension;
        long[] linkedPackedPositions = root.getLongArray("linked_positions");
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
            CompoundTag linkedTag = linkedEntries.getCompound(i);
            if (!linkedTag.contains("pos", Tag.TAG_LONG)) continue;

            ResourceKey<Level> dimension = parseDimensionKey(linkedTag.getString("dimension"));
            if (dimension == null) continue;

            LinkedStorageRef ref = new LinkedStorageRef(dimension, BlockPos.of(linkedTag.getLong("pos")).immutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkedTag.getByte("mode"));
                int priority = linkedTag.contains("priority", Tag.TAG_INT) ? linkedTag.getInt("priority") : 0;
                UUID backpackUuid = linkedTag.contains("bpUuid", Tag.TAG_INT_ARRAY) ? linkedTag.getUUID("bpUuid") : null;
                String backpackItemId = isRegisteredItemId(linkedTag.getString("bpItem"))
                        ? linkedTag.getString("bpItem") : null;
                session.linkedStorageInfo.add(ref, linkMode,
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority),
                        backpackUuid, backpackItemId);
                if (linkedTag.getBoolean("bpDetached")) session.linkedStorageInfo.markDetached(ref);
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
        ListTag list = root.getList("recent_entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            String id = tag.getString("id");
            long amount = tag.getLong("amount");
            if (id.isBlank() || amount <= 0L) continue;
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;
            session.uiMemory.addRecentEntryLast(new RecentEntry(
                    id, amount, Math.max(0L, tag.getLong("capacity")), tag.getByte("kind")));
            if (session.uiMemory.getRecentEntries().size() >= RtsStorageRecentEntries.RECENT_ENTRY_LIMIT) break;
        }
    }

    // -- 快速槽位 --

    private static void saveQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        ListTag list = new ListTag();
        for (int i = 0; i < session.uiMemory.getQuickSlotCount(); i++) {
            String itemId = session.uiMemory.getQuickSlotItemId(i);
            if (itemId.isBlank()) continue;
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;

            CompoundTag tag = new CompoundTag();
            tag.putInt("slot", i);
            tag.putString("item_id", itemId);
            ItemStack preview = i < session.uiMemory.getQuickSlotPreviews().length
                    && session.uiMemory.getQuickSlotPreview(i) != null
                    ? session.uiMemory.getQuickSlotPreview(i) : ItemStack.EMPTY;
            if (!preview.isEmpty() && preview.is(BuiltInRegistries.ITEM.get(key))) {
                tag.put("stack", preview.copyWithCount(1).save(player.registryAccess()));
            }
            list.add(tag);
        }
        root.put("quick_slots", list);
    }

    private static void loadQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getQuickSlotItemIds(), "");
        Arrays.fill(session.uiMemory.getQuickSlotPreviews(), ItemStack.EMPTY);
        ListTag list = root.getList("quick_slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getInt("slot");
            String itemId = tag.getString("item_id");
            if (slot < 0 || slot >= RtsStorageBindings.QUICK_SLOT_COUNT || itemId.isBlank()) continue;
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) continue;

            session.uiMemory.setQuickSlotItemId(slot, itemId);
            ItemStack preview = ItemStack.EMPTY;
            if (tag.contains("stack", Tag.TAG_COMPOUND)) {
                preview = ItemStack.parseOptional(player.registryAccess(), tag.getCompound("stack"));
                if (!preview.isEmpty() && !preview.is(BuiltInRegistries.ITEM.get(key))) preview = ItemStack.EMPTY;
            }
            session.uiMemory.setQuickSlotPreview(slot, preview.isEmpty()
                    ? new ItemStack(BuiltInRegistries.ITEM.get(key))
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
            tag.putString("dimension", binding.dimension().location().toString());
            if (binding.face() != null) tag.putByte("face", (byte) binding.face().get3DDataValue());
            tag.putString("label", binding.label() == null ? "" : binding.label());
            tag.putString("item_id", binding.itemId() == null ? "" : binding.itemId());
            list.add(tag);
        }
        root.put("gui_bindings", list);
    }

    private static void loadGuiBindings(RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getGuiBindings(), null);
        ListTag list = root.getList("gui_bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            int slot = tag.getInt("slot");
            if (slot < 0 || slot >= RtsStorageBindings.GUI_BINDING_SLOT_COUNT
                    || !tag.contains("pos", Tag.TAG_LONG)) continue;

            String dimensionId = tag.getString("dimension");
            ResourceLocation key = ResourceLocation.tryParse(dimensionId);
            if (key == null) continue;

            String label = tag.getString("label");
            String itemId = tag.getString("item_id");
            ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
            String normalizedItemId = itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey) ? itemId : "";
            Direction face = null;
            if (tag.contains("face", Tag.TAG_BYTE)) {
                int faceId = tag.getByte("face");
                if (faceId >= 0 && faceId < Direction.values().length) face = Direction.from3DDataValue(faceId);
            }
            session.uiMemory.setGuiBinding(slot, new GuiBinding(
                    BlockPos.of(tag.getLong("pos")).immutable(),
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
            jobTag.putUUID("operation_id", job.operationId());
            jobTag.putString("dimension", job.dimension().location().toString());
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
                claimTag.putUUID("id", claim.entityId());
                claimTag.putInt("ordinal", claim.ordinal());
                claimTag.put("stack", claim.expectedStack().save(player.registryAccess()));
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
        ListTag recoveryList = root.getList("placed_recovery_jobs", Tag.TAG_COMPOUND);
        int loadedClaims = 0;
        for (int i = 0; i < recoveryList.size()
                && session.placement.recoveryJobs.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS
                && loadedClaims
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; i++) {
            CompoundTag jobTag = recoveryList.getCompound(i);
            ResourceKey<Level> dimension = parseDimensionKey(jobTag.getString("dimension"));
            // 旧版没有 operationId/ordinal/stack，无法证明 claim 身份，保守留给世界实体自行处理。
            if (dimension == null || !jobTag.hasUUID("operation_id")) continue;
            java.util.ArrayDeque<com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim>
                    claims = new java.util.ArrayDeque<>();
            ListTag encodedClaims = jobTag.getList("entities", Tag.TAG_COMPOUND);
            for (int j = 0; j < encodedClaims.size()
                    && claims.size()
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB
                    && loadedClaims
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; j++) {
                CompoundTag claimTag = encodedClaims.getCompound(j);
                // 旧版只有 UUID、没有物品指纹；保守放弃自动接管，让实体继续留在世界中。
                if (!claimTag.hasUUID("id")
                        || !claimTag.contains("ordinal", Tag.TAG_INT)
                        || claimTag.getInt("ordinal") < 0
                        || !claimTag.contains("stack", Tag.TAG_COMPOUND)) continue;
                ItemStack expected = ItemStack.parseOptional(
                        player.registryAccess(), claimTag.getCompound("stack"));
                if (expected.isEmpty()) continue;
                claims.addLast(new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim(
                        claimTag.getUUID("id"), claimTag.getInt("ordinal"), expected));
                loadedClaims++;
            }
            if (!claims.isEmpty()) {
                session.placement.recoveryJobs.addLast(
                        new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob(
                                jobTag.getUUID("operation_id"), dimension,
                                BlockPos.of(jobTag.getLong("target")).immutable(), claims));
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
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    private static boolean isRegisteredItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key != null && BuiltInRegistries.ITEM.containsKey(key);
    }
}
