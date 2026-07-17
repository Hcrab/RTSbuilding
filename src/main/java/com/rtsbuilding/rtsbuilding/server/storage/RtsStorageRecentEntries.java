package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 维护玩家的最近物品/流体历史记录，用于 RTS 存储 UI。
 *
 * <p>本类仅持有存储在 {@link RtsUiMemory#getRecentEntries()} 中的
 * 短期"最近看到或使用过"的历史记录。最近条目是 UI 记忆，
 * 不是权威的物品栏数量，绝不能用作存储计数。
 *
 * <p>它刻意不序列化 NBT、搜索存储、构建存储页面负载、
 * 执行合成、转移物品或流体，或吸收掉落物。
 * 这些系统可以读取或记录最近条目，但本类仅修改最近条目的双端队列。
 *
 * <p>原来的去重、排序、数量合并、容量合并和限制行为必须保持稳定：
 * 等效的物品/流体条目合并，最新条目出现在最前，
 * 历史记录裁剪到存储 UI 限制。
 */
public final class RtsStorageRecentEntries {
    public static final int RECENT_ENTRY_LIMIT = 24;

    private RtsStorageRecentEntries() {
    }

    static void recordCraftedOutput(RtsStorageSession session, ItemStack crafted) {
        if (crafted == null || crafted.isEmpty()) {
            return;
        }
        recordRecentItem(
                session,
                crafted,
                S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED,
                crafted.getCount());
    }

    /**
     * 通过解析注册表键来记录一个物品。如果键无法解析，
     * 该物品被跳过；从不使用显示名称，因为它们随语言和资源包变化。
     */
    static void recordRecentItem(RtsStorageSession session, ItemStack stack, byte kind, long amount) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        recordRecentItem(session, id.toString(), kind, amount);
    }

    /**
     * 记录一个预解析的物品注册表键。缺失的键被跳过，
     * 调用者必须传递稳定的注册表 ID 而非翻译后的显示名称，
     * 以确保最近历史在语言变更后仍然有效。
     */
    public static void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(itemId, amount, 0L, kind));
    }

    /**
     * 记录一个预解析的流体注册表键。缺失的键被跳过，
     * 调用者必须传递稳定的注册表 ID 而非翻译后的显示名称，
     * 以确保最近历史在语言变更后仍然有效。
     */
    static void recordRecentFluid(RtsStorageSession session, String fluidId, byte kind, long amount, long capacity) {
        if (fluidId == null || fluidId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(fluidId, amount, Math.max(0L, capacity), kind));
    }

    /**
     * 使用现有的 UI 历史规则推送最近条目：按注册表 ID 加物品/流体类别去重，
     * 最新或合并后的条目插入到最前，
     * 超出 UI 限制的旧条目从末尾裁剪。
     * 非正数量被忽略，因为最近历史代表玩家实际看到或使用过的内容；
     * 零/负数量会创建空的 UI 行，这不是真实的存储计数。
     */
    static void pushRecentEntry(RtsStorageSession session, RecentEntry entry) {
        if (session == null
                || entry == null
                || entry.id() == null
                || entry.id().isBlank()
                || entry.amount() <= 0L) {
            return;
        }
        RecentEntry normalized = new RecentEntry(
                entry.id(),
                Math.max(1L, entry.amount()),
                Math.max(0L, entry.capacity()),
                entry.kind());
        RecentEntry merged = normalized;
        var recentEntries = session.uiMemory.getRecentEntries();
        for (RecentEntry existing : recentEntries) {
            if (!sameRecentKey(existing, normalized)) {
                continue;
            }
            long mergedAmount = Math.max(1L, RtsCountUtil.saturatedAdd(existing.amount(), normalized.amount()));
            long mergedCapacity = Math.max(Math.max(existing.capacity(), normalized.capacity()), mergedAmount);
            merged = new RecentEntry(normalized.id(), mergedAmount, mergedCapacity, normalized.kind());
            break;
        }
        final RecentEntry mergedEntry = merged;
        recentEntries.removeIf(existing -> sameRecentKey(existing, mergedEntry));
        recentEntries.addFirst(mergedEntry);
        while (recentEntries.size() > RECENT_ENTRY_LIMIT) {
            recentEntries.removeLast();
        }
    }

    private static boolean sameRecentKey(RecentEntry a, RecentEntry b) {
        if (a == null || b == null) {
            return false;
        }
        return a.id().equals(b.id()) && isRecentFluidKind(a.kind()) == isRecentFluidKind(b.kind());
    }

    private static boolean isRecentFluidKind(byte kind) {
        return kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
    }

}
