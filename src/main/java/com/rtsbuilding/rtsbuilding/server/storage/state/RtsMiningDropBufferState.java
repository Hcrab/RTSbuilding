package com.rtsbuilding.rtsbuilding.server.storage.state;

import net.minecraft.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自动存入的有界中间缓存。
 *
 * <p>同步生成的挖掘掉落先快速进入这里，再由 Tick 末限量写入 AE/RS 或普通库存。
 * 它不拥有挖掘工作流生命周期，但在物品写出前是唯一的短期所有者。</p>
 */
public final class RtsMiningDropBufferState {
    public static final int MAX_BUFFERED_ITEMS = RtsMiningDropBufferPolicy.MAX_BUFFERED_ITEMS;
    public static final int MAX_STACKS = RtsMiningDropBufferPolicy.MAX_STACKS;

    public final Deque<ItemStack> stacks = new ArrayDeque<>();
    public int bufferedItems;
    /** 连续一次真实储存写入零进度的起始 Tick；排队和正常写入时间不计入三秒回退。 */
    public long firstQueuedGameTime = -1L;
    public boolean fullNoticeSent;
    private boolean fallbackNoticeSent;
    private long fullSinceGameTime = -1L;
    /** 远距离安全入库提示的节流时间；仅属于本次在线会话，不需要持久化。 */
    private long lastRemoteSafetyNoticeGameTime = Long.MIN_VALUE;
    public int remainingCapacity() {
        return RtsMiningDropBufferPolicy.remainingCapacity(bufferedItems);
    }

    public boolean isFull() {
        return RtsMiningDropBufferPolicy.isFull(bufferedItems, stacks.size());
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    /**
     * Accepts a logical item count while keeping every buffered stack within the item's legal stack size.
     */
    public int enqueueMerged(ItemStack prototype, int requestedCount) {
        if (prototype == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(prototype) || requestedCount <= 0 || isFull()) {
            return 0;
        }
        int remaining = Math.min(requestedCount, remainingCapacity());
        int requested = remaining;
        int maxStackSize = Math.max(1, prototype.getMaxStackSize());

        for (ItemStack existing : stacks) {
            if (remaining <= 0) break;
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(existing, prototype)
                    || !ItemStack.areItemStackTagsEqual(existing, prototype)) continue;
            int free = Math.max(0, Math.min(maxStackSize, existing.getMaxStackSize()) - existing.stackSize);
            if (free <= 0) continue;
            int moved = Math.min(free, remaining);
            com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.grow(existing, moved);
            remaining -= moved;
        }

        while (remaining > 0 && stacks.size() < MAX_STACKS) {
            int chunkSize = Math.min(remaining, maxStackSize);
            ItemStack chunk = prototype.copy();
            chunk.stackSize = chunkSize;
            stacks.addLast(chunk);
            remaining -= chunkSize;
        }

        int accepted = requested - remaining;
        bufferedItems += accepted;
        return accepted;
    }

    public void markStorageBlocked(long gameTime) {
        if (firstQueuedGameTime < 0L) firstQueuedGameTime = gameTime;
    }

    public void markStorageProgress() {
        firstQueuedGameTime = -1L;
        fallbackNoticeSent = false;
    }

    public boolean fallbackEligible(long gameTime, long timeoutTicks) {
        return firstQueuedGameTime >= 0L && gameTime >= firstQueuedGameTime
                && gameTime - firstQueuedGameTime >= Math.max(0L, timeoutTicks);
    }

    public void updateFullState(long gameTime) {
        if (isFull()) {
            if (fullSinceGameTime < 0L) fullSinceGameTime = gameTime;
        } else {
            fullSinceGameTime = -1L;
            fullNoticeSent = false;
        }
    }

    public boolean shouldNotifyFull(long gameTime, long delayTicks) {
        return isFull() && !fullNoticeSent && fullSinceGameTime >= 0L
                && gameTime >= fullSinceGameTime
                && gameTime - fullSinceGameTime >= Math.max(0L, delayTicks);
    }

    /** 同一段持续写入失败只提示一次；只有储存真正恢复写入后才允许再次提示。 */
    public boolean shouldNotifyFallback() {
        if (fallbackNoticeSent) return false;
        fallbackNoticeSent = true;
        return true;
    }

    /** 范围挖掘可能每 tick 捕获很多方块，提示只按冷却周期发送一次。 */
    public boolean shouldNotifyRemoteSafety(long gameTime, long cooldownTicks) {
        long cooldown = Math.max(0L, cooldownTicks);
        if (lastRemoteSafetyNoticeGameTime == Long.MIN_VALUE
                || gameTime < lastRemoteSafetyNoticeGameTime
                || gameTime - lastRemoteSafetyNoticeGameTime >= cooldown) {
            lastRemoteSafetyNoticeGameTime = gameTime;
            return true;
        }
        return false;
    }

    public void clearTimingWhenEmpty() {
        if (stacks.isEmpty()) {
            bufferedItems = 0;
            firstQueuedGameTime = -1L;
            fullNoticeSent = false;
            fullSinceGameTime = -1L;
        }
    }
}
