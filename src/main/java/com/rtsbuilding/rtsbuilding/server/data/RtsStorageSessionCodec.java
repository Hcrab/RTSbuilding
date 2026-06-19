package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsBrowserState;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * {@link RtsStorageSession} 的 NBT 编解码器。
 *
 * <p>本类负责 RTS 存储会话的所有存档字段名称、默认值、校验以及旧存档迁移。
 * 本类有意不解析方块实体、查询能力、刷新存储页面、发送数据包或判断玩家
 * 是否可以使用某个链接方块——那些运行时决策仍归属于 service/storage 模块。
 *
 * <p>链接存储的序列化已提取到 {@link RtsLinkedStorageCodec} 中。
 * 本类保留顶层编排以及剩余较小的编解码部分（内部流体、近期条目、
 * 快速槽位、GUI 绑定和浏览状态）。请在此保持向后兼容性——
 * 现代格式将每个链接存储以维度+位置的复合形式存储在
 * {@code linked_entries} 中；旧存档使用 {@code linked_positions}
 * 加一个 {@code linked_dimension}。两者都必须持续加载，直到故意执行
 * 存档格式迁移为止。
 */
public final class RtsStorageSessionCodec {
    public static final String ROOT_KEY = "rtsbuilding_storage_session";

    private static final String NBT_INTERNAL_FLUIDS = "internal_fluids";
    private static final String NBT_FLUID_ID = "id";
    private static final String NBT_FLUID_AMOUNT = "amount";
    private static final String NBT_RECENT_ENTRIES = "recent_entries";
    private static final String NBT_RECENT_ENTRY_ID = "id";
    private static final String NBT_RECENT_ENTRY_AMOUNT = "amount";
    private static final String NBT_RECENT_ENTRY_CAPACITY = "capacity";
    private static final String NBT_RECENT_ENTRY_KIND = "kind";
    private static final String NBT_QUICK_SLOTS = "quick_slots";
    private static final String NBT_QUICK_SLOT_INDEX = "slot";
    private static final String NBT_QUICK_SLOT_ITEM_ID = "item_id";
    private static final String NBT_QUICK_SLOT_STACK = "stack";
    private static final String NBT_GUI_BINDINGS = "gui_bindings";
    private static final String NBT_GUI_BINDING_SLOT = "slot";
    private static final String NBT_GUI_BINDING_POS = "pos";
    private static final String NBT_GUI_BINDING_DIMENSION = "dimension";
    private static final String NBT_GUI_BINDING_FACE = "face";
    private static final String NBT_GUI_BINDING_LABEL = "label";
    private static final String NBT_GUI_BINDING_ITEM_ID = "item_id";
    private static final String NBT_PAGE = "page";
    private static final String NBT_SEARCH = "search";
    private static final String NBT_CATEGORY = "category";
    private static final String NBT_SORT = "sort";
    private static final String NBT_ASCENDING = "ascending";
    private static final String NBT_AUTO_STORE_MINED_DROPS = "auto_store_mined_drops";
    private static final String NBT_USE_BD_NETWORK = "use_bd_network";
    private static final String NBT_CRAFT_SEARCH = "craft_search";
    private static final String NBT_CRAFT_SHOW_UNAVAILABLE = "craft_show_unavailable";
    private static final String NBT_CRAFT_REQUESTED_COUNT = "craft_requested_count";

    /** 工具类，私有构造防止实例化 */
    private RtsStorageSessionCodec() {
    }

