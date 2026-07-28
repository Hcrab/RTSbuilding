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
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
    public static void loadAll(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
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
    public static NBTTagCompound serializeDropBuffer(EntityPlayerMP player, RtsStorageSession session) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList stacks = new NBTTagList();
        int count = 0;
        for (ItemStack stack : session.miningDropBuffer.stacks) {
            if (stack == null || stack.isEmpty()
                    || stacks.tagCount() >= com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
                continue;
            }
            int accepted = Math.min(stack.getCount(),
                    com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_BUFFERED_ITEMS - count);
            if (accepted <= 0) break;
            int remaining = accepted;
            int maxStackSize = Math.max(1, stack.getMaxStackSize());
            while (remaining > 0
                    && stacks.tagCount()
                    < com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
                int chunkSize = Math.min(remaining, maxStackSize);
                stacks.appendTag(copyWithCount(stack, chunkSize).writeToNBT(new NBTTagCompound()));
                count += chunkSize;
                remaining -= chunkSize;
            }
        }
        root.setTag("drop_buffer_stacks", stacks);
        root.setLong("drop_buffer_since", session.miningDropBuffer.firstQueuedGameTime);
        root.setBoolean("drop_buffer_blocked_timer_v2", true);
        return root;
    }

    private static void loadDropBuffer(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState buffer = session.miningDropBuffer;
        buffer.stacks.clear();
        buffer.bufferedItems = 0;
        NBTTagList stacks = root.getTagList("drop_buffer_stacks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stacks.tagCount()
                && buffer.stacks.size() < com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS;
                i++) {
            ItemStack stack = new ItemStack(stacks.getCompoundTagAt(i));
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

    public static NBTTagCompound serializeFunnel(EntityPlayerMP player, RtsStorageSession session) {
        NBTTagCompound root = new NBTTagCompound();
        root.setBoolean("funnel_enabled", session.funnel.funnelEnabled);
        if (session.funnel.funnelTarget != null && session.funnel.funnelTargetDimension != null) {
            root.setLong("funnel_target", session.funnel.funnelTarget.toLong());
            root.setString("funnel_target_dimension",
                    dimensionName(session.funnel.funnelTargetDimension));
        }
        root.setInteger("funnel_cooldown", Math.max(0, session.funnel.funnelTickCooldown));
        NBTTagList stacks = new NBTTagList();
        for (ItemStack stack : session.funnel.funnelBuffer) {
            if (stack != null && !stack.isEmpty()
                    && stacks.tagCount() < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS) {
                stacks.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        root.setTag("funnel_buffer", stacks);
        return root;
    }

    private static void loadFunnel(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        session.funnel.funnelEnabled = root.getBoolean("funnel_enabled");
        Integer targetDimension = parseDimensionKey(
                root.getString("funnel_target_dimension"));
        if (root.hasKey("funnel_target", Constants.NBT.TAG_LONG) && targetDimension != null) {
            session.funnel.funnelTarget = BlockPos.fromLong(root.getLong("funnel_target")).toImmutable();
            session.funnel.funnelTargetDimension = targetDimension;
        } else {
            // 旧存档没有维度身份时不能猜测当前世界，否则切维后可能在同坐标误吸物品。
            session.funnel.funnelTarget = null;
            session.funnel.funnelTargetDimension = null;
        }
        session.funnel.funnelTickCooldown = Math.max(0, root.getInteger("funnel_cooldown"));
        session.funnel.funnelBuffer.clear();
        NBTTagList stacks = root.getTagList("funnel_buffer", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stacks.tagCount()
                && session.funnel.funnelBuffer.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.FUNNEL_BUFFER_MAX_STACKS; i++) {
            ItemStack stack = new ItemStack(stacks.getCompoundTagAt(i));
            if (!stack.isEmpty()) session.funnel.funnelBuffer.add(stack);
        }
    }

    // ======================================================================
    //  浏览状态（字段级加载到 final browser 对象）
    // ======================================================================

    /** 序列化浏览状态到 NBT */
    public static NBTTagCompound serializeBrowser(RtsBrowserState v) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("page", Math.max(0, v.page));
        tag.setString("search", v.search);
        tag.setString("category", RtsStoragePageBuilder.normalizeCategory(v.category));
        tag.setInteger("sort", (v.sort == null ? RtsStorageSort.QUANTITY : v.sort).ordinal());
        tag.setBoolean("ascending", v.ascending);
        tag.setString("craft_search", v.craftSearch);
        tag.setBoolean("craft_show_unavailable", v.craftShowUnavailable);
        tag.setInteger("craft_requested_count", Math.max(1, Math.min(999, v.craftRequestedCount)));
        return tag;
    }

    /** 将会话标志序列化到 NBT */
    public static NBTTagCompound serializeFlags(SessionFlags v) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("auto_store", v.autoStoreMinedDrops);
        tag.setBoolean("use_bd", v.useBdNetwork);
        NBTTagList fluids = new NBTTagList();
        for (java.util.Map.Entry<String, Long> entry : v.internalFluidMb.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            NBTTagCompound ft = new NBTTagCompound();
            ft.setString("id", entry.getKey());
            ft.setLong("amount", entry.getValue());
            fluids.appendTag(ft);
        }
        tag.setTag("fluids", fluids);
        return tag;
    }

    private static void loadBrowserFields(RtsStorageSession session, NBTTagCompound tag) {
        session.browser.page = tag.hasKey("page", Constants.NBT.TAG_INT) ? Math.max(0, tag.getInteger("page")) : 0;
        session.browser.search = tag.hasKey("search", Constants.NBT.TAG_STRING) ? tag.getString("search").trim() : "";
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(tag.getString("category"));
        session.browser.sort = parseSort(tag.getInteger("sort"));
        session.browser.ascending = tag.hasKey("ascending", Constants.NBT.TAG_BYTE) && tag.getBoolean("ascending");
        session.browser.craftSearch = tag.hasKey("craft_search", Constants.NBT.TAG_STRING) ? tag.getString("craft_search").trim() : "";
        session.browser.craftShowUnavailable = tag.hasKey("craft_show_unavailable", Constants.NBT.TAG_BYTE) && tag.getBoolean("craft_show_unavailable");
        session.browser.craftRequestedCount = tag.hasKey("craft_requested_count", Constants.NBT.TAG_INT)
                ? Math.max(1, Math.min(999, tag.getInteger("craft_requested_count")))
                : RtsBrowserState.CRAFTABLE_BATCH_SIZE;
    }

    private static void loadFlagsFields(RtsStorageSession session, NBTTagCompound tag) {
        session.sessionFlags.autoStoreMinedDrops = !tag.hasKey("auto_store", Constants.NBT.TAG_BYTE) || tag.getBoolean("auto_store");
        session.sessionFlags.useBdNetwork = !tag.hasKey("use_bd", Constants.NBT.TAG_BYTE) || tag.getBoolean("use_bd");
        session.sessionFlags.internalFluidMb.clear();
        NBTTagList fluids = tag.getTagList("fluids", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            NBTTagCompound ft = fluids.getCompoundTagAt(i);
            String id = ft.getString("id");
            long amount = ft.getLong("amount");
            if (!isBlank(id) && amount > 0) {
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

    public static NBTTagCompound serializeLinkedStorage(RtsStorageSession session) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList linkedEntries = new NBTTagList();
        long[] linkedPacked = new long[session.linkedStorageInfo.size()];
        byte[] linkedModes = new byte[session.linkedStorageInfo.size()];
        int[] linkedPriorities = new int[session.linkedStorageInfo.size()];
        for (int i = 0; i < session.linkedStorageInfo.size(); i++) {
            LinkedStorageRef ref = session.linkedStorageInfo.get(i);
            if (ref == null || ref.pos() == null) continue;

            byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(
                    session.linkedStorageInfo.getMode(ref));
            int priority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedStorageInfo.getPriority(ref));
            linkedPacked[i] = ref.pos().toLong();
            linkedModes[i] = linkMode;
            linkedPriorities[i] = priority;

            NBTTagCompound linkedTag = new NBTTagCompound();
            linkedTag.setLong("pos", ref.pos().toLong());
            linkedTag.setString("dimension", dimensionName(ref.dimension()));
            linkedTag.setByte("mode", linkMode);
            linkedTag.setInteger("priority", priority);
            UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
            if (backpackUuid != null) linkedTag.setUniqueId("bpUuid", backpackUuid);
            String backpackItemId = session.linkedStorageInfo.getBackpackItemId(ref);
            if (isRegisteredItemId(backpackItemId)) linkedTag.setString("bpItem", backpackItemId);
            if (session.linkedStorageInfo.isDetached(ref)) linkedTag.setBoolean("bpDetached", true);
            linkedEntries.appendTag(linkedTag);
        }
        root.setTag("linked_entries", linkedEntries);
        NbtCompat.setLongArray(root, "linked_positions", linkedPacked);
        root.setByteArray("linked_modes", linkedModes);
        root.setIntArray("linked_priorities", linkedPriorities);
        if (!session.linkedStorageInfo.isEmpty()) {
            LinkedStorageRef first = session.linkedStorageInfo.get(0);
            if (first != null) {
                root.setString("linked_dimension", dimensionName(first.dimension()));
            }
        }
        return root;
    }

    public static void loadLinkedStorage(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        session.linkedStorageInfo.clear();

        byte[] linkedModes = root.getByteArray("linked_modes");
        int[] linkedPriorities = root.getIntArray("linked_priorities");

        Integer legacyDimension = null;
        String legacyDimensionId = root.getString("linked_dimension");
        if (!isBlank(legacyDimensionId)) legacyDimension = parseDimensionKey(legacyDimensionId);

        NBTTagList linkedEntries = root.getTagList("linked_entries", Constants.NBT.TAG_COMPOUND);
        if (!linkedEntries.isEmpty()) {
            loadLinkedStorageModern(linkedEntries, session);
            return;
        }

        WorldServer level = player.getServerWorld();
        int dimension = legacyDimension == null ? level.provider.getDimension() : legacyDimension;
        long[] linkedPackedPositions = NbtCompat.getLongArray(root, "linked_positions");
        for (int i = 0; i < linkedPackedPositions.length; i++) {
            LinkedStorageRef ref = new LinkedStorageRef(dimension, BlockPos.fromLong(linkedPackedPositions[i]).toImmutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = i < linkedModes.length ? linkedModes[i] : RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL;
                int priority = i < linkedPriorities.length ? linkedPriorities[i] : 0;
                session.linkedStorageInfo.add(ref,
                        RtsLinkedStorageResolver.sanitizeLinkMode(linkMode),
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
            }
        }
    }

    private static void loadLinkedStorageModern(NBTTagList linkedEntries, RtsStorageSession session) {
        for (int i = 0; i < linkedEntries.tagCount(); i++) {
            NBTTagCompound linkedTag = linkedEntries.getCompoundTagAt(i);
            if (!linkedTag.hasKey("pos", Constants.NBT.TAG_LONG)) continue;

            Integer dimension = parseDimensionKey(linkedTag.getString("dimension"));
            if (dimension == null) continue;

            LinkedStorageRef ref = new LinkedStorageRef(dimension, BlockPos.fromLong(linkedTag.getLong("pos")).toImmutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkedTag.getByte("mode"));
                int priority = linkedTag.hasKey("priority", Constants.NBT.TAG_INT) ? linkedTag.getInteger("priority") : 0;
                UUID backpackUuid = linkedTag.hasUniqueId("bpUuid") ? linkedTag.getUniqueId("bpUuid") : null;
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

    public static NBTTagCompound serializeUiMemory(EntityPlayerMP player, RtsStorageSession session) {
        NBTTagCompound root = new NBTTagCompound();
        saveRecentEntries(session, root);
        saveQuickSlots(player, session, root);
        saveGuiBindings(session, root);
        return root;
    }

    public static void loadUiMemory(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        loadRecentEntries(session, root);
        loadQuickSlots(player, session, root);
        loadGuiBindings(session, root);
    }

    // -- 近期条目 --

    private static void saveRecentEntries(RtsStorageSession session, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        for (RecentEntry entry : session.uiMemory.getRecentEntries()) {
            if (entry == null || isBlank(entry.id())) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", entry.id());
            tag.setLong("amount", Math.max(0L, entry.amount()));
            tag.setLong("capacity", Math.max(0L, entry.capacity()));
            tag.setByte("kind", entry.kind());
            list.appendTag(tag);
        }
        root.setTag("recent_entries", list);
    }

    private static void loadRecentEntries(RtsStorageSession session, NBTTagCompound root) {
        session.uiMemory.getRecentEntries().clear();
        NBTTagList list = root.getTagList("recent_entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String id = tag.getString("id");
            long amount = tag.getLong("amount");
            if (isBlank(id) || amount <= 0L) continue;
            ResourceLocation key = parseResourceLocation(id);
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) continue;
            session.uiMemory.addRecentEntryLast(new RecentEntry(
                    id, amount, Math.max(0L, tag.getLong("capacity")), tag.getByte("kind")));
            if (session.uiMemory.getRecentEntries().size() >= RtsStorageRecentEntries.RECENT_ENTRY_LIMIT) break;
        }
    }

    // -- 快速槽位 --

    private static void saveQuickSlots(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < session.uiMemory.getQuickSlotCount(); i++) {
            String itemId = session.uiMemory.getQuickSlotItemId(i);
            if (isBlank(itemId)) continue;
            ResourceLocation key = parseResourceLocation(itemId);
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) continue;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setString("item_id", itemId);
            ItemStack preview = i < session.uiMemory.getQuickSlotPreviews().length
                    && session.uiMemory.getQuickSlotPreview(i) != null
                    ? session.uiMemory.getQuickSlotPreview(i) : ItemStack.EMPTY;
            if (!preview.isEmpty() && preview.getItem() == ForgeRegistries.ITEMS.getValue(key)) {
                tag.setTag("stack", copyWithCount(preview, 1).writeToNBT(new NBTTagCompound()));
            }
            list.appendTag(tag);
        }
        root.setTag("quick_slots", list);
    }

    private static void loadQuickSlots(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        Arrays.fill(session.uiMemory.getQuickSlotItemIds(), "");
        Arrays.fill(session.uiMemory.getQuickSlotPreviews(), ItemStack.EMPTY);
        NBTTagList list = root.getTagList("quick_slots", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("slot");
            String itemId = tag.getString("item_id");
            if (slot < 0 || slot >= RtsStorageBindings.QUICK_SLOT_COUNT || isBlank(itemId)) continue;
            ResourceLocation key = parseResourceLocation(itemId);
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) continue;

            session.uiMemory.setQuickSlotItemId(slot, itemId);
            ItemStack preview = ItemStack.EMPTY;
            if (tag.hasKey("stack", Constants.NBT.TAG_COMPOUND)) {
                preview = new ItemStack(tag.getCompoundTag("stack"));
                if (!preview.isEmpty() && preview.getItem() != ForgeRegistries.ITEMS.getValue(key)) preview = ItemStack.EMPTY;
            }
            session.uiMemory.setQuickSlotPreview(slot, preview.isEmpty()
                    ? new ItemStack(ForgeRegistries.ITEMS.getValue(key))
                    : copyWithCount(preview, 1));
        }
    }

    // -- GUI 绑定 --

    private static void saveGuiBindings(RtsStorageSession session, NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < session.uiMemory.getGuiBindingCount(); i++) {
            GuiBinding binding = session.uiMemory.getGuiBinding(i);
            if (binding == null || binding.pos() == null) continue;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setLong("pos", binding.pos().toLong());
            tag.setString("dimension", dimensionName(binding.dimension()));
            if (binding.face() != null) tag.setByte("face", (byte) binding.face().getIndex());
            tag.setString("label", binding.label() == null ? "" : binding.label());
            tag.setString("item_id", binding.itemId() == null ? "" : binding.itemId());
            list.appendTag(tag);
        }
        root.setTag("gui_bindings", list);
    }

    private static void loadGuiBindings(RtsStorageSession session, NBTTagCompound root) {
        Arrays.fill(session.uiMemory.getGuiBindings(), null);
        NBTTagList list = root.getTagList("gui_bindings", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("slot");
            if (slot < 0 || slot >= RtsStorageBindings.GUI_BINDING_SLOT_COUNT
                    || !tag.hasKey("pos", Constants.NBT.TAG_LONG)) continue;

            String dimensionId = tag.getString("dimension");
            Integer dimension = parseDimensionKey(dimensionId);
            if (dimension == null) continue;

            String label = tag.getString("label");
            String itemId = tag.getString("item_id");
            ResourceLocation itemKey = parseResourceLocation(itemId);
            String normalizedItemId = itemKey != null && ForgeRegistries.ITEMS.containsKey(itemKey) ? itemId : "";
            EnumFacing face = null;
            if (tag.hasKey("face", Constants.NBT.TAG_BYTE)) {
                int faceId = tag.getByte("face");
                if (faceId >= 0 && faceId < EnumFacing.values().length) face = EnumFacing.byIndex(faceId);
            }
            session.uiMemory.setGuiBinding(slot, new GuiBinding(
                    BlockPos.fromLong(tag.getLong("pos")).toImmutable(),
                    dimension,
                    label, normalizedItemId, face));
        }
    }

    // ======================================================================
    //  放置任务
    // ======================================================================

    public static NBTTagCompound serializePlacement(EntityPlayerMP player, RtsStorageSession session) {
        NBTTagCompound root = new NBTTagCompound();
        // 新命令不会再写入这些队列；非空值只可能是旧存档迁移 shadow。
        // 在 TaskStore root rev1 ACK 前继续保存 shadow，避免 Session 先清空而迁移任务尚未落盘。
        NBTTagList recoveryList = new NBTTagList();
        int serializedClaims = 0;
        for (com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob job
                : session.placement.recoveryJobs) {
            if (job == null) continue;
            if (recoveryList.tagCount()
                    >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS
                    || serializedClaims
                    >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS) {
                break;
            }
            NBTTagCompound jobTag = new NBTTagCompound();
            jobTag.setUniqueId("operation_id", job.operationId());
            jobTag.setString("dimension", dimensionName(job.dimension()));
            jobTag.setLong("target", job.targetPos().toLong());
            NBTTagList claims = new NBTTagList();
            for (com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim claim
                    : job.claims()) {
                if (claims.tagCount()
                        >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB
                        || serializedClaims
                        >= com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS) {
                    break;
                }
                NBTTagCompound claimTag = new NBTTagCompound();
                claimTag.setUniqueId("id", claim.entityId());
                claimTag.setInteger("ordinal", claim.ordinal());
                claimTag.setTag("stack", claim.expectedStack().writeToNBT(new NBTTagCompound()));
                claims.appendTag(claimTag);
                serializedClaims++;
            }
            jobTag.setTag("entities", claims);
            recoveryList.appendTag(jobTag);
        }
        root.setTag("placed_recovery_jobs", recoveryList);
        return root;
    }

    public static void loadPlacement(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        session.placement.recoveryJobs.clear();
        NBTTagList recoveryList = root.getTagList("placed_recovery_jobs", Constants.NBT.TAG_COMPOUND);
        int loadedClaims = 0;
        for (int i = 0; i < recoveryList.tagCount()
                && session.placement.recoveryJobs.size()
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS
                && loadedClaims
                < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; i++) {
            NBTTagCompound jobTag = recoveryList.getCompoundTagAt(i);
            Integer dimension = parseDimensionKey(jobTag.getString("dimension"));
            // 旧版没有 operationId/ordinal/stack，无法证明 claim 身份，保守留给世界实体自行处理。
            if (dimension == null || !jobTag.hasUniqueId("operation_id")) continue;
            java.util.ArrayDeque<com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim>
                    claims = new java.util.ArrayDeque<>();
            NBTTagList encodedClaims = jobTag.getTagList("entities", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < encodedClaims.tagCount()
                    && claims.size()
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB
                    && loadedClaims
                    < com.rtsbuilding.rtsbuilding.server.service.RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS; j++) {
                NBTTagCompound claimTag = encodedClaims.getCompoundTagAt(j);
                // 旧版只有 UUID、没有物品指纹；保守放弃自动接管，让实体继续留在世界中。
                if (!claimTag.hasUniqueId("id")
                        || !claimTag.hasKey("ordinal", Constants.NBT.TAG_INT)
                        || claimTag.getInteger("ordinal") < 0
                        || !claimTag.hasKey("stack", Constants.NBT.TAG_COMPOUND)) continue;
                ItemStack expected = new ItemStack(claimTag.getCompoundTag("stack"));
                if (expected.isEmpty()) continue;
                claims.addLast(new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim(
                        claimTag.getUniqueId("id"), claimTag.getInteger("ordinal"), expected));
                loadedClaims++;
            }
            if (!claims.isEmpty()) {
                session.placement.recoveryJobs.addLast(
                        new com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob(
                                jobTag.getUniqueId("operation_id"), dimension,
                                BlockPos.fromLong(jobTag.getLong("target")).toImmutable(), claims));
            }
        }
    }

    // ======================================================================
    //  破坏任务
    // ======================================================================

    public static NBTTagCompound serializeDestroy(EntityPlayerMP player, RtsStorageSession session) {
        return new NBTTagCompound();
    }

    public static void loadDestroy(EntityPlayerMP player, RtsStorageSession session, NBTTagCompound root) {
        // 拆除任务只由 TaskStore 持有；旧 Session 队列不再恢复。
    }

    // ======================================================================
    //  工具方法
    // ======================================================================

    /** 将维度 ID 字符串解析为 ResourceKey<Level> */
    public static Integer parseDimensionKey(String dimensionId) {
        if (isBlank(dimensionId)) return null;
        if ("minecraft:overworld".equals(dimensionId)) return 0;
        if ("minecraft:the_nether".equals(dimensionId)) return -1;
        if ("minecraft:the_end".equals(dimensionId)) return 1;
        try {
            return Integer.valueOf(dimensionId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isRegisteredItemId(String itemId) {
        if (isBlank(itemId)) return false;
        ResourceLocation key = parseResourceLocation(itemId);
        return key != null && ForgeRegistries.ITEMS.containsKey(key);
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResourceLocation parseResourceLocation(String value) {
        if (isBlank(value)) return null;
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String dimensionName(int dimension) {
        if (dimension == 0) return "minecraft:overworld";
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return Integer.toString(dimension);
    }
}
