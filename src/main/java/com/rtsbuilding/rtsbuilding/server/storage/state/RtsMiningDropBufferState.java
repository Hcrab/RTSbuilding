package com.rtsbuilding.rtsbuilding.server.storage.state;

import com.rtsbuilding.rtsbuilding.server.task.buffer.LegacyBufferHandoffState;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 自动存入的有界中间缓存。
 *
 * <p>它只保留旧存档迁移所需的 Session shadow；新挖掘掉落直接进入 TaskStore escrow。
 * shadow 与 {@link #legacyHandoff} 一起持久化，在 Task root 与 Session clear 都确认前不得发放。</p>
 */
public final class RtsMiningDropBufferState {
    public static final int MAX_BUFFERED_ITEMS = RtsMiningDropBufferPolicy.MAX_BUFFERED_ITEMS;
    public static final int MAX_STACKS = RtsMiningDropBufferPolicy.MAX_STACKS;

    public final Deque<ItemStack> stacks = new ArrayDeque<>();
    public int bufferedItems;
    public long firstQueuedGameTime = -1L;
    public boolean fullNoticeSent;
    /** 旧 Session 缓存向 TaskStore 的两阶段所有权交接；新任务不会使用该字段。 */
    public LegacyBufferHandoffState legacyHandoff;
    /** 本进程等待落盘的 DROP_BUFFER 组件 revision；重启后由已加载的空 shadow 重新推导。 */
    public long handoffClearRevision;
    /** 指纹或迁移身份不一致时 fail-closed，禁止自动发放或重新提交。 */
    public boolean legacyHandoffConflict;

    public int remainingCapacity() {
        return RtsMiningDropBufferPolicy.remainingCapacity(bufferedItems);
    }

    public boolean isFull() {
        return RtsMiningDropBufferPolicy.isFull(bufferedItems, stacks.size());
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public void clearTimingWhenEmpty() {
        if (stacks.isEmpty()) {
            bufferedItems = 0;
            firstQueuedGameTime = -1L;
            fullNoticeSent = false;
        }
    }
}