    /**
     * 从 NBT 根节点加载存储会话的全部状态。
     * 依次加载：链接存储、浏览状态、会话标志、内部流体、近期条目、快速槽位、GUI 绑定和放置任务。
     */
    public static void load(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        RtsLinkedStorageCodec.load(player, session, root);

        session.browser.page = root.contains(NBT_PAGE, Tag.TAG_INT) ? Math.max(0, root.getInt(NBT_PAGE)) : 0;
        session.browser.search = sanitizeSavedText(root.getString(NBT_SEARCH), 128);
        session.browser.category = RtsStoragePageBuilder.normalizeCategory(root.getString(NBT_CATEGORY));
        session.browser.sort = parseSavedSort(root.getInt(NBT_SORT));
        session.browser.ascending = root.contains(NBT_ASCENDING, Tag.TAG_BYTE) && root.getBoolean(NBT_ASCENDING);
        session.sessionFlags.autoStoreMinedDrops = !root.contains(NBT_AUTO_STORE_MINED_DROPS, Tag.TAG_BYTE)
                || root.getBoolean(NBT_AUTO_STORE_MINED_DROPS);
        session.sessionFlags.useBdNetwork = !root.contains(NBT_USE_BD_NETWORK, Tag.TAG_BYTE)
                || root.getBoolean(NBT_USE_BD_NETWORK);
        session.browser.craftSearch = sanitizeSavedText(root.getString(NBT_CRAFT_SEARCH), 128);
        session.browser.craftShowUnavailable = root.contains(NBT_CRAFT_SHOW_UNAVAILABLE, Tag.TAG_BYTE)
                && root.getBoolean(NBT_CRAFT_SHOW_UNAVAILABLE);
        session.browser.craftRequestedCount = root.contains(NBT_CRAFT_REQUESTED_COUNT, Tag.TAG_INT)
                ? Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE,
                        Math.min(999, root.getInt(NBT_CRAFT_REQUESTED_COUNT)))
                : RtsBrowserState.CRAFTABLE_BATCH_SIZE;

        loadInternalFluids(session, root);
        loadRecentEntries(session, root);
        loadQuickSlots(player, session, root);
        loadGuiBindings(session, root);
        loadPlacementJobs(player, session, root);
        loadDestroyJobs(player, session, root);
    }

    /**
     * 将存储会话的全部状态序列化为 NBT 根节点。
     * 依次保存：浏览状态、会话标志、链接存储、内部流体、近期条目、快速槽位、GUI 绑定和放置任务。
     */
    public static CompoundTag serialize(ServerPlayer player, RtsStorageSession session) {
        CompoundTag root = new CompoundTag();

        root.putInt(NBT_PAGE, Math.max(0, session.browser.page));
        root.putString(NBT_SEARCH, sanitizeSavedText(session.browser.search, 128));
        root.putString(NBT_CATEGORY, RtsStoragePageBuilder.normalizeCategory(session.browser.category));
        root.putInt(NBT_SORT, (session.browser.sort == null ? RtsStorageSort.QUANTITY : session.browser.sort).ordinal());
        root.putBoolean(NBT_ASCENDING, session.browser.ascending);
        root.putBoolean(NBT_AUTO_STORE_MINED_DROPS, session.sessionFlags.autoStoreMinedDrops);
        root.putBoolean(NBT_USE_BD_NETWORK, session.sessionFlags.useBdNetwork);
        root.putString(NBT_CRAFT_SEARCH, sanitizeSavedText(session.browser.craftSearch, 128));
        root.putBoolean(NBT_CRAFT_SHOW_UNAVAILABLE, session.browser.craftShowUnavailable);
        root.putInt(NBT_CRAFT_REQUESTED_COUNT,
                Math.max(RtsBrowserState.CRAFTABLE_BATCH_SIZE, Math.min(999, session.browser.craftRequestedCount)));

        RtsLinkedStorageCodec.save(session, root);
        saveInternalFluids(session, root);
        saveRecentEntries(session, root);
        saveQuickSlots(player, session, root);
        saveGuiBindings(session, root);
        savePlacementJobs(player, session, root);
        saveDestroyJobs(player, session, root);

        return root;
    }

    // ======================================================================
    //  内部流体
    // ======================================================================

    /** 从 NBT 加载内部流体数据 */
    private static void loadInternalFluids(RtsStorageSession session, CompoundTag root) {
        session.sessionFlags.internalFluidMb.clear();
        ListTag fluidEntries = root.getList(NBT_INTERNAL_FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidEntries.size(); i++) {
            CompoundTag fluidTag = fluidEntries.getCompound(i);
            String fluidId = fluidTag.getString(NBT_FLUID_ID);
            long amount = fluidTag.getLong(NBT_FLUID_AMOUNT);
            if (fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(fluidId);
            if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
                continue;
            }
            session.sessionFlags.internalFluidMb.put(fluidId, amount);
        }
    }

    /** 将内部流体数据保存到 NBT */
    private static void saveInternalFluids(RtsStorageSession session, CompoundTag root) {
        ListTag fluidEntries = new ListTag();
        for (var entry : session.sessionFlags.internalFluidMb.entrySet()) {
            String fluidId = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (fluidId == null || fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString(NBT_FLUID_ID, fluidId);
            fluidTag.putLong(NBT_FLUID_AMOUNT, amount);
            fluidEntries.add(fluidTag);
        }
        root.put(NBT_INTERNAL_FLUIDS, fluidEntries);
    }

    // ======================================================================
    //  近期条目
    // ======================================================================

    /** 从 NBT 加载近期条目列表 */
    private static void loadRecentEntries(RtsStorageSession session, CompoundTag root) {
        session.uiMemory.getRecentEntries().clear();
        ListTag recentEntries = root.getList(NBT_RECENT_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < recentEntries.size(); i++) {
            CompoundTag recentTag = recentEntries.getCompound(i);
            String entryId = recentTag.getString(NBT_RECENT_ENTRY_ID);
            long amount = recentTag.getLong(NBT_RECENT_ENTRY_AMOUNT);
            long capacity = recentTag.getLong(NBT_RECENT_ENTRY_CAPACITY);
            byte kind = recentTag.getByte(NBT_RECENT_ENTRY_KIND);
            if (entryId.isBlank() || amount <= 0L) {
                continue;
            }
            session.uiMemory.addRecentEntryLast(new RecentEntry(entryId, amount, Math.max(0L, capacity), kind));
            if (session.uiMemory.getRecentEntries().size() >= RtsStorageRecentEntries.RECENT_ENTRY_LIMIT) {
                break;
            }
        }
    }

    /** 将近期条目列表保存到 NBT */
    private static void saveRecentEntries(RtsStorageSession session, CompoundTag root) {
        ListTag recentEntries = new ListTag();
        for (RecentEntry recentEntry : session.uiMemory.getRecentEntries()) {
            if (recentEntry == null || recentEntry.id() == null || recentEntry.id().isBlank()) {
                continue;
            }
            CompoundTag recentTag = new CompoundTag();
            recentTag.putString(NBT_RECENT_ENTRY_ID, recentEntry.id());
            recentTag.putLong(NBT_RECENT_ENTRY_AMOUNT, Math.max(0L, recentEntry.amount()));
            recentTag.putLong(NBT_RECENT_ENTRY_CAPACITY, Math.max(0L, recentEntry.capacity()));
            recentTag.putByte(NBT_RECENT_ENTRY_KIND, recentEntry.kind());
            recentEntries.add(recentTag);
        }
        root.put(NBT_RECENT_ENTRIES, recentEntries);
    }

    // ======================================================================
    //  快速槽位
    // ======================================================================

    /** 从 NBT 加载快速槽位配置 */
    private static void loadQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getQuickSlotItemIds(), "");
        Arrays.fill(session.uiMemory.getQuickSlotPreviews(), ItemStack.EMPTY);
        ListTag quickSlots = root.getList(NBT_QUICK_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < quickSlots.size(); i++) {
            CompoundTag quickSlotTag = quickSlots.getCompound(i);
            int slot = quickSlotTag.getInt(NBT_QUICK_SLOT_INDEX);
            String itemId = quickSlotTag.getString(NBT_QUICK_SLOT_ITEM_ID);
            if (slot < 0 || slot >= RtsStorageBindings.QUICK_SLOT_COUNT || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            session.uiMemory.setQuickSlotItemId(slot, itemId);
            ItemStack preview = ItemStack.EMPTY;
            if (quickSlotTag.contains(NBT_QUICK_SLOT_STACK, Tag.TAG_COMPOUND)) {
                preview = ItemStack.parseOptional(player.registryAccess(), quickSlotTag.getCompound(NBT_QUICK_SLOT_STACK));
                if (!preview.isEmpty() && !preview.is(BuiltInRegistries.ITEM.get(key))) {
                    preview = ItemStack.EMPTY;
                }
            }
            session.uiMemory.setQuickSlotPreview(slot, preview.isEmpty()
                    ? new ItemStack(BuiltInRegistries.ITEM.get(key))
                    : preview.copyWithCount(1));
        }
    }

    /** 将快速槽位配置保存到 NBT */
    private static void saveQuickSlots(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        ListTag quickSlots = new ListTag();
        for (int i = 0; i < session.uiMemory.getQuickSlotCount(); i++) {
            String itemId = session.uiMemory.getQuickSlotItemId(i);
            if (itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            CompoundTag quickSlotTag = new CompoundTag();
            quickSlotTag.putInt(NBT_QUICK_SLOT_INDEX, i);
            quickSlotTag.putString(NBT_QUICK_SLOT_ITEM_ID, itemId);
            ItemStack preview = i < session.uiMemory.getQuickSlotPreviews().length && session.uiMemory.getQuickSlotPreview(i) != null
                    ? session.uiMemory.getQuickSlotPreview(i)
                    : ItemStack.EMPTY;
            if (!preview.isEmpty() && preview.is(BuiltInRegistries.ITEM.get(key))) {
                quickSlotTag.put(NBT_QUICK_SLOT_STACK, preview.copyWithCount(1).save(player.registryAccess()));
            }
            quickSlots.add(quickSlotTag);
        }
        root.put(NBT_QUICK_SLOTS, quickSlots);
    }

    // ======================================================================
    //  GUI 绑定
    // ======================================================================

    /** 从 NBT 加载 GUI 绑定配置 */
    private static void loadGuiBindings(RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.uiMemory.getGuiBindings(), null);
        ListTag guiBindings = root.getList(NBT_GUI_BINDINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < guiBindings.size(); i++) {
            CompoundTag bindingTag = guiBindings.getCompound(i);
            int slot = bindingTag.getInt(NBT_GUI_BINDING_SLOT);
            if (slot < 0 || slot >= RtsStorageBindings.GUI_BINDING_SLOT_COUNT
                    || !bindingTag.contains(NBT_GUI_BINDING_POS, Tag.TAG_LONG)) {
                continue;
            }
            String bindingDimensionId = bindingTag.getString(NBT_GUI_BINDING_DIMENSION);
            ResourceLocation key = ResourceLocation.tryParse(bindingDimensionId);
            if (key == null) {
                continue;
            }
            String label = bindingTag.getString(NBT_GUI_BINDING_LABEL);
            String itemId = bindingTag.getString(NBT_GUI_BINDING_ITEM_ID);
            ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
            String normalizedItemId = itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey) ? itemId : "";
            Direction face = null;
            if (bindingTag.contains(NBT_GUI_BINDING_FACE, Tag.TAG_BYTE)) {
                int faceId = bindingTag.getByte(NBT_GUI_BINDING_FACE);
                if (faceId >= 0 && faceId < Direction.values().length) {
                    face = Direction.from3DDataValue(faceId);
                }
            }
            session.uiMemory.setGuiBinding(slot, new GuiBinding(
                    BlockPos.of(bindingTag.getLong(NBT_GUI_BINDING_POS)).immutable(),
                    ResourceKey.create(Registries.DIMENSION, key),
                    label,
                    normalizedItemId,
                    face));
        }
    }

    /** 将 GUI 绑定配置保存到 NBT */
    private static void saveGuiBindings(RtsStorageSession session, CompoundTag root) {
        ListTag guiBindings = new ListTag();
        for (int i = 0; i < session.uiMemory.getGuiBindingCount(); i++) {
            GuiBinding binding = session.uiMemory.getGuiBinding(i);
            if (binding == null || binding.pos() == null || binding.dimension() == null) {
                continue;
            }
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putInt(NBT_GUI_BINDING_SLOT, i);
            bindingTag.putLong(NBT_GUI_BINDING_POS, binding.pos().asLong());
            bindingTag.putString(NBT_GUI_BINDING_DIMENSION, binding.dimension().location().toString());
            if (binding.face() != null) {
                bindingTag.putByte(NBT_GUI_BINDING_FACE, (byte) binding.face().get3DDataValue());
            }
            bindingTag.putString(NBT_GUI_BINDING_LABEL, binding.label() == null ? "" : binding.label());
            bindingTag.putString(NBT_GUI_BINDING_ITEM_ID, binding.itemId() == null ? "" : binding.itemId());
            guiBindings.add(bindingTag);
        }
        root.put(NBT_GUI_BINDINGS, guiBindings);
    }

    // ======================================================================
    //  放置任务（待处理 + 进行中）
    // ======================================================================

    /** 待处理放置任务列表的 NBT 键名 */
    private static final String NBT_PENDING_PLACEMENT_JOBS = "pending_placement_jobs";
    /** 进行中放置任务列表的 NBT 键名 */
    private static final String NBT_ACTIVE_PLACEMENT_JOBS = "active_placement_jobs";

    /** 从 NBT 加载放置任务（待处理 + 进行中） */
    private static void loadPlacementJobs(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.placement.pendingJobs.clear();
        session.placement.placeBatchJobs.clear();

        ListTag pendingList = root.getList(NBT_PENDING_PLACEMENT_JOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            RtsPlacementBatch.PlaceBatchJob job =
                    RtsPlacementBatch.PlaceBatchJob.fromNbt(pendingList.getCompound(i), player.registryAccess());
            session.placement.pendingJobs.addLast(job);
        }

        ListTag activeList = root.getList(NBT_ACTIVE_PLACEMENT_JOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            RtsPlacementBatch.PlaceBatchJob job =
                    RtsPlacementBatch.PlaceBatchJob.fromNbt(activeList.getCompound(i), player.registryAccess());
            session.placement.placeBatchJobs.addLast(job);
        }
    }

    /** 将放置任务保存到 NBT（待处理 + 进行中） */
    private static void savePlacementJobs(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        ListTag pendingList = new ListTag();
        for (RtsPlacementBatch.PlaceBatchJob job : session.placement.pendingJobs) {
            if (job != null) {
                pendingList.add(job.toNbt(player.registryAccess()));
            }
        }
        root.put(NBT_PENDING_PLACEMENT_JOBS, pendingList);

        ListTag activeList = new ListTag();
        for (RtsPlacementBatch.PlaceBatchJob job : session.placement.placeBatchJobs) {
            if (job != null) {
                activeList.add(job.toNbt(player.registryAccess()));
            }
        }
        root.put(NBT_ACTIVE_PLACEMENT_JOBS, activeList);
    }

    // ======================================================================
    //  破坏任务（待处理 + 挂起）
    // ======================================================================

    /** 待处理破坏任务列表的 NBT 键名 */
    private static final String NBT_ACTIVE_DESTROY_JOBS = "active_destroy_jobs";
    /** 挂起破坏任务列表的 NBT 键名 */
    private static final String NBT_PENDING_DESTROY_JOBS = "pending_destroy_jobs";

    /** 从 NBT 加载破坏任务（待处理 + 挂起） */
    private static void loadDestroyJobs(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.destruction.destroyJobs.clear();
        session.destruction.pendingDestroyJobs.clear();

        ListTag activeList = root.getList(NBT_ACTIVE_DESTROY_JOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            RtsDestructionBatch.DestructionJob job =
                    RtsDestructionBatch.DestructionJob.fromNbt(activeList.getCompound(i));
            session.destruction.destroyJobs.addLast(job);
        }

        ListTag pendingList = root.getList(NBT_PENDING_DESTROY_JOBS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            RtsDestructionBatch.DestructionJob job =
                    RtsDestructionBatch.DestructionJob.fromNbt(pendingList.getCompound(i));
            session.destruction.pendingDestroyJobs.addLast(job);
        }
    }

    /** 将破坏任务保存到 NBT（待处理 + 挂起） */
    private static void saveDestroyJobs(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        ListTag activeList = new ListTag();
        for (RtsDestructionBatch.DestructionJob job : session.destruction.destroyJobs) {
            if (job != null) {
                activeList.add(job.toNbt());
            }
        }
        root.put(NBT_ACTIVE_DESTROY_JOBS, activeList);

        ListTag pendingList = new ListTag();
        for (RtsDestructionBatch.DestructionJob job : session.destruction.pendingDestroyJobs) {
            if (job != null) {
                pendingList.add(job.toNbt());
            }
        }
        root.put(NBT_PENDING_DESTROY_JOBS, pendingList);
    }

    // ======================================================================
    //  工具方法
    // ======================================================================

    /**
     * 清理并截断保存的文本，去除前后空白。
     *
     * @param value    原始文本
     * @param maxLength 最大允许长度
     * @return 清理后的文本，长度不超过 maxLength
     */
    private static String sanitizeSavedText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = value.trim();
        int limit = Math.max(0, maxLength);
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    /**
     * 从存储的序数值解析排序方式。
     *
     * @param ordinal 存储的排序枚举序数
     * @return 对应的排序方式，如果超出范围则返回默认排序（数量）
     */
    private static RtsStorageSort parseSavedSort(int ordinal) {
        RtsStorageSort[] values = RtsStorageSort.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return RtsStorageSort.QUANTITY;
        }
        return values[ordinal];
    }
}
